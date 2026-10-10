# Optimize Path (project 0.0.15)

A pass over the code that runs while the game is played (server ticks, events, entity and block-entity ticks, client ticks) in all three mods: Defense Beacon (`arsenal-beacon`), Gun Guide (`arsenal-gun-guide`) and Gun Displays (`arsenal-displays`). The rule of this round: **no feature changes**. Every change gives exactly the same result as the code it replaces; only the work done to get there is smaller. Rendering, culling and HUD drawing were left alone on purpose (they are hard to verify here; see *Not touched*).

What was tested, and the measured numbers: `docs/OPTIMIZE-PATH-TESTING.md`.

## What runs, and how often

| Code | How often | Notes |
| --- | --- | --- |
| `ArsenalBeacon.tick` (server tick) | every tick | Campaign state machine; per-raider loop once a second during a raid. |
| `BaseSurvey.tick` | every tick between raids while players are online | Reads up to 4,096 blocks a tick until the zone is covered (at most 49 x 49 x 65 = 156,065 positions), then scores the base and waits 400 ticks. |
| `BaseScoring.analyze` | every finished survey (about every 20 s between raids) and once at every raid start | Scores tens of thousands of blocks in one go: the biggest single piece of work the mod does. |
| `BeaconCombat.tick` | every tick | Keeps the beacon's objective; once a second attaches goals to hostile mobs near the base. |
| `BeaconNetwork.syncNearby` | once a second | Builds and sends the beacon state to every player. |
| `RaidTypes.Events.tick` (`LivingTickEvent`) | every living entity, every tick | Paratrooper fall and glider steering. |
| `PlayerMeals` (`PlayerTickEvent`) | every player, every tick | Meal timer and effects. |
| `StructureMigration` (`LevelTickEvent`) | every level, every tick | Two queued chunks per tick. |
| `SpecialForcesRaids.cleanup` | once a second | Looked at every entity of the Overworld. |
| Event listeners (death, damage, joins, block place/break, explosions, mob griefing, projectile impacts) | per event | |
| Client ticks (`MealReloadClient`, `BeaconStartup`, `GuideClient`, `ClientDefaults`) | every client tick | |

## Changes (all behaviour-preserving)

1. **A listener that was called for every event in the game.** `BeaconCombat.gunBlockImpact` was subscribed to Forge's base `Event` class and compared each event's class name with TaCZ's `AmmoHitBlockEvent`. Forge therefore called it for *every* event posted on the main bus, on the server and on the client (each entity tick, each render stage, each input, each sound), only for it to return at once; when it did match, it looked the two getters up by reflection on every hit. TaCZ is a required mod in both editions, so the listener now takes TaCZ's event type directly (still only TaCZ's own class, not a subclass) and calls the getters directly. Measured in a nearly empty flat world: about 3,400 events per tick reached the old listener at about 17 ns each.
2. **Base score.** `BaseScoring.analyze` built each block's id string, ran a dozen string searches on it and walked its class hierarchy *for every block it scored*, and for every floor candidate it checked the distance to *every* light and piece of furniture (a stream over the whole list). Now:
   * what a block counts as (its id, terrain, furniture, machine, detail) is worked out once per block type and kept (`BaseScoring.Kind`); the parts that depend on the block state (air, fluid, light level, solidity, block entity) are still read from each state;
   * lights and furniture are kept in a grid of 12-block cells (`BaseScoring.Anchors`), so "is one within 12 blocks?" looks at the 27 cells around a block instead of the whole list. The answer is the exact same squared-distance test (`<= 144`, block corners, like `BlockPos.distSqr`).
   * the floor search runs its cheap tests (inside the zone, a block above?) on the packed position first, so the many blocks that have a block above them are skipped without creating any position objects.
   The blocks are visited in the same order as before, so even the floor cap (the scan stops after 72 + 180 x core floors) stops on the same floor. Measured on a 49,221-block Mk-4 base: **about 64 to 66 ms before, 21 to 26 ms after** (2.6 to 3x), identical scores on 73 test bases.
3. **Resource path check without a regular expression.** `SoundResourcePaths` (called by ModernFix's resource cache for every pack file while resources load, and for every gun sound) used `String.matches("[a-z0-9/._-]+")`, which compiles a pattern on every call. It is now a plain character loop with the same answer.
4. **Special Forces corpse sweep.** Once a second the raid code walked every entity of the Overworld looking for defeated Special Forces soldiers. Without the optional Special Forces mod (`taczsf`) no soldier can exist, so the sweep is skipped; with the mod installed it runs as before. `SpecialForcesRaids.soldier` also answers "no" without a string comparison when the mod is absent.
5. **Deaths.** `ArsenalBeacon.died` looked up and stringified the entity type id of every mob that died in the Overworld before checking whether a raid was running. The id is now only looked up for deaths inside the zone during a raid (same test: namespace `tacz_turrets`).
6. **Zone size lookups.** `Rules.radius`, `below`, `above`, `maximumHealth`, `bossHealth` and `platformScore` built a new array on every call; `radius`, `below` and `above` are asked by every "is this inside the zone?" test (block events, mob griefing checks, the raid loop, the base score). The tables are now built once. `CampaignData.inside(x,y,z)` answers the zone test without a position object (`inside(BlockPos)` uses it).

The new code paths are covered by `OptimizePathTest` (unit), `SoundResourcePathsTest` (unit) and `OptimizePathGameTests` (real server, opt-in `-Darsenal.optimizeTests=true`, command `/optimize-path-test`), which runs each faster path next to a verbatim copy of the old code and requires the same answer.

## Looked at and left as it is

| Code | Why it stays |
| --- | --- |
| `BaseSurvey.tick` and the raid snapshot `scan()` (4,096 block reads a tick) | Already bounded per tick and only busy for about 40 ticks of every 440. A faster loop would have to change which chunk checks are made (a chunk that is still loading is waited for today); not worth the risk for the gain. |
| Snapshot and survey maps (`HashMap<Long,BlockState>`) | A primitive map would save memory, but the base score walks the map in its iteration order and stops at the floor cap: a different map would change which floors are counted in very big bases. Kept for exact scores. |
| `RaidTypes.Events.tick` (every living entity, every tick) | Two NBT flag reads per entity per tick; cheap, and the order of the checks matters for parachutes. |
| `PlayerMeals.tick` | Returns at once for players without a meal; with a meal it does one zone check a tick. |
| `BeaconNetwork.syncNearby` (state packet once a second) | Builds a cost plan for 12 lists per player per second (well under a millisecond). Sending only on change would make the client HUD think its state is stale (`STALE_TICKS`), so the packet keeps its rhythm. |
| `StructureMigration` | Two chunks a tick, palette check first. |
| Block-entity tickers (Support Cannon) and support entities (flares, parcels, gates) | Small, and they only run while such a thing exists. |
| Mixins (`RaiderSunlightMixin`, meal mixins) | One flag read or one multiplication each. |
| Gun Displays | No ticking code: racks are chunk-baked furniture with no ticking block entity. |
| Gun Guide `GuideClient.tick` | One cached reflective call a client tick. |
| `MealReloadClient.tick` | Allocates one small set a client tick; negligible. |

## Not touched (rendering), candidates for a later round

These run every frame on the client. They were not changed in this round because a mistake there shows up as a visual glitch or a culling problem that cannot be checked reliably in this environment:

* `BeaconClient` HUD (`hud`) and the zone outline / damage outline (`RenderLevelStageEvent`): both handlers are called for every render stage of every frame; an early return on the stage they draw in would save work.
* `SupportClient.draw` (`RenderLivingEvent.Post`, owner nameplates): runs for every living entity drawn.
* `BeaconPopups` and `BeaconAlerts` (drawn on every screen render as well as in the HUD).
* `KitchenRenderer`, `DisplayRackClient`, the cannon and gate renderers.

Each of these should be measured with a profiler (for example Spark) in a real pack before changing anything.
