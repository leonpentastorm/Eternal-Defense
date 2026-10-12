# 0.0.33: the Supply Catapult and the specialty seller, the FOB zone's ground line, placement previews: testing

Branch `feature/supply-catapult`.

## How it was run

* **Unit tests** (`:arsenal-beacon:test`), JDK 17.
* **FOB GameTests** (`FobGameTests`, batches `fob_01` to `fob_24`; `fob_23` and `fob_24` are new), on the real client of each edition at the start of `tools/qa/QaFob.java.txt`.
* **Real client** of each edition (Forge 47.4.20 development runtime, `xvfb`, 1280 x 720 at GUI scale 2; the size checks at 640 x 360, 480 x 270 and 427 x 240), `QaFob` on a fresh NORMAL world in a generated village and at home, the editions one after the other, with the overlap check (`Ui.drawn`) on the new screens.

## Results

### Unit tests: RESULT_UNIT

* New `Round0033Test`: a lot is worth one S-tier item for every specialty item; the prices live in `Economy` (4 standalone, twice the Exchange's ghast tear; 2 pack), the catapult's fabrication price, the Workshop and creative lists; the seller's closed reasons in order; the throw follows the owner's README (0 to -88 degrees in 4 ticks, the box leaves as the arm passes -45, a 50-tick cycle; the cup's release point and the 45-degree velocity, turned with the frame); the flight times; the seller's button ids decode back and stay clear of the table's own; the catapult and its boxes are saved with the FOB; the owner's five models are installed as they are, with the textures, the blockstate, the item model, the loot table and the axe tag; every line exists (block, entity, tooltip, refusals for each reason, the seller's screen, the Field Guide card); block events reach the block entity (catapult and cannon); protocol 38.
* `LangKeysTest` lists the Supply Catapult's Field Guide card. `FobTest`, `RaidRoundTest` check protocol 38.

### FOB GameTests: 24 pass, 0 fail, in both editions

| Test | What it checks |
| --- | --- |
| `fob_23` | No FOB: the catapult is refused; 64 blocks out of the village: refused; in the FOB's village: placed, it serves the FOB, its 3 x 3 x 2 body stands; a second one in the same FOB: refused; research 0: the seller closed ("research"), research 5: open; the Command Table is told (catapult, seller open, index); taken down: unlinked, the seller closed again ("catapult"), its body gone. |
| `fob_24` | No Ardent Energy: refused, nothing queued; a lot of ghast tears bought (the lot's price taken); the box waits; the loaded catapult turns to face the beacon and throws once, a thrown box is in the air, it lands after the flight home; the parcel beside the beacon holds the ghast tear; four boxes waiting, a fifth refused ("busy"); four lots of sugar are 64; below research 5: refused. |

### Real client: RESULT_CLIENT

New in 0.0.33:
* Holding a Supply Catapult in the FOB's village: the ghost catapult and its 3 x 3 x 2 outline where it would stand (`f29`); placed with a right-click, it serves the FOB; the owner's model (`f29b`).
* The FOB zone outline carries the FOB Beacon's ground level (`f29c`).
* Command Table, FOBs tab: the row has a catapult and an open seller; Contact Specialty Seller, active (`f30`); the seller opens on the FOB's specialty, its whole grid (`f30b`), the overlap check at the three sizes (`f30c`); Buy 1 lot: bought, the box waits (`f30d`).
* At the village: the catapult throws (`f31`, `f31b`): it had turned to the beacon and thrown once; the box flies home; at home a parcel comes down beside the beacon with what was bought, the buyer reads where (`f31c`).
* Holding a Defense Beacon: the ghost beacon and the base zone it would get (`f32`); a Command Table: its 2 x 2 x 2 outline (`f32b`).

### Found by the real-client runs and fixed

* **First standalone run** (`checks-standalone-first-run.txt`, 242 of 256):
  * the FOBs tab's location and SOS lines were cut short at 480 x 270 and 427 x 240 by the new seller button: it takes its short label ("Seller") when the whole one would cut the row's lines, and on a small window's SOS row it steps aside until the SOS is over;
  * harness: it held the catapult the moment the FOB screen closed and the item never reached the hand (the rest of the catapult checks failed after it); it now waits for the item in hand.
* **Second standalone run** (`checks-standalone-second-run.txt`, 250 of 256):
  * **the catapult's throw never reached the clients**: a plain `Block` ignores block events and the server only sends one on when the block takes it, so the arm never moved on screen (the box was thrown and landed all the same). The Support Cannon had the same gap: its recoil was never drawn. Both blocks hand block events to their block entity now;
  * harness: the player was put one block in the air to look at a spot, fell, and looked elsewhere (the FOB Beacon's and the catapult's ghost checks failed); it stands on the ground now.
RESULT_LATER

## Not tested

* The seller on a dedicated server with a second client (single player only).
* A catapult in a village far outside the loaded area at the moment of a purchase, then visited (the unseen throw is covered by the GameTests' logic and the code path, not watched in the client).
* A box landing while the base's land is not loaded (it waits; not run).
* Every specialty's grid in the client (the monster hunter guild's was; the trader hub's 70 items scroll, not run).
* The placement preview of every block one by one (the catapult, the Defense Beacon and the Command Table were checked; the code is the same for the rest); Gun Displays' racks.
* The Support Cannon's recoil, now drawn, in the client.
* The pack edition's prices in play (2 Ardent Energy a lot).
