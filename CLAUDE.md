# Instructions for Claude sessions on this repository

Read `docs/HANDOFF.md` first: it says where the project stands, how to build and test, what is unverified and what to do next.

## Rules of this project

* The active development branch is **`dev`**: work and push there only (see *Branches and versions* below). Open no pull request unless the owner asks.
* End every commit message with these two lines (and no model identifier anywhere in the repo, in code comments or in docs):
  `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` and `Claude-Session: <the session link of that session>`.
* Build with JDK 17: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (unit tests: `:arsenal-beacon:test`).
* Write down only what was actually tested; list what was not. Display artwork is All Rights Reserved by its creator.
* Two editions come from one source tree: `pack` (Create, KubeJS) and `standalone` (no Create). The flavor is `/arsenal-build.properties` in the jar (`BuildFlavor.STANDALONE`); every price lives in `Economy`.
* Guide text keys: `key`, `key.pack`, `key.standalone`; in lang files `%` must be written `%%`. `LangKeysTest` and `SupportTest` fail when keys or quoted numbers drift.
* Bump the network protocol (`BeaconNetwork`) whenever a packet changes, and say so in the test-build README.
* Keep `docs/HANDOFF.md` current at the end of every round (status, decisions, untested list, next steps) and add a line to `docs/CHANGELOG.md`.
* `docs/GAME-DESIGN-DOCUMENT.md` (Eternal Defense GDD) must be updated in the same commit as any gameplay, numbers, economy, raid, UI-flow or edition change: bump its document version, add a revision-history row.

## Branches and versions (Eternal Defense project version, file `VERSION`)

* `dev` is the development branch: all work goes to `dev` (historical context only: until 0.0.1 the work lived on `claude/minecraft-mod-ui-guidebook-ak57k4`, which is fully merged into `dev` and must not be used). `main` only receives merges from `dev` when the owner says so.
* Version scheme: **0.0.x** for every delivered update on `dev` (0.0.1, then 0.0.2, 0.0.3, ...); **0.1.0** only when the owner asks for it; **1.0.0** when `dev` is merged into `main`.
* Each release: update `VERSION`, the GDD revision history and `docs/CHANGELOG.md`, commit, then tag `vX.Y.Z` on that commit and try to push the tag (the cloud git proxy refused tag pushes in the first attempt; if it still does, `VERSION` and the changelog are the record).
* The mod jars still carry their own build versions (0.21.0 and 1.1.0); the project version is the one in `VERSION` and the tags.
