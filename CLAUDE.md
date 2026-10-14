# Instructions for Claude sessions on this repository

Read `docs/HANDOFF.md` first: it says where the project stands, how to build and test, what is unverified and what to do next.

## Branch note (2026-10-09)

The Mess Hall v6 work (`feature/messhall-ver-6`, project 0.0.7 to 0.0.11) was merged into `dev` at the owner's request and the merged feature branches were deleted. On 2026-10-10 the feature branches of 0.0.13 to 0.0.17 (`feature/stuck-raiders`, `feature/raider-gates`, `feature/optimize-path`, `feature/beautify-path`, `feature/music-player`) were merged into `dev` at the owner's request (a fast-forward). Work on `dev` as the rules below say; `main` still only receives merges when the owner asks. Build/test execution is authorized; include any newly required backend JAR in the test ZIP.

## Release 1.0.0 (2026-10-14)

At the owner's request `dev` was merged into `main` as **1.0.0**: the project is named **Eternal Defense** everywhere (mod ids `eternal_defense`, `eternal_defense_gun_guide`, `eternal_defense_displays`; package `dev.eternaldefense`; command `/eternaldefense`); no reference to the project's earlier name remains in the code, assets, pack scripts or jars (the tools that migrate old exports name the old ids on purpose). The main mod ships in two builds, `eternal-defense-<version>.jar` (radio voice lines) and `eternal-defense-<version>-novoice.jar` (game sounds instead, `src/novoice/voice-replacements.json`).

## Rules of this project

* The active development branch is **`dev`**: work and push there only (see *Branches and versions* below). Open no pull request unless the owner asks.
* End every commit message with these two lines (and no model identifier anywhere in the repo, in code comments or in docs):
  `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` and `Claude-Session: <the session link of that session>`.
* Build with JDK 17: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (unit tests: `:eternal-defense:test`). `releaseJars` makes both builds of the main mod; a new spoken sound needs its game sound in `src/novoice/voice-replacements.json` (`VoiceEditionTest` checks it).
* Code comments are one short, neutral line; no version, owner, session or process notes in code, assets or pack scripts. Release jars carry no tests, QA harnesses, test structures or readme/notes: GameTests and their structures go in `src/gametest` (development runs only), QA harnesses stay in `tools/qa` as `.java.txt`.
* Write down only what was actually tested; list what was not. Display artwork is All Rights Reserved by its creator.
* Test bundles must include separate JARs for newly added required mods, with versions, source links, checksums and applicable license notices. For Mess Hall v5 this includes leopoko's TaCZ Attributes 1.4. Do not shade the addon into the beacon JAR. If redistribution is unavailable, state that before packaging and provide the author's download instead.
* Since 0.0.41 there is one edition, the **pack** (Create, KubeJS, KubeJS Create, JEI, TaCZ, TaCZ Attributes, LesRaisins Tactical Equipments are required in `mods.toml`); the standalone edition is discontinued (owner, 0.0.41). Every price lives in `Economy`. The test pack is the owner's exported Modrinth profile rebuilt with `tools/release/make-testpack.py`.
* Guide text keys: one key per text (no edition suffixes or markers since 0.0.41); in lang files `%` must be written `%%`. `LangKeysTest` and `SupportTest` fail when keys or quoted numbers drift.
* Guide, tooltip, hint and confirmation text is written for a player who just spawned in: what it is, what it gives, how it works, how to unlock it, and what can go wrong (never design-document material). Rules and the tests that enforce them: `docs/GUIDE-STYLE.md`. Adding a block or item needs a Field Guide card.
* Bump the network protocol (`BeaconNetwork`) whenever a packet changes, and say so in the test-build README.
* Keep `docs/HANDOFF.md` current at the end of every round (status, decisions, untested list, next steps) and add a line to `docs/CHANGELOG.md`.
* `docs/GAME-DESIGN-DOCUMENT.md` (Eternal Defense GDD) must be updated in the same commit as any gameplay, numbers, economy, raid, UI-flow or edition change: bump its document version, add a revision-history row.

## Branches and versions (Eternal Defense project version, file `VERSION`)

* `dev` is the development branch: all work goes to `dev` (historical context only: until 0.0.1 the work lived on `claude/minecraft-mod-ui-guidebook-ak57k4`, which is fully merged into `dev` and must not be used). `main` only receives merges from `dev` when the owner says so.
* Version scheme: **0.0.x** for every delivered update on `dev` up to 0.0.43; **1.0.0** when `dev` was merged into `main` (2026-10-14). After 1.0.0 ask the owner which number comes next; until told, 1.0.1, 1.0.2, ... for updates on `dev`.
* Each release: update `VERSION`, the GDD revision history and `docs/CHANGELOG.md`, commit, then tag `vX.Y.Z` on that commit and try to push the tag (the cloud git proxy refused tag pushes in the first attempt; if it still does, `VERSION` and the changelog are the record).
* Since 1.0.0 the three mod jars carry the project version (`VERSION`, the tags and every `build.gradle` agree).
