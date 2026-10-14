# Release 1.0.0: what was tested

Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-14). Evidence: `docs/validation/release-1.0.0/`.

## Unit tests

408 pass, 0 fail (401 in the main mod, 7 in Gun Guide). New: `VoiceEditionTest` (every spoken line, and only those, has a game sound in the no-voice map). Updated for the rename and the removed upgrades: `Round0043Test`, `RouteTest`, `RaidRoundTest`, `Round0041Test`, `OwnerList0028Test`, `Round0034Test`, `Round0038Test`, `Round0039Test`, `Round0030Test`, `RadioAndWhistleTest`.

## Jars

| Jar | Bytes | Entries |
| --- | --- | --- |
| `eternal-defense-1.0.0.jar` | 26,123,285 | 1,337 |
| `eternal-defense-1.0.0-novoice.jar` | 25,547,473 | 1,322 |
| `eternal-defense-gun-guide-1.0.0.jar` | 21,927 | 20 |
| `eternal-defense-displays-1.0.0.jar` | 175,062 | 104 |

SHA-512 of each jar: the release zip's `SHA512SUMS.txt`.

* Contents: classes, `assets/`, `data/`, `META-INF/` (manifest, `mods.toml`), `pack.mcmeta`, the mixin config and refmap, the wood lists; the displays jar also `LICENSE.txt`. No GameTest class, test structure, QA harness, readme, markdown or text note (listed and searched by name).
* Strings in all classes: no old project name, no "QA", "Claude" or GameTest reference, no version note (the idle animation config comment had "0.0.39:" and the controls marker "playtest-1": both removed; checked again on the final jars).
* JSON in the main jar: no comment or note field; model credits read "Eternal Defense" (84 generated models) or "Eternal Defense - remodel" (the artist's models, unchanged).
* The no-voice jar differs from the voiced one only in `assets/eternal_defense/sounds.json`, the 15 voice files it leaves out and the `mods.toml` description.

## Production dedicated server, the owner's pack (Forge 47.4.20, 326 mods)

| Run | Result |
| --- | --- |
| The owner's 0.0.41 test-pack server with the three 1.0.0 jars, the renamed KubeJS scripts and quests, old configs moved away, a new world | "Done (62.8 s)"; KubeJS: startup 1/1 and server 6/6 scripts, 0 errors, 0 warnings; our scripts load `dev.eternaldefense.beacon.RecipePolicy` and `ArmorPlatform` and log "[Eternal Defense] ... applied"; no missing-id or old-name line in any log |
| The same with `eternal-defense-1.0.0-novoice.jar` instead | "Done (64.8 s)"; KubeJS 6/6, 0 errors; no old name in the logs |
| A fresh server folder installed from `Eternal-Defense-1.0.0-test.mrpack` (`make-testpack.py` from the owner's export, `install-mrpack.py --side server`: 263 downloads checked, 44 client-only files skipped) with the final jars | "Done (62.3 s)"; KubeJS startup 1/1 and server 6/6, 0 errors; the migrated config files in place (`eternal-defense-*.toml`, `eternal-defense-controls-1.applied`, ...); no old name in any log |

`make-testpack.py` on the owner's export: 37 files moved to the 1.0.0 ids (6 old pack scripts and configs dropped, the old exchange file dropped, 7 config files renamed, 21 files rewritten: quests, sound physics, JEI, Inventory Profiles, Improved Mobs, the controls files); its final check found no old name left (third-party gun packs aside).

## Real client (development run, xvfb)

* `QaVoice` on the no-voice sounds (`QA_NOVOICE=1`: the no-voice jar's `sounds.json`, voice files deleted): 17 of 17. Each of the 15 radio events resolves to the game sound of its map entry (`minecraft:block/beacon/power1-3`, `note/bit`, `block/beacon/activate`, `mob/villager/yes1-3`); the cannon's shot still plays the mod's own file. No "missing sound" line for the mod (the log has vanilla's two goat horn lines, as every development client).
* `QaVoice` on the voiced copy: 17 of 17, every radio line resolves to its voice file and the file exists.
* `QaProfile` (Create running; every installation in the zone, a FOB with gates and a catapult, the previews, the menus, a raid), before and after the optimization, JFR recordings:

| Phase | Server tick before (ms) | after (ms) | Mod share of server samples before | after |
| --- | --- | --- | --- | --- |
| Idle base | 2.9 | 4.0 | 11.3 % | 9.1 % |
| Placement previews | 3.1 | 4.0 | 3.0 % | 4.2 % |
| Menus | 3.3 | 4.1 | 6.7 % | 5.5 % |
| Raid (wave 1, 9 raiders) | 13.1 | **9.1** | 39.3 % | **19.0 %** |

In the raid the pathfinder's share of server samples fell from 33.5 % to 15.1 % and `MeleeBeacon.tick` from 24.9 % to 8.0 %. The idle phases run code the optimization did not change; their absolute tick times differ by about 1 ms between the two runs while the mod's share stayed the same or fell, so about 1 ms is the run-to-run spread on this machine.

## Not tested

* A real client of the full pack with either build, and the sounds heard by ear (the container has no audio device: the events were resolved, not played).
* A server and a client on different builds (voiced and no-voice).
* The release zip's `profile/` files and delete list applied to the owner's own launcher profile.
* A full play session on 1.0.0 (raids beyond the first wave, FOB routes over hours, the Hub with Create automation); these features were tested in their own rounds (`docs/*-TESTING.md`) and 1.0.0 changed only names there, except for the raider movement above.
* Multiplayer.
