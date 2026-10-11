# The owner's list after the four FOB slices (0.0.28): testing

## How it was run

* **Unit tests** (`:arsenal-beacon:test`), JDK 17.
* **FOB GameTests** `fob_01` to `fob_21` on the real client of each edition (`tools/qa/QaFob.java.txt`), among them the new `fob_21`: the wood label on a dark oak log cost and on the logs tag, a Warlord Seal sold at an Exchange Shop in survival, a Patrol cleared and its pay counted in the reward chest.
* **Real client** of each edition: the whole `QaFob` script (slices 1 to 4) on a fresh NORMAL world, plus the Exchange Shop's Sell tab with a seal in hand (`f28`).

## Results

RESULTS28_TBD

## Not tested

* The pack's restoration matrix recipe in a real pack (KubeJS and Create are not in the development runtime); a unit test reads the script.
* The Pressed Receiver in a real raid's prizes: the pack edition's raid rewards also hand out the gunpacks' tactical supplies, which the development runtime does not have; a unit test reads the code.
* Firing the Bunker Buster, the Cluster Strike, the Gravity Well and the Starshell in a world: their numbers come from `CannonUpgrades`, whose values the unit tests check, and unit tests read that the flare hurts with `BUNKER_HITS` and gives Speed II.
* An exchange file edited by hand (it keeps its lines; the seal line must be added by hand).
