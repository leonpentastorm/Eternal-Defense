# 1.1.6: what was tested

The owner's 1.1.6 list: Badass mobs slower the bigger they are (creepers 1.5 times as much), a Badass creeper's blast growing with its size. Branch `feature/raid-control`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-15). Evidence: `docs/validation/release-1.1.6/` (the check lines of the final run and its screenshots).

## Unit tests

`:eternal-defense:test` 470 pass, 0 fail. New `Release116Test` (3 tests): the slowdown by level (5 to 25%, a creeper 7.5 to 37.5%, never past V); the blast radius by size (5, 7, 9, 12, 15 from a vanilla 3; a creeper that is not bigger keeps its own); `make` applies both through the elite speed id the raid's balancing keeps and the creeper's own radius field; the guide and the elite command quote them. `LangKeysTest` caught the guide's Badass line at 356 characters (limit 330); it was shortened. `:eternal-defense-displays:test` 7 pass.

## Real client

`tools/qa/QaRelease116.java.txt`, the same mods and gun packs as 1.1.5 (Create, LesRaisins, TaCZ with Apocalypse and TRIS-dyna, the displays). A new flat world, survival, Normal; window 1280 x 720. Final run (run 2): **8 of 8 checks pass**.

* **By command** (`/eternaldefense elite badass 1..5 minecraft:zombie` and `minecraft:creeper`): zombies 5, 10, 15, 20 and 25% slower (speed 0.2185 to 0.1725 of 0.23); creepers 7.5, 15, 22.5, 30 and 37.5% slower (0.2313 to 0.1563 of 0.25); creeper blast radius 5, 7, 9, 12 and 15. Each reply says how much slower, and a creeper's blast radius (picture of both rows).
* **In a raid at level 3** (no waves of its own), a Badass V zombie and a Badass V creeper raider after four seconds of the raid's balancing: still 25% and 37.5% slower (the balancing set the creeper's base to 0.23; it moves at 0.1438), blast radius still 15.
* **Craters** (pads 31 x 31, 12 high, outside the zone, each creeper lit on top of its own pad): stone (blast resistance 6): a plain creeper broke 1 block, a Badass I 9, a Badass V 62; oak planks: a plain creeper 9, a Badass V 235 (picture). Run 1 had stone only: 1, 8, 61.

## Jars

All four jars: no test, QA or note files; PNGs hold image chunks only; no tool, assistant or session names (the one match is TaCZ's "PEQ6 ILLM" laser, as before).

## Network

Protocol **42**, unchanged.

## Not tested

* A Badass creeper arriving by itself in a siege raid and blowing up inside the base's zone (the blocks go through the damage journal, as any raider's blast; not run this round); players caught in a 15-radius blast (the test player could not be hurt).
* A charged (lightning) Badass creeper (vanilla doubles its blast again).
* A world saved and loaded with a Badass creeper (the radius is the creeper's own saved field).
