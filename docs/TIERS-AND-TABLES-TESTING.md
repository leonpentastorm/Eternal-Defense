# Mess Hall levels, two-column kitchen and one-button table upgrade: what was actually tested (project 0.0.11)

Same environment as `FIELD-GUIDE-TESTING.md`: Linux container, JDK 17, Forge 47.4.20 development runtime with TaCZ 1.1.8-hotfix2 and TaCZ Attributes 1.4, a real Minecraft client under Xvfb with software OpenGL. Create and KubeJS are absent from the development runtime, so the pack run shows their items as "Air" in the Workshop material lists (a development artifact, not a bug of the mod).

## Automated, in the build

* `./gradlew --no-daemon --offline build releaseJars` (JDK 17) succeeds for all three modules and both editions. **96 beacon unit tests, 0 failures, 0 skipped.**
* `MealRulesTest.eachTierBuildsOnTheLastOne`: the four levels have 1/2/3/4 pots, 4/8/12/16 servings, 3/3/3/6 food slots and 15/20/25/30 minutes; stew starts at Mk II and doubling at Mk III; Mk IV lasts the old fixed 36000 ticks.
* `MealRulesTest.doublesUnlockAtMkThreeAndLegendaryNeedsTwoFoodsFromEachGroup`: ordinary pairs double from Mk III, a legendary effect needs two foods from each group.
* `MealPlannerTest`: the planner doubles from Mk III in sandwiches and in stew, stamps each meal with its hall's length, refuses orders with more food kinds than open slots (`slots`), caps the table at the open slots, and every single effect and every pair of effects is still planned soundly or reported impossible for every level and both modes (the existing exhaustive tests).
* `LangKeysTest`: every kitchen text family exists (including `slots` and `stew_locked`), the kitchen guide names the four levels and the Doubling card, and every guide card keeps the What it is / You get / How it works / How to unlock format.

## Real client (`tools/qa/QaKitchen.java.txt`, 1280 x 720, GUI scale 3)

Three runs on the same code, apart from one label width changed between the first and the other two:

* Pack edition, all parts (kitchen, stew, legendary, tiers, dispensers, beacon, table, equivalence): **`QA_SUMMARY pass=61 fail=0`** (`checks-all-parts-pack.txt`).
* Pack edition, parts `kitchen tiers table`, on the final build (after the "Base" label was widened): **`pass=38 fail=0`** (`checks-final-pack.txt`).
* Standalone edition, parts `kitchen tiers`, on the final build: **`pass=35 fail=0`** (`checks-final-standalone.txt`). The standalone Mk I hall shows "Upgrade to Mk 2" with 4 Ardent Energy, as `Economy` prices it.
* The standalone table upgrade, the dispensers and the beacon screen were not run in the standalone edition.

* Kitchen: the one-screen kitchen opens with 26 effect cells; choosing effects, Gather, Make sandwich, the conflict and order-full refusals, Clear, Take back, stew gathering, cooking, closing and reopening the screen, and three disjoint legendary recipes in a Mk IV stew all pass as before the redesign.
* `compose` still equals the v6 implementation on 6000 random Mk IV stew tables of real foods (same=6000, differ=0). The equivalence is limited to Mk IV stew on purpose: below it the rules changed.
* Levels, checked through the real menu on a server hall at Mk I, II and III: **Mk I** has three open food slots and a closed fourth, stew is refused (`stew_locked`), a sandwich lasts 15 minutes (18000 ticks), ×2 is off. **Mk II** stew is open, a sandwich lasts 20 minutes (24000 ticks), the stew lasts 20 minutes and fills 8 servings, ×2 is off. **Mk III** a sandwich lasts 25 minutes (30000 ticks), the stew lasts 25 minutes and fills 12 servings, and the same two foods now make a doubled Vitality. **Mk IV** stew gathers four foods for a doubled legendary and fills 16 servings.
* Table upgrade view (Weapon/Ammo/Attachment/Armor): the Fabrication tab shows its cards; the Upgrade view has one button, dark while the materials are missing; with free materials the same button is live and one click raised the table by one Age.
* Dispensers (bowl and milk) and the Beacon screen still behave as in `MESS-HALL-V7-TESTING.md` (pack edition, all-parts run only).

Screenshots in `docs/validation/messhall-tiers/` were looked at one by one: kitchen at Mk I, III and IV (two columns, calm dimmed effect tiles, chips for minutes / slots / ×2, order cards with ×2), the stew tab at Mk I with its padlock, and the table upgrade view (Age ladder, "Next: ...", material grid, one Upgrade button, enabled and disabled).

## Field Guide

Both pages that changed were read in a real client at 1280 x 720: *Mess Hall & meals* (Sandwich, Cook Pot and stew, the new *Mess Hall levels* and *Doubling (×2)* cards) and *Stations* (the Ages card describing the single Upgrade button). `QaGuide` still passes **21 of 21** checks (menu entries, page order, no overlap, jump by click at scale 2; `checks-guide-pack.txt`).

## Found and fixed during the run

* The first kitchen screenshot showed a truncated "Sandwi…" tab, the Base and Out labels touching, a legendary heading overlapping its hint, a truncated "Picked from your f…" label and a meaningless "Food: 48 / 3" line for sandwiches. All fixed and shown in the final screenshots. The "Base" label still read "Ba…" at Mk I and II in the all-parts run; it was widened and the final runs show it whole.
* The new upgrade button was live without materials; it now stays dark until the materials are present.
* The first QA client refused to join its own server because the protocol constant was changed in only some of its four places; all four now read 28.

## Not tested

* A human balance playtest of the ladder: whether 15 / 20 / 25 / 30 minutes and the Mk III ×2 unlock feel right is the owner's call and untouched by any number here.
* The standalone edition's guide pages for these cards were not re-photographed, and its table upgrade view was not opened (the shared code is the same as the pack run).
* GUI scale 2 and 4, and 4:3 or small windows where the GUI is narrower than the kitchen's 376 pixels. At 1280 x 720 GUI scale 3 the GUI is 427 wide and fits; a 1024 x 768 window at scale 3 (341 wide) would need the GUI scale lowered.
* The original author's v2 to v6 server GameTests. `MessHallV2GameTests`, `MessHallV5GameTests` and `MessHallV6GameTests` were edited so that they compile with the new level rules, but none of them was executed.
* A dedicated server, two players at one hall, and the full Create/KubeJS pack (the Workshop material icons).
* Mk II and Mk III stew was cooked on the server through the menu; eating it and the field timer running out at 15 / 20 / 25 minutes were not played through (the duration is read from the saved meal data).
* Other languages.
