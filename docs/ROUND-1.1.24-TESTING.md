# 1.1.24: what was tested

The owner's request after 1.1.23, on `feature/power-defense`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-17). Evidence: `docs/validation/release-1.1.24/` (check list and pictures).

## What was asked, and how it was read

> "Oh yeah, the spotlight is another defense tab object in the beacon fab tab, emits strong light in a dirrection, uses power and can be interact with to angle in 45 degree each turn, making it able to face 8 dirrection"

* **"Another defense tab object in the beacon fab tab."** The Industrial Spotlight is made at Workshop Fabrication (the beacon screen's Workshop Fabrication tab), last in the "Defense & defense logistics" group. Cost: 6 iron ingots, 2 copper ingots, 2 glass and 2 lanterns. The Defense Depot no longer sells it (it cost 24 coins there in 1.1.23).
* **"Emits strong light in a direction."** While lit it keeps invisible light blocks (light level 15) on the ground 3, 7, 11, 15, 19 and 23 blocks out along the way it faces. Each is placed only if the lenses can see that spot; the first wall or hill in the way ends the beam. The stand itself gives light 7. The renderer draws glowing lenses and two soft cones of light. The drawn cones fade to a quarter in full daylight; the light blocks do not change.
* **"Uses power."** As in 1.1.23: lit only while a powered Power Pole reaches it, 2 stress per RPM.
* **"Interact with to angle in 45 degree each turn, making it able to face 8 directions."** It is placed facing the way the player looks. A right-click turns it 45 degrees clockwise; a sneaking right-click turns it back. The line above the hotbar names the way it faces, and says when it has no power. The placement preview turns with the player.

## Unit tests

Eternal Defense **568 pass** (562 before), Gun Guide 8, displays 7.

* New `Release1124Test` (6 tests):
  * 8 directions, 45 degrees apart, clockwise from north;
  * it faces the way the player looks (every yaw from -720 to 720 in 7.5 degree steps lands within 22.5 degrees);
  * the beam's columns: 6 of them, in front, nearest first, on the beam's line, 3 to 23 blocks out;
  * made at Workshop Fabrication (last in the Defense group, 6 iron, 2 copper, 2 glass, 2 lanterns), not sold at the Depot;
  * every number the guide quotes;
  * the light block: replaceable, no drops, never `.air()`, goes out when nobody claims it; the base survey skips it; the renderer and the preview turn the model; one blockstate model; the light block's blockstate.
* Changed: `Release1123Test` (the Depot no longer sells the spotlight), `LangKeysTest` and `Round0041Test` (the light block has no item and is not an installation).

## Real client

`tools/qa/QaRelease1124.java.txt` (Create in the run). A flat world, a Mk IV beacon zone, a Shaft Dynamo on a creative motor and one Power Pole. The spotlight stands 6 blocks south and 3 east of the pole.

### Run history

* **Run 1, 37 of 47.** Every failure was in the harness. After the night pictures it clicked the spotlight from 9 blocks away, and the server ignores a click from that far. The later checks then expected the wrong direction. Two checks expected the wrong thing: the beam's first light block also lights the block beside the stand, and the grid lists the spotlight's stress, not a turret. The night pictures were taken before the client had midnight.
* **Run 2, 46 of 48.** Two harness timings: one check ran 10 ticks after a stone was placed, before the spotlight's next 2-second check; the stray-light check expected light 0 where the beacon's own light still reaches (level 1). Seen in the pictures: in daylight the drawn beam was a bright white cone. **Changed:** the drawn cones fade to a quarter in full daylight under open sky (full strength at night or out of the sky's light).
* **Run 3, 48 of 48.** The client log warned that the light block had no model. **Fixed:** its blockstate uses vanilla's light block model (nothing is drawn).
* **Run 4, 48 of 48**, no model warning. The check list and pictures are from this run.

### What run 4 shows

* **Workshop Fabrication**
  * The Defense Depot no longer sells it.
  * The tab lists it last in the Defense group with 6 iron, 2 copper, 2 glass and 2 lanterns (`r1124_workshop_defense`).
  * Making one takes exactly those (7 iron in the pack leaves 1) and says "Place it inside your beacon zone or a FOB's zone." Without the materials nothing is made.
* **Placed and powered**
  * Placed by a player looking east: it faces east, 2 blocks tall, and lights up in the pole's reach.
  * 6 light blocks on the ground, 3, 7, 11, 15, 19 and 23 blocks east, invisible, with no collision and no outline.
  * Block light 15 at 23 blocks out, 13 at 13 blocks, 0 at 12 blocks behind and 12 blocks to the side. The stand gives light 7.
  * The grid counts 2 stress per RPM for it.
* **Turning**
  * 8 right-clicks turn it through all 8 ways and back to east. After each, its light blocks are exactly the 6 of the new way, none of the old ones are left, and the line above the hotbar says "on, facing" the new way.
  * A sneaking right-click turns it back one step.
  * Night pictures: facing north-east, south-east and east (`r1124_spotlight_northeast`, `_southeast`, `_east`). The lit band on the ground follows the beam; behind the stand stays dark.
* **Walls and blocks**
  * A stone wall 3 high 10 blocks out: only the ground at 3 and 7 blocks stays lit, and it is dark 23 blocks out (`r1124_spotlight_wall`: the drawn cone ends on the wall). With the wall gone, all 6 come back at the next check.
  * A stone placed where a light block is replaces it, and the beam then lights the top of the stone.
* **Power**
  * The motor stops: it goes dark, every light block goes and the ground ahead is dark. A right-click then says "off, no power, facing south-east. Place it within 8 blocks of a powered Power Pole." (the click also turns it).
  * The motor turns again: lit again, with the beam of its new way.
  * One placed out of the pole's reach stays dark with no light blocks.
* **Clean-up**
  * Breaking the spotlight removes every light block of its beam and its upper cell.
  * A light block placed by hand, owned by no spotlight, lights its spot (15) and turns back to air at its next check, the light as it was (1, from the beacon).
* **Preview and daylight**
  * Holding a spotlight and looking north-east, the preview is drawn turned north-east and the placed spotlight faces north-east (`r1124_spotlight_preview`).
  * In daylight the lit spotlight shows a faint cone (`r1124_spotlight_day`).

## Network

Protocol stays **43**: no packet of the mod's own channel changed (the rotation and the lit state are block state, sent by Minecraft). A new block (the light block) means 1.1.23 and 1.1.24 do not mix (Forge refuses).

## Not tested

* Save and reload with a lit spotlight, and chunks unloading under its beam.
* Many spotlights at once, two beams crossing.
* Caves, water, rain, slopes and steps, snow; shader packs.
* A raid at night with spotlights: whether mobs stay out of the beam's light.
* FOB zones, a server, a second player.
* Light blocks removed by pistons or explosions (they come back at the next check by design, not seen).
* How it looks to the owner and whether the price is right.
