# Audio sources (Eternal Defense)

The sounds the beacon mod ships under `assets/arsenal_beacon/sounds/` come from here. Since 0.0.19 all the music (the raid songs and the
victory fanfare) is the owner's, converted by `import_songs.py`; the owner's effects and radio lines are converted by `import_sfx.py` and
`import_chatter.py`. The rest (the shell and bomb whistles, napalm, frost, the gravity hum, the starshell pop) is synthesised from scratch
by `sfx.py` (no samples, no third-party recordings).

* `synth.py`: the engine (oscillators, envelopes, filters, the instruments, a stereo mixer with reverb that can fold its tail onto the start for seamless loops, mastering and OGG export through `ffmpeg`).
* `compose.py`: the raid themes and the victory fanfare of 0.0.16 (Hold the Line, Iron Tyrant, Strange Signals, Laurels). **Not shipped since 0.0.19** (the owner wants only their own music); kept for the record.
* `import_songs.py`: `python3 import_songs.py <the owner's upload folder> <out_dir>` converts the owner's MP3s found in that folder (0.0.17: `Boss/`, `Normal/`, `Special raid/`; 0.0.19: `New songs/`, four songs and the Victory Fanfare) (silence trimmed, -16 LUFS two-pass linear normalisation, cover art and tags dropped, Vorbis q4 48 kHz). Its table maps file names to resource names and pools; keep it in step with `RaidPlaylist.SONGS`.
* `import_sfx.py`: `python3 import_sfx.py <folder with the SFX-* files> <out_dir>` converts the owner's hand-tuned effects (0.0.18): the cannon (turning loop, lock, shot), the Bunker Buster, the cluster bomblets, the shockwave and the attack alarm (the incoming round of 0.0.18 was dropped in 0.0.19). Positional sounds are made mono; the lock trim is what the cannon's timing is built on (see the script); the cluster bomblet is lowered 6 dB in the file (a volume above 1 in the code only adds range).
* `import_chatter.py`: `python3 import_chatter.py <folder with "Fire support chatter (1..6).mp3"> <out_dir>` converts the owner's six fire support radio lines (0.0.19) to `chatter_1.ogg` ... `chatter_6.ogg`: mono, about -18 LUFS with one linear gain each (peak at most -1 dBFS), nothing trimmed.
* `find_sfx.py`: `python3 find_sfx.py <capture.wav> name=file.ogg[@pitch,...] ... [--threshold] [--head] [--skip] [--from] [--to]` finds when each sound effect plays in a recording of the game's audio output (OpenAL Soft's `wave` backend), by normalised cross-correlation at every pitch the code can give it. Used for `docs/HAND-TUNED-ROUND-TESTING.md`. Needs numpy and ffmpeg.
* `sfx.py`: the 0.0.16 effects. Six are shipped: shell_whistle and bomb_whistle (back in 0.0.19), cryo_burst, napalm_whoosh, gravity_hum, starshell_pop. The others were replaced by the owner's sounds or the game's own explosion sound in 0.0.18, and flare_signal (the flare siren) by the owner's radio lines in 0.0.19. `python3 sfx.py <out_dir>` still writes all 17.

Needs Python 3 with `numpy` and `scipy`, and `ffmpeg` with `libvorbis`. Copy the results into `custom-mods/arsenal-beacon/src/main/resources/assets/arsenal_beacon/sounds/music/` and `sounds/sfx/`; the events are declared in `sounds.json` and registered in `ArsenalSounds`.
