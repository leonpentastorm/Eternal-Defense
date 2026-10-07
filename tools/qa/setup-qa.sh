#!/usr/bin/env bash
# Builds a THROWAWAY copy of the repo that boots a real client into a flat world and runs scripted checks (QaWorld).
# Nothing here is part of the mod: the harness is stored as QaWorld.java.txt so it is never compiled into a release.
# Usage: tools/qa/setup-qa.sh [target-dir] [pack|standalone]
set -euo pipefail
SRC="$(cd "$(dirname "$0")/../.." && pwd)"
DST="${1:-/tmp/claude-0/qa}"; FLAVOR="${2:-pack}"
rm -rf "$DST"; mkdir -p "$DST"
(cd "$SRC" && tar --exclude=.git --exclude=build --exclude=.gradle --exclude=run-client --exclude=./dist -cf - .) | (cd "$DST" && tar -xf -)
B="$DST/custom-mods/arsenal-beacon/src/main"
cp "$SRC/tools/qa/QaWorld.java.txt" "$B/java/dev/createarsenal/beacon/QaWorld.java"
# TaCZ, Create and KubeJS are not in the dev runtime: make them optional
sed -i '/modId="tacz"/,/side=/ s/mandatory=true/mandatory=false/; /modId="create"/,/side=/ s/mandatory=true/mandatory=false/; /modId="kubejs"/,/side=/ s/mandatory=true/mandatory=false/' "$B/resources/META-INF/mods.toml"
# Gun Displays also needs TaCZ: optional here
sed -i '/modId="tacz"/,/side=/ s/mandatory=true/mandatory=false/' "$DST/custom-mods/arsenal-displays/src/main/resources/META-INF/mods.toml"
# the standalone starter kit needs TaCZ items
sed -i 's/StandaloneBalance.grantStarter(p);return;}/try{StandaloneBalance.grantStarter(p);}catch(Throwable t){}return;}/' "$B/java/dev/createarsenal/beacon/ArsenalBeacon.java"
# a client run configuration (the repository only has server runs) and, when TACZ_JAR points at the TaCZ 1.1.8-hotfix2 jar, TaCZ on the runtime classpath
[ -n "${TACZ_JAR:-}" ] && python3 "$SRC/tools/qa/make_srg_to_official.py" "${FG_VERSIONS:-/root/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1}" "$DST/srg2off.srg"
python3 - "$DST/custom-mods/arsenal-beacon/build.gradle" "${TACZ_JAR:-}" "$DST/custom-mods/arsenal-beacon" <<'PY'
import sys,shutil,os
path,jar,mod=sys.argv[1:4]
s=open(path).read()
s=s.replace("minecraft {\n","evaluationDependsOn(':arsenal-displays')\nminecraft {\n",1)
s=s.replace('implementation project(":arsenal-displays")','implementation project(":arsenal-displays").sourceSets.main.output',1)
displays="                   arsenal_displays { source project(':arsenal-displays').sourceSets.main } }\n"
if not jar:
    s=s.replace("    runs {\n","    runs {\n        client {\n            workingDirectory project.file('run-client')\n            args '--width','1280','--height','720'\n            mods { arsenal_beacon { source sourceSets.main }\n"+displays+"        }\n",1)
else:
    # TaCZ mode (game tests): the production mixins of TaCZ are remapped to the development names, and Gun Displays is a mod of the run
    s=s.replace("        gameTestServer {\n","        gameTestServer {\n            args '-mixin.config=arsenal_beacon.mixins.json'\n            property 'mixin.env.remapRefMap','true'\n            property 'mixin.env.refMapRemappingFile',new File(rootProject.projectDir,'srg2off.srg').absolutePath\n",1)
    s=s.replace("            property 'forge.enabledGameTestNamespaces', 'arsenal_beacon'\n            mods { arsenal_beacon { source sourceSets.main } }","            property 'forge.enabledGameTestNamespaces', 'arsenal_beacon'\n            mods { arsenal_beacon { source sourceSets.main }\n"+displays,1)
if jar:
    d=os.path.join(mod,'libs/maven/local/tacz/1.1.8-hotfix2');os.makedirs(d,exist_ok=True);shutil.copy(jar,os.path.join(d,'tacz-1.1.8-hotfix2.jar'))
    os.makedirs(os.path.join(mod,'gametest-run'),exist_ok=True);open(os.path.join(mod,'gametest-run','eula.txt'),'w').write('eula=true\n')
    open(os.path.join(d,'tacz-1.1.8-hotfix2.pom'),'w').write('<project><modelVersion>4.0.0</modelVersion><groupId>local</groupId><artifactId>tacz</artifactId><version>1.1.8-hotfix2</version></project>')
    s=s.replace("repositories { ","repositories { maven { url = file('libs/maven') }; ",1)
    s=s.replace("dependencies {\n","dependencies {\n    implementation fg.deobf('local:tacz:1.1.8-hotfix2')\n",1)
open(path,'w').write(s)
PY
# first launch would stop at the narrator screen: start with known client options
mkdir -p "$DST/custom-mods/arsenal-beacon/run-client" && cp "$SRC/tools/qa/qa-options.txt" "$DST/custom-mods/arsenal-beacon/run-client/options.txt"
echo "flavor=$FLAVOR" > "$B/resources/arsenal-build.properties"
echo "QA copy ready in $DST (flavor $FLAVOR)."
echo "1. echo all > /tmp/claude-0/qa-r5.flag   (parts: sign reward exchange upgrade hard raids dbg all)"
echo "2. cd $DST && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 xvfb-run -a ./gradlew --no-daemon --offline :arsenal-beacon:runClient > /tmp/claude-0/qa.log 2>&1"
echo "3. grep QA_CHECK /tmp/claude-0/qa.log ; screenshots land in $DST/custom-mods/arsenal-beacon/run-client/shots/screenshots (look for run-client under the module)"
echo "Kill leftover client JVMs by PID afterwards (pkill -x java misses them)."
