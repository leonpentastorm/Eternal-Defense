# Handoff: where the project stands

Last updated for the Mess Hall development round 6 (project version 0.0.2). Active development branch: **`dev`** (project version in `VERSION`). History note: the work up to 0.0.1 was done on `claude/minecraft-mod-ui-guidebook-ak57k4`, now merged into `dev`. Round 6 test build: `dist/Arsenal-MessHall-Round6-0.0.2-test.zip`; prior support build: `dist/Arsenal-Support-Round5-0.21.0-test.zip`.
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
| Network | `BeaconNetwork` (protocol **22**; new S2C messages 15 meal HUD, 16 open kitchen preview; older messages in `docs/ENERGY-AND-SUPPORT.md`) |
| Prepared food | `MealRules`, `MealData`, `IngredientTraits`, `PreparedSandwich`, `PlayerMeals`; `KitchenBlock`, `MessHall`, `CookPot`, `MessHallMenu`, `MessHallScreen`, `MealClient`, `KitchenRenderer`; `docs/MESS-HALL.md` |
| Art | `tools/ui-assets/*.py` generate sprites and textures; the hand-made 3D models come from `docs/art/` via `import_support_gear.py` |

## How to build and test

* `./gradlew --no-daemon --offline build releaseJars` (JDK 17); jars land in `custom-mods/*/build/libs/` (`-standalone` jars are the standalone edition).
* Unit tests: 61 in the beacon module (including three meal-rule tests) and 7 in gun guide. Round 6 validation is listed below.
* Managed cloud build: source `/workspace/.eternal-defense/activate.sh` to use retained JDK 17 and Gradle cache before the normal offline build.
* Kitchen real-server checks: `-Darsenal.messHallTests=true`, `/mess-hall-test` (five tests). Restart fixture: `/mess-hall-fixture seed`, stop/restart the same disposable server, `/mess-hall-fixture check`. Chunk fixture: `/mess-hall-chunks unload`, poll `/mess-hall-chunks check_unloaded`, then `/mess-hall-chunks reload`. Flags are off during normal play.
* Kitchen real-client smoke: `-Darsenal.kitchenSmoke=true` alongside the existing standalone UI smoke flags; hooks exercise both preparation modes, stock a pot through the real container packet, serve through the real bowl interaction, then leave/re-enter home territory.
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

## Round 6 decisions and status

* Implemented Mk I–IV halls (2×2×1), pots (2×1×1), two preparation tabs, portable sandwiches, communal bowl serving, trait definitions, persistent state and live board/HUD. Both editions have vanilla entry recipes; upgrades follow `Economy` (pack plating / standalone energy).
* Pots link within 8 blocks; hall identities and persistent capacity reservations survive unloads. Upgrading retains inventory/output and links. A normal pot break discards its stew; a normal hall break drops contents and its current-tier item. Raid repairs use the existing live block-entity journal.
* Meals use four owned attribute bonuses, 30 minutes of field ticks, 2× home strength and exact freeze/resume. Logout/restart pause time; dimension travel preserves state, other dimensions are field; death clears meals; non-death respawn preserves them. New meals replace old ones. Direct pot serving returns the reusable empty bowl in place.
* Mk I/II/III/IV: 1/2/3/4 pots, 4/8/12/16 servings. All tiers allow two sandwich / three stew effects. No efficiency discount or weapon-handling integration was added. Final painted art and co-op balance remain playtest work.
* Resource generator: `tools/kitchen/generate_resources.py`. Datapack food extension examples, all numeric defaults and lifecycle choices: `docs/MESS-HALL.md`. Field guide has a new Mess Hall page in both editions.

### Recorded validation

* JDK 17 offline `build releaseJars`: passed for all three mods and both editions. 61 beacon + 7 gun-guide unit tests, zero failures.
* Real Forge 1.20.1/47.4.20 + TaCZ standalone servers, with and without Gun Displays: six existing beacon tests and five Mess Hall tests passed in each configuration. New tests cover all rotations and breaking, composition limits, normal sandwich consumption/NBT, competing cooks, blocked output, returned milk bucket, separate diners, no negative servings, saved links/strengths/servings, field/home timers, login, death and paid/free progression.
* Actual stop/restart of the same server world preserved hall inputs, hall/pot identity, stew effects/strengths, 9 servings and exactly 12,345 remaining player field ticks.
* Actual chunk unload/reload across a hall/pot chunk boundary preserved reserved capacity, food and pot identity without forced loading during link validation.
* Real client + dedicated server: Sandwich/Stew screens, server container preparation, pot update packets, real bowl serving, 30:00 frozen home HUD, counting field HUD and a frozen reduced timer on re-entry passed. Screenshots are in `docs/ui/screens/kitchen/`. Final screenshot inspection prompted a shorter input label and a front-mounted menu board so all effects/servings remain visible.
* Raid-damage checks preserve live servings and spent ingredients on restoration. Footprint rebuild retries if neighbors are temporarily unavailable and never overwrites an obstruction.

### Not yet covered by owner playtesting

* Two real human clients / Essential co-op, long raid-and-farming balance, full Create/KubeJS pack upgrade payment, modded food packs, live travel to the End/modded dimensions, all GUI scales/localizations and controller accessibility. Shared-player server tests use distinct fake players; real-client smoke uses one actual client and dedicated server. Do not equate that with a complete multiplayer playtest.
* Models and ration icon are structurally distinct vanilla-texture placeholders. Replace painted art independently of state/footprints. Long translated pot-board text may truncate; chat exposes complete effect data.

## Earlier gaps and unverified items (round 5)

1. **Zone outline flicker** while the cannon fires: never reproduced; the fix (stale limit 4 s to 30 s, own vertex buffer in `BeaconClient`) is a best guess. If the owner still sees it, instrument the client packet timing and the render stage first.
2. Air, Siege and "They are thousands" raids were checked through their mob mixes only; in the small QA arena `RaidSpawns.find` found no ground spawn. Needs a hands-on run (use `/arsenal test-raid <type>`).
3. Not tested: real multiplayer, the End and modded dimensions, the pack prices with real Create/KubeJS items (the QA world only has this mod's parts), migration of an old `arsenal-beacon-exchange.txt`.
4. All prices and drop rates are design estimates; no long playtest. Expect the owner to ask for tuning: change `Economy`, `CannonUpgrades`, `RaidRewards`, `StandaloneBalance`, `ArdentEnergy`/`ArsenalConfig`, then update the numbers quoted in `docs/ENERGY-AND-SUPPORT.md` and the field guide (a test checks the guide).
5. Modrinth page screenshots are placeholders (`REPLACE_ME`).

## Suggested next steps

* Playtest round 6 with a cook and two defenders: stock pots, eat, leave and re-enter home, then raid. Tune `MealRules`, datapack trait weights and `Economy` from that feedback; finish kitchen art separately.
* Run a real session with the special-raid commands and fix spawn placement for flyers, siege and swarm if needed.
* When the owner confirms a round, tag the state in `docs/CHANGELOG.md` and build a new `dist/` zip like the earlier ones (`standalone/`, `pack/`, `README.txt`, `SHA512SUMS`).
