# Forward Operating Bases, slice 4 (0.0.27): testing

## How it was run

* **Unit tests** (`:arsenal-beacon:test`), JDK 17.
* **FOB GameTests** (`FobGameTests`, batches `fob_01` to `fob_20`, opt-in `-Darsenal.fobTests=true` or `/fob-test`), run on the real client of each edition at the start of `tools/qa/QaFob.java.txt`.
* **Real client** of each edition (Forge 47.4.20 development runtime, `xvfb`, 1280 x 720), `QaFob` on a fresh NORMAL world: slices 1 to 3 as before, then slice 4 at home. The steps: place the Tactical Enchanting Table with a right-click; open it; put a diamond sword in its slot; buy Sharpness I with the screen's own menu button; try Sharpness II with one ACTIVE FOB; unlock Mending with a Warlord Seal and buy it; upgrade the table to Mk I; close the screen and get the sword back. Screenshots `f24` to `f27`.

## Results

### Unit tests: 271 pass, 0 fail

New `EnchantTest` (7 tests):
* The level gate for every top level from 1 to 7: level I is free, the top level needs five, never fewer FOBs for a higher level, and the brief's table for 2-, 3-, 4- and 5-level enchants.
* Prices by rarity, level, treasure and edition, with the pack paying half, rounded up.
* The Mk discount and its prices.
* The Warlord Seals: the treasure list, the levels each seal opens, and that a cleared Warlord queues a seal.
* Why a level is locked, in order.
* The owner's model and texture unchanged (checksums), 2 x 2 x 1, and our blockstate using them.
* The prices in the Field Guide matching `Economy` and `EnchantRules`.

`FobTest` and `RaidRoundTest` check protocol 34. `LangKeysTest` checks the two new names and that each has a Field Guide card.

### FOB GameTests and real client

The real-client runs of both editions were in progress when this file was first committed; the results follow in the next commit.

## Not tested

* Balance in play: the prices against a real campaign's Ardent Energy income.
* A dedicated server, and two players at one table.
* Gear carrying a modded enchant.
