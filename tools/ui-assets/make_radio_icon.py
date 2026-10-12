#!/usr/bin/env python3
"""Draws the 0.0.34 handheld radio icon (16 x 16, our own pixel art) for the Command Table's Contact Specialty Seller button: an
olive field radio with a whip antenna, a channel knob, a lit green display and a speaker grille.
Usage: python3 tools/ui-assets/make_radio_icon.py <textures/gui directory> [preview.png]"""
import os, sys
from PIL import Image

def c(h):
    h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)

P = {'k': c('#0d0c14'),                                  # outline
     'A': c('#9aa0a6'), 'a': c('#5b6168'),                # antenna, its shadow
     'O': c('#7f8a4c'), 'o': c('#5a6234'), 'h': c('#a3ad6a'),  # olive body, shadow, highlight
     'K': c('#2b2f22'), 'n': c('#c9c2a8'),                # knob, its mark
     'G': c('#8dff9a'), 'g': c('#2f9e4a'),                # display lit, display dim
     'S': c('#3a3f2a'),                                   # speaker slots
     'R': c('#2b2f22')}                                   # side talk button

ART = [
    "....k...........",
    "...kAk..........",
    "...kAk..........",
    "...kAk...kkk....",
    "...kak..kKnKk...",
    "..kkkkkkkkkkkk..",
    "..khhhhhhhhhok..",
    "..khkkkkkkkhok..",
    ".RkhkGGgGGkhok..",
    ".RkhkkkkkkkhokK.",
    "..khOOOOOOOOok..",
    "..khOSSSSSSOok..",
    "..khOOOOOOOOok..",
    "..khOSSSSSSOok..",
    "..koooooooooook.",
    "...kkkkkkkkkkk..",
]

def draw():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(ART):
        assert len(row) == 16, (y, len(row))
        for x, ch in enumerate(row):
            if ch != '.':
                img.putpixel((x, y), P[ch])
    return img

if __name__ == '__main__':
    out = sys.argv[1]; os.makedirs(out, exist_ok=True)
    img = draw(); img.save(os.path.join(out, 'radio.png'))
    if len(sys.argv) > 2:
        img.resize((256, 256), Image.NEAREST).save(sys.argv[2])
