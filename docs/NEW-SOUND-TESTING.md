# The owner's music and radio chatter (0.0.19): testing

## How it was run

* **Unit tests** (`:arsenal-beacon:test`), JDK 17.
* **Real client** (Forge 47.4.20 development runtime, `xvfb`, 1280 x 720), driven by the `r9` scenario of `tools/qa/QaWorld.java.txt` (`echo all > /tmp/claude-0/qa-r9.flag`, then `tools/qa/setup-qa.sh <dir> <pack|standalone> QaWorld` and `runClient`; parts: `music chatter whistle`). The scenario sets raid states on the integrated server (a hard raid of eight waves, a special raid of four, then a real victory in the standalone edition) and reads the song the client plays; it drops real flares (fire, supply, return, one under a thick roof) and a real Explosion Barrage and Bunker Buster. A listener on the client's sound manager logs every sound of the mod the client really starts (`QA_SOUND`: tick, event, pitch), so the checks see which song, which radio line and which whistle actually played.
* **Sound.** The client ran with OpenAL Soft's `wave` backend, so the game's real mixed output was written to a WAV file. `tools/audio/which_song.py` names the song in every 2-second window; `tools/audio/find_sfx.py` finds when each effect starts (and at which pitch). **Nobody listened to it.**

## Results

### Unit tests: 173 pass, 0 fail

* `MusicPlayerTest`: eight ordinary songs for the longest raid (8 waves); for 800 seeds, normal and special raids, hard or not, of 3 to 8 waves: no song twice in a raid, the same raid and wave always give the same song, any ordinary song can open a raid, a special raid opens with Anomaly Protocol, a hard raid's last wave plays Boss Battle; a raid longer than the songs never repeats the song just played; the three 0.0.16 themes are gone; the fanfare is the owner's (10 s) and fits `RaidMusic.VICTORY_TICKS`. Every song still has its `sounds.json` entry, its name and the length the player shows.
* New `RadioAndWhistleTest`: the six radio lines are shipped (mono, 3 to 5 s, with a subtitle) and the siren is gone; the line picker never repeats a line, a fire flare can get all six, a supply flare only 1 and 4, a return flare none; the shell and bomb whistles are back (mono, 1.55 s), the incoming sound is gone, and for every pitch a round can get, the whistle ends as its blast is heard.
* `HandTunedRoundTest` (0.0.18) no longer expects the incoming sound; the flight stays 103 ticks.

### Standalone edition, real client (`docs/validation/new-sound/checks-standalone.txt`): 34 pass, 0 fail

* Music: a hard raid of eight waves played eight different songs, the planned one each wave, Boss Battle on the last; the client started each of them and none of the removed themes. A special raid of four waves opened with Anomaly Protocol and played three different ordinary songs. The real victory played the owner's fanfare and held the game's own music (`standalone-music-wave2.jpg`: the player under the beacon card reads "Assault Loop 0:04").
* Radio: six fire flares got one line each (3, 1, 2, 6, 3, 1), never the same twice in a row, at normal pitch, each landing with the game's firework pop, never the siren; three supply flares got 4, 1, 4; the return flare and the flare refused under a 3-block roof (handed back) got none.
* Whistle: six shell whistles (pitch 0.92 to 1.08) for the six shells of a barrage, no incoming sound; each whistle's end was within 80 ms of its shell's blast as the client heard it (-26, -25, +63, -10, -1, +4 ms); the Bunker Buster's bomb whistle (pitch 0.90 to 1.0) ended 21 ms after its blast began; the Bunker Buster's own blast sound played.

### What the standalone run played (`docs/validation/new-sound/audio-standalone.txt`)

* The recording names the same songs at the same moments (r = 1.00 in mid-song windows), each from its start, and the fanfare played to its end (10 s).
* The same radio lines in the same order (r = 0.85 to 0.98); no flare siren and no 0.0.18 incoming sound anywhere in the capture.
* Each shell whistle ends between 0.04 s before and 0.07 s after its blast; the bomb's ends on the blast.

### Pack edition, real client (`docs/validation/new-sound/checks-pack.txt`, `audio-pack.txt`): 32 pass, 0 fail

* The same parts without the victory (the development runtime has no Create, which the pack's completion rewards need). Another order of songs (another beacon position), again eight different songs with Boss Battle last, and Anomaly Protocol first in the special raid; fire lines 5, 2, 4, 2, 6, 5, supply lines 1, 4, 1; whistle end minus blast -0.02 to +0.05 s in the recording, the bomb's 0.00 s.

### Found by the first standalone run and fixed (`docs/validation/new-sound/checks-standalone-first-run.txt`: 33 pass, 2 fail)

* The whistles were timed to end on the client's landing tick and the recording showed them ending 0.03 to 0.10 s (0.07 s on average) **before** the blast: the blast reaches the client about 1.4 ticks after the client's copy of the round lands (they travel by different paths). The whistle now starts 1.4 ticks later (`OrdnanceClient.BLAST_HEARD_AFTER_LANDING`), and it plays to its end instead of being cut at the landing. The two failing checks measured this with the client's game clock (which the server corrects); they now compare each whistle's end with the blast the client hears.

## Not tested

* **Listening by a person:** whether the new songs sit well together and next to the effects, how the radio lines sound over a raid and the fanfare, whether 1.55 s of whistle is enough after 3.6 s of silence in the fall.
* A whole real raid (the wave changes were set on the server, not fought), a real hard raid's boss, a raid of the pack edition to its victory.
* A dedicated server and two players (each hears the same song from the shared seed: arithmetic only; the radio line follows only the thrower).
* The radio when the thrower is dead or in another dimension (it then plays at the flare: code-read only).
* Song titles: the owner's file names are shown; three MP3 tags name the songs differently (see `docs/HANDOFF.md`).
