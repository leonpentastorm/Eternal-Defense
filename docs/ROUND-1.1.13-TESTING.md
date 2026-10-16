# 1.1.13: what was tested

The owner's bug report (with a picture of empty armor slots): armor equipped from a Loadout Flare's capsule did not show in the inventory until the Curios button was pressed; dropping two flares, Equip at the second capsule said the 4 armor slots were taken though nothing showed. On `dev`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-16). Evidence: `docs/validation/release-1.1.13/`.

## Reproduced first

`tools/qa/QaCapsuleSync.java.txt` in the development client (no extra mods), a new flat world in Survival: two capsules stand beside the player (`LoadoutCapsule.place`), the first with an iron set, a shield and an iron sword, the second with a diamond set, a shield and a bow. The player opens the first with a real right-click and presses Equip, closes it, opens the inventory, then does the same at the second.

On the 1.1.12 code: **2 of 6 checks pass.** "Equipped 6 items."; the server wears the iron set and the shield, the client's own inventory has only the shield (its armor slots empty), with the capsule open and after closing it; the second capsule says "Equipped 1 items. 5 stay in the capsule: those slots were taken." while the client still shows no armor. This is the owner's report.

## Cause

Equip put the kit on with the capsule's screen open and sent single slots of the player's inventory. While another screen is open the client applies those only for hotbar slots, so armor updates were lost, and the server counted them as sent. The off-hand also sat in that path (in this run it reached the client by another route). Opening another screen such as Curios's sends the whole inventory, which is why the armor then appeared.

## The fix, tested

`InventoryPayment.sync` sends the whole inventory when another screen is open, after the capsule's Equip, the station's print, every table and shop payment, and FOB research. Same scenario: **6 of 6 checks pass.** After Equip, with the capsule still open, the client's inventory has the iron set and the shield, the same as the server; after closing, the inventory screen's armor and off-hand slots show them (picture); the second capsule keeps its diamond set, says the slots are taken, and the inventory still shows the iron set.

Unit tests: `:eternal-defense:test` 501 pass (new `Release1113Test`: the refresh and every caller), `:eternal-defense-gun-guide:test` 8, `:eternal-defense-displays:test` 7.

## Jars

All four jars: no test, QA or note files; every PNG holds image chunks only; no tool, assistant or session names in text, data or class files (the one match is TaCZ's "PEQ6 ILLM" laser in the catalogue, as before).

## Network

Protocol **43**, unchanged (no packet changed; a vanilla whole-inventory packet is sent more often).

## Not tested

* With Curios and other inventory mods loaded (the owner's pack); a dedicated server and a second player.
* The station's print, table and shop payments and FOB research after the change (same refresh; not run in this round).
