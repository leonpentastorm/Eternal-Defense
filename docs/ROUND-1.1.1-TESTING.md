# 1.1.1: what was tested

The owner's list after 1.1.0, on branch `feature/loadout-station`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-14). Evidence: `docs/validation/release-1.1.1/` (screenshots and the check lines of the final run).

## Unit tests

444 pass, 0 fail. New or changed for 1.1.1: `Release111Test` (attachment types and their texts, the Exchange scroll bar and count line, the Support shop's product tooltip, the cook pot's eating sound, the sandwich hint's position, a name and a description for every Workshop Fabrication card), `LoadoutRulesTest` (the capsule's fall: 96 blocks at 4 a tick, hitting as the countdown ends, the door opening within a second; right-click opens, sneak-click prints, the held sneak does not skip the shots; the player let go standing on the exit cell), `Round0038Test` (each MRE food slot takes a different food), `FlareVoiceAndAmmoTest`, `RadioAndWhistleTest`, `VoiceEditionTest` (the four Loadout radio lines, their lengths and their no-voice game sound).

## Real client (development run, xvfb, TaCZ, TaCZ Attributes and Create running: `QA_CREATE=1`)

`tools/qa/QaLoadout.java.txt`. Final run (run 15): **134 of 134 checks pass**. Earlier runs found and fixed:

* the camera shake of the impact was missed when the client's copy of the falling capsule (a few blocks behind the server's) was removed more than 12 blocks above its target; it now shakes when removed within a third of the drop height;
* after a print the player slid on for up to a block (the walk's last step left them moving; 1.02 blocks off the exit cell in one run); the last held step now leaves no speed, and the server puts the player on the exit cell when more than 0.5 blocks off as the doors start to close (in the final run this happened once, 0.75 blocks, after the sneak-skip print);
* the Loadout Station's Workshop Fabrication card showed its raw text key (owner's report): the name and description were missing;
* harness only: a layout check read the screen before a frame was drawn; table rows shorten long names on purpose (their tooltip has the whole name).

Without Create (run 12) the attachment table lists almost nothing: the mod's recipe rules give attachment recipes Create iron sheets, so those recipes do not load without Create. The owner's pack has Create.

What the final run did for 1.1.1 (the 1.1.0 checks ran again and passed):

* **Clicks.** A plain right-click on the doors opens the menu and starts no print. Sneak + right-click starts a print; the sneak still held from that click does not skip the camera shots; a fresh press after letting go does. Every other print in the run was started by a sneak-click.
* **The wardrobe.** The active slot carries an Active tag; a click on a wardrobe row (beside the slot) shows that slot's kit on the closet figure, and back. No text of the station's screen is cut or over another.
* **The capsule.** It appears 96 blocks over its cell, falls 24 ticks (8 blocks between two samples of the harness), the explosion sound plays at its cell, the view shakes, its door opens by itself before anyone touches it, it faces its owner. Closed with two items inside, it stays open; emptied, it shuts and folds away. Under 3 blocks of stone with Quantum Tunneling it falls through the roof (the roof unbroken) and stands up inside; the Nether, the low-ceiling parcel and the nearest-cell cases as in 1.1.0. The radio answer is one of the owner's Loadout lines (`flare.loadout_chatter_N` played on the owner).
* **Exchange.** 16 offers, 7 rows: dragging the scroll bar to the bottom shows the last offers, the wheel goes back to the top; no text cut or over another.
* **Support shop.** Hovering the Loadout Flare shows its whole note and the item's description.
* **Attachment table** (creative, every Age open, nothing in hand): 100 attachments counted by type (sight 32, muzzle 19, extended mag 17, stock 14, grip 12, laser 5, other 1), each in exactly one type; the type picker; "Sights & scopes" lists only sights.
* **MRE Station.** A second slot refuses a bread another slot holds; shift-click stacks the bread on its own slot and puts the apples in another; a pack forced to hold the same food twice is refused by the server (`same_food`).
* **Cook pot.** Eating stew with a bowl plays the game's eating sound and serves one portion.
* **Mess Hall.** The sandwich hint ends above the Make button.

## Network

No packet added or changed: protocol stays **41**.

## Not tested

* Sounds by ear (no audio device): the owner's four Loadout lines, the impact, the eating sound; only that the server played them.
* The no-voice build's Loadout radio sound in game (only its mapping is unit-tested).
* The capsule seen by a second real client; how the shake and the fall feel; the owner's full pack (326 mods) and a dedicated server.
* The attachment table in survival with a gun in hand (the run used creative with an empty hand, which lists every attachment).
* The new Workshop Fabrication card text in game (unit-tested only).
* The screens at window sizes other than 1280 x 720.
