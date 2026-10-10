# Audio sources (Eternal Defense)

The sounds the beacon mod ships under `assets/arsenal_beacon/sounds/` come from here. The owner's raid songs are converted by
`import_songs.py`; every other sound (the three 0.0.16 raid themes, the victory fanfare, every effect) is synthesised from scratch by
the other scripts (no samples, no third-party recordings).

* `synth.py`: the engine (oscillators, envelopes, filters, the instruments, a stereo mixer with reverb that can fold its tail onto the start for seamless loops, mastering and OGG export through `ffmpeg`).
* `compose.py`: the raid themes and the victory fanfare. `python3 compose.py <out_dir>` writes `raid_normal.ogg` (Hold the Line), `raid_boss.ogg` (Iron Tyrant), `raid_special.ogg` (Strange Signals) and `victory.ogg` (Laurels).
* `import_songs.py`: `python3 import_songs.py <folder with Boss/, Normal/, Special raid/> <out_dir>` converts the owner's MP3s (silence trimmed, -16 LUFS two-pass linear normalisation, cover art and tags dropped, Vorbis q4 48 kHz). Its table maps file names to resource names and pools; keep it in step with `RaidPlaylist.SONGS`.
* `import_sfx.py`: `python3 import_sfx.py <folder with the SFX-* files> <out_dir>` converts the owner's hand-tuned effects (0.0.18): the cannon (turning loop, lock, shot), the incoming round, the Bunker Buster, the cluster bomblets, the shockwave and the attack alarm. Positional sounds are made mono; the lock and incoming trims are what the cannon and flight timings are built on (see the script); the cluster bomblet is lowered 6 dB in the file (a volume above 1 in the code only adds range).
* `find_sfx.py`: `python3 find_sfx.py <capture.wav> name=file.ogg[@pitch,...] ... [--threshold] [--head] [--skip] [--from] [--to]` finds when each sound effect plays in a recording of the game's audio output (OpenAL Soft's `wave` backend), by normalised cross-correlation at every pitch the code can give it. Used for `docs/HAND-TUNED-ROUND-TESTING.md`. Needs numpy and ffmpeg.
* `sfx.py`: the 0.0.16 effects. Only five are still shipped (flare_signal, cryo_burst, napalm_whoosh, gravity_hum, starshell_pop); the others were replaced by the owner's sounds or the game's own explosion sound in 0.0.18. `python3 sfx.py <out_dir>` still writes all 17.

Needs Python 3 with `numpy` and `scipy`, and `ffmpeg` with `libvorbis`. Copy the results into `custom-mods/arsenal-beacon/src/main/resources/assets/arsenal_beacon/sounds/music/` and `sounds/sfx/`; the events are declared in `sounds.json` and registered in `ArsenalSounds`.
