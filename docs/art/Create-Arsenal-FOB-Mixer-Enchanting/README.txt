CREATE ARSENAL - FOB STATION / DRINK MIXER / ENCHANTING BENCH
=============================================================
INSTALL
  minecraft-models/block/*.json -> assets/arsenal_beacon/models/block/
  minecraft-models/item/*.json  -> assets/arsenal_beacon/models/item/
  textures/block/*.png          -> assets/arsenal_beacon/textures/block/
  File names are suggestions - if your IDs differ, rename the JSON + PNG pair and the texture
  reference inside the JSON ("arsenal_beacon:block/<name>").
  All three are static, solid-layer models (no BER, no glass).

FOB STATION  (fob_station, 2 tall x 1 x 1, front = north)
  Claim post for turning a village into a Forward Operating Base. Sandbag emplacement (individual
  bags, open at the back), olive console with a big FOB plate and a ZONE screen (top-down zone
  ring over village houses, "R64" radius readout), hazard-striped lid, a mini beacon emitter
  (brass ring, glowing cyan core, cage) as the zone projector, telescoping mast with a floodlight
  and a two-panel waving olive flag (gold star + FOB), whip antenna, ammo can and jerry can.
  Uses the cell above (y 0..32): invisible filler, same TWO_TALL rules as the shop / dispensers.
  The flag's fly end reaches x 16.2 (0.2 px past the block edge) - harmless, culling is off.
  Code hooks (model px, front = north):
    zone screen face (z = 3.75) ........ x 4.5..11.5, y 8.75..12.75
    emitter core centre ................ (8, 16.25, 7) - good origin for zone particles / beam

DRINK MIXER  (drink_mixer, 2 tall x 1 x 1, front = north)
  Fast-food soda fountain built as a sibling of the milk / bowl dispensers (same base plate,
  olive body, stainless front). Backlit DRINKS header, three backlit flavour plates (COLA,
  ORANGE, LIME) over chrome valves with push levers, grated drip tray with a cup being filled
  with orange and a finished cola with lid and straw, ICE door below, cup stack, lid stack and
  straw holder on top, CO2 bottle strapped to the right-hand side.
  I made it 2 tall so its height matches the milk dispenser, bowl dispenser and cook pot it
  stands next to (cell above = invisible filler).
  Valve centres if you want per-flavour interaction: x 11.75 (COLA), 8 (ORANGE), 4.25 (LIME),
  nozzles at y 17.25..18, z ~6.25. Cup spot on the tray: (8, 13, 5.9).

ENCHANTING BENCH  (enchanting_platform, 2 wide x 2 tall x 1 deep, front = north)
  The vanilla enchanting table as army kit. Same frame as the weapon / ammo / attachment / armor
  benches (legs, drawers, under-shelf, canopy) so it sits in that row, with an obsidian top.
  Centre: obsidian altar under red cloth with gold trim and four diamond corners, a purple
  levitator pad and a floating open book (glyph pages) hovering above it.
  Back wall: two field-manual archives (the bookshelves) either side of a glowing ENCHANT sigil
  panel (glyph ring, hexagram, LVL 30 readout); purple glyph sparks drift from the archives into
  the book; ARCANE OPS sign, purple canopy light.
  Bench: lapis ammo can full of lapis (open lid), stack of tomes, rack of three glowing XP
  bottles, and a rifle with a purple enchant glint on red cradles. Under: ARCANE crate + tomes.
  Same footprint rules as the benches: x 0..32, y 0..32, z 0..16; partner =
  anchor.relative(facing.getClockWise()) + the two cells above.
  Code hooks: book centre (16, 18.9, 7); sigil panel face (z = 15) x 10..22, y 11..26.75.
  If you want the book to bob/turn like vanilla, tell me and I'll split it into its own part
  (that needs a BER, so keep it static unless you really want the motion).

EDITING
  blockbench/*.bbmodel (texture embedded), blender/*.obj (X east, Y south, Z up, 1 unit = 1 block).
