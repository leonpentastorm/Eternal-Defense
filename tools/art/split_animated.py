#!/usr/bin/env python3
"""0.0.39: the animation pass. Splits the moving parts out of the owner's block models.

For each model below it writes, next to the owner's file (which is never changed):
  models/block/<model>_static.json      the owner's model without the moving parts (the blockstates use it)
  models/block/anim/<model>_<part>.json one model per moving part, its elements exactly as the owner placed them
and points the blockstates at the static model. Item models keep the owner's whole model, so inventory icons are unchanged. At rest
(no transform) the parts drawn over the static model give back the owner's model exactly; the renderer (Animations.java) moves them.

A part is a list of element names, or ('strips', element, n): that element cut into n strips along its x axis (its UVs cut to match),
so a cloth can bend between them (the FOB flag).
"""
import copy, json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'custom-mods/eternal-defense/src/main/resources/assets/eternal_defense'
MODELS = ASSETS / 'models/block'

SPLITS = {
    'fob_station': {
        'flag_0': ('strips', 'Flag_hoist', 2, 0), 'flag_1': ('strips', 'Flag_hoist', 2, 1),
        'flag_2': ('strips', 'Flag_fly', 2, 0), 'flag_3': ('strips', 'Flag_fly', 2, 1),
        'whip': ['Whip', 'Whip_tip'],
        'emitter_ring': ['Emitter_ring'],
    },
    'satellite_beacon': {
        'azimuth': ['Turntable', 'Yoke_base', 'Yoke_arm_0', 'Yoke_arm_1', 'Elevation_axle'],
        'dish': ['Dish', 'Dish_wide', 'Dish_tall', 'Dish_wide_2', 'Dish_tall_2', 'Shell', 'Shell_v', 'Shell_h', 'Hub', 'Mount',
                 'LNB', 'Horn', 'Emitter', 'Strut_low', 'Strut_high'],
        'whip': ['Whip', 'Whip_tip'],
    },
    'command_table': {
        'beacon': ['Beacon'],
        'antenna': ['Antenna', 'Antenna_tip'],
    },
    'defense_beacon_level1': {'ring_0': ['Ring']},
    'defense_beacon_level2': {'ring_0': ['Ring_0'], 'ring_1': ['Ring_1'], 'emitter_ring': ['Emitter_ring']},
    'defense_beacon_level3': {'ring_0': ['Ring_0'], 'ring_1': ['Ring_1'], 'ring_2': ['Ring_2'],
                              'emitter_ring': ['Emitter_ring', 'Prong_0', 'Prong_1', 'Prong_2', 'Prong_3']},
    'defense_beacon_level4': {'ring_0': ['Ring_0'], 'ring_1': ['Ring_1'], 'ring_2': ['Ring_2'],
                              'emitter_ring': ['Emitter_ring', 'Prong_0', 'Prong_1', 'Prong_2', 'Prong_3']},
    'support_platform_mk1': {'emitter': ['Emitter']},
    'support_platform_mk2': {'emitter': ['Emitter']},
    'support_platform_mk3': {'emitter': ['Emitter']},
    'support_platform_mk4': {'emitter': ['Emitter']},
    'enchanting_platform': dict(
        {'book_l': ['Book_cover_L', 'Book_pages_L'], 'book_r': ['Book_cover_R', 'Book_pages_R'], 'levitator_ring': ['Levitator_ring']},
        **{f'spark_{i}': [f'Glyph_spark_{i}'] for i in range(9)},
        **{f'rifle_spark_{i}': [f'Rifle_spark_{i}'] for i in range(3)}),
    'mess_hall_mk1': {'spit': ['Spit_rod', 'Spit_crank', 'Roast'], 'flame': ['Flame']},
    'cook_pot': {'flames': ['Flames']},
    'drink_pot': {'flames': ['Flames'], 'paddle': ['Paddle', 'Paddle_blade']},
    'mre_maker': {'press': ['Press_rod', 'Press_plate', 'Press_heater'], 'roller_0': ['Roller_0'], 'roller_1': ['Roller_1'],
                  'lever': ['Lever_arm', 'Lever_knob']},
    'support_cannon_turret': {'hook': ['Crane_cable', 'Crane_hook']},
}

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=1) + '\n')

def strip(element, n, k):
    """Strip k of n of an element cut along x: the same rotation, its x range and every face's u range cut to match."""
    e = copy.deepcopy(element)
    x0, x1 = element['from'][0], element['to'][0]
    a, b = x0 + (x1 - x0) * k / n, x0 + (x1 - x0) * (k + 1) / n
    e['from'][0], e['to'][0] = a, b
    e['name'] = f"{element['name']}_strip_{k}"
    for side, face in e['faces'].items():
        if side in ('east', 'west') or 'uv' not in face:
            continue
        u0, v0, u1, v1 = face['uv']
        # the north face shows x from its east end (to) at u0; south, up and down from its west end (from)
        lo, hi = ((x1 - b) / (x1 - x0), (x1 - a) / (x1 - x0)) if side == 'north' else ((a - x0) / (x1 - x0), (b - x0) / (x1 - x0))
        face['uv'] = [u0 + (u1 - u0) * lo, v0, u0 + (u1 - u0) * hi, v1]
    return e

def split(name, parts):
    source = json.loads((MODELS / f'{name}.json').read_text())
    elements = source['elements']
    by_name = {}
    for e in elements:
        by_name.setdefault(e.get('name'), []).append(e)
    moving = set()
    for part, spec in parts.items():
        if isinstance(spec, tuple):
            _, element, n, k = spec
            assert len(by_name[element]) == 1, (name, element)
            chosen = [strip(by_name[element][0], n, k)]
            moving.add(element)
        else:
            chosen = []
            for element in spec:
                assert len(by_name.get(element, [])) == 1, (name, element)
                chosen.append(copy.deepcopy(by_name[element][0]))
                moving.add(element)
        model = {'credit': f'Generated by tools/art/split_animated.py from the owner\'s {name}.json (part {part})'}
        for key in ('ambientocclusion', 'textures'):
            if key in source:
                model[key] = source[key]
        model['elements'] = chosen
        write(MODELS / 'anim' / f'{name}_{part}.json', model)
    static = {k: v for k, v in source.items() if k != 'elements'}
    static['credit'] = f'Generated by tools/art/split_animated.py: the owner\'s {name}.json without its moving parts'
    static['elements'] = [e for e in elements if e.get('name') not in moving]
    write(MODELS / f'{name}_static.json', static)
    return len(static['elements']), len(moving)

if __name__ == '__main__':
    for name, parts in SPLITS.items():
        kept, moved = split(name, parts)
        print(f'{name}: {kept} static elements, {moved} moving, {len(parts)} parts')
    # the blockstates draw the static models (the renderer adds the parts); item models keep the owner's whole model
    for state in sorted((ASSETS / 'blockstates').glob('*.json')):
        text = state.read_text()
        new = text
        for name in SPLITS:
            new = new.replace(f'"eternal_defense:block/{name}"', f'"eternal_defense:block/{name}_static"')
        if new != text:
            state.write_text(new)
            print('blockstate', state.name)
