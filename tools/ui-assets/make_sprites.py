#!/usr/bin/env python3
"""Generates every new sprite, tile kit texture, 3D model, blockstate, loot table and recipe for the energy and support systems.

    python3 -m pip install pillow
    python3 tools/ui-assets/make_sprites.py

Item art follows industrial_atlas.png; placeables are JSON element models over one shared tile kit
(textures/block/support_kit.png). Re-running overwrites the generated files.
"""
import json, pathlib, sys
sys.path.insert(0, str(pathlib.Path(__file__).parent))
from artlib import *
from sprites import SPRITES, portal_sheet, parachute_texture, parachute_red_texture, nameplate_texture
from fire_icons import ICONS
import models

ROOT = pathlib.Path(__file__).resolve().parents[2]
MOD = ROOT / 'custom-mods/arsenal-beacon/src'
ASSETS = MOD / 'main/resources/assets/arsenal_beacon'
PREVIEWS = ROOT / 'docs/ui/sprites'

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n') if isinstance(data, (dict, list)) else path.write_text(data)

def main():
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    for name, make in SPRITES.items():
        im = make(); im.save(ASSETS / 'textures/item' / f'{name}.png')
        im.resize((256, 256), Image.NEAREST).save(PREVIEWS / f'{name}_256.png')
        write(ASSETS / 'models/item' / f'{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'arsenal_beacon:item/{name}'}})
        print('sprite', name)
    icons = ASSETS / 'textures/gui/fire_support'; icons.mkdir(parents=True, exist_ok=True)
    for name, make in ICONS.items():
        im = make(); im.save(icons / f'{name}.png')
        for px in (32, 16): im.resize((px, px), Image.BOX).save(icons / f'{name}_{px}.png')
        im.resize((256, 256), Image.NEAREST).save(PREVIEWS / f'fire_icon_{name}_256.png'); print('icon', name)
    ent = ASSETS / 'textures/entity'; ent.mkdir(parents=True, exist_ok=True)
    parachute_texture().save(ent / 'parachute.png'); parachute_red_texture().save(ent / 'parachute_red.png'); nameplate_texture().save(ent / 'nameplate.png'); print('parachute + nameplate')
    portal = ASSETS / 'textures/entity/return_portal.png'; portal.parent.mkdir(parents=True, exist_ok=True); sheet = portal_sheet(); sheet.save(portal); sheet.resize((512, 256), Image.NEAREST).save(PREVIEWS / 'return_portal_512.png')
    for path, data in models.all_files(ASSETS, MOD):
        write(path, data); print('wrote', path.relative_to(ROOT))

if __name__ == '__main__':
    main()
