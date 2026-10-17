ETERNAL DEFENSE - DEFENSE BATCH 2: TESLA COILS, PUNJI TRAP, SPOTLIGHT, CURSE TOTEM, HEAVY CANNON
=================================================================================================
Original models in the pack's style, namespace eternal_defense, 2 texels per pixel (4-6 on small parts, plates and
lenses). Front = north for every model; blockstates turn them. Same conventions as the electric + defense kit.

WHAT'S INSIDE
  id                   footprint   models (models/block)
  tesla_turret_mk1     1x1x2       tesla_turret_mk1, tesla_turret_mk1_live (overlay)          upper block: tesla_turret_upper
  tesla_turret_mk2     1x1x2       tesla_turret_mk2, tesla_turret_mk2_live                    upper block: tesla_turret_upper
  tesla_turret_mk3     2x2x2       tesla_turret_mk3, tesla_turret_mk3_live                    other 3+4 blocks: tesla_turret_mk3_part
  punji_trap           1x1x1       punji_trap (armed), punji_trap_sprung, punji_trap_spikes (moving part)
  spotlight            1x1x2       spotlight (off), spotlight_on                              upper block: spotlight_upper
  curse_totem          1x1x2       curse_totem, curse_totem_active (overlay)                  upper block: curse_totem_upper
  heavy_cannon         2x2x2       heavy_cannon (base), _head, _gun, _barrel, _assembled (item) other blocks: heavy_cannon_part
  Blockstates, item models, en_us additions and defense2_spec.json (leads, emitters, pivots, travel) are included.
  The *_upper / *_part models are empty (particle texture only), like power_pole_upper.

TESLA COIL TURRETS (tesla line: dark steel, copper, ceramic, chrome)
  Mk I    pedestal, ceramic insulator stack, flat copper primary, tall fine-wound secondary, chrome toroid, strike pin
  Mk II   + two Leyden-jar capacitors strapped to the primary, a grading ring, a bigger toroid with four rim prongs
  Mk III  2x2 slab, wide coil on a big insulator stack, 21 px toroid, four corner capacitor towers with ball electrodes,
          charge console with lamps
  No moving parts: the coil fires in every direction, so there's no head or gun model. Fed by electric wire, not ammo:
  each has an IN insulator (cyan band) on the plinth.
  Blockstate: multipart, base always + <id>_live when powered=true. The live overlay is cutout + full-bright: arcs
  fan from the strike ball to the toroid rim, sparks crackle round the rim, and on Mk III arcs jump from the four
  corner balls to the toroid. The bolt that hits a target is drawn by code (it changes length every shot); it should
  start at the emitter point below.
     IN lead (model px)         emitter (top of the strike pin)
     Mk I   (12.75, 4.3, 12.75)  (8, 30.5, 8)
     Mk II  (12.75, 4.3, 12.75)  (8, 31.5, 8)
     Mk III (16, 5.6, 28.75)     (16, 32, 16)

PUNJI SPIKE TRAP (1 block)
  A low steel tray (3 px high) with a hazard-painted rim and a slotted grate. Sixteen fire-hardened bamboo stakes on a
  spring bed shoot up through the slots, up to 12.5 px.
  punji_trap         armed: only the charred tips show in the slots, re-arm lever lying flat, amber lamp lit
  punji_trap_sprung  stakes up, re-arm lever raised (the cue that it needs resetting), lamp dark
  punji_trap_spikes  the bed + stakes alone, modelled in the sprung position, for a renderer that animates it:
                     translate it from y -10.5 px (armed) to 0 (sprung), fast up, slow back down while re-arming.
                     While it's down it sits inside the block below, so draw it only while it moves (or sprung) and
                     use the static models the rest of the time.
  Blockstate: facing (turns the lever side) + sprung (false/true).

INDUSTRIAL SPOTLIGHT (work-light tower, 1x1x2)
  Built to the reference you sent: twin square LED flood panels on a T-bar (black housings, yellow corner bumpers,
  finned backs), a telescoping hazard-yellow pole with a clamp knob, a carry handle on top and a tripod (legs to the
  west, east and back so the front stays clear). Cables run from the panels down the pole to a junction box carrying
  the IN lead (8, 13.7, 9.6) and a status lamp.
  spotlight          lenses dark, lamp off
  spotlight_on       lenses lit (warm white, full-bright, LED chip in the middle of each panel), green status lamp
  Blockstate: facing + half (lower/upper) + lit, like the power pole: the lower block draws it, the upper block uses
  spotlight_upper. The block light itself (level 15 when lit) is a block property. If you want a visible beam,
  the lens centres are (3.875, 28.625, 6.25) and (12.125, 28.625, 6.25), pointing -z.
  The aimable searchlight from the first pass was dropped in favour of this design.

CURSE GAS TOTEM (1x1x2, purple)
  A stacked totem in deep violet plate: a gas tank with three glowing sight glasses (violet liquid, bubbles) and a
  brass filler cap at the back; a head with a gas-mask face (brass-rimmed glowing eyes, filter snout with a lit
  grille) and glowing invented rune plates on the other three sides; a vent collar with four exhaust stacks; and a
  curse-crystal cluster in a brass cage on top. ID plate CT-1 on the plinth.
  curse_totem          idle (eyes, runes, tank, crystal glow; no gas)
  curse_totem_active   cutout overlay of drifting violet gas round the crown and the base, full-bright
  Blockstate: multipart on facing + half + active: the lower block draws curse_totem (turned by facing) and adds
  curse_totem_active when active=true; the upper block uses curse_totem_upper. The real gas cloud and its effect
  are particles and code; the overlay just makes an active totem readable at a glance.

HEAVY CANNON (2x2 base, the barrel sweeps inside 4x4; elevates to straight up)
  Lighter than the 3x3 support cannon: a long gun on a 2x2 ring mount that can fire flat or lob shells like a mortar.
  The trunnions sit high on tall cheeks, close to a short breech, and the shield is open at the top (low front plate
  under the barrel, two tall wings either side), so the barrel swings from -5 to 90 degrees without touching the deck
  or the shield. Worn industrial orange shield, wings, ring and fire-control box; dark grey cheeks, cradle and barrel.
  Brass trunnion caps, recoil cylinders, an elevation gear on the trunnion with a hand-wheel drive on the right cheek,
  a ready rack of rounds and a periscope sight.
  Parts (anchor = the north-west block, model x/z 0..32):
     heavy_cannon          static base
     heavy_cannon_head     yaw about the vertical line x 16, z 16
     heavy_cannon_gun      pitch about the X-parallel line y 20, z 18 (after the head's yaw), -5 to +90 degrees
     heavy_cannon_barrel   same pitch as the gun, plus recoil: slide it up to 3 px toward +z in the gun's frame
     heavy_cannon_assembled  rest pose (pitch 0) for the item
  Swept in 1-degree steps from -5 to 90, with and without full recoil, both ways (moving parts against fixed parts and
  the reverse): nothing touches.
  Muzzle at (16, 20, -15.75) at rest: 31.75 px from the yaw axis, so the flat sweep stays inside the 4x4 blocks
  centred on the base. At 90 degrees the muzzle is 53.75 px (3.4 blocks) up, so the block entity's render box must
  reach that high (getRenderBoundingBox), or the barrel pops out of view when the base block is off screen.
  It has no ammo item yet.

RENDER TYPES
  cutout: tesla_turret_mk*_live, curse_totem_active. Everything else is solid. Glowing parts use shade:false + forge_data/neoforge_data
  light 15.

INSTALL
  assets/eternal_defense/...           -> src/main/resources/assets/eternal_defense/ (merge)
  en_us.additions.json                 names to merge into lang/en_us.json
  defense2_spec.json                   leads, emitters, pivots, travel (machine-readable)

CHECKS
  Every model: attachment audit clean (two expected flags: the spotlight's lower pole tube and the cannon's elevation
  gear are held from the side), no overlapping coplanar faces, all elements within -16..32, only legal rotations.
  Moving parts were swept through their range against the fixed parts (cannon -5..+90, with recoil).

EDITING
  blockbench/*.bbmodel (texture embedded), blender/*.obj. The source is generated (defense2.py), so ask me for changes
  rather than hand-editing, or a rebuild overwrites them.

PREVIEWS
  tesla_turrets.png (front, then 3/4 with the live overlay), punji_trap.png (armed / sprung), spotlight.png (off / on),
  curse_totem.png (idle, active, back), heavy_cannon.png, heavy_cannon_elevation.png (0 / 30 / 60 / 90 degrees),
  icons_128.png + icons_actual_size_x3.png, and viewer_*.png from the 3D viewer (day, night, close-ups; the cannon is
  posed at yaw -25, pitch +65).
