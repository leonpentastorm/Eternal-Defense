# Handoff: where the project stands

Last updated for the **Field Guide pass**, project **0.0.9** (on top of the Mess Hall v6 UI pass 0.0.8), branch **`feature/messhall-ver-6`** (owner override in `CLAUDE.md`), on top of v6 `cfba0ab`. No dev/main merge or push, PR, tag or public release. Fresh worlds per feature until 1.0. Protocol **27-pack / 27-standalone**. JAR versions remain 0.21.0 / 1.1.0; TaCZ 1.1.8-hotfix2 and TaCZ Attributes 1.4 are required as before.

Current specification: `docs/MESS-HALL-V7-UI.md` (UI and feedback); rules, numbers and recipe matrix are still `docs/MESS-HALL-V6-IMPLEMENTATION.md`. Validation: `docs/MESS-HALL-V7-TESTING.md`.

## Field Guide pass decisions (0.0.9)

* **Audience and rule (owner, 2026-10-09):** guide text helps a player who just spawned in: what the mod is about, and the catches to mind when using each system (for example the beacon only comes off with the Recovery Shovel, which resets everything, so choose where to plant it). It is **not** the design document. The owner said the same applies to every guide-style prompt in the UI. Rules and enforcement: `docs/GUIDE-STYLE.md`; one line in `CLAUDE.md`.
* Fourteen pages (`GuideText.IDS`, new `gear` page for Gun Guide and Gun Displays). Each feature is a card: `What it is`, `You get`, `How it works`, `How to unlock`, then `Careful` (red) and `Good to know` bullets. Exact numbers stay in each page's `detail` key, which feeds the Reference tab; the Reference tab was kept, only the page `body` keys were rewritten (the `*.pack`/`*.standalone` body suffix keys were folded into the cards).
* Edition lines: a line starting `[pack] ` or `[standalone] ` is dropped for the other edition (`GuideText.forEdition`). Old suffix keys still work (coop pages, detail keys). `RichText` colours a bullet's leading `Label:`.
* Tests added: `LangKeysTest` card format, density (330 characters / 42 words) and `everyRegisteredFeatureHasAGuideCard` (parses `ArsenalBeacon` registrations; add a block or item and it fails until a card exists); `GuideTextTest`.
* Corrected while auditing: standalone upgrades and the upgrade hover claimed crafted parts although `Economy.beaconAmount` charges Ardent Energy; the plant confirmation understated the shovel (now says it resets the campaign); the start page still listed the old tab names.
* GDD claimed a Gun Displays trophy room counts toward base score. `BaseScoring` has no rule for displays (they count only as ordinary placed blocks); the GDD line was corrected. Nobody has playtested whether that is what the owner wants.
* `tools/qa/QaGuide.java.txt` is the real-client scenario for the guide (`tools/qa/setup-qa.sh <dir> pack QaGuide`).
* Validation: `docs/FIELD-GUIDE-TESTING.md` (95 unit tests; two real-client runs, 17/17 checks each, evidence in `docs/validation/field-guide/`). Bundle: `tools/release/package-field-guide.py --backend-jar <TaCZ Attributes 1.4 jar>` writes `dist/Arsenal-FieldGuide-0.0.9-test.zip` and its `.sha512`.
* **Not done / next:** nobody outside the code has read the new copy as a new player would; ask the owner for a fresh-world playthrough and fix the questions it raises. The Reference tab is still the old exact-numbers material (kept on purpose); cut it if the owner wants a lighter guide. The v2 to v6 server GameTests were still not run.

## UI pass decisions (0.0.8)

* The kitchen is one screen: recipe palette, Gather, Take back, one primary button. `MealRecipeScreen` and the Mix guide are deleted. The recipe order and sandwich/stew mode are stored on the `HallEntity` (`stewMode`, `order`), not in the menu.
* Planning lives in `MealPlanner` (no Minecraft types): `evaluate` is the one composition function behind both `IngredientTraits.compose` and Gather; `plan` searches for foods that make exactly an order from the table plus the pack. Keep the two in step by changing only `evaluate`. Planner tests: `MealPlannerTest` (includes a brute-force completeness check).
* Toggling a recipe is checked against the hall's tier and group rules, not against the pack, so players can plan ahead. Pack-aware status per recipe (`Fit` in the preview: 0 none, 1 blocked, 2 addable, 3 chosen, 4 chosen but no food) is recomputed only when the table, pack, order, mode or tier change.
* Preview schema changed: `Choices` removed; added `Explicit`, `Order`, `Unmet`, `Made`, `Fit`, `Gatherable`, `Covered`, `Limit`, `NoticeSeq/Notice/NoticeKind/NoticeArgs` and a one-time `Catalogue`. Button ids: 5 gather, 6 take back (new); 40+ recipe toggles and 100+ pots as before.
* Dispensers: the Milk Dispenser and Milk Bottle never spend milk when the player has no effects. All messages are action bar plus sound and particles; one chat line when a saved meal is lost.
* Restyled screens and the rules they follow are in `docs/ui/DESIGN-SPEC.md` (new section *Container screens*). Panels must stay within 316 x 238.
* `tools/qa/QaKitchen.java.txt` is the real-client scenario (`tools/qa/setup-qa.sh <dir> pack QaKitchen`, then `xvfb-run ./gradlew :arsenal-beacon:runClient`). It sneaks through the key binding (a forced server flag is overwritten by the client) and waits for rendered frames before each screenshot because the software renderer lags the game tick.

Previous handoff, **Mess Hall v6**, project **0.0.7**, branch **`feature/messhall-ver-6`**, based on v5 `5eebf947cb7b2f07a1fb7f5b50fbd33ce96ecc94`. Owner authorized implementation, build/test, packaging required addon JARs, and pushing this feature. No dev/main merge or push, PR, tag or public release. Fresh worlds per feature until 1.0.

Current specification: `docs/MESS-HALL-V6-IMPLEMENTATION.md`; current validation: `docs/MESS-HALL-V6-TESTING.md` and `docs/MESS-HALL-VALIDATION-0.0.7.json`. Protocol **26-pack / 26-standalone**. JAR versions remain 0.21.0 / 1.1.0. TaCZ 1.1.8-hotfix2 + TaCZ Attributes 1.4 are required on both ends; Attributes is separately bundled in both editions. Milk uses Forge's built-in milk fluid and adds no required mod.

## V6 decisions

* Ordinary effects are the default. `MealRecipeScreen` is a live cookbook and explicit selector using server-owned choices, sources and native container-button intents. First selection starts manual mode; reset returns to ordinary ranking. A selected legendary recipe reserves its two groups and replaces their ordinary bonuses. Recipes cannot overlap groups; missing selected foods invalidate preparation. Menu IDs 40–65 survive Minecraft's signed-byte packet; pot IDs remain 100–103.
* All eleven ordinary effects have same-group vanilla ×2 pairs. Apple additionally supplies Fortune; melon supplies Springy Step. Any three chosen ordinary effects fit six slots. Doubling remains Mk IV stew only, exactly two pips; HOME separately doubles and pauses the 30-minute field timer.
* Hall has eight persistent slots: six mix inputs, output 6, bread-only base 7. Base bread is consumed separately and contributes no Grain/effect. Mixing bread intentionally still grants Grain/Steadiness. Stew locks/hides base and output. Cookbook return clears/rebuilds pot widgets correctly.
* `MilkDispenser` uses an 8,000 mB milk-only Forge FluidTank/capability, one filled-container slot and one empty-output slot. Bucket 1,000 mB, bottle 250 mB; refill validates entire liquid and empty output before mutation. Normal use spends 250 mB, clears saved meal/timer/owned native and TaCZ modifiers, then every potion effect. Portable Milk Bottles share cleansing and return glass. One bucket + four glass bottles crafts four doses. Tank and pending containers persist; breaking drops items and loses liquid.
* Both dispensers stock through empty-both-hands crouch right-click; item/menu/guide text says so. Offhand invocation cannot double dispense. Both halves retain zone checks/anchor forwarding.
* Beacon main tabs: Overview, Upgrades, Workshop Fabrication, Raid break. Header Settings sits beside Guide. All eleven starters fit compact 2-column/6-row cards with visible material icons/counts and full hover costs. Milk price: 8 iron + 4 glass + 4 copper in either edition.
* Owner art imported exactly for shovel, sandwich, bowl and milk. Controller inherits supplied shovel model. `tools/kitchen/generate_resources.py` excludes artist files; original ZIP/provenance is in `docs/art/kitchen-extras-v6/`. Field Guide and cookbook cover current behavior.

## Build and QA

Source `/workspace/.eternal-defense/activate.sh`, run `./gradlew --no-daemon --offline build releaseJars` with JDK 17. Both editions build; 74 beacon unit tests pass (7 Guide tests up-to-date). All 36 server cases pass, and their gameplay classes/data match the delivery JAR. Only renderer, language and gray bread sprite entries changed afterward; the exact delivery JAR passes real milk/bread/meal save/restart and all 13 real-client phases with AppleSkin at the minimum 240-pixel GUI. Exact hashes and entry comparisons are in the validation JSON. Production runtime scripts are `tools/qa/run-v6-server.py` and `tools/qa/run-v6-client.py`. QA flags are off in ordinary launches. Final actual checks and remaining playtests are recorded in the v6 testing document; do not treat interrupted attempts as passes.

## Historical v5 handoff

Last updated for **Mess Hall v5**, project **0.0.6**, branch **`feature/messhall-ver-5`**, based on v4 `73dbebf0bcff7fd8ce33ba9aa05df17fa1c87c97`. The owner authorized implementation, builds, tests, a bundle containing newly required addon JARs, and committing/pushing this feature branch. Do not merge/push `dev` or `main`; no PR, tag or public release requested. Start a fresh world for each feature until 1.0.

Current design: `docs/MESS-HALL-V5-IMPLEMENTATION.md`; actual validation: `docs/MESS-HALL-V5-TESTING.md` and `docs/MESS-HALL-VALIDATION-0.0.6.json`. Protocol **25-pack / 25-standalone**. JAR versions remain 0.21.0 / 1.1.0. Required TaCZ 1.1.8-hotfix2 and TaCZ Attributes 1.4; the backend JAR is supplied separately in both edition folders.

## V5 decisions

* Vanilla food traits give ordinary bonuses; fifteen legendary two-group recipes replace both groups' ordinary bonuses. Six slots and three stew effects; Mk IV stew doubles only with two distinct foods per relevant group (four for legendary). No pips below Mk IV; exactly two pips at Mk IV. Sandwiches remain single strength.
* Animated colored links and shared buff icons identify the server-selected recipe contributors. Hover shows both groups and resulting buff; four contributors are linked for doubled legendary recipes.
* Normal Forge ingredient tooltip receives kitchen information, avoiding the old second overlapping tooltip. Stew hides/disables sandwich output. Menu height 238 fits a 240-pixel GUI.
* Every equipment table upgrades itself; retain four nearby Age indicators and enlarge the current table frame. Beacon Workshop Fabrication owns starter purchases, including hall/pot/bowl dispenser. All upgrade cost hovers use material icon/name rows. Prices live in `Economy`.
* Bowl Dispenser is two tall with one 64-bowl slot; use dispenses one, crouch-use stores. Recovery shovel deals zero damage despite boosts and has intrinsic Knockback II. Creative contents are grouped and show one generic Mess Hall starter.
* Apocalypse author pack 1.1.4_F contains `bf1:ef46` and `bf1:wex` FUEL guns. Since safe compatibility with the owner's exact jam addon was not established, exclude all indexed FUEL guns from catalogues, Supply imports and final craft requests; explicitly reject `bf1:ef46`. Existing third-party guns retain native behavior. The reported unjam crash was not reproduced. The All Rights Reserved pack is local QA only and is not bundled.
* Field Guide regular pages cover v3–v5 food and v4 sunlight/raid adaptation. Kitchen generation no longer rewrites guide text: `en_us.json` owns it. This corrects the old v3 regeneration regression and missing v4 guide update.

## Build and QA

Source `/workspace/.eternal-defense/activate.sh` in this managed workspace, then from the repository root run `./gradlew --no-daemon --offline build releaseJars` with JDK 17. Dependencies must already be cached. Real-runtime scripts are `tools/qa/run-v5-server.py` and `tools/qa/run-v5-client.py`; flags stay disabled in ordinary gameplay. The build passes 74 beacon unit tests (unchanged seven Guide tests are Gradle up-to-date). All 30 server gameplay cases, restart persistence and all eight real-client menu phases with AppleSkin at a 240-pixel GUI pass; current full regression results, hashes and exact commands are in the testing document.

Remaining owner playtests: the matching complete Create/KubeJS modpack, exact Gun Durability/jam addon versions, two-human co-op, long balance sessions and other tooltip/gunpack mods. The standalone runtime is the automated test target. No legacy kitchen-footprint migration is needed under the fresh-world policy.

## Previous handoff (historical v4 and earlier)

Historical handoff for **Mess Hall v4**, project test build **0.0.5**, on **`feature/messhall-ver-4`** based on v3 commit `2ecd1276d58e430dda1ef6a012c82beccc9d57f4`. The branch was created from the verified remote v3 head without discarding local work. The owner authorized the test bundle and a commit/push to this feature branch. No PR or public release is part of this round. `dev`/`main` are unchanged. Active integration branch remains `dev`; the owner selects feature branches for this work.

**Current authorization (2026-10-08):** the owner has now requested builds, tests, fixes and a documented test bundle, superseding the attachment's earlier static-only restriction. Include newly required mod JARs separately in test bundles; v4 requires TaCZ Attributes 1.4. The owner also authorized committing and pushing to `feature/messhall-ver-4`; no merge to `dev`/`main` or public release is requested. Current results are in `docs/MESS-HALL-V4-TESTING.md` and `docs/MESS-HALL-VALIDATION-0.0.5.json`; earlier v2/v3 results remain historical. Until final 1.0 the owner starts a fresh world for every feature; legacy kitchen footprint migration is not implemented in this round. Existing meal save formats remain intact.
The design is described in `docs/GAME-DESIGN-DOCUMENT.md` (keep it updated). Rules and conventions are in `/CLAUDE.md`; this file is the state of the work.

## What the project is

Three Forge 1.20.1 mods for a TaCZ gun pack: **Defense Beacon** (`custom-mods/arsenal-beacon`, id `arsenal_beacon`, package `dev.createarsenal.beacon`), **Gun Guide**, **Gun Displays**.
Each is built twice: the **pack edition** (needs Create and KubeJS) and the **standalone edition** (flavor flag in the jar). The owner plays the pack; the standalone edition is a public Modrinth release (`docs/MODRINTH.md`).
Most of the code is dense single-line style; match the surrounding file.

## Map of the code (beacon mod)

| Area | Files |
| --- | --- |
| Campaign and raids | `ArsenalBeacon` (begin, scan, waves, spawn, fail, commands), `RaidMarch` (blocked-raider detection), `BeaconCombat` (raider goals, straight marching), `CampaignData` (saved state), `RaidBalance`, `RaidSpawns`, `RaidBreaching`, `RaidRewards`, `RaidTypes` (special raids), `HardRaids`, `RaidRespite`, `RaidAdaptation`, `RaiderSunlightMixin` |
| Money | `Economy` (all prices per edition), `SupportRules` (flare prices, timings), `CannonUpgrades` (upgrade table, fire types), `ArdentEnergy` (drops), `ExchangeShop` (+ `ExchangeScreen`), `StandaloneBalance`, `ArsenalConfig` |
| Support system | `SupportData` (per-owner bases), `SupportCalls`, `SupportFlares`, `SupportPlatform`, `SupportCannon`/`CannonEntity`/`CannonControl`/`CannonScreen`, `SupportShop`, `BaseZone`, `ReturnZone`, `SupportCrate`, `SupportHud` |
| Client | `BeaconClient` (screens, zone outline), `SupportClient` (renderers: sign, nameplate, canopy, red parachutes), `BeaconPopups`, `Ui` (UI kit) |
| Network | `BeaconNetwork` (protocol **27**; message 14 `RaidTypes.Marks` (raid exclamation marks and parachutes), 16 open kitchen preview; the v2 meal HUD message 15 is gone; older messages in `docs/ENERGY-AND-SUPPORT.md`) |
| Prepared food | `MealPlanner` (pure recipe logic and the Gather planner), `MessHallMenu`/`MessHallScreen` (one-screen kitchen), `MealGunBackend`, `MealReloadClient`, `MealGunCompat`, `MealReloadContinuityMixin`, `MealBashMixin`, `MealExplosionMixin`, gated `MealGrenadeMixin`; `MealRules`, `MealData`, `IngredientTraits`, `PreparedSandwich`, `PlayerMeals`, `MealEffects` and `MealEffectClient` (vanilla potion effects that show a meal); `KitchenBlock`, `MessHall`, `CookPot`, `MessHallMenu`, `MessHallScreen`, `MealClient`, `KitchenRenderer`; `docs/MESS-HALL.md` |
| Art | `tools/ui-assets/*.py` generate sprites and textures; the hand-made 3D models come from `docs/art/` via `import_support_gear.py` |

## Existing build and test tools

* The owner has authorized v4 testing. Use JDK 17. First resolve the new Curse Maven TaCZ/Attributes dependencies and MixinGradle with network access; `./gradlew --no-daemon --offline build releaseJars` works only once they are cached; jars land in `custom-mods/*/build/libs/` (`-standalone` jars are the standalone edition).
* Existing unit tests: 70 in the beacon module (including nine meal-rule and three raid-behaviour tests) and 7 in gun guide. V4 build/test results and exact scope are recorded separately below; historical v2/v3 and round 6 results remain labeled.
* Managed cloud build: source `/workspace/.eternal-defense/activate.sh` to use retained JDK 17 and Gradle cache before the normal offline build.
* Kitchen real-server checks: `-Darsenal.messHallTests=true`, `/mess-hall-test` (five tests). Restart fixture: `/mess-hall-fixture seed`, stop/restart the same disposable server, `/mess-hall-fixture check`. Chunk fixture: `/mess-hall-chunks unload`, poll `/mess-hall-chunks check_unloaded`, then `/mess-hall-chunks reload`. Flags are off during normal play.
* Kitchen v2 native tests: with `-Darsenal.messHallTests=true`, `/mess-hall-v2-test` runs six food/gun/radius/reload/lifecycle comparisons. Its reload case measures actual TaCZ server ammo transfer and completion, using the same AK-47 baseline/home/field.
* Kitchen real-client smoke: `-Darsenal.kitchenSmoke=true` alongside the existing standalone UI smoke flags; hooks exercise both preparation modes, stock a pot through the real container packet, serve through the real bowl interaction, then leave/re-enter home territory.
* **Real-client QA:** `tools/qa/setup-qa.sh` makes a throwaway copy with the scripted harness `QaWorld` (stored as `tools/qa/QaWorld.java.txt` so it never ships), boots a real client with `xvfb-run` and logs `QA_CHECK PASS/FAIL` lines.
  Pick the parts to run with a flag file (`/tmp/claude-0/qa-r5.flag`: `sign reward exchange upgrade hard raids dbg all`); switch edition through the flavor argument. Tips learned the hard way:
  kill leftover client JVMs by PID (`pkill -x java` misses them, and `pgrep -f` in the same command line kills your own shell); never nest the server-read helper; walls in the harness must not cover the shop column.
  `QaWorld` has rounds 3 to 5 scripts and `scriptR6()` for Mess Hall v3 (flag file `/tmp/claude-0/qa-r6.flag`, parts `sign halls ui effects marks raids air march siege sun`); add a new `scriptR7()` rather than editing old ones. The script has a client run configuration, makes Gun Displays' TaCZ dependency optional and starts with known client options (first launch otherwise stops at the narrator screen); `TACZ_JAR=<path> tools/qa/setup-qa.sh <dir>` puts TaCZ on the classpath so `./gradlew :arsenal-beacon:runGameTestServer` runs the real TaCZ game tests. Run long QA in the background and poll the log.

* **Kitchen UI QA (0.0.8):** `tools/qa/setup-qa.sh /tmp/claude-0/qa pack QaKitchen` copies the tree with the `QaKitchen` scenario (`tools/qa/QaKitchen.java.txt`: hall, pot, both dispensers, beacon and weapon-table screens; parts via `/tmp/claude-0/qa-kitchen.flag`: `kitchen stew legendary dispensers beacon table`), then `xvfb-run -s "-screen 0 1280x720x24" ./gradlew --no-daemon :arsenal-beacon:runClient`. Screenshots land in `run-client/shots/screenshots`; copy them out before the next `setup-qa.sh`, which wipes the tree. TaCZ and TaCZ Attributes are normal dependencies, so the setup only adds a client and a server run with the mixin ref-map remap.
* **Not re-run in this session:** the v2 to v6 Mess Hall GameTests of the original author (`run-v6-server.py` needs his retained runtime). A dev-server replay was tried (Gradle does not forward stdin; RCON gave an unexplained command error) and dropped. Instead `QaKitchen` part `equiv` compares the new `compose` with the v6 one on random tables of real foods (see `MESS-HALL-V7-TESTING.md`). `MessHallV6GameTests` was edited for the new milk and selection rules but its changed cases have not been executed.
* Unit tests: 88 in the beacon module (`MealPlannerTest` adds twelve, `LangKeysTest` now also scans the sources for kitchen and dispenser keys).

## Decisions the owner has made (do not re-litigate)

* One Support Platform and one Support Cannon per player, anywhere in the beacon zone; shovel moves them for free.
* Pack: cannon and beacon upgrades are paid with the pack's own parts; Ardent Energy only buys flares and Exchange goods. Standalone: everything in Ardent Energy, drop rate x1.3.
* Raids: special raids from the fourth raid on, 50 percent chance, also in boss raids; the next type is shown at the end of a raid and in the Overview.
* The beacon itself is the reward chest (no chest block). Bunker Buster never removes obsidian, crying obsidian, bedrock or anything with resistance 1,200 or more.
* The platform stays two blocks tall; its name sign is render-only (no hitbox); the two blocks in front are a no-build return zone.

## Mess Hall v4 decisions and status

* **Sunlight only:** raider daylight ignition is blocked at `Mob.isSunBurnTick`; the blanket `clearFire` tick is removed. Flame arrows and fire traps work subject to native species immunity and trap adaptation. Production mapping uses MixinGradle's generated refmap.
* **Trap adaptation:** current-raid attackers learn eleven tagged damage classes only after positive damage. The first damaging wave stays at normal damage; each later wave gains 25% resistance, reaching immunity after four steps. Each class announces its steps and is saved in `CampaignData.TrapFirstWave`; new raids/reset clear it. Owned/attributed weapons and support cannon damage are excluded. Ownerless custom turrets need tags/markers if they discard attribution. See `docs/RAID-ADAPTATION.md`.
* **Backend:** require leopoko **TaCZ Attributes 1.4** (`tacz_attributes`, exact `[1.4]`, CurseForge project 1113285/file 8470731), clients and server in both editions. Global `gun_damage` and `reload_speed` receive stable meal-owned transient `MULTIPLY_TOTAL` modifiers of amount `b`, preserving other values and yielding `1+b`. Former Firepower hit listener and `MealReloadMixin`/`ReloadClock` accelerator are removed. TaCZ compile/runtime pin is **1.1.8-hotfix2**, project 1028108/file **9037989**. No dependency JAR is embedded.
* **Reload:** one backend timing path; rate changes rebase accumulated progress, and native staged timestamp deltas survive backend restoration via a cached, type-checked field bridge and one native call. Reload clips are selected by animation name across track metadata, including transitions; neutral/finished/swapped runners reset to 1. Custom private script timers/animation conventions remain a compatibility limit.
* **Combat adapters:** Brawler/Heavy Hand retain one native bash hook without adding Strength/Might again. Demolition uses actual explosive kinetic projectile behavior/owner, not an `rpg` category. Optional LesRaisins `lrtactical` 0.4.3 (`[0.4.3,0.4.4)`) scales its shared grenade detonation radius once. Absent/other optional versions stay inactive. Power/fuse/destruction settings remain native. TAA, GunsmithLib, Apotheosis, Apothic Attributes and TaCZ:Accel are not mandatory.
* **Preserved:** recipes, costs, servings, pot capacities, traits/pairs, models, vanilla display, baseline values and existing meal saves/lifecycle. Protocol stays **24**, with no packet changes. No legacy footprint migration is needed under the owner's fresh-world policy.
* **Delivery/verification:** local test bundle with both editions and separate TaCZ Attributes 1.4 JARs. Both editions build; 70 beacon tests pass (7 guide results remain Gradle up-to-date); 20 standalone server cases, optional LesRaisins ownership/radius, a real stop/restart fixture, and native real-client reload speeds pass. Runtime testing fixed the cross-mixin timestamp bridge and dripstone classification. See `docs/MESS-HALL-V4-TESTING.md` and its validation JSON for client status, exact evidence and remaining coverage.

## Historical Mess Hall v3 decisions and status

* **Models:** the artist package (`Create-Arsenal-Mess-Hall.zip`) is installed: halls are **2 wide x 1 deep x 2 tall** (anchor, right cell, and both above), the pot **1 x 1 x 2**; front is the serving side. `tools/kitchen/generate_resources.py` no longer rewrites the model geometry (blockstates, item models, recipes, tags and traits are still generated). The menu board text of the pot is drawn by `KitchenRenderer` on the chalkboard of the upper block.
* **Meal display:** the custom HUD and its packet are removed. A meal is shown with vanilla potion effects, one per bonus (`meal_<id>`, amplifier = strength - 1 + 4 for a pair), endless at home plus a Home Zone effect, finite and counting down in the field. The server (`PlayerMeals.syncEffects`) keeps them in step; the meal is server state, so removing the display only hides it until the next sync. Forge asks for the client extension from inside the `MobEffect` constructor: never read subclass fields there (a real crash found by the real-client QA).
* **Effects:** 16 (nine from v2 plus Might, Agility, Fortune, Recovery, Hearth, Springy Step, Strong Swimmer); every vanilla food has a trait; a stew doubles an effect when two different foods of the same `family` feed it. **Gun effect values were raised** (Firepower 8%, Quick Hands 10%, Brawler 20%, Heavy Hand 15%, Demolition 10%): the v2 values passed the TaCZ server tests but could not be felt. If the owner still feels nothing, the remaining cause is that TaCZ's reload and bash animations keep their own speed (only the server state finishes earlier); remove the five effects if they cannot be made visible.
* **Raids:** raiders march straight at the beacon and dig only when `RaidMarch.idle` (under 1 block in a second). The old breach check ran only when `gameTime%40==0` inside a loop that runs when `clock%20==0`; the two counters have an arbitrary offset, so in most worlds it practically never fired (this also kept siege creepers from digging). Raiders carry a red exclamation mark and are sun-proof; spawns are open surface ground at or above the beacon (`RaidSpawns.openGround`, relaxed after 30 s / 90 s); vexes and phantoms are steered straight in by `RaidTypes.glide` (their own AI is off: a mob without AI is not moved by vanilla, so they are moved by hand); the marks packet is idempotent and resent every second (fixes paratroopers without a parachute: the one-shot packet could arrive before the client knew the mob).
* **Sign post:** the post of the platform sign is drawn without culling (a flat strip vanished when seen from its back).

### Historical v3 validation (prior round; not rerun for v4)

* Unit tests: 70 beacon tests, zero failures (`:arsenal-beacon:test`).
* Real client (QA harness, pack flavor, software GL): halls and pot footprints, collision boxes, kitchen screen in both modes, pair bonus preview, vanilla effects at home and in the field, effect restore after clearing, raid marks and parachutes on every paratrooper (15 of 15), flyers reaching the beacon, zombies digging through a wall, siege creeper digging at the wall and a far one staying alive, sun immunity. Screenshots in `docs/ui/screens/kitchen-v3/`.
* Real TaCZ game tests (QA copy, TaCZ 1.1.8-hotfix2 with its mixins remapped, this mod's mixins registered): **Firepower** on an AK-47 body hit 6.0 base, 6.48 in the field, 6.96 at home (+8% / +16%); **Brawler** x1.20 and **Heavy Hand** x1.15 on the native gun bash in the field, ordinary melee unchanged; **Demolition** M320 radius 4.0 base, 4.4 field, 4.8 home, unrelated explosives unchanged (radius stays 4.0); **Quick Hands** AK reload 2602 ms base, 2366 ms field (+10%), 2168 ms home (+20%). So all five gun effects work against real TaCZ; what the owner could not feel is size, and TaCZ keeps its reload and bash animations at their own speed. One assertion of the unchanged v2 launcher test ("vanilla explosions never enter the TaCZ hook") fails only in the development harness: it finds the explosion radius by reflecting on the field name `radius`, which exists on the vanilla class under development names but not under the production names; the radii themselves are correct.
* Real server game tests without TaCZ: `MessHallGameTests` (5) and `MessHallV3GameTests` (4), 9 of 9 passed, including the new footprints.
* Not verified: ground spawns of raids in a normal world (the flat QA world fails the existing "thin roof" check of `RaidSpawns.environmentSafe`, so `find` returns nothing there; the new `openGround` rule returned true, and the pure rule `allowedDrop` is unit tested); raid behaviour with real TaCZ soldiers; multiplayer; the standalone edition in a real client (same code, other flavor file); the pack with real Create items; balance of the 16 effects and the 2x pair bonus.


## Historical Mess Hall v2 decisions and status

* Stew costs **4/7/10/12 ingredient items** for the existing **4/8/12/16 servings**, tuned in `MealRules.tier`. Reserve one of each composition type, then distribute extra cost across stacks in slot order, round by round. Mk I supports 2–4 distinct types; later tiers 2–6. Sandwich uses 2–3 types, normally one staple and one or two fillings, one item each. All failures remain atomic; every consumed container ingredient returns its remainder.
* Composition scores each distinct item type once, independent of its quantity or duplicate slots. Datapack overlaps merge by maximum; types then sum, strongest effects win, strength remains capped at 3. Added tags/definitions for all five new IDs: beef `firepower`, honey `quick_hands`, pork `brawler`, golden carrot `heavy_hand`, pufferfish `demolition`. Existing saved meals retain their compositions.
* Base FIELD bonuses: Firepower **+5% gun damage**, Quick Hands **+6% reload speed**, Brawler **+15% gun bash damage**, Heavy Hand **+12% gun bash knockback**, Demolition **+5% launcher explosion radius**. Same 1/1.25/1.5 strength scaling, 2× HOME, frozen/reduced field time, replacement, UUID persistence and death clearing as existing meals.
* TaCZ 1.1.8-hotfix2 integration: native `EntityHurtByGunEvent.Pre` for damage; native reload timestamp advancement in `LivingEntityReload.tickReloadState`; only `ModernKineticGunItem.doPerLivingHurt` bash arguments; only `ExplodeUtil.createExplosion` radius for player-owned native kinetic projectiles whose gun index is `rpg` (M320, RPG-7 and compatible launchers). Three narrowly targeted `@Pseudo` mixins and reflection follow existing project conventions. Both editions already require TaCZ; no dependency policy changed.
* Protocol **23-pack / 23-standalone**: cost/availability/per-slot deductions in server preview; all five effect IDs and base amounts in pot board/chat; FIELD/HOME amounts in preview tooltips and actual active amounts in the HUD. The HUD now lists all effects. Both editions share gameplay.
* Unsupported: separate thrown-grenade entities, non-launcher explosive ammunition, custom scripts bypassing TaCZ's native reload clock/bash/explosion paths. Vanilla melee, TNT/creepers and unrelated mod explosives get no meal bonus. Animation clips keep their own playback speed while native server reload state/ammunition complete sooner. See `docs/MESS-HALL.md` for exact integration points.

### Historical v2 validation

* JDK 17 offline `build releaseJars`: passed, all three modules / both editions. 64 beacon + 7 gun-guide unit results, zero failures/errors/skips. Beacon tests executed after v2 changes; unchanged guide results remained up-to-date.
* Real Forge 1.20.1 / 47.4.20 + TaCZ 1.1.8-hotfix2 servers, with and without Gun Displays: six baseline, five original Mess Hall and six v2 tests passed in each configuration. Covers all tier costs, per-unit container returns, insufficient/competing cooks, duplicate stack scoring, vanilla sources for all effects, persistence serialization and player ownership/home transitions.
* Identical native AK body hit: 6.0 baseline, about 6.3 field and 6.6 home. Native bash: 8 baseline, +15% damage and +12% direct bash knockback in field; ordinary melee damage/knockback unchanged. The knockback check captures the actual native bash impulse, since vanilla damage can add its own separate impulse.
* Native AK reload/ammo transfer: baseline/home/field 2627/2362/2487 ms with displays; 2641/2340/2499 ms without. Measurements begin after fixture setup to exclude world generation stalls, and allow native server-tick quantization.
* Native M320 explosion radius: 4 baseline, 4.2 field, 4.4 home. Explosive rifle utility call, non-TaCZ exploder, actual vanilla TNT/creeper radii stay unchanged. Transient test event capture cancels blasts after measuring; production compatibility uses no global radius event handler.
* Actual client + dedicated server: new food cost / gun-effect previews, real container preparation, 16 pot servings, reusable bowl serving, all three gun bonuses in the HUD, home freeze, field countdown and reduced-duration re-entry passed. Visual inspection found unlabeled paired field/home amounts; final preview tooltips label each state explicitly.

Recorded checks and JAR hashes: `docs/MESS-HALL-VALIDATION-0.0.3.json`. Screenshots: `docs/ui/screens/kitchen-v2/`. Full two-human co-op, Create/KubeJS pack gameplay, custom gunpack scripts, third-party thrown grenades, long balance sessions and exhaustive GUI scales remain owner playtests. Native gameplay cases use separate server fake players; UI smoke uses one actual Minecraft client on a dedicated server.

## Round 6 decisions and status (historical 0.0.2)

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

* Install the v4 test bundle in a fresh world; include its separate required backend JAR. Commit/push is authorized only for the v4 feature branch.
* Follow the remaining playtests in `docs/MESS-HALL-V4-TESTING.md`, especially the full Create/KubeJS pack, two defenders, custom gunpack reload lifecycle and turret attribution.
* Balance pairs and gun effects during a later survival playtest (strength 3 paired Vitality at home reaches +24 health); include marching, flyers, siege and paratroopers. Tune `MealRules`, trait weights and `Economy` from that feedback.
* Merge/tag/public release remains a separate owner-directed step; the v4 ZIP is a local test artifact.
