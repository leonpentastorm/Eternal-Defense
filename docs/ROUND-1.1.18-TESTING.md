# 1.1.18: what was tested

The owner sent two art packs (the "Electric and Defense Kit" and the "Defense Depot") and asked for a power system that works with Create, turrets, defenses and a Defense Depot, on a new feature branch. The work is on `feature/power-defense`, made from `dev` at 1.1.17. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-17). Evidence: `docs/validation/release-1.1.18/`.

## What was asked, and how it was read

* **Two dynamos that run on Create stress.** One takes a Create shaft, one a Create cogwheel; both spin like Create's own parts. Built as Create kinetic blocks: the Shaft Dynamo is a gearbox standing on its axis (shafts on its four sides), the Cog Dynamo a small cogwheel like the mechanical arm's.
* **Power poles on the dynamos, with a reach.** A pole on a turning dynamo starts a network and powers turrets within 8 blocks. Poles further out are joined with Electric Wire (spans up to 16 blocks; the wire is made from copper).
* **Three turret lines, three Marks each, the owner's models.** Machine gun, shotgun, flamethrower. They fire on their own when a powered pole reaches them, burning ammunition from their own inventory. The ammo has Create factory recipes, and the inventory takes items from hoppers, Create chutes and funnels (a belt feeds through a funnel).
* **Turret damage meets raid adaptation.** Bullets and pellets are the projectile trap, the flame is fire, the wire's cut is spikes, live wire is lightning.
* **Sandbags, barricade, barbed wire.** Sandbags resist shots, the barricade resists blows, barbed wire has little health and hurts mobs attacking or crossing it.
* **"Battery".** Read as the kit's Shock Box: wired from a pole and to Barbed Wire, it makes the wire live (more damage).
* **Shop and workshop.** The Defense Depot sells everything except itself, the pole and the two dynamos, which are in Workshop Fabrication under a new "Defense & defense logistics" group. The depot takes Universal Ammo Coins.
* **"Only in beacon range AND in FOB range".** Read as: inside the beacon zone, or inside a FOB's zone.
* **"Turrets and the electricity box need stress units; overloaded, the whole network goes down."** Each turret and Shock Box asks stress per RPM; the network's total is shared by its turning dynamos as their stress impact on Create. If any of them is overstressed, the whole network stops.
* **Balance.** First proposals in `Economy`, `Turrets.stats`, `PowerKit` and `DefenseWalls`; the owner will tune after playing (GDD 5.14 lists them all).

## Unit tests

`:eternal-defense:test` **524 pass, 0 fail**. The new `Release1118Test` has 10 tests:

* **Pole reach and wire spans:** 8 blocks around, 4 down, 6 up; spans of 16 blocks, never to the same block.
* **Networks:** the load is shared by the turning dynamos; one overstressed dynamo stops the network; no turning dynamo, no power.
* **Wire pairs:** pole to pole or Shock Box, Shock Box to Barbed Wire; nothing else.
* **Walls:** shots do a quarter to sandbags, blows 40% to the barricade.
* **Turret lines:** range, damage a second and stress grow with the Mark; the machine gun reaches furthest.
* **Aim:** the yaw and pitch rules match the models (north is the model's front; positive pitch lifts the gun; the mount allows -15 to +20 degrees).
* **The depot:** sells all nine turrets, the three ammo types, the walls, the Shock Box and wire, but not the depot, pole or dynamos, which are at Workshop Fabrication with a price.
* **The Field Guide quotes the real numbers:** ranges, prices, rounds per item, reach, wire span, minimum RPM, charge time, stress, depot and fabrication prices, damage a second, links per pole and box, wire damage.
* **Wiring:** walls resist their kind of blow, barbed wire cuts a raider hacking at it, raiders' arrows and bullets count as ranged, the grid ticks, the dynamos register with Create and push their stress, Buy uses the vanilla menu button.
* **Data:** the five damage types exist and are in the trap tags (the flame also in `minecraft:is_fire`), the raid-target tag lists the defenses, the three ammo recipes are Create recipes.

Old tests updated for intended changes: `Release1111Test` (the arrow hit now passes `ranged`), `Round0035Test` and `Round0038Test` (the Workshop has a fourth group), `Round0041Test` (the turret's item-port part is not an installation). `LangKeysTest` now also reads the kit's `kit(...)` registrations, the turret loop and the Create dynamos, and every one has a guide card.

`:eternal-defense-gun-guide:test` 8 pass. `:eternal-defense-displays:test` 7 pass.

## Real client

`tools/qa/QaRelease1118.java.txt` ran with `QA_CREATE=1` (Create 6.0.8, Flywheel, Ponder) in a new flat world at 1280 x 720. A Defense Beacon was planted (zone radius 32); blocks were placed with real right-clicks holding their items, unless noted.

### Run history

* **Run 1: 38 of 48.** All three turret lines never fired: the line of sight started inside the turret's own collision box, so the turret saw itself as a wall. Fixed in the mod: the sight line ignores the turret's own blocks (`Turrets.Sight`), for targeting and for pellet rays. The overload checks also failed because they waited a fixed 2 seconds; Create had flagged the overload but the grid had not run since. The harness now waits for the condition.
* **Run 2: 47 of 48.** Turrets fire and kill. The belt-fed turret had shot a slime (the flat world's) and opened one of its three cans, so "3 cans in the slots" failed though all 3 arrived. The harness now counts shots loaded and clears slimes at the start.
* **Run 3: 48 of 48.** Results and pictures below.

### The checks (run 3)

* **Dynamos:** both placed by right-click; a creative motor turns the Shaft Dynamo through its west side (32 RPM); a Create cogwheel turned from above turns the Cog Dynamo (32 RPM). With no pole on them they add no stress. Pictures `r1118_shaft_port_a.png` (Create's shaft half in the dynamo's port; it had turned in a second frame 3 ticks later) and `r1118_cog_a.png` (the cog band at the cogwheel's height; it had turned in the second frame).
* **Poles and wire:** a pole on each dynamo (2 blocks tall). The first pole alone runs a network (1 dynamo). In Survival, one Electric Wire joins the two poles (one span, one wire used); the network now has both dynamos turning. Picture `r1118_wire_span.png`.
* **Shock Box and Barbed Wire:** box wired from the pole and to one Barbed Wire block; that block and the two joined to it go live, the box charges, the posts join like panes, the network counts the box. A husk in the live wire is shocked (`electric_shock`) and slowed. Picture `r1118_live_wire_night.png`.
* **Machine gun turret:** placed (2 tall), loaded by right-click with 2 cans, powered by the pole's reach; the network asks 16 stress per RPM (turret 8 + box 8), 256 SU on each dynamo at 32 RPM. Two husks 12 blocks out: both killed by the turret on its own, 42 rounds used. Picture `r1118_mg_firing.png`.
* **Auto-Shotgun (Mk II) and Twin Flame (Mk III):** the twin fills 2 x 2 x 2; ammo went in through their item handlers (the flame turret's through its far upper corner); a turret refuses the wrong ammo; both powered. A husk in front of each: both dead; the shotgun used 7 shots, the flamethrower 21 bursts, and set its target on fire. Picture `r1118_flame_firing.png`.
* **Create loading:** a Create chute on the machine gun turret fed 3 cans into it. A belt sunk in the ground with an andesite funnel against another turret's face delivered 3 cans; nothing fell off. Picture `r1118_belt_funnel.png`.
* **Overload:** the creative motors' stress capacity was set to 1 per RPM (Create's server config). Create flagged the Shaft Dynamo's network overstressed; the whole power network went down, the Cog Dynamo's side too; every turret was unpowered; the far pole said "Network down: the Create network is overstressed. Your defenses ask 60 stress per RPM. Add more Create power." With the capacity back, the network and the turrets came back by themselves.
* **Walls:** three Sandbags and three Barricades placed by hand join like walls; shots do a quarter to sandbags, blows 40% to the barricade. Picture `r1118_walls.png`.
* **Defense Depot:** placed in Survival (2 wide, 2 tall), opened by right-click; the screen shows the 64 coins; four wheel notches down, the MG ammo row picked, Buy: 4 cans for 8 coins (56 left). Pictures `r1118_depot.png`, `r1118_depot_screen.png`.
* **Workshop Fabrication:** a Power Pole made for 6 iron and 8 copper ("Place it inside your beacon zone or a FOB's zone."); the tab lists the four kit installations in the Defense group. Picture `r1118_workshop_defense.png`.
* **Zone rule:** 400 blocks out, in Survival, a turret placement is refused and the item kept.
* **Field Guide:** the Turrets & Power page. Picture `r1118_guide.png`.
* **All turrets:** the nine turrets side by side, with the dynamos, pole, box and walls (`r1118_lineup.png`, placed directly; unpowered, so the guns droop). Overview: `r1118_power_plant.png`.

## Jars

* All four jars (both builds of the main mod, the gun guide, the displays) hold no test, QA or note files.
* No tool, assistant or session names appear in text, data or class files. The one match is TaCZ's "PEQ6 ILLM" laser in the catalogue, as before.
* The Create classes in the release jar carry Minecraft's production names for their overrides (checked with `javap`).

## Network

* Protocol **43**, unchanged: no packet was added or changed. The depot's Buy is the vanilla menu button; turret aim, shots and wire spans travel in block entity updates and block events.
* New blocks, items, block entities and a menu type: 1.1.17 and 1.1.18 do not mix (Forge refuses).

## Not tested

* A real raid against turrets and walls: raiders targeting the new defenses, hacking at barbed wire, adapting to turret damage over waves, the sandbag and barricade resistances in a raid (the rules are unit-tested and wired, not played).
* FOB zones: placement there was not tried (only the beacon zone and outside both).
* Mechanical arms, hoppers and smart funnels feeding turrets (the chute and a belt with an andesite funnel were).
* Breaking turrets, poles and boxes (ammo and wire drops), saving and reloading a world with a network, chunk unloading across a long wire run.
* Other Create power sources (water wheels, steam engines), Create's goggles on the dynamos, very high or very low RPM.
* Sounds (no audio device in the test machine). In action only the machine gun Mk I, the shotgun Mk II and the flamethrower Mk III were tried; the other six were placed and drawn. The Shock Box running on its charge after the power went down.
* The owner's full pack, a dedicated server, a second player, shaders.
