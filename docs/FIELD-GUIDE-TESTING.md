# Field Guide pass: what was actually tested (project 0.0.9)

Same environment as `MESS-HALL-V7-TESTING.md`: Linux container, JDK 17, Forge 47.4.20 development runtime with TaCZ 1.1.8-hotfix2 and TaCZ Attributes 1.4, a real Minecraft client under Xvfb with software OpenGL, 1280 x 720. Create and KubeJS are absent from the development runtime, so the pack run shows the pack text with `{jei}` resolving to "Install JEI for recipe shortcuts".

## Automated, in the build

* `./gradlew --no-daemon --offline build releaseJars` (JDK 17) succeeds for all three modules and both editions; **95 beacon unit tests, 0 failures** (7 new: 3 in `LangKeysTest`, 4 in `GuideTextTest`).
* `LangKeysTest.everyGuideCardFollowsTheSameFormat`: every card that starts with *What it is* also has *You get*, *How it works* and *How to unlock*, in that order, in both editions.
* `LangKeysTest.guideSentencesStayShortAndPlain`: no guide line over 330 characters and no sentence over 42 words.
* `LangKeysTest.everyRegisteredFeatureHasAGuideCard`: every block and item registered in `ArsenalBeacon.java` has a card or is listed as an ingredient; `Gun Guide` and `Gun Displays` cards are required as well. The registration parser was checked by hand against the source: it finds 29 ids.
* `GuideTextTest`: `[pack]` / `[standalone]` lines reach only their edition, bullets continue the list above, the Reference tab uses the viewer's edition.
* The existing guide tests still pass unchanged in intent (standalone text never mentions pack-only mods, pack text keeps the Create/Essential/FTB instructions, the support page quotes the real numbers).

## Real client (`tools/qa/QaGuide.java.txt`)

Two runs, pack and standalone, each **17 checks, 17 pass** (`docs/validation/field-guide/client-qa-checks.txt`): the guide opens from the server like a player's right-click; each of the 14 pages is reached by Next (arrow key) and is the expected one; every page is scrolled with Page Down to its end and photographed (79 screenshots per run); at GUI scale 2 the page list appears and a click jumps to the *Extra gun mods* page; the plant confirmation is opened at scale 3. Twelve screenshots are kept in `docs/validation/field-guide/`. All 79 per edition were not read line by line: the first and last screens of each page, the Careful notes on the start page, the labelled lists, both confirmation and list layouts and the standalone Ardent Energy and Upgrades pages were inspected.

Found and fixed during the run: the label colour leaked over the whole bullet (child components inherit the parent's style); the page title *Gun Guide & Displays* was truncated in the list at scale 2 (renamed *Extra gun mods*).

## Not tested

* A new player reading it. Nobody has checked that the copy actually answers their questions; it is written from the code and the design document.
* Facts were checked by reading the code, not by playing each one: Special Forces tiers, the raid warning cadence, Gun Guide's five seconds and default key, the Gun Displays interactions, Raid break stacking, the Mess Hall recipe, dispenser stocking, fire-support types. The numbers on the Support page are the only ones a unit test ties to the code.
* The item tooltips (Defense Beacon red line, Field Guide) were compiled but not looked at in game.
* GUI scale 4, other languages, narrator, dedicated server, two players, the full Create/KubeJS pack, JEI and AppleSkin.
* The original author's v2 to v6 server GameTests are still not run (see the handoff).
* `Gun Guide` and `Gun Displays` run as separate mods; their cards were written from their source and `docs/STANDALONE.md`, not from a run of those mods in this round.
* Packaging: the bundle's JARs were built from the same sources as the tested run but not started on a server.
