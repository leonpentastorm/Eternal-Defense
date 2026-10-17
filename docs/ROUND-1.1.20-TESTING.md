# 1.1.20: what was tested

The owner's list after 1.1.19, all on `feature/power-defense`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-17). Evidence: `docs/validation/release-1.1.20/`.

## What was asked, and how it was read

* **"Increase all turret sound by 20%, change shotgun sound to this one (M1014-3.wav), for the shotgun turret increase the sound by 25% instead of 20%."** Read as louder than 1.1.19 by those amounts, keeping what the owner asked for before: the machine gun stays "30 percent quieter" and is then 20 percent louder (0.7 x 1.2 = 0.84 of the clip, -1.5 dB); the flamethrower's start, loop and end are 1.2 (+1.6 dB); the new shotgun clip keeps the 30 percent cut and the room reverb asked for the old one, then 25 percent louder (0.7 x 1.25 = 0.875, -1.2 dB). Minecraft caps a sound's loudness at volume 1 (a higher volume in the code only makes it carry farther), so the change is in the OGG files (`tools/audio/import_sfx.py`). The flame start reached -0.1 dBFS after the gain: a look-ahead limiter holds its few peaks at -1 dBFS (at most 0.9 dB off a few peaks). The two vanilla turret sounds (the loading clank and the empty click) are 20 percent louder in the code (0.96 and 0.72).
* **"Power pole area is hard to see in daylight, use semi translucent walls on the edges."** Every edge of a pole's reach is now a see-through wall from its lowest layer (4 down) to its highest (6 up), strongest at the floor (alpha 96 of 255) and fading to a third at the top and bottom; blue powered, orange down, white for a pole in hand. The walls are drawn from both sides, 0.01 inside the edge (no flicker against block faces), and write no depth, so nothing drawn after them is hidden. The 1.1.19 outline stays on top.
* **"Increase the cost of everything in the defense shop by 4x."** All 17 Defense Depot prices x4 (`Economy`): turrets 96 / 192 / 384, flame 128 / 256 / 512; 4 ammo 32 / 32 / 40; 8 Sandbags, 4 Barricades, 8 Barbed Wire, 8 Electric Wire 16 each; Shock Box 64. The Workshop Fabrication cost of the depot itself (and of poles and dynamos) is not "in the shop" and is unchanged. The Field Guide quotes the new prices.
* **"The sound reduce when player go far from the turret is a bit too extreme, let player knows if a turret nearby is shooting."** Minecraft's default: a sound from the server is sent only to players within 16 blocks, and it fades to nothing over those 16 blocks (a Mark III machine gun shoots 26 blocks out). The five turret sounds now carry 48 blocks with an even fade: two thirds as loud at 16 blocks, half at 24, a third at 32. Up close nothing is louder. The flame sounds were played at volume 1.5 (24 blocks); they are at 1 now, with the same 48 blocks, which also lets the roar's 3-tick fade be heard (at 1.5 its first steps were capped).

## Unit tests

`:eternal-defense:test` **542 pass** (537 before; new `Release1120Test`, 5 tests; `Release1119Test` reads a sounds.json entry that is now an object). The displays and the Gun Guide are unchanged (their tests: see Jars).

* `everyDepotPriceIsFourTimesTheOldOne`: each of the 17 products is four times its 1.1.19 price.
* `theGuideQuotesEveryDepotPrice`: sandbags, barricades, barbed and electric wire are quoted with their prices; no old price is left (turrets, ammo and the Shock Box were already checked by `Release1118Test`).
* `turretSoundsAreTwentyPercentLouderAndTheShotgunTwentyFive`: the gains in the import table come to 0.84, 1.2 and 0.875 of the clips; the old shotgun clip is gone; the reverb stays; the loading and empty sounds at 0.96 and 0.72.
* `turretsAreHeardFromFurtherAway`: all five turret sounds fade over 48 blocks in sounds.json, equal to `DefenseSounds.TURRET_RANGE`; their events are fixed-range at that distance; the guns play at volume 1 and the flame at no more than 1.
* `reachWallsSitJustInsideEveryEdge`: for every edge of the reach, the side the wall is moved to is inside the reach and the other side is not; the walls are see-through, both sides, without depth writes.

## Sound files (decoded, before and after)

| Sound | 1.1.19 | 1.1.20 |
| --- | --- | --- |
| turret_lmg | peak -4.5 dBFS, RMS -27.4 | peak -3.0, RMS -25.8 (+1.65 dB) |
| flame_start | peak -1.7, RMS -14.9 | peak -1.0, RMS -13.3 (+1.62) |
| flame_loop | peak -4.4, RMS -16.8 | peak -3.1, RMS -15.2 (+1.59) |
| flame_end | peak -6.2, RMS -18.3 | peak -4.6, RMS -16.7 (+1.60) |
| turret_shotgun | M1014-4Suppressor, 0.95 s, peak -4.3, RMS -22.1 | M1014-3, 1.15 s with the reverb tail, peak -2.3, RMS -17.5 |

+1.58 dB is 1.2 times; the OGG encoding adds a few hundredths. The shotgun is a different clip, so its numbers are not a ratio.

## Real client

`tools/qa/QaRelease1120.java.txt` ran with `QA_CREATE=1` (Create 6.0.8) in a new flat world at 1280 x 720, at noon (day time 6000), with the game's audio written to a WAV file by OpenAL Soft's `wave` backend (no sound card; nobody listened).

### Run history

* Run 1: 10 of 11. The pole-in-hand preview check stood 5 blocks from the ground spot and the crosshair hit the block next to it (a harness position; the preview itself showed). Moved to 3 blocks as in 1.1.19.
* Run 2: 11 of 11. The night picture was taken 5 ticks after the time change, before it reached the client (still day): the step now waits 2 s.
* Run 3: 11 of 12. A new step looked at the preview spot from 16 blocks away: beyond a player's reach there is no target, so no preview (correct). The step was dropped.
* After run 3 the owner asked for turret sounds to carry further; the change went in and the harness moved the player 32 blocks from the guns during the sound part.
* Run 4 (the final code): **12 of 12**. Pictures in `docs/validation/release-1.1.20/` are from this run.

### The checks (run 4, `checks-release-1.1.20.txt`)

* A pole on a turning Shaft Dynamo is live; a pole on bare ground is down.
* The walls' render type: translucent, no culling, colour written and depth not.
* With defense gear in hand both reaches are drawn: blue and orange walls at noon (`r1120_reach_day`, `r1120_reach_day_above`; the same views with empty hands `..._none`: about 18 and 21 percent of the picture above the hotbar changes when the walls are on). From inside the reach (`r1120_reach_inside`) and at midnight (`r1120_reach_night`).
* A pole in hand previews its reach on the ground it looks at (`r1120_reach_preview`, white walls).
* A pole pinned with a sneak right-click keeps its walls with empty hands (`r1120_reach_pinned`).
* A Light Gun Turret, a Twin Auto-Shotgun Turret (Mark III) and a Flame Turret fire at tough husks; the player stands 32 and 33 blocks from the guns: the client receives `turret.mg_shot` and `turret.shotgun_shot` (in 1.1.19 the server sent them only within 16 blocks), and the flame's start and loop.
* Every Defense Depot price is four times the old one; the depot screen shows them (`r1120_depot_screen`); 96 coins buy a Mark I turret and leave none; 31 coins are not enough for 4 machine gun cans (32) and the screen says so (`r1120_depot_short`); 32 coins buy them.

### The recordings (`tools/audio/find_sfx.py`)

* Runs 1 and 2 (player 6 to 8 blocks from the guns, 1.1.19's reach): the new shotgun clip matches the recording at r = 0.46 to 0.81 every half second while the shotgun fires; the 1.1.19 clip only at 0.32 to 0.41, the level any noise reaches. So the client plays M1014-3.
* Run 4 (32 to 33 blocks away, 48-block reach): the shotgun matches at r = 0.44 to 0.89, the machine gun at 0.41 to 0.49 and the flame loop at 0.45 while they fire (50.6 to 53.9 s); the recording's level in that window is -22.8 dBFS RMS, against -17.6 dBFS in run 2 at 6 to 8 blocks: about 5 dB quieter at four to five times the distance, as the even 48-block fade predicts (a third at 32 blocks against a bit over half at 7 of 16). At 32 blocks 1.1.19 played nothing.

## Jars

Built with `build releaseJars`: `eternal-defense-1.1.20.jar`, `eternal-defense-1.1.20-novoice.jar`, `eternal-defense-gun-guide-1.1.20.jar`, `eternal-defense-displays-1.1.20.jar`. Test zips: `dist/Eternal-Defense-1.1.20.zip` and `-novoice.zip` with `.sha512`.

## Network

Protocol stays **43**: no packet changed, no new item, block or menu. 1.1.19 and 1.1.20 still should not be mixed: the prices are the server's, but the sound reach needs both sides (the server sends a shot 48 blocks; a 1.1.19 client fades it out over 16).

## Not tested

* How the sounds sound to a person: nobody listened. The levels are measured in the files and found in the recordings.
* Many turrets firing at once at 48 blocks (how busy it gets), and the reach walls with many poles at once.
* The walls with shader packs (Oculus), and under water or in caves.
* A raid with the new prices; a server and a second player.
