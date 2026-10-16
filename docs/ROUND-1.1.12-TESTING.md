# 1.1.12: what was tested

The owner: "For armor table, Increase age 1 cost and lower age 4 and 5 cost, make sure the armor have a formular that gauge their crafting value just like gungs"; then, mid-round: "also add keybind from tacz addtion, tacz tactical movement and tacz magazine into our gun info mod as optional compat, only shows up when these mods are install, and also remove normal attack from the menu". On `dev`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-16). Evidence: `docs/validation/release-1.1.12/`.

## The armor data, read first

`tools/qa/QaArmorDump.java.txt` ran in the development client with the owner's eight armor mods from the exported profile (LR Armor 0.1.4.4, Marbled's Arsenal 2.4.0.0, Fracture Point 3.0.0-PT2, CAPS AWIM Tactical Gear Rework 3.0, Omega Recon 3.2.1, Tactical Vests 1.0.7, Tactical Stuff 1.0.2, Chovy's Apocalypse 1.9; with GeckoLib 4.8.4, MCore 1.0.3.0, Curios 5.14.1, Player Animator 1.0.2, LesRaisins 0.4.3). It wrote every armor-table entry with its stats and 1.1.11 price: 569 pieces of gear (536 worn pieces). They give only armor, toughness and knockback resistance; 55 Fracture Point pieces have no attribute modifiers, and their item's own numbers are used.

The 1.1.11 Ages put 265 pieces in Exotic, because the stat rule sends anything with toughness 3 there (uniforms and balaclavas with 1 armor too). Median protection score by Age (1.1.12 formula, same Ages): Frontier 1.7, World Wars 8.3, Modern 18.2, Advanced 22.0, Exotic 18.7. Ages were left as they are (see Not done).

## Unit tests

`:eternal-defense:test` 500 pass, 0 fail. New `Release1112Test` (5 tests): the score against vanilla sets (leather cap 1.7, iron 10, diamond 17.5, netherite 18.7; a helmet scales to the same set as its chestplate; at most 20; knockback up to 10%), the softened ratio and its clamp, the medians (fewer than 3 rated pieces: none), the rare part at the guns' threshold, the share of a set per slot and the rounding (the first material never 0), the set prices against 1.1.11 (Frontier over 1.5 times, World Wars up but under 1.5 times, Modern within 5%, Advanced and Exotic under 0.8 times, each Age dearer than the last), the Exotic optics, the settings in `balance.json`, and the wiring (table, plate rule, page row, screen text, guide line, reload). `:eternal-defense-gun-guide:test` 8 pass (a new test: the add-on keys only with their mod, Normal Attack never, the new labels). `:eternal-defense-displays:test` 7 pass.

## Real client

`tools/qa/QaRelease1112.java.txt` (`QA_CREATE=1`; `QA_MODS`: the armor mods above, TaCZ Additions 1.3.0, TaCZ Magazines 0.2.5-Beta, Tactical Movement 1.0.0 from the owner's profile, and the built Gun Guide 1.1.12), a new flat world, window 1280 x 720, the 1.1.11 dump beside it for comparison.

Run 1: **21 of 21 checks pass**, but the screenshot showed the gun card cutting its two longest labels ("Unload magazi...", "Special Attack/Acti..."): the card was sized 2 px narrower than the room its labels were given. Fixed (and LesRaisins' Special Attack/Action reads "Special attack"). Run 2, the same checks after the fix: **21 of 21**, every label whole. Run 3, without TaCZ Additions, TaCZ Magazines and Tactical Movement: **21 of 21**, the three add-ons' rows gone; its picture showed the narrower card's title touching "Hide" (the shorter Special attack label let the card shrink to its minimum): the card is now at least as wide as its header. Final runs on that code: run 4 without the add-ons and run 5 with them, **21 of 21** each, both cards clean (pictures).

* **The list:** 569 pieces, as in 1.1.11. 15 armor plates, none of them worn (the 31 `..._chestplate_...` pieces and plate carriers listed under plates in 1.1.11 are vests).
* **Totals by Age** (every piece's price in iron-ingot worth, against 1.1.11): Frontier 239 -> 736 (3.1 times), World Wars 296 -> 490 (1.7), Modern 3602 -> 3425 (0.95), Advanced 4844 -> 3420 (0.71), Exotic 24850 -> 16254 (0.65).
* **Typical chest pieces (ratio near 1), 1.1.11 -> 1.1.12:** Frontier oak ghillie chestplate: 3 iron sheets, 6 leather, 6 wool -> 9 iron sheets, 6 leather, 3 wool. World Wars Joker chestplate: 6 iron sheets, 6 andesite alloy, 6 leather, 6 wool -> 8, 5, 5, 3. Modern winter military chestplate: 9 plating, 3 mechanisms -> 7, 2 (and less andesite, leather, wool). Advanced kevlar vest: 12 plating, 3 netherite scrap -> 8 plating, 1 scrap. Exotic Ratnik chestplate: 15 plating, 3 scrap, 6 resonance coils -> 9 plating, 1 scrap, 2 coils.
* **Price follows protection:** within each Age and slot, of 74 pairs of pieces with different scores, no stronger piece costs less.
* **Outliers:** 14 pieces need a rare part, all a single diamond: 5 at Frontier, 9 at World Wars. LR Armor's scout boots (Frontier, 2 armor: iron boots among bare gear) cost 1 diamond, 6 iron sheets, 4 leather and 2 wool; the table marks them "Strong: 200% of its Age's usual protection" (picture). An Exotic uniform with 1 armor costs 2 plating, 1 mechanism, 1 coil, 1 andesite, 1 leather, 1 wool, no netherite.
* **A real craft in Survival** at a placed armor table (Frontier): with exactly the new price in the pack the boots are made and nothing is left over; a second try without materials says "Need 1 more Diamond. Nothing consumed."
* **The screen** shows the Exotic Fracture Point machine gunner vest at 9 plating, 2 mechanisms, 1 scrap, 2 coils, 5 andesite, 5 leather, 3 wool (15 plating, 3 scrap, 6 coils in 1.1.11) (picture).
* **Gun card with the add-ons:** the card lists TaCZ's eleven keys, Special attack, Unload magazine (Ctrl + R), Toggle laser (K), Lean right (Alt + E) and Lean left (Alt + Q); Normal Attack is not listed although LesRaisins registers it (picture).

## Jars

All four jars (both builds of the main mod, the gun guide, the displays): no test, QA or note files; every PNG holds image chunks only; no tool, assistant or session names in text, data or class files (the one match is TaCZ's "PEQ6 ILLM" laser in the catalogue, as before).

## Network

Protocol **43**, unchanged; no packet changed and no block or entity was added. A 1.1.11 client on a 1.1.12 server would label an armor piece's strength line "damage".

## Not done

* Armor Ages still come from names and stats as before. With the score, Exotic's many mid-tier pieces (anything with toughness 3) could move down like unknown guns are placed by damage; that changes when gear unlocks, so it waits for the owner.

## Not tested

* The owner's full pack in a client; a dedicated server.
* Gear whose value is not an armor attribute (the plates that go into Fracture Point, Omega Recon and Tactical Vests vests, pouches' storage, optics) is priced by its Age alone, a seventh of a set; protection that a mod gives through its own systems or Curios slots does not enter the score.
* Pricing the armor of other armor mods than the eight above.
* The Gun Guide with TaCZ Additions or TaCZ Magazines keys in conflict with another key (the warning mark is the same code as before).
