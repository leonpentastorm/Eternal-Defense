# Mess Hall v6 UI pass: what was actually tested (project 0.0.8)

Environment of this round: Linux container, JDK 17, network access open (Sponge, CurseMaven and Forge reachable), Forge 47.4.20 development runtime with TaCZ 1.1.8-hotfix2 and TaCZ Attributes 1.4 from CurseMaven, a real Minecraft client under Xvfb with software OpenGL, 1280 × 720, GUI scale 3 (a 427 × 240 GUI, the smallest the automatic scale allows). Pack flavor of the beacon with Create and KubeJS absent (the QA copy makes them optional), so a few Create-priced items show as Air in the Workshop Fabrication tooltip; this is a property of the test setup, not of the mod.

## Automated, in the build

* `./gradlew :arsenal-beacon:test` passes: **88 beacon unit tests** (74 existing, 12 new in `MealPlannerTest`, 2 new in `LangKeysTest`). The planner tests include a brute-force completeness check (150 random pantries: the planner finds a table exactly when one exists), soundness of every planned table against `MealPlanner.evaluate` for all 26 effects × both modes × four tiers, every one of the 325 effect pairs in both modes at Mk IV, Mk IV doubling, Mk I food-kind limits, scarce packs, one-item stacks and a speed bound (a feasibility check stays under 5 ms).
* `LangKeysTest` now also scans the sources for kitchen, dispenser and milk keys that reach `Ui.t` through helpers or are sent by the server in full form. It caught one real mistake during the round (`kitchen.footprint` deleted while still used).
* `./gradlew --no-daemon build releaseJars` (JDK 17): see the last section.

## Real client, scripted (`tools/qa/QaKitchen.java.txt`)

One run on the final code: **41 checks, 41 pass, 0 fail** (`docs/validation/messhall-v7/client-qa-checks.txt`, screenshots next to it). The client creates a flat world, builds a beacon zone, a Mk IV Mess Hall, a Cook Pot, a Bowl Dispenser and a Milk Dispenser, gives the player a pack of vanilla foods and then drives the real screens (real mouse clicks on widgets) and the real blocks (use, crouch-use).

* **Differential check of `compose`**: the v6 composition copied verbatim into the scenario and the new one give identical meals and food counts on 6,000 random tables built from the real food traits (random stews, tiers, automatic and chosen orders). The only difference by design: where v6 rejected an order as `selection` because a chosen effect had no food yet (about 1,800 of the 6,000), the new code reports `missing` and keeps the rest of the order.
* **Kitchen screen**: 26 recipe icons are present; with nothing chosen the primary button is disabled and the coach names the problem; the pack-aware status marks Vitality addable and Recovery, Hearth and others without food; one click chooses a recipe and stores it on the hall; Gather places foods and the base bread so the table is ready; Make sandwich yields a sandwich with exactly that effect and spends the fillings; Firepower is refused next to Vitality (shared Protein) with a toast; Clear returns to automatic; Firepower and Mobility can be ordered together; a third recipe in a sandwich is refused; Gather shares the apple between two recipes (three kinds of food); Take back empties the table into the pack; stew mode is stored; Mk IV stew Gather fills six kinds (twelve units), both recipes doubled; Cook stew fills the pot with 16 servings; closing and reopening the hall keeps the order and mode; three disjoint legendary recipes can be ordered in a Mk IV stew.
* **Bowl Dispenser**: taking a bowl shows `Took a bowl — only 2 left`, the bowl arrives; the last bowl and the empty dispenser have their own messages; crouch-use with empty hands (sneaking through the key binding, within 8 blocks) opens the new screen.
* **Milk Dispenser**: with no effects it says so and spends nothing; with Poison, Strength and a Firepower Stew it reports `Milk cleared Strength, Poison, your Firepower Stew — 5,500 mB left`, spends exactly one dose, clears every effect and sends one chat line about the lost meal; its screen opens.
* **Beacon**: the Workshop Fabrication tab opens with all eleven cards. **Weapon table**: the Upgrade view opens and was inspected in a screenshot (no scripted assertion).
* Screenshots inspected by eye: kitchen (empty, hover tooltips, chosen, gathered, conflict toast, two chosen, stew gathered, cooked, three legendary), both dispenser screens (with hover tooltips), fabrication tab (with cost tooltip), table upgrade view.

## Not tested

* The original author's v2 to v6 server GameTests (`run-v6-server.py` needs his retained runtime). `MessHallV6GameTests` was edited for the new rules (an order of chosen recipes is accepted before the foods exist; milk is not spent when there is nothing to clear) and its changed cases were **not executed**. A development-server replay was tried and abandoned (see the handoff).
* A dedicated server and a second human player. Two cooks opening the same hall share the stored order by design, but it was not observed.
* The standalone edition (no Create) and GUI scales 2 and 4; the full Create/KubeJS pack; JEI, AppleSkin and other tooltip mods with the new tooltips; narrator and key-only navigation of the recipe palette (the tiles are real focusable buttons with the effect name as their message, not tried with a screen reader).
* Sound and particle effects are triggered on the server (verified only that the messages and state changes occur; no audio device exists in the container). Whether the sounds suit the feel is a playtest question.
* Balance: nothing about numbers, recipes or prices changed.
* Packaging: see below.

## Build and package

* `./gradlew --no-daemon build releaseJars` (JDK 17) succeeds for all three modules and both editions; the beacon module reports 88 tests, 0 failures, 0 skipped. Jars stay 0.21.0 (beacon, Gun Guide) and 1.1.0 (Gun Displays).
* `tools/release/package-messhall-v7.py` builds `dist/Arsenal-MessHall-v6-ui-0.0.8-test.zip`: pack and standalone folders with four JARs each, the unmodified TaCZ Attributes 1.4 JAR (from the CurseMaven file 8470731 that the build already uses) in each folder, `SHA512SUMS`, `THIRD-PARTY.md` and the MIT notice, this document, `MESS-HALL-V7-UI.md` and the screenshots. The zip's own SHA-512 is in the `.sha512` file beside it (a bundle cannot carry its own hash); `SHA512SUMS` inside lists every file. TaCZ itself and LesRaisins are not redistributed.
* The bundled JARs were not started on a dedicated server or in the full Create/KubeJS pack in this round; the real-client scenario ran from the development sources of the pack flavor.
