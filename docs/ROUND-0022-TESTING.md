# Radio lines, ammo parts, hunt pay (0.0.22): testing

## How it was run

* **Unit tests** (`:eternal-defense:test`), JDK 17.
* **Real client** of each edition (Forge 47.4.20 development runtime, `xvfb`, 1280 x 720), `tools/qa/QaHunt.java.txt` on a fresh normal world, as in 0.0.21, with three additions: after the Starshell the player throws a supply flare (with something in the platform's grid) and then a return flare, and the client's sounds are checked for the flare's own line; the Warlord's pay is checked against the beacon's Next payout tier; the GameTests include `hunt_14`. The game's audio output was recorded (OpenAL Soft's `wave` backend) and searched with `tools/audio/find_sfx.py`. **Nobody listened.**
* **Not runnable here:** the KubeJS recipe script (the development runtime has no KubeJS and no TaCZ gunpacks).

## Results

### Unit tests: 226 pass, 0 fail

* New `FlareVoiceAndAmmoTest` (7): every flare kind has its own lines (supply 2, recall 3, fire support 6, event names); the last line is kept per player and per kind, and every thrown flare answers, return flares too; the import script knows the two new groups; ammo takes twice the propellant and 20 percent more cases rounded up, for every batch from 1 to 256 rounds (8 rounds: 2 and 2; 32: 5 and 4; 64: 10 and 8); the KubeJS script and the Ammo Platform both call `AmmoInputs`, the old formulas are gone, the standalone edition does not use it, and the class and its two methods are public (KubeJS can only load a public class); hunts pay and fight at the beacon's Next payout tier (`Hunts.tier`, the function the Overview uses) and none reads the last won raid's tier; a hunt pays at least 1 Ardent Energy and 2 coins for every class, tier and edition, and the guide says so.
* Changed: `RadioAndWhistleTest` (the lines of every flare kind are shipped, mono, 2.5 to 5 s, with subtitles; the supply flare has 2 lines, the return flare 3).

### Hunt GameTests: 14 pass, 0 fail, in every run of both editions

The thirteen of 0.0.21 and `hunt_14`: a campaign whose last paid tier is 0 while its beacon shows Next payout tier 5 (the owner's case) pays a Patrol at tier 5: 4 Ardent Energy and 13 coins in the standalone edition, 2 and 6 in the pack edition (it paid 0 and 1 before the fix).

### Real client, final runs (code with the pay fix)

| Edition | Checks | Failed |
| --- | --- | --- |
| Standalone (`checks-standalone.txt`) | 73 pass, 0 fail | |
| Pack (`checks-pack.txt`) | 71 pass, 2 fail | the arrival tick time and one Garrison lookup (both timing, see below) |

* **Radio lines:** a supply flare answered with `flare.supply_chatter_2` and a return flare with `flare.recall_chatter_3` in both editions (no fire support line). In the recording the supply line is found at 0.94 (standalone) and 0.95 (pack); the recall line, which played while the cannon turned towards the supply flare and clunked into lock, only at 0.35 to 0.36, where the client log puts it (`audio-standalone.txt`, `audio-pack.txt`). In the first standalone run a recall line played with nothing on top of it and matched at 0.81 (`audio-standalone-first-run.txt`).
* **Hunt pay:** the Warlord at the objective paid exactly the pay at the beacon's Next payout tier (2 + 7 standalone, 1 + 4 pack, at tier 0 in this test world, whose last paid tier is 3).
* **Everything of 0.0.21** passes again (HUD rule, anchor, red marks, map burning, Starshell Haste, night raids, booking, armed raiders, the ordinary zombie, the alarm's 22 ticks).
* **The two pack failures are timing on this machine, in code 0.0.22 does not change.** (1) While the warband came out, the mean server tick was 50.9 ms against 31.8 ms idle (+19 ms; the check allows +15). Across this round's runs the same code added +13, +8 and +19 ms, and +4 and +12 ms in 0.0.21: the QA machine's software-rendered client shares the CPU with the server, so this number swings. (2) One Garrison lookup took 64 ms (the others 1 to 35 ms; 63 ms once in 0.0.21, 32 to 35 ms in its later runs).

### Earlier runs this round (kept as evidence)

* `checks-standalone-first-run.txt` (71 / 73): the supply flare was thrown at an empty grid, which is refused ("Your supply grid is empty. Your flare was not used.") and so, correctly, has no radio answer; the QA now fills the grid first.
* `checks-pack-before-tier-fix.txt` (72 / 72) and `checks-standalone-second-run.txt` (72 / 72): the code before the pay fix; there the two flares were thrown a second apart and their lines overlapped in the recording (`audio-pack-before-tier-fix.txt`: both found at 0.48 to 0.51 only).

## Not tested

* **Nobody listened** to the five new lines.
* **The KubeJS ammo recipes** were not run (no KubeJS or gunpacks in the development runtime): the script now calls `AmmoInputs` through `Java.loadClass`, as it already calls `HistoricalGuns`; the unit tests check the numbers and the calls. The first real check is the owner's pack: an ammo recipe in JEI or at the Ammo Platform.
* The Ammo Platform's own costs in a client (the factory items exist only with KubeJS).
* The hunt minimum in a real game (unit tests only; in the QA world every pay was above it).
* A dedicated server and two players.
