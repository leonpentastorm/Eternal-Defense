# The owner's raid and Tactical Operations list (0.0.21): testing

## How it was run

* **Unit tests** (`:arsenal-beacon:test`), JDK 17.
* **Real client** (Forge 47.4.20 development runtime, `xvfb`, 1280 x 720), driven by `tools/qa/QaHunt.java.txt` (`tools/qa/setup-qa.sh <dir> <pack|standalone> QaHunt`, then `runClient`), on a fresh **normal** world (seed 20201010). It first runs the hunt GameTests (`HuntGameTests`, 13 now) in the client's integrated server, then walks through the round like a player: the board, the table, Accept, the status card with an empty hand, the map and the shovel, a Warlord's arrival (timed), the clear, a Starshell, a raid with gate spawns, a due raid by day, Start raid by day, nightfall, armed raiders, an ordinary zombie during the raid, the alarm, Garrison lookups. The QA teleports the player and kills by command; nobody walked or fought.
* **Sound.** The client ran with OpenAL Soft's `wave` backend, so the game's mixed output was written to a WAV file; `tools/audio/find_sfx.py` finds each start of the alarm in it. **Nobody listened.**
* **Regression:** the 0.0.14 stuck-raider GameTests (`tools/qa/QaStuck.java.txt`, pack edition, flat world), because raid skeletons and pillagers now get a different attack goal.

## Results

### Unit tests: 219 pass, 0 fail

* New `RaidRoundTest` (17): raid skeletons and pillagers are armed before they join the world and only an armed one gets the ranged goal; the raid hours (13000 to 21000) and the time until them; Start raid by day books the raid (`raidRequested` saved, synced, cleared by `begin`); the raid draws every hostile mob within zone + 48 blocks, between raids only within 24 and in sight; the alarm is cut before the third beep (`tools/audio/import_sfx.py`: 0 to 0.495 s, padded to 0.6 s; the file measures 0.600 s with `ffprobe`) and repeats every 22 ticks (0.6 s clip, 0.5 s pause); the anchor search order (nearest first, a disc of 16) and the spawn ring (8 to 12); the arrival code has no spot search left and spawns one mob a tick; a map is stale only when its mission is over and its offer gone; the mission line needs the map or the shovel in hand; the table's colours (blue, purple, gold, the chosen X red); offers are never placed on water; Starshell Haste lasts 30 s; protocol 30; the pack shop sells the standalone edition's rare materials at the same prices; both shops sell iron in bulk, a little cheaper per ingot; an untouched 0.0.20 shop file of either edition gets the new default and an edited one is kept.
* Changed: `RulesTest` (the interval is 10 active days at tier 0 down to 3), `HandTunedRoundTest` (the alarm), `TacticalOperationsTest` (the new numbers and guide lines, `MissionSync` with the id).

### Hunt GameTests (`HuntGameTests`, in the real client's integrated server): 13 pass, 0 fail, in every run of both editions

The nine of 0.0.20 (see `docs/TACTICAL-OPERATIONS-TESTING.md`) and four new ones:

10. **One block is enough.** The objective sits in a pool of water 17 blocks wide with one stone block left in the middle: that block is the anchor, the whole warband (9 to 12 mobs in the runs) comes out on it, every mob knows its anchor, carries the red mark and has the rush goal; killing them clears and pays.
11. **No ground.** The same pool with no block left: the mission ends, its offer leaves the board and a new one is made, the player gets one open return gate in front of them, the map burns, no warband.
12. **Armed raiders.** A raid skeleton (tier 3 and tier 1) holds a bow and a raid pillager a crossbow, each with the ranged beacon goal; the skeleton walked from 30 blocks to 11 blocks from the beacon in 10 seconds and opened no gate.
13. **Raid cover.** Between raids a zombie 19 blocks away comes for a visible beacon, not behind a wall, not from 36 blocks; during a raid it comes through the wall and from 36 blocks too.

### Pack edition, real client (`docs/validation/raid-round-0021/checks-pack.txt`): 68 pass, 0 fail

### Standalone edition, real client, final run (`checks-standalone.txt`): 68 pass, 0 fail

Both editions, the same checks:

* **Mission line in hand only** (`*-hud_empty_hand.jpg`, `*-hud_map_in_hand.jpg`, `standalone-hud_shovel.jpg`): 120 blocks from the base with an empty hand the client knows the mission but shows no line; with the mission map in hand the line shows ("OP SABLE SENTINEL | Warlord | 648 m W | not found yet"); at the beacon with the shovel it shows under the beacon card.
* **Table colours** (`*-table_colours.jpg`): Warlords gold, the Patrol blue, the selected X red.
* **Arrival** (`*-arrival_gates.jpg`, `standalone-arrival_at_anchor.jpg`): the anchor was the objective's own block; the warband (12 to 14) came out of red gates (9 in the first run) on the ring and gathered in the small zone (the farthest mob 5 blocks from the anchor a few seconds later); every warband mob was in the client's set of red marks.
* **Server tick time at arrival.** On this machine an idle server tick already takes 27 to 34 ms on average with peaks of 75 to 79 ms (the software-rendered client shares the CPU), so the QA compares with the idle ticks of the same run. Final runs:

  | Edition | Idle (mean / slowest) | Spawning the warband | After it is out |
  | --- | --- | --- | --- |
  | Pack | 31 / 75 ms (64 ticks) | 35 / 58 ms (11 ticks) | 29 / 82 ms (70 ticks) |
  | Standalone | 27 / 79 ms (62 ticks) | 39 / 59 ms (13 ticks) | 26 / 51 ms (66 ticks) |

  Spawning adds 4 to 12 ms to the mean tick here for half a second; no spawning tick was slower than the slowest idle tick; once the warband is out the ticks are back to idle. The first run's single check ("every tick under 50 ms", over a window that also held the teleport) failed at 72 ms and was replaced by these, because idle ticks on this machine already exceed 50 ms (`checks-standalone-first-run.txt`, `checks-standalone-second-run.txt`).
* **Map burning** (`*-map_burned.jpg`): the player carried the Warlord's map; on the clear it was gone and chat said "Your map of OP AMBER BASTION burned: that operation is over." (The abandoned first mission's map stays: its offer is back on the board.)
* **Starshell:** in the light the player has Haste II and nothing else, with 592 to 594 ticks left (about 30 s); 10 s after walking 40 blocks away it is still on with 385 to 386 ticks left (`standalone-starshell_haste.jpg`).
* **Night only:** a raid that was due waited through 5 s of noon (phase still preparation). The Raid break tab by day (`standalone-raid_tab_day.jpg`); pressing **Start raid** booked it: the screen said "Raids only come at night. Your raid starts at nightfall (in about 6 min) if someone is near the beacon.", the button read "Raid at nightfall" greyed out (`*-raid_booked_button.jpg`); at `time set 13000` the raid began at once and the booking was spent.
* **Armed raiders** during that raid: the skeleton held a bow and the pillager a crossbow; the skeleton walked from 30 to 11 blocks from the beacon in about 10 s and opened no gate (`*-raid_skeleton.jpg`).
* **An ordinary zombie** (no raider) on a stone bridge 64 blocks from the beacon (40 past the zone's edge) walked to 33 blocks in 10 s during the raid. The first run placed it beyond the edge of the test platform and it fell; the check passed once the QA laid the bridge.
* **Alarm:** with the beacon hit, the alarm started at client ticks 22 apart in every run (`[5057, 5079, 5101, 5123]` in the final standalone run). In the recorded audio (`audio-pack.txt`, `audio-standalone.txt`) the alarm's starts are 1.06 to 1.14 s apart (once 1.34 s in the standalone run: client ticks run late on this machine, and the period is counted in client ticks); `*-raid_alarm.jpg` shows the banner during the night raid.
* The 0.0.20 checks all pass again (board, table, scan, Accept, Abandon, the map's red X and scale, the Warlord's captain, the clear and its jingle, the cannon card, the Starshell flare and incoming sound, gate spawns, the speed burst, the chat line once, `/arsenal test-gate`, Garrison lookups: 5 of 10 areas, each confirmed, no chunk loaded, slowest 32 to 35 ms; 63 ms in the first run).

### Regression: the stuck-raider GameTests (`stuck-raider-regression.txt`)

See the file (pack edition, flat world).

## Not tested

* **Nobody listened** to the alarm or anything else.
* **The owner's arrival lag** was not reproduced: the QA arrives by teleport on a stone platform, and the measured cost is for this machine. Walking or flying in over real terrain, on the owner's machine, with the full pack, is the test that matters.
* A whole raid fought at night to its end; a raid booked by day reaching nightfall through the real day cycle (the QA used `time set`); the 10-day interval (unit tests only).
* Skeleton and pillager shots at the beacon (damage, aim): the QA checks that they hold their weapons and walk in.
* Offers off water in a real world (unit tests and the GameTest pool only; the four offers of the QA board were not checked against their biomes); an old offer on water being replaced (1200 ticks); the return gate over a real ocean.
* Map burning at login and for a map picked up from a chest.
* The Exchange screen with the new lines; a real 0.0.20 shop file being upgraded (unit tests only).
* A dedicated server and two players; the red marks seen by a second player.
* Starshell Haste with the full pack's other mods: nothing in this repository removes it, but another mod could.
