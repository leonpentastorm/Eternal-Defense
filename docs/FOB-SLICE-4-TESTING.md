# Forward Operating Bases, slice 4 (0.0.27): testing

## How it was run

* **Unit tests** (`:arsenal-beacon:test`), JDK 17.
* **FOB GameTests** (`FobGameTests`, batches `fob_01` to `fob_20`, opt-in `-Darsenal.fobTests=true` or `/fob-test`). They run on the real client of each edition at the start of `tools/qa/QaFob.java.txt`.
* **Real client** of each edition (Forge 47.4.20 development runtime, `xvfb`, 1280 x 720), `QaFob` on a fresh NORMAL world: slices 1 to 3 as before, then slice 4 at home:
  * place the Tactical Enchanting Table with a right-click; open it;
  * put a diamond sword in its slot;
  * point at an enchant's name (its tooltip);
  * buy Sharpness I with the menu button the screen sends;
  * try Sharpness II with one ACTIVE FOB: refused, the screen names the level and why, the locked button's tooltip;
  * unlock Mending with a Warlord Seal and buy it;
  * upgrade the table to Mk I;
  * close the screen: the sword comes back.

## Results

### Unit tests: 271 pass, 0 fail

New `EnchantTest` (7 tests):
* the level gate for every top level from 1 to 7: level I needs none, the top level all five, never fewer FOBs for a higher level, the brief's table for 2-, 3-, 4- and 5-level enchants;
* prices by rarity, level, treasure and edition (the pack pays half, rounded up);
* the Mk discount: rounded down, every Mk at least as cheap as the one before, at least 1 off any price from 2 to 80 at every Mk, and the pack's Sharpness V at Mk I;
* the Warlord Seals: the treasure list, the levels each seal opens, a cleared Warlord queues a seal;
* why a level is locked, in order;
* the owner's model and texture unchanged (checksums), 2 x 2 x 1, our blockstate using them;
* the Field Guide's prices against `Economy` and `EnchantRules`.

`FobTest` and `RaidRoundTest` check protocol 34. `LangKeysTest` checks the two new names and their Field Guide cards.

### FOB GameTests: 20 pass, 0 fail, in both editions (final runs)

`fob_01` to `fob_17` as before, and:

| Test | What it shows |
| --- | --- |
| `fob_18` one table per base, in the zone | Placed in the zone, it stands as an anchor and three structure parts (2 wide, 2 tall, 1 deep), and the base records it. A second table is refused, and so is one outside the zone. The Recovery Shovel on its upper part gives it back as an item, and the base forgets it. |
| `fob_19` the level gate | In survival with 64 Ardent Energy, with a diamond sword:<br>• Curses are never listed, Mending is listed, books are refused.<br>• Sharpness I is bought with no FOB at its exact price; Sharpness II is refused with nothing taken.<br>• 2 ACTIVE FOBs open Sharpness II; V is refused until all five are ACTIVE.<br>• Smite (conflicts with Sharpness) and Protection (does not fit a sword) are refused.<br>• Mending is locked without a seal, and the Unlock button does nothing without one; with a seal, the seal is used up and Mending is bought at its treasure price.<br>• Mk I makes Sharpness V cheaper.<br>• Closing the screen gives the enchanted sword back. |
| `fob_20` a Warlord's seal | A cleared Patrol pays no seal; a cleared Warlord pays one Warlord Seal into the reward chest. |

### Real client, final runs (`docs/validation/fob-slice-4/checks-*.txt`): pack 69 pass, 0 fail; standalone SA_TBD

Slices 1 to 3 as in 0.0.25 and 0.0.26, then slice 4 at home:
* the table placed with a right-click stands with its parts;
* the screen opens; Sharpness I applied through the screen's menu button;
* Sharpness II stays locked with 1 ACTIVE FOB, and the screen's line reads "Sharpness II needs 2 ACTIVE FOBs (you have 1).";
* Mending unlocked for the team (everyone reads it) and applied;
* the table is Mk I;
* the sword comes back enchanted when the screen closes.

Screenshots:
* `f24`: the owner's model in the zone.
* `f25`: the list for a sword, with an enchant name's tooltip ("Bane of Arthropods: up to level V. ACTIVE FOBs needed for each level: 0, 2, 3, 4, 5.").
* `f26`: the locked Sharpness II, with its tooltip and the refusal line.
* `f27`: Mk I, with the two chat lines.

### Found by the real-client runs and fixed

* **First standalone run** (every slice 4 check passed; 64 of 68 overall), from the screenshots:
  * the level buttons were too narrow for II, III and IV (they showed "…"); they are now 22 wide;
  * the left column cut lines short; the gear slot moved up, the lines are shorter, and the Mk is in Roman numerals as in the guide.
* **First standalone run, the 4 failed checks:** all on the slice 2 route. A villager stood in the Supply Delivery Gate's 3 x 3 frame, and the mod rightly refused the gate ("No room"). The harness now moves any mob out of the frame before it places a gate, and logs it; the final runs did not need to.
* **Second standalone run** (68 of 68), from the screenshots:
  * a refused level showed its message with the placeholders unfilled; the server now sends the finished line and the QA checks it;
  * row subtitles were cut short on 5-level enchants; they are shorter now.
* **First pack run** (`checks-pack-first-run-failures.txt`):
  * `fob_19` failed: in the pack, Mk I took nothing off any price under 10 (Sharpness V, 5, at 10 percent off rounded up, is 5). The discount now rounds down, never below 1.
  * Because the check failed midway, the test left the enchanting menu open, and three later checks failed after it (the FOB Zone effect, the meal, the gate screen). `fob_19` now closes the menu whatever happens; in the final pack run all three pass.

## Not tested

* Balance in play: the prices and the level gate against a real campaign's Ardent Energy income.
* Breaking the table with a tool (it drops itself through its loot table; only the shovel was used); an explosion near it.
* A dedicated server; two players at one table.
* Gear that already carries a modded enchant; modded gear.
* A real Warlord fight paying the seal (`fob_20` completes the hunt by setting its state, as the Tactical Operations tests do).
* The mouse wheel on a long list, and a GUI scale where the screen (384 x 236) does not fit.
