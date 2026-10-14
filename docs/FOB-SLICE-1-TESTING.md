# Forward Operating Bases, slice 1 (0.0.23): testing

## How it was run

* **Unit tests** (`:eternal-defense:test`), JDK 17.
* **FOB GameTests** (`FobGameTests`, batches `fob_01` to `fob_08`, opt-in `-Deternaldefense.fobTests=true` or `/fob-test`), run on the real client of each edition at the start of `tools/qa/QaFob.java.txt`: a stone platform in the sky (11 x 11 chunks, kept loaded), a village of a bell, three composters and two beds, villagers without AI, and the online player.
* **Real client** of each edition (Forge 47.4.20 development runtime, `xvfb`, 1280 x 720), `QaFob` on a fresh NORMAL world (seed 20201010, Normal difficulty, creative): after the GameTests it finds the nearest generated village, waits until it counts as a village, and plays slice 1 like a player: the FOB Beacon planted with the client's own right-click (`MultiPlayerGameMode.useItemOn`, sent to the server like a mouse click), the FOB screen opened the same way, the status card with the shovel, `/eternaldefense fob complete`, a purchase with the screen's Buy button, a meal in the zone, a renamed Name Tag, the Field Guide page, the Recovery Shovel and its confirmation screen's Take it back button, the Workshop tab. No sound device; nothing was listened to (this slice adds no sound).

## Results

### Unit tests: 241 pass, 0 fail

New `FobTest` (15): the owner's limits (5 FOBs, 3 villagers, 32-character names, vanilla's one-section village rule, the data name); plant refusals in the brief's order; the raid is lost below half of the villagers counted at its start (for every start from 1 to 40, the number the announcement asks to keep is the smallest that holds); raid status to state (victory, loss, stop, gone); villagers protected only outside their FOB's raid, never against `/kill` or the void; generated names (deterministic, spread, free in any case), renaming rules; the saved data round trip with a broken record skipped and a duplicate name replaced, slice 2 and 3 data kept; the meal zone (Home wins, ACTIVE FOB counts, PLANTED does not); the vanilla-raider Ardent rule; the brief's traps checked in the source (no `eternalRaider` tag set, `CampaignData` only read, `BaseZone.problem` untouched and unused for FOBs, no invulnerable flag, the two-tall footprint, meals through `MealZone`, protocol 31); the footprint read from the owner's model (y 0 to 32, one block wide and deep); the owner's model and texture byte-identical to `docs/art`; every message key the FOB code sends; the Field Guide quoting the real numbers in both editions; **no two event handlers that would share the class Forge generates for them** (added after the first client run crashed on exactly that). Changed: `LangKeysTest` (the FOB Beacon needs its card), `RaidRoundTest` (protocol 31).

### FOB GameTests: 8 pass, 0 fail, in both editions, in each of the last two runs

| Test | What it shows |
| --- | --- |
| `fob_01` planting | the Nether refused; open ground 64 blocks out "not a village"; 2 villagers "too few" and the item places nothing; with 3 it plants; a sixth FOB refused at 5; PLANTED with a generated name, two tall, a vanilla raid at Bad Omen level 1, the Bad Omen used up, 3 villagers counted; a second FOB Beacon in the village refused ("taken"); unbreakable, its upper cell too |
| `fob_02` lost | 4 villagers; during the raid a villager can be hurt; 2 of 4 alive holds; 1 of 4: DOWN within a second, never activated, the vanilla raid stopped; the last villager protected again |
| `fob_03` won | a whole vanilla raid on Normal: 5 waves, 37 to 41 raiders, each killed by the player as it came; vanilla's victory makes the FOB ACTIVE; no Ardent Energy on the ground; every villager alive |
| `fob_04` protection | `/eternaldefense fob complete` makes it ACTIVE and stops the raid; a player, fire, a fall and a zombie hurt no villager; a TNT-size blast beside the FOB Beacon leaves it standing (its upper cell rebuilt) and hurts no villager; lightning turns no villager into a witch; `/kill` still works; a village-raid pillager drops no Ardent in 300 deaths while the same pillager outside a raid and a beacon raid mob do |
| `fob_05` meals | PLANTED zone not home; the status card names the FOB, state, raid and villagers; ACTIVE zone: the meal timer frozen, the bonus doubled, the FOB Zone effect (not Home Zone); out of the zone it counts down and the effect goes; the card then only counts the FOBs |
| `fob_06` shop | the screen opens; PLANTED sells nothing; ACTIVE sells each flare at 6, 3 and 2 Ardent Energy; short of Ardent nothing is taken or given; the screen's prices and village numbers |
| `fob_07` names | renamed (spaces tidied), Name Tag used up; another FOB's name in another case refused and free; 33 characters refused; an unrenamed tag does nothing; the name is saved |
| `fob_08` raid again, commands, shovel | a raid stopped with no result leaves it PLANTED; Call the raid starts a new one; complete, list, status, reset; the shovel asks first (on the upper cell too), then the FOB is deleted, block and upper cell gone, the FOB Beacon back, the villagers mortal |

### Real client, final runs (`checks-standalone.txt`, `checks-pack.txt`): 27 pass, 0 fail, each edition

In the generated village at (-1088, 720): a spot found with 4 villagers in the zone; the FOB Beacon planted where the client clicked, PLANTED, named, a vanilla raid started with the 4 villagers counted, the chat announcement, both cells; the FOB screen opened by right-click (raid card "the first wave is coming", 4 of 4 alive, keep 2); the status card's FOB line with the shovel ("FOB ... | PLANTED | RAID | 4 villagers"); ACTIVE after the command, with its chat line; the screen again, Buy gives a Support Flare and the screen updates in place; the FOB Zone effect on the client with an endless meal; renamed to North Gate with a Name Tag and announced; the Field Guide's FOBs page; the shovel opens the confirmation and nothing is removed until Take it back; then the FOB, its block and cells are gone, the FOB Beacon is back and everyone reads who took it; the Workshop lists the FOB Beacon (its row fits the third row of the Support group).

Screenshots (`standalone-f1` to `f11`, and four of the pack): the owner's model in a real village (front and side), the raid bar, the FOB screen PLANTED with the raid, ACTIVE, after a purchase, the status card, the FOB Zone effect, the Field Guide page, the confirmation, the Workshop.

### Found by the real-client runs and fixed

* **A crash at the first hit on any living thing** (`crash-first-run.txt`): two event handlers sharing Forge's generated class name. Fixed and guarded by a unit test.
* **The village test**: vanilla counts a village only around beds, workstations or a bell that villagers have claimed; the GameTests now claim them, the texts say so, the QA waits for a real village to settle.
* From the screenshots: two clipped lines on the FOB screen and the effect text, "The FOB FOB ..." on the confirmation, "wave 0 of 5" before the first wave, "Supply Flare" where the item is the Support Flare; a confirmation screen left open by a GameTest over the first model shots (harness).
* `checks-*-second-run.txt`: the runs before the last two text changes (27/27 and 8/8 in both editions too).

## Not tested

* Nobody fought a FOB raid by hand: the GameTest kills raiders by command as they appear; the real-village run ends its raid with `/eternaldefense fob complete`.
* A raid lost by the village falling (vanilla's own loss) in a real village; the loss by villager deaths was tested on the platform.
* Flares bought at a FOB and thrown there (the shop hands them over; throwing them is the existing flare code).
* A dedicated server, two players, a FOB beyond view distance during its raid, a save and reload with FOBs (the data's save and load are unit-tested).
* Survival placement by a real mouse in survival mode (the QA player was creative; the GameTests place with the real player's placement context).
