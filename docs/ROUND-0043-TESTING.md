# 0.0.43: the Exchange's iron and andesite alloy, clay and stones from the mine: testing

Branch `feature/logistics-hub`, after 0.0.42. Only what is listed under *Results* was run; *Not tested* lists what was not.

## How it was run

* **Unit tests** (`:eternal-defense:test`), JDK 17.
* **A real client** (Forge 47.4.20 development runtime with TaCZ 1.1.8 and **Create 6.0.8** running, `QA_CREATE=1`; `xvfb`, 1280 x 720, GUI scale 2). Harness `tools/qa/QaRound43.java.txt` (`QA_CREATE=1 tools/qa/setup-qa.sh <dir> pack QaRound43`): a fresh flat world on Normal, a beacon 2000 blocks out. The Exchange part in survival with 20 Ardent Energy; every purchase made with the screen's own row and Buy buttons (real clicks). The FOB part: a test village 300 blocks east (a bell, three composters, two beds, six villagers), the FOB Beacon planted through its item on the server, `/eternaldefense fob complete`, both gates placed 140 blocks apart and linked, the route marked open, emeralds, the farm and the specialty switched off on the server (so the mine takes the whole trip); the Receiving Gate's screen opened with a real click and the mine's items chosen with the screen's own buttons; each delivery is the route's own passive delivery (its due time moved to a second later on the server).

## Results

### Unit tests: 403 of 403 pass

New `Round0043Test` (4 tests): the Exchange default (Iron Ore 16 for 2, iron blocks 8 for 8, andesite alloy 16 for 1, no iron ingots; iron twice the old price per unit in both offers; these three lines are the only change from `PACK_0041`), an untouched 0.0.41/0.0.42 file replaced and an edited one kept (and the older defaults still replaced), the mine list (the first six unchanged in place, the twelve new ones after them, no duplicates, only `minecraft:` and `create:` items, none locked, clay worth 1 and each stone worth what andesite is), the Choose tip's text. `RouteTest.theLootListsAreTheOwnersLists`, `RaidRoundTest.theShopSellsIronInBulkSlightlyCheaperPerIngot` (now: a block a little cheaper per ingot than smelted ore) and `Round0041Test`'s blaze-rod test (now against `PACK_0041`) were updated to the new lists. `EnergyAndExchangeTest` (buying always costs more than selling, alloy included) and `RouteTest.theExchangeBuysOnlyTheseTributeItems` (the Exchange buys no tribute item, the new ones included) pass unchanged.

### Real client: 23 of 23 pass (one run, on the committed code)

`docs/validation/round-0043/checks-round43.txt`; screenshots `r43_*.png` in the same folder.

* **Exchange:** the offers file written as 0.0.41 and 0.0.42 left it untouched; opening the shop replaced it with the 0.0.43 default. The Buy list: 16 Iron Ore for 2, 8 Block of Iron for 8, 16 Andesite Alloy for 1, no iron ingots (`r43_01`). Bought each with real clicks in survival: 16 alloy for 1, 16 Iron Ore for 2, 8 iron blocks for 8; 20 Ardent Energy in, 9 left (`r43_02`).
* **The mine:** the Choose tip names clay and the stones (`r43_03`). The picker shows 18 items in one row, each a real item on the client and on the server (Create running); clay's tooltip "6.4 per trip | about 76 an hour" (`r43_04`). Clay chosen: the server's choice and the screen's say clay; the next delivery put 6 clay into the gate's storage (the route's own passive delivery, `lastItems` 6). Asurine chosen (`r43_05`): 12.7 a trip on the screen, twice clay's 6.4; the next delivery brought 13. The screen's storage shows both stacks (`r43_06`).

## Not tested

* A client of the full pack (326 mods).
* The other 16 mine items delivered (each was shown in the picker and resolved to a real item; clay and asurine were delivered).
* An offers file someone edited, on a real world (unit-tested: kept).
* The FOB made through a real vanilla village and a ridden round trip (the 0.0.31 to 0.0.34 runs covered those; this run used the GameTests' test village and the route's passive delivery).
* Multiplayer.
