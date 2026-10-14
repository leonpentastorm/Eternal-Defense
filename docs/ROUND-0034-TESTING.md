# 0.0.34: the live catapult, the supply drop, Fishing & Animal Husbandry, the catapult's status, the radio, the specialty cap, the war horn, no totems: testing

Branch `feature/supply-catapult`.

## How it was run

* **Unit tests** (`:eternal-defense:test`), JDK 17.
* **FOB GameTests** (`FobGameTests`, batches `fob_01` to `fob_24`; `fob_24` changed for the 12-second drop from above and the rest heading), on the real client of each edition at the start of `tools/qa/QaFob.java.txt`.
* **Real client** of each edition (Forge 47.4.20 development runtime, `xvfb`, 1280 x 720 at GUI scale 2; the size checks at 640 x 360, 480 x 270 and 427 x 240), `QaFob` on a fresh NORMAL world. The sound engine ran on OpenAL Soft's wave backend (`ALSOFT_CONF`, output to a file), so the sounds the client played are counted by the game's own sound listener.

## Results

### Unit tests: 320 pass, 0 fail

* New `Round0034Test`: the catapult's motion (loaded, turned to the beacon, settled, thrown, the box released 3 ticks into the throw, then back to its rest heading), the rest heading from the placer's facing, the motion saved with the FOB and run every tick (no separate unseen throw); the flight home is the Supply Flare's 12 seconds, the parcel appearing a fall early over open sky, the flare's countdown and parcel used; protocol 39 and the live view packet (id 23), drawn in the seller with the grid narrower; the radio icon (16 x 16), the static and the four order lines (sounds.json, the OGG files, the subtitles, the button and the purchase); Fishing & Animal Husbandry (4 specialties, both lists, both icons, both names); saves of 0.0.31 to 0.0.33 by ordinal map to the right specialty and a husbandry choice keeps its item, saves now by name; the specialty slot at most 4 S-tier items an hour for the slowest, a middling and the biggest FOB alone at research 5, the 30-minute floor untouched, the other slots not capped; the FOB screen's catapult state and the lines for it, level 5 of the research tab.
* Also in `Round0034Test`: the war horn (the file, sounds.json, the subtitle, sounded on planting, on the raid starting and on the SOS raid, 12 seconds between two). `Round0032Test`: the monster hunter guild has no totem of undying.
* Changed: `Round0033Test` (the flight is 12 seconds), `RouteTest` (4 specialties; the research ratio checked on a small FOB, under the cap), `Round0032Test` (the merged specialty's two icons), `FobTest` and `RaidRoundTest` (protocol 39).

### FOB GameTests: 24 pass, 0 fail, in both editions

| Test | What it checks (changed) |
| --- | --- |
| `fob_24` | After the throw: it lands 12 s later, its landing spot planned beside the beacon; the parcel appears high over the spot (open sky) and comes down by parachute; the parcel holds the ghast tear; with nothing left to throw the catapult turns back to its rest heading. |

### Real client: 297 pass, 0 fail in each edition on the final code (`checks-standalone.txt`, `checks-pack.txt`)

New in 0.0.34:
* The war horn: planting the FOB (which calls its raid at once) sounds it once; the SOS raid starting sounds it again (the client's sound listener counted `eternal_defense:fob.war_horn`, 1 then 2).
* FOB Beacon, Research tab, the top level: level 5 reads "Supply Catapult + Specialty Shop" with the catapult beside it; one gift from level 2 to 5 announces the S tier and the Supply Catapult and Specialty Shop, each (`f23d`). The FOB tab: "Supply Catapult: OFFLINE, none placed" (`f23f`), and with the catapult placed, "ONLINE" (`f29d`); the overlap check on both.
* Receiving Gate: the village made Fishing & Animal Husbandry with beef chosen (a husbandry index moved up by the fisher's five): the card shows both names and both icons (`f17m`), the grid every fish, meat and leather, 12 (`f17n`). The specialty's rate on this 21 km line, faster than a vanilla cart, is 64.0 value an hour: the cap (the first run found 87 before the fix below).
* In the village: bought with the server's own call (as the table's button sends it): one of the owner's order lines played (the client's sound listener counted `eternal_defense:seller.order_chatter_N`); the arm throws on the client (`f31`, `f31b`); the box flies, landing 12 s after the throw, the chat says it was thrown and counts down ("Supplies arriving in ..."). Home: the base's land had not been loaded during the flight; the parcel came down after its fall, beside the beacon.
* Command Table: Contact Specialty Seller carries the radio icon (`f30`); pressing it played the owner's radio static (`seller.radio_static`); the seller shows the live catapult (the server's state reaches the client), "Ready" (`f30b`), the overlap check at the three sizes (`f30c`). Buy 1 lot: another order line; the view shows it loaded and "Turning to base" (`f30e`), then "Throwing!" with the arm up (`f30f`), the box flying out (`f30g`), "In the air: 9 s" (`f30h`).
* At home looking up: the parcel appears 27 blocks over its spot and comes down by parachute (`f31e`, `f31f`), lands beside the beacon with what was bought (`f31d`); the chat counted both boxes down (28 lines).

### Found by the real-client runs and fixed

* **First standalone run** (`checks-standalone-first-run.txt`, 288 of 294):
  * **the specialty cap leaked on a fast rail**: the cap was per trip (64 value times the hours a trip carries), but a rail faster than a vanilla cart delivers more often than that; the gate screen showed 58 beef an hour (87 value). The cap is now counted against the real time between deliveries (`RouteRules.deliveryHours`), a unit test covers a fast rail and the client run checks the gate screen's rate;
  * "Supply Catapult: OFFLINE, none placed" was cut short in the FOB screen's left column at 640 x 360: it wraps onto two lines;
  * the seller's line "Fishing & Animal Husbandry | 4 Ardent Energy a lot | 0 of 4 boxes waiting" was cut short at 427 x 240: it drops the boxes, then the name, when it does not fit; the live view's line "Turning to your base: 1 box(es) loaded" was cut short: the lines are shorter ("Turning to base", "In the air: 9 s");
  * harness: the FOB screen was opened from 10 blocks away and closed at once (out of reach): the player stands beside the FOB Beacon first.
* **Second standalone run** (`checks-standalone-second-run.txt`): 295 of 295, before the totem was removed and the war horn added (owner, late in the round).
* **First pack run** (`checks-pack-first-run-stopped.txt`): stopped after the FOB GameTests and the first client checks, to run on the code without the totem; two meal-buff checks had failed while a unit test build ran beside it (load), not seen in any other run.
* **Second pack run** (`checks-pack-before-horn.txt`): 295 of 295, with the totem removed, before the war horn.
* **Final standalone run** (`checks-standalone.txt`): 297 of 297, the final code (the war horn's two checks added).
* **Third pack run** (`checks-pack-third-run.txt`, 296 of 297): the check of the live view's "In the air" line read a frame recorded before (the software renderer, loaded, had drawn no new frame in 3 ticks); the picture taken just after shows "In the air: 9 s". Harness: the live view's text checks wait for a fresh frame.
* **Fourth pack run** (`checks-pack.txt`): 297 of 297, the final code.

## Not tested

* A dedicated server with a second client (single player only): the live view's packet goes to every player with a table open.
* The sounds were played by the client and counted by its sound listener; nobody listened to them (the wave file was not reviewed by ear).
* The cap in long real play (the gate screen's numbers are the rules'; a ridden line still brings up to 2.5 times a passive delivery).
* A world from 0.0.33 loaded in 0.0.34 (the specialty migration is covered by the unit tests on saved data, not by loading a real world).
* The trader hub's 70-item grid scrolled in the seller (narrower now beside the live view).
* GUI scale 1.
