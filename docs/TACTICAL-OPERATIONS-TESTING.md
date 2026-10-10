# Tactical Operations and the owner's raid list (0.0.20): testing

## How it was run

* **Unit tests** (`:arsenal-beacon:test`), JDK 17.
* **Real client** (Forge 47.4.20 development runtime, `xvfb`, 1280 x 720), driven by `tools/qa/QaHunt.java.txt` (`tools/qa/setup-qa.sh <dir> <pack|standalone> QaHunt`, then `runClient`). It makes a fresh **normal** world (seed 20201010, not a flat world, so the satellite scan and the Garrison lookups meet real terrain and real structure placement), runs the opt-in hunt GameTests (`HuntGameTests`) on its integrated server, then walks through 0.0.20 like a player: places the Command Table and the Satellite Beacon, fills a Mk III board, scans, opens the table, accepts and abandons, reads the HUD, holds the map, runs a Warlord to its end, opens the cannon menu, calls a Starshell, starts a raid with no natural ground left, and runs `/arsenal test-gate`. A listener on the client's sound manager logs every sound of the mod the client really starts (`QA_SOUND`), and the chat it shows (`QA_CHAT`).
* **Sound.** The client ran with OpenAL Soft's `wave` backend, so the game's real mixed output was written to a WAV file; `tools/audio/find_sfx.py` finds when each effect starts in it. **Nobody listened to it.**

## Results

### Unit tests: 202 pass, 0 fail

* New `TacticalOperationsTest` (23): the constants the spec asks for; offers per satellite Mk (2/3/4) and range (256/512/1024); offers beyond the zone + 150, at least 40 degrees apart and inside the border; a structure qualifies only inside the window; offers on one board get mixed classes (found by the real client: every offer of a board had the same class, because `Random` seeded with close seeds gives correlated first draws; offers now use a SplitMix64 mix); codenames deterministic and varied; warband sizes (+2 per extra player, at most +8); the pay formula (a share of a raid's guaranteed Ardent Energy) and that coins from a hunt never sell back for Ardent Energy; the satellite buys choice, not income; no mission accepted in a raid or the day before one; a finished offer comes back at dawn, one a day; the board fills once and only grows with an upgrade; the whole state machine (offered, active, engaged, cleared, paid once); a warband left alone scatters and another one comes; abandoning puts the offer back and never hands a second flare; an unreachable objective is replaced; mobs lost without dying are spawned again, never written off; the board survives a restart (save and load); the scan is live where loaded, stale where seen and a biome elsewhere; map columns encode negative heights and shade like vanilla maps; the mission map holds the base and the objective on one sheet; the Field Guide quotes the real numbers; every new packet round-trips.
* New `RaidFixesTest` (6): gate spawns only after 10 s without natural ground, rings from the edge of the active chunks in to 12 blocks past the zone, never inside it; every raid enemy (ordinary, Special Forces, the boss) gets Speed II for 10 s and a gate spawn opens a gate, hunt warbands too, and the old repeating line is gone from the code; a spawn gate stands 1.2 blocks behind its enemy, straight away from where it is seen, level, is drawn 1.6 times bigger and stays 5 s; the Cryo Shell reaches 7.8 blocks (napalm still 4.8), 15.6 wide and still 2 high; the cannon card's numbers for every fire support at level 0 and with upgrades (rounds, interval, damage per round, total, width, height, duration); every fire support has an Effect, Damage and Area line, the Starshell's says Haste II and not Regeneration, the Healing Barrage's damage line says None.
* Changed: `RadioAndWhistleTest` and `HandTunedRoundTest` (the incoming sound is back, 5.35 s, ending on the impact 5.15 s after the shot; the shell and bomb whistles are gone), `LangKeysTest` (the two new Field Guide cards, the cannon card keys).

### Hunt GameTests (`HuntGameTests`, in the real client's integrated server): 9 pass, 0 fail, in every run of both editions

1. nothing spawns while the player is 224 blocks away; when they come within 64 blocks the whole warband comes out (a Patrol's size), every mob tagged with the warband's serial, never a raider, never despawning, leashed to and spawned near the objective; the last death clears and pays once (a second completion pays nothing) and the offer leaves the board;
2. abandoning sends the warband away and taking the offer again gives no second flare;
3. when its chunk loads from disk, a mob of a scattered warband or of a mission that no longer runs is discarded, and a mob of the running warband stays;
4. the table refuses with the satellite too far or a roof over the dish (NO UPLINK; open sky again links it), during a raid (RAID IN PROGRESS) and in the day before one (RAID WARNING), accepts once all is clear, allows one mission at a time, and a raid does not end the running mission;
5. the blocks only go inside the zone, one of each per base;
6. hunt mobs do not burn in daylight, never attack the beacon and drop no Ardent Energy;
7. a warband left alone scatters;
8. the scan reads only loaded chunks (48 far chunks watched under the image: none loaded by the scan) and keeps one job at a time;
9. a Garrison lookup never loads a chunk.

### Standalone edition, real client, final code (`docs/validation/tactical-operations/checks-standalone.txt`): 45 pass, 0 fail

* **The board** (satellite Mk III, a real normal world): 4 offers, at least 40 degrees apart (closest 44.9), between the zone + 150 and 1024 blocks; it filled over a few passes, one Garrison lookup per server tick. Around this beacon (forest and ocean) no Garrison candidate passes the biome filter, so Garrison rolls fall back to Patrols here (this run's board: four Patrols; the pack run's: three Patrols and a Warlord; earlier runs: two and two, one and three). See the Garrison lines below for land where they are found.
* **The table** (`standalone-table_region.jpg`, `-table_base.jpg`, `-table_card.jpg`): the screen opens with the scan image (Region and Base views), the offer rows and the side card; pressing **SCAN MAP** with the table open plays the sweep, and the done pings when the scan finishes; **Accept** starts the mission, hands the player the mission map and a Return Flare, and refreshes the screen in place (`-table_accepted.jpg`); **Abandon** asks first (`-table_abandon_confirm.jpg`).
* **The status card:** away from the base the mission line shows alone (`-hud_mission_away.jpg`: "OP GHOST SPEAR | Patrol | 378 m E | not found yet"); at the beacon with the shovel it sits under the beacon card (`-hud_status_card.jpg`).
* **The mission map** (`-mission_map.jpg`): the beacon and the red X on one sheet (scale 2), the red X at the objective (read from the map's data on the server).
* **A Warlord** at the objective: the warband comes out of red gates (4 gates, all drawn as big spawn gates on the client: `-warband_gates.jpg`), one glowing captain named after the operation with 152 health (twice a heavy's), 11 in all; killing it clears and pays the mission, chat tells everyone, and the mission-cleared jingle plays (`-cleared_chat.jpg`).
* **The cannon menu** (`-cannon_card_starshell.jpg`, `-cannon_card_bunker_top.jpg`, `-cannon_card_bunker_scrolled.jpg`): the card shows Effect, Damage and Area; the Bunker Buster's is longer than the box and scrolls (101 px of text to scroll; one wheel step moved it 22).
* **A Starshell** called by a real flare: the incoming sound started on the client while the player looked away; a player in the Starshell's light gets **Haste II and no Regeneration** (the effects read from the server's player: `effect.minecraft.haste 2` only; `-starshell_effects.jpg`).
* **A raid with no natural ground** (the QA removes the ground far out): after 10 s the first reinforcement came out of a gate 44 blocks from the beacon with Speed II (`-raid_gate_spawn.jpg`: the zombie in front of its gate); every raid enemy had the speed burst; chat said the gate line once in the wave and never the old "Reinforcements are waiting" line.
* **`/arsenal test-gate`** opens a gate with a zombie standing in front of it (`-test_gate.jpg`).
* **Garrison lookups over land** (10 areas of 2048 x 2048 blocks around 0/0, +-3000 and +-6000): 5 give a Garrison (two woodland mansions, two desert pyramids, one pillager outpost), each confirmed by generating that chunk after the lookup and reading its structure starts; the lookups loaded no chunk; the slowest took 39 ms.

### Pack edition, real client, final code (`checks-pack.txt`, `audio-pack.txt`): 45 pass, 0 fail

* The same walkthrough and the same results in the pack edition, with another board (three Patrols and a Warlord, closest 51.3 degrees apart): the scan sounds at the table, Accept and Abandon, the beacon card with the mission line (`pack-hud_status_card.jpg`), the map with both on one sheet (scale 3, `pack-mission_map.jpg`), a Warlord out of 6 big gates (`pack-warband_gates.jpg`), 14 in all, its captain with 152 health; the cannon card scrolls; the Starshell gives Haste II only; a raid reinforcement 45 blocks out came out of a gate with Speed II (`pack-raid_gate_spawn.jpg`); `/arsenal test-gate` (`pack-test_gate.jpg`); the same 5 of 10 Garrison areas, each confirmed, no chunk loaded, the slowest 36 ms.

### Earlier runs of this round

`checks-standalone-first-run.txt` (31/31 on the code before the gate and Garrison fixes) and `checks-pack-first-run.txt` (32 pass, 2 fail: the Garrison lookups). Between them and the final runs, runs of 44/44 in each edition (the gate and Garrison fixes), one run of 44 of 45 that found the map leaving the base off the sheet, and a run with the two editions side by side in which one lookup took 62 ms (the cost limits below). These logs are not kept; the findings are listed below.

### Regression: the 0.0.14 stuck-raider GameTests (`stuck-raider-regression.txt`): 11 pass, 0 fail

0.0.20 changed `RaiderGates` (spawn gates) and `RaidSpawns` (the clock for gate spawns), which the stuck-raider rescue also uses, so `tools/qa/QaStuck.java.txt` was run again (pack edition): all eleven pass, from the sealed cell and the lava probe to nine raiders sharing one gate and the boss that is never withdrawn.

### What the final runs played (`audio-standalone.txt`, `audio-pack.txt`)

* Every sound the client logged is in the recording at the same second: the table's refuse and accept, the scan sweep when SCAN MAP was pressed and the done pings 12.8 s later, the contact alert when a warband came out, and the mission-cleared placeholder at the last kill.
* **The incoming sound is back and is heard looking away:** it starts 5.20 s (standalone) and 5.18 s (pack) before the Starshell bursts (the flight is 5.15 s; earlier runs 5.12 and 5.16 s). The player stood 17 blocks from the cannon looking away from the flare. No shell or bomb whistle anywhere.

### Found by the real-client runs and fixed in this round

* **Every offer on a board had the same class** (four Patrols, or four Warlords): the per-offer `Random` was seeded with close numbers. Fixed with `TacticalRules.mix` (SplitMix64); unit test.
* **The spawn gates could not be seen** (`checks-standalone-first-run.txt` passed, but its warband screenshot showed only a sliver of red by a pillager's feet): the gate stood exactly where its enemy appeared, so the enemy hid most of the 1 x 2 sprite, and it was gone after 3 s. A spawn gate now stands 1.2 blocks behind the enemy (seen from the base, or from the nearest player for a warband), is drawn 1.6 times bigger (a synced flag on the gate entity), and stays 5 s after the last enemy. `standalone-test_gate.jpg`, `standalone-raid_gate_spawn.jpg` and `standalone-warband_gates.jpg` show the result.
* **Garrison lookups found no structure anywhere** (`checks-pack-first-run.txt`: 0 of 10 areas): for a chunk never generated, vanilla's structure check answers `CHUNK_LOAD_NEEDED` when its simulation of the generator says the structure starts there; only `START_PRESENT` (a chunk already saved) was accepted. With both accepted, 5 of the same 10 areas give a Garrison, and generating each of those chunks afterwards shows the structure really starts there (two woodland mansions, two desert pyramids, one pillager outpost); the lookups loaded no chunk and the slowest took 29 ms.
* **The mission map could leave the base off the sheet** (found by the map check added to the harness: a map at scale 4 with the beacon outside it): vanilla maps snap to a fixed grid (2048 blocks at scale 4), and the test beacon stands 344 blocks from a grid line, so a red X 420 blocks east fell in the next cell at every scale. The map is now centred halfway between the beacon and the objective (vanilla's own map data, loaded with that centre) at the smallest scale that holds both 16 blocks from the edge; a unit test checks 20,000 random beacon and objective pairs up to 1024 blocks apart.
* **A Garrison lookup's cost depends on the server's load:** the same lookup took 22 ms in one run and 62 ms in another while two clients shared the machine's four cores (10 to 30 ms a structure check), and a board filling at Mk III could run up to three lookups in one tick. A lookup now makes at most 4 checks (every structure found in these runs was found within 2), and a board fill makes at most one lookup per server tick: a second Garrison rolled in the same tick waits for the next pass a second later (its odds are unchanged).
* **QA harness only:** the status-card shot was taken 5 ticks after the teleport back to the beacon, before the once-a-second state sync, so the card looked missing (the card was right); the map shot was taken before the hand came up; the scan sounds were never heard because the QA started the scan with the table closed (they play only at the table, by design). All three steps now wait or press the real button.

## Not tested

* **Listening by a person:** the five table sounds (`tools/audio/tactical.py`, made for this round), the contact alert in a fight, the incoming sound in a real barrage.
* **A whole real hunt on foot:** the QA teleports the player to the objective and kills the warband with commands; walking 500 to 1000 blocks to a red X, fighting a warband with guns, the Return Flare home after it.
* **Garrison warbands at a real structure in play** (lookups were checked against generated chunks; no warband was fought at one), and how it feels next to a mansion's own vindicators.
* A dedicated server and two players (warband growth per extra player, shared mission, a player logging in mid-mission: unit-tested only).
* Raids on real terrain with gate spawns: the QA takes the ground away to force them; whether 10 s without natural ground is the right wait, and how Speed II for 10 s feels over a 70-block walk.
* The Command Table and Satellite Beacon models were looked at in screenshots only (`standalone-table_and_satellite.jpg`); upgrading the satellite to Mk II and III through the table with real items (the QA sets the Mk on the server).
* Other GUI scales and window sizes for the table screen and the cannon card (only 1280 x 720 at the QA's GUI scale).
* The mission-cleared jingle is a placeholder (the Victory Fanfare) until the owner's song comes.

