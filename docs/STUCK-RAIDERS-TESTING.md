# Stuck raiders (project 0.0.13): what was actually tested

Environment: Linux container, JDK 17, Forge 47.4.20 development runtime with TaCZ 1.1.8-hotfix2 and TaCZ Attributes 1.4, a real Minecraft client under Xvfb with software OpenGL (flat world, creative, pack edition, Create and KubeJS absent). Rules: GDD section 5.2 (*Stuck raiders*, *Marchable corridors*).

## Automated, in the build

* `./gradlew --no-daemon --offline build releaseJars` (JDK 17) succeeds for all three modules and both editions. **124 beacon unit tests, 0 failures** (28 new, `StuckRaiderTest`).
* `StuckRaiderTest` covers the pure decisions:
  * the progress tracker: real progress restarts the clock; a creep under 3 blocks is not progress until it adds up; moving away or standing still never counts; stuck after exactly 25 s; busy raiders are never stuck and being busy restarts the clock; a rescue gives a fresh clock and a 10 s grace window; the grace window alone defers a flag on an old stall; progress keeps the grace and forgets dry attempts;
  * busy: melee reach needs line of sight, firing range (14 blocks) needs line of sight and a ranged raider, a breach counts for 5 s, a defender counts only when a player is near and the raider was hurt, a frozen chunk is busy;
  * the ladder: three rescues, then withdraw on the fourth stall; a boss is always rescued and never withdrawn; no place found: retried twice, then withdrawn (never a boss); the chat line singular and plural;
  * bookkeeping: pruning drops every entry of a gone raider and a reset clears the rest; the withdrawal count is taken once per wave;
  * corridors on fake profiles: a flat dry corridor is clean; water or magma is a violation; only the stretch between the spawn and the zone is judged; a rise over 2 or a drop over 3 is a violation in the direction of the march; a cliff and a lake add up; unknown samples are neither failures nor judged; a distance inside the probed stretch counts only its samples; every wedge centre maps back to its wedge at any distance;
  * rescue place: 24 blocks from every player (exactly 24 is fine, height counts), the stuck wedge is the last resort, the first wedge that yields a place wins;
  * the diagnostics line format, and that the diagnostics are off by default.

## Real server (GameTests in a real client's integrated server)

`tools/qa/QaStuck.java.txt` boots a flat world on the real client and runs `StuckRaiderGameTests` (opt-in, see below) on its integrated server. The tests build one 13 x 13 chunk stone platform around a fake beacon and use their own `CampaignData`, so the real campaign is never touched. A raider that never makes progress is a husk with no AI and no gravity (or one sealed in a stone cell). **7 of 7 pass** (`docs/validation/stuck-raiders/gametests-and-diagnostics.txt`, which also holds the real `[stuck]` diagnostics lines of that run):

1. a raider sealed in a stone cell (normal AI) is moved to the staging ring after about 25 s (window 480 to 660 ticks), outside the zone plus clearance, in another sector than the one it was stuck in, with one rescue counted on the mob, no leftover fall or path;
2. a raider on an isolated 6-block pillar is moved off it in the same time window, onto the ground of the ring;
3. through the real `ArsenalBeacon.raid` loop (reflection, as the older campaign tests do): three rescues, then the fourth stall withdraws the raider, the first wave of several completes (its supply reward is paid) about 2,000 ticks after it started, far below the 24,000-tick timeout, with no beacon damage, one withdrawal counted and announced, nothing left in the trackers;
4. a boss is moved on every stall (at least five times, past the point where an ordinary raider is withdrawn) and is never withdrawn;
5. a vex (glider) and a husk under a parachute are left alone for 1,100 ticks: not moved, not tracked, no rescue counted;
6. one of twelve sectors has a lake across its centre ray: its corridor score has violations and the other eleven are clean; 60 calls of `RaidSpawns.find` never return a spawn in the lake sector; at most 12 probes were made;
7. every sector has water across its ray except one that keeps only half of it: 40 calls of `find` always return that sector (the fewest violations) and never fail to find a place.

Found and fixed with these runs: the first runs crashed the server because the harness ticked the test framework a second time (a development run already ticks it; the runner now asks Forge); tests were registered twice and the two copies pruned each other's trackers (in production there is one campaign, so this only affected the test harness).

How to run: `tools/qa/setup-qa.sh /tmp/claude-0/qa pack QaStuck`, then `xvfb-run ./gradlew --no-daemon --offline :arsenal-beacon:runClient` in that copy, and wait for `QA_SUMMARY`. On a server, `-Darsenal.stuckTests=true` registers the tests and `/stuck-raider-test` (permission level 2) starts them.

## Not tested

* A real raid on real (non-flat) terrain. The thresholds (25 s, 3 blocks, 3 rescues) and the corridor limits are estimates; the `[stuck]` diagnostics are the tool for tuning them.
* The victory path of the withdraw case (the final wave): it pays Create items that this development runtime does not have, so the test ends on a non-final wave. The older `CampaignEightGameTests` exercise that path in a full environment.
* A dedicated server, two players (the player-distance rule was tested only as a pure function; the GameTests have no player), the pack with Create/KubeJS, other dimensions.
* Siege-creeper raids through the new tracker (creepers go through it; their ignite code is unchanged and was not exercised).
* A raider that is stuck while players fight it (the "hurt recently" exemption is unit-tested as a function only).
* The legacy v2 to v6 GameTests of the original author (still not run; see the handoff).
* The Field Guide line was written to the style rules and checked by `LangKeysTest`/`GuideTextTest`; it was not looked at in the game.
