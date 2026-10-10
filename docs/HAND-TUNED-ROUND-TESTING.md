# Hand-tuned sounds and fire support rework (0.0.18): testing

## How it was run

* **Unit tests** (`:arsenal-beacon:test`), JDK 17.
* **Real client** (Forge 47.4.20 development runtime, `xvfb`, 1280 x 720), driven by the `r8` scenario of `tools/qa/QaWorld.java.txt` (`echo all > /tmp/claude-0/qa-r8.flag`, then `tools/qa/setup-qa.sh <dir> <pack|standalone> QaWorld` and `runClient`; parts: `cannon cover beacon arrow cluster napalm cryo gravity shockwave starshell bunker alarm`). The scenario places a beacon (Mk-4), a Support Platform and a cannon, throws real flares with the player's hand, reads the server's state tick by tick (cannon lock and shot, blocks placed, mob health, effects, positions), reads what the client shows (the vortex sprite, tipped arrows, the screen shake, the alarm), and takes pictures. Fire supports are thrown at night at six zombies.
* **Sound.** The machine has no sound card. The client ran with OpenAL Soft's `wave` backend (`ALSOFT_CONF` pointing at a config with `drivers = wave`), so the game's real mixed output was written to a WAV file. `tools/audio/find_sfx.py` then finds every sound effect in it: each file, at every pitch the code can give it, is slid along the recording and the normalised cross-correlation is computed (1.00 = exactly that sound at that moment). That gives the moment each sound started, to a hundredth of a second. **Nobody listened to it.**

## Results

### Unit tests: 169 pass, 0 fail

New `HandTunedRoundTest`: every new sound has its `sounds.json` entry, its file and a subtitle, is mono when it plays in the world (the alarm is stereo), and the replaced 0.0.16 events are gone; the cannon's arming time is the length of the lock sound (18 ticks for 0.870 s) and the flight is the incoming sound's impact (103 ticks for 5.15 s); the incoming sound is played at pitch 1 (added after the real-client run, see below); the alarm rule (when the warning appears, again after 8 s, never without the warning); the turret's turn speeds (1.5 times as slow, the top level equal to the old base); the cover rule (3 solid blocks); the beacon prices (Defense in plating, Vertical in 2/4/8/16/32 of each part, standalone in Ardent Energy); the lingering types are one round and Volley lengthens Napalm, Cryo and Gravity but not Cluster; the Cluster's 12 s of pulses add up to 80 percent of a full barrage; the area sizes (Napalm and Cryo 1.2 times, Gravity twice, Shockwave the level-1 beacon zone); the Shockwave carries a mob from any point of the area past its edge; the arrow potions harm their target (undead get healing). Updated: `SupportTest`, `RulesTest`, `BeautifyPathTest`, `LangKeysTest`.

### Pack edition, real client (`docs/validation/hand-tuned-round/checks-pack.txt`): 46 pass, 0 fail

* Cannon: the turning sound started while the turret turned; the gun fired 17 ticks after the lock (lock tick 159, shot tick 176; the lock sound is 18 ticks long and the harness samples once per client tick, so +-1).
* Cover: an open well 5 blocks deep is not underground (the flare stays and the cannon fires on it); a 1-block roof is not; a 3-block roof is, and the flare is handed back.
* Beacon: the Vertical zone took 2 of each of the four parts; Defense took 8 reinforced plating and no coil; Reconnaissance stops at level 3, where attackers glow at once (`pack-beacon-upgrades.jpg`: the cards show the plating price, "attackers glow after 3 minutes, next 1 minute" and the four part icons of the Vertical zone).
* Arrow Cluster: 6 volleys; the arrows were tipped (coloured); zombies got Slowness or Weakness (`pack-arrows.jpg`).
* Cluster Strike: one shot; its bomblets killed all six zombies.
* Napalm Carpet: one shot; 110 fire blocks on the ground (`pack-napalm.jpg`); all gone when it ended; the zombies burned to death.
* Cryo Shell: one shot; 110 snow layers (`pack-cryo.jpg`); all gone when it ended; Slowness V; no damage (lowest health 20).
* Gravity Well: one shot; the vortex sprite was up on the client (`pack-gravity.jpg`); no damage. The pull check passed (mean distance 7.5 to 4.2 blocks) but it measured the wrong thing: the zombies were spread out when the flare landed, 6 s before the well opened, and had walked off after the player by then. The check was rewritten for the standalone run (zombies put 7 blocks out the moment the well opens).
* Shockwave: 4 shots; every zombie ended past the 8-block edge (mean 2.5 to 10.6 blocks); no damage.
* Starshell: 81 light blocks; all gone when it ended; the player got Haste; zombies glowed (`pack-starshell.jpg`: the lit ground).
* Bunker Buster: the view shook near the impact.
* Alarm: the warning picture (`pack-attack-warning.jpg`).

### What the pack run played (`docs/validation/hand-tuned-round/audio-pack.txt`)

* The turning loop started when the flare landed and stopped when the turret locked on, 6.96 s later; the lock clunk (0.870 s) started at 42.06 s and **the shot followed at 42.94 s, 0.01 s after the clunk ended**. Five more shots, 2 s apart.
* The incoming scream started with every shot (at the impact point); **each barrage shell's explosion came 5.15 to 5.20 s after its shot, where the scream hits**. The explosions are Minecraft's own (random/explode1-4, at the shell's low pitch). None of the replaced 0.0.16 sounds was heard.
* The Cluster crackle (the owner's bomblet sound at many pitches) began at the impact and lasted 12.9 s. The four Shockwave blasts and the Bunker Buster blast are the owner's sounds, each about 5.15 s after its shot.
* The alarm played every 8.0 s from 76 s to the end of the run. That was real: the beacon was being attacked through the whole fire-support test by slimes of the flat world, which the harness's own `/kill` had split into smaller ones (`pack-attack-warning.jpg` shows one beside the beacon). The harness now removes monsters without killing them.

### Fixed after reading the pack recording

* **The Bunker Buster's incoming scream played at pitch 0.92**, which stretches it: it would have hit 0.45 s after the bomb landed. It now plays at pitch 1 like a shell's (and a unit test keeps it so).
* **The Cluster crackle was nearly as loud as a barrage** (-13.5 dB RMS against -11.5 dB, for 12 s). The code's volume 2.5 did not make it quieter: Minecraft caps loudness at volume 1 and uses anything above only for range. The bomblet file is now 6 dB quieter (`tools/audio/import_sfx.py`, a gain column); the other seven files regenerate bit for bit.

### Standalone edition, real client, after those fixes (`docs/validation/hand-tuned-round/checks-standalone.txt`): 46 pass, 0 fail

* The same parts (the beacon part checks Reconnaissance only: the standalone edition pays Ardent Energy, `standalone-beacon-upgrades.jpg`). The harness now removes monsters without `/kill`, throws the fire tests 20 blocks farther from the beacon, and logs every hit the beacon registers with the mobs near it.
* Cannon: lock at tick 156, shot at 174: exactly the lock sound's 18 ticks.
* Gravity Well (new check): the six zombies, walking, were put 7 blocks from the flare the moment the well opened; the pull brought them to a mean of 0.43 blocks from the middle (0.45 after 3 s), with no damage. `standalone-gravity.jpg` is the moment they were put out, on the rim of the vortex.
* Shockwave: from 2.5 to a mean of 14.7 blocks, past the 8-block edge; no damage.
* Alarm (new checks): it never sounded before the alarm test (the probe logged no hit on the beacon until then); in a 10-second attack it sounded exactly twice, when the banner came up and 8 s later.
* Every other check as in the pack run (110 fire blocks, 110 snow layers, 81 light blocks, all removed at the end; tipped arrows; cluster kills; the screen shake).

### What the standalone run played (`docs/validation/hand-tuned-round/audio-standalone.txt`)

* The lock clunk at 43.56 s, **the shot at 44.48 s, 0.05 s after the clunk ended**; six barrage explosions (Minecraft's own), each 5.14 to 5.18 s after its shot.
* **The Bunker Buster's incoming scream now plays at pitch 1** (found with r=0.97) and the Bunker Buster blast came 5.18 s after the shot, 0.03 s after the scream's impact.
* The Cluster crackle: 42 separate bomblets found over 11.4 s; -21.4 dB RMS against -12.0 dB for the barrage (in the pack run, before the file was lowered: -13.5 dB).
* The alarm only twice, 8.0 s apart, during the attack; never during the fire tests.

## Not tested

* **Listening by a person:** whether the sounds are mixed well (the loop's grind at a distance, the clunk-to-shot rhythm, the crackle now 6 dB lower, the alarm's loudness against raid music). The recordings prove what played and when, not how it sounds. The alarm's loop seam was not checked (it is not a loop); the turning loop's own seam was not reached in any turn (the longest turn, 7 s, is shorter than the 8.2-second file).
* A real raid: the alarm in a long fight (it repeats every 8 s while the banner is up), Reconnaissance's reveal times in an actual wave (only the rule and the menu were checked), raid bosses in a Gravity Well or a Shockwave (unit tests only).
* Napalm next to the beacon zone (the 4-block no-fire margin is code-read only), fire or snow on uneven ground, in water, on leaves; what happens to a player's own blocks under the snow and light (none were there).
* A world reload while fire, snow or light blocks are placed (they are saved with the flare and removed after the reload: code-read only).
* Volley levels above 0 for the lingering types, Area of effect levels above 0 for the new sizes (unit tests only), the Faster traverse levels in the game (unit tests only).
* A dedicated server, two players (the alarm is client-side per player; the shake and the incoming sound are per client), other GUI scales.
* The pack edition with Create and KubeJS loaded (the development runtime has neither).
