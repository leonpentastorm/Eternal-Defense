# 0.0.41: one edition, the test pack, the owner's list: testing

Branch `feature/logistics-hub`, after 0.0.40. Only what is listed under *Results* was run; *Not tested* lists what was not.

## How it was run

* **Unit tests** (`:eternal-defense:test`), JDK 17.
* **A production dedicated server of the full test pack**: `tools/release/make-testpack.py` built `Eternal-Defense-0.0.41-test.mrpack` from the owner's exported profile (Eternal Defense 0.22.1, 307 Modrinth files, 19 jars and 6,383 gun-pack files in its overrides) and this build's release jars; `tools/release/install-mrpack.py --side server` installed it (263 downloads, each checked against the pack's SHA-1 and SHA-512; 44 client-only files skipped) on Forge 47.4.20 (the official installer's server), `-Xmx8G`, `nogui`.
* **A real client with Create** (Forge 47.4.20 development runtime with TaCZ, Create 6.0.8, Ponder, Flywheel and Registrate; `xvfb`, 1280 x 720, GUI scale 2), the new harness `tools/qa/QaRound41.java.txt` (`QA_CREATE=1 tools/qa/setup-qa.sh <dir> pack QaRound41`): a fresh flat world, a beacon 2000 blocks out, a Logistics Hub placed with its item by a real right-click, the Mess Hall opened by a real right-click and driven through its own screen's buttons, three real player deaths during a raid, the radio's sound packets counted where the client receives them, the ammo and attachment tables' screen fed the server's state for a gun whose parts all wait in later Ages, Create's goggles worn while looking at the MRE Station, the Satellite Beacon (a minute), the Hub and the Mess Hall.
* **The 0.0.40 harnesses again** (`QaHub`, `QaHubCreate`) as a regression check of the Hub and the supply source.

## Results

### Unit tests: 394 of 394 pass

New `Round0041Test` (11 tests): the MRE Station's rod (the owner's model reaches y 26; the rod moves with the press, the collar stays; the press's travel keeps the rod in the collar), the later-Age hint (code, keys, the pop-up gone), the radio's free/busy rule, the plating recipe (three sheets, no heat, no hardened alloy), the blaze rod (6, the 0.0.28 default moves on, an edited file stays), the Hub zone (effect, icon, hook, milk, status, Overview keys), the skull (3, 2, 1, 3 ...; the penalty falls exactly when one is left), the Mess Hall's Hub stock (fit, signature, Take back into the pack), the installations (20 in the set, 25 points each, 500 for all, one building tier; Mk variants, parts, halves, the beacon and FOB-only blocks; every registered block is either in the set or left out on purpose; the guide line; the Overview cell), no Reference line repeated, protocol 41. `RadioAndWhistleTest` re-measures every radio line's length from its .ogg file against the tick table. `LangKeysTest` knows the new keys. `GuideTextTest` was rewritten for one edition (0.0.41's first part).

### Production dedicated server, the full test pack

* First boot: **refused to start**. Tactical Movement 1.0.0 (`tacticalmovement-1.0.0.jar`, in the pack's own overrides) loads `LocalPlayer` on a server: "Attempted to load class net/minecraft/client/player/LocalPlayer for invalid dist DEDICATED_SERVER", "Mod loading has failed". The jar has 4 classes (keybinds, camera lean), no network channel. `make-testpack.py` now moves it to `client-overrides/mods` (`CLIENT_ONLY`).
* Third boot, with the final jars (the side quest and the text fixes in): "Done (6.520s)" on the world made by the second boot; KubeJS 6/6 server scripts, 0 errors; the same 11 added, 476 removed, 13758 modified recipes; nothing logged by this mod but its usual lines.
* Second boot: **"Done (63.625s)"**, 305 mods loaded (ModernFix: the server took 215 s to load in this container). KubeJS: 1/1 startup and 6/6 server scripts, 0 errors, 0 warnings; "Universal wood inputs applied to 12475 recipes", "reduced conversion materials to 25% in 44 recipes", "Factory gates applied to 1284 TaCZ recipes"; "Added 11 recipes, removed 476, modified 13758, with 18 failed recipes": all 18 failed recipes belong to other mods (createmetalwork 37 parse warnings, deeperdarker 9, delightfulcreators 6, tacticalvests 5, createfood 4 and others; none `kubejs:` or `eternal_defense:`). 11 added recipes is the same count as before 0.0.41 (the plating recipe was replaced one for one). Stopped with SIGTERM: configs unloaded, no crash.
* The other errors in the server log are other mods' data (TaCZ gun pack files `hamster:*`, `ww:i37`, `qkl:mu73_bayonet`; advancements and loot tables of `casualness_delight`, `culturaldelights`, `createmetalwork`, `irons_spellbooks` and others).

### Real client with Create: 40 of 40 pass (the final run, on the committed code)

`docs/validation/round-0041/checks-round41.txt`; screenshots `r41_*.png` in the same folder.

* **Logistics Hub Zone:** no Hub, no effect, the Overview says "This base does not have any active Logistics Hub" (`r41_01`); a Hub placed: the effect on, on the server and the client, endless while you stay (`r41_02`); out of the Home Zone it goes; back in, it returns; the Recovery Shovel picks the Hub up: gone even inside the zone; the Hub placed again (the same record, one record): back; the mod's milk does not list it and it is back a moment later; the Overview says "This base has 1 active Logistics Hub" (`r41_03`).
* **Mess Hall from the Hub alone** (empty pack): with no food anywhere the main button is off (`r41_04`); with the food put in the Hub it turns on within a second and reads Gather ingredients (`r41_05`); a real click lays out 6 food from the Hub (Hub 252 to 246, the pack never holds any, 2 bread in the Base); the button reads Make sandwich (`r41_06`); a click makes a Prepared Sandwich; Take back puts the table's food in the pack and the Hub is unchanged.
* **Raid skull:** a raid next to the beacon reads 3 (`r41_07`); after real deaths 1 and 2 it reads 2 and 1 (red) and the beacon keeps 2000 / 2000 (`r41_08_*`); after death 3 it reads 3 again and the beacon has 1800 / 2000 (a tenth); the Overview shows the skull beside HP during the raid (`r41_09`).
* **Radio:** three flare calls at once: one line (`flare.chatter_1`); a seller order while it plays: no line; after it ended, two orders at once: one line (`seller.order_chatter_1`); later a return flare call: a line again. Counted as the sound packets the client receives (this container has no sound device, so nothing is heard).
* **Ammo and attachment tables:** with 3 and with 1 part in later Ages, both tables say "There are 3 more attachments for this weapon in later Ages ..." / "There is 1 more ammo type for this weapon in a later Age ..." and not "Hold a gun" (`r41_10_*`); with no gun in hand: "Hold a gun and press [H] at this table. It lists what fits that gun." (`r41_11`).
* **MRE Station:** the press with the owner's rod (`r41_12`).
* **Installations (side quest):** the scorer, given one of each of the game's own 20 installation blocks: 500 points; a second Mess Hall (Mk III) and the FOB Beacon, Supply Catapult, Supply Delivery Gate and a Hub part add nothing; without the Command Table 475. The live survey of the test base (the Hub, the Mess Hall, the MRE Station and the satellite): 100 points, in the total (238); the Overview shows "Installations 100 (4/20)" and "Total 238" (`r41_14`).
* **Goggles:** Create's goggles on, looking at the MRE Station, the Satellite Beacon for a minute (`r41_13`), the Hub and the Mess Hall: no `ClassCastException` in the log, the game kept running.
* The first run (`checks-round41-first-run.txt`, 26 of 36) found faults in the harness, not the mod: checks that ran before their wait (it counts a step's delay after the step), a button label read from the widget although the Mess Hall paints its own, a Hub record unplaced while its blocks stood (the Hub then made itself a second record; the shovel's real path is used now), and sounds listened for at the client's sound engine, which does not start without a sound device. Its screenshots also showed two text faults, fixed in the code: the Hub zone effect's description was cut after three lines in the inventory (now "You are in the Logistics Hub zone."), and the later-Age hint ran under the list's scrollbar (now 12 px narrower). The second run (`checks-round41-second-run.txt`) passed 37 of 37 before the side quest was added; the final run adds the three installation checks.

### The 0.0.40 harnesses again

* `QaHub` (pack, the 0.0.40 harness: placement, the 270 slots, hoppers, every rerouted station from the pack and from the Hub, the Hub line, the break guard, the shovel, reload): **99 of 99 pass** (`checks-qahub-regression.txt`; 99 of 99 in 0.0.40 too). It ran on the code before the side quest and the two text fixes.
* `QaHubCreate` (pack with Create 6.0.8: belts, funnels, arms into and out of the Hub, a coil line): **13 of 13 pass** (`checks-qahubcreate-regression.txt`), on the final code.

## Not tested

* A real client of the full pack (326 mods): the pack ran only as a dedicated server. The owner's single player test of `Eternal-Defense-0.0.41-test.mrpack` is the next step.
* The later-Age hint with a real TaCZ gun and real gun packs: the development client has no TaCZ gun packs, so the screen was fed the state the server sends for such a gun (`locked`, `notice`); the server's count of `locked` is unchanged code.
* The radio as heard: the sound packets were counted, nothing was listened to.
* The satellite crash in the owner's log: it is a `StackOverflowError` in Player Animator rendering a Corpse mod body; neither mod is in the development client. The `ClassCastException`s in Create's goggle overlay could not be traced to a mod (no stack trace in the owner's log) and did not appear here.
* The plating recipe in JEI on a client, and compacting it with a real Mechanical Press: the recipe was only checked as loaded (KubeJS: 0 errors, the same 11 recipes added).
* Multiplayer: several players' radios, the Hub zone effect for players on a dedicated server.
