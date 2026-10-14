# Enchanting prices doubled (0.0.29): testing

## How it was run

* **Unit tests** (`:eternal-defense:test`), JDK 17: `EnchantTest` checks the new prices of both editions, the discounts, that a Mk III table charges the 0.0.27 standalone price, and that the Field Guide quotes the new numbers.
* **FOB GameTests** `fob_01` to `fob_21` on the real client of each edition, among them `fob_19`, which buys Sharpness I, II and V and Mending in survival and checks that each purchase takes exactly its price (the player now starts with 192 Ardent Energy, enough for the pack's 104).
* **Real client** of each edition: the whole `QaFob` script, as in 0.0.28.

## Results

* **Unit tests:** 276 pass, 0 fail (`EnchantTest` with the new prices).
* **FOB GameTests:** 21 pass, 0 fail, in both editions; `fob_19` buys Sharpness I, II and V and Mending and each purchase takes exactly its new price.
* **Real client:** 71 pass, 0 fail, in both editions (`docs/validation/round-0029/checks-*.txt`), the same script as in 0.0.28.

## Not tested

* Balance in play.
