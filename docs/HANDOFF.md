# Handoff: where the project stands

Last updated at the end of round 5. Branch `claude/minecraft-mod-ui-guidebook-ak57k4`. Latest delivered test build: `dist/Arsenal-Support-Round5-0.21.0-test.zip`.
The design is described in `docs/GAME-DESIGN-DOCUMENT.md` (keep it updated). Rules and conventions are in `/CLAUDE.md`; this file is the state of the work.

## What the project is

Three Forge 1.20.1 mods for a TaCZ gun pack: **Defense Beacon** (`custom-mods/arsenal-beacon`, id `arsenal_beacon`, package `dev.createarsenal.beacon`), **Gun Guide**, **Gun Displays**.
Each is built twice: the **pack edition** (needs Create and KubeJS) and the **standalone edition** (flavor flag in the jar). The owner plays the pack; the standalone edition is a public Modrinth release (`docs/MODRINTH.md`).
Most of the code is dense single-line style; match the surrounding file.

## Map of the code (beacon mod)

| Area | Files |
| --- | --- |
| Campaign and raids | `ArsenalBeacon` (begin, scan, waves, spawn, fail, commands), `CampaignData` (saved state), `RaidBalance`, `RaidSpawns`, `RaidBreaching`, `RaidRewards`, `RaidTypes` (special raids), `HardRaids`, `RaidRespite` |
| Money | `Economy` (all prices per edition), `SupportRules` (flare prices, timings), `CannonUpgrades` (upgrade table, fire types), `ArdentEnergy` (drops), `ExchangeShop` (+ `ExchangeScreen`), `StandaloneBalance`, `ArsenalConfig` |
| Support system | `SupportData` (per-owner bases), `SupportCalls`, `SupportFlares`, `SupportPlatform`, `SupportCannon`/`CannonEntity`/`CannonControl`/`CannonScreen`, `SupportShop`, `BaseZone`, `ReturnZone`, `SupportCrate`, `SupportHud` |
| Client | `BeaconClient` (screens, zone outline), `SupportClient` (renderers: sign, nameplate, canopy, red parachutes), `BeaconPopups`, `Ui` (UI kit) |
| Network | `BeaconNetwork` (protocol **21**; messages 6 to 14 listed in `docs/ENERGY-AND-SUPPORT.md`) |
| Art | `tools/ui-assets/*.py` generate sprites and textures; the hand-made 3D models come from `docs/art/` via `import_support_gear.py` |

## How to build and test

* `./gradlew --no-daemon --offline build releaseJars` (JDK 17); jars land in `custom-mods/*/build/libs/` (`-standalone` jars are the standalone edition).
* Unit tests: 58 in the beacon module, all passing at the end of round 5.
* **Real-client QA:** `tools/qa/setup-qa.sh` makes a throwaway copy with the scripted harness `QaWorld` (stored as `tools/qa/QaWorld.java.txt` so it never ships), boots a real client with `xvfb-run` and logs `QA_CHECK PASS/FAIL` lines.
  Pick the parts to run with a flag file (`/tmp/claude-0/qa-r5.flag`: `sign reward exchange upgrade hard raids dbg all`); switch edition through the flavor argument. Tips learned the hard way:
  kill leftover client JVMs by PID (`pkill -x java` misses them, and `pgrep -f` in the same command line kills your own shell); never nest the server-read helper; walls in the harness must not cover the shop column.
  `QaWorld` has rounds 3 to 5 scripts; add a new `scriptR6()` rather than editing old ones.

## Decisions the owner has made (do not re-litigate)

* One Support Platform and one Support Cannon per player, anywhere in the beacon zone; shovel moves them for free.
* Pack: cannon and beacon upgrades are paid with the pack's own parts; Ardent Energy only buys flares and Exchange goods. Standalone: everything in Ardent Energy, drop rate x1.3.
* Raids: special raids from the fourth raid on, 50 percent chance, also in boss raids; the next type is shown at the end of a raid and in the Overview.
* The beacon itself is the reward chest (no chest block). Bunker Buster never removes obsidian, crying obsidian, bedrock or anything with resistance 1,200 or more.
* The platform stays two blocks tall; its name sign is render-only (no hitbox); the two blocks in front are a no-build return zone.

## Known gaps and unverified items (round 5)

1. **Zone outline flicker** while the cannon fires: never reproduced; the fix (stale limit 4 s to 30 s, own vertex buffer in `BeaconClient`) is a best guess. If the owner still sees it, instrument the client packet timing and the render stage first.
2. Air, Siege and "They are thousands" raids were checked through their mob mixes only; in the small QA arena `RaidSpawns.find` found no ground spawn. Needs a hands-on run (use `/arsenal test-raid <type>`).
3. Not tested: real multiplayer, the End and modded dimensions, the pack prices with real Create/KubeJS items (the QA world only has this mod's parts), migration of an old `arsenal-beacon-exchange.txt`.
4. All prices and drop rates are design estimates; no long playtest. Expect the owner to ask for tuning: change `Economy`, `CannonUpgrades`, `RaidRewards`, `StandaloneBalance`, `ArdentEnergy`/`ArsenalConfig`, then update the numbers quoted in `docs/ENERGY-AND-SUPPORT.md` and the field guide (a test checks the guide).
5. Modrinth page screenshots are placeholders (`REPLACE_ME`).

## Suggested next steps

* Collect the owner's feedback on the round 5 test build; fix bugs first, tune numbers second.
* Run a real session with the special-raid commands and fix spawn placement for flyers, siege and swarm if needed.
* When the owner confirms a round, tag the state in `docs/CHANGELOG.md` and build a new `dist/` zip like the earlier ones (`standalone/`, `pack/`, `README.txt`, `SHA512SUMS`).
