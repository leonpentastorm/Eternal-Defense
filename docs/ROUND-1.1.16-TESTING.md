# 1.1.16: what was tested

The owner asked: "In the "lock out some attachment" segment of the attachment table code, add exemption for whatever weapon is in the first 2 age. it's the age 3 and up attachment that add so much damage to the gun it become problematic in my testing". On `dev`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-17). Evidence: `docs/validation/release-1.1.16/`.

Read as: an add-on pack's attachment that raises damage is sold again when the attachment table lists it at Age 1 (Frontier) or Age 2 (World Wars). From Age 3 (Modern) on it stays off the table, as before.

## What changed

* **The exemption.** `AttachmentDamage.sold(output, age)` now lets an attachment through when its Age is at most `damage_attachments_kept_up_to_age` in `balance.json` (2).
  * The Age used is the one the table shows: the catalogue's, or a hand-set Age line (`age_overrides.json` or `config/eternal-defense-ages.json`). An attachment the catalogue does not know is Age 5.
  * So the check moved in `WeaponPlatform.entries`: from before the Ages are set to just after `CraftBalance.aged`.
* **Unchanged.**
  * TaCZ's own damage attachments are still kept at any Age.
  * Attachments that lower damage were always sold.

## What it brings back in the owner's pack

Every gun pack in the owner's pack (the copy used for the 1.0.1 test pack) was read offline. Its add-on attachments that raise damage were listed with the Age the catalogue gives them:

* **Age 1 or 2: one.** Create Armorer's "Flywheel" Refit: Bigger Cylinder, +1 damage, Age 1. It is sold again.
* **Age 3 and up: all the others stay off.**
  * LesRaisinsAddon (lradd 0.3.0): 9, Age 3.
  * WaT's M32A1 HE: Age 3.
  * Create Armorer: 2, Age 4.
  * MS-Mobius: 26, Age 4.
  * EMX-Arms: 32, Age 5.
  * TRIS-dyna: 16, Age 5.
  * Helldivers: 6, Age 5.

A damage attachment that a later pack adds at Age 1 or 2, or that a one-line Age moves there, is sold too.

## Unit tests

`:eternal-defense:test` **512 pass, 0 fail**. The new `Release1116Test` has 2 tests:

* **The rule:** Ages 1 and 2 kept, 3 and 5 not, a setting of 0 keeps none, and the setting is 2.
* **Wiring:** the check comes after the Ages are set, and nothing checks before. `Release114Test` now looks for the new call.

`:eternal-defense-gun-guide:test` 8 pass. `:eternal-defense-displays:test` 7 pass.

## Real client

`tools/qa/QaRelease1116.java.txt` ran with `QA_CREATE=1` (Create loaded) and two of the owner's gun packs in `run-client/tacz`: Create Armorer 1.2.0.1 and TRIS-dyna 1.1.5. Before the game started, `config/eternal-defense-ages.json` held one line, `"trisdyna:muzzle_tris2": 2`. New flat world, 1280 x 720.

**Run 1: 9 of 9.** The table's attachment list, read on the server:

* Create Armorer's Bigger Cylinder (+1 damage, Age 1) is listed at Age 1.
* Create Armorer's AP grenade refit (+40 damage) and blasting protocol (explosive rounds), both Age 4: not listed.
* TRIS-dyna's Tris 2 muzzle (+7%), moved to Age 2 by the config line: listed at Age 2.
* TRIS-dyna's other 15 damage attachments (Age 5): not listed.
* No add-on damage attachment past Age 2 is listed.
* TaCZ's own hollow point, HE and incendiary rounds (Age 4) are still listed.
* A real attachment table was placed and opened, and a search for "cylinder" lists the refit (picture `r1116_cylinder.png`: Age 1 Frontier, 8 iron ingots and 12 lapis).

## Jars

* All four jars (both builds of the main mod, the gun guide, the displays) hold no test, QA or note files.
* No tool, assistant or session names appear in text, data or class files. The one match is TaCZ's "PEQ6 ILLM" laser in the catalogue, as before.

## Network

* Protocol **43**, unchanged: no packet and no registry entry changed.
* The server decides the table's list, so a 1.1.15 client on a 1.1.16 server sees the new list. Not tried.

## Not tested

* The owner's full pack in game: the list above comes from an offline read of its files.
* Crafting the cylinder in Survival, and fitting it to a gun.
* A dedicated server, a second player.
