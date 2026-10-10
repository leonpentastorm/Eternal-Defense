# Raid music player (0.0.17): testing

Also covers the real-client checks of the Beautify Path (0.0.16), which were not run in that round.

## How it was run

* Real client (Forge 47.4.20 development runtime, `xvfb`, 1280 x 720), driven by the `r7` scenario of `tools/qa/QaWorld.java.txt` (`echo all > /tmp/claude-0/qa-r7.flag`, then `tools/qa/setup-qa.sh <dir> <pack|standalone> QaWorld` and `runClient`; parts: `music settings menu pictures loop fire`). The scenario sets raid states on the integrated server (kind, hard or not, wave) and reads what the client decides (`RaidMusic.nowPlaying()`), takes screenshots, clicks the Settings switches and the cannon menu like a player, and fires every new fire support at zombies.
* **Sound.** The machine has no sound card. The client was started with OpenAL Soft's `wave` backend (`ALSOFT_CONF` pointing at a config with `drivers = wave`), so the game's real audio output was written to a WAV file. That file was then cut into 2-second windows and each window cross-correlated against every music file (8 kHz mono): a score near 1.00 means that file, at that position, is what the game was playing. Nobody listened to it.

## Results

### Unit tests

158 pass, 0 fail (`:arsenal-beacon:test`). New `MusicPlayerTest`: the pool of every raid moment (ordinary, special, boss wave of a hard raid, none outside a raid or with stale state), that no wave repeats the song of the wave before (600 seeds x 12 waves), that the same raid and wave always give the same song, that any song can open a raid, the clock format, that both switches default to on, and for every song: a `sounds.json` entry that streams, a name in the lang file, and an OGG whose length (read from the file's last Ogg page) matches the length the player shows, within 1 second.

### Standalone edition, real client (`docs/validation/music-player/checks-standalone.txt`): 46 pass, 2 fail

* Music: a hard raid's wave 1 plays an ordinary song, the one the seed predicts; wave 2 changes to another ordinary song; the boss wave plays a boss song; far from the beacon (150 blocks) the status card hides and the song and the player stay; the player can be hidden while the song plays; a special raid plays a special song; the game's music manager is held while a song plays.
* Settings: the tab has both switches; Raid music off stops the song and is saved; on again brings the wave's song back; Music player off and on are saved.
* Cannon menu (0.0.16): it opens; Area of effect is with the three-level upgrades; a maxed upgrade is inactive but hovered and shows its tooltip with "Fully upgraded"; the list scrolls to its end with the Starshell in view and the rows above hidden; choosing a type is stored.
* Fire supports (0.0.16), each thrown at six zombies at night: Explosion Barrage 6 shots, falling shells seen on the client (181 samples of `Ordnance` entities), all zombies dead; Cluster Strike 5 shots, 1 zombie left; Cryo Shell 4 shots, a zombie frozen and slowed with Slowness V; Napalm Carpet 4 shots, zombies burned and all died; Gravity Well 3 shots, the zombies' mean distance to the flare went from 2.4 to 0.3 blocks; Shockwave 4 shots, from 2.4 to 10.8 blocks; Starshell 1 shot, the star burned, zombies glowed, the player got Night Vision, no zombie died.
* **Fail 1 and 2 (harness, not the mod):** the victory step waited for the `restore` phase, which lasts one tick when nothing needs repairing, so it timed out and checked 80 seconds later, after the fanfare and the quiet. The server did announce the victory at 05:32:20, and the audio capture shows the fanfare playing right then (below). The step now waits for the end of the `raid` phase.
* The Bunker Buster picture was refused by the game itself ("Don't throw bunker buster in your own bunker"): the harness threw it inside the zone. The picture part now throws far from the zone.

### What the game actually played (`docs/validation/music-player/audio-capture-standalone.txt`)

* 34 to 40 s: Phase One Assault (wave 1), score 1.00, its position advancing 2 seconds per 2-second window (real time, from 0.9 s).
* 42 to 46 s: Hold the Line (wave 2), from its start; 48 to 62 s: Iron Tyrant (the boss wave), still playing after the player walked 150 blocks away and hid the player strip; 64 to 68 s: Anomaly Protocol (the special raid).
* 72 to 80 s: the victory fanfare, score 0.97 to 0.99, right when the server announced VICTORY; then silence (the game's music held).
* 164 to 174 s: the raid song again; Raid music off then on at 168 s starts it over (position 1.6 s), and it fades out when the raid ends.
* No music during the cannon and fire tests (no raid). Loudness of the songs in the capture: about -15 to -22 dB RMS, blasts -12 to -14 dB.

### Pack edition, real client (`docs/validation/music-player/checks-pack.txt`): 24 pass, 0 fail

* The same music, Settings and cannon-menu checks as above (another world, so another seed: Line Holder, then Barren Gap, then Boss Battle), with the card and player widened to the song's name (`music-player-muted.png` shows "Anomaly Protocol" in full beside "muted").
* Loop: a special raid held on a wave that plays the 64-second Strange Signals for 75 seconds: the client still plays it, 11 seconds into its second time through.
* The victory is not part of the pack run (the development runtime has no Create, which the pack's completion cache needs; a first pack run crashed on exactly that, `Missing guaranteed reward create:andesite_alloy`, in `RaidRewards`, before any music code).

### The loop seam in the recorded audio (`docs/validation/music-player/audio-loop-pack.txt`)

* At 178 s the song is 60.5 s in, at 182 s it is 1.0 s in: it started over at 181.0 s, the moment the 63.5-second file ended. The quietest 50 ms around the seam is -29 dB against a median of -22 dB: no silent gap.

### Falling rounds (`docs/validation/music-player/pictures-run.txt`, `shell-falling.png`, `bunker-bomb-falling.png`, `bunker-bomb-falling-lower.png`)

* An Explosion Barrage shell and the Bunker Buster bomb photographed in their fall from beside their path: the 3D shell with its smoke trail, and the banded bomb with smoke and sparks above it, the red box below. (Thrown 40 blocks from the beacon: closer, the game refused the Bunker Buster, as it should.)

### Raider Gate GameTests (`docs/validation/music-player/gate-gametests.txt`): 11 pass, 0 fail

* Including the extended `aRaiderSealedInAStoneCell...`: a second red gate stood where the raider came out, and it was gone 2 seconds later with no exit gate left.

## Not tested

* Listening by a person: whether the songs, the fades (3 s out, 1.5 s in), the loudness next to the game's own music and the fanfare sound right. The recordings prove what played and when, not how it sounds.
* The victory step with its fixed wait (the fanfare itself is proven by the standalone recording).
* The exit gate and the blasts were not photographed; the screen shake, the whistles and the other new sound effects were not checked in the recordings.
* A dedicated server and two players (every player should hear the same song: the seed is shared; only the arithmetic is tested).
* GUI scales other than the automatic one at 1280 x 720; very long song names on narrow screens (the card widens up to a third of the screen, then the name is cut with an ellipsis).
* The pack edition's victory in the development runtime (see above).
