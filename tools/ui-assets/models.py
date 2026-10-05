"""JSON element models, blockstates, loot tables, recipes and tags for the energy and support blocks."""
import json
from kit import TILES

TILE = {n: (i % 4, i // 4) for i, n in enumerate(TILES)}
FACES = ('north', 'south', 'east', 'west', 'up', 'down')

def uv(tile, w, h):
    tx, ty = TILE[tile]; return [tx * 4, ty * 4, tx * 4 + min(w, 16) / 4, ty * 4 + min(h, 16) / 4]

def el(frm, to, tile, **kw):
    """One cuboid. tile: default tile name. kw: per-face overrides (north='cyan_screen'), emit=n, rot=(angle, axis, origin), spin=90 for vertical pipes."""
    dx, dy, dz = (to[i] - frm[i] for i in range(3))
    dims = {'north': (dx, dy), 'south': (dx, dy), 'east': (dz, dy), 'west': (dz, dy), 'up': (dx, dz), 'down': (dx, dz)}
    faces = {}
    for f in FACES:
        t = kw.get(f, tile)
        if t is None: continue
        face = {'uv': uv(t, *dims[f]), 'texture': '#kit'}
        if kw.get('spin') and f in ('north', 'south', 'east', 'west'): face['rotation'] = kw['spin']
        faces[f] = face
    e = {'from': list(frm), 'to': list(to), 'faces': faces}
    if kw.get('emit'): e['light_emission'] = kw['emit']
    if kw.get('rot'): a, axis, o = kw['rot']; e['rotation'] = {'angle': a, 'axis': axis, 'origin': list(o)}
    return e

def model(elements, parent='minecraft:block/block', display=None):
    m = {'parent': parent, 'textures': {'kit': 'arsenal_beacon:block/support_kit', 'particle': 'arsenal_beacon:block/support_kit'}, 'elements': elements}
    if display: m['display'] = display
    return m

# ---- exchange shop (front faces north) -------------------------------------------------------
def exchange_shop():
    return model([
        el((1, 0, 1), (15, 2, 15), 'steel_dark'),
        el((2, 2, 4), (14, 12, 14), 'steel_plate'),
        el((3, 3, 3), (11, 11, 4), 'steel_dark', north='amber_window', emit=12),
        el((11.5, 3, 3), (13, 11, 4), 'steel_dark', north='vents'),
        el((11.5, 7, 2.5), (13, 8.5, 3), 'brass_plate'),                     # coin slot plate
        el((4, 11.5, 2.5), (12, 14, 4), 'steel_dark', north='cyan_screen', emit=15),
        el((3, 2, 1), (13, 3.5, 3.5), 'brass_plate'),                        # exit tray
        el((0, 13.5, 0), (16, 14.5, 1.5), 'steel_dark', north='hazard'),     # awning lip
        el((0, 14.5, 0), (16, 16, 14), 'brass_plate'),                       # canopy
        el((2, 13, 0.5), (4, 14, 1.5), 'ember_core', emit=15), el((12, 13, 0.5), (14, 14, 1.5), 'ember_core', emit=15),
        el((1.2, 2, 5), (2.2, 13.5, 7), 'copper_pipe', spin=90), el((13.8, 2, 5), (14.8, 13.5, 7), 'copper_pipe', spin=90),
        el((3, 3, 14), (13, 12, 15), 'vents'),
    ])

# ---- support platform, one variant per Mk -----------------------------------------------------
def support_platform(mk):
    e = [
        el((0, 0, 0), (16, 3, 16), 'steel_dark'),
        el((1, 3, 1), (15, 5, 15), 'steel_plate', up=f'grid{mk}', emit=0),
        el((1, 3, 0), (15, 4.2, 1), 'steel_dark', north='hazard'),
        el((2, 5, 11), (14, 10, 15), 'steel_plate', north='steel_dark'),
        el((3, 7, 10), (13, 10, 11), 'steel_dark', north='cyan_screen', emit=15),
        el((9.5, 5.2, 11.2), (13, 6.2, 12), 'gauge', up='gauge'),
        el((0, 3, 3), (1, 5, 13), 'copper_pipe'), el((15, 3, 3), (16, 5, 13), 'copper_pipe'),
    ]
    posts = [(0.5, 0.5), (13.5, 0.5), (0.5, 13.5), (13.5, 13.5)]
    use = {1: [], 2: posts[2:], 3: posts, 4: posts}[mk]
    for (x, z) in use:
        e.append(el((x, 5, z), (x + 2, 9, z + 2), 'brass_plate'))
    if mk >= 3: e.append(el((0, 9, 12), (16, 10, 16), 'brass_plate'))
    if mk == 4:
        e.append(el((6, 10, 11.5), (10, 12, 14.5), 'steel_plate'))
        e.append(el((7, 12, 12.5), (9, 14, 13.5), 'ember_core', emit=15))
    return model(e)

# ---- cannon: static base + turret (rotates) + barrel (recoils) --------------------------------
def cannon_base():
    return model([
        el((1, 0, 1), (15, 3, 15), 'steel_dark'),
        el((4, 3, 4), (12, 8, 12), 'steel_plate'),
        el((3, 8, 3), (13, 9, 13), 'brass_plate'),
        el((12, 3, 6), (15, 7, 10), 'vents', east='vents'),
        el((1, 3, 6), (4, 6, 10), 'steel_plate'),
        el((1.5, 3, 11), (3, 6, 14), 'copper_pipe'),
    ])
def cannon_turret():
    return model([
        el((4, 9, 4), (12, 14, 12), 'steel_plate', north='steel_dark'),
        el((5, 10, 3.2), (11, 13, 4), 'steel_dark', north='cyan_screen', emit=15),
        el((12, 11, 6), (14, 13, 8), 'steel_plate', east='gauge'),
        el((5, 14, 5), (11, 15, 11), 'brass_plate'),
        el((7, 15, 7), (9, 17, 9), 'steel_dark'),
        el((6, 9.5, 12), (10, 13.5, 14), 'hazard', south='hazard'),
    ])
def cannon_barrel():
    return model([
        el((6.5, 10.5, -8), (9.5, 13.5, 4), 'steel_dark'),
        el((6, 10, -10.5), (10, 14, -8), 'brass_plate'),
        el((6.2, 10.2, -3), (9.8, 13.8, -2), 'brass_plate'), el((6.2, 10.2, 0), (9.8, 13.8, 1), 'brass_plate'),
        el((6.8, 10.8, -11), (9.2, 13.2, -10.5), 'ember_core', emit=14),
    ])
ITEM_DISPLAY = {
    'gui': {'rotation': [30, 225, 0], 'translation': [0, -1, 0], 'scale': [0.6, 0.6, 0.6]},
    'ground': {'translation': [0, 3, 0], 'scale': [0.35, 0.35, 0.35]},
    'fixed': {'rotation': [0, 180, 0], 'scale': [0.5, 0.5, 0.5]},
    'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.35, 0.35, 0.35]},
    'firstperson_righthand': {'rotation': [0, 45, 0], 'scale': [0.4, 0.4, 0.4]},
}
def cannon_item():
    m = model(cannon_base()['elements'] + cannon_turret()['elements'] + cannon_barrel()['elements'], display=ITEM_DISPLAY); return m

# ---- parcel entity: crate + parachute ----------------------------------------------------------
def parcel():
    return model([
        el((3, 0, 3), (13, 10, 13), 'crate'),
        el((2, 10, 2), (14, 11.5, 14), 'brass_plate'),
        el((6, 11.5, 6), (10, 12.5, 10), 'steel_dark'),
        el((7, 12.5, 7), (9, 14, 9), 'ember_core', emit=15),
        el((2.6, 0, 2.6), (3.4, 10, 3.4), 'brass_plate'), el((12.6, 0, 2.6), (13.4, 10, 3.4), 'brass_plate'),
        el((2.6, 0, 12.6), (3.4, 10, 13.4), 'brass_plate'), el((12.6, 0, 12.6), (13.4, 10, 13.4), 'brass_plate'),
    ])
def parcel_chute():
    e = [el((-7, 25, -7), (23, 27, 23), 'cloth'), el((-3, 27, -3), (19, 29, 19), 'cloth'), el((1, 29, 1), (15, 30, 15), 'cloth')]
    for (x, z) in ((3, 3), (12.4, 3), (3, 12.4), (12.4, 12.4)): e.append(el((x, 11.5, z), (x + .6, 25, z + .6), 'steel_plate'))
    return model(e)

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
