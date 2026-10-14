#!/usr/bin/env python3
"""Paints the 18 x 18 potion-effect icons of the meal effects (textures/mob_effect/*.png)."""
from pathlib import Path
from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parents[2] / 'custom-mods/eternal-defense/src/main/resources/assets/eternal_defense/textures/mob_effect'
OUT.mkdir(parents=True, exist_ok=True)

def hexc(c, a=255):
    c = c.lstrip('#')
    return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), a)

def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)

def tile(accent):
    im = Image.new('RGBA', (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, 17, 17), radius=3, fill=hexc('#0f1a22'))
    d.rounded_rectangle((0, 0, 17, 17), radius=3, outline=shade(accent, .75))
    d.line((2, 1, 15, 1), fill=shade(accent, 1.2))
    return im, d

# each glyph draws inside the 14 x 14 area starting at (2, 2)
def heart(d, c):
    for (x, y) in [(4, 5), (5, 4), (6, 4), (7, 5), (8, 5), (9, 4), (10, 4), (11, 5)]:
        d.point((x, y), fill=c)
    d.polygon([(4, 5), (11, 5), (12, 8), (8, 13), (7, 13), (3, 8)], fill=c)
    d.rectangle((5, 4, 6, 6), fill=c); d.rectangle((9, 4, 10, 6), fill=c)
    d.point((5, 5), fill=shade(c, 1.5))

def shield(d, c):
    d.polygon([(4, 3), (13, 3), (13, 9), (8, 14), (4, 9)], fill=c)
    d.polygon([(6, 5), (11, 5), (11, 8), (8, 11), (6, 8)], fill=shade(c, .65))
    d.line((4, 3, 13, 3), fill=shade(c, 1.4))

def pillar(d, c):
    d.rectangle((6, 3, 11, 5), fill=shade(c, 1.2)); d.rectangle((7, 6, 10, 11), fill=c); d.rectangle((5, 12, 12, 14), fill=shade(c, .8))

def chevrons(d, c):
    for x in (3, 8):
        d.line((x, 4, x + 4, 8), fill=c, width=2); d.line((x + 4, 8, x, 12), fill=c, width=2)

def crosshair(d, c):
    d.ellipse((4, 4, 13, 13), outline=c, width=2); d.line((8, 2, 8, 15), fill=c); d.line((2, 8, 15, 8), fill=c); d.point((8, 8), fill=shade(c, 1.6))

def magazine(d, c):
    d.polygon([(6, 3), (11, 3), (12, 13), (7, 14)], fill=shade(c, .85)); d.rectangle((6, 3, 11, 5), fill=c)
    d.arc((2, 5, 9, 12), 200, 330, fill=shade(c, 1.4), width=2)

def fist(d, c):
    d.rectangle((4, 6, 13, 13), fill=c)
    for x in (4, 7, 10): d.rectangle((x, 4, x + 2, 7), fill=shade(c, 1.15))
    d.line((4, 10, 13, 10), fill=shade(c, .65)); d.rectangle((3, 8, 4, 12), fill=shade(c, .8))

def hammer(d, c):
    d.rectangle((3, 3, 12, 7), fill=c); d.rectangle((3, 3, 12, 4), fill=shade(c, 1.3)); d.rectangle((7, 8, 9, 14), fill=hexc('#a8794a'))

def bomb(d, c):
    d.ellipse((3, 6, 12, 15), fill=shade(c, .9)); d.ellipse((5, 8, 7, 10), fill=shade(c, 1.8))
    d.line((10, 6, 12, 3), fill=hexc('#c9a36a'), width=1); d.point((13, 2), fill=hexc('#ffb23d')); d.point((12, 2), fill=hexc('#ff6a2a'))

def sword(d, c):
    d.line((12, 3, 4, 11), fill=c, width=3); d.line((12, 3, 4, 11), fill=shade(c, 1.4), width=1)
    d.line((3, 9, 7, 13), fill=hexc('#a8794a'), width=2); d.line((5, 12, 3, 14), fill=hexc('#a8794a'), width=2)

def bolt(d, c):
    d.polygon([(10, 2), (4, 9), (8, 9), (6, 15), (13, 7), (9, 7)], fill=c); d.line((10, 3, 5, 9), fill=shade(c, 1.5))

def clover(d, c):
    for (x, y) in [(5, 4), (9, 4), (5, 8), (9, 8)]: d.ellipse((x, y, x + 3, y + 3), fill=c)
    d.line((8, 10, 8, 15), fill=shade(c, .7), width=2); d.point((7, 7), fill=shade(c, 1.5))

def plus(d, c):
    d.rectangle((6, 3, 10, 14), fill=c); d.rectangle((3, 6, 13, 10), fill=c); d.rectangle((7, 4, 9, 13), fill=shade(c, 1.4)); d.rectangle((4, 7, 12, 9), fill=shade(c, 1.4))

def flame(d, c):
    d.polygon([(8, 2), (11, 7), (13, 10), (11, 14), (5, 14), (3, 10), (6, 7)], fill=c)
    d.polygon([(8, 7), (10, 10), (9, 13), (6, 13), (6, 10)], fill=hexc('#ffe36b'))

def spring(d, c):
    for y in (4, 7, 10, 13): d.line((4, y, 13, y - 1), fill=c, width=2)
    d.polygon([(8, 1), (11, 4), (5, 4)], fill=shade(c, 1.4))

def waves(d, c):
    for y in (5, 9, 13):
        for x in range(3, 13, 4):
            d.arc((x, y - 2, x + 4, y + 2), 180, 360, fill=c, width=2)

def house(d, c):
    d.polygon([(8, 2), (15, 8), (1, 8)], fill=shade(c, 1.1)); d.rectangle((3, 8, 13, 14), fill=c)
    d.rectangle((7, 10, 9, 14), fill=hexc('#0f1a22')); d.rectangle((4, 9, 5, 10), fill=hexc('#ffe9a8'))

def flag(d, c):
    # the FOB Zone (0.0.23): a planted flag on a sandbag mound
    d.rectangle((4, 2, 4, 14), fill=hexc('#c9ced4')); d.polygon([(5, 2), (13, 4), (5, 7)], fill=c)
    d.point((7, 4), fill=hexc('#f3d34a')); d.rectangle((2, 13, 13, 14), fill=shade(c, .55)); d.rectangle((3, 12, 11, 12), fill=shade(c, .7))

def crates(d, c):
    # the Logistics Hub Zone (0.0.41): three stacked supply crates
    for (x0, y0) in ((2, 9), (9, 9), (5, 3)):
        d.rectangle((x0, y0, x0 + 6, y0 + 5), fill=c, outline=shade(c, .45)); d.line((x0 + 1, y0 + 1, x0 + 5, y0 + 1), fill=shade(c, 1.3))
        d.line((x0 + 1, y0 + 3, x0 + 5, y0 + 3), fill=shade(c, .7)); d.point((x0 + 3, y0 + 3), fill=hexc('#f3d34a'))

def recoil(d, c):
    d.rectangle((4, 6, 13, 8), fill=c); d.rectangle((5, 9, 7, 13), fill=shade(c, .7))
    d.line((3, 3, 3, 7, 1, 5), fill=shade(c, 1.3)); d.line((11, 11, 14, 14, 14, 11), fill=c)

def eye(d, c):
    d.polygon([(2, 8), (6, 4), (11, 4), (15, 8), (11, 12), (6, 12)], outline=c)
    d.rectangle((7, 6, 10, 10), fill=c); d.point((8, 7), fill=shade(c, 1.6))

def aim(d, c):
    d.line((3, 3, 3, 6, 6, 6), fill=c, width=2); d.line((14, 14, 14, 11, 11, 11), fill=c, width=2)
    d.line((5, 13, 12, 6), fill=c, width=2); d.rectangle((10, 4, 13, 7), fill=shade(c, 1.5))

def draw(d, c):
    d.rectangle((3, 5, 12, 7), fill=c); d.rectangle((4, 8, 6, 13), fill=c)
    d.line((10, 10, 14, 10, 12, 8), fill=shade(c, 1.4), width=2); d.line((14, 10, 12, 12), fill=c, width=2)

def headshot(d, c):
    d.ellipse((5, 3, 12, 10), outline=c); d.line((8, 1, 8, 13), fill=c); d.line((3, 6, 14, 6), fill=c)
    d.rectangle((6, 12, 11, 14), fill=shade(c, .7))

def gun_stride(d, c):
    d.rectangle((3, 4, 13, 6), fill=c); d.rectangle((4, 7, 6, 9), fill=c)
    for x in (4, 9): d.line((x, 10, x+3, 12, x, 14), fill=shade(c, 1.3), width=2)

def impact(d, c):
    d.rectangle((2, 7, 8, 10), fill=c); d.polygon([(8, 7), (11, 8), (8, 10)], fill=shade(c, 1.4))
    d.line((12, 3, 12, 6), fill=c); d.line((12, 11, 12, 14), fill=c); d.line((13, 7, 15, 5), fill=c); d.line((13, 10, 15, 12), fill=c)

def rapid(d, c):
    for y in (4, 8, 12): d.line((3, y, 12, y), fill=c, width=2); d.point((14, y), fill=shade(c, 1.5))

def scope(d, c):
    d.ellipse((3, 3, 14, 14), outline=c, width=2); d.line((8, 5, 8, 12), fill=c); d.line((5, 8, 12, 8), fill=c)

def hip(d, c):
    d.line((3, 4, 3, 13, 7, 13), fill=c, width=2); d.line((14, 4, 14, 13, 10, 13), fill=c, width=2)
    d.rectangle((6, 6, 11, 8), fill=c); d.rectangle((7, 9, 8, 11), fill=shade(c, 1.5))

# 0.0.35 (owner): three effects replaced in place
def drumstick(d, c):   # Hardy: slower hunger
    d.ellipse((3, 3, 10, 10), fill=c); d.ellipse((4, 4, 7, 7), fill=shade(c, 1.35))
    d.line((9, 9, 12, 12), fill=(236, 230, 214, 255), width=2)
    d.rectangle((12, 11, 13, 14), fill=(236, 230, 214, 255)); d.rectangle((11, 12, 14, 13), fill=(236, 230, 214, 255))

def blast_shield(d, c):   # Blast Ward: less explosion damage
    d.polygon([(4, 3), (13, 3), (13, 9), (8, 14), (4, 9)], fill=shade(c, .55))
    for (x0, y0, x1, y1) in [(8, 4, 8, 12), (5, 8, 12, 8), (6, 5, 11, 11), (11, 5, 6, 11)]:
        d.line((x0, y0, x1, y1), fill=c)
    d.rectangle((7, 7, 9, 9), fill=shade(c, 1.4)); d.line((4, 3, 13, 3), fill=shade(c, 1.4))

def arrow_shield(d, c):   # Projectile Ward: less arrow, bolt, fireball and trident damage
    d.polygon([(4, 3), (13, 3), (13, 9), (8, 14), (4, 9)], fill=shade(c, .55))
    d.line((3, 13, 12, 4), fill=(236, 230, 214, 255)); d.line((10, 4, 12, 4), fill=c); d.line((12, 4, 12, 6), fill=c)
    d.line((3, 11, 5, 13), fill=c); d.line((4, 3, 13, 3), fill=shade(c, 1.4))

ICONS = {
    'vitality': ('#e0484f', heart), 'fortitude': ('#6aa0d8', shield), 'steadiness': ('#b08a5a', pillar), 'hardy': ('#d9a45a', drumstick),
    'firepower': ('#ff9a3d', crosshair), 'quick_hands': ('#f3d34a', magazine), 'brawler': ('#d9603f', fist), 'heavy_hand': ('#9aa4ad', hammer),
    'demolition': ('#8c8f99', bomb), 'might': ('#c9ced4', sword), 'agility': ('#5fe0ea', bolt), 'fortune': ('#7fd65a', clover),
    'recoil_control': ('#82c9ef', recoil), 'focus': ('#f0daa0', eye), 'snap_aim': ('#7ce0cf', aim),
    'fast_draw': ('#efad72', draw), 'dead_eye': ('#db82d9', headshot), 'gun_mobility': ('#8db1ff', gun_stride),
    'impact': ('#e4a477', impact), 'rapid_fire': ('#ffbc4e', rapid), 'scoped_focus': ('#b7bbff', scope), 'hip_focus': ('#9de4b5', hip),
    'recovery': ('#8ef08e', plus), 'blast_ward': ('#ff7a2a', blast_shield), 'projectile_ward': ('#a9c4dc', arrow_shield), 'swim': ('#4aa8ff', waves),
}

for name, (color, glyph) in ICONS.items():
    im, d = tile(hexc(color))
    sub = Image.new('RGBA', (18, 18), (0, 0, 0, 0))
    sd = ImageDraw.Draw(sub)
    glyph(sd, hexc(color))
    im.alpha_composite(sub)
    im.save(OUT / f'meal_{name}.png')
im, d = tile(hexc('#e8c17b'))
sub = Image.new('RGBA', (18, 18), (0, 0, 0, 0)); house(ImageDraw.Draw(sub), hexc('#e8c17b')); im.alpha_composite(sub); im.save(OUT / 'home_zone.png')
im, d = tile(hexc('#9cc26a'))
sub = Image.new('RGBA', (18, 18), (0, 0, 0, 0)); flag(ImageDraw.Draw(sub), hexc('#9cc26a')); im.alpha_composite(sub); im.save(OUT / 'fob_zone.png')
im, d = tile(hexc('#58c4e0'))
sub = Image.new('RGBA', (18, 18), (0, 0, 0, 0)); crates(ImageDraw.Draw(sub), hexc('#58c4e0')); im.alpha_composite(sub); im.save(OUT / 'logistics_hub_zone.png')
print('wrote', len(ICONS) + 3, 'icons')
