# 0.0.40: the Logistics Center Hub: testing

Branch `feature/logistics-hub`, made from `feature/animation-pass` at 0.0.39 (`4e593b3`).

## How it was run

* **Unit tests** (`:eternal-defense:test`), JDK 17.
* **Real client of each edition** (Forge 47.4.20 development runtime with TaCZ, `xvfb`, 1280 x 720 at GUI scale 2), the new harness `tools/qa/QaHub.java.txt` (`tools/qa/setup-qa.sh <dir> <standalone|pack> QaHub`). It works on a fresh flat world with a Defense Beacon planted 2000 blocks out, so that Hub A's anchor sits on a chunk corner (its 27 blocks span four chunks). It drives the game the way a player does where it can (right-clicks, screen clicks, page buttons, shift-clicks, mining) and calls the server's own handlers where a click would only reach them (the stations' buy, upgrade and Gather actions). After every action it waits on the server until the Hub line has been sent, then checks the pack, the Hub, the station and the chat.
* **Real client of the pack edition with Create 6.0.8 loaded** (Ponder 1.0.91, Flywheel 1.0.6, Registrate MC1.20-1.3.3, from maven.createmod.net and maven.ithundxr.dev), the harness `tools/qa/QaHubCreate.java.txt` (`QA_CREATE=1 tools/qa/setup-qa.sh <dir> pack QaHubCreate`). It builds real Create machines next to a Hub and lets them run. The pack's KubeJS is not in the development runtime, so the pack's Resonance Coil recipe (`kubejs:beacon/defense`, heated mixing) is mirrored as a datapack recipe (`tools/qa/qa_resonance_coil_mixing.json`) with the same inputs and output.

## Results

### Unit tests: 384 of 384 pass

New `SupplyRulesTest` (9 tests):
* **The allocation:** the pack pays first and the Hub only the rest. The nearest Hub pays before the next one. Overlapping ingredients (a tag and an item that both accept the same stack) never block a request that can be paid. A request that cannot be paid in full takes nothing. Over many random requests, the pack is always used to the full and nothing is overdrawn.
* **The Hub line:** pulls of the same kind add up and new kinds join at the end. A line names at most three kinds and counts the rest. The same player's pulls within two seconds are one line.
* **The zone re-check:** a Hub just placed checks the zone at once, then once a second. This is the overflow found in the client (below).

### Real client, standalone

100 of 100 pass (`docs/validation/round-0040/checks-standalone.txt`). Per-slot calls on a Hub with 220 of its 270 slots in use (540,000 calls each): `getStackInSlot` 21 ns, a simulated insert 38 ns, a count 25 ns.

### Real client, pack

99 of 99 pass (`checks-pack.txt`): the same run without the weapon table's Age upgrade. Per-slot calls: 14 ns, 33 ns, 26 ns.

### Real client, pack with Create

13 of 13 pass (`checks-create.txt`).

## What the real-client runs cover

**The block**
* Placing is refused outside the Home Zone, and refused when a block is in the way. Neither places anything, and the item is kept.
* The 27 blocks all point at one Hub id, and Hub A spans four chunks. Its record is in the world's saved data. A second Hub has its own id.
* The placement ghost (screenshot `hub01`).

**Storage**
* Every face of all 27 blocks (189 faces, and the null face) hands out the one inventory.
* 270 slots fill with normal stacks, and the rest is handed back. Unstackables take a slot each, and the same kind goes first.
* 4000 random inserts, pulls, per-slot moves and in-place changes: the count cache always equals a full scan.
* Per-slot calls take tens of nanoseconds: `getStackInSlot`, a simulated insert, a count.

**The screen**
* It opens from a part. Page 1 shows slots 0 to 53, Next shows 54 to 107, and page 5 shows the last slot. Previous is off on page 1 and Next is off on page 5, and the server keeps the page.
* Shift-click works both ways, and clicks inside a page keep the counts.
* A full Hub reads 270 / 270. A tooltip shows over a Hub slot.
* At 640 x 360 the texts never overlap each other or a button.

**Automation**
* A vanilla hopper on top fills the Hub. A powered dropper puts one item in per pulse.
* A hopper underneath pulls out of the Hub, and nothing is lost or copied.
* None of it sends a Hub line.

**Stations**
* Workshop Fabrication, paid three ways: from the pack alone (no line), from the Hub alone (one line), and from both (the pack first, the line names only the Hub's part).
* A request that cannot be paid takes nothing and sends no line.
* Two buys within 2 seconds give one line with the running total; a buy after 2 seconds starts a new line.
* A FOB Beacon from the Hub: three kinds, then "and N more", in the recipe's order.
* Two Hubs: the nearer one is emptied first, and one line covers both.
* The Exchange Shop: buying with the Hub's Ardent Energy ("spent" line); selling an item taken from the Hub.
* The Defense Beacon: repair and a Defense upgrade.
* The Mess Hall: Gather with the pack and the Hub both full (the pack alone fills the table), from the Hub alone, and from both (the pack's bread first). Then its Mk II upgrade.
* The Drink Station Mk II: Gather fetches a Swiftness potion from the Hub.
* The Tactical Enchanting Table: a Warlord Seal from the Hub unlocks Soul Speed.
* The Support Cannon's first upgrade.
* The Support Platform: its screen shows the energy within reach, the pack plus the Hub, and a Supply Flare is bought with the Hub's energy.
* Standalone only: the weapon table's Age 1 to 2 upgrade. The pack edition's Age costs need Create and KubeJS items, which are absent there.

**Players**
* A second player stood in the zone and a third outside it. These were Forge FakePlayer stand-ins, not real clients. The second read the line and the third did not. The second player's own pull reached this player.
* The server log has `[Logistics Hub] ...`.

**The Home Zone only**
* A Hub set down outside the zone (by command, as its item refuses):
  * right-click says it does nothing there;
  * its 27 blocks stand;
  * a hopper gets nothing from it;
  * stations never draw from it.
* With the beacon removed:
  * right-click says no beacon is standing;
  * there is no handler, and stations draw nothing;
  * the items wait.
* With the beacon planted again, the handler and the supply come back.

**Side quests**
* The FOB Beacon's screen has the Supply Catapult row:
  * its Buy is off while the pack is short, because the Hub does not count at a FOB;
  * a refused buy takes nothing;
  * with the materials in the pack, the catapult is built and the screen says so.
* A FOB flare is refused when only the Hub holds the energy.
* The Command Table's Contact Specialty Seller button now sits inside the FOB card, 4 px above its lower edge.

**Break guard and relocation**
* Mining a Hub that holds items:
  * no crack progress, and "Empty the Hub first" in the action bar;
  * the client shows no cracks;
  * a forced break by a survival player is refused too.
* An explosion leaves all 27 blocks and every item.
* The Recovery Shovel picks it up: the stacks wait in the saved data and the item remembers them. Placed again, it has the same id and the same stacks.
* A creative break drops the contents and forgets the record. An empty Hub breaks whole and drops its item once.

**Chunks and reload**
* With all four of its chunks unloaded, the inventory is intact in the saved data.
* With only a part's chunk loaded (a region ticket), that part hands out the one inventory.
* Save, quit to title and reload: with the Hub's chunks not loaded, the inventory is exactly as saved. Its screen reads the same slots in use, a named sword keeps its name, and the anchor's id is still the record's.

## The Create run, in detail

Pack edition, Create 6.0.8 running in the same client, on a fresh flat world with a beacon planted 2000 blocks out. Every machine is Create's own block, powered by creative motors, and left to run. The harness only builds the machines, puts the first items in and reads the results.

**The Hub's blocks**
* With Create loaded, the 26 parts are the Create-aware kind (`CreateHubPart`).
* The mechanical arm's `eternal_defense:logistics_hub` interaction point type is registered.

**A line that makes one of our materials: Resonance Coils**
* A mechanical mixer over a basin on a blaze burner (lit with a block of coal: "kindled"). The basin spills onto a belt that ends at the Hub's west face, with no funnel.
* Two batches of the coil's ingredients went into the basin: 2 precision mechanisms, 2 ender pearls, 8 redstone, 4 golden sheets.
* Create mixed 8 Resonance Coils, and the belt delivered all 8 into the Hub. The basin and the belt were left empty, and nothing fell off the belt's end.
* The pack's KubeJS recipe (`kubejs:beacon/defense`) is mirrored as a datapack recipe here, because KubeJS is not in the development runtime.

**A belt ending at a funnel** (new in the final run)
* A second belt, sunk one block into the ground, runs north. Its last segment is under an andesite funnel on the Hub's south face (Create made it a belt funnel: `shape=retracted`).
* The belt's own end meets the ground, not the Hub, so the funnel is the only way in.
* 32 gold and 16 lapis put on the belt: all arrived in the Hub. Nothing stayed on the belt and nothing dropped.

**Extracting a chosen item**
* A brass funnel set into the ground under the Hub, pulling down into a chute and an item vault.
* With an item filter (diamonds): it pulled all 64 diamonds and nothing else. The Hub kept 64 cobblestone and 32 bread.
* With an attribute filter ("consumable"): it pulled the 32 bread and left the cobblestone and the coils.

**Chutes**
* A chute on top of the Hub fed 16 redstone in.
* A smart chute filtered to iron fed 16 iron in, and refused gold.

**Mechanical arms**
* One arm took 16 copper from a depot and put it into the Hub.
* A second arm took items out of the Hub (8 Resonance Coils) and put them on a second depot. Arms have no chest point, so a depot stands in for a chest.
* Conservation held: Hub + depot + both arms' hands = what was there + 16.

**A vault beside the Hub**
* An item vault against the Hub's east face kept its own 20 emeralds.
* The Hub's handler on that face still showed the Hub's own 270 slots. Nothing merged, and the Hub's 27 blocks stood.

**Speed**
* Create's own `ItemHelper.extract` (what funnels and arms call) on a full Hub: 16 µs per call.
* A simulated `insertItemStacked`: 6 µs per call.
* Each figure is over 2000 calls, on a Hub holding 269 stacks of cobblestone and one of diamonds.
* Earlier final-candidate run: 24 µs and 11 µs.

**Not covered by this run:** Create's packager, stock ticker and frogport systems; mechanical drills, deployers and other machines feeding the Hub directly (they go through the same item handler); the pack's own KubeJS recipes.

## Earlier runs, and what they found

**Real bugs, fixed**
* **A Hub that was just placed never worked.** Its "not checked yet" time was `Long.MIN_VALUE`, and `now - Long.MIN_VALUE` overflows to a negative number, so the once-a-second zone check never ran. The result: no item handler, and stations never drew from it. This was found in the early standalone runs. Fixed in `SupplyRules.recheck`, with a unit test.
* **The Recovery Shovel opened the Hub's screen** instead of picking it up. The block now passes the click on when the player holds the shovel.
* **The break guard read the anchor's synced "holds items" flag**, which is refreshed once a second. A Hub filled a moment before could still be mined (the second-to-last standalone run, 99 of 100). The server now asks the Hub's inventory.

**The harness's own mistakes**
* Lines merged across checks: it now waits for the line to go out before the next action.
* Removing the beacon decommissioned the campaign: it now plants the beacon back the way the game does.
* A click out of reach (6.36 blocks).
* The test iron was cleared by emptying the hand.
* `destroyBlock` reports true even when a block refuses: it now checks the block state.
* A Drink Station Mk I cannot mix: it now uses a Mk II.
* The Contact button was looked up by a different label: it now matches the full and the short label.

**Runs**
* Standalone:
  * a compile error;
  * 52 / 47;
  * 73 / 26;
  * 89 / 10;
  * 99 / 1 (the Contact button lookup);
  * 99 / 1 (the break guard's flag);
  * the final run, 100 / 0.
* Pack: an earlier full run, 99 of 99 (before the zone re-check refactor and the break-guard fix; kept as `checks-pack-earlier-run.txt`), then the final run on the current code, 99 / 0.
* Create:
  * 7 / 5: the burner was not lit, the funnel and smart chute were powered (redstone-locked) by default, and the arm could not output into a chest;
  * a compile error;
  * 12 / 12;
  * the final run, 13 / 0, with a new check for a belt ending at a funnel, with the inventory cleared at the start.

## Screenshots (`docs/validation/round-0040/`)

* `*-hub01_ghost.png`: the placement ghost.
* `*-hub02_placed.png`: the Hub placed.
* `*-hub03_menu_page1.png`, `*-hub04_menu_page5.png`, `*-hub05_menu_full.png`, `*-hub06_menu_tooltip.png`: the screen.
* `*-hub07_fob_catapult_short.png`, `*-hub08_fob_catapult_ready.png`, `*-hub09_fob_catapult_bought.png`: the FOB Beacon's catapult row.
* `*-hub10_table_fob_card.png`: the Command Table's FOB card with the moved Contact button.
* `*-hub11_after_blast.png`: after the explosion.
* `*-hub12_after_reload.png`: after the reload.
* `create-create01_coil_line.png`: the mixing line and the belt into the Hub.
* `create-create02_arms.png`: the two arms.
* `create-create03_overview.png`: the whole test site.
* `create-create04_funnel_line.png`: the sunk belt and the funnel on the Hub's south face.
* Checks: `checks-standalone.txt`, `checks-pack.txt`, `checks-create.txt`, and the earlier runs `checks-pack-earlier-run.txt` and `checks-create-earlier-run.txt` (12 of 12, before the funnel check).

## Not tested

**Setup**
* A dedicated server, and real second and third players. The harness used Forge FakePlayer stand-ins on the integrated server, so the Hub line's delivery to other clients over a network was not watched.
* The owner's pack itself with KubeJS. The Create line made Resonance Coils from a datapack copy of the pack's recipe; Reinforced Beacon Plating's own pack recipe and the other KubeJS materials were not run.

**Stations rerouted in code but not run in the client**
* Ammo Coin purchases.
* The Command Table upgrade and the specialty seller.
* The enchanting table's purchases and its Mk upgrade.
* The raid break.
* The Support Platform's Mk upgrades.
* The Drink Station's Mk upgrades, and the Latte's Paper Cup from the Hub.
* Gun, ammo, attachment and armor crafting.
* The pack weapon table's Age upgrade (it needs Create and KubeJS items).

**Threats to the block**
* Pistons, fire and mob griefing (only an explosion was tried).

**Scale and chunk loading**
* Many Hubs at once, and hours of throughput.
* A Hub whose anchor chunk is unloaded while a part's chunk ticks with a hopper or Create machine on it. Only direct handler calls on a non-ticking part chunk were tried.

**Other**
* The placement sound.
* Create's packager, stock ticker and frogport systems, and Create's display links.
* Mods other than Create that use the item handler.
