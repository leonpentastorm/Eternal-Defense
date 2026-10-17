# 1.1.23: what was tested

The owner's request after 1.1.22, with the art pack "Tesla, Punji, Spotlight, Totem, Heavy Cannon", on `feature/power-defense`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-17). Evidence: `docs/validation/release-1.1.23/` (check lists and pictures).

## What was asked, and how it was read

* **"A heavy cannon tower that fire slowly, have the furthest range in the turrets, deal big heavy damage with explosion, act like both a cannon and a mortal. Expensive, no upgrade, price is the current price of flame tower."** One Mark, 2 x 2 x 2 (the owner's model). Range 40, the longest of any tower. A shell every 4 seconds bursts for 30 at the middle and 12 at the edge 4 blocks out. It fires flat when the way to the mob is clear and lobs high, like a mortar, when it is not. Price: **512**, what the Twin Flame Turret (Mk III) cost, the dearest tower. New ammo item: Heavy Cannon Shells, 4 shots a crate.
* **"Reduce flame tower price to the current price of other turrents."** Flame turrets 96 / 192 / 384 (were 128 / 256 / 512).
* **"A punji trap that fip up when enemies step on it, doesn't need energy, deal damage one time and reset when wave end."** 20 damage to the first hostile mob that steps on it; it stays sprung until the wave it sprang in is over (the beacon raid's wave, or a village raid's), then sets itself again. Right-click sets it by hand (the model's re-arm lever). Outside a raid it stays sprung until a raid wave starts or you set it.
* **"A tesla coil tower that chain electricity onto multiple enemies."** Three Marks, as the art has. No ammo. A bolt hits the nearest foe in sight and jumps to the next one within reach, 3, 5 and 7 mobs, 15 percent weaker at each jump.
* **"A curse tower that spam down AOE zone that gives slow and poisons."** The Curse Gas Totem. No ammo. Every 2 seconds it drops a cloud 6 blocks across on the biggest crowd it sees. Each second a foe in a cloud gets Slowness II, Poison I and 2 gas damage. Undead are not poisoned in vanilla, so the gas is what hurts them.
* **"Give sandbag, barricate, and barbwire create factory crafting recipe too."** Create compacting (press and basin), like the turret ammo.
* **"All of these use the same adaptation rule our mod have."** New damage types in the trap tags: tesla in lightning, shells in explosion, gas in magic, stakes in spikes. None of them has an owner, so raiders learn them wave by wave like any other trap. The totem's poison is a sourceless effect, so its ticks count as magic too. Its slowness also gets shorter as the raid learns magic.
* **Not asked:** the Industrial Spotlight from the same art pack. It is in as a plain light (level 15 while a powered pole reaches it).

## Unit tests

Eternal Defense **562 pass** (551 before), Gun Guide 8, displays 7.

* New `Release1123Test` (11 tests):
  * the lines, their Marks and ids;
  * the cannon reaches furthest, fires slowest and hits hardest;
  * flame turrets cost what the others do, and the cannon 512;
  * a tesla zap's 15 percent per jump;
  * a shell lands where it was aimed (flat and lobbed, four targets, three speeds, with a lead for a walking mob);
  * the muzzle where the art puts it (at rest and pointing straight up);
  * a blast's falloff;
  * the curse slowness under adaptation and the cloud's shape;
  * the stakes' animation;
  * every new damage type in its trap tag, with no owner;
  * the four Create recipes;
  * every number the guide quotes;
  * the art, blockstates and sounds wired up.
* Changed:
  * `LangKeysTest`: the new cards, and lines with one Mark.
  * `Release1118Test`: lines with one Mark; the ammo and price lines in the guide.
  * `Release1119Test`: the lamp call and the shot event.
  * `Release1120Test`: compares Depot prices by id, since the flame turrets changed.

## Real client

`tools/qa/QaRelease1123.java.txt` (Create in the run). A flat world at noon with no natural mobs, a Mk IV beacon zone, a Shaft Dynamo on a creative motor, and one Power Pole. Each tower stands alone in the pole's reach. The targets are vindicators with 200 health: no armor, so damage reads exactly. The adaptation checks run in a raid held open by the harness: it never spawns, and the test raiders are added by hand.

### Run history

* **Run 1, 30 of 38.**
  * The cannon's direct hit did 28.65, not 30. It burst on the 0.3-padded box it uses to catch a mob, 0.3 from the mob. **Fixed:** a direct hit now bursts on the mob itself.
  * The barrel pitch was read in the same tick the lob flag arrived. Fixed in the harness.
  * All five punji checks failed. A vindicator without AI never moves, so the game never checks the blocks it stands in. Real raiders walk. The harness now uses a vindicator with its AI, held still.
  * The tesla picture showed no bolt. A tower's renderer only ran while the tower's own blocks were on screen, so bolts reaching 14 blocks out vanished when the coil was out of view. **Fixed:** tesla coils and the cannon (barrel up to 3.4 blocks high) now draw off screen too, culled by their reach. The curse cloud was faint, so it now has violet potion swirls as well.
* **Run 2, 38 of 38.** The bolt picture still missed the bolt: software rendering runs at a few frames a second, and the picture came after the bolt's 8 ticks.
* **Run 3, 42 of 42**, with sound to a file and four sound checks added. The bolt picture is now taken in the first frame that draws a fresh zap. The pictures and `checks-release-1.1.23.txt` are from this run.

### What run 3 shows

* **Tesla Coil Turret Mk III**
  * Placed from the item: 2 x 2 x 2, powered, with the live arcs on.
  * Green lamp; the line reads "ready, range 14 blocks, needs no ammo". The grid counts it as one turret.
  * One zap on a line of 9 mobs, 4 blocks apart, hits 7 of them: 8.00, 6.80, 5.78, 4.91, 4.18, 3.55, 3.02.
  * The client gets all 7 links and draws the bolt (`r1123_tesla_bolts`). The zap is heard.
* **Heavy Cannon**
  * Placed from the item. With no shells the lamp is orange.
  * One crate through the top cell (a funnel's way) loads 4 shots.
  * A flat shot at 22 blocks hits for 30, and the mob 2 blocks away for 22.3. One shell spent. The shot is heard.
  * Behind a 6-high stone wall, it lobs: the barrel lifts to 72.7 degrees (`r1123_cannon_lob`) and the shell hits for 30.
  * A mob 4 blocks away is never shelled.
* **Curse Gas Totem**
  * The cloud lands on the crowd of four, not the lone mob 9 blocks off.
  * Vindicators in it get Slowness II and Poison and 2 gas damage. The husk gets no poison, but it is slowed and gassed (1.92 through its armor). The lone mob is untouched.
  * The gas overlay shows on the totem, and the cloud is heard. After the mobs leave, the clouds clear and the overlay goes off.
* **Punji Spike Trap**
  * Placed from the item. 20 damage once; the stakes come up, and the spring is heard. Standing on the sprung trap does nothing more.
  * A right-click sets it again.
  * In a raid it stays sprung through wave 1 and sets itself when wave 2 begins.
* **Industrial Spotlight:** light 15 in the pole's reach, dark outside it (`r1123_spotlight_night`).
* **Adaptation, in a raid at wave 3**
  * A Mk I tesla on a raider: 6, and lightning is recorded as learned in wave 3. With lightning learned two waves back: 3.
  * The cannon with explosions learned two waves back: 15.
  * The totem against a raid immune to magic: the raider gets no slowness and no damage, while the non-raider beside it is slowed and gassed.
  * Punji stakes with spikes learned two waves back: 10.
* **Prices and recipes:** flame turrets 96, 192 and 384, the tesla Mk III 384, the cannon 512. The four new recipes load as `create:compacting`.
* **Pictures:** the Depot list with the new products (25 rows), a line-up of all seven new blocks in daylight, and every tower in action.

### Sound

* The game started all four new sounds: the tesla zap, the cannon shot, the curse cloud and the punji spring.
* In the recording, `tools/audio/find_sfx.py` finds the cannon shot (r 0.70 and 0.50) and the punji spring (0.50, 0.45). The tesla zap and the curse cloud are mostly noise and do not match above the 0.45 threshold.
* Nobody has listened to them.

## Network

Protocol stays **43**: none of the mod's own packets changed. A tesla zap goes out as vanilla block events, one per link, and a shell's arc goes in Forge's spawn data, so the shell needs no position updates. New blocks, items and an entity mean 1.1.22 and 1.1.23 do not mix (Forge refuses).

## Not tested

* A real raid against the new towers, and many towers at once: the cannon plans its arcs and the totem gasses every second.
* FOB zones, a village raid resetting a trap, a server, a second player.
* Save and reload with a sprung trap or live clouds (clouds are not saved).
* Create pressing the four recipes in a running factory (only their loading was checked).
* The towers in caves, under water, and in rain or at night with shader packs.
* Breaking the towers by hand and getting the items back.
* How the new sounds sound, and whether the numbers feel right.
