"""Icons of the twelve fire support types (64 x 64, the same painted style as the flare sprites).

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


RAMPS['ice'] = [hx(c) for c in ('#0b2f45', '#145a80', '#2b93c4', '#6fd0f2', '#bff0ff', '#f4fdff')]
RAMPS['star'] = [hx(c) for c in ('#5a3d00', '#a87400', '#f2b705', '#ffe066', '#fff6c2', '#ffffff')]


def cluster():
    """A canister shell breaking open over five falling bomblets."""
    im = new(64, 64)
    d = ImageDraw.Draw(im)
    # the opened canister, tilted, at the top
    d.polygon([(18, 4), (34, 4), (36, 18), (16, 18)], fill=pick('steel', .55))
    d.polygon([(20, 4), (26, 4), (26, 18), (18, 18)], fill=pick('steel', .8))
    d.rectangle((16, 12, 36, 14), fill=pick('ember', .7))
    d.polygon([(16, 18), (12, 24), (20, 20)], fill=pick('steel', .4)); d.polygon([(36, 18), (42, 24), (33, 20)], fill=pick('steel', .4))
    # the bomblets, each with a spark trail
    for (x, y, r) in ((14, 34, 5), (30, 30, 5), (46, 32, 5), (22, 50, 5), (40, 50, 5)):
        for k in range(1, 4): d.point((x - k, y - 2 * k), fill=pick('ember', .9 - k * .15))
        sphere(im, x, y, r, 'dark')
        im.putpixel((x - 2, y - 2), pick('ember', .95)); im.putpixel((x - 1, y - 2), pick('ember', .7))
    spark(im, 52, 14, 'ember', 3); spark(im, 8, 18, 'ember', 2)
    outline(im)
    return im


def cryo():
    """A six-armed ice crystal over a cold glow."""
    im = new(64, 64)
    glow(im, 32, 32, 28, 'ice', .55)
    d = ImageDraw.Draw(im)
    for k in range(6):
        a = k * math.pi / 3 - math.pi / 2
        x1, y1 = 32 + math.cos(a) * 27, 32 + math.sin(a) * 27
        d.line([(32, 32), (x1, y1)], fill=pick('ice', .85), width=4)
        d.line([(32, 32), (x1, y1)], fill=pick('ice', .99), width=1)
        for t, l in ((.55, 9), (.8, 6)):
            bx, by = 32 + math.cos(a) * 27 * t, 32 + math.sin(a) * 27 * t
            for side in (-1, 1):
                b = a + side * math.pi / 3.2
                d.line([(bx, by), (bx + math.cos(b) * l, by + math.sin(b) * l)], fill=pick('ice', .8), width=2)
    sphere(im, 32, 32, 6, 'ice')
    for (x, y, s) in ((10, 10, 2), (54, 14, 3), (12, 52, 3), (52, 52, 2)):
        spark(im, x, y, 'ice', s)
    outline(im)
    return im


def napalm():
    """A wall of fire rolling over the ground."""
    im = new(64, 64)
    d = ImageDraw.Draw(im)
    tongues = [(6, 30), (14, 14), (22, 26), (30, 6), (38, 22), (46, 10), (54, 28), (60, 20)]
    base = [(2, 56), (62, 56)]
    d.polygon([base[0]] + tongues + [base[1]], fill=pick('red', .55))
    inner = [(x, y + 10 + (4 if i % 2 else 0)) for i, (x, y) in enumerate(tongues)]
    d.polygon([(6, 56)] + inner + [(58, 56)], fill=pick('ember', .55))
    core = [(x, min(54, y + 22)) for (x, y) in tongues[1:-1]]
    d.polygon([(12, 56)] + core + [(52, 56)], fill=pick('ember', .85))
    d.rectangle((2, 56, 61, 60), fill=pick('dark', .45))
    for (x, y, s) in ((8, 6, 2), (56, 4, 3), (30, 2, 2)):
        spark(im, x, y, 'ember', s)
    outline(im)
    return im


def gravity():
    """A violet vortex with a black core, pulling specks in."""
    im = new(64, 64)
    glow(im, 32, 32, 30, 'purple', .5)
    for arm in range(3):
        for k in range(160):
            t = k / 160
            a = arm * 2 * math.pi / 3 + t * 3.4 * math.pi
            r = 4 + 25 * t
            x, y = int(32 + math.cos(a) * r), int(32 + math.sin(a) * r)
            for dx in (0, 1):
                if 0 <= x + dx < 64 and 0 <= y < 64: im.putpixel((x + dx, y), pick('purple', .95 - .55 * t))
    sphere(im, 32, 32, 6, 'dark')
    for (x, y) in ((8, 20), (54, 44), (46, 8), (14, 52), (58, 24)):
        im.putpixel((x, y), pick('purple', .99)); im.putpixel((x + 1, y), pick('purple', .7))
    outline(im)
    return im


def shockwave():
    """A white-hot burst with pressure rings running outward."""
    im = new(64, 64)
    d = ImageDraw.Draw(im)
    for r, v in ((29, .45), (22, .65), (15, .85)):
        d.ellipse((32 - r, 32 - r, 32 + r, 32 + r), outline=pick('star', v), width=3)
    glow(im, 32, 32, 11, 'star', 1.2)
    sphere(im, 32, 32, 6, 'star')
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        d.line([(32 + math.cos(a) * 9, 32 + math.sin(a) * 9), (32 + math.cos(a) * 16, 32 + math.sin(a) * 16)], fill=pick('cloth', .95), width=1)
    outline(im)
    return im


def starshell():
    """A burning star hanging under a small parachute, lighting the night."""
    im = new(64, 64)
    glow(im, 32, 42, 22, 'star', .6)
    d = ImageDraw.Draw(im)
    d.pieslice((14, 2, 50, 30), 180, 360, fill=pick('cloth', .7))
    d.pieslice((18, 5, 46, 27), 180, 360, fill=pick('cloth', .9))
    for x in (16, 32, 48):
        d.line([(x, 16), (32, 38)], fill=pick('cloth', .5), width=1)
    pts = []
    for i in range(10):
        a = i * math.pi / 5 - math.pi / 2
        r = 12 if i % 2 == 0 else 5
        pts.append((32 + math.cos(a) * r, 44 + math.sin(a) * r))
    d.polygon(pts, fill=pick('star', .75))
    sphere(im, 32, 44, 4, 'star')
    for (x, y, s) in ((8, 40, 3), (56, 38, 3), (14, 58, 2), (50, 58, 2)):
        spark(im, x, y, 'star', s)
    outline(im)
    return im


ICONS = {'explosion': explosion, 'arrow': arrow, 'narukami': narukami, 'bunker': bunker, 'heal': heal, 'curse': curse,
         'cluster': cluster, 'cryo': cryo, 'napalm': napalm, 'gravity': gravity, 'shockwave': shockwave, 'starshell': starshell}

if __name__ == '__main__':
    # Writes only the icons named on the command line (all twelve without names): make_sprites.py rewrites much more than icons.
    import sys, pathlib
    out = pathlib.Path(__file__).resolve().parents[2] / 'custom-mods/arsenal-beacon/src/main/resources/assets/arsenal_beacon/textures/gui/fire_support'
    for name in sys.argv[1:] or list(ICONS):
        im = ICONS[name](); im.save(out / f'{name}.png')
        for px in (32, 16): im.resize((px, px), Image.BOX).save(out / f'{name}_{px}.png')
        print('icon', name)
