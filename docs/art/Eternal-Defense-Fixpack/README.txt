ETERNAL DEFENSE - FIX PACK: FLOATING PARTS + SEE-THROUGH FACES
==============================================================
Drop-in replacements. Same model IDs, same footprints, same item models.
Overwrite each model's JSON AND its PNG together: every texture here was repacked, so an old PNG no
longer matches the new JSON.
This pack supersedes the "Mess Hall Mk II + Mk III sign fix" zip (both boards changed again).
The Loadout Station is included because its atlas was repacked (replace all 7 loadout_station*
JSONs + loadout_station.png). The Loadout Capsule is unchanged.

INSTALL (overwrite)
  minecraft-models/block/*.json -> assets/eternal_defense/models/block/
  minecraft-models/item/*.json  -> assets/eternal_defense/models/item/
  textures/block/*.png          -> assets/eternal_defense/textures/block/
  textures/item/*.png           -> assets/eternal_defense/textures/item/

HOW EVERY MODEL WAS CHECKED
  1. Connectivity: every cube must share a face with the rest of the model, and the model must
     reach the ground (or the wall, for the wall racks/cases). Touching only along an edge
     doesn't count as attached.
  2. Legs and posts: no leg / post / strut may end in the air.
  3. See-through test: each model rendered alone with single-sided faces, like Minecraft does,
     from 16 angles, with the inside of every cube painted magenta. Any magenta = a hole you can
     see through in game. What's left after the fixes is single-pixel edge seams, not holes.

FIXES
  Brew pot (drink_pot)     Menu-board posts stopped at y 4.5 behind the kettle; they now stand on the
                           base plate. Burner sat 0.75 px above the plate; now on it. Paddle and mug
                           hooks now touch the kettle floor / board frame.
  Cook pot                 Same board posts + burner fix; ladle now stands on the kettle floor.
  Mess hall Mk III         Stone backsplash hung 10.5 px above the ground behind the counter; it now
                           runs down to the ground. Name board bolted onto the front of the beam.
  Drink station Mk III     Same backsplash fix (it hung 8.5 px up).
  Mess hall Mk IV          The side walls, back wall, steam kettle, dispensers and tray stack sat
  Drink station Mk IV      1 px above the base with a slit under them (on the Mk IV drink station
                           the whole enclosure + CO2 bottles + syrup boxes weren't attached at all).
                           Base now runs to the back wall and a stainless back bench fills the gap.
                           The tray rail hangs off the counter lip instead of floating in front of it.
  Mess hall Mk I           Name board only touched the rack beam along an edge; it now stands on it.
  Drink station Mk I       Same.
  All name boards          The MK tier plate had a 0.5 px gap beside its board; now bolted to its edge
                           (mess hall Mk I-III, drink station Mk I-III, command table).
  Command table            Board tucked under the canopy; map divider legs now reach the map.
  Cup dispenser            The three cup stacks hovered 1.75 px above the cabinet; now stand on it.
  Milk dispenser           Sight gauge sat 0.25 px off the tank; now on the tank skin.
  Satellite dish           The feed horn's two struts ended in mid-air; the horn is now carried by
                           two booms along the dish axis, so it stays attached at any tilt.
  Potion cannon            Sight sat 0.5 px off the cheek (turret + assembled models).
  Beacon Mk III            The four power cells sat 0.25 px off the core collar.
  Weapon bench             Kar98k bolt handle only touched the receiver along an edge.
  Ammo bench               Reloading-press lever floated beside the column; now on a pivot pin.
  Supply receiving gate    Tip chutes hovered over the bins; their lips now rest in the bin mouths
                           (both halves + the item model).
  Logistics hub            Pallet, ammo cans, sacks and med box sat 0.25 px above the rack decks;
                           extinguisher 0.25 px off its column; the four slings of the hoisted crate
                           weren't attached to the hook - they now hang from a spreader bar under it.
  MRE maker                Bay light hung 1 px below the hopper; now fixed to its underside.
  Exchange shop            SEE-THROUGH: the shelf fronts behind the glass were removed by the face
                           culling (the glass pane counted as a cover), so you could look into the
                           shelves. Fixed in the builder for every model: glass never hides a face.
  Loadout station doors    SEE-THROUGH: inside the pod, the undersides of the neck / head fills of the
                           half-shells were missing. Restored (station now 570 faces, all clearance
                           checks re-run and still pass - see the Loadout zip's CHECKS.txt).

KEPT ON PURPOSE
  Enchanting bench         The open book hovering over its glowing levitator (the vanilla enchanting
                           table's floating book) and the purple glyph / rifle sparks (magic particles).
                           Say the word and I'll seat the book on a stand and drop the sparks.
  Open frames              Mk I rough racks and the pegboard backs are open frames by design; every
                           piece of them is attached, they're just not solid walls.

EDITING
  blockbench/*.bbmodel (texture embedded), blender/*.obj (X east, Y south, Z up, 1 unit = 1 block).
  previews/: before/after of the brew pot and both Mk III backs, plus the other fixed models.
