CREATE ARSENAL - SUPPLY RAIL GATES
==================================
INSTALL
  minecraft-models/block/*.json -> assets/arsenal_beacon/models/block/
  minecraft-models/item/*.json  -> assets/arsenal_beacon/models/item/
  textures/block/*.png          -> assets/arsenal_beacon/textures/block/
  Static, solid-layer models (no BER, no glass). The minecart and rails in the previews are
  viewer stand-ins, not part of the files.

SHARED FRAME  (both gates: 3 wide x 3 tall x 1 deep, front = north)
  Concrete footings with hazard edges, two lattice girder columns, girder crossbeam with the
  gate's name on BOTH faces (front and back), three-lamp rail signal on the right-hand column.
  A vanilla rail runs straight through the middle, front to back, along the facing axis.

  FOB NAMEPLATE (both gates, front AND back): a long blank off-white plate in a dark riveted
  frame, under the gate title on the crossbeam. Deliberately empty - draw the FOB name on it
  with a BlockEntityRenderer on the anchor (same idea as a vanilla sign). In the model's own
  frame (facing north; apply the usual facing rotation first):
    front plate surface z = 2.6    text area x -4.5..20.5, y 17.75..20.5, centre (8, 19.125)
    back  plate surface z = 13.4   same x / y; render rotated 180 deg about Y so it reads
  Suggested text: dark ink (0x2A2A2A), scale ~0.018 block per font unit (y flipped) -> letters
  ~2.5 px tall, ~14-16 vanilla-font characters fit across; shrink the scale for longer names.
  Offset the text ~0.002 block off the plate to avoid z-fighting.

  HOW THE CELLS WORK (this one differs from the benches - read this)
    The ANCHOR is the middle cell of the opening = the cell directly ABOVE the rail.
    Model coords are relative to it: ground is y = -16, top is y = 32, x -16..32, z 0..16.
      anchor                                   renders the model; NO collision, NO selection
                                               shape, non-suffocating, non-occluding
      anchor.below()                           the RAIL CELL - not part of the gate. The player
                                               lays any straight vanilla rail here (normal,
                                               powered, detector, activator)
      anchor.above()                           filler (crossbeam)
      anchor.relative(cw)  + its .below() / .above()     filler column (viewer's left)
      anchor.relative(ccw) + its .below() / .above()     filler column (viewer's right)
    = 8 gate cells around 1 free rail cell. Fillers render nothing (RenderShape.INVISIBLE),
    take the clicks and forward them to the anchor. Suggested placement: the item is used on a
    rail -> anchor = railPos.above(), facing = the rail's direction.
  Clear opening: x 0..16, y -16..15.85 (just under 2 full blocks) - chest/hopper carts AND a
  riding player pass through without touching anything.
  Cart detection box: the rail cell, anchor.below() (inflate ~0.2 if you want early pickup).

SUPPLY DELIVERY GATE  (supply_delivery_gate)  -  FOB side, green signal lit
  Loading hopper on top of the beam heaped with supplies (SUPPLY stencil, hazard rim), loading
  collar with a hazard hatch under the beam right over the cart, olive bucket elevator on the
  left with a green readout window, feeding the hopper through a spout, and a pallet of FOB
  tribute crates waiting on the right. Warm lights under the beam.
  Load point (where items "drop" into the cart): (8, 16, 8) on the collar's underside.

SUPPLY RECEIVING GATE  (supply_receiving_gate)  -  base side, red signal lit
  Cargo scanner: cyan light bar under the beam plus a cyan emitter strip down the right-hand
  column. Tip chute from the cart's left side into an olive INBOUND bin heaped with goods
  (the left column sits further out to make room for it). Raised red/white checkpoint barrier
  on the right, amber warning beacon and a link antenna back to the FOBs on top.
  Unload point: bin top, x 18..25.5, y -6, z 2.5..13.5 (or drop into a container you put there).
  Gameplay hint that matches the art: put an unpowered powered-rail in the rail cell; it brakes
  the cart under the scanner (red light), and the gate powers it to release the empty cart.

EDITING
  blockbench/*.bbmodel (texture embedded), blender/*.obj (X east, Y south, Z up, 1 unit = 1 block).
