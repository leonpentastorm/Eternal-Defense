# 1.1.21: what was tested

The owner's request after 1.1.20, on `feature/power-defense`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-17). Evidence: `docs/validation/release-1.1.21/`.

## What was asked, and how it was read

* **"Each time you defeated a wave should add 5 minutes into the raid time out."** A raid failed after a fixed 20 minutes counted from its first wave (`raidTicks > 24000` in `EternalDefense.raid`). The guide and the GDD never mentioned this limit. Read as: the same clock, with 5 more minutes on the limit for every wave cleared. During wave n the limit is 20 + 5 (n - 1) minutes: 20 in wave 1, 25 in wave 2, up to 55 in the last wave of an 8-wave raid. A timeout is still a defeat, and the beacon still loses a fifth of its health.
* So players learn about the limit, chat says when a wave (not the last) is cleared and how many minutes are left, rounded up: "Wave 1 cleared: +5 minutes on the raid clock. 15 minutes left to win." The Field Guide's raids page has a Careful line about the limit.

## Unit tests

`:eternal-defense:test` **546 pass** (542 before; new `Release1121Test`, 4 tests).

* `everyClearedWaveAddsFiveMinutes`: 20, 25 and 55 minutes for 0, 1 and 7 cleared waves; a negative count (wave 0) counts no wave cleared; for every tier, the last wave's limit is 20 + 5 x (waves - 1).
* `theMinutesLeftRoundUpAndNeverGoBelowZero`: 20 at the start; 15 at minute 10 with one wave cleared; one tick left still reads 1; never below 0.
* `theRaidUsesTheClockAndSaysSo`: the raid checks `raidTimeLimit(d.wave-1)`, the fixed 24000 is gone, and the chat line goes out before the next wave starts; the English text.
* `theGuideQuotesTheClock`: the raids page quotes the 20 and 5 from `Rules` and says what a timeout costs.

## Real client

`tools/qa/QaRelease1121.java.txt` (Create in the run) in a new flat world at 1280 x 720. A Mk I beacon and a tier-0 raid (3 waves) driven by the mod's own raid tick. The harness sets the raid clock and holds a wave open by keeping its raiders from spawning. It clears a wave by emptying it, and the mod then runs its normal wave-cleared path. Chat is read as the client receives it.

### Run history

* Run 1: 7 of 9. The two wave-1 checks read chat in the same client tick the server started wave 2, a moment before the line arrived (the next log lines show it). The checks now wait for the line.
* Run 2: 9 of 9, but the guide picture was taken under the DEFEAT banner. The harness now waits 10 seconds before it.
* Run 3: **9 of 9**. The pictures and `checks-release-1.1.21.txt` are from this run.

### The checks (run 3)

* Wave 1 cleared at minute 10: chat reads "Wave 1 cleared: +5 minutes on the raid clock. 15 minutes left to win.", then "Wave 2 / 3 incoming." (`r1121_wave1_cleared`).
* Wave 2 at 24166 ticks (20 minutes 8 seconds, past the old limit): the raid goes on.
* Wave 2 cleared at minute 16.7: "Wave 2 cleared: +5 minutes on the raid clock. 14 minutes left to win."
* Wave 3 at 30057 ticks (past 25 minutes): the raid goes on.
* Wave 3 set just under minute 30: the raid ends on tick 36001, the first past 30 minutes. Chat says "Raid timed out." and the beacon is at 800 of 1000 (`r1121_timed_out`; the HUD card updates once a second and still shows 1000 in that frame).
* The Field Guide's raids page shows the new Careful line (`r1121_guide_raids`).

## Jars

Built with `build releaseJars`: `eternal-defense-1.1.21.jar`, `eternal-defense-1.1.21-novoice.jar`, `eternal-defense-gun-guide-1.1.21.jar`, `eternal-defense-displays-1.1.21.jar`. Test zips: `dist/Eternal-Defense-1.1.21.zip` and `-novoice.zip`, each with a `.sha512`.

## Network

Protocol stays **43**. No packet changed, and nothing new is saved: the limit is worked out from the wave number, which the save already keeps.

## Noticed, not changed

* The DEFEAT banner reads "The beacon was overrun. Repair it and rebuild." for every defeat, including a timeout (as before). The chat line gives the real reason.

## Not tested

* A full raid fought to its clock with real raiders (the harness held the waves and moved the clock).
* A raid saved mid-wave and loaded after the update.
* A server and a second player.
