CREATE ARSENAL - SUPPLY RAIL GATES, 4 WIDE  (replaces the 3x3 gates)
=====================================================================
INSTALL
  minecraft-models/block/*.json -> assets/arsenal_beacon/models/block/
  minecraft-models/item/*.json  -> assets/arsenal_beacon/models/item/
  textures/block/*.png          -> assets/arsenal_beacon/textures/block/
  textures/item/*.png           -> assets/arsenal_beacon/textures/item/
  Delete the old block models supply_delivery_gate.json / supply_receiving_gate.json and their
  block textures - the blocks now use the _right / _left halves below.

SIZE  4 wide x 2 tall x 1 deep, front = north. Opening = the 2 middle cells of the bottom row:
  two side-by-side rail lanes (one rail through either lane, or two parallel lines).

WHY TWO HALF-MODELS
  A vanilla block model can't reach more than 3 blocks (elements are limited to -16..32 px), so a
  4-wide gate can't be one model. Each gate is two models, each drawn by its own column block:
    <gate>_right   model x 0..32    drawn by the RIGHT column block (seen from the front)
    <gate>_left    model x -16..16  drawn by the LEFT column block
  Together they form the whole gate; the beam is split exactly at the middle with no seam.

CELLS  (anchor = right column, bottom; +x = facing.getClockWise(); 'cw' below = that direction)
    c0  anchor                         renders <gate>_right; solid (column + footing)
    c1  anchor.relative(cw, 1)         RAIL LANE - not part of the gate, free for any vanilla rail
    c2  anchor.relative(cw, 2)         RAIL LANE - not part of the gate, free for any vanilla rail
    c3  anchor.relative(cw, 3)         renders <gate>_left; solid (column + footing)
    the .above() of all four           invisible fillers (the beam)
  6 gate cells around 2 free rail cells. Fillers render nothing (RenderShape.INVISIBLE) and pass
  clicks to the anchor.
  IMPORTANT - lane-top fillers (above c1 and c2): give them collision ONLY for the beam, i.e. the
  top part of the cell, box (0, 9.5, 3) .. (16, 16, 13) in that cell. A full-cube collision there
  would hit a riding player's head. Nothing hangs lower than 24.95 px (1.56 blocks) over the lanes.
  Placement: c0, c3 and the 4 top cells must be free; c1 / c2 may already hold rails.

RIDER CLEARANCE (honest note)
  2 tall means the beam sits in a riding player's head space. Chest / hopper / furnace carts pass
  with lots of room. A player sitting in a cart has their head about level with the beam's underside,
  so it can graze it as they pass. That's the cost of the 2-tall frame; the 3-tall version in the
  earlier zip gives full headroom if this bothers you in testing.

SUPPLY DELIVERY GATE  (FOB side, green signals lit)
  Lattice girder columns on concrete footings, girder beam with SUPPLY DELIVERY + blank nameplate on
  both faces, warm lights under the beam, one loading collar with a hazard hatch over EACH lane,
  rail signal on each column, olive bucket elevator on the left feeding the beam (green readout
  window), pallet of FOB tribute crates on the right.
  Load points: (24, 25, 8) over lane c1 and (40, 25, 8) over lane c2  [full-gate frame, x 0..64].

SUPPLY RECEIVING GATE  (base side, red signals lit)
  Same frame with SUPPLY RECEIVING + nameplate, cyan scanner bar across both lanes plus a lit scanner
  post at each lane edge (floor to beam), an INBOUND bin beside each lane with a tip chute from the
  cart side, a signal on each scanner post, raised red/white barrier arms in front of both columns,
  amber warning lamps on the beam ends.
  Unload points: bin tops at x 7..15 (right bin) and x 49..57 (left bin), y 10, z 4..13.5.

FOB NAMEPLATE (both gates, front and back): blank, drawn by code like a sign.
  In the full-gate frame: plate x 11..31, y 26.75..30.75 - it sits on the RIGHT half, so draw it from
  the anchor's BlockEntityRenderer. Front surface z = 2.6, back surface z = 13.4 (render the back
  copy rotated 180 deg about Y). Text area x 11.5..30.5, y 27.25..30.25; ~14 vanilla-font characters
  at ~0.016 block per font unit.

ITEM  models/item/<gate>.json is the whole gate scaled to 75 % so the inventory icon shows all of it.

EDITING
  blockbench/*.bbmodel (texture embedded; *_item.bbmodel = the scaled whole gate),
  blender/*.obj (X east, Y south, Z up, 1 unit = 1 block).
