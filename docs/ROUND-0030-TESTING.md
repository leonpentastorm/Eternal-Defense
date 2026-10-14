# 0.0.30: the 4-wide gates, where a FOB counts, two screens: testing

## How it was run

* **Unit tests** (`:eternal-defense:test`), JDK 17.
* **FOB GameTests** (`FobGameTests`, batches `fob_01` to `fob_22`), on the real client of each edition at the start of `tools/qa/QaFob.java.txt`.
* **Real client** of each edition (Forge 47.4.20 development runtime, `xvfb`, 1280 x 720), `QaFob` on a fresh NORMAL world in a generated village and at home: slices 1 to 4 as before, with the 0.0.30 steps added (below). Editions run one after the other.

## Results

### Unit tests: 283 pass, 0 fail

* `RouteTest`, rewritten for the owner's 4-wide models:
  * the two halves joined in the anchor's frame are 4 cells wide, 2 tall, 1 deep; nothing hangs into the lanes below 24.95 px; each half stays within a block model's reach;
  * the frame: four beam parts on the top row, the left column the fourth cell, the lanes the two middle cells; the beam's collision starts 1.59 blocks above the rail (a seated rider's head is at about 1.51);
  * the lane choice (`Frame.laneOrder`): a rail beside the clicked one decides the side, a lone rail goes by the half clicked;
  * a pass through either lane counts, a cart in the anchor's or the column's cell does not;
  * the owner's files unchanged (checksums of the four half models, their textures, both item models and item textures against `docs/art/Eternal-Defense-Supply-Rail-Gates-4wide`), the blockstates draw each half with its three lamp overlays, the owner's lamp elements are where the overlays draw, the 3 x 3 model is gone;
  * the Delivery Gate is sold at Workshop Fabrication.
* New `Round0030Test` (5 tests): the zone outline switch is saved and starts on (a FOB saved before 0.0.30 shows it); every refusal the FOB Beacon can give has a preview line; every band of the enchanting screen's left column ends before the next begins, the inventory inside the panel, the screen bigger than 0.0.29's and within 480 x 270; the Workshop sells both gates side by side and scrolls (mouse wheel, clipping, a Make button only on a whole row); a loaded rider's way back through the Delivery Gate is quiet for 10 s.
* `FobTest` and `RaidRoundTest` check protocol 35. `LangKeysTest` ignores the two left-column blocks (no item; their gate has the card).

### FOB GameTests: 22 pass, 0 fail, in both editions

| Test | What changed in 0.0.30 |
| --- | --- |
| `fob_09` the owner's round trip | The route is two rails side by side (156 blocks) joined by a curve at each end, both gates across both lanes. A plain minecart with a seated player goes out on lane A through the Receiving Gate and the Delivery Gate (the route opens), round the east curves, and home on lane B through both gates again (the round trip delivers). Checked: out on lane A, home on lane B, the rider seated all the way, the cart never slowed below 0.25 blocks a tick between the gates, both lights amber during the ride and green after it, the left column's lamps too. |
| `fob_12` placement | Two rails side by side: the Delivery Gate used on one of them stands across both (its lanes are exactly the two rails), with its left column and four beam parts; the rails stay. A click on the left column finds the gate. The shovel takes the whole gate back. On a lone rail it is placed again, the rail in one of its lanes. Not on a rail, a second gate for the same FOB, a Receiving Gate outside the base zone: refused, as before. |
| `fob_22` (new) where a FOB counts | The preview of a good spot: no problem, 3 villagers, 2 beds, the workstations, the sections to outline (the spot's own among them); the item's own check agrees. 64 blocks out: not a village, nothing to outline. No FOB: no outline sent. After planting: next to it the village is taken, and the FOB's outline is sent to a player in its village. The FOB screen's switch turns it off (nothing sent), a player holding a FOB Beacon still sees it, on again; 260 blocks away: nothing sent. |

### Real client: 86 pass, 0 fail, in each edition (`docs/validation/round-0030/checks-standalone.txt`, `checks-pack.txt`; the pack ran once, after the fixes below)

New in 0.0.30:
* Holding the FOB Beacon and looking at the spot: the server's preview is for exactly that spot with nothing in the way, it outlines the village part it would take, the line under the crosshair reads "FOB here: 4 villagers (3 needed) | beds 1 | workstations 3", and the ghost stands where the crosshair points (`f0`).
* After planting, the FOB's zone outline reaches the client (`f1`, `f2b` from above).
* The FOB screen's Zone outline switch: off (the FOB records it, the client's outline goes), on again (`f7b`).
* The Delivery Gate used on one of two rails laid side by side stands across both, a column either side, the rails still in its lanes, and links to the FOB (`f12`, `f13`).
* A seated rider in a minecart on lane A rolls on the rail, under the beam and through the gate (`f12a` the lanes ahead, `f12c` looking up at the nameplate as it passes over).
* The Receiving Gate at home on two rails: the link screen, the FOB chosen, green on both columns after the round trip (`f14` to `f16`); the gate screen opens by clicking the beam over a lane (`f17`).
* The enchanting screen with its slot empty: nothing overlaps (`f24b`).
* Workshop Fabrication: the list is longer than its window; scrolled to the end, the Supply Delivery Gate's Make button is in view and rows scrolled out hide theirs (`f11b`).

Screenshots in `docs/validation/round-0030/`: `standalone-*` (13) and `pack-*` (5: the preview, the Workshop scrolled, the Delivery Gate, the Receiving Gate green, the empty enchanting screen).

### Found by the real-client runs and fixed

* **First standalone run** (`checks-standalone-first-run.txt`, 80 of 85): five checks failed, none in the mod:
  * the preview and "the outline is back" checks ran 10 ticks after the action (a QA step's delay comes after it; the server looks every 10 ticks and sends outlines every 40); the harness now waits;
  * the rider's cart stood off the rail (the harness laid rails only into free cells, and the village had a block there); the harness now clears the lanes (never a bed, workstation or block entity) and checks that the cart is on a rail.
* From its screenshots: the FOB screen's switch label was cut ("Zone outline (45 se..."); it is "Zone outline" now, the section count in its tooltip. The zone outline was hard to read from the ground; each wall now has a bright band at the player's own height.

## Not tested

* A player riding a whole real loop between a real FOB village and a real base (128 blocks or more of track over real terrain): `fob_09` rides the loop on the test platform; the QA rides through one gate.
* A furnace, chest or hopper minecart through the new gates (only plain and chest minecarts, as before).
* Gates placed before 0.0.30 in a world updated to 0.0.30 (not converted; the documented way is the shovel).
* The owner's clearance note: a rider's head grazing the beam in third person, or with a resource pack or a mod that changes the riding height.
* The FOB outline and the preview in the Nether or the End (both are Overworld only by design: nothing is sent there), on a dedicated server, with several players at once, and their cost with five FOBs in view.
* The enchanting screen at a GUI scale where 440 x 260 does not fit (for example 1280 x 720 at GUI scale 3).
* The Workshop list's scroll bar dragged with the mouse (the QA uses the wheel).
