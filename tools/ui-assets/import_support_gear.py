#!/usr/bin/env python3
"""Installs the hand-made support gear models (docs/art/Create-Arsenal-Support-Gear) into the mod.

Reads the Blockbench files (textures are embedded in them), copies the Minecraft JSON models, puts the Ardent Energy crystal on the green
credit square of the Exchange Shop, builds the shop's split model and the inventory model of the cannon. Re-run after editing the art:

    python3 tools/ui-assets/import_support_gear.py
"""
import base64, copy, io, json, pathlib
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / 'docs/art/Create-Arsenal-Support-Gear'
ASSETS = ROOT / 'custom-mods/arsenal-beacon/src/main/resources/assets/arsenal_beacon'
MODELS = ['exchange_shop', 'support_cannon', 'support_cannon_turret', 'support_cannon_barrel', 'support_parcel',
          'support_platform_mk1', 'support_platform_mk2', 'support_platform_mk3', 'support_platform_mk4']

def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(data, indent=2) + '\n')

def texture_of(name):
    d = json.loads((SRC / f'blockbench/{name}.bbmodel').read_text())
    return Image.open(io.BytesIO(base64.b64decode(d['textures'][0]['source'].split(',', 1)[1]))).convert('RGBA')

def shop_crystal(tex):
    """The credit display is a pale green 26 x 26 square (uv 128..154, 78..104): a dark screen with the Ardent Energy sprite on it."""
    box = (128, 78, 154, 104)
    screen = Image.new('RGBA', (26, 26), (6, 20, 24, 255))
    for k in range(26):
        for e in (0, 1):
            for (x, y) in ((k, e), (k, 25 - e), (e, k), (25 - e, k)): screen.putpixel((x, y), (16, 90, 100, 255) if e == 0 else (10, 48, 56, 255))
    sprite = Image.open(ASSETS / 'textures/item/ardent_energy.png').convert('RGBA')
    sprite = sprite.crop(sprite.getbbox()).resize((20, 20), Image.LANCZOS)
    a = sprite.getchannel('A').point(lambda v: 255 if v > 90 else 0); sprite.putalpha(a)
    screen.alpha_composite(sprite, (3, 3))
    tex.paste(screen, box[:2])
    return tex

def split_shop(model):
    """The shop's glass pane must not share a render layer with the shelves behind it.

    A model has one render type. With the whole shop translucent, the layer sorts quads by distance, so from some angles the pane was drawn
    before the crates behind it, wrote its depth first, and hid them. Forge's composite loader draws each child in its own layer: the body
    in the normal layers and only the pane in the translucent one.
    """
    glass = [e for e in model['elements'] if e.get('name') == 'Glass']
    body = [e for e in model['elements'] if e.get('name') != 'Glass']
    assert len(glass) == 1 and body, 'expected exactly one Glass element'
    textures = model['textures']
    root = {k: v for k, v in model.items() if k not in ('elements', 'render_type')}
    root.update({'loader': 'forge:composite',
                 'children': {'body': {'parent': 'arsenal_beacon:block/exchange_shop_body'}, 'glass': {'parent': 'arsenal_beacon:block/exchange_shop_glass'}},
                 'item_render_order': ['body', 'glass']})
    child = lambda els, rt: {'credit': 'Create Arsenal - remodel', 'ambientocclusion': False, 'render_type': rt, 'textures': textures, 'elements': els}
    return root, child(body, 'minecraft:cutout'), child(glass, 'minecraft:translucent')

def cannon_item():
    """Inventory model: base + turret + a shortened barrel tilted 45 degrees at the trunnion (a model may only reach -16..32)."""
    parts = [('support_cannon', '0'), ('support_cannon_turret', '1'), ('support_cannon_barrel', '2')]
    base = json.loads((ASSETS / 'models/block/support_cannon.json').read_text())
    out = {'textures': {'0': 'arsenal_beacon:block/support_cannon', '1': 'arsenal_beacon:block/support_cannon_turret', '2': 'arsenal_beacon:block/support_cannon_barrel',
                        'particle': 'arsenal_beacon:block/support_cannon'}, 'elements': [], 'display': base.get('display', {})}
    for name, slot in parts:
        m = json.loads((ASSETS / f'models/block/{name}.json').read_text())
        for e in m['elements']:
            e = copy.deepcopy(e)
            for f in e['faces'].values(): f['texture'] = '#' + slot
            if name == 'support_cannon_barrel':
                for k in ('from', 'to'):                                     # uniform 0.55 scale about the hinge, hinge (8, 8, 24) -> trunnion (8, 27.2, 8)
                    x, y, z = e[k]
                    e[k] = [8 + (x - 8) * 0.55, 27.2 + (y - 8) * 0.55, 8 + (z - 24) * 0.55]
                a, b = e['from'], e['to']; e['from'] = [min(a[i], b[i]) for i in range(3)]; e['to'] = [max(a[i], b[i]) for i in range(3)]
                e['rotation'] = {'angle': 45, 'axis': 'x', 'origin': [8, 27.2, 8]}
            out['elements'].append(e)
    return out

def main():
    for name in MODELS:
        m = json.loads((SRC / f'minecraft-models/block/{name}.json').read_text())
        if name == 'exchange_shop':
            root, body, glass = split_shop(m)
            dump(ASSETS / 'models/block/exchange_shop.json', root); dump(ASSETS / 'models/block/exchange_shop_body.json', body); dump(ASSETS / 'models/block/exchange_shop_glass.json', glass)
        else:
            dump(ASSETS / f'models/block/{name}.json', m)
        tex = texture_of(name)
        if name == 'exchange_shop': tex = shop_crystal(tex)
        (ASSETS / 'textures/block').mkdir(parents=True, exist_ok=True)
        tex.save(ASSETS / f'textures/block/{name}.png'); print('installed', name, tex.size)
    for stale in ('textures/block/support_parcel_chute.png', 'models/block/support_parcel_chute.json'):   # the parachute is drawn from vertices now
        if (ASSETS / stale).exists(): (ASSETS / stale).unlink()
    dump(ASSETS / 'models/item/support_cannon.json', cannon_item())
    dump(ASSETS / 'models/item/exchange_shop.json', {'parent': 'arsenal_beacon:block/exchange_shop'})
    dump(ASSETS / 'models/item/support_platform.json', {'parent': 'arsenal_beacon:block/support_platform_mk1'})
    old = ASSETS / 'textures/block/support_kit.png'
    if old.exists(): old.unlink()

if __name__ == '__main__':
    main()
