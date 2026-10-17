# 1.1.22: what was tested

The owner's report after 1.1.21, on `feature/power-defense`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-17). Evidence: `docs/validation/release-1.1.22/` (logs; no pictures, see below).

## What was asked, and what was found

* **"Make mob spawn out earlier, in my testing the wave mob only spawn after 25-30s of the wave starting."**
* Measured first, in a real client on generated terrain (seed 42, simulation and render distance 12). A Mk I beacon was placed and a raid started through the mod's own start, snapshot and wave code. Each raider was logged with its spawn time, its distance, and when the first raider came within 32 blocks of the beacon.
* **Raiders were not spawning late.** The first one appeared 0.05 s into the wave and one followed every second, as the code says.
* **They spawned far away and walked a long way.** They landed 79 to 174 blocks from the beacon and walk about 2 to 3 blocks a second, so the first came within 32 blocks after 19.7 s (beacon on the ground) and 33.75 s (beacon on a plank floor one block up). That is the 25 to 30 seconds the owner saw. (`spawns-1.1.21-random-spot.txt`)
* There were two causes in `RaidSpawns`:
  * `find` walked the rings from the edge of the active chunks (168 blocks at simulation distance 12) inward. It also preferred a clean way in at any distance over a nearer one with a step in it.
  * A spawn had to be at or above the beacon's level for the first 30 seconds of a wave. A survey of 72 spots per ring found that rule ruled out 85 to 90 percent of every ring around a beacon on a rise. The good ground near the beacon was 4 to 17 blocks below it. (`spawns-survey-height-rule.txt`, taken with the intermediate build that already had the closer, nearest-first rings)

## What changed

* **Closer clearance.** `CLEARANCE` is 32 blocks past the zone edge (64 before). This also moves the staging ring for gate exits of stuck raiders.
* **Nearest ring first.** `spawnRings` lists the rings from the zone radius plus 32 outward, in steps of 16, with each candidate up to 16 farther. The nearest ring with good ground wins, and the corridor score only orders candidates within the same ring.
* **Height allowance.** `allowedDrop` lets spawn ground sit 20 blocks below the beacon at once and 30 after 30 s (before: 0, then 4 after 30 s, then 10 after 90 s). The upper limit (24 above) and every other ground rule are unchanged: open sky, no overhang, a flat 5 x 5, no water, nothing built within 6 blocks, inside the border and in entity-ticking chunks.
* **Gates nearest first.** `gateRings` now runs from 12 blocks past the zone outward (outermost first before).
* **Player distance.** A fresh raider never spawns within `SPAWN_PLAYER_DISTANCE` (16) of a player. Closer spawns made this possible; the old 64-block clearance rarely met a player.
* **Text.** The Field Guide's raids page says enemies come from open ground about 32 blocks outside the zone and never right next to a player. The gate chat line no longer says "far out".

## Unit tests

`:eternal-defense:test` **551 pass** (546 before):

* New `Release1122Test` (5 tests): the rings nearest first for Mk I and Mk IV zones and small simulation distances; the height allowance (and never lower than a gate may be); gates nearest first and still closer than natural ground; 15 blocks from a player is refused and 16 allowed; `find` sorts by ring and then violations; the guide quotes `CLEARANCE`.
* Changed: `RaidBehaviourTest` (the new height allowance) and `RaidFixesTest` (gate rings ascending).

## Real client

`tools/qa/QaSpawnTiming.java.txt` (Create in the run). Night, mob spawning off, the player 4 blocks from the beacon. It runs a raid at three fixed spots on the same seed:

* **A-ground:** x -30, z 0, beacon on the ground at y 67.
* **A-floor:** the same spot on a 9 x 9 plank floor, beacon one block up.
* **B-high:** x -39, z -6.

Checks at each spot: the first raider spawns within 2 s, none spawns closer to the zone than the clearance, none spawns within 16 blocks of the player, and one comes within 32 blocks of the beacon. Between spots the script clears the repair list, which is not under test. The old code ran with two harmless helpers added so the script compiles (`spawnRings`, `SPAWN_PLAYER_DISTANCE`), and with its own rules.

### First raider within 32 blocks of the beacon

| Spot | 1.1.21 (old code) | 1.1.22 |
| --- | --- | --- |
| A-ground | 19.7 s, 33.4 s, 10.8 s | 3.8 s, 3.6 s |
| A-floor | 33.75 s, 13.2 s (run 1 never started: see below) | 8.75 s, 2.8 s |
| B-high | 17.7 s (ground read y 73 that run) | 5.35 s, 6.6 s (ground y 80) |

### Spawn distance from the beacon

* **1.1.21:** 72 to 174 blocks, all at or above the beacon's level.
* **1.1.22:** 24 of 28 spawns at 40 to 65 blocks; the other four at 69, 72, 85 and 123. Up to 17 blocks below the beacon at B-high, where the raiders climbed the hill and arrived first after 5.35 and 6.6 s.

### Checks and run notes

* 1.1.22 passes all 12 checks in both runs.
* 1.1.21 passes all 12 in run 2 (`spawns-1.1.21-fixed-sites-run2.txt`). In run 1 the first raid left a repair list behind, and the mod refused the next two raids ("Complete pending restoration") until the script cleared it.
* The ground height at B read 73 in one old-code run and 80 in the other runs on the same seed; the reason was not found (perhaps a tree there). B before and after are therefore not the same height.
* The spawn ground varies run to run, because candidate angles are random. That is why the old-code times range from 10.8 to 33.75 s on the same spot.

### Pictures

The screenshots taken at "first raider visible" show only the night sky (the camera looked up), so none is kept.

## Network

Protocol stays **43**: no packet changed and nothing new is saved.

## Not tested

* A full raid to the end with the closer spawns: more waves, bigger raids and special raids. Paratroopers drop over the zone and were not changed.
* A Mk IV zone (radius 32) in game; its rings are covered only by unit tests.
* A base in a forest, on water or in mountains, where gates take over.
* Stuck raiders' gate exits at the closer staging ring.
* A server, and a second player standing out in the field (the 16-block rule is covered by unit tests only).
* Whether raiders this close feel right in play.
