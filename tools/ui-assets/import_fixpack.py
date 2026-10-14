#!/usr/bin/env python3
"""Installs the owner's fixed models (docs/art/Eternal-Defense-Fixpack): attached parts, no see-through faces.

Each model's JSON and its texture are installed together (the textures were repacked). The weapon and ammo tables keep their current
models (owner). The Exchange Shop, the Loadout Station and the receiving gate go through their own installers, which read the same
fixed files from their own folders:

    python3 tools/ui-assets/import_fixpack.py
    python3 tools/ui-assets/import_support_gear.py
    python3 tools/ui-assets/import_loadout.py
    python3 tools/fob/install_gates_4wide.py
    python3 tools/art/split_animated.py
"""
import json, pathlib
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / 'docs/art/Eternal-Defense-Fixpack'
ASSETS = ROOT / 'custom-mods/eternal-defense/src/main/resources/assets/eternal_defense'
NAMESPACE = 'eternal_defense'
# the pack's name -> the mod's
RENAMED = {'satellite_dish': 'satellite_beacon'}
OWN_INSTALLER = ('exchange_shop', 'loadout_', 'supply_receiving_gate')

def renamed(value):
    if isinstance(value, dict): return {k: renamed(v) for k, v in value.items()}
    if isinstance(value, list): return [renamed(v) for v in value]
    if isinstance(value, str) and ':' in value and value.split(':', 1)[1].startswith(('block/', 'item/')):
        kind, name = value.split(':', 1)[1].split('/', 1)
        return f'{NAMESPACE}:{kind}/{RENAMED.get(name, name)}'
    return value

def main():
    for path in sorted((SRC / 'minecraft-models/block').glob('*.json')):
        name = path.stem
        if name.startswith(OWN_INSTALLER):
            continue
        target = RENAMED.get(name, name)
        assert (ASSETS / f'models/block/{target}.json').exists(), f'no model {target} in the mod'
        model = renamed(json.loads(path.read_text()))
        (ASSETS / f'models/block/{target}.json').write_text(json.dumps(model, indent=2) + '\n')
        image = Image.open(SRC / f'textures/block/{name}.png'); image.load()
        image.save(ASSETS / f'textures/block/{target}.png')
        print('installed', name, '->', target, image.size, len(model['elements']), 'elements')

if __name__ == '__main__':
    main()
