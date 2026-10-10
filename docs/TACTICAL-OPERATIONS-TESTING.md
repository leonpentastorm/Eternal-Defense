# Tactical Operations and the owner's raid list (0.0.20): testing

## How it was run

* **Unit tests** (`:arsenal-beacon:test`), JDK 17.
* **Real client** (Forge 47.4.20 development runtime, `xvfb`, 1280 x 720), driven by `tools/qa/QaHunt.java.txt` (`tools/qa/setup-qa.sh <dir> <pack|standalone> QaHunt`, then `runClient`). It makes a fresh **normal** world (seed 20201010, not a flat world, so the satellite scan and the Garrison lookups meet real terrain and real structure placement), runs the opt-in hunt GameTests (`HuntGameTests`) on its integrated server, then walks through 0.0.20 like a player: places the Command Table and the Satellite Beacon, fills a Mk III board, scans, opens the table, accepts and abandons, reads the HUD, holds the map, runs a Warlord to its end, opens the cannon menu, calls a Starshell, starts a raid with no natural ground left, and runs `/arsenal test-gate`. A listener on the client's sound manager logs every sound of the mod the client really starts (`QA_SOUND`), and the chat it shows (`QA_CHAT`).
* **Sound.** The client ran with OpenAL Soft's `wave` backend, so the game's real mixed output was written to a WAV file; `tools/audio/find_sfx.py` finds when each effect starts in it. **Nobody listened to it.**

Results: the runs of this round are being written up (this file is completed in the next commit).
