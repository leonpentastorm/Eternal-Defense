"""Icons of the six fire support types (64 x 64, the same painted style as the flare sprites).

They are drawn in the cannon menu next to each type's name and, large, in the "current support" banner and HUD.
"""
from artlib import *

RAMPS['green'] = [hx(c) for c in ('#0c3a1a', '#16702f', '#2cb050', '#5fe07c', '#aaffb8', '#e6ffea')]
RAMPS['gold'] = [hx(c) for c in ('#4a3300', '#8a6100', '#d9a300', '#ffd21f', '#fff07a', '#ffffd6')]


def _poly(im, pts, ramp, v):
    ImageDraw.Draw(im).polygon(pts, fill=pick(ramp, v))


def explosion():
    """A fireball: a jagged ember burst with a white-hot core and flying sparks."""
    im = new(64, 64)
    cx, cy = 32, 33
    pts = []
    for i in range(16):
        a = i * math.pi / 8 - math.pi / 2
        r = 29 if i % 2 == 0 else 17
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    _poly(im, pts, 'red', .62)
    pts2 = [(cx + (x - cx) * .72, cy + (y - cy) * .72) for x, y in pts]
    _poly(im, pts2, 'ember', .45)
    pts3 = [(cx + (x - cx) * .45, cy + (y - cy) * .45) for x, y in pts]
    _poly(im, pts3, 'ember', .8)
    glow(im, cx, cy, 11, 'ember', 1.2)
    sphere(im, cx, cy, 7, 'ember')
    glow(im, cx - 1, cy - 1, 4, 'cloth', 1.0)
    for (x, y, s) in ((8, 10, 2), (54, 12, 2), (6, 52, 3), (56, 50, 2)):
        spark(im, x, y, 'ember', s)
    outline(im)
    return im


def arrow():
    """Three arrows falling in a shower."""
    im = new(64, 64)
    d = ImageDraw.Draw(im)

    def one(x, y0, y1, lean):
        # shaft, head at the bottom (they fall), fletching at the top
        for y in range(y0, y1):
            xx = x + int((y - y0) * lean)
            for dx in (0, 1):
                im.putpixel((xx + dx, y), pick('wood', .75 if dx == 0 else .35))
        hx_, hy = x + int((y1 - y0) * lean), y1
        d.polygon([(hx_ - 3, hy), (hx_ + 4, hy), (hx_ + 1, hy + 8)], fill=pick('steel', .8))
        d.polygon([(hx_ - 1, hy), (hx_ + 2, hy), (hx_ + 1, hy + 6)], fill=pick('steel', .95))
        for k in range(3):
            fy = y0 + k * 3
            xx = x + int(k * 3 * lean)
            d.polygon([(xx - 4, fy - 3), (xx, fy), (xx, fy + 3)], fill=pick('red', .7 - k * .1))
            d.polygon([(xx + 5, fy - 3), (xx + 1, fy), (xx + 1, fy + 3)], fill=pick('red', .45 - k * .08))
    one(16, 6, 36, .12)
    one(32, 2, 46, 0)
    one(47, 9, 38, -.12)
    spark(im, 10, 52, 'ember', 3)
    spark(im, 54, 50, 'ember', 2)
    d.polygon([(6, 58), (58, 58), (52, 61), (12, 61)], fill=pick('dark', .5))
    outline(im)
    return im


def narukami():
    """A forked lightning bolt over a storm glow."""
    im = new(64, 64)
    glow(im, 32, 32, 28, 'cyan', .55)
    bolt = [(37, 3), (17, 34), (29, 34), (21, 60), (49, 24), (35, 24), (45, 3)]
    _poly(im, bolt, 'gold', .55)
    inner = [(38, 8), (23, 33), (33, 33), (26, 52), (44, 26), (32, 26), (41, 8)]
    _poly(im, inner, 'gold', .8)
    core = [(39, 13), (30, 32), (37, 32), (32, 44), (40, 29)]
    _poly(im, core, 'gold', .99)
    for (x, y, s) in ((10, 14, 3), (54, 40, 3), (12, 48, 2), (52, 10, 2)):
        spark(im, x, y, 'cyan', s)
    outline(im)
    return im


def bunker():
    """A heavy bomb falling nose first, with fins and a red warhead band."""
    im = new(64, 64)
    # fins
    d = ImageDraw.Draw(im)
    d.polygon([(22, 12), (14, 4), (24, 4), (27, 14)], fill=pick('steel', .35))
    d.polygon([(42, 12), (50, 4), (40, 4), (37, 14)], fill=pick('steel', .35))
    vcylinder(im, 22, 42, 10, 18, 'steel')
    vcylinder(im, 20, 44, 18, 44, 'steel')
    # warhead cone
    for y in range(44, 60):
        t = (y - 44) / 15
        half = int(12 * (1 - t) ** .8)
        for x in range(32 - half, 32 + half + 1):
            u = (x - 32) / max(1, half)
            im.putpixel((x, y), pick('dark', light(u, 0, math.sqrt(max(0, 1 - u * u))) * .9))
    vcylinder(im, 20, 44, 28, 33, 'red')
    vcylinder(im, 20, 44, 37, 40, 'ember')
    for x in (24, 32, 40):
        rivet(im, x, 21, 'brass')
    d.line([(26, 20), (26, 26)], fill=pick('steel', .95), width=1)
    spark(im, 10, 22, 'ember', 3)
    spark(im, 55, 30, 'ember', 2)
    outline(im)
    return im


def heal():
    """A green cross with a heart-bright core and sparkles."""
    im = new(64, 64)
    glow(im, 32, 32, 27, 'green', .5)
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((22, 5, 41, 58), radius=4, fill=pick('green', .55))
    d.rounded_rectangle((5, 22, 58, 41), radius=4, fill=pick('green', .55))
    d.rounded_rectangle((25, 8, 38, 55), radius=3, fill=pick('green', .78))
    d.rounded_rectangle((8, 25, 55, 38), radius=3, fill=pick('green', .78))
    d.rectangle((28, 12, 35, 51), fill=pick('green', .93))
    d.rectangle((12, 28, 51, 35), fill=pick('green', .93))
    # top-left bevel
    d.line([(22, 8), (22, 22)], fill=pick('green', .97))
    d.line([(8, 22), (22, 22)], fill=pick('green', .97))
    for (x, y, s) in ((10, 10, 3), (54, 12, 2), (11, 54, 2), (53, 52, 3)):
        spark(im, x, y, 'green', s)
    outline(im)
    return im


def curse():
    """A skull inside a violet flame."""
    im = new(64, 64)
    glow(im, 32, 34, 29, 'purple', .6)
    d = ImageDraw.Draw(im)
    # flame
    d.polygon([(32, 2), (24, 14), (14, 20), (10, 36), (16, 50), (32, 60), (48, 50), (54, 36), (50, 20), (40, 14)], fill=pick('purple', .45))
    d.polygon([(32, 8), (26, 18), (18, 24), (16, 36), (22, 48), (32, 54), (42, 48), (48, 36), (46, 24), (38, 18)], fill=pick('purple', .7))
    # skull
    sphere(im, 32, 30, 14, 'cloth')
    d.rounded_rectangle((24, 36, 40, 50), radius=3, fill=pick('cloth', .55))
    d.rectangle((26, 36, 38, 40), fill=pick('cloth', .6))
    d.ellipse((22, 26, 30, 36), fill=pick('dark', .15))
    d.ellipse((34, 26, 42, 36), fill=pick('dark', .15))
    d.point((26, 31), fill=pick('purple', .95)); d.point((38, 31), fill=pick('purple', .95))
    d.polygon([(31, 37), (33, 37), (34, 41), (30, 41)], fill=pick('dark', .15))
    for x in (27, 30, 33, 36):
        d.line([(x, 44), (x, 49)], fill=pick('dark', .3), width=1)
    spark(im, 10, 12, 'purple', 3)
    spark(im, 56, 14, 'purple', 3)
    outline(im)
    return im


ICONS = {'explosion': explosion, 'arrow': arrow, 'narukami': narukami, 'bunker': bunker, 'heal': heal, 'curse': curse}
