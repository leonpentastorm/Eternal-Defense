ETERNAL DEFENSE - LOADOUT STATION + LOADOUT CAPSULE
==================================================
Original design (suit-bay mood, no copied shapes, logos or text). Stencils used: LOADOUT, BAY 01, CAUTION.
Style: same gunmetal / oxide-red / brass / hazard palette, rivets, chips and cyan-amber glow as the
support cannon and the rest of the Eternal Defense set.

INSTALL
  minecraft-models/block/*.json -> assets/eternal_defense/models/block/
  minecraft-models/item/*.json  -> assets/eternal_defense/models/item/
  textures/block/loadout_station.png (512x512) and loadout_capsule.png (256x256)
                                -> assets/eternal_defense/textures/block/
  ONE texture per set: every station part points at eternal_defense:block/loadout_station,
  every capsule part at eternal_defense:block/loadout_capsule.
  Texel density is the same everywhere: 2 texels per model pixel (32 per block, like the rest of
  the mod). Every cube edge is a multiple of 0.5 px, so every face maps to whole texels: nothing
  stretched, nothing resampled. All cubes inside -16..32; rotations single-axis, 22.5 / 45 deg.
  Solid render layer (no translucency). Glow cubes: "shade": false + forge_data/neoforge_data
  block_light 15 / sky_light 15 (fullbright in 1.20.1 Forge).

COORDINATES
  Model pixels of the ANCHOR block, 16 px = 1 block, machine facing NORTH (front = -Z).
  From the front, +X is the viewer's LEFT. For other facings rotate about the anchor centre
  (8, 8, 8) first, then apply the part transforms below.

==========================================================================================
PART 1 - LOADOUT STATION  (3 x 3 x 3, anchor = centre cell, footprint -16..32 on x, y, z)
==========================================================================================
MODELS
  loadout_station             static body: deck, side columns, cavity walls, mold back shell, floor
                              plate, roof + fascia, control column, coin bank, vents, ceiling rig.
                              Use as the anchor's blockstate model; the other 26 cells are fillers
                              (RenderShape.INVISIBLE).
  loadout_station_door_right  clamshell leaf on the viewer's RIGHT (-x side)   - BER
  loadout_station_door_left   clamshell leaf on the viewer's LEFT (+x side)     - BER
  loadout_station_ring        print ring                                        - BER
  loadout_station_arm_a / _b  articulated print arms                            - BER
  loadout_station_assembled   everything in the closed pose (item/loadout_station.json -> this)

THE MOLD
  Back shell: a 4 px deep human-shaped recess (head, neck, shoulders, arms, torso, legs, boots)
  cut into the shell at z 15.5..19.5, quilted black padding at the bottom with a teal seam on the
  outline. Each door carries the matching half-shell on its inner face (z -9..-5.5), so with the
  doors shut the three shells enclose the full silhouette around the player. Faceless: no visor,
  no face detail anywhere.
  Clear cavity (guaranteed empty, every part, every pose):
      x 1..15, y -13.5..21.5, z -2..12   ->  14 x 14 px footprint, 35 px clear height
  Floor plate top: y -13.5 (2.5 px above the bottom cells), boot marks + BAY 01 stencil.

  STANDING POINT (player feet, facing north / yaw 180):  (8, -13.5, 5)
      world = anchorPos + (0.5, -0.84375, 0.3125)
      The player hitbox (9.6 x 28.8 x 9.6 px) then sits 2.2 px inside the clear cavity on every side.
      Give the bottom-centre cell a 2.5 px floor collision box (Block.box(0,0,0,16,2.5,16)) and the
      anchor cell no collision, or the player will sink to y -16 or bump the anchor.

DOORS  (vertical hinge axes at the outer edges, swinging outward)
  door_right  hinge axis x = -5, z = -11   ->  Axis.YP.rotationDegrees(+a)
  door_left   hinge axis x = 21, z = -11   ->  Axis.YP.rotationDegrees(-a)
      a = 0 shut ... 100 open (checked clear up to 105).
      poseStack.translate(hx/16, 0, hz/16); poseStack.mulPose(rotation); poseStack.translate(-hx/16, 0, -hz/16)
  Swept numerically every 0.5 deg, 0..105 deg, both leaves moving together: no contact with the
  frame, columns, console, coin bank, vents, the other leaf or the player (hitbox and rendered
  model). The leaves only touch when shut (the left leaf carries a seam lip over the 1 px gap).
  Fully open the right leaf reaches z -23.7, about half a block in front of the machine:
  inflate the BER's render bounding box (e.g. new AABB(pos).inflate(2)).

PRINT RING  (horizontal frame round the player, 4 inward nozzles, rides the 4 corner rails)
  rest = head height (ring y 15.5..18).  poseStack.translate(0, dy/16, 0)
      dy = 0 (head height) ... -29 (bottom of the ring on the floor plate)
  Swept every 0.5 px: touches nothing but the rails it rides on (sleeves), never enters the clear
  cavity, never touches the player.

ARMS  (tilt about a sideways X axis through the hub)
  arm_a pivot (4.5, 28.25, 5)   arm_b pivot (11.5, 28.25, 5)
      poseStack.translate(px/16, py/16, pz/16); mulPose(Axis.XP.rotationDegrees(t)); translate back
      t = -45 ... +45 (+t swings the emitter toward the doors). Each arm: hub, link, elbow and a
      forearm bent 22.5 deg with a glowing emitter tip.
  Swept every 1 deg: clear of the ceiling beam, clevis plates, lights, ring and player; the lowest
  point stays at y 21.99 (above the 21.5 cavity ceiling of the clear zone).

ANIMATION (80 ticks)
  0-16   doors close 100 -> 0 deg
  16-64  ring dy 0 -> -29 (one top-to-bottom sweep); arms t = 45*sin(2*pi*(tick-16)/16),
         arm_b = -arm_a (three swings); sparks at the 4 nozzles, following the ring
  64-80  doors open 0 -> 100 deg; burst steam from the 4 vents at tick 64; ring can rise back
         to dy 0 during this phase (it moves around the player, nothing to hit)
  The viewer page has a "Loadout cycle" button that plays exactly this timeline.

STEAM VENT POINTS (grilles framing the doors; "right/left" as seen from the front)
  top_right     (-7.75, 24, -10.5)     blow down
  top_left      (23.75, 24, -10.5)      blow down
  bottom_right  (-7.75, -13, -10.5)   blow up / forward (-z)
  bottom_left   (23.75, -13, -10.5)    blow up / forward (-z)

SPARK / NOZZLE POINTS (ring nozzle tips at rest; add the ring's dy to y; all point inward)
  west   (0, 16.5, 5)   -> +x
  east   (16, 16.5, 5)   -> -x
  north  (8, 16.5, -2)   -> +z
  south  (8, 16.5, 12)   -> -z
  Bonus emitter tips on the arms (rotate with the arm): arm_a (4.5, 22.21, 3.95), arm_b (11.5, 22.21, 3.95)

GLOW (fullbright cubes, each on its own element)
  loadout_station:  Screen (dark monitor glass), Status_lamps (green/amber/red), Lamp_00..Lamp_22
                    (the 9 coin lamps), Beacon_L / Beacon_R (amber fascia lamps),
                    Ceiling_light_f / _b (cyan cavity lights)
  loadout_station_ring:  Print_front / Print_back / Print_L / Print_R  (the cyan print light,
                    on the ring's underside)
  loadout_station_arm_a / _b:  Emitter
  Doors: no glow.

CONTROL COLUMN (viewer's right)
  Screen face (dark, ready for a UI overlay): north face at z -11, x -15..-7.5, y 6.5..14.5
  Status lamp strip above it, keypad below, red e-stop.
COIN BANK (viewer's left)
  9 brass-ringed slots, 2 x 2.5 px, front face z -10.5, rows top->bottom y 11.5 / 8 / 4.5 (bottom
  edges), columns x 23.5-25.5 / 26.5-28.5 / 29.5-31.5 (door side -> outer edge); each slot has a 0.5 x 1.5 px lamp on its
  door side (Lamp_rc, row r, column c). Lamps are baked in lit (rows 0-1 green, row 2 amber).
  Coin return tray below at y 1..2.5.

FACES  (limit ~700)
  loadout_station 300, door_right 65, door_left 68, ring 81, arm_a 28, arm_b 28
  -> 570 faces for the whole rig (assembled item model: 570)
  loadout_station.png: 512 x 512, about 51% filled.

==========================================================================================
PART 2 - LOADOUT CAPSULE  (1 x 1 x 2, x/z 0..16, y 0..32, anchor = bottom cell, front = north)
==========================================================================================
MODELS
  loadout_capsule       static pod: boat-tail base, 4 stabiliser fins (two 45 deg blades), brass
                        rotating band, round hull, front window with the door pocket, display mold,
                        hazard band, ogive nose (13 -> 11 -> 9 -> 5 px) and the lifting eye.
                        Upper cell = invisible filler.
  loadout_capsule_door  armoured door, slides sideways INTO the hull (BER)
  loadout_capsule_item  static + door shut (item/loadout_capsule.json -> this)
  Stencils: LOADOUT (vertical) on both sides and the back; hazard band; slide arrow and 01 on the door.

DOOR
  poseStack.translate(dx/16, 0, 0), dx = 0 (shut) ... 4 (open), along +X (east)
  TRAVEL = 4 px.  Door x 6..10.5, y 8..21.5, z 3..4; it disappears into a pocket
  behind the left-hand jamb and stays fully inside the hull at full travel (checked every 0.25 px).
  Window it uncovers: x 6..10, y 8..21.5 (4 x 13.5 px).
  Why the window is narrow: a single door that slides sideways has to fit beside the window inside
  the 16 px block, so window + door + the hull wall can't exceed ~15 px. The widest centred opening
  that a one-piece sliding door can fully clear in a 1-block pod is about 4-5 px. A wider window
  would need two leaves sliding apart, or a door that sticks out of the hull.

MOLD (display only, nobody enters)
  Human silhouette in the same language as the station (helmet head, shoulders, hips, two legs),
  2 px deep, teal seam on the outline, with shallow painted equipment pockets: visor, two chest
  pouches, belt, thigh holster, knife sheath.

POINTS
  Chute anchor (centre of the lifting-eye hole): (8, 30.5, 8)   top of the eye: (8, 32, 8)
  Steam vents (grilles high on the flanks):  west (1, 19.25, 9.5) blow -x
                                             east (15, 19.25, 9.5) blow +x
  No glow cubes on the capsule; the face budget went to the shape.

FACES  (limit ~150)
  loadout_capsule 148 + loadout_capsule_door 2 = 150
  loadout_capsule.png: 256 x 256 at the same 2 texels/px, about 42% filled.

EDITING
  blockbench/<part>.bbmodel        one file per model, texture embedded
  blockbench/loadout_station_rig.bbmodel / loadout_capsule_rig.bbmodel
                                   the assembled models split into one group per part, with each
                                   group's pivot on its hinge / axis, for previewing motion
  blender/*.obj + .mtl + the two atlases (X east, Y south, Z up, 1 unit = 1 block)
  previews/                        renders (open, closed, mold without player, night, cycle frames)
