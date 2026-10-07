# Instructions for Claude sessions on this repository

Read `docs/HANDOFF.md` first: it says where the project stands, how to build and test, what is unverified and what to do next.

## Rules of this project

* Work on branch `claude/minecraft-mod-ui-guidebook-ak57k4`; push only there. Open no pull request unless the owner asks.
* End every commit message with these two lines (and no model identifier anywhere in the repo, in code comments or in docs):
  `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` and `Claude-Session: <the session link of that session>`.
* Build with JDK 17: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (unit tests: `:arsenal-beacon:test`).
* Write down only what was actually tested; list what was not. Display artwork is All Rights Reserved by its creator.
* Two editions come from one source tree: `pack` (Create, KubeJS) and `standalone` (no Create). The flavor is `/arsenal-build.properties` in the jar (`BuildFlavor.STANDALONE`); every price lives in `Economy`.
* Guide text keys: `key`, `key.pack`, `key.standalone`; in lang files `%` must be written `%%`. `LangKeysTest` and `SupportTest` fail when keys or quoted numbers drift.
* Bump the network protocol (`BeaconNetwork`) whenever a packet changes, and say so in the test-build README.
* Keep `docs/HANDOFF.md` current at the end of every round (status, decisions, untested list, next steps) and add a line to `docs/CHANGELOG.md`.
