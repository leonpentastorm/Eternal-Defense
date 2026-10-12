CREATE ARSENAL - SUPPLY CATAPULT  (supply boxes only, no shells)
================================================================
INSTALL
  minecraft-models/block/*.json -> assets/arsenal_beacon/models/block/
  minecraft-models/item/supply_catapult.json -> assets/arsenal_beacon/models/item/
  textures/block/*.png          -> assets/arsenal_beacon/textures/block/
  All static models, solid layer. Same footprint and same part split as the support cannon, so the
  cannon's block / block-entity / renderer code can be reused with these models and pivots.

WHAT IT IS  (front = north, 3 x 3 cells, about 1.7 blocks tall)
  A field-built onager. Rough timber deck on an octagonal base with a banded turntable, rope-lashed
  corner stakes, sandbags along the front, spare supply boxes and a rope coil in the back corners.
  On top: two timber skids stencilled SUPPLY, a torsion skein of twisted rope with olive iron bands
  and ratchet plates, a padded stop beam with a hazard-striped crossbar, and a windlass with spoked
  hubs at the back. The throwing arm has iron bands and a plank cup with an iron rim; a trigger lever
  with a red tag sits by the windlass.

PARTS AND PIVOTS  (model px, modelled in place, rest pose = cocked, facing north)
  supply_catapult            STATIC block model: deck, turntable, stakes, sandbags, spare boxes.
                             x/z -16..32, y 0..12. This is the block's model (anchor = centre cell).
  supply_catapult_frame      YAW about the vertical axis through (8, -, 8). Full 360.
                             Swept radius 22.3 px - checked against everything on the deck: nothing
                             touches at any angle.
  supply_catapult_arm        PITCH about the X axis through (8, 11, 6), then the frame's yaw.
                             0 = cocked (lying back on the windlass, cup up).
                             Negative X rotation throws it forward; the cup meets the padded stop at -88.
  supply_catapult_payload    the supply box riding in the cup. Same transform as the arm.
                             Draw it only while loaded; hide it the tick you release the real parcel.
  supply_catapult_assembled  base + frame + arm + payload, ITEM / GUI ONLY (models/item parents it).

THROW  (what the art is built for)
  1. Arm 0 -> -88 deg over ~4 ticks (fast, ease-in), hold, then ease back to 0 over ~40 ticks
     (the windlass winding it back down).
  2. Release at -45 deg: hide supply_catapult_payload and spawn the supply-parcel entity at the
     cup's position at that moment, model px (8, 26.9, 15.6) in the frame's (yawed) space, with its
     velocity along (0, +0.707, -0.707) yawed - 45 deg up, out over the front. That's the cup's
     direction of travel at -45, so the box visibly leaves the cup. The parcel then deploys the
     chute you already have (support_parcel + support_parcel_chute).
  3. Aim: yaw the frame towards the flare, exactly like the support cannon's turret.
  The payload model is a smaller crate than support_parcel (the cup is 8 px wide); swap the
  spawned entity to the real parcel at release and nobody sees the size jump mid-flight.

EDITING
  blockbench/*.bbmodel (texture embedded), blender/*.obj (X east, Y south, Z up, 1 unit = 1 block).
