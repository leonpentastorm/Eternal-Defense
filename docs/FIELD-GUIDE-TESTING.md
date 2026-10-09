# Field Guide pass: what was actually tested (project 0.0.9 and 0.0.10)

Same environment as `MESS-HALL-V7-TESTING.md`: Linux container, JDK 17, Forge 47.4.20 development runtime with TaCZ 1.1.8-hotfix2 and TaCZ Attributes 1.4, a real Minecraft client under Xvfb with software OpenGL, 1280 x 720. Create and KubeJS are absent from the development runtime, so the pack run shows the pack text with `{jei}` resolving to "Install JEI for recipe shortcuts".

## Automated, in the build

* `./gradlew --no-daemon --offline build releaseJars` (JDK 17) succeeds for all three modules and both editions; **96 beacon unit tests, 0 failures** (8 new since 0.0.8: 4 in `LangKeysTest`, 4 in `GuideTextTest`).
* `LangKeysTest.everyGuideCardFollowsTheSameFormat`: every card that starts with *What it is* also has *You get*, *How it works* and *How to unlock*, in that order, in both editions.
* `LangKeysTest.guideSentencesStayShortAndPlain`: no guide line over 330 characters and no sentence over 42 words.
* `LangKeysTest.everyRegisteredFeatureHasAGuideCard`: every block and item registered in `ArsenalBeacon.java` has a card or is listed as an ingredient; `Gun Guide` and `Gun Displays` cards are required as well. The registration parser was checked by hand against the source: it finds 29 ids.
* `GuideTextTest`: `[pack]` / `[standalone]` lines reach only their edition, bullets continue the list above, the Reference tab uses the viewer's edition.
* The existing guide tests still pass unchanged in intent (standalone text never mentions pack-only mods, pack text keeps the Create/Essential/FTB instructions, the support page quotes the real numbers).

## Real client (`tools/qa/QaGuide.java.txt`)

Runs of the final build (0.0.10): pack at 1280 x 720, 1600 x 900 and 1920 x 1080, standalone at 1280 x 720, each **21 checks, 21 pass** (`docs/validation/field-guide/client-qa-checks.txt`). The 0.0.9 text was checked the same way before the menu returned: the guide opens from the server like a player's right-click; each of the 14 pages is reached by Next (arrow key) and is the expected one; every page is scrolled with Page Down to its end and photographed (57 to 89 screenshots per run, depending on the window); the side menu has one entry per page, stays inside the panel, does not overlap itself and clears the page column (checked at GUI scale 3 and 2 in every window); at GUI scale 2 a click on the menu jumps to the *Extra gun mods* page; the plant confirmation is opened at scale 3. Fifteen screenshots are kept in `docs/validation/field-guide/`. All 79 per edition were not read line by line: the first and last screens of each page, the Careful notes on the start page, the raid timing and boss wording, the labelled lists, both confirmation and list layouts and the standalone Ardent Energy and Upgrades pages were inspected.

Corrected after the owner's review: raids come about weekly at reward tier 0 and more often as the tier rises, and a boss raid is only a stronger raid, unrelated to timing (the plant confirmation, original and my first rewrite, put them in one sentence). Both editions were re-run on the corrected text (before the menu returned); the plant confirmation was resized to fit at GUI scale 3.

Found and fixed during the run: the label colour leaked over the whole bullet (child components inherit the parent's style); the page title *Gun Guide & Displays* was truncated in the list at scale 2 (renamed *Extra gun mods*).

## Side menu (0.0.10)

* The menu showed as a two-column icon rail in the 427 x 240 GUI (1280 x 720 at scale 3), as a list with 12 px icons in the 534 x 300 GUI (1600 x 900 at scale 3), and as a list with 16 px icons in the 640 x 360 GUI (1920 x 1080 at scale 3, and 1280 x 720 at scale 2). All were looked at.
* Clicking the menu at scale 2 jumped to the right page. Hovering the rail was seen to show a name only in the code path; the tooltip was not captured in a screenshot.
* `LangKeysTest.everyGuidePageHasItsOwnMenuIcon` reads the source: a page without an icon `case` fails.

## 0.0.11 text changes

The Mess Hall page (new *Mess Hall levels* and *Doubling (×2)* cards, Mk I to IV wording on the sandwich, stew and meal cards) and the Stations page (the Ages card, one Upgrade button) changed. `QaGuide`, pack edition, 1280 x 720, was re-run on the final text: **21 of 21** checks. The two Mess Hall cards were read in the screenshots (`docs/validation/messhall-tiers/guide-*.png`). The standalone edition's text for these cards was read in the lang file and covered by the edition tests, but not re-photographed in this round. The beacon unit tests number 96, all passing.

## Not tested

* A new player reading it. Nobody has checked that the copy actually answers their questions; it is written from the code and the design document.
* Facts were checked by reading the code, not by playing each one: Special Forces tiers, the raid warning cadence, Gun Guide's five seconds and default key, the Gun Displays interactions, Raid break stacking, the Mess Hall recipe, dispenser stocking, fire-support types. The numbers on the Support page are the only ones a unit test ties to the code.
* The item tooltips (Defense Beacon red line, Field Guide) were compiled but not looked at in game.
* GUI scale 4, keyboard-only use of the menu and the narrator, the crossbow icon used when Gun Displays is not installed, other languages, dedicated server, two players, the full Create/KubeJS pack, JEI and AppleSkin.
* The original author's v2 to v6 server GameTests are still not run (see the handoff).
* `Gun Guide` and `Gun Displays` run as separate mods; their cards were written from their source and `docs/STANDALONE.md`, not from a run of those mods in this round.
* Packaging: the bundle's JARs were built from the same sources as the tested run but not started on a server.
