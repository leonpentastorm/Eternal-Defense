# 1.0.1: what was tested

Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-14). Evidence: `docs/validation/release-1.0.1/`.

## Unit tests

414 pass, 0 fail (407 in the main mod, 7 in Gun Guide). New: `Round0101Test` (6: gun smith gates by Age, ammo cases and propellant, the outputs of the mod's Create recipes, no `kubejs:` id left in the mod's code and data, the factory tier's health caps, the displays as their own mod with no dependency on Eternal Defense). Updated to read the mod's recipe JSON and `RecipeRules` instead of the deleted pack scripts: `FlareVoiceAndAmmoTest`, `OwnerList0028Test`, `Round0041Test`; `LangKeysTest` maps the nine new items to the "Factory parts" guide card.

## Jars

| Jar | Bytes | Entries |
| --- | --- | --- |
| `eternal-defense-1.0.1.jar` | 26,153,545 | 1,356 |
| `eternal-defense-1.0.1-novoice.jar` | 25,577,392 | 1,341 |
| `eternal-defense-gun-guide-1.0.1.jar` | 21,926 | 20 |
| `eternal-defense-displays-1.0.1.jar` | 174,431 | 104 |

No GameTest, QA harness, readme or note in any jar (listed by name; `package-dev.py` checks it again). The jars tested on the servers below are byte for byte the ones in `build/libs` (SHA-256 compared).

## Dedicated servers (`servers.txt`)

| Run | Result |
| --- | --- |
| Required mods only: TaCZ 1.1.8-hotfix2, TaCZ Attributes 1.4, Create 6.0.8, LesRaisins 0.4.3 and the main jar; no KubeJS, no displays | "Done (2.9 s)"; "Factory gates on 281 gun smith recipes, universal wood on 302"; "Removed 4 native station and armor recipes"; no ERROR line but vanilla's "No key layers" |
| The same with the no-voice jar | "Done (3.0 s)", the same recipe lines, no other ERROR line |
| The displays jar with TaCZ only | "Done (2.8 s)", no ERROR line |
| A fresh server folder installed from `Eternal-Defense-1.0.1-test.mrpack` (`make-testpack.py` from the owner's export, `install-mrpack.py --side server`) | "Done (53.7 s)"; gates on 1,284 gun smith recipes (the KubeJS script of 1.0.0 also reported 1,284), universal wood on 12,525 (1.0.0's script: 12,475), conversions reduced in 46 (44), 476 recipes removed; KubeJS (still in the owner's pack) loads only its example scripts, 0 errors; no `kubejs:` part id and no missing `eternal_defense` item in the log |

`make-testpack.py` on the owner's export dropped the 11 old Eternal Defense scripts and configs and rewrote the `kubejs:` factory part ids in the quests. The wood and conversion counts are a little higher than the script's: the mod rewrites every recipe file before it is parsed, the script saw only the recipes KubeJS had parsed. The extra recipes were not compared one by one.

## Real client (development run, xvfb, Create running; `qa-release101.txt`)

`tools/qa/QaRelease101.java.txt`:

* **With the displays mod: 32 of 32.** The 11 recipes of the mod are loaded as Create recipes of the right type and output (pressing, mixing, compacting, sequenced assembly, mechanical crafting). Gun smith recipes carry the mod's parts (113 of the 285 in the development set with a receiver, 33 with cartridge cases). No recipe makes a native gun smith table. The Eternal Defense creative tab holds 47 items, only Eternal Defense items, the seven factory parts among them, no stand, no unfinished part. The displays tab holds the 11 stands, all `eternal_defense_displays:*`. The Weapon Platform's Supplies lists the 11 stands. The stands have their crafting table recipes. After the first login the player carries the "welcomed" mark, so the welcome is not sent again (the chat line itself and a second login were not checked). A zombie has 60 health at factory tier 0 (3 x 20); after the player holds a Brass Receiver the tier is 1 and a new zombie has 80 (4 x 20). A cow is not scaled.
* **Without the displays mod (`QA_NO_DISPLAYS=1`): 31 of 31.** The same checks, except: no displays tab, and Supplies lists no stand.
* Screenshots of both creative tabs were taken and looked at during the run; the QA copy they were in was deleted to free disk space, so they are not in the repository.

## Not tested

* A real client of the full pack (the owner's 326 mods) with 1.0.1, and the creative tabs and JEI pages of the factory parts there.
* Running a sequenced assembly or mechanical crafting in a world (the recipes were loaded and checked by type and output, not crafted with Create machines).
* A world made with 1.0.0 opened in 1.0.1 (the factory parts and the stands changed ids; a fresh world is advised).
* Buying a stand in Supplies with real clicks (it was listed; buying uses the same code as every other Supplies item).
* The welcome line in chat and a second login; the factory tier above 1, and its announcement seen by a second player.
* The release zip's delete list applied to the owner's own launcher profile.
* Multiplayer.
