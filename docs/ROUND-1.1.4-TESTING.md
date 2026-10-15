# 1.1.4: what was tested

The owner's 1.1.4 list: the crafting cost balancer and one-line Ages, damage attachments (add-on packs only), more name words, Ardent drops and raid size, elite mobs, Raid control with raid modifiers. Branch `feature/raid-control`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-15). Evidence: `docs/validation/release-1.1.4/` (the check lines of the final run and its screenshots).

## Unit tests

`:eternal-defense:test` 462 pass, 0 fail. New `Release114Test` (16 tests): damage output, medians, rare parts by ratio, placing an unknown gun, one-line Ages, the attachment rule and TaCZ's own kept, the name lists (310 and 323 words, none in both, the first FOB named over 150 ways in 200 worlds), Ardent drops (wild, 300-health boss, marked at twice the chance, elites, the multiplier with modifiers and Luck), raid pay and size, elite counts per level and every elite number, the modifiers' pay, drops, attackers and elites, special raids chosen (no roll), the popup to the other players, the Long Shot's goal priority, every new text, and the guide quoting the code's numbers. The protocol tests moved to 42. `FobTest.noTwoEventHandlersShareTheBusGeneratedName` caught two handlers named `Events.hurt` (elites and trap learning); the elite handlers were renamed. `:eternal-defense-displays:test` 7 pass.

## Real client

`tools/qa/QaRelease114.java.txt`, run with `QA_CREATE=1` and `QA_MODS` holding LesRaisins Tactical Equipments 0.4.3: Eternal Defense, the displays, TaCZ 1.1.8-hotfix2 with its default gun pack, TaCZ Attributes 1.4, Create 6.0.8 and LesRaisins, plus two of the owner's gun packs in `run-client/tacz` (Apocalypse 1.1.7, which has the No. 3 Revolver, and TRIS-dyna 1.1.5, which has attachments that raise damage). A new flat world, survival, Normal; the player cannot be hurt. A config file `config/eternal-defense-ages.json` with one line, `"tacz:m1911": 4`, written before the game starts. Window 1280 x 720. Final run (run 7): **78 of 78 checks pass**.

* **Names.** The world draws its own FOB name salt; fifty salts give fifty different first-FOB names.
* **Balancer** (with these three packs the Age medians are 16.2, 24.1, 38.9, 58.5 and 31.4 damage a second). 15 guns are 1.4 times their Age's median or more; each asks for a rare part: 11 got the balancer's (10 Age 2 guns of the Apocalypse pack a diamond or more, e.g. the MG 08/15 2.88 times its median: 2 diamonds; TaCZ's M870 2 emeralds) and 4 already had one in their author's recipe (TaCZ's M1014, SCAR-H, SPAS-12; the Apocalypse pack's Rorsch Mk4), which is left as it is. The No. 3 Revolver is Age 1 (its line in `age_overrides.json`) and keeps its author's one diamond (with only these packs it is 1.19 times its median, so it is not judged strong here). The M1911 is Age 4 by the config line and takes the Age 4 receiver (hardened, not pressed).
* **Weapon table screen.** A real table placed with a right-click; searching "chauchat" lists the Chauchat (locked, Age 2) and the server marks it 170% of its Age; its diamond heads the cost list; the screen shows "Strong: 170% of its Age's usual damage" above the unlock lines, whole (run 4 showed it cut and over the unlock lines, and the diamond fifth in a list that shows four rows; both fixed).
* **Attachments.** TaCZ's own attachments that raise damage (hollow point, HE, incendiary) are all still sold; the 16 TRIS-dyna attachments that raise damage are not.
* **Ardent from kills.** 40 wild zombies killed by the player: 0 Ardent. A wither (300 health) killed by the player: 1.
* **Elites in a raid at level 1** (a raid with no waves of its own, so each kind could be put next to the player):
  * Badass III has 4 times its kind's health (120 of 30), Badass V 6 times; hitboxes 1.6 and 2.0 times as wide; names over the heads; after the raid's own balancing (3 seconds) still 4 times; the client draws it 1.6 times as big with its hitbox.
  * Flak Armor: 10 from an explosion does 4, from an arrow 12, from a melee hit 12; Body Armor 12, 4, 12; a plain zombie 10, 10, 10; both armored elites 1.5 times the health.
  * A Healer brought a hurt raider and a hurt wild skeleton from 4 to 12 health in 3.5 seconds.
  * A Speedster keeps its +80% speed after the raid's balancing.
  * A Matrioska has half the health; killed, it explodes and two copies stand; killing those gives four; killing those leaves none. Only the first drops its own Ardent (2).
  * Their hits on a pig: Vampiric heals half of what it deals (3 dealt, 1.5 healed); Frostbite gives Slowness II and Mining Fatigue; a Berserker at a tenth of its health hits 1.9 times as hard (5.7 against 3).
  * A Long Shot skeleton sees 101 blocks (85 more) and its arrow exploded at a target 40 blocks away (run 1 found it never shot: the raid's own bow attack outranked its aim; fixed).
  * Badass I to V drop 1 to 5 Ardent, a Healer 2, a Speedster and a Flak Armor at least 1.
* **Raid control at GUI scale 3** (the compact view): the tab is called Raid control and opens on Level & break; the cap, the next raid's level, how it is made, the waves, mobs, elites and pay, and the break card with its price all show; no text and no button label is cut. - lowers the cap to 9 on the server, + raises it back. Buy five active days (under the break card) adds 5 days and +1 level; the cards show "Bought 5 days, +1 of 3 levels", "Next raid: level 1". The Raid modifiers page has Ordinary and the four special raids and six modifiers; Air raid, then Siege (replacing it), then Ordinary are chosen on the server; Reinforcements and Elite Cadre are ticked; the state says +35% payout and +25% drops, the first wave goes from 11 to 15 attackers, elites from 0.2 to 1.2 a wave, the Ardent pay from 3 to 5; the tab reads "Raid modifiers (2)" and the line under it the summary. Tooltips of a modifier and a special raid (screenshots).
* **GUI scale 2** (the roomy view): both pages, no text or label cut, the chips and the notes shown.
* **A real raid with the two modifiers** (started with Start raid at midnight): it runs at level 1 with both, the choice used up; chat names them and their pay; wave 1 sends 15 attackers (11 + 15%, then +30%); during the raid the page shows them ticked and locked with "This raid: payout +35%, Ardent drops +25%". An elite showed up in wave 1 with its chat line and its name over its head; every wave of the 4 had one (Elite Cadre). Won by killing everything as it came: the reward chest got 9 Ardent (3 raised by 35% = 5, plus 4 elite waves); chat said the cache is 35% bigger and paid the elite waves; afterwards no modifiers, the next raid ordinary, and the chat line saying where to choose. Raid mobs dropped Ardent 8 to 16 times in 62 kills over the runs (the chance is 1 in 6 at +25%); every elite dropped its own. In run 7 a Matrioska came by itself in the raid and split.

Earlier runs found and fixed: the Long Shot's aim never ran (priority); one Matrioska copy did not appear when the block beside it was solid (the copies now try their own block, the other sides and one up); the roomy level card cut its note to one line (the cards now fill the height); the special-raid popup appeared on the chooser's own screen and stacked when picking quickly (now only the other players get it); the weapon table's strength line was cut and overlapped a locked gun's unlock lines. Harness only: the window ran at GUI scale 2, so the compact view is now set explicitly; my browse request did not reach the table screen (the search box and its own browse are used now); waiting for an elite while killing nothing stalled the wave at its 12 live raiders; a check read the chat one tick early.

## Jars

All four jars (both builds of the main mod, the gun guide, the displays): no test, QA or note files; every PNG holds image chunks only; no tool, assistant or session names in text, data or class files (the one match of three letters is TaCZ's "PEQ6 ILLM" laser in the catalogue, as before).

## Network

Protocol **42** (was 41): the raid marks packet carries the elites' sizes, the beacon state carries the Raid control numbers and choices, and there is a `raid-mod:` action. A 1.1.3 client cannot join a 1.1.4 server.

## Not tested

* The owner's full pack (all 14 gun packs), a dedicated server and JEI in the run.
* Two real players (the special-raid popup to the others).
* Fog of War, Hardened, Forced March and Exposed Beacon in a running raid (only Reinforcements and Elite Cadre ran one); a special raid picked and fought.
* Raids above level 1 (the balancing path from level 2 with elites in it) and Badass IV and V arriving by themselves.
* Tactical Operation mobs' doubled drop chance; Luck.
* A world saved and loaded during a raid with elites.
* Window sizes other than 1280 x 720 (GUI scales 2 and 3 were run).
