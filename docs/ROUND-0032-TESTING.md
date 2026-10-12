# 0.0.32: village research, the monster hunter guild, the 30-minute S-tier floor, icons: testing

## How it was run

* **Unit tests** (`:arsenal-beacon:test`), JDK 17.
* **FOB GameTests** (`FobGameTests`, batches `fob_01` to `fob_22`), on the real client of each edition at the start of `tools/qa/QaFob.java.txt`.
* **Real client** of each edition (Forge 47.4.20 development runtime, `xvfb`, 1280 x 720 at GUI scale 2; the size checks at 640 x 360, 480 x 270 and 427 x 240 as in 0.0.31), `QaFob` on a fresh NORMAL world in a generated village and at home. The editions ran one after the other. The overlap check (`Ui.drawn`) of 0.0.31 runs on every new screen of this round.

## Results

### Unit tests: 301 pass, 0 fail

* `RouteTest`, rewritten for the value an hour: the slowest FOB (3 villagers, 128 blocks, every slot on) makes 64 value an hour and, at research level 4, its specialty makes one S-tier item every 30 minutes (`theSlowestFobMakesAnSTierItemEvery30Minutes`); 20 villagers twice 3, a 1 km line twice again, +20 percent at 5 km (the best 4.8 times the slowest), fewer than 3 villagers a third each; a vanilla cart's speed earns exactly the promise, a slow rail never less, a fast one more; never worse farther out; riding back to back 2.5 times passive and a ride never more than that on any route; research opens the tiers and grows the slots; the slots that are on share the trip; 500 random trips lose and make no value; the owner's lists; the tribute items the standalone Exchange buys (emeralds, ghast tears, blaze rods and ender pearls at the same Ardent Energy per value; shulker shells at twice that, an open question in `HANDOFF.md`); the save format with the research points.
* New `Round0032Test` (8 tests; the research announcements carry one "[FOB]" prefix): dragon's breath is S and only with the potion makers, whose list runs D to S; the monster hunter guild has every non-boss drop listed (none of the bosses'), ender pearls A, and fits the gate's button ids; research levels, unlocks, fallbacks and bonuses; the research buttons have their own ids and pay like every other payment; every slot, specialty and research has a round 32 x 32 icon (clear corners) and its flat icon is kept; the map's Names switch hides both kinds of FOB labels and every new screen line exists; the SOS answer waits 3 seconds in a loaded village; protocol 37.
* `FobTest` and `RaidRoundTest` check protocol 37. `Round0031Test` counts five specialties.
* First run: one failure, the test's own (fewer than 3 villagers count a third each, not a half).

### FOB GameTests: 22 pass, 0 fail, in both editions

### Real client: 226 pass, 0 fail, in each edition (`docs/validation/round-0032/checks-standalone.txt`, `checks-pack.txt`)

New in 0.0.32:
* The potion makers' grid at research level 0: the A and S items are locked (no click), D to B open; the ghast tear's tooltip names its tier and the level that opens it (`f17d`). The choose button sent anyway (as a modified client could) is refused by the server ("gate.result.locked"), the choice unchanged.
* Research level 4: every potion makers' item open; the ghast tear chosen; the screen's ghast tears per delivery are the rule's; the specialty slot shows x2.
* The monster hunter guild: the gate screen shows it, its grid lists every drop, ender pearls locked at level 1 (`f17k`) and open at level 2, the S items still locked; ender pearls chosen and sent (`f17l`).
* The slot cards show the round villager icons (`f17a`, `f17e`, `f17l`).
* The Command Table's map: the Names switch hides the FOB 20 km away's name and distance and the near FOB's name; the flags, the zone and the edge arrow stay (`f19f`); on again.
* The FOB screen's Research tab, from a survival inventory: level 0 and the player's 12 diamonds shown; the emerald buttons off (none carried), the diamonds' on (`f23c`); +10 diamonds takes ten from the inventory, 40 points, level 1, the screen and the chat say so; an emerald sent anyway is refused ("You have no emeralds to give."), nothing given; a creative player gives for free (every button on): 80 points, level 2, "A-tier items are open" in chat; ten more presses stop at the top level (240 points), "S-tier items are open" in chat (`f23d`). The tab passes the overlap check at the three sizes with no text cut short (`f23e`).
* Everything of 0.0.31 still passes (the gate's storage, the countdown, the switches, take only, the size checks, the SOS, the repair, the enchanting table, the Exchange).

Screenshots in `docs/validation/round-0032/`: `standalone-*` (14) and `pack-*` (5).

### Found by the real-client runs and fixed

* **First standalone run** (`checks-standalone-first-run.txt`, 195 of 223):
  * **the SOS race of 0.0.31 again**: "Keep at least 0 of its 0 villagers alive". The 0.0.31 check waited only for the zone columns whose land was loaded, so a teleported player could still start the raid before any villager's entity existed (the lost villager, the repair price and the villager brought back then failed after it). The answer now needs a player in the zone with the village loaded for 3 seconds in a row (`SosRules.ARRIVE_SECONDS`);
  * the gate's "Last delivery: a ride, 481 items, 12 s ago." no longer fit the countdown card (rides on the QA's 21 km line now bring hundreds of items): a short form when the long one does not fit;
  * the Research tab's +10 buttons were 2 px too narrow for their label;
  * harness: its ghast tear check expected 0.0.31's "several deliveries per tear" (the 21 km line now sends 9.3 a delivery: about 7 an hour, by design); it pressed "the first active +1", which in survival without emeralds is the diamonds' button; it checked the screens 30 ticks after a server change, before the once-a-second state had come (the server runs slow under `xvfb`); it now waits for the state.
* The second launch did not compile (a leftover step label in the harness). The third standalone run passed 226 of 226 (`checks-standalone-third-run.txt`).
* **First pack run** (`checks-pack-first-run.txt`, 225 of 226): the research announcements read "[FOB] [FOB] North Gate reached research level 1." (`Fobs.announce` adds the prefix and the new lines had it too; `Round0032Test` now checks it); the harness read the chat before the client had received the line (it waits for it now). The final runs of both editions are on the final code.
* Not found by a test: the FOB screen's "Villagers" showed the tribute's worker count (at most 20) since 0.0.31, because the tribute wrote the same key; seen while reading the code, fixed.

## Not tested

* The 30-minute promise in real play: the unit tests check the numbers; no client waited 30 minutes at a 3-villager FOB at level 4.
* The balance in long play: about 45 emeralds an hour from an emerald-only FOB of 10 villagers at 1 km, 2 totems of undying an hour from a small level-4 guild (`HANDOFF.md`).
* Ardent Energy given to the research (the code path is shared with emeralds and diamonds; only diamonds and the emerald refusal were run).
* The research buttons and the locked-choice refusal on a dedicated server with a second client.
* The Command Table's FOBs tab with research pips and specialty icons for several FOBs (one FOB in the run).
* The trader hub's locked goods and the fishing and husbandry icons in the client (only the potion makers and the guild ran; the icons are checked by `Round0032Test` and in the art sheet).
* The icons at GUI scale 1 (drawn at half their pixels).
* A world saved by 0.0.31 loaded in 0.0.32 (a potion choice may point at another ingredient; research starts at 0).
