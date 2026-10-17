# 1.1.19: what was tested

The owner's list after 1.1.18, sent during the round, all on `feature/power-defense`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-17). Evidence: `docs/validation/release-1.1.19/`.

## What was asked, and how it was read

* **"See if a dynamo is working, how much stress it uses and how much the network connected to it uses."** An empty-hand right-click on a dynamo prints its state (working, not turning fast enough, down because its own or another dynamo's Create network is overstressed, no pole), the stress units it carries, the whole power network's total and its Create network's use and capacity. Engineer's Goggles show the same under Create's own lines. Pole status lines give stress units too.
* **"The shaft of the Shaft Dynamo should rotate the same direction as the Create shaft. Check both dynamos."** The Shaft Dynamo was a Create gearbox, which turns the far side the other way. It is now a plain Create kinetic block with shafts on its four sides: every side turns at the input's speed and sign, and its halves are drawn at the shafts' own angle. The Cog Dynamo meshes like Create's small cogs (it turns the other way to the cogwheel it meshes with, as two meshed cogs must); unchanged.
* **"The zone that poles power should be shown, maybe toggleable."** While any defense gear is in hand, every pole within 64 blocks shows its reach as an outline on its floor (faint at the lowest and highest layer, with uprights); blue when powered, orange when down; a pole in hand shows where its reach would be. Sneak and right-click a pole with both hands empty to keep its outline on.
* **"Which turret is online needs an indication, and its ammo inventory should open by hand."** A lamp above each turret (green ready, orange out of ammo, red no power) and the same line in words under the crosshair. Right-clicking a turret with anything but its ammo opens a screen: its state, shots loaded, range, stress and its four ammo slots, filled and emptied by hand.
* **"Flamer is weak, buff it; machine gun about 20% less damage; the flame a hitbox in front instead of pellets, for performance and one flamethrower sound."** Machine gun damage x0.8. Flame: longer (8, 10, 12), twice the damage (12, 16, 20 a second on every mob in it), longer fire (5, 6, 7 s). The flame is a box along the aim (2, 2.5 and 3 blocks wide) cut short by the first block in the way: one sight ray a burst instead of one per mob, and no particle packets (each client draws the flames). One sound plays while it burns.
* **"Shotgun and machine gun eject casings out the side, visual only."** Each shot throws a brass case (a red shell from the shotgun) out of each gun's side, drawn by the client.
* **The owner's sounds.** Machine gun: `LMG_Eternal_Defense.mp3`, "30% quieter because it plays a lot" (its 0.83 s of leading silence cut, then -3.1 dB, 0.7 of the amplitude). Flamethrower: `Flamethrower.mp3`, "only the first 4 seconds: second 1 to start firing, 2 to 3 firing, 4 ending" (0-1 s start, 1-3 s seamless loop, 3-4 s end). Shotgun: `M1014-4Suppressor.wav`, "30% quieter and some reverb" (a short room reverb, 0.8 s, 35% wet, then -3.1 dB).
* **"Placing preview for the barricade and sandbag is bugged; not just the preview, both show a missing model."** The owner's pack has Diagonal Walls; see below.
* **"Barbed wire's electric effect should be animated."** The live overlay textures are now 8-frame animations (new arcs every 2 ticks), and live wire throws electric sparks.
* **"One Shock Box can only shock 15 barbed wire, like the spread range of redstone."** One box makes at most 15 joined Barbed Wire blocks live, the nearest first from the blocks it is wired to (was 48 from each wired block).
* **"Triple the Badass health bonus on all levels."** +300% health a level (4, 7, 10, 13, 16 times; was +100%, 2 to 6 times).
* **"Shotgun turret knockback, about a quarter of a player's punch at max level."** A punch knocks back with strength 0.4; each shotgun shot pushes every mob it hurt away from the turret with 0.1 at Mark III (0.05 Mark I, 0.075 Mark II), knockback resistance applying.

## The missing model: Diagonal Walls

The owner's pack lists `DiagonalWalls-v8.0.4-1.20.1-Forge.jar` (with Puzzles Lib 8.1.33). Read from its code: it registers a copy of every block that extends Minecraft's wall class with exactly the vanilla wall properties (`diagonalwalls:eternal_defense/sandbag`, `.../barricade`), builds a model for the copy from the original's, and makes the item place the copy. Our walls' models do not translate, so the copy is the magenta cube; it is also not our class, so it would not resist shots or blows. Its own remedy is the block tag `diagonalwalls:non_diagonal_walls`: for a block in it, the item places the original. Sandbags and barricades are in that tag now (optional entries, so nothing changes without the mod). Barbed Wire has a sixth property (`powered`), so Diagonal Windows never took it, which is why it was fine.

Separately, the placement ghost of walls and Barbed Wire drew nothing in any pack: it asked the multipart model for its translucent parts only, and Forge's multipart model drops parts of other layers. The ghost now asks for every part.

## Unit tests

`:eternal-defense:test` **537 pass, 0 fail**. `Release1119Test` (13 tests):

* **Dynamo state** for each case; **stress units** (share x RPM summed); the readout only resends when something shown changes; the right-click lines and their numbers; the wiring (no gearbox, shafts on the four sides, an item in hand still places, goggles, halves at `getAngleForBe`, the grid reports to the dynamos); all new text keys.
* **Turret lamp** (ready, empty, off); **reach outline** edges lie exactly on the reach's boundary, none twice; the turret screen works on the turret's own slots while machines still only insert, slots take only their ammo, the menu and screen are registered, the lamp is drawn, the crosshair line uses the shots the client heard, the reach toggle is saved; the guide no longer says ammo cannot come out.
* **Flame jet**: a mob in front and within half the width is in it, one too far aside, past the end (where a wall cuts the jet) or behind the nozzle is not; the jet's pitch stays within the mount's; machine gun damage is 0.8 of 1.1.18's at every Mark, flame damage at least doubled and reach longer, still the shortest line.
* **Shock Box**: 15 of a 20-block line, the first 15; two spans from one box share the 15; a shorter run is all live.
* **Sounds and art**: the five sound events, their files and subtitles; the turrets play them; no flame particles sent by the server; the shot event is passed from the block to its entity; start, loop and end in the client code; the animated live textures have 8 frames and an animation file; live wire sparks; the Diagonal Walls tag; the ghost draws every part.
* **Badass**: 3, 6, 9, 12, 15 extra health per level step (4x to 16x), other elites unchanged; **shotgun knockback** 0.05, 0.075, 0.1 and none for the other lines.

Old tests updated for intended changes: `Release114Test` (the Badass bonus). `:eternal-defense-gun-guide:test` 8 pass, `:eternal-defense-displays:test` 7 pass.

## Real client

`tools/qa/QaRelease1119.java.txt` ran with `QA_CREATE=1` (Create 6.0.8, Flywheel, Ponder) in a new flat world at 1280 x 720, with the game's audio written to a WAV file by OpenAL Soft's `wave` backend (the machine has no sound card; nobody listened). A Defense Beacon was planted; blocks were placed with real right-clicks holding their items, unless noted. Separately, `tools/qa/QaWallPreview.java.txt` ran with **Diagonal Walls 8.0.4 and Puzzles Lib 8.1.33** from Modrinth added (`QA_MODS`).

### Run history

* **Run 1: 41 of 44.** The turret block never passed its shot event to the block entity, so the event never reached the clients: no casings, and (since 1.1.18) no recoil. Fixed in the mod (`Turrets.TurretBlock.triggerEvent`, as the cannon and catapult do). Two harness mistakes: the reach-preview camera stood beyond block reach, and the wiring clicks stood too far from the pole.
* **Run 2: 45 of 46.** The Badass check assumed a husk's 20 health, but the world's tier scaling (`FactoryTiers`) had raised the test husks' base to 60 before the elite bonus; the check now compares against each mob's own base.
* **Run 3: 45 of 46.** The flame particle count was read 1 to 3 ticks after the jet lit; the harness now lets it burn half a second first.
* **Run 4: 46 of 46**, but the pictures showed two problems: the casings were the whole shared item texture sheet shrunk down (the Cartridge Case item model draws from `industrial_atlas`), and the dynamo close-ups were hidden behind a red placement ghost (the player still held a dynamo). Fixed in the mod (casings use the plain `item/cartridge_case` texture) and in the harness (empty hand for those pictures).
* **Run 5: 47 of 47.** Results and pictures below.

### The checks (run 5)

* **Shaft Dynamo:** placed by right-click; a creative motor drives a shaft into its west side; shafts on its east (two), north and south sides. All six turn at **32 RPM with the same sign** on the server and on the client; the dynamo's east and west halves are drawn at exactly the west and east shafts' angle, its north and south halves at the north shaft's. Pictures `r1119_shaft_line_a/_b.png` (3 ticks apart), `r1119_shaft_south_a/_b.png`.
* **Cog Dynamo:** a Create cogwheel turned from above turns it at the same speed the other way (cogwheel -32, dynamo +32), as meshed Create cogs do. Pictures `r1119_cog_a/_b.png`, `r1119_cog_top_a/_b.png`.
* **Readouts:** with a pole on each dynamo and a Light Gun Turret in reach, the Shaft Dynamo carries 8 stress per RPM, 256 su at 32 RPM, and Create's network sees 256 su of 524,288. Empty-hand right-click: "Shaft Dynamo: working at 32 RPM." / "This dynamo carries 256 su (8 su per RPM)." / "Its power network asks 256 su in all, shared by 1 turning dynamo(s), for 1 turret(s) and 0 Shock Box(es)." / "Its Create network: 256 su used of 524,288 su." The Cog Dynamo's own network: working, 0 su. The pole status: "Network running: 1 dynamo(s) at 32 RPM, 256 su (8 su per RPM), 1 turret(s), 0 Shock Box(es)." Goggles add "Power network: / Working / This dynamo: 256 su / Whole network: 256 su / 1 dynamo(s) turning, 1 turret(s), 0 Shock Box(es)". Pictures `r1119_dynamo_readout.png`, `r1119_goggles.png`.
* **Overload:** the motor's capacity set to 1 per RPM: "Shaft Dynamo: its Create network is overstressed, so the whole power network is down.", "This dynamo carries 512 su (16 su per RPM).", "Its Create network: 512 su used of 32 su."; the turret lost power; with the capacity back the network came back. Picture `r1119_dynamo_overstressed.png`.
* **Pole reach:** holding a turret shows both poles' outlines; empty hands show none; a sneak right-click with empty hands pins one pole ("This pole now shows its reach all the time. ...", saved on the pole, seen by the client) and a second one unpins it; a pole in hand shows the outline where it would stand. Pictures `r1119_reach_holding.png`, `r1119_reach_hidden.png`, `r1119_reach_pinned.png`, `r1119_reach_preview.png`.
* **Turret screen (Survival):** opens with an empty hand; 3 cans picked up and put in a slot by hand, 2 more shift-clicked in, shotgun shells refused; the screen shows "Online: ready to fire" and 320 shots; one shift-click takes all 5 cans back out; the screen then shows "Online, but out of ammo" with ghost cans; they go back in. Pictures `r1119_turret_screen.png`, `r1119_turret_screen_empty.png`.
* **Lamps and the crosshair line:** a loaded powered turret green (`ready`), an empty powered one orange (`empty`), a loaded one out of reach red (`off`), by day and at night; looking at a turret: "Light Gun Turret: ready, 320 shots loaded, range 18 blocks."; looking at the far turret's upper block: "Shotgun Turret: no power, 24 shots loaded. Place it within 8 blocks of a powered Power Pole." Pictures `r1119_lamps.png`, `r1119_lamps_night.png`, `r1119_turret_hud.png`, `r1119_turret_hud_off.png`.
* **Flame jet:** a Flame Turret (Mark I) with fuel, a husk 3 blocks in front and another behind a stone wall 5 blocks out. The jet lit (the client's flag), 48 flame particles drawn by the client after half a second; the husk in front died; the one behind the wall was untouched and not burning; the jet went out once nothing was left in sight. Sounds started: `turret.flame_start`, `turret.flame`, `turret.flame_end`; in the recording, the start clip at 68.34 s, the loop from 69.28 s (0.94 s later) and again 1.90 s later (the loop's length), the end clip at 73.86 s. Picture `r1119_flame_jet.png`.
* **Casings and gun sounds:** the machine gun's shots threw casings (3 in the air at the check) showing `eternal_defense:item/cartridge_case`; a Twin Auto-Shotgun Turret (Mark III) fired at a 200-health husk; every knockback on the husk was **0.1** (4 shots), a quarter of a punch. Sounds started: `turret.mg_shot`, `turret.shotgun_shot`. In the recording the two guns' shots overlap and could not be told apart (best matches 0.26 to 0.41 in run 2), so only the game starting them is shown, not how they sound. Pictures `r1119_casings_mg.png` (a casing on the ground), `r1119_casings_shotgun.png`.
* **Shock Box:** a box placed by hand, wired from the pole and to the first of 20 joined Barbed Wire blocks: exactly **15** went live, the first 15 along the line. At night, two pictures 3 ticks apart show different arcs: `r1119_live_wire_a.png`, `r1119_live_wire_b.png`.
* **Badass:** five husks made Badass I to V: maximum health **4, 7, 10, 13 and 16 times** their own base (240 to 960 on a base of 60).
* **Overview:** `r1119_overview.png`.

### Diagonal Walls (`QaWallPreview`, 6 of 6)

* Diagonal Walls registered `diagonalwalls:eternal_defense/sandbag` and `diagonalwalls:eternal_defense/barricade` (`ForgeDiagonalWallBlock`). With the tag, a right-click with a Sandbag, a Barricade and Barbed Wire placed the mod's own block each time, and each had its model on the client. The copy of the sandbag, placed directly, has **no model** (the magenta cube the owner saw): `r1119_walls_diagonal_copy.png`.
* Before the ghost fix, the wall and wire models gave **0** faces on the translucent layer (28, 22 and 44 in all); the ghosts now draw them: `r1119_walls_preview_sandbag.png`, `r1119_walls_preview_barricade.png`, `r1119_walls_preview_barbed_wire.png`; placed: `r1119_walls_placed_sandbag.png`, `r1119_walls_placed_barricade.png`. Checks: `checks-diagonal-walls.txt`.

## Jars

* All four jars (both builds of the main mod, the gun guide, the displays) hold no test, QA or note files. The new sounds, the animated textures with their `.mcmeta`, `TurretEffectsClient` and the Diagonal Walls tag are in both main jars.
* No tool, assistant or session names appear in text, data or class files. The one match is TaCZ's "PEQ6 ILLM" laser in the catalogue, as before.
* The Create classes and the casing particle in the release jar carry Minecraft's production names for their overrides (checked with `javap`).

## Network

* Protocol **43**, unchanged: no packet was added or changed. The turret screen is opened with Forge's menu opening (a new menu type); the turret's state (powered, shots loaded, burning) travels in its block entity update.
* New menu type and sound events: 1.1.18 and 1.1.19 do not mix (Forge refuses).

## Not tested

* A real raid against the turrets: the new flame and machine gun numbers, the shotgun's knockback on raiders, Badass health in a raid (only husks made Badass by hand were measured).
* How the owner's machine gun and shotgun sounds sound in game (the recording could not separate overlapping shots); the flame sounds were found in the recording but nobody listened. The no-voice build has the same turret sounds (they are not spoken lines).
* The Mark II and III flame turrets and the twin nozzles' flames; the Mark II shotgun; casings from a twin machine gun.
* Diagonal Walls in the owner's full pack (it ran here with only Create, Puzzles Lib and our mods), and sandbags or barricades already placed as Diagonal Walls' copies (they must be broken and placed again).
* Diagonal Fences and Diagonal Windows (no fence or pane block of ours qualifies; Barbed Wire has an extra property).
* Pole outlines from many poles at once, a pinned outline after reloading the world, the turret screen with a second player, a dedicated server.
* Other Create power sources, very high or very low RPM, Create's goggles on the Cog Dynamo (the Shaft Dynamo's were checked).
* Sounds of the live wire (it has none of its own), shaders.
