# Audio sources (Eternal Defense)

The sounds the beacon mod ships under `assets/arsenal_beacon/sounds/` come from here. The owner's raid songs are converted by
`import_songs.py`; every other sound (the three 0.0.16 raid themes, the victory fanfare, every effect) is synthesised from scratch by
the other scripts (no samples, no third-party recordings).

* `synth.py`: the engine (oscillators, envelopes, filters, the instruments, a stereo mixer with reverb that can fold its tail onto the start for seamless loops, mastering and OGG export through `ffmpeg`).
* `compose.py`: the raid themes and the victory fanfare. `python3 compose.py <out_dir>` writes `raid_normal.ogg` (Hold the Line), `raid_boss.ogg` (Iron Tyrant), `raid_special.ogg` (Strange Signals) and `victory.ogg` (Laurels).
* `import_songs.py`: `python3 import_songs.py <folder with Boss/, Normal/, Special raid/> <out_dir>` converts the owner's MP3s (silence trimmed, -16 LUFS two-pass linear normalisation, cover art and tags dropped, Vorbis q4 48 kHz). Its table maps file names to resource names and pools; keep it in step with `RaidPlaylist.SONGS`.
* `sfx.py`: the cannon, flare, ordnance and fire support effects. `python3 sfx.py <out_dir>` writes the 17 files of `sounds/sfx/`.

Needs Python 3 with `numpy` and `scipy`, and `ffmpeg` with `libvorbis`. Copy the results into `custom-mods/arsenal-beacon/src/main/resources/assets/arsenal_beacon/sounds/music/` and `sounds/sfx/`; the events are declared in `sounds.json` and registered in `ArsenalSounds`.
