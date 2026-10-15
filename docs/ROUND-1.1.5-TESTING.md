# 1.1.5: what was tested

The owner's 1.1.5 list: giant Badass elites (up to five times), modifiers for raids started by the timer, red raid buttons, Fog of War switching off Reconnaissance, drops at 1.5 times the base chance (twice with every modifier), operator test commands. Branch `feature/raid-control`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-15). Evidence: `docs/validation/release-1.1.5/` (the check lines of the final run and its screenshots).

## Unit tests

`:eternal-defense:test` 467 pass, 0 fail. New `Release115Test` (5 tests): Badass sizes 1.5, 2.2, 3, 4, 5 (never more), the stride (3 blocks at five times) kept by the raid's balancing, giants from Badass II; the smash call in the raid loop and its repair journal; the name tag and mark drawn above the hitbox; every modifier and a special raid add 50% of the base chance (2.0 times), payout still +85%, the per-modifier numbers; an elite made outside a raid drops its own Ardent; Start raid and Call the raid red; Fog of War's texts and the one glow rule; the page and guide say "however it starts"; the commands registered for operators only. `Release114Test` follows the new 1.5 multiplier. `:eternal-defense-displays:test` 7 pass.

## Real client

`tools/qa/QaRelease115.java.txt`, run with `QA_CREATE=1` and `QA_MODS` holding LesRaisins Tactical Equipments 0.4.3: Eternal Defense, the displays, TaCZ 1.1.8-hotfix2 with its default gun pack, TaCZ Attributes 1.4, Create 6.0.8 and LesRaisins, plus the owner's Apocalypse 1.1.7 and TRIS-dyna 1.1.5 gun packs in `run-client/tacz`. A new flat world, survival, Normal; the player cannot be hurt. Window 1280 x 720. Every command was typed through the client (`/eternaldefense ...` as a chat command, op in a world with cheats on). Final run (run 3): **47 of 47 checks pass**.

* **Commands known to the client:** `elite`, `raid-mod`, `raid-timer`, `raid-info`, `balance`, `ages` under `/eternaldefense`.
* **balance / ages:** `balance` lists the Age medians (16.2, 24.1, 38.9, 58.5, 31.4 damage a second, from 5, 23, 43, 5 and 3 guns) and the 15 guns 1.4 times their median or more; `balance tacz:m1911` says Age 4 (set by an Age line) with its costs (a hardened receiver); `ages` lists the two hand-set lines; a second line written into `config/eternal-defense-ages.json` while the game runs (`tacz:glock_17`: 1), then `ages reload` (3 lines) and `/reload`: the Glock 17 is Age 1, with Age 1's receiver and, at 1.61 times its new median, a diamond from the balancer.
* **Giants:** `elite badass 1` to `5` (outside a raid): health 2 to 6 times; hitboxes 1.5, 2.2, 3, 4 and 5 times as wide and as tall; step heights 0.9, 1.32, 1.8, 2.4 and 3 blocks; names over their heads; the client draws each at its size with its hitbox; each reply gives health, size and drops. Pictures of the row and of Badass V's head with its name just above it.
* **Stride:** on a stone ledge 3 blocks high, Badass V walks to the top; Badass I stays below.
* **Smashing:** a Badass V raider (a raid with no waves of its own) inside a ring of stone 15 wide and 7 high, outside the zone: it smashed 49 blocks and walked out in 4.4 seconds; with Reconnaissance at its top level and no Fog of War it glowed, on the client too.
* **raid-mod / raid-info:** with no modifier a red-marked mob drops at 6.7% x 1.50 = 10.0%; `raid-mod list` shows the six with their payout and drop percents; all six switched on and `raid-mod special swarm`: chosen on the server, payout +85%, drops +50% of the base chance; `raid-info` then says 6.7% x 2.00 = 13.3% and the raid's pay (4 Ardent at level 0, raised 85%, plus the Swarm's 4).
* **Beacon screen (GUI scale 3):** Start raid is red on both Raid control pages; the modifiers page shows all six ticked and Thousands picked, "+85%, +50%", the title "MODIFIERS | NEXT RAID, HOWEVER IT STARTS" and "Next raid: payout +85%, Ardent drops +50% (however it starts)"; no text or label cut. Fog of War's tooltip: "Switches off your Reconnaissance upgrade for that raid: mobs left alive never glow through walls, so you have to find them yourself." (picture).
* **Raid started by its timer:** `raid-timer 0` by day: the raid is due, the reply says it waits for night and a player near the beacon (`/time set 14000`); five seconds of day: no raid; `/time set 14000`: the raid starts by itself (not booked by Start raid) with all six modifiers and the Swarm, the choice used up; chat names them with +85% and +50%. Reconnaissance at its top level, Fog of War on: 48 sightings of raid mobs over 15 seconds, none glowing on the server or the client. Hardened (+35% health) and Forced March (+20% speed) are on the raiders. `raid-info` in the raid shows this raid's drops (x 2.00), the wave, mobs left and alive. `raid-mod hardened` and `raid-timer 30` are refused during the raid. `elite badass 5` in the raid: it joins the raid (no glow under Fog of War) and rushes the beacon (picture). Won by killing everything: the reward chest got 11 Ardent (4 raised by 85%, 3 elite waves, the Swarm's 4); chat said the cache is 85% bigger; every elite dropped its own.
* **Drop rate** (a raid with no waves of its own, 400 red-marked zombies killed by the player each): every modifier and a Swarm 58 Ardent (expected 53.4 at 2.0 times the base chance); no modifier 35 (expected 40.0 at 1.5 times). Run 2 had 54 and 41.

Earlier runs, harness only: run 1 assumed the next raid was level 1 (a new world's is level 0), sampled Fog of War too briefly (2 raiders seen) and judged the drop rate on 45 kills; run 2 took two pictures from the ground (the player fell after a teleport) and one after the page had changed. No game code changed between runs except the commands class name (`TestCommands` to `OperatorCommands`, see Jars).

## Jars

All four jars (both builds of the main mod, the gun guide, the displays): no test, QA or note files; every PNG holds image chunks only; no tool, assistant or session names in text, data or class files (the one match of three letters is TaCZ's "PEQ6 ILLM" laser in the catalogue, as before). The first scan flagged the new commands class by its name (`TestCommands`); it is `OperatorCommands` now and the jars were built and scanned again.

## Network

Protocol **42**, unchanged (no packet changed).

## Not tested

* The FOB's Call the raid button in game (its red look is checked in the source only).
* The owner's full pack, a dedicated server, two players.
* Raids above level 0 with giants arriving by themselves; a giant smashing blocks inside the base's zone and their repair after the raid (the ring stood outside the zone).
* Special raids other than Thousands together with modifiers.
* A server whose config still has the old `markedChanceMultiplier` line; Luck.
* Window sizes other than 1280 x 720.
