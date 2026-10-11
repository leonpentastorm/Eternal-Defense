# Forward Operating Bases, slice 2 (0.0.24): testing

## How it was run

* **Unit tests** (`:arsenal-beacon:test`), JDK 17.
* **FOB GameTests** (`FobGameTests`, batches `fob_01` to `fob_12`, opt-in `-Darsenal.fobTests=true` or `/fob-test`), run on the real client of each edition at the start of `tools/qa/QaFob.java.txt`: the slice 1 test village on a stone platform in the sky, plus for slice 2 a straight rail along X with the Receiving Gate 70 blocks west and the Delivery Gate 70 blocks east of the village (140 blocks apart), and the online player.
* **Real client** of each edition (Forge 47.4.20 development runtime, `xvfb`, 1280 x 720), `QaFob` on a fresh NORMAL world (seed 20201010, Normal difficulty, creative): slice 1 as before, then slice 2 in the generated village and at home: the Delivery Gate bought at the FOB shop and placed with the client's own right-click on a rail in the village, the Receiving Gate placed on a rail in the base zone, linked on its own screen, then the three passes of a round trip made on the server (`Routes.pass`, as a ride would; the physical ride is `fob_09`), the light, the gate screen opened by clicking the arch, and the gate turning UNLINKED when the FOB is taken back.

## Results

### Unit tests: 254 pass, 0 fail

New `RouteTest` (13): a pass is a segment crossing the gate's plane (both directions, 12 blocks in one tick, stopping inside, standing still, a track beside the gate, over the arch, the wrong axis, leaving from the plane); a ride runs home, FOB, home (start, restart, turn, commission, load, complete, another rider busy, start at home, wrong gate, gates too close); the baseline is the ride and cannot be faster than 0.57 blocks a tick; passive deliveries every 2T / 0.8, none with the clock run out, the FOB not ACTIVE, no route or the backlog; the light's one meaning; the numbers against the tuning principles (1 km: 57.6 items an hour passive against 65 made by 10 villagers; a ridden round trip 2.5 times passive; flat per hour from 128 blocks to 4 km); the stockpile grows with villagers and is capped; every item paid exactly once; no tribute good is bought by the Exchange; the route and stockpile save and load (unknown keys in their tags survive; an empty save reads as nothing); the model's opening over the rail is empty and the frame is 3 x 3; the lamps are an overlay and the owner's models and textures are byte for byte those of `docs/art/`; the prices and the guide agree. `FobTest` and `RaidRoundTest` check protocol 32.

### FOB GameTests: 12 pass, 0 fail, in both editions, in every run since the `fob_09` threshold fix

`fob_01` to `fob_08` as in slice 1 (`docs/FOB-SLICE-1-TESTING.md`), and:

| Test | What it shows |
| --- | --- |
| `fob_09` a ridden minecart | both gates linked, no route: red; a chest minecart rolled through the Receiving Gate starts nothing; the player seated in a plain minecart rolls east through the Receiving Gate and the Delivery Gate (140 blocks apart) and back, the cart never slowing below 0.25 blocks a tick through or between the arches (vanilla moves a ridden cart 0.3 at speed 0.4; an arch in the way would stop it); the ride through the Delivery Gate opens the route, T is the measured one-way time (never under the distance at 0.57 b/t), the inspection full; both lights amber during the ride; the way back delivers a ride's load into the reward chest's queue and renews the inspection; the rider stayed seated |
| `fob_10` passive tribute | a delivery by itself after 2T / 0.8 ticks of play, C items, the chat line sent and the note cleared, green; `/arsenal fob overdue` lets the inspection run out: both lights red, and no delivery in the next 300 ticks |
| `fob_11` backlog | 200 stacks queued: amber; a round trip renews the inspection and delivers nothing; no passive delivery either; the chest emptied: green again |
| `fob_12` shop and placement | a PLANTED FOB sells no gate; an ACTIVE one sells its Delivery Gate (survival, materials taken); not over a rail: refused; over the rail in the village: placed, its seven frame parts, linked by itself, the rail kept; a second gate for the same FOB refused; a Receiving Gate outside the base zone refused; the shovel takes the gate back and the item remembers its FOB; a FOB's removal turns its Receiving Gate UNLINKED and red |

### Real client, final runs (`checks-standalone.txt`, `checks-pack.txt`): 38 pass, 0 fail, each edition

Slice 1's 27 checks as before, then in the generated village at (-1088, 720) and at home: the FOB shop hands over its Supply Delivery Gate; a spot found and a rail laid; the gate used on the rail with the client's own right-click stands over it and links to the FOB by itself; the Receiving Gate used on a rail in the base zone opens its screen with the FOB list, and picking "North Gate (ACTIVE)" links it; a round trip made on the server (the three passes a ride makes) opens the route and delivers a ride's load (40 items, 21 km between the gates); the light at home turns green; a click on the arch opens the gate screen; after the FOB is taken back the Receiving Gate reads UNLINKED and is red.

Screenshots (`standalone-f11` to `f18`, four of the pack, from the second runs): the Delivery Gate in the village from the front and the back, its name on both plate faces and the red light; the Receiving Gate's FOB list; the gate screen before the first ride (the checklist with the full route instruction) and while the route runs (green, inspection time, stockpile, next delivery, the last ride); the Receiving Gate green, then UNLINKED and red; the Workshop with the Supply Receiving Gate row.

### Found by the real-client runs and fixed

* `checks-*-first-run.txt`: `fob_09` failed in both editions on its own speed check (0.2999999999992724 against "above 0.3": vanilla moves a ridden cart exactly 0.3 at speed 0.4); the light at home was checked 5 ticks after the round trip, before its once-a-second update (the screenshot showed it green); and the click on the arch opened nothing because the harness had closed the previous gate screen on the client only, so the server refreshed a screen that was no longer there. Fixed: the test's threshold, the harness's timing, and in the mod a click on a gate or a FOB Beacon now always opens a fresh screen.
* From the first screenshots: the route's instruction cut after two lines, "Inspection overdue" with a cross before the first ride, the village gate hidden by trees (harness camera), the home gate in front of the beacon's beam (harness spot).
* `checks-*-second-run.txt`: 38 of 38 and 12 of 12 in both editions with those fixes; the later runs add the `deliver` and `overdue` operator commands (`fob_10` uses `overdue`) and the route in `/arsenal fob list`.
* `checks-pack-third-run-harness-cleared-the-fob.txt`: the harness's view clearing (it removes trees between the camera and the village gate) also removed the FOB Beacon, which happened to stand in that corridor in this run; the mod did what it should (the FOB was deleted with its block, and the checks after it failed for want of a FOB). The clearing now leaves FOB blocks, block entities, beds, workstations and bells alone. `checks-standalone-third-run.txt` (38 of 38) ran the final mod code with the old clearing.
* The final standalone run started together with the pack run was killed by the machine (exit 137, out of memory with two clients) after its GameTests passed; it was run again alone (`checks-standalone.txt`).

## Not tested

* Nobody rode a real rail from home to a real village: the GameTest rides a plain minecart (with the player seated) through both gates on the test platform; the QA client places both gates for real and makes the passes on the server.
* A real 8-hour inspection running out (the clock is unit-tested; `fob_10` shortens it with `/arsenal fob overdue`); a passive delivery after hours of real play (`fob_10` uses a T of 60 ticks).
* Powered rails, slopes and curves next to a gate, and a gate in an unloaded chunk while a cart passes (the crossing test is unit-tested at 12 blocks a tick and from both sides; an unloaded rider is not tracked).
* A dedicated server, two riders on one route, a logout or a server restart in the middle of a ride (each cancel rule is in `Routes.second`; only the dismount-free round trip and the "another rider" refusal are unit-tested through `RouteRules.step`).
* Survival placement with a real mouse (the QA player was creative; its clicks are the client's own `useItemOn`).
