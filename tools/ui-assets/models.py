"""JSON element models, blockstates, loot tables, recipes and tags for the energy and support blocks.

Everything is built from the shared 512 x 512 tile kit (kit.py): every face of up to 16 units shows one 64 px tile (4 texels per unit),
and larger boxes are cut into 16-unit pieces so the detail density never drops. Style matches defense_beacon_level4: charcoal
brushed metal, gold plates, cyan light strips, hazard stripes.
"""
import json
from kit import TILES, FULL

TILE = {n: (i % 8, i // 8) for i, n in enumerate(TILES)}
FACES = ('north', 'south', 'east', 'west', 'up', 'down')

def uv(tile, w, h):
    tx, ty = TILE[tile]
    if tile in FULL:      # authored at the face's own shape: map the authored area whole
        pw, ph = FULL[tile]; return [tx * 2, ty * 2, tx * 2 + pw / 32, ty * 2 + ph / 32]
    return [tx * 2, ty * 2, tx * 2 + min(w, 16) / 8, ty * 2 + min(h, 16) / 8]    # 512 px kit = 16 uv units: a 64 px tile is 2 units, one model unit is 4 px = 0.125 units

def cuts(a, b):
    n = max(1, int(-(-(b - a) // 16)))
    step = (b - a) / n
    return [(a + i * step, a + (i + 1) * step) for i in range(n)]

class M:
    """Collects cuboids. b(from, to, tile, up='tile', emit=n, rot=(angle, axis, origin)); None removes a face, a tile name replaces it."""
    def __init__(self): self.e = []
    def b(self, frm, to, tile, emit=0, rot=None, **faces):
        xs, ys, zs = cuts(frm[0], to[0]), cuts(frm[1], to[1]), cuts(frm[2], to[2])
        if rot and (len(xs) > 1 or len(ys) > 1 or len(zs) > 1): raise ValueError('rotated boxes must be at most 16 units')
        for ix, (x0, x1) in enumerate(xs):
            for iy, (y0, y1) in enumerate(ys):
                for iz, (z0, z1) in enumerate(zs):
                    edge = {'west': ix == 0, 'east': ix == len(xs) - 1, 'down': iy == 0, 'up': iy == len(ys) - 1, 'north': iz == 0, 'south': iz == len(zs) - 1}
                    dx, dy, dz = x1 - x0, y1 - y0, z1 - z0
                    dims = {'north': (dx, dy), 'south': (dx, dy), 'east': (dz, dy), 'west': (dz, dy), 'up': (dx, dz), 'down': (dx, dz)}
                    fs = {}
                    for f in FACES:
                        t = faces.get(f, tile)
                        if t is None or not edge[f]: continue
                        fs[f] = {'uv': uv(t, *dims[f]), 'texture': '#kit'}
                    el = {'from': [x0, y0, z0], 'to': [x1, y1, z1], 'faces': fs}
                    if emit: el['light_emission'] = emit
                    if rot: a, axis, o = rot; el['rotation'] = {'angle': a, 'axis': axis, 'origin': list(o)}
                    self.e.append(el)
        return self
    def model(self, display=None):
        m = {'parent': 'minecraft:block/block', 'textures': {'kit': 'arsenal_beacon:block/support_kit', 'particle': 'arsenal_beacon:block/support_kit'}, 'elements': self.e}
        if display: m['display'] = display
        return m

# ---- exchange shop: a trader's booth, front faces north --------------------------------------------------
def exchange_shop():
    m = M()
    m.b((0, 0, 0), (16, 2, 16), 'dark_plate', up='perforated')
    m.b((1, 0.6, 0), (15, 1.8, 0.4), 'hazard')                                                  # hazard skirt
    m.b((0.5, 2, 0.5), (15.5, 8, 7), 'plate', north='perforated')                              # counter
    m.b((2, 3, 0.1), (14, 7, 0.5), 'dark_plate', north='dark_plate')                           # recessed front panel
    m.b((1.5, 2.6, 0.0), (14.5, 3.2, 0.5), 'gold'); m.b((1.5, 7, 0.0), (14.5, 7.6, 0.5), 'gold')
    m.b((10.5, 4.6, -0.3), (13.5, 5.8, 0.3), 'gold_dark', north='gold_dark')                  # coin slot plate
    m.b((11, 5, -0.4), (13, 5.4, 0.1), 'dark_plate')
    m.b((-0.5, 8, -1), (16.5, 9, 8), 'gold')                                                    # counter top
    m.b((0.5, 9, 2), (4, 11, 5), 'crate'); m.b((4.5, 9, 3), (7.5, 10.4, 6), 'crate')         # goods on the counter
    m.b((11, 9, 3), (14, 9.8, 6), 'glass', emit=8); m.b((11.5, 9.8, 3.5), (13.5, 11, 5.5), 'core', emit=15)   # sample energy cell
    m.b((1, 9, 8), (15, 15, 14), 'dark_plate', north='perforated')                              # back board
    m.b((2.5, 10, 7.6), (9.5, 14, 8), 'screen_exchange', north='screen_exchange', emit=15)
    m.b((10.5, 10.5, 7.6), (13.5, 11.2, 8), 'cyan', emit=15); m.b((10.5, 12.5, 7.6), (13.5, 13.2, 8), 'cyan', emit=15)
    m.b((10.2, 11.8, 7.2), (13.8, 12.2, 8.4), 'gold')                                           # shelf
    m.b((0, 9, 7), (1.2, 15, 15), 'gold_dark'); m.b((14.8, 9, 7), (16, 15, 15), 'gold_dark')   # side posts
    m.b((1, 2, 14), (15, 15, 16), 'dark_plate', south='vents', north='vents')                  # rear
    m.b((1, 15, -1.5), (15, 16, 9), 'gold', up='gold', north='gold')                            # awning roof
    m.b((1, 14.2, -1.5), (15, 15, -0.7), 'hazard')                                              # hazard fringe
    m.b((1, 14, 0), (15, 15, 9), 'dark_plate')
    m.b((3, 13.4, 1.6), (5, 14, 3.4), 'cyan', emit=15); m.b((11, 13.4, 1.6), (13, 14, 3.4), 'cyan', emit=15)   # awning lamps
    m.b((15.4, 2, 7.6), (16.4, 9, 8.6), 'pipe_gold', north='pipe_gold')                        # side pipe
    return m.model()

# ---- support platform: a command desk, one variant per Mk ---------------------------------------------------
def support_platform(mk):
    m = M()
    m.b((0, 0, 0), (16, 3, 16), 'dark_plate', up='perforated')
    m.b((1, 0.8, 0), (15, 2.2, 0.4), 'hazard')
    m.b((1, 3, 3), (15, 8, 15), 'plate', north='perforated')
    m.b((0, 3, 0), (16, 4, 3), 'gold', north='gold')
    m.b((0.5, 4, 0.5), (15.5, 7.6, 3), 'dark_plate', north='perforated')                         # front kick panel
    m.b((1.5, 7.2, 0.2), (14.5, 8, 3), 'gold', north='gold')
    m.b((1.5, 8, 0.5), (14.5, 8.6, 10.5), 'gold', up='gold')                                      # pad bezel
    m.b((3.5, 8.6, 1), (12.5, 8.9, 10), 'dark_plate', up=f'grid{mk}', emit=6)                  # the grid pad
    m.b((1, 8, 11), (15, 15, 13), 'plate', north='dark_plate')                                    # back console
    m.b((1.5, 14.6, 10.6), (14.5, 15.4, 13.4), 'gold')
    m.b((2.5, 9.2, 10.6), (9.5, 13.8, 11), 'screen_support', north='screen_support', emit=15)
    m.b((10.2, 9.6, 10.6), (13.8, 12.2, 11), f'mk{mk}', north=f'mk{mk}', emit=15)
    m.b((10.2, 12.8, 10.6), (11.6, 14, 11), 'led', north='led', emit=15)
    m.b((12.2, 12.8, 10.6), (13.8, 14, 11), 'gauge', north='gauge')
    m.b((13, 15.4, 12), (13.8, 18, 12.8), 'pipe_steel'); m.b((12.8, 18, 11.8), (14, 18.8, 13), 'led', emit=15)   # antenna
    m.b((0, 3, 4), (1, 6, 14), 'pipe_gold'); m.b((15, 3, 4), (16, 6, 14), 'pipe_gold')
    m.b((3, 3, 15), (13, 11, 16), 'vents', north=None)
    if mk >= 2:                                                                                   # wing panels + side screens
        m.b((-1, 3, 3), (0, 10, 14), 'gold'); m.b((16, 3, 3), (17, 10, 14), 'gold')
        m.b((-1.2, 5, 5), (-1, 9, 12), 'cyan', emit=15); m.b((17, 5, 5), (17.2, 9, 12), 'cyan', emit=15)
    if mk >= 3:                                                                                   # corner pylons and a top rail
        for (x, z) in ((-1, 0), (14.5, 0)): m.b((x, 3, z), (x + 2.5, 14, z + 2.5), 'light_plate'); m.b((x - .3, 14, z - .3), (x + 2.8, 15, z + 2.8), 'gold'); m.b((x + .6, 15, z + .6), (x + 1.9, 16.5, z + 1.9), 'cyan', emit=15)
        m.b((1.5, 14, 0), (14.5, 15, 1.5), 'hazard', north='hazard')
    if mk >= 4:                                                                                   # roof spar and the energy core
        m.b((-1, 14.6, 0), (17, 15.6, 16), 'dark_plate', up='gold', down=None, north='gold')
        m.b((5, 15.6, 5), (11, 17.6, 11), 'gold_dark'); m.b((6, 17.6, 6), (10, 21.5, 10), 'core', emit=15, up='core'); m.b((5.5, 21.5, 5.5), (10.5, 22.5, 10.5), 'gold')
    return m.model()

# ---- support cannon: 3 x 3 x 2 emplacement (base) + rotating turret + elevated recoiling barrel -----------------
def cannon_base():
    m = M()
    m.b((-16, 0, -16), (32, 3, 32), 'dark_plate', up='perforated')
    for (a, b_) in (((-16, -16), (32, -14.5)), ((-16, 30.5), (32, 32)), ((-16, -14.5), (-14.5, 30.5)), ((30.5, -14.5), (32, 30.5))):
        m.b((a[0], 3, a[1]), (b_[0], 4.5, b_[1]), 'gold')                                       # gold curb all round
    for (a, b_) in (((-13, -13), (29, -11.5)), ((-13, 27.5), (29, 29)), ((-13, -11.5), (-11.5, 27.5)), ((27.5, -11.5), (29, 27.5))):
        m.b((a[0], 3, a[1]), (b_[0], 3.9, b_[1]), 'hazard', up='hazard')                        # hazard ring
    for (cx, cz) in ((-14.5, -14.5), (21.5, -14.5), (-14.5, 21.5), (21.5, 21.5)):               # anchor pylons
        m.b((cx, 3, cz), (cx + 7.5, 9, cz + 7.5), 'plate'); m.b((cx - .4, 9, cz - .4), (cx + 7.9, 10, cz + 7.9), 'gold')
        m.b((cx + 2.5, 10, cz + 2.5), (cx + 5, 12.5, cz + 5), 'cyan', emit=15)
    m.b((-8, 3, -4), (24, 9, 20), 'plate'); m.b((-4, 3, -8), (20, 9, 24), 'plate')              # octagonal pedestal
    m.b((-9, 9, -5), (25, 10, 21), 'gold_dark'); m.b((-5, 9, -9), (21, 10, 25), 'gold_dark')
    m.b((-15, 4, -1), (-9, 12, 17), 'dark_plate', west='vents', east='perforated')              # magazine
    m.b((-15.5, 12, -1.5), (-8.5, 13, 17.5), 'gold'); m.b((-9.3, 6, 2), (-9, 10, 14), 'cyan', emit=15)
    m.b((25, 4, -1), (31, 12, 17), 'plate', east='vents', west='perforated')                    # generator
    m.b((24.6, 6.5, 3), (25, 11, 13), 'screen_fire', west='screen_fire', emit=15)
    m.b((25, 12, -1.5), (31.5, 13, 17.5), 'gold')
    m.b((-9, 5, 6.5), (-8, 7.5, 9.5), 'pipe_gold'); m.b((24, 5, 6.5), (25, 7.5, 9.5), 'pipe_gold')
    m.b((-6, 3, -14), (22, 5, -9), 'light_plate', up='perforated'); m.b((-6.5, 5, -14), (22.5, 5.8, -13), 'gold')   # step plate
    m.b((2, 4, -8.4), (14, 8, -8), 'screen_fire', north='screen_fire', emit=15)
    m.b((0, 4, 24), (16, 8, 24.4), 'sign_cannon', south='sign_cannon')
    m.b((-16, 3, 12), (-15, 7, 20), 'sign_ardent', west='sign_ardent')
    return m.model()

def cannon_turret():
    m = M()
    m.b((-6, 10, -3), (22, 14, 19), 'dark_plate', up='plate'); m.b((-3, 10, -6), (19, 14, 22), 'dark_plate', up='plate')   # turntable
    m.b((-7, 13.5, -4), (23, 14.5, 20), 'gold_dark'); m.b((-4, 13.5, -7), (20, 14.5, 23), 'gold_dark')
    m.b((-4, 14.5, 12), (20, 29, 28), 'plate', south='vents', north='perforated', up='perforated')   # engine house
    m.b((-4.6, 29, 11.4), (20.6, 30, 28.6), 'gold')
    m.b((0, 17, 11.6), (10, 23, 12), 'screen_support', north='screen_support', emit=15)
    m.b((12, 22, 11.6), (14, 24.5, 12), 'led', north='led', emit=15)
    m.b((12, 16.5, 11.6), (18, 21, 12), 'gauge', north='gauge')
    for i in range(5): m.b((6, 15.5 + 2.8 * i, 28), (10, 16.2 + 2.8 * i, 28.8), 'gold')       # ladder rungs
    m.b((16, 30, 24), (16.8, 32, 24.8), 'pipe_steel')
    m.b((-3, 14, -7), (19, 21, 0), 'plate', north='hazard'); m.b((-3.4, 21, -6), (19.4, 22, 0.5), 'gold')   # armoured shield
    m.b((-2, 21, -5), (19, 24, -2), 'dark_plate')
    m.b((-3, 14, 0), (3, 32, 16), 'light_plate', up='gold', west='plate'); m.b((13, 14, 0), (19, 32, 16), 'light_plate', up='gold', east='plate')   # cheek plates
    m.b((-3.1, 17, 2), (-3, 30, 3.5), 'cyan', emit=15); m.b((19, 17, 2), (19.1, 30, 3.5), 'cyan', emit=15)
    m.b((-6, 25.2, 6), (22, 29.2, 10), 'gold_dark')                                              # trunnion axle through the cheeks
    m.b((-7, 24.6, 5), (-5, 29.8, 11), 'gold'); m.b((21, 24.6, 5), (23, 29.8, 11), 'gold')
    m.b((4, 29, 14), (12, 30.5, 22), 'pipe_gold')                                                # hydraulic reservoir
    return m.model()

def cannon_barrel():
    """Built along -Z with its hinge at (8, 8, 24): the renderer pivots it on the turret and slides it back when it fires."""
    m = M()
    m.b((3, 3, 26), (13, 13, 32), 'dark_plate'); m.b((2.5, 2.5, 27), (13.5, 13.5, 28.5), 'gold')            # breech
    m.b((3, 3, 10), (13, 13, 26), 'plate'); m.b((4.5, 1.8, 10), (11.5, 14.2, 26), 'plate'); m.b((1.8, 4.5, 10), (14.2, 11.5, 26), 'plate')
    m.b((2.5, 2.5, 22), (13.5, 13.5, 23.6), 'gold'); m.b((2.5, 2.5, 12), (13.5, 13.5, 13.6), 'gold')
    m.b((4, 4, -4), (12, 12, 10), 'light_plate'); m.b((5.2, 2.8, -4), (10.8, 13.2, 10), 'light_plate'); m.b((2.8, 5.2, -4), (13.2, 10.8, 10), 'light_plate')
    m.b((3.4, 3.4, 4), (12.6, 12.6, 5.4), 'gold'); m.b((3.4, 3.4, -2), (12.6, 12.6, -0.6), 'gold')
    m.b((5, 5, -12), (11, 11, -4), 'plate'); m.b((4.4, 4.4, -7), (11.6, 11.6, -5.6), 'gold')
    m.b((3, 3, -16), (13, 13, -12), 'dark_plate'); m.b((2.6, 2.6, -16), (13.4, 13.4, -14.8), 'gold')       # muzzle brake
    m.b((13, 5, -15), (13.2, 11, -13), 'cyan', emit=15); m.b((2.8, 5, -15), (3, 11, -13), 'cyan', emit=15); m.b((5, 13, -15), (11, 13.2, -13), 'cyan', emit=15)
    m.b((0.6, 6, -2), (2.4, 8.6, 20), 'pipe_gold'); m.b((13.6, 6, -2), (15.4, 8.6, 20), 'pipe_gold')        # recoil sleeves
    m.b((6, 13.2, 12), (10, 13.8, 24), 'cyan', emit=15)
    return m.model()

ITEM_DISPLAY = {
    'gui': {'rotation': [30, 225, 0], 'translation': [0, -1.5, 0], 'scale': [0.26, 0.26, 0.26]},
    'ground': {'translation': [0, 3, 0], 'scale': [0.14, 0.14, 0.14]},
    'fixed': {'rotation': [0, 180, 0], 'scale': [0.2, 0.2, 0.2]},
    'head': {'translation': [0, 14, 0], 'scale': [0.4, 0.4, 0.4]},
    'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.1, 0.1, 0.1]},
    'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 1, 0], 'scale': [0.1, 0.1, 0.1]},
    'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 1, 0], 'scale': [0.1, 0.1, 0.1]},
}
def cannon_item():
    """Inventory form: base and turret as built, plus a short barrel tilted 45 degrees on the hinge (model limits: -16..32)."""
    base = cannon_base()['elements']; turret = cannon_turret()['elements']
    barrel = M()
    barrel.b((3, 19, 4), (13, 29, 12), 'dark_plate', rot=(45, 'x', (8, 27.2, 8))); barrel.b((4, 20, -12), (12, 28, 4), 'light_plate', rot=(45, 'x', (8, 27.2, 8)))
    barrel.b((3, 19, -16), (13, 29, -12), 'dark_plate', rot=(45, 'x', (8, 27.2, 8)))
    m = M(); m.e = base + turret + barrel.e
    return m.model(display=ITEM_DISPLAY)

# ---- parcel entity: a chest + parachute ------------------------------------------------------------------------
def parcel():
    m = M()
    m.b((2, 0, 2), (14, 9, 14), 'crate', up='dark_plate', down='dark_plate')
    m.b((1, 9, 1), (15, 11, 15), 'gold', down='gold_dark')
    m.b((6.5, 7, 13.7), (9.5, 10.6, 14.4), 'gold_dark', south='gold_dark'); m.b((7.2, 8.2, 14.4), (8.8, 9.4, 14.8), 'cyan', emit=15)   # latch
    m.b((0, 3, 5.5), (2, 6, 10.5), 'pipe_gold', west='pipe_gold'); m.b((14, 3, 5.5), (16, 6, 10.5), 'pipe_gold', east='pipe_gold')      # carry handles
    m.b((6.5, 11, 6.5), (9.5, 13, 9.5), 'core', emit=15, up='core')
    m.b((1.4, 0, 1.4), (2.6, 11, 2.6), 'gold_dark'); m.b((13.4, 0, 1.4), (14.6, 11, 2.6), 'gold_dark')
    m.b((1.4, 0, 13.4), (2.6, 11, 14.6), 'gold_dark'); m.b((13.4, 0, 13.4), (14.6, 11, 14.6), 'gold_dark')
    return m.model()
def parcel_chute():
    m = M()
    m.b((-7, 25, -7), (23, 27, 23), 'cloth'); m.b((-3, 27, -3), (19, 29, 19), 'cloth'); m.b((1, 29, 1), (15, 30, 15), 'cloth')
    for (x, z) in ((-2, -2), (17.4, -2), (-2, 17.4), (17.4, 17.4)): m.b((x, 11, z), (x + .6, 25, z + .6), 'pipe_steel')
    return m.model()

# ---- everything that gets written ---------------------------------------------------------------
def all_files(assets, mod):
    out = []
    def A(path, data): out.append((assets / path, data))
    A('models/block/exchange_shop.json', exchange_shop())
    A('models/item/exchange_shop.json', {'parent': 'arsenal_beacon:block/exchange_shop'})
    for mk in range(1, 5): A(f'models/block/support_platform_mk{mk}.json', support_platform(mk))
    A('models/item/support_platform.json', {'parent': 'arsenal_beacon:block/support_platform_mk1'})
    A('models/block/support_cannon.json', cannon_base())
    A('models/block/support_cannon_turret.json', cannon_turret())
    A('models/block/support_cannon_barrel.json', cannon_barrel())
    A('models/item/support_cannon.json', cannon_item())
    A('models/block/support_parcel.json', parcel())
    A('models/block/support_parcel_chute.json', parcel_chute())
    ys = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    A('blockstates/exchange_shop.json', {'variants': {f'facing={f}': {'model': 'arsenal_beacon:block/exchange_shop', 'y': y} for f, y in ys.items()}})
    A('blockstates/support_platform.json', {'variants': {f'facing={f},mk={mk}': {'model': f'arsenal_beacon:block/support_platform_mk{mk}', 'y': y} for f, y in ys.items() for mk in range(1, 5)}})
    A('blockstates/support_cannon.json', {'variants': {'': {'model': 'arsenal_beacon:block/support_cannon'}}})
    def loot(name, keep=None):
        entry = {'type': 'minecraft:item', 'name': f'arsenal_beacon:{name}'}
        if keep: entry['functions'] = [{'function': 'minecraft:copy_state', 'block': f'arsenal_beacon:{name}', 'properties': keep}]
        return {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [entry], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]}
    for name, keep in (('exchange_shop', None), ('support_platform', ['mk']), ('support_cannon', None)):
        out.append((mod / f'main/resources/data/arsenal_beacon/loot_tables/blocks/{name}.json', loot(name, keep)))
    def shaped(result, pattern, key, count=1):
        return {'type': 'minecraft:crafting_shaped', 'pattern': pattern, 'key': {k: {'item': v} for k, v in key.items()}, 'result': {'item': f'arsenal_beacon:{result}', 'count': count}}
    recipes = {
        'exchange_shop': shaped('exchange_shop', ['CCC', 'IGI', 'IRI'], {'C': 'minecraft:copper_ingot', 'I': 'minecraft:iron_ingot', 'G': 'minecraft:glass', 'R': 'minecraft:redstone'}),
        'support_platform': shaped('support_platform', ['CGC', 'IRI', 'III'], {'C': 'minecraft:copper_ingot', 'G': 'minecraft:glass', 'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone_block'}),
    }
    for folder in ('main', 'standalone'):               # both editions craft these; the standalone jar replaces main's recipe folder
        for name, data in recipes.items(): out.append((mod / f'{folder}/resources/data/arsenal_beacon/recipes/{name}.json', data))
    tag = mod / 'main/resources/data/minecraft/tags/blocks/mineable/pickaxe.json'
    values = json.loads(tag.read_text())['values'] if tag.exists() else []
    for b in ('exchange_shop', 'support_platform', 'support_cannon'):
        if f'arsenal_beacon:{b}' not in values: values.append(f'arsenal_beacon:{b}')
    out.append((tag, {'replace': False, 'values': values}))
    return out
