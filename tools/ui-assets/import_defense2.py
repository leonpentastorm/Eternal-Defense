#!/usr/bin/env python3
"""Installs the second defense batch (docs/art/Eternal-Defense-Defense-Batch-2): Tesla Coil Turrets, Punji Spike Trap,
Industrial Spotlight, Curse Gas Totem and Heavy Cannon.

Copies the models and item models, re-saves the textures (no metadata) and writes the blockstates. The mod fills the extra cells
of a tall or wide block with its invisible structure parts, so the pack's empty *_upper and *_part models and its "half"
property are not used: the lower block's model draws the whole thing. Re-run after editing the art:

    python3 tools/ui-assets/import_defense2.py
"""
import json, pathlib, shutil
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / 'docs/art/Eternal-Defense-Defense-Batch-2/assets/eternal_defense'
ASSETS = ROOT / 'custom-mods/eternal-defense/src/main/resources/assets/eternal_defense'
FACINGS = {'north': 0, 'east': 90, 'south': 180, 'west': 270}

def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(data, indent=2) + '\n')

def rotated(model, facing, **extra):
    out = {'model': f'eternal_defense:block/{model}'}
    if FACINGS[facing]: out['y'] = FACINGS[facing]
    out.update(extra); return out

def blockstates():
    states = {}
    for mk in (1, 2, 3):
        states[f'tesla_turret_mk{mk}'] = {'multipart': [
            {'apply': {'model': f'eternal_defense:block/tesla_turret_mk{mk}'}},
            {'when': {'powered': 'true'}, 'apply': {'model': f'eternal_defense:block/tesla_turret_mk{mk}_live'}}]}
    states['heavy_cannon'] = {'variants': {'': {'model': 'eternal_defense:block/heavy_cannon'}}}
    states['punji_trap'] = {'variants': {f'facing={f},sprung={s}': rotated('punji_trap_sprung' if s == 'true' else 'punji_trap', f)
                                         for f in FACINGS for s in ('false', 'true')}}
    totem = []
    for f in FACINGS:
        totem.append({'when': {'facing': f}, 'apply': rotated('curse_totem', f)})
        totem.append({'when': {'facing': f, 'active': 'true'}, 'apply': rotated('curse_totem_active', f)})
    states['curse_totem'] = {'multipart': totem}
    # The spotlight turns in steps of 45 degrees, so its block entity renderer draws it
    states['spotlight'] = {'variants': {'': {'model': 'eternal_defense:block/spotlight'}}}
    return states

def main():
    for model in sorted((SRC / 'models/block').glob('*.json')):
        if model.stem.endswith('_upper') or model.stem.endswith('_part'): continue
        shutil.copyfile(model, ASSETS / 'models/block' / model.name)
    for model in sorted((SRC / 'models/item').glob('*.json')):
        shutil.copyfile(model, ASSETS / 'models/item' / model.name)
    for texture in sorted((SRC / 'textures/block').glob('*.png')):
        Image.open(texture).convert('RGBA').save(ASSETS / 'textures/block' / texture.name)
    for name, state in blockstates().items():
        dump(ASSETS / 'blockstates' / f'{name}.json', state)

if __name__ == '__main__':
    main()
