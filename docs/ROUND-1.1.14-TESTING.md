# 1.1.14: what was tested

The owner sent the art for five armor sets and a radio, and: "implement them one for each age and present to me what Buff what armor should give, then implement after i choose, this will make the armor workbench always available. The tier 1 frontier age armor is equivalent to iron, age 3 is at diamond level, and upper level just have more and better buffs". The owner then chose a bonus per Age and described the radio. On `dev`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-17). Evidence: `docs/validation/release-1.1.14/`.

## The supplied art

Read before it went in. Every PNG holds image chunks only (IHDR, IDAT, IEND). The worn textures are 256 x 128, the item textures 256 x 256 and the radio texture 128 x 128. The item models carry the credit "Eternal Defense - armor" or "Eternal Defense - handheld radio", like the mod's other models. The supplied Java went in with one-line neutral comments. The geometry file's header named the generator; it is now a plain one-line comment, and `Release1114Test` checks for that. The Blockbench, Blender, GeckoLib and preview files of the zip were not added.

## Unit tests

`:eternal-defense:test` **507 pass, 0 fail**. The new `Release1114Test` has 6 tests:

* **Stats:** Frontier has iron's armor points and durability. World Wars has 16 armor points. Modern, Advanced and Exotic have diamond's 3/8/6/3, toughness 2 and durability.
* **Protection scores** (the 1.1.12 formula): Frontier 10 like an iron set; Modern 17.5 like a diamond set; Advanced and Exotic the same as Modern.
* **Bonus numbers and the pure rules:** the 25% cut applies to bullets and explosions on Advanced only. Ardent Ward fires below 30% health, never on a lethal hit and never during its cooldown.
* **Wiring:** the attributes, the effects, the immunities and the events.
* **Text:** the tooltips and guide quote the real numbers, the guide has the new cards, and the "nothing to make without an armor mod" line is gone.
* **Armor table:** the sets are listed and priced with no median. A hand-set Age still wins over the set's own Age.
* **Art:** every model points at its set's texture, the radio has its art, there is no trim tag, and there are no tool names.
* **Radio:** the marker rule (aimed at the ground; at a wall, the ground in front; no ground), the shared call, a flare used only when the call goes out, the menu, the shop product, the screen registration and the sound.

`:eternal-defense-gun-guide:test` 8 pass. `:eternal-defense-displays:test` 7 pass.

On the way, `FobTest.noTwoEventHandlersShareTheBusGeneratedName` caught that the set events class (first named `Events`) would share Forge's generated class name with `RaidAdaptation.Events.hurt`. It is now `ArmorSetBonus.SetEvents`.

## Real client

`tools/qa/QaRelease1114.java.txt` ran with `QA_CREATE=1` and **no armor mod**, in a new flat world (Normal difficulty) at 1280 x 720.

**Run 1: 61 of 65.**

* Two failures were the harness's own mistake. A piece's toughness counts per piece as in vanilla, so the set totals are 4 (Trench) and 8 (Operator, as a diamond set), not 1 and 2. The checks were corrected.
* Two were real: the radio screen cut "Flares, up to 16 of each. Pick ..." and the key hint, and the key hint overlapped the "Inventory" label. The picture also showed the call buttons cut ("Sup...", "Retu...").
* Fixed: the screen is wider (232), the lines are shorter ("Flares (up to 16 each)", "Plant the chosen flare"; the reach moved to the hint's tooltip), and the inventory sits lower.

**Run 2: 65 of 65.**

### The sets

* The 20 worn models are baked, and five armor stands show the five sets. Pictures: by day from the front and the back, and at night, where the Vanguard Exo visor, core and vents and the Resonance crystals and runes stay lit.
* The player wears the Ranger and Vanguard Exo sets (pictures). All 21 item models load; the picture shows the inventory full of pieces.
* Tooltip with three Ranger pieces and a Trench helmet: "Frontier Age armor. Wear all four pieces of the set for its bonus.", "Set bonus (3 of 4 worn): Quick Draw", "Guns reload 15% faster."

### The bonuses

Values were read from the server player.

* A mixed set gives nothing.
* **Ranger:** reload speed 1.0 -> 1.15. Armor 15, toughness 0.
* **Trench:** gun damage 1.0 -> 1.1. Armor 16, toughness 4. Poison is refused, and works again once the set is off.
* **Operator:** endless Night Vision with no particles, and gun damage x1.1. Armor 20, toughness 8. Night Vision leaves with the set.
* **Vanguard Exo:** knockback resistance 1. Slowness is refused. After every handler, an arrow hit of 8 comes to 6, an explosion of 8 to 6, and a husk's melee hit stays 8.
* **Resonance:** maximum health 24, knockback resistance 1, endless Regeneration I.
* **Ardent Ward:** two harmless husks stood 2.5 blocks away. A magic hit of 18 left 6 of 24 health.
  * The pulse gave 8 absorption.
  * It threw both husks to 9.7 blocks.
  * The action bar read "Ardent Ward! ... Ready again in 60 seconds.", and the amethyst sound played.
  * A second low hit within the minute did nothing (picture of the pulse).
* Taking the set off ends it all.
* A Night Vision potion stays a potion (finite) while the Operator set is worn and after it comes off.

### The armor table, with no armor mod installed

* It lists the 20 pieces, each set at its Age (1 to 5).
* Workshop Fabrication sells the table (in 1.1.13 it refused to sell it without an armor mod).
* Chest pieces (the Age's typical chest share):

  | Set | Price |
  |---|---|
  | Ranger | 8 iron sheets, 5 leather, 3 wool |
  | Trench | 8 iron sheets, 5 andesite alloy, 5 leather, 3 wool |
  | Operator | 7 plating, 2 precision mechanisms, 5 andesite, 5 leather, 3 wool |
  | Vanguard Exo | 8 plating, 2 mechanisms, 1 netherite scrap, 5 andesite, 5 leather, 3 wool |
  | Resonance | 9 plating, 2 mechanisms, 1 scrap, 2 resonance coils, 5 andesite, 5 leather, 3 wool |

* **A real craft in Survival** at a placed Frontier table: the Ranger Duster is made with exactly its price, nothing left over. An Exotic piece at that table says "Requires Age 5: Exotic."
* A search for "Ranger" shows the four pieces (picture).

### The radio

* Bought at the Support Platform for 8 Ardent; the shop lists it (picture).
* Sneak and use opens the menu with a burst of radio static. The menu shows the station's coins (slot 1: 2, slot 3: 1).
* The four flare stacks shift-click into their own slots. The radio cannot be moved out of the hand, by shift-click or by the hotbar swap key.
* Picking Fire and kit 3 makes slot 3 the station's active slot.
* After closing, the radio keeps the flares (`{Call:"fire",Flares:{fire:3,loadout:2,return:1,supply:2}}`), they left the pack, and its tooltip lists them.
* **Fire Support:** using the radio while looking at the ground 12 blocks away plants a landed Fire Support Flare there (within 1.5 blocks). One flare is used, the radio crackles and the fire support line plays. The cannon turns and fires for it (picture with the red box).
* **A stone wall:** the flare lands on the ground in front of it.
* **Looking at the sky:** "Look at the ground within 48 blocks to plant the flare." and nothing used.
* **Loadout:** set to Loadout, the radio's call delivers a capsule with kit 3 (slot 3's coin used, slot 1's kept). One Loadout Flare is used.
* **Empty:** with no Fire Support Flare left: "No Fire Support Flare in the radio. Sneak and use it to load some."

## Jars

* All four jars (both builds of the main mod, the gun guide, the displays) hold no test, QA or note files.
* Every PNG holds image chunks only, the new textures included.
* No tool, assistant or session names appear in text, data or class files. The one match is TaCZ's "PEQ6 ILLM" laser in the catalogue, as before.
* The new classes and art are in both builds of the main mod.

## Network

* Protocol **43**, unchanged: no packet changed.
* New items (20 armor pieces, the radio) and a new menu type mean a 1.1.13 client and a 1.1.14 server (or the other way) do not mix. Forge refuses the registries. The test-build INSTALL says so.

## Not tested

* The owner's full pack, and with the owner's armor mods installed (other mods' armor pricing is unchanged in code: the new pieces are left out of the medians).
* A dedicated server, and a second player.
* TaCZ guns firing with Quick Draw or Stormtrooper worn: the attribute values were checked, not a reload or a shot.
* Real raid mobs against Ardent Ward and Exo Bulwark (husks and damage sources made by the harness were used).
* The radio's Supply and Return calls, and the radio in another dimension.
* Shaders with the glowing sets.
* Enchanting and anvil repair of the pieces.
* The Field Guide pages in the client: their text is covered by the unit tests.
