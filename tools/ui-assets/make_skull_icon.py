#!/usr/bin/env python3
"""Draws the 0.0.41 skull icon (9 x 9, our own pixel art) that sits on the beacon's health bar during a raid, next to the number of
defender deaths left before the beacon loses health.
Usage: python3 tools/ui-assets/make_skull_icon.py <textures/gui directory> [preview.png]"""
import os, sys
from PIL import Image

def c(h):
    h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)

P = {'k': c('#0d0c14'),                     # outline
     'W': c('#ece6d6'), 'w': c('#b9b2a0'),  # bone, its shadow
     'E': c('#e0484f')}                     # eye glow (the HUD's red)

ART = [
    ".kkkkkkk.",
    "kWWWWWWwk",
    "kWWWWWWwk",
    "kWEkWEkwk",
    "kWkkWkkwk",
    "kWWWkWWwk",
    ".kWwWwWk.",
    ".kWkWkwk.",
    "..kkkkk..",
]

def draw():
    img = Image.new('RGBA', (9, 9), (0, 0, 0, 0))
    for y, row in enumerate(ART):
        assert len(row) == 9, row
        for x, ch in enumerate(row):
            if ch in P:
                img.putpixel((x, y), P[ch])
    return img

if __name__ == '__main__':
    out = sys.argv[1]
    img = draw()
    img.save(os.path.join(out, 'skull.png'))
    if len(sys.argv) > 2:
        img.resize((90, 90), Image.NEAREST).save(sys.argv[2])
    print('wrote', os.path.join(out, 'skull.png'))
