# 1.1.0: what was tested

The Loadout Station, Loadout Coins, the Loadout Flare and its capsule, on branch `feature/loadout-station`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-14). Evidence: `docs/validation/release-1.1.0/` (screenshots and the harness's check lines).

## Unit tests

434 pass, 0 fail. New for 1.1.0:

* `LoadoutModelTest` (11): the installed models parsed from the jar's JSON: every cube inside -16..32, the standing point, nozzles, vents, arm tips and chute anchor inside the bounds, the doors' 100-degree travel fits the 105-degree check of the README, the face counts of the README, the collision of every cell and the standing point free in all four facings, the capsule's collision.
* `LoadoutRulesTest` (16): a 14-slot NBT round trip (a TaCZ-shaped gun with attachments and rounds, enchanted armor, names); coins and filled containers refused; the size guard on the saved size, a string too long for NBT counted in full; a slot remembers its kind; coin source order (slot, hand, Hub); cooldown across a save; one print per machine and per player; in front, within reach, clear line; the timeline is 6 to 7 seconds; a stop before the swap changes nothing; a Loadout call takes its coin only when the cannon fires; the capsule door; a late client catches up without a jump; a client up to 3 ticks behind is not put back.
* `LangKeysTest` maps the Loadout items to the "Loadout Station" Field Guide card; `Round0041Test` leaves the station out of the installations score.

## Real client (development run, xvfb, TaCZ and TaCZ Attributes running, no Create)

`tools/qa/QaLoadout.java.txt`, flat world, Peaceful (the flat world's slimes killed the player in an early run), one real client and, where a second player was needed, a server-side fake player. Final run: **104 of 104 checks pass** (`checks-loadout.txt`; the run before it, without the name tag fix, also passed 104 of 104). Earlier runs found and fixed:

* the size guard missed a 70 KB string (the game silently saves such a string empty); now refused as too big;
* the PRINT camera sat against the player's arm (the clear cavity is 14 px wide); it now looks down from the cavity's top front corner;
* TaCZ drew its first-person gun over the camera shots; the hand pass is now skipped while a shot is on;
* a print could start with a 0.76-block snap when the client learnt of it a few ticks late, and the server put such a client back on the path (6 corrections in one print); the client now catches up at no more than 0.45 blocks a tick and the server allows 3 ticks of lag;
* texts cut off on the station and capsule screens; the screens are wider and the harness checks that no text is cut or overlaps;
* the closet figure carried the player's name tag over its head; the tag is no longer drawn there.

What the final run did, in order:

* **Placement.** Refused outside the Home Zone (item kept); placed with a real right-click on the ground: 27 cells pointing at the anchor, facing the player, the bottom-centre cell on the clicked spot, the item used up. Collision of the north-facing station: anchor none, bottom-centre floor 2.5 px, top-centre from 5.5 px, closed door solid. Stations facing east, south and west (set by command): 27 cells each, the standing point free for the player's box in all four facings. Removing an anchor removes its 26 cells and its record. Blast resistance 3,600,000, fireproof item.
* **Coin.** Kit A (enchanted, damaged diamond armor, shield, a TaCZ AK-47 with 4 attachments: `sight_pk06_rifle`, `stock_ak12`, `extended_mag_3`, `muzzle_brake_pioneer`, 30 rounds and a name, 64 of its ammo, golden apples, pearls, a named enchanted sword, torches, bread, a water bucket, a compass) moved into the pack area with real shift-clicks (armor to its slots, the shield to the off hand). Make coin with the fee in the pack: one coin, the fee gone, the pack area empty, the coin's 14 stacks equal to the originals tag for tag, the gun with its 4 attachments and 30 rounds. **A full coin is 1,560 bytes of NBT** (the limit is 64 KB). Refused, with nothing taken: a filled shulker box, a coin inside the kit, a paper with 70 KB of data.
* **Slots and the closet.** Slot 1 takes the coin and remembers its kind; an identical coin stacks (2); a different coin is refused there and goes to slot 2. Set active is stored per player (another player's stays slot 1). The closet figure wears the kit being packed (diamond armor, the AK in hand, the shield), then slot 1's coin; > shows slot 2 (chainmail, crossbow), < goes back. No name tag over the figure on the station and capsule screens (seen on `lo11`, `lo13`, `lo53`; not a check line).
* **Print 1 (camera shots on, no Hub):** started by a real right-click on the doors. Timeline from 2.5 blocks out: **entry 20 + cycle 80 + exit 15 + doors 12 = 127 ticks (6.35 s)**. Camera shot on during the walk in; at the swap the kit is on exactly, slot 1 went from 2 coins to 1, the old gear is in the Locker, the 27 main slots untouched. **Jitter:** from tick 10 on the player was 0.000 blocks off the synced path (114 samples), the largest step 0.38 blocks, no step over 0.5, **0 server corrections**; this time the client learnt of the print in time, so there was nothing to catch up (in the run before it caught up from 0.19 blocks at the start, never faster than 0.45 blocks a tick). The HUD was hidden during the shots (the hotbar overlay was cancelled in 72 frames and drawn in 11; the count ran from just before the right-click to just after the print, so the drawn frames include those before the first shot and after the last, and frames were not matched to shots one by one); afterwards first person, HUD on, the player at the exit cell 2 blocks out, the 30-second cooldown running, the action bar naming the Locker.
* **Refusals:** during the cooldown (nothing starts, the coin stays), 7 blocks out (far), a stone in the way (path), standing beside the doors (aside).
* **Hub:** placed with a real right-click; a second coin (kit B) made with the fee from the Hub, and the Hub's chat line "Dev just pulled 4 Reinforced Beacon Plating and spent 1 Ardent Energy from the Hub".
* **Print 2 (camera shots off, watched from a still camera outside):** no shots with the option off; the doors, the walk and the ring seen from outside (screenshots); a fake second player asking at the same time is refused "busy" and the print stays the first player's; the held player takes no damage and item use is cancelled; a dropped item comes back; the old gear (kit A, the gun with its 4 attachments) went to the Hub. Jitter: from tick 10 on 0.000 blocks off the path, caught up from 0.19 blocks at the start, largest step 0.45, no step over 0.5. The server made 3 corrections, all from the test's still camera: a client whose camera is another entity sends no positions (vanilla `LocalPlayer.sendPosition`), so the server's copy fell behind; every print seen through the player's own camera had 0.
* **No room:** the Hub and the Locker full: refused, nothing changes.
* **Stops before tick 40** (each with the camera shot on before the stop): death (/kill), dimension change (teleported to the Nether), logout (a player not in the player list): the print stops, the coins stay, the gear is unchanged, the camera is back in first person with the HUD on, the station is free again.
* **Skipping:** Esc during a shot: no pause menu, the shot stops, the print goes on and the kit is printed; sneak: the same, and the player is not left sneaking.
* **Late joiners:** a station block entity rebuilt from the update tag mid-print has the same occupant, start, timeline and door angle.
* **Night:** day and night pictures of the front at the same spot. The glow parts kept their brightness at night (the two header lamps 100 %, the coin bank lamps 96 %) while the unlit door, side panel and floor stripes fell to 57 % (the station gives light level 7): **the glow works**, no change to `forge_data` was needed.
* **Loadout Flare:** three bought at the Support Platform for 5 Ardent each. With no coin in slot 1 or the Hub: refused, the flare kept. With 2 coins: thrown outdoors; a second throw at once refused ("still on its way"); the coin still in the slot after the throw, taken when the cannon fired; a capsule fell under the parachute and stood up two blocks high where the flare landed, facing its owner; another player cannot open it; its menu opens, its figure wears the kit; Equip with a leather helmet worn and a stick in hotbar 1 filled every other slot and left the diamond helmet and the AK in the capsule; the gun in the capsule is the exact gun; the door state is in the update tag; emptied and closed, it folded away.
* **Underground** (a room under 3 blocks of stone): without Quantum Tunneling the flare came back and the coin stayed; with it a capsule stood up inside the room and used the coin.
* **No room for a capsule:** under a wide 1-block-high ceiling: the old supply parcel (beside the caller, as for an underground supply call) and the chat line; under a small one: the capsule stood on the nearest free two-high cell within 3 blocks.
* **The Nether:** without the Dimensional Link refused, the flare kept; with it a capsule stood up in the Nether and used the coin.
* **Break guard and breaking:** a survival player cannot mine the station while coins wait ("Empty the machine first"); broken by a command at tick 20 of a print: the print stops, nothing used, every coin of the slots drops as items, the gear unchanged, the camera back, the cells gone.

## Network

No packet added or changed: protocol stays **41**. The menu uses the existing menu-button packet and container data; the print and the capsule's door travel in block update tags.

## Not tested

* **Two real clients:** a second real player racing for the station, seeing the walk, doors and effects without the camera shots, or joining mid-print. The race and the busy lock were checked with a server-side fake player, the late joiner by rebuilding the block entity from its update tag, the outside view with a still camera on the printing client.
* **A real logout and relog** of the client during a print or during the cooldown (the logout path was driven by a player missing from the player list; the cooldown's save and load is unit-tested).
* A dedicated server, network latency beyond the integrated server's, the full pack (326 mods), Create running in the same client.
* **Sounds** (no audio device in the container): the door, steam and cue sounds were not heard.
* A capsule left unopened for 30 minutes (lifetime), a capsule whose owner is offline, a capsule landing in water.
* Backpacks and bundles in the pack area (only a filled shulker box was tried; bundles are experimental in 1.20.1).
* Placement facing east, south or west with a real right-click (those three were set by command); placing on uneven ground.
* Dragging the closet figure to turn it (not tried); the screens at window sizes other than 1280 x 720.
