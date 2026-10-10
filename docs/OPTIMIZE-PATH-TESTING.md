# Optimize Path (project 0.0.15): what was actually tested

Environment: Linux container, JDK 17, Forge 47.4.20 development runtime with TaCZ 1.1.8-hotfix2 and TaCZ Attributes 1.4, a real Minecraft client under Xvfb with software OpenGL (flat world, pack edition, Create, KubeJS and ModernFix absent). What changed and why: `docs/OPTIMIZE-PATH.md`. Evidence: `docs/validation/optimize-path/gametests-and-benchmarks.txt`.

## Automated, in the build

* `./gradlew --no-daemon --offline build releaseJars` (JDK 17): all three modules and both editions build; **146 beacon unit tests, 0 failures** (3 new), 7 Gun Guide tests, 0 failures.
* `SoundResourcePathsTest.thePlainCheckAnswersExactlyLikeTheOldPattern`: the new character check gives the same answer as the old pattern `[a-z0-9/._-]+` for the empty string, for **every one of the 65,536 single characters**, for 50,000 random paths built from letters, digits, the allowed punctuation and characters that must fail (capitals, `:`, `\`, space, accents, a surrogate pair), and for real gun-pack paths. The existing sound-path tests still pass.
* `OptimizePathTest`: the light/furniture grid answers "anything within 12 blocks?" exactly like the old scan of the whole list: 40 random clouds of up to 120 anchors around random origins (negative coordinates included) with 400 random queries each, plus the edges (12 blocks along an axis is near, 13 is not; 64+49 is near, 64+49+49 is not; a full 41 x 41 sweep across the cell borders and zero).
* The built pack JAR was inspected: `BeaconCombat.gunBlockImpact` takes `com.tacz.guns.api.event.server.AmmoHitBlockEvent` and calls `getAmmo()`/`getHitResult()` directly; the published TaCZ 1.1.8-hotfix2 JAR has exactly these getters.

## Real server (GameTests in a real client's integrated server)

`tools/qa/QaOptimize.java.txt` boots a flat world and runs `OptimizePathGameTests` (opt-in: `-Darsenal.optimizeTests=true`, or `/optimize-path-test`). Each test runs the new code next to a verbatim copy of the code it replaced. Final run: **5 of 5 pass**.

1. **Same base score.** 72 synthetic bases (6 seeds x the 4 zone sizes x 3 sets of player-placed blocks, all heights, the beacon at positive and negative coordinates) plus one two-storey hall over a whole Mk-1 zone, which really reaches the floor cap (at least 72 floors) so the cap's stop is compared too: **the old and the new scoring give the identical score record on all 73.** The bases have layered ground with ores and water pockets, houses of 1 to 3 storeys (floors, walls, glass, doors, slab/stair/plank roofs, lamps, chests, barrels, bookshelves, beds, carpets, flower pots), lamp posts, fences, walls, leaves, sand, gravel, a lit and an unlit furnace, fire, lava, weapon stations of random age, a structure part and the beacon.
2. **Faster base score.** On the biggest zone (Mk-4 core, full height, 49,221 blocks, score 3,252), median of 7 timed runs after 3 warm-ups, old and new alternating on the same map: **63.9 ms to 21.2 ms (3.0x)** in the first run, **66.4 ms to 25.7 ms (2.6x)** in the final run (that run also has the allocation-free pre-checks; the two runs differ by the noise of a software-rendered client on the same machine). This is the hitch a base has about every 20 seconds between raids and once at every raid start.
3. **A TaCZ bullet still damages the beacon.** A real `AmmoHitBlockEvent` (a TaCZ `EntityKineticBullet` owned by a husk, hitting the beacon block) posted on the Forge bus reaches the beacon through the typed listener: the attack is registered and the beacon loses exactly the contact damage (2 HP). The test borrows the campaign of the beacon-less test world and puts it back (no objective left).
4. **Event bus traffic (informational).** During 200 ticks of this nearly empty flat world, **about 3,170 events per server tick and 240 per tick on the other threads** were posted on the Forge bus: the old catch-all listener was called for every one of them. On a private bus, one call of the old listener costs **about 17 ns** (posting a fresh event with and without it, 3 x 2 million posts). That is roughly 55 microseconds per server tick in an empty world; it grows with every entity and every rendered frame in a real world. The new listener is called only for TaCZ block impacts.
5. **Special Forces sweep.** With the optional mod absent (as here) `installed()` follows the mod list, a husk is never a soldier, and the once-a-second sweep costs **0.2 microseconds instead of 11.7 to 14 microseconds** for the 84 entities of this world (it grows linearly with the entities of a real Overworld).

The first run of the new test file had **two failing tests whose setups were wrong**, not the code under test: the "floor cap" base was too small to reach the cap (61 floors; replaced by the hall above) and the bus benchmark posted one event object twice, which Forge refuses (now a fresh event per post). In that same run the score equivalence had already passed on all 72 ordinary bases and on that smaller base.

**Regression:** the 11 Raider Gates GameTests (`tools/qa/QaStuck.java.txt`, which drive `RaidRescue`, `BeaconCombat`'s goals and the real `ArsenalBeacon.raid` loop) pass **11 of 11** on the final code.

How to run: `tools/qa/setup-qa.sh /tmp/claude-0/qa-opt pack QaOptimize`, then `xvfb-run ./gradlew --no-daemon --offline :arsenal-beacon:runClient` in that copy, and wait for `QA_SUMMARY`.

## Not tested

* A real pack (Create, KubeJS, many mods) and a real lived-in base: the timings above are on synthetic bases in a development runtime; the shape of the gain (no per-block strings, no list scan per floor) holds, the exact numbers will differ.
* The ModernFix path of the resource check: ModernFix is not in the development runtime, so the mixin that calls `SoundResourcePaths` was not exercised in the game; the check itself is covered by the unit test above.
* A dedicated server, two or more players, the standalone edition in the game (it builds and its unit tests pass; the changed code is shared by both editions).
* With the Special Forces mod installed (the sweep still runs then; only the "absent" case was run).
* Rendering, HUD and culling: not changed in this round and not re-tested.
* The kitchen, the table upgrade, the dispensers and the Field Guide were not re-run in the client (none of their code changed; they build and their unit tests pass).
* The legacy v2 to v6 server GameTests (still not run; see the handoff).
