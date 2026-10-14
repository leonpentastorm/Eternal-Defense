# 1.1.2: what was tested

The displays round (owner): putting a gun on a stand with one press of TaCZ's interact key, a crafting recipe for every stand, the Weapon Display Tuner. Branch `feature/display-tuner`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-14). Evidence: `docs/validation/release-1.1.2/` (screenshots and the check lines of the final run).

## Why a gun went in only "by spamming both keys, at certain angles"

* TaCZ cancels a right-click with a gun in hand (the right button aims) unless its interact key is held down at that moment. Its interact key only "uses" blocks on its `tacz:interact_key/whitelist` block tag, and the stands were not on it. So a press of H did nothing, and a right-click did something only while H was held.
* A wide stand's two halves each had an outline inset from the middle, leaving a gap right where the gun sits. Aiming at the gun (the natural spot) went through the gap and hit the wall or floor behind. Single floor stands were also narrower than the gun lying on them, and the heavy stand's outline (6.75 px tall) was below its gun (13.5 px up).
* Also found: emptying a stand sent an empty update, which the game drops, so players kept seeing the old gun on it until the chunk reloaded.

## Unit tests

`:eternal-defense:test` 446 pass, 0 fail (new: `AssetFilesTest`, every shipped texture holds image data only and every sound names only its encoder). `:eternal-defense-displays:test` 7 pass, 0 fail (new `DisplaysTest`: every stand and the tuner have their own shaped recipe with vanilla items and no two share a grid, an unlock for each, the interact-key tag lists exactly the 11 stands, the tuner's limits, steps and slider positions, every text the mod shows is in the language file, the tuner's icon and model exist).

## Real client (development run, xvfb, TaCZ, TaCZ Attributes and Create running: `QA_CREATE=1`)

`tools/qa/QaDisplays.java.txt`. Final run (run 3): **150 of 150 checks pass**. Survival mode, the player flying in place, every press a real key event through the game's keyboard handler and every right-click the game's own right-click routine.

* **The interact key, on every stand, from four angles.** For each of the 11 stands, from the front, from 40 degrees left, from 40 degrees right and from above: one press of the interact key (H) with a gun in hand puts it on the stand and empties the hand; a right-click with an empty hand gives it back, and this client then sees the stand empty (88 checks).
* A right-click with a gun only aims: the stand keeps its gun, the gun stays in hand. The interact key with another gun swaps them. The gun keeps its name and ammo on the stand. A stick in hand leaves the stand alone.
* The stand's tooltip names the player's own interact key and the tuner. TaCZ's own interact list accepts all 11 stands.
* **Outlines.** Wide halves meet in the middle; floor stands span the block; the heavy stand's outline reaches 20 px (over its gun) while its collision stays 6.75 px.
* **Recipes.** Each of the 12 grids (11 stands, the tuner) matches only its own recipe among every loaded mod's (Eternal Defense, Create, TaCZ and the rest of the development run). Two stands and an iron ingot no longer make a wide one. Holding an iron ingot unlocks all 12 in the recipe book. All 12 were made at a real crafting table by placing the items one by one, and each landed in the pack.
* **Tuner.** An empty stand answers "Put a gun on the stand first." and opens nothing. On a stand with a gun the panel opens, the game keeps running behind it, its 25 controls fit at GUI scale 2 (640 x 360) and 3 (427 x 240) with nothing outside, overlapping or cut, the hint in two lines. Size + ten times reads 150% and reaches the server; a click at three quarters of the Turn bar gives +90; then Up +4, Left/Right -8, Tilt -30, Roll +45, Back/Front -3 all reach the server. Done closes it and this client shows the new settings at once. Copy, then Paste on another stand gives it the same settings; Reset brings back the default and the stand saves no settings entry. The settings save with the stand and read back; a gun swap keeps them.
* **The server's checks.** Wild numbers are held to the limits (300%, 16 px, 180 degrees); a change sent without a tuner in hand, from 20 blocks away or for an empty stand is ignored. The panel closes when the gun is taken from the stand. Sneaking with the tuner still opens it.
* **What each setting does** (one at a time, front view, `dp2x_*.png`): Right +8 moves the gun to the viewer's right, Up +8 up, Front +8 toward the viewer, Tilt +30 raises the muzzle, Turn and Roll turn it about the vertical and the barrel, Size 200% and 50%.

Earlier runs found and fixed: the outline gap of wide stands and the narrow floor and heavy outlines (above), the emptied-stand update, Tilt's direction (plus lowered the muzzle), the hint needing three lines. Harness only: the camera put the player's feet in the ground (the game pushed the player aside and survival flight switched off, so the aim drifted); too many armor stands in one slot; the panel's picture aimed beside the stand.

## Network

The displays mod has its own channel now, `eternal_defense_displays:main`, protocol 1 (one packet, client to server: a stand's settings). Client and server must both run 1.1.2. Eternal Defense's protocol stays **41** (no packet of it changed).

## Not tested

* The owner's full pack (326 mods) and a dedicated server; TaCZ's interact key with a server config that blacklists blocks.
* Two players: the tuned gun as a second client sees it; two players tuning one stand at once.
* A raid's repair bringing a stand back with its settings (it restores the stand's whole saved data, so it should).
* The interact key bound to a mouse button (the harness pressed a keyboard key).
* Guns from gun packs other than TaCZ's default (the run used the AK-47 with three attachments and the M4A1).
* Window sizes other than 1280 x 720 (GUI scale 3 was checked in that window).
