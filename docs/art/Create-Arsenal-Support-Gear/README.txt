CREATE ARSENAL - SUPPORT GEAR (remodel)
=======================================
Same names as your Support-3D-Models pack, restyled to match the benches / beacon / display pieces.
Each model now has its OWN texture (arsenal_beacon:block/<name>); support_kit.png is no longer used.
return_portal.png is unchanged - keep yours.

INSTALL
  minecraft-models/block/*.json -> assets/arsenal_beacon/models/block/
  minecraft-models/item/*.json  -> assets/arsenal_beacon/models/item/   (cannon base, shop, platforms, parcel)
  textures/block/*.png          -> assets/arsenal_beacon/textures/block/

SUPPORT CANNON  (3x3x2, oxide red + gunmetal, ~440 faces for all three parts)
  Your old pivot conventions are kept, so your renderer code should work unchanged:
  support_cannon          static base, x/z -16..32, y 0..10 (top of the bearing race = y 10)
                          outrigger jacks, hazard ring, steel traverse gear, two ready racks of
                          support canisters, fire-control console. Static block model.
  support_cannon_turret   yaws around the block centre; deck sits at y 10. Riveted truss cheeks,
                          breech house, gunner cab, loading crane, railings. Kept inside x/z -7..23
                          like the old one, so your 1.25x scale still clears the base props.
                          Designed to look right at 1.0x too - try both.
  support_cannon_barrel   modelled along -Z: axis x = 8, y = 8, hinge (8, 8, 24), muzzle at z = -16
                          (muzzle face has the bore), breech block at z 26..32, recoil cylinders on top.
                          Trunnion in turret space: (8, 27.2, 8)  -> barrel offset (0, 19.2, -16)
                          (taken from the trunnion pin on your old turret - check it lines up).
                          Projectile spawn: muzzle centre (8, 8, -16) in barrel space.
                          Breech clears the deck and breech house anywhere from 0 to 50 deg elevation.

EXCHANGE SHOP  (vending machine, 1x1x2, front = north)
  Glowing EXCHANGE header, glass window with four shelves of brass ammo-coin stacks and price tags,
  blank credit display (draw the player's balance there if you like), coin slot, keypad, dispenser flap and coin-return cup.
  Glass -> needs the translucent layer: "render_type": "minecraft:translucent" is in the JSON
  (Forge/NeoForge); on Fabric use BlockRenderLayerMap ... RenderType.translucent(). Also .noOcclusion().
  Model spans y 0..31.5: the block above must be an invisible filler (same as the benches).

SUPPORT PLATFORM Mk-1..4  (1x1x2, front = north, same size as the shop)
  Upper face: supply grid that grows each tier - Mk-1 3x3, Mk-2 4x4, Mk-3 5x5, Mk-4 6x6 cells,
              each cell with a status LED, MK-n screen above it.
              Grid area (model px): x 2..14, y 13.5..26.5, face at z 1.25 - if you render stored items
              in the cells, cell (i, j) is x 2 + i*12/n .. , y 13.5 + j*13/n .. (n = cells per side = Mk level + 2).
  Lower face: hazard-bordered plate "STAND CLEAR OF RECALL POSITION" with an arrow pointing down at
              the floor in front - the recall portal block is origin.relative(facing) (north side).
  Top: red flare-link emitter; Mk-2+ add antennas, Mk-3 adds side light strips, Mk-4 a glowing plinth.

SUPPLY PACKAGE  (support_parcel, 1x1)
  Olive steel supply drop: rubber corner guards, two cargo straps, carry handles, latches, yellow band,
  SUPPLY stencils, green locator light. Harness ring on top at (8, 13.5, 8) - attach your existing
  parachute model there.

EDITING
  blockbench/*.bbmodel (texture embedded), blender/*.obj (X east, Y south, Z up, 1 unit = 1 block).
