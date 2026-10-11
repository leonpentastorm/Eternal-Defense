# The owner's list after the four FOB slices (0.0.28): testing

## How it was run

* **Unit tests** (`:arsenal-beacon:test`), JDK 17.
* **FOB GameTests** `fob_01` to `fob_21` on the real client of each edition (`tools/qa/QaFob.java.txt`), among them the new `fob_21`: the wood label on a dark oak log cost and on the logs tag, a Warlord Seal sold at an Exchange Shop in survival, a Patrol cleared and its pay counted in the reward chest.
* **Real client** of each edition: the whole `QaFob` script (slices 1 to 4) on a fresh NORMAL world, plus the Exchange Shop's Sell tab with a seal in hand (`f28`).

## Results

### Unit tests: 276 pass, 0 fail

New `OwnerList0028Test` (5): the seal on both editions' sell lists (4 and 8), an untouched 0.0.21 to 0.0.27 exchange file replaced and an edited one kept; matrix prices halved (`Economy.matrices`, Quantum 6, Aura 4, Dimension 10, the platform's last era 8) and the recipe making 8 with a Brass Receiver; the Pressed Receiver added after the hard raid's bonus; the wood names; nine goods in two rows of five. Updated to the new numbers (13 tests): the enchanting prices by edition and the discounts (`EnchantTest`), the Patrol pay doubled at every tier and the minimum (`TacticalOperationsTest`, `FlareVoiceAndAmmoTest`), the cluster's six shells and the Bunker Buster's five at every Damage level, the Gravity Well's 6 to 12 s and the Starshell's 30 to 60 s, the starshell's Speed II (`HandTunedRoundTest`, `RaidFixesTest`, `BeautifyPathTest`, `RaidRoundTest`), the matrix share of the Vertical zone, the emerald as the one tribute good the standalone Exchange buys (`RouteTest`).

### FOB GameTests: 21 pass, 0 fail, in both editions

`fob_01` to `fob_20` as before, and `fob_21` (`theOwnersListFor0028`): a dark oak log cost, expanded to the logs tag, reads "Wood (any log)" and takes birch logs and crimson stems; so does a logs-tag cost; iron has no label. In survival, an Exchange Shop placed in the zone buys a Warlord Seal: the seal is gone and the player holds 4 Ardent Energy (pack) or 8 (standalone). A cleared Patrol puts exactly its doubled pay in the reward chest.

### Real client: 71 pass, 0 fail, each edition (`docs/validation/round-0028/checks-*.txt`)

Slices 1 to 4 as in 0.0.27 (69 checks), and the Exchange Shop at home: it opens, its Sell tab lists the Warlord Seal at 4 (pack) or 8 (standalone) Ardent Energy. Screenshots: the gate screen with nine goods in two rows (the emerald last), the enchanting table at Mk I ("Table Mk I: 17% off", "Upgrade to Mk II: 33% off"), the Exchange's Sell tab with the seal first.

### Found by the runs and fixed

* The harness pressed the Exchange's Sell tab while looping over the screen's live widget list, which the press rebuilds (a `ConcurrentModificationException` after the press, in the first standalone run, 71 of 72): it loops over a copy now. No mod change.

## Not tested

* The pack's restoration matrix recipe in a real pack (KubeJS and Create are not in the development runtime); a unit test reads the script.
* The Pressed Receiver in a real raid's prizes: the pack edition's raid rewards also hand out the gunpacks' tactical supplies, which the development runtime does not have; a unit test reads the code.
* Firing the Bunker Buster, the Cluster Strike, the Gravity Well and the Starshell in a world: their numbers come from `CannonUpgrades`, whose values the unit tests check, and unit tests read that the flare hurts with `BUNKER_HITS` and gives Speed II.
* An exchange file edited by hand (it keeps its lines; the seal line must be added by hand).
