# 0.0.42: the corpse crash, placement, the return zone, magazines: testing

Branch `feature/logistics-hub`, after 0.0.41. Only what is listed under *Results* was run; *Not tested* lists what was not.

## How it was run

* **Unit tests** (`:arsenal-beacon:test`), JDK 17.
* **A real client** (Forge 47.4.20 development runtime with TaCZ 1.1.8 and its default gun pack; `xvfb`, 1280 x 720, GUI scale 2) with three mods from the owner's pack added at run time (`QA_MODS`, new in `tools/qa/setup-qa.sh`): **TaCZ Magazines 0.2.5-Beta**, **Corpse 1.20.1-1.0.23** and **Player Animator 1.0.2-rc1+1.20**, the jars from the owner's Create Arsenal 0.22.1 profile. Harness `tools/qa/QaRound42.java.txt` (`QA_MODS=<dir> tools/qa/setup-qa.sh <dir> pack QaRound42`): a fresh flat world, a beacon 2000 blocks out, survival; every placement made with a real right-click (`useItemOn`, so the client's own prediction runs and the server answers over the connection), the hand counted on both sides.
* **The magazine scan**, `tools/qa/QaMagazines.java.txt`: the same client with the owner's 11 gun packs and the `lradd`/`daffas` packs copied into `run-client/tacz` and the owner's `taczmagazines-*.toml` into `run-client/config`. For each of the 540 guns that can be crafted: the gun held, the ammo table's list worked out at Ages 1 to 5, the first Age at which a round that fits is listed and the first at which a magazine is, and whether TaCZ Magazines gives the gun a magazine family.

## Results

### Unit tests: 399 of 399 pass

New `Round0042Test` (5 tests): the stand-in rule (moved on once per game tick, a real player left alone, Player Animator's API only, at most 20 ticks caught up at once), every block item checks before it places and sends the hand back on a refusal (the zone items, FOB Beacon, kitchens, catapult, weapon tables, Hub; the cancelled-event path), the previews turn red (outline and model, the FOB's own preview, the guide line; the vertex tint is red), the magazine rule (rounds' Age caps the standard magazine's, taken from the recipes the table lists; extended ones one Age per level), the return zone sweep (twice a second, solid only by the platform's own rule, the mod's own blocks and unbreakable blocks left alone, broken out with drops, cannons refused in a zone). `LangKeysTest` passes with the new guide lines (one was too long at first and was split).

### Real client: 32 of 32 pass (the final run, on the committed code)

`docs/validation/round-0042/checks-round42.txt`; screenshots `r42_*.png` in the same folder.

* **FOB Beacon in a hole one block high** (stone on top): the ghost and its box are red, the line under the crosshair says "Something is in the way: the FOB Beacon needs 2 free blocks, one on the other." (`r42_01`); used there: nothing placed, the hand reads 2 before and after the click on the client and 2 on the server.
* **FOB Beacon where only the server can say no** (open ground, no village): not red; the server's verdict arrives (`r42_02`); used before the client had the verdict, the client took the item (2 to 1, its guess), the server refused and the hand read 2 on both sides a moment later. The **Exchange outside the Home Zone**: the same (2 to 1, back to 2).
* **Red previews, refused with the item kept** (inside the Home Zone): the Exchange in a hole one block high (`r42_03`), a Mess Hall with a block on its second column (`r42_04`), the Support Cannon with a block inside its 3 x 3 x 2 (`r42_05`), the Logistics Hub with a block inside its 3 x 3 x 3 (`r42_06`), a weapon table with a block on its second cell (`r42_07`): each red, each refused, the hand unchanged on both sides. A free spot: not red (`r42_08`), placed, one item spent on both sides.
* **Return zone:** a Support Platform placed with its item; sand dropped from 5 blocks up into the zone's first row, gravel then sand into its second, sand beside the zone (`r42_09`): 3.5 seconds later no solid block in the zone's 4 cells, 3 sand/gravel items on the ground, the sand beside the zone still standing (`r42_10`); the server log names each block broken out. Stone placed into the zone with a real click: refused, 64 in the hand on both sides.
* **Ammo table and magazines** (TaCZ Magazines 0.2.5-Beta, an AK-47 in hand, survival): an Age 1 table lists nothing and counts 5 in later Ages (the AK's rounds are a later Age; `r42_11_ammo_age1_all`); Age 3: its rounds, its 30-round magazine and the 34-round Extended I (`r42_11_ammo_age3_all`), the Compatible magazines filter lists both (`r42_12_ammo_age3_magazines`), one bought with Ammo Coins (an empty magazine in the pack); Age 5: the rounds and all four magazine sizes, one bought with coins.
* **The corpse crash** (Corpse + Player Animator + TaCZ): the player died holding an AK-47 (keep-inventory off); the corpse was drawn as a `DummyPlayer` not in the world, holding the gun, a Satellite Beacon turning beside it. With the fix taken off the event bus for ten seconds: 217 frames drawn, 217 fades nested in one layer, one more each frame, the chain that overflows the stack in the owner's log (`r42_13`). Fix back on: within three seconds the latest frame had 12; over the next 30 seconds (706 frames) never more than 14 (`r42_14`). With Idle animations switched off: 245 frames in 10 seconds, at most 14.
* Earlier runs (`checks-round42-first-run.txt` 22 of 30, `-second-run` 29 of 31, `-third-run` 32 of 32) failed only on the harness: checks that ran before their wait (the sand had not landed yet; the chain was measured before the old fades ran out), the corpse looked up after respawning 2000 blocks away (its chunk had unloaded), and an Age-1 expectation that ignored the AK's own Age. The third run's screenshots showed the red tint did not reach the ghost model (a block model's colour only reaches tinted faces); the final run has the vertex tint.

### The magazine scan: 108 guns before, 0 after

`docs/validation/round-0042/magazine-scan-before.txt` and `magazine-scan-after.txt` (one line per gun). 540 guns could be crafted, 425 have a magazine family (the others are TaCZ's own loose-round guns: shotguns, revolvers, launchers). **Before:** for 108 of the 425 the table listed the gun's rounds at an earlier Age than any magazine (for example `daffas_arsenal:hk45`: rounds at Age 2, magazines at Age 3; `bf1:rorsch_mk4`: rounds at 2, magazines at 5). **After:** 0; 417 list rounds and magazines at the same Age, 2 list the magazine earlier. 6 Create Armorer guns list neither (they need Create milestones; this client has no Create).

## Not tested

* A client of the full pack (326 mods), and the Athena mod: the crash was reproduced and fixed with Corpse, Player Animator and TaCZ alone; Athena (a connected-textures library) is not in the stack trace of the owner's earlier log and was not loaded here. The owner's new crash log was not attached to the report, so it was not read.
* The other Player Animator users in the pack (Iron's Spells, LesRaisins Tactical Equipments) on a corpse.
* Placement on a dedicated server with real network latency (the integrated server's connection was used: the client's guess and the server's answer were both observed, but not over a slow link).
* Every block item one by one: the Exchange, Mess Hall, Support Cannon, Hub, weapon table and FOB Beacon were run; the Drink Station, pots, dispensers, MRE Station, Command and Enchanting Tables, satellite and Supply Catapult use the same checks (unit-tested in code) but were not placed in the client. The gates' red preview was not run.
* Pistons and Create contraptions pushing blocks into a return zone (only falling blocks were run; the sweep does not care how a block got there).
* Magazines with Create Armorer guns (no Create milestones in the client), and buying magazines with materials (the client has no Create items; coins were used).
* Multiplayer.
