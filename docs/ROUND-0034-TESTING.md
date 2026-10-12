# 0.0.34: the live catapult, the supply drop, Fishing & Animal Husbandry, the catapult's status, the radio, the specialty cap: testing

Branch `feature/supply-catapult`.

## How it was run

* **Unit tests** (`:arsenal-beacon:test`), JDK 17.
* **FOB GameTests** (`FobGameTests`, batches `fob_01` to `fob_24`; `fob_24` changed for the 12-second drop from above and the rest heading), on the real client of each edition at the start of `tools/qa/QaFob.java.txt`.
* **Real client** of each edition (Forge 47.4.20 development runtime, `xvfb`, 1280 x 720 at GUI scale 2; the size checks at 640 x 360, 480 x 270 and 427 x 240), `QaFob` on a fresh NORMAL world. The sound engine ran on OpenAL Soft's wave backend (`ALSOFT_CONF`, output to a file), so the sounds the client played are counted by the game's own sound listener.

## Results

### Unit tests: RESULT_UNIT

* New `Round0034Test`: the catapult's motion (loaded, turned to the beacon, settled, thrown, the box released 3 ticks into the throw, then back to its rest heading), the rest heading from the placer's facing, the motion saved with the FOB and run every tick (no separate unseen throw); the flight home is the Supply Flare's 12 seconds, the parcel appearing a fall early over open sky, the flare's countdown and parcel used; protocol 39 and the live view packet (id 23), drawn in the seller with the grid narrower; the radio icon (16 x 16), the static and the four order lines (sounds.json, the OGG files, the subtitles, the button and the purchase); Fishing & Animal Husbandry (4 specialties, both lists, both icons, both names); saves of 0.0.31 to 0.0.33 by ordinal map to the right specialty and a husbandry choice keeps its item, saves now by name; the specialty slot at most 4 S-tier items an hour for the slowest, a middling and the biggest FOB alone at research 5, the 30-minute floor untouched, the other slots not capped; the FOB screen's catapult state and the lines for it, level 5 of the research tab.
* Changed: `Round0033Test` (the flight is 12 seconds), `RouteTest` (4 specialties; the research ratio checked on a small FOB, under the cap), `Round0032Test` (the merged specialty's two icons), `FobTest` and `RaidRoundTest` (protocol 39).

### FOB GameTests: RESULT_GAMETESTS

| Test | What it checks (changed) |
| --- | --- |
| `fob_24` | After the throw: it lands 12 s later, its landing spot planned beside the beacon; the parcel appears high over the spot (open sky) and comes down by parachute; the parcel holds the ghast tear; with nothing left to throw the catapult turns back to its rest heading. |

### Real client: RESULT_CLIENT

New in 0.0.34:
RESULT_NEW

RESULT_LATER

## Not tested

* A dedicated server with a second client (single player only): the live view's packet goes to every player with a table open.
* The sounds were played by the client and counted by its sound listener; nobody listened to them (the wave file was not reviewed by ear).
* The cap in long real play (the gate screen's numbers are the rules'; a ridden line still brings up to 2.5 times a passive delivery).
* A world from 0.0.33 loaded in 0.0.34 (the specialty migration is covered by the unit tests on saved data, not by loading a real world).
* The trader hub's 70-item grid scrolled in the seller (narrower now beside the live view).
* GUI scale 1.
