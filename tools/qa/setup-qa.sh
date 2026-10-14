#!/usr/bin/env bash
# Builds a THROWAWAY copy of the repo that boots a real client into a flat world and runs scripted checks (QaWorld).
# Nothing here is part of the mod: the harness is stored as QaWorld.java.txt so it is never compiled into a release.
# QA_CREATE=1 (pack only): Create runs too; QA_MODS=<dir>: the jars in it run too (0.0.42); the scenario is compiled in the Create compat source set; the first run needs the network.
# Usage: tools/qa/setup-qa.sh [target-dir] [pack] [QaWorld|QaKitchen|QaGuide|QaStuck|QaOptimize|QaHunt|QaFob|QaDrinks|QaFire|QaDrinkLoop|QaMre|QaAnim|QaHub|QaHubCreate|QaRound41|QaRound42|QaRound43|QaProfile|QaMagazines|QaVoice|QaRelease101|QaLoadout|QaDisplays]   (QA_NO_DISPLAYS=1: without the displays mod)   (QaWorld scenario by flag file, e.g. echo all > /tmp/ed-qa/qa-r7.flag for the music player and beautify checks)   (QA_WIDTH / QA_HEIGHT set the window, default 1280 x 720)   (the scenario file tools/qa/<name>.java.txt)
set -euo pipefail
SRC="$(cd "$(dirname "$0")/../.." && pwd)"
DST="${1:-/tmp/ed-qa/qa}"; FLAVOR="${2:-pack}"
# 0.0.41: the standalone edition is discontinued; the second argument stays for old command lines and must be "pack"
if [ "$FLAVOR" != "pack" ]; then echo "Only the pack edition exists since 0.0.41 (got: $FLAVOR)." >&2; exit 2; fi
rm -rf "$DST"; mkdir -p "$DST"
(cd "$SRC" && tar --exclude=.git --exclude=build --exclude=.gradle --exclude=run-client --exclude=./dist -cf - .) | (cd "$DST" && tar -xf -)
B="$DST/custom-mods/eternal-defense/src/main"
SCENARIO="${3:-QaWorld}"
# the scenario is compiled in the gametest source set (it sees the main code and the GameTests); with QA_CREATE=1 that set also sees Create
mkdir -p "$DST/custom-mods/eternal-defense/src/gametest/java/dev/eternaldefense/beacon"
cp "$SRC/tools/qa/$SCENARIO.java.txt" "$DST/custom-mods/eternal-defense/src/gametest/java/dev/eternaldefense/beacon/$SCENARIO.java"
if [ "${QA_CREATE:-0}" = "1" ]; then
  # Create itself runs too (Create, Ponder, Flywheel, Registrate from their own mavens, fetched online once)
  printf "\nsourceSets.gametest.compileClasspath += sourceSets.create.compileClasspath + sourceSets.create.output\n" >> "$DST/custom-mods/eternal-defense/build.gradle"
fi
# TaCZ, Create, LesRaisins Tactical Equipments and JEI are not all in the dev runtime: make them optional (the test pack has them all)
sed -i '/modId="tacz"/,/side=/ s/mandatory=true/mandatory=false/; /modId="create"/,/side=/ s/mandatory=true/mandatory=false/; /modId="jei"/,/side=/ s/mandatory=true/mandatory=false/; /modId="lrtactical"/,/side=/ s/mandatory=true/mandatory=false/' "$B/resources/META-INF/mods.toml"
# Gun Displays also needs TaCZ: optional here
sed -i '/modId="tacz"/,/side=/ s/mandatory=true/mandatory=false/' "$DST/custom-mods/eternal-defense-displays/src/main/resources/META-INF/mods.toml"
# TaCZ and TaCZ Attributes are normal dependencies of the beacon since Mess Hall v4. Their production mixins use SRG names, so the client run
# remaps every refmap to the development names (see make_srg_to_official.py); the beacon's own mixins are configured explicitly.
FG_VERSIONS="${FG_VERSIONS:-/root/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1}"
python3 "$SRC/tools/qa/make_srg_to_official.py" "$FG_VERSIONS" "$DST/srg2off.srg"
python3 - "$DST/custom-mods/eternal-defense/build.gradle" <<'PY'
import sys,os
path=sys.argv[1]
s=open(path).read()
s=s.replace("minecraft {\n","evaluationDependsOn(':eternal-defense-displays')\nminecraft {\n",1)
# QA_NO_DISPLAYS=1: the displays mod is only compiled against, never loaded
if os.environ.get('QA_NO_DISPLAYS')=='1':
    s=s.replace('implementation project(":eternal-defense-displays")','compileOnly project(":eternal-defense-displays").sourceSets.main.output',1)
    displays="            }\n"
else:
    s=s.replace('implementation project(":eternal-defense-displays")','implementation project(":eternal-defense-displays").sourceSets.main.output',1)
    displays="                   eternal_defense_displays { source project(':eternal-defense-displays').sourceSets.main } }\n"
client=("        client {\n            workingDirectory project.file('run-client')\n            args '--width','"+os.environ.get('QA_WIDTH','1280')+"','--height','"+os.environ.get('QA_HEIGHT','720')+"','-mixin.config=eternal_defense.mixins.json'\n"
       "            property 'mixin.env.remapRefMap','true'\n            property 'mixin.env.refMapRemappingFile',new File(rootProject.projectDir,'srg2off.srg').absolutePath\n"
       "            mods { eternal_defense { source sourceSets.main; source sourceSets.gametest"+("; source sourceSets.create" if os.environ.get('QA_CREATE')=='1' else "")+" }\n"+displays+"        }\n")
s=s.replace("    runs {\n","    runs {\n"+client,1)
if os.environ.get('QA_CREATE')=='1':
    s+=("\nrepositories {\n    maven { url = 'https://maven.createmod.net'; content { includeGroup 'dev.engine-room.flywheel'; includeGroup 'net.createmod.ponder' } }\n"
        "    maven { url = 'https://maven.ithundxr.dev/mirror'; content { includeGroup 'com.tterrag.registrate' } }\n}\n"
        "dependencies {\n"
        "    runtimeOnly(fg.deobf('com.simibubi.create:create-1.20.1:6.0.8-291:slim')){ transitive = false }\n"
        "    runtimeOnly(fg.deobf('net.createmod.ponder:Ponder-Forge-1.20.1:1.0.91')){ transitive = false }\n"
        "    runtimeOnly(fg.deobf('dev.engine-room.flywheel:flywheel-forge-1.20.1:1.0.6')){ transitive = false }\n"
        "    runtimeOnly(fg.deobf('com.tterrag.registrate:Registrate:MC1.20-1.3.3')){ transitive = false }\n}\n")
open(path,'w').write(s)
PY
# 0.0.42: QA_MODS=<dir> adds every jar in that directory to the client as a runtime mod (remapped like TaCZ; their mixins' refmaps are remapped
# by the run's refmap setting), e.g. TaCZ Magazines, Corpse and Player Animator from the owner's pack
if [ -n "${QA_MODS:-}" ]; then
  mkdir -p "$DST/qa-mods"; n=0; deps=""
  for jar in "$QA_MODS"/*.jar; do n=$((n+1)); cp "$jar" "$DST/qa-mods/qamod$n-1.jar"; deps="$deps    runtimeOnly(fg.deobf('blank:qamod$n:1')){ transitive = false }\n"; echo "QA mod $n: $(basename "$jar")"; done
  printf "\nrepositories { flatDir { dirs rootProject.file('qa-mods') } }\ndependencies {\n$deps}\n" >> "$DST/custom-mods/eternal-defense/build.gradle"
fi
# first launch would stop at the narrator screen: start with known client options
mkdir -p "$DST/custom-mods/eternal-defense/run-client" && cp "$SRC/tools/qa/qa-options.txt" "$DST/custom-mods/eternal-defense/run-client/options.txt"
cp "$SRC/custom-mods/eternal-defense/src/novoice/voice-replacements.json" "$DST/custom-mods/eternal-defense/run-client/qa-voicemap.json"
# QA_NOVOICE=1: the client runs the no-voice edition's sounds.json, without the voice files (build noVoiceJar in the repo first)
if [ "${QA_NOVOICE:-0}" = "1" ]; then
  unzip -o -q "$SRC/custom-mods/eternal-defense/build/libs/eternal-defense-"*"-novoice.jar" assets/eternal_defense/sounds.json -d "$DST/novoice"
  cp "$DST/novoice/assets/eternal_defense/sounds.json" "$B/resources/assets/eternal_defense/sounds.json"
  python3 - "$SRC/custom-mods/eternal-defense/src/main/resources/assets/eternal_defense/sounds.json" "$DST/custom-mods/eternal-defense/run-client/qa-voicemap.json" "$B/resources" <<'PY'
import json,sys,os
sounds=json.load(open(sys.argv[1]));voice=json.load(open(sys.argv[2]))
for event in voice:
    for s in sounds[event]['sounds']:
        ns,name=(s['name'] if isinstance(s,dict) else s).split(':')
        os.remove(os.path.join(sys.argv[3],'assets',ns,'sounds',name+'.ogg'))
PY
  touch "$DST/custom-mods/eternal-defense/run-client/qa-novoice"
fi
echo "QA copy ready in $DST (pack edition)."
echo "1. echo all > /tmp/ed-qa/qa-r5.flag   (parts: sign reward exchange upgrade hard raids dbg all)"
echo "2. cd $DST && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 xvfb-run -a ./gradlew --no-daemon --offline :eternal-defense:runClient > /tmp/ed-qa/qa.log 2>&1"
echo "3. grep QA_CHECK /tmp/ed-qa/qa.log ; screenshots land in $DST/custom-mods/eternal-defense/run-client/shots/screenshots (look for run-client under the module)"
echo "Kill leftover client JVMs by PID afterwards (pkill -x java misses them)."
