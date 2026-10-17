# 1.1.15: what was tested

The owner asked: "Raid improvement: Mob path finding prioritize using raider bridge that other raider already built, adding more to it when needed instead of building whole new bridge. When they get submerged in water they imidiattely start teleporting if it calculate that it will takes more than 5s to swim toward the target". On `dev`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-17). Evidence: `docs/validation/release-1.1.15/`.

"Teleporting" was read as the Raider Gate that already exists: the raider stands still for 8 seconds in a red ring, then comes out of a gate on land outside the zone.

## What changed

* **Bridges.** A raider at water, lava or a chasm first looks for a bridge of this raid.
  * The bridge must start within 16 blocks, be at the level of its feet (one block up or down), lead toward its target, and be wide enough for it.
  * It walks to the bridge's start, walks the planks already down, and lays up to 24 more from the end when the bridge does not reach land yet.
  * Only when there is no such bridge does it start a new one. The raid's limit of 384 planks only stops new bridges.
* **Water.** Once a second, a raider that swims (head under water, or not standing on the bottom) goes to a gate at once, when either:
  * the water toward its target before land would take more than 5 seconds at its best swimming speed (vanilla: 1.6 blocks a second per unit of speed; a zombie 0.37 blocks a second, so 2 blocks of water or more), or
  * it has already been in the water for 5 seconds.
* Not while it attacks its target, not while it can hit it, and not within the grace window after a gate. Drowned and water mobs swim on.
* The second rule was added because raiders were measured to make almost no way in water (below), so a swim that looks short can still last.

## Unit tests

`:eternal-defense:test` **510 pass, 0 fail**. The new `Release1115Test` has 3 tests:

* **Which bridge:** one 8 or 16 blocks along the shore is used; 16.5 blocks is too far. A bridge leading away is not used, and neither is a one-plank bridge for a giant. A small raider takes a giant's wide bridge. A raider already past a bridge's start does not turn back.
* **The swim rule:** 1 block of water to the shore is swum by a zombie, 2 are not. 5 seconds exactly is still swum. Five seconds in the water mean a gate. The grace window holds.
* **Wiring and text:** an existing bridge before a new one; planks already down are walked; land ends the job; the bridges are forgotten with the raid; the water rule's conditions; Drowned and water mobs; wading is walking. The guide quotes the real numbers, including the zombie's 0.37 blocks a second.

`:eternal-defense-gun-guide:test` 8 pass. `:eternal-defense-displays:test` 7 pass.

## Real client

`tools/qa/QaRelease1115.java.txt` ran with `QA_CREATE=1` (Create loaded) in a new flat world at 1280 x 720. A 10-wide moat (2 deep) runs across the raiders' way to the beacon; a lake (3 deep, 51 x 13) lies 46 blocks south.

### Run history

* **Run 1: 5 of 12.**
  * Three were the harness's own mistakes. The lake was dug 4 deep and took the flat world's bedrock with it, so the near zombie fell out of the world. The swim checks expected the estimate within 35%, but raiders measured 0.00 blocks a second.
  * One was real: the second zombie did not finish the first one's bridge and ended up in the water.
* **Run 2: 7 of 12.** Lake 3 deep. The second zombie still went into the water. It skipped the planks already down before it had reached the bridge, so it walked straight at the last plank, across the water. Fixed: it walks to the start (or the last plank it reached) first, and only then along the planks.
* **Run 3: 9 of 12.** The bridge passed.
  * Raiders measured 0.01 to 0.02 blocks a second in the water against 0.37 to 0.40 estimated: they barely make way. So the estimate is only a best case.
  * The near raider sank to the lake floor at the shore and never climbed out.
  * Added: 5 seconds in the water also mean a gate. The checks were changed: the measured speed must not beat the estimate, and the near raider must climb out or go to a gate after 5 seconds.
* **Run 4: 12 of 13.** The exit check wanted the raiders standing on the ground the moment they came out; they arrive a little above it. It now checks that they are at ground height (at most half a block below) and out of the water.
* **Run 5: 13 of 13.**
* **Run 6: 13 of 13**, with the camera fixed for the pictures (in run 5 the player fell from the camera spot). The results below are from run 6.

### Bridges

* Zombie a, 30 blocks east of the beacon, lays 4 planks straight toward it and is taken away.
* Zombie b, 8 blocks south of a's start, walks along the shore to a's bridge, along its 4 planks, and lays the other 6 to the far shore. It lays no plank of its own row, swims nowhere, and uses no gate.
* Zombie c, 10 blocks north, walks to the same bridge and crosses without laying a plank. The raid records 10 planks: one bridge.
* Picture: one bridge across the moat.

### Water

* **Speed.** A zombie raider and a skeleton raider were kept in the lake (a long grace window held the gate off) and measured over 5 seconds:
  * zombie: 0.02 blocks a second, against 0.37 estimated;
  * skeleton: 0.00, against 0.40;
  * a plain zombie (not a raider) that had a villager as its target: 0.02.
* **Far out.** A zombie and a skeleton raider 11 blocks from the shore start their gate after 10 ticks (half a second), reason "water". Picture: both channelling in the lake. The portal particles show; the red ring is at their feet, under the surface.
* **Near the shore.** A zombie raider 1 block from the shore gets no gate at 3 seconds (the swim looks short).
  * It sank to the floor at the shore wall and worked there; the harness prints any work job as "bridge".
  * It did not climb out. After 111 ticks (5.5 seconds) it went to a gate, reason "water".
* **Out.** After the channel both far swimmers come out of their gates on land, outside the zone: 1 gate each, out of the water.

## Jars

* All four jars (both builds of the main mod, the gun guide, the displays) hold no test, QA or note files.
* No tool, assistant or session names appear in text, data or class files. The one match is TaCZ's "PEQ6 ILLM" laser in the catalogue, as before.

## Network

* Protocol **43**, unchanged: no packet changed.
* No new item, block or menu, so 1.1.14 and 1.1.15 should mix. Not tried.

## Not tested

* A full raid from the raid timer, with many raiders at a real river or lake: the harness placed single raiders.
* Bridges over lava and over a chasm with the reuse rule (only water was tried).
* A bridge broken by a player while a raider uses it.
* Two raiders extending the same bridge at the same moment.
* A giant on a wide bridge.
* Raiders with other mods' swimming changes (the swim speed attribute is read but no such mod was loaded).
* The owner's full pack, a dedicated server, a second player.
* The Field Guide pages in the client: their text is covered by the unit tests.
