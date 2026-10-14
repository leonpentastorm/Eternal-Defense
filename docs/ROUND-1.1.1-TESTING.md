# 1.1.1: what was tested (in progress)

The owner's list after 1.1.0, on branch `feature/loadout-station`. **This round is not finished:** no complete clean in-game run yet, no release jars or zips, no tag. Evidence: `docs/validation/release-1.1.1/` (screenshots and check lines of runs 12 and 13).

## Unit tests

443 pass, 0 fail (`:eternal-defense:test`). New or changed for 1.1.1: `Release111Test` (attachment types and their texts, the Exchange scroll bar and count line, the Support shop's product tooltip, the cook pot's eating sound, the sandwich hint's position, a name and a description for every Workshop Fabrication card), `LoadoutRulesTest` (the capsule's fall timing: 96 blocks at 4 a tick, hitting as the countdown ends, the door opening within a second; right-click opens, sneak-click prints, the held sneak does not skip the shots), `Round0038Test` (each MRE food slot takes a different food), `FlareVoiceAndAmmoTest`, `RadioAndWhistleTest`, `VoiceEditionTest` (the four Loadout radio lines, their lengths and their no-voice game sound).

## Real client (development run, xvfb, `tools/qa/QaLoadout.java.txt`)

* **Run 12, without Create: 131 pass, 3 fail.** Failed: the first menu layout check read no texts (the frame had not been drawn yet; the harness now waits for it); the attachment table listed one item (without Create the mod's recipe rules give attachment recipes Create iron sheets, so those recipes do not load in a run without Create); the impact shake was not seen on the client (the client's copy of the falling capsule trails the server by a few blocks and was removed more than 12 blocks above its target; the margin is now a third of the drop height).
* **Run 13, with Create (`QA_CREATE=1`): 133 pass, 1 fail.** Passed, among others: a plain right-click on the doors opens the menu and starts no print; sneak + right-click starts a print, and the sneak still held does not skip the shots, a fresh press does; the wardrobe's Active tag; a click on a wardrobe row shows that slot in the closet; the capsule plunges (8 blocks between two samples, 25 ticks from spawn to landing), the explosion sound at the impact, the view shake, the door opening by itself, the capsule staying open while the kit is inside and folding away once emptied, the capsule through the bunker's roof with Quantum Tunneling; the radio answer is one of the owner's Loadout lines; the Exchange (13 offers, 7 rows) scrolls by dragging its bar to the last offers and back with the wheel; the Support shop tooltip holds the whole note and the item's description; the attachment table counts 100 attachments by type (sight 32, muzzle 19, extended mag 17, stock 14, grip 12, laser 5, other 1) and the Sights filter lists only sights; the MRE slots refuse a second bread, shift-click keeps one food per slot, the server refuses a forced repeat; eating stew from the pot plays the eating sound and serves one portion; the sandwich hint ends above the Make button. Failed: "no room in the Hub or the locker: refused, nothing changes" (the refusal message was shown; the check printed no detail; in run 14 the same check passed: no print, 3 coins in slot 1).
* **Run 14, with Create: stopped after 114 passes** (the run was interrupted). One failure: after print 1 the player stood 1.02 blocks from the exit cell (the check allows 0.6; run 13 measured 0.32). **Open: not investigated yet.**

## Network

No packet added or changed: protocol stays **41**.

## Not tested

* A complete clean run of the whole harness (see above), the exit cell after a quick print (open), and the 1.1.0 list that the harness repeats.
* Sounds by ear (no audio device): the owner's four Loadout lines, the impact, the eating sound; only that the server played them.
* The capsule seen by a second real client; the shake's feel; the fall in the owner's full pack.
* The no-voice build's Loadout radio sound in game (only its mapping is unit-tested).
* The new Workshop Fabrication card text in game (unit-tested only).
