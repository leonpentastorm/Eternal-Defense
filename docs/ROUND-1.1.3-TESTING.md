# 1.1.3: what was tested

The owner's list after 1.1.2: logos for the three mods, the fixed models, no outline during a print, the starter revolver, tables with nothing to make. Branch `feature/display-tuner`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-14). Evidence: `docs/validation/release-1.1.3/` (the check lines of the final run, screenshots, and three sheets of every replaced model from the front, the front left and behind).

## Unit tests

`:eternal-defense:test` 446 pass, 0 fail (`LoadoutModelTest` with the new face counts of the station's doors and assembled model; `AssetFilesTest` now also reads the three logos: image data only). `:eternal-defense-displays:test` 7 pass, 0 fail. The changed starter GameTest (`starterKitIsGrantedOncePerPlayer`, now the revolver and its rounds) compiles; it was not run.

## Real client with the required mods only

`tools/qa/QaRelease113.java.txt`, run with `QA_CREATE=1` and `QA_MODS` holding LesRaisins Tactical Equipments 0.4.3: Eternal Defense, the displays, TaCZ 1.1.8-hotfix2 with its default gun pack, TaCZ Attributes 1.4, Create 6.0.8 and LesRaisins, nothing else (JEI is not in the development run). A new world in survival. Final run (run 3): **41 of 41 checks pass**.

* **Starter kit.** On first join the player gets the .22 Model 943 revolver (`tacz:taurus943`) and one stack of 64 `tacz:22wmr` (the ammo TaCZ's data names for that gun), no other gun, and the chat line naming both. The real reload key loads 8 rounds (56 left in the stack); the real fire key fires one (7 left).
* **Tables with nothing to make.** With these mods: weapon table 54 entries, ammo 25, attachment 119, armor **0**. The kitchen and support rows are never marked empty. Buying the armor table on the server is refused ("no installed mod adds anything to make there ... Nothing consumed"), no table lands in the pack; the weapon table is refused only for missing materials. On the beacon screen the armor card says *No mod adds anything to it* in orange, its Make button is off and not marked short of materials, the other three tables are not marked; hovering it shows the reason and no price. An order sent anyway (as a modified client could) gets the refusal on the screen's message line and nothing.
* **No outline during a print.** The station placed with a real right-click (27 cells); three coins in slot 1; sneak and right-click the doors. Counting every block outline the game asks to draw, after all other listeners: before the print 16 to 17 drawn, none hidden; while the print plays (from the walk in until the doors have shut) **none drawn** (59 hidden with the camera shots on, 59 with them off); after it 17 to 18 drawn again. The kit was put on both times. A watching player (this client given the print state the server sends for another player's print): no outline on the station (27 hidden), the ground beside it keeps its outline (14 drawn).
* **Models.** Every block state of the mod has a model (494 states), every item (60), every moving part (52). The 19 replaced blocks (Command Table, Cook Pot, Cup Dispenser, Defense Beacon Mk III, Drink Station Mk I to IV, Brew Pot, Exchange Shop, Logistics Center Hub, Mess Hall Mk I to IV, Milk Dispenser, MRE Station, Satellite Beacon, Supply Receiving Gate) and the placed Loadout Station photographed from three sides (`models_0.png`, `models_35.png`, `models_180.png`, `r113_model_loadout_station_*.png`): all drawn with their textures.
* **Logos.** The game's Mods list shows Eternal Defense's and the displays' logo (480 x 160). The gun guide is not part of the development run; its jar holds its logo and its `mods.toml` names it.

Earlier runs found and fixed: with the camera shots off the outline came back while the doors were still closing (the player is let go before the doors shut): the outline now stays hidden until the doors have shut, and is hidden on a printing station for every player. The empty table's tooltip still listed a price with red marks: it now gives only the reason. Harness only: the server's refusal goes to the beacon screen's message line, not the chat; a fake second player cannot print (the server treats a player who is not connected as logged out at tick 1), so the watching player's view was given the synced state instead.

## Jars

All four jars (both builds of the main mod, the gun guide, the displays) checked: no test, QA or note files; every PNG holds image chunks only; no tool, assistant or session names in text, data or class files (one match of three letters is TaCZ's "PEQ6 ILLM" laser in the catalogue). Each holds its logo.

## Network

No packet changed: Eternal Defense's protocol stays **41**, the displays channel **1**. The beacon state carries one more field per fabrication row (`empty`); a 1.1.2 client ignores it.

## Not tested

* The owner's full pack, a dedicated server and JEI in the run.
* Two real players (a watching player's view was simulated on one client).
* The gun guide's logo in the game's list.
* A mod that adds armor (the armor table then has entries and is sold as before; only the empty case was run).
* The starter GameTest (compiled, not run).
* Window sizes other than 1280 x 720.
