# The owner's fixes (0.0.26): testing

## How it was run

* **Unit tests** (`:eternal-defense:test`), JDK 17.
* **FOB GameTests** `fob_01` to `fob_17` on the real client of each edition (`tools/qa/QaFob.java.txt`), among them the new `fob_17`: a player with a Support Platform and no cannon in the hunt tests' base zone.
* **Real client** of each edition: the whole `QaFob` script (slices 1 to 3) on a fresh NORMAL world, to show nothing else changed.

## Results

### Unit tests: 264 pass, 0 fail

`FlareVoiceAndAmmoTest`, updated and two new tests:

* `weakSpecialRoundsKeepTheirOldPrice`: the five specials (hamster:flares_ammo, ronmc:bean_bag, ronmc:68pepperball, daffas_arsenal:water, bf1:medkit) take 1 case and 1 propellant for one round, still 1 case per 8 rounds and 1 propellant per 16. Nine ordinary ids (9 mm, 5.56, 12 gauge, .50 BMG, a rocket, an empty id and others) take the 0.0.22 amounts for batches of 1, 8, 30 and 64; one 9 mm round takes 2 cases and 2 propellant.
* `bothPlacesThatSetAmmoPartsUseTheSameNumbers`: the KubeJS script passes the ammo id. `WeaponPlatform.costs` removes the recipe's cases and propellant and adds the `AmmoInputs` amounts. Both new methods are public, so KubeJS can call them.
* `theReturnFlareNeedsTheCannon`: no flare kind skips the cannon check in `SupportCalls`, and `Hunts.returnGate` never calls `SupportCalls.problem`.

### FOB GameTests: 17 pass, 0 fail, in both editions

`fob_01` to `fob_16` as in slice 3, and `fob_17` (`theReturnFlareNeedsTheCannonButAnOperationsGateDoesNot`):
* A player with a Support Platform and no cannon is refused the Return Flare ("no_cannon").
* With a cannon placed, the same check passes.
* With the cannon taken away again, `Hunts.returnGate` (an operation with no ground) still opens its portal in front of the player.

### Real client: 59 pass, 0 fail, each edition (`docs/validation/round-0026/checks-standalone.txt`, `checks-pack.txt`)

The whole `QaFob` script (slices 1 to 3) passes the same 59 checks as in 0.0.25, so nothing else changed.

## Not tested

* The Universal Ammo Platform with the owner's pack: KubeJS, Create and the gunpacks' factory items are not in the development runtime (the cartridge case and propellant items do not exist there), so the platform's amounts are checked by unit tests (`AmmoInputs`, and that `WeaponPlatform.costs` replaces the recipe's cases and propellant) and not on screen. JEI's recipe view needs the updated script from the pack bundle.
* A Return Flare thrown by hand without a cannon in a real client: `fob_17` calls the same server check the throw uses, and the return gate of an operation.
