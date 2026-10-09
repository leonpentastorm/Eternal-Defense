# Raider Gates and the kitchen changes (project 0.0.14): what was actually tested

Environment: Linux container, JDK 17, Forge 47.4.20 development runtime with TaCZ 1.1.8-hotfix2 and TaCZ Attributes 1.4, a real Minecraft client under Xvfb with software OpenGL (flat world, pack edition, Create and KubeJS absent). Rules: GDD section 5.2 (*Raider Gates*), `docs/MESS-HALL-TIERS.md`. The earlier stuck-raider tests are in `docs/STUCK-RAIDERS-TESTING.md`.

## Automated, in the build

* `./gradlew --no-daemon --offline build releaseJars` (JDK 17): all three modules and both editions build; **143 beacon unit tests, 0 failures** (19 new since 0.0.13: `RaiderGateTest` and the adjusted language and rules tests).
* `RaiderGateTest` (new) covers the pure decisions of the gate: the terrain probe on fake profiles (lava and magma always block, a drop of 6 blocks blocks and 5 does not, a long gentle slope is not a chasm, a rise is never a drop, 8 wet blocks in a row block and 7 do not, a 3-wide pond must not trigger, two ponds separated by land are not one lake, unknown samples never block and break a wet run, the width of a wet stretch at a sample spacing of 2, lava outranks a drop and a drop outranks water); the channel clock (8 s undisturbed; each hurt second adds 3 s up to 10 s in total, 18 s at most; a rounding difference is not damage; the destination shows 3 s before the end); gate assignment (nearest gate within 8 blocks, none beyond, exactly 8 still joins, eight channelers then wait, the gate stands to the side of the raider); the ladder in gates (three, then withdraw; a boss gets another).
* `StuckRaiderTest` (0.0.13, adjusted): progress timer and busy rules, grace window, ladder and boss exception, no-destination retries, bookkeeping and pruning, destination filter (24 blocks from every player, the stuck sector is the last resort), spawn-corridor scoring.
* `LangKeysTest` / `MealRulesTest` (adjusted): the new kitchen keys exist; the guide quotes 3/5/7/9 servings; the tiers have 3/5/7/9 servings; the Raids card explains the red gate in one short line in both editions; the existing format, density and number tests still pass.

## Real server (GameTests in a real client's integrated server)

`tools/qa/QaStuck.java.txt` boots a flat world on the real client and runs `RaiderGateGameTests` (opt-in, see the handoff) on its integrated server: a 13 x 13 chunk stone platform around a fake beacon, each test with its own `CampaignData`, each in its own batch (the channel state is global). **11 of 11 pass** (`docs/validation/raider-gates/gametests-and-diagnostics.txt` holds the log with the real `[stuck]` lines; `gate-day.png` and `gate-night.png` show a gate and a husk):

1. a raider sealed in a stone cell (normal AI) stops after about 25 s, a gate appears beside it, it channels for about 8 s and then stands outside the zone plus clearance, in another sector, with one gate counted on the mob, no leftover fall or path, and the gate is gone;
2. a raider with normal AI and lava 4 to 8 blocks ahead on its line opens a gate **at once** (reason `probe:lava`, not after 25 s), stays within 0.6 blocks of where it stood although its AI keeps asking to walk away every tick, and exactly one gate stands beside it;
3. lava, magma, a 7-deep chasm and a 12-wide lake each trigger the look-ahead (`probe:lava`, `probe:lava`, `probe:drop`, `probe:fluid`), a 3-wide pond does not;
4. damage delays the channel (hurt every second while channeling: it takes longer than 8 s) and the delay is capped (at most 18 s in all);
5. killing a channeler cancels its channel and the empty gate goes away; the raider never went through;
6. nine stuck raiders in a cluster share **one** gate, eight channel at once and the ninth waits; the ninth comes out a whole channel after the first; the gate goes away with the last raider;
7. through the real `ArsenalBeacon.raid` loop: three gates, then the fourth trigger withdraws the raider; the first wave of several completes (its supply reward is paid) about 2,500 ticks after it started, far below the 24,000-tick timeout, with no beacon damage, one withdrawal counted and announced, and no channel or gate left;
8. a boss goes through gate after gate (at least four, past the point where an ordinary raider is withdrawn) and is never withdrawn;
9. a vex and a husk under a parachute are left alone for 1,100 ticks: not moved, not tracked, no gate opened, none counted;
10. and 11. the spawn-sector tests of 0.0.13 (a lake sector is never chosen while clean sectors exist; when every sector has water the one with the fewest violations is used).

Found and fixed during these runs: the first run of the gate tests shared one batch and the channel clean-up of one test removed the channels of the others (the real game has one campaign, so only the test had to change); the first confirmation overlay was drawn below the item icons of the slots and was moved above them (looked at in a screenshot of the first version; the corrected overlay was photographed in the last kitchen run listed below).

## Kitchen (real client, `tools/qa/QaKitchen.java.txt`, 1280 x 720, GUI scale 3, pack edition)

Final run, parts `kitchen stew tiers` (one hall's Sandwich and Stew flows, a Mk IV stew, and Mk I to III through the real menu): **50 of 50 checks pass** (`docs/validation/raider-gates/kitchen-checks.txt`), including: the Mk IV stew fills **9** servings and Mk II and III stew fill 5 and 7; a pot that still has stew is not an error, cooking is offered; the screen asks before it throws the stew away and the pot is unchanged; **Keep it** changes nothing; **Replace stew** refills the pot and the notice says "replaced"; the Cook Pot gives block light 9 on its upper cell. Screenshots: `kitchen-replace-question.png` (the question, drawn above the slot items after a first version that was drawn below them) and `cook-pot-board-night.png` (at night, from the north the board text is readable: "MENU, Vitality Stew, +8 max Health ×2, +2 armor, 7 servings"; from the south the board is not visible, which is the model). An earlier run of the table-upgrade part (38 checks) also passed on the same code apart from these changes; the table and dispenser parts were not re-run after this round's final edits.

## Not tested

* A real raid on real (non-flat) terrain: every threshold (25 s, 3 blocks, 8 s channel, 3 gates, drop 6, 8 wet blocks) is an estimate. The `[stuck]` log is the tool for tuning.
* The red particle ring on the channeler, the telegraph particles and the portal sounds were never looked at or listened to (only the gate sprite was photographed); they are in the code and in the log events.
* A dedicated server, two players (the 24-block rule is checked as a pure function and by the destination search with no player present; no GameTest puts a real player near a destination), other dimensions, the full Create/KubeJS pack.
* Wide shallow lakes that a zombie could wade through are gated like deep ones (the rule is "8 wet blocks"), by design of the request.
* The standalone edition: the kitchen confirmation, the new servings and the lamp were run in the pack edition only; the standalone edition builds and its guide text passes the edition tests.
* The legacy v2 to v6 server GameTests (their servings assertions were adjusted so they compile; the stew cases are out of date for other reasons and were not run).
* Other GUI scales or window sizes for the confirmation overlay.
