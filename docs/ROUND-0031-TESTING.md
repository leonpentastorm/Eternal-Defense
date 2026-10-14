# 0.0.31: tribute you choose, the Receiving Gate's storage, FOB management: testing

## How it was run

* **Unit tests** (`:eternal-defense:test`), JDK 17.
* **FOB GameTests** (`FobGameTests`, batches `fob_01` to `fob_22`), on the real client of each edition at the start of `tools/qa/QaFob.java.txt`.
* **Real client** of each edition (Forge 47.4.20 development runtime, `xvfb`, 1280 x 720 at GUI scale 2, and for the size checks 960 x 540 at GUI scale 2 and 1280 x 720 at GUI scale 3), `QaFob` on a fresh NORMAL world in a generated village and at home. The editions ran one after the other.
* **The overlap check (new).** While a check runs, `Ui` records every text its helpers draw (`Ui.drawn`: the box and the words; null in play). The harness records one frame and checks that no text lies over another text or over a button, that every text and every button is inside the screen's panel, and (on the gate screen and the Command Table's FOBs tab) that no text or button label was cut short with "…" (elsewhere cut texts are listed, not failed). It runs at 640 x 360, 480 x 270 (what 1920 x 1080 at GUI scale 4 gives) and 427 x 240 (1280 x 720 at GUI scale 3), and checks that the window really has that size.

## Results

### Unit tests: 291 pass, 0 fail

* `RouteTest`, rewritten for the tribute (17 tests): passive deliveries every 2 T / 0.8, never more often than every 5 minutes; the light; the tuning (ten villagers at 1 km and a vanilla cart's speed: 57.6 tribute value an hour, twenty twice that, never worse per hour on a longer route, +20 percent at 5 km, riding back to back 2.5 times passive); the slots that are on share the trip, the last one stays on, emeralds at least a third a delivery; 500 random trips lose and make no value (the tallies keep the rest); a ride never pays more than 2.5 times passive, on any route from 128 blocks to 20 km at three speeds and with stops; the owner's lists (the mine's six, 17 brewing ingredients by tier, 70 wandering trader items, no mod currency); the tribute items the Exchange buys (standalone: emerald and ghast tear, the same Ardent Energy an hour; pack: none); the save format with the new fields and a FOB saved before 0.0.31.
* New `Round0031Test` (7 tests): both gates cost 2 chests, the storage is 54 slots and take only, only the Receiving Gate opens it; every gate button has its own id and the ids over 127 go by the mod's own packet; every screen with menu buttons sends them with `BeaconNetwork.MenuButton` (the source has no vanilla button call left); the 5-minute floor and the ride cap are wired in `Routes` (a route's first delivery always full); a new FOB rolls a specialty (all four seen in 200 FOBs) and saves it; the screens' lines exist.
* `FobTest` and `RaidRoundTest` check protocol 36. `OwnerList0028Test` lost its 0.0.28 "nine goods fit the gate screen" test (the goods are gone).
* First run: 6 failures, all in the round's own code or tests: a FOB's storage made an item stack in a unit test, where Minecraft's registries do not exist (the storage is now made on first use), a 0.1 percent rounding wobble in the "never worse per hour" check, and the guessed Exchange list in the test.

### FOB GameTests: 22 pass, 0 fail, in both editions

| Test | What changed in 0.0.31 |
| --- | --- |
| `fob_09` the owner's round trip | A farm village (only the farm on, melon slices; 10 villagers): the ridden round trip puts two deliveries of melon slices into the Receiving Gate's storage, nothing into the reward chest's queue, and renews the inspection. |
| `fob_10` passive tribute | A delivery by itself lands in the storage with its chat line; the farm alone brings four times its all-on share; a menu of the storage refuses a player's stack (by hand and with shift); the inspection run out stops deliveries. |
| `fob_11` a full storage | Amber; a round trip renews the inspection but brings nothing; no delivery by itself either; emptied, green again. |
| `fob_12` placement and sale | The FOB shop's gate costs 2 chests more (the test hands them over). |

The first real-client run found three of these failing, each the test's own mistake: `fob_09` and `fob_10` set 20 villagers, but the FOB counts its real villagers (3 on the test platform) every 5 seconds, so a trip carried less than one melon slice; `fob_12` did not hand over the chests. The second run found `fob_09` failing on a real gap (below).

### Real client: 184 pass, 0 fail, in each edition (`docs/validation/round-0031/checks-standalone.txt`, `checks-pack.txt`)

New in 0.0.31:
* The ridden round trip's tribute is in the Receiving Gate's storage; the client's screen shows it.
* The gate screen takes the whole window less 8 px; at 640 x 360 the storage stands beside the tabs; the four slots with potion makers as the specialty (`f17a`).
* The countdown runs: 52 ticks later it reads about 52 ticks less (the server's state every second).
* Switching the mine OFF: the server records it and the screen shows it at once, the farm's rate four thirds as big (`f17b`); ON again.
* Choose: the mine's six items (`f17c`), iron chosen; the potion makers' 17, the ghast tear's tooltip with its rate and tier S (`f17d`), chosen: one every 8 trips (`f17e`); the Route tab's checklist (`f17f`).
* Take only, with the client's own clicks: shift-click takes a stack out; dirt put on an empty storage slot by hand, or shift-clicked from the hotbar, stays out.
* The overlap check, at 640 x 360, 480 x 270 and 427 x 240: the gate's Tribute and Route tabs and its potion makers' grid at all three, the Storage tab of a narrow window at the two small ones (the storage on its own tab, its slots hidden on the others), the Command Table's FOBs tab at all three: no text over another or over a button, every text and button inside the panel, no text or label cut short (`f17g` to `f17j`, `f19c`).
* The FOB screen sums up the tribute (4 slots, the next delivery) and passes the overlap check at the three sizes; texts it cuts short are listed below (`f23`, `f23b`).
* Command Table, during an SOS: the FOBs tab lists the FOB with its name, its distance from the base (worked out again by the harness), ACTIVE, the time left to answer; the table knows the map is not carried; Reprint SOS map hands one over and says so; a second press is refused while one is carried (`f19b`).
* The Base map: a FOB 21 km away is an arrow at the map's edge with "North Gate (21110 blocks)"; a second FOB 72 blocks from home (a record made by the harness, DOWN, removed again) is a flag with its name on its zone (`f19d`, `f19e`).
* The enchanting table's buy, unlock and upgrade buttons work through the new packet (the harness sends `BeaconNetwork.menuButton`, as the screen does).
* The FOB taken back with the shovel hands its storage (3 diamonds put in by the harness) to the player, who reads so.

Screenshots in `docs/validation/round-0031/`: `standalone-*` (20) and `pack-*` (6).

### Found by the real-client runs and fixed

* **First standalone run** (`checks-standalone-first-run.txt`, 154 of 167):
  * the three GameTests above (the tests' own mistakes);
  * **an SOS answered the moment the village loaded counted no villagers** ("Keep at least 0 of its 0 villagers alive"): the harness teleports into the village and the raid started before the villagers' entities had loaded, so the villager killed in the raid was never recorded and the repair checks failed after it. The answer now waits for the village's entities (`FobSos.villagersLoaded`). A player who walks or rides in can hit the same race;
  * the Command Table's "No scan yet" hint sat in the middle of the map, under a FOB's arrow label (it is under the map now);
  * at 480 x 270 the compact countdown cut "Next delivery: 1 h 16 min" (it now takes a line of its own);
  * the FOB screen showed no next delivery right after the repair: it was only sent when something was pressed (it is now refreshed every second, the buttons rebuilt only when they change);
  * harness: its check of the countdown expected 2 s where its own steps take 2.6 s; its label check measured buttons as 16 px narrower than `Ui.UiButton` draws them; the 427 x 240 size ran at 640 x 360 (the GUI scale option is clamped to the window's size when it is set, so it is set a step after the window now).
* **Second standalone run** (`checks-standalone-second-run.txt`, 175 of 184):
  * **a route's first round trip brought one delivery, not two**: the ride cap counted from tick 0 of the world for a route that never delivered (a FOB 20 km out got almost nothing). A route that never delivered now gets the full two;
  * at 427 x 240 the Storage tab's three tab buttons ran past the panel's edge (the tabs now share the row), its countdown line was cut (a short form), the Command Table's message line was cut on the FOBs tab (it takes the tab's width), and on the FOB screen the gate's fifth material ran under its Placed button (the materials take a second line);
  * the harness read the gate's link 20 ticks after the press, once before the server had handled it (it waits for the link now).
* A third standalone run passed 184 of 184; the screenshots then showed a FOB's arrow label running over the beacon's mark in the map's middle (it steps above it now) and the FOB screen's last hint cut on a short window (it shows only when it fits whole). The pack run and the final standalone run are on the final code.

## Not tested

* A real player riding a real loop between a real village and a real base: the GameTest rides a loop on the test platform; the real-client run makes the three passes on the server.
* The new menu-button packet on a dedicated server with a second client: it is used in single player by every screen (and checked there), but the bug it fixes only shows on a dedicated server, which was not run.
* The ride cap and the 5-minute floor in long play (several hours, several FOBs); the balance numbers are design estimates (GDD 0.0.31).
* A passive delivery seen arriving in the real client (the countdown of the QA's 21 km route is 77 minutes); `fob_10` covers it on the test platform.
* The Delivery Gate's own screen in the real client (same screen class without the storage; its state is covered by the GameTests).
* A full storage in the real client (`fob_11` covers it).
* Five FOBs on the Command Table's FOBs tab (the scroll), and several FOBs' arrows pointing the same way (their labels can overlap each other).
* A world saved by 0.0.30 loaded in 0.0.31 (the migration is unit-tested on saved data only).
* The FOB screen on a 427 x 240 window cuts texts short that were there before 0.0.31 (the village's "Beds 1 | Workstations 3", the shop's "Fire Support Flare" and "Supply Delivery Gate", whose tooltips have them in full, the "Every flare also needs your Support Cannon" warning) and drops its storage line when there is no room; nothing overlaps. At 480 x 270 only the two shop names are cut.
* Screens at other GUI scales than the three sizes above.
