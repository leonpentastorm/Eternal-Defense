#!/usr/bin/env python3
"""Installs the Loadout Station and Loadout Capsule art (docs/art/Eternal-Defense-Loadout) into the mod.

Copies minecraft-models/block and item and the two textures into assets/eternal_defense, writes the blockstates of the
station, the capsule and their invisible filler cells, and checks the face counts the pack's README promises.
The README is the authority for every coordinate; the mod reads them from LoadoutModel. Re-run after editing the art:

    python3 tools/ui-assets/import_loadout.py
"""
import json, pathlib, shutil

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / 'docs/art/Eternal-Defense-Loadout'
ASSETS = ROOT / 'custom-mods/eternal-defense/src/main/resources/assets/eternal_defense'
NAMESPACE = 'eternal_defense'
# README "FACES": faces per model
FACES = {'loadout_station': 300, 'loadout_station_door_right': 65, 'loadout_station_door_left': 68, 'loadout_station_ring': 81,
         'loadout_station_arm_a': 28, 'loadout_station_arm_b': 28, 'loadout_station_assembled': 570,
         'loadout_capsule': 148, 'loadout_capsule_door': 2, 'loadout_capsule_item': 150}
FACING_Y = {'north': 0, 'east': 90, 'south': 180, 'west': 270}

def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(data, indent=2) + '\n')

def renamed(value):
    """Texture and parent references in the mod's own namespace, whatever namespace the pack was exported with."""
    if isinstance(value, dict): return {k: renamed(v) for k, v in value.items()}
    if isinstance(value, list): return [renamed(v) for v in value]
    if isinstance(value, str) and ':' in value and value.split(':', 1)[1].startswith(('block/', 'item/')): return NAMESPACE + ':' + value.split(':', 1)[1]
    return value

def model(path):
    m = renamed(json.loads(path.read_text()))
    if 'credit' in m: m['credit'] = 'Eternal Defense - remodel'
    return m

def facings(model_id):
    return {'variants': {f'facing={f}': {'model': model_id, 'y': y} for f, y in FACING_Y.items()}}

def main():
    for kind in ('block', 'item'):
        for f in sorted((SRC / 'minecraft-models' / kind).glob('*.json')):
            m = model(f)
            if kind == 'block' and f.stem in FACES:
                faces = sum(len(e['faces']) for e in m['elements'])
                assert faces == FACES[f.stem], f'{f.stem}: {faces} faces, README says {FACES[f.stem]}'
            dump(ASSETS / 'models' / kind / f.name, m); print('model', kind, f.stem)
    for name in ('loadout_station', 'loadout_capsule'):
        shutil.copyfile(SRC / 'textures/block' / f'{name}.png', ASSETS / 'textures/block' / f'{name}.png'); print('texture', name)
    # The anchors draw the static bodies; every filler cell is invisible and only names a particle texture
    dump(ASSETS / 'blockstates/loadout_station.json', facings(f'{NAMESPACE}:block/loadout_station'))
    dump(ASSETS / 'blockstates/loadout_capsule.json', facings(f'{NAMESPACE}:block/loadout_capsule'))
    for name, texture in (('loadout_station_part', 'loadout_station'), ('loadout_capsule_part', 'loadout_capsule')):
        dump(ASSETS / f'models/block/{name}.json', {'credit': 'Eternal Defense', 'textures': {'particle': f'{NAMESPACE}:block/{texture}'}})
        dump(ASSETS / f'blockstates/{name}.json', {'multipart': [{'apply': {'model': f'{NAMESPACE}:block/{name}'}}]})
    print('blockstates written')

if __name__ == '__main__':
    main()
