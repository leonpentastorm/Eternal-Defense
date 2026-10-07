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
# the standalone starter kit needs TaCZ items
sed -i 's/StandaloneBalance.grantStarter(p);return;}/try{StandaloneBalance.grantStarter(p);}catch(Throwable t){}return;}/' "$B/java/dev/createarsenal/beacon/ArsenalBeacon.java"
echo "flavor=$FLAVOR" > "$B/resources/arsenal-build.properties"
echo "QA copy ready in $DST (flavor $FLAVOR)."
echo "1. echo all > /tmp/claude-0/qa-r5.flag   (parts: sign reward exchange upgrade hard raids dbg all)"
echo "2. cd $DST && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 xvfb-run -a ./gradlew --no-daemon --offline :arsenal-beacon:runClient > /tmp/claude-0/qa.log 2>&1"
echo "3. grep QA_CHECK /tmp/claude-0/qa.log ; screenshots land in $DST/custom-mods/arsenal-beacon/run-client/shots/screenshots (look for run-client under the module)"
echo "Kill leftover client JVMs by PID afterwards (pkill -x java misses them)."
