#!/usr/bin/env python3
"""0.0.30: installs the owner's 4-wide supply gates (docs/art/Eternal-Defense-Supply-Rail-Gates-4wide) and writes what is ours around them.

The owner's block models, item models and textures are copied byte for byte. Ours: the blockstates, a 16 x 16 lamp texture
(supply_gate_lamps.png: three lit and three dark lamps) and, for each gate, half and light, an overlay model that covers the three lamps of
that half's signal (the lit one for the light, the two others dark), read from the owner's own lamp elements (Signal_lamp_<half><n>,
n = 0 red at the top, 1 amber, 2 green). The owner's files are never edited. The 3 x 3 models of 0.0.24 and their overlays are removed,
as the owner's README asks.
"""
import json, os, shutil, struct, sys, zlib
R = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ART = os.path.join(R, 'docs/art/Eternal-Defense-Supply-Rail-Gates-4wide')
A = os.path.join(R, 'custom-mods/eternal-defense/src/main/resources/assets/eternal_defense')
KINDS = ['supply_delivery_gate', 'supply_receiving_gate']
HALVES = {'right': '0', 'left': '1'}
LIGHTS = ['red', 'amber', 'green']
FACINGS = [('north', 0), ('east', 90), ('south', 180), ('west', 270)]

def png(path, pixels, w, h):
    raw = b''.join(b'\x00' + bytes(c for px in row for c in px) for row in pixels)
    def chunk(t, d): return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    open(path, 'wb').write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))

def lamps_texture(path):
    """Row 0 (v 0..4): lit red, amber, green (a bright core, a glowing rim); row 1 (v 4..8): the same lamps dark."""
    lit = [((255, 70, 55), (255, 190, 175)), ((255, 176, 40), (255, 236, 160)), ((70, 235, 90), (200, 255, 205))]
    dark = [(70, 22, 20), (72, 52, 16), (18, 58, 24)]
    px = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for i in range(3):
        for y in range(4):
            for x in range(4):
                core = 1 <= x <= 2 and 1 <= y <= 2
                rim, hot = lit[i]
                px[y][i * 4 + x] = (*(hot if core else rim), 255)
                d = dark[i]
                px[4 + y][i * 4 + x] = (*(tuple(min(255, c + 12) for c in d) if core else d), 255)
    png(path, px, 16, 16)

def overlay(kind, half, light):
    model = json.load(open(os.path.join(A, 'models/block', f'{kind}_{half}.json')))
    els = {e['name']: e for e in model['elements'] if e.get('name', '').startswith('Signal_lamp_')}
    out = []
    for n, colour in enumerate(LIGHTS):
        lamp = els[f'Signal_lamp_{HALVES[half]}{n}']
        x0, y0, z0 = lamp['from']; x1, y1, _ = lamp['to']
        on = colour == light
        u = n * 4; v = 0 if on else 4
        e = {"name": f"Lamp_{colour}_{'lit' if on else 'dark'}", "from": [x0, y0, round(z0 - .12, 3)], "to": [x1, y1, round(z0 - .04, 3)],
             "shade": False, "faces": {"north": {"uv": [u, v, u + 4, v + 4], "texture": "#lamps"}}}
        if on: e["forge_data"] = {"block_light": 15, "sky_light": 15}
        out.append(e)
    return {"credit": "Eternal Defense: the gate's live light, drawn over the owner's three lamps of this half (front face); the owner's files are unchanged",
            "ambientocclusion": False, "textures": {"lamps": "eternal_defense:block/supply_gate_lamps", "particle": "eternal_defense:block/supply_gate_lamps"}, "elements": out}

def blockstate(model_base):
    parts = [{"when": {"facing": f}, "apply": {"model": f"eternal_defense:block/{model_base}", "y": y}} for f, y in FACINGS]
    for light in LIGHTS:
        for f, y in FACINGS:
            parts.append({"when": {"facing": f, "signal": light}, "apply": {"model": f"eternal_defense:block/{model_base}_signal_{light}", "y": y}})
    return {"multipart": parts}

def main():
    # the owner's files, unchanged
    for kind in KINDS:
        for half in HALVES:
            shutil.copyfile(os.path.join(ART, 'minecraft-models/block', f'{kind}_{half}.json'), os.path.join(A, 'models/block', f'{kind}_{half}.json'))
            shutil.copyfile(os.path.join(ART, 'textures/block', f'{kind}_{half}.png'), os.path.join(A, 'textures/block', f'{kind}_{half}.png'))
        shutil.copyfile(os.path.join(ART, 'minecraft-models/item', f'{kind}.json'), os.path.join(A, 'models/item', f'{kind}.json'))
        os.makedirs(os.path.join(A, 'textures/item'), exist_ok=True)
        shutil.copyfile(os.path.join(ART, 'textures/item', f'{kind}.png'), os.path.join(A, 'textures/item', f'{kind}.png'))
    # the 3 x 3 gates of 0.0.24 and their overlay go, as the owner's README asks
    for old in [f'models/block/{k}.json' for k in KINDS] + [f'textures/block/{k}.png' for k in KINDS] + \
               [f'models/block/supply_gate_signal_{l}.json' for l in LIGHTS] + ['textures/block/supply_gate_signal.png']:
        p = os.path.join(A, old)
        if os.path.exists(p): os.remove(p)
    # ours
    lamps_texture(os.path.join(A, 'textures/block/supply_gate_lamps.png'))
    for kind in KINDS:
        for half in HALVES:
            for light in LIGHTS:
                json.dump(overlay(kind, half, light), open(os.path.join(A, 'models/block', f'{kind}_{half}_signal_{light}.json'), 'w'), indent=2)
        json.dump(blockstate(f'{kind}_right'), open(os.path.join(A, 'blockstates', f'{kind}.json'), 'w'), indent=2)
        json.dump(blockstate(f'{kind}_left'), open(os.path.join(A, 'blockstates', f'{kind}_left.json'), 'w'), indent=2)
    print('installed the owner\'s 4-wide gates and wrote 12 lamp overlays, 4 blockstates and the lamp texture')

if __name__ == '__main__':
    main()
