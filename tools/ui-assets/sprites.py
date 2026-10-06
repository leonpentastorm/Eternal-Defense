"""Item sprites (64x64) in the painted style of industrial_atlas.png."""
from artlib import *

def energy_cell():
    """Ardent Energy: a faceted ember crystal in a brass cradle."""
    im = new(64, 64)
    d = ImageDraw.Draw(im)
    R = RAMPS['ember']
    # crystal facets: crown (top) lighter, pavilion (bottom) darker, a bright centre facet
    d.polygon([(32, 5), (14, 22), (24, 22)], fill=R[3])
    d.polygon([(32, 5), (24, 22), (40, 22)], fill=R[5])
    d.polygon([(32, 5), (40, 22), (50, 22)], fill=R[4])
    d.polygon([(14, 22), (24, 22), (32, 55)], fill=R[2])
    d.polygon([(24, 22), (40, 22), (32, 55)], fill=R[4])
    d.polygon([(40, 22), (50, 22), (32, 55)], fill=R[1])
    # inner glow
    for y in range(24, 44):
        for x in range(25, 40):
            dd = math.hypot(x - 32, (y - 33) * .8) / 8
            if dd < 1 and im.getpixel((x, y))[3]: im.putpixel((x, y), pick('ember', .7 + .3 * (1 - dd)))
    # facet edge lines
    for (a, b) in (((14, 22), (50, 22)), ((24, 22), (32, 55)), ((40, 22), (32, 55)), ((32, 5), (24, 22)), ((32, 5), (40, 22))):
        d.line([a, b], fill=R[0] if a[1] != 22 or b[1] != 22 else R[1], width=1)
    # brass girdle ring with claws
    for y in range(20, 25):
        for x in range(12, 53):
            if im.getpixel((x, y))[3]: im.putpixel((x, y), pick('brass', {20: .95, 21: .7, 22: .5, 23: .3, 24: .1}[y]))
    for cx in (15, 26, 38, 49):
        for y in range(25, 31):
            if im.getpixel((cx, y))[3]: im.putpixel((cx, y), pick('brass', .8 - (y - 25) * .1))
    rivet(im, 18, 21); rivet(im, 44, 21)
    spark(im, 25, 13, 'ember', 4)
    d.point((30, 36), fill=pick('ember', .999)); d.point((31, 36), fill=pick('ember', .999))
    outline(im); return im

def flare(kind):
    im = new(64, 64)
    body = {'support': 'cyan', 'return': 'purple', 'fire': 'red'}[kind]
    flame = {'support': 'cyan', 'return': 'purple', 'fire': 'ember'}[kind]
    vcylinder(im, 20, 44, 24, 53, body)
    for y in range(24, 54, 3):
        for x in (23, 41): im.putpixel((x, y), pick(body, .15))   # worn paint ticks
    # paper label band
    vcylinder(im, 20, 44, 31, 44, 'cloth')
    stripe = {'support': 'cyan', 'return': 'purple', 'fire': 'red'}[kind]
    for y in (32, 43):
        for x in range(20, 45):
            u = (x - 32) / 12.5; im.putpixel((x, y), pick(stripe, light(u, 0, math.sqrt(max(0, 1 - u*u)))))
    # glyph on the label
    g = {'support': ["..XX..", ".XXXX.", "XX..XX", "..XX..", "..XX..", "..XX.."],      # arrow down: delivery
         'return':  ["..XX..", ".XXXX.", "XXXXXX", ".X..X.", ".X..X.", ".XXXX."],      # house: home
         'fire':    ["..X...", ".XX.X.", ".XXXX.", "XXXXXX", "XXXXXX", ".XXXX."]}[kind]  # flame
    for j, row in enumerate(g):
        for i, c in enumerate(row):
            if c == 'X': im.putpixel((29 + i, 35 + j), pick(stripe, .25))
    # brass caps
    vcylinder(im, 19, 45, 53, 58, 'brass'); vcylinder(im, 19, 45, 18, 23, 'brass')
    for x in range(19, 46): im.putpixel((x, 23), pick('brass', .3)); im.putpixel((x, 58), pick('brass', .08))
    # fuse nub + flame
    vcylinder(im, 29, 35, 12, 18, 'steel')
    R = RAMPS[flame]
    flame_shape = [(32, 3), (28, 8), (26, 11), (38, 11), (36, 8)]
    d = ImageDraw.Draw(im); d.polygon([(32, 2), (27, 9), (29, 12), (35, 12), (37, 9)], fill=R[3])
    d.polygon([(32, 4), (29, 9), (30, 12), (34, 12), (35, 9)], fill=R[4]); d.polygon([(32, 7), (31, 11), (33, 11)], fill=R[5])
    spark(im, 22, 8, flame, 3); spark(im, 43, 5, flame, 2)
    rivet(im, 22, 55); rivet(im, 40, 55); rivet(im, 22, 19); rivet(im, 40, 19)
    outline(im); return im

SPRITES = {'ardent_energy': energy_cell, 'support_flare': lambda: flare('support'), 'return_flare': lambda: flare('return'), 'fire_support_flare': lambda: flare('fire')}

def portal_sheet():
    """Return Flare portal: four 16 x 32 frames side by side, a spinning violet vortex in a bright rim, drawn 1 block wide and 2 tall."""
    W, H = 16, 32
    sheet = new(W * 4, H)
    for f in range(4):
        phase = f * math.pi / 2
        for y in range(H):
            for x in range(W):
                u = (x - 7.5) / 7.8; v = (y - 15.5) / 15.8
                r = math.hypot(u, v)
                if r > 1.0: continue
                ang = math.atan2(v * .5, u)
                if r > .84:                                          # glowing rim
                    shade = .55 + .45 * (1 - (r - .84) / .16)
                    c = pick('purple', .62 + .38 * shade * (.7 + .3 * math.sin(ang * 3 + phase)))
                    if r > .95: c = pick('purple', .25)
                    sheet.putpixel((f * W + x, y), c[:3] + (255,)); continue
                swirl = math.sin(ang * 3 + r * 7 - phase * 1.5)       # spiral arms that turn from frame to frame
                core = max(0, 1 - r * 1.6)
                val = .28 + .3 * (swirl + 1) / 2 + .5 * core
                c = pick('purple', min(.999, val))
                sheet.putpixel((f * W + x, y), c[:3] + (232,))
        for (sx, sy) in (((1, 6 + f * 3), (14, 20 - f * 3))):
            sheet.putpixel((f * W + sx, sy), pick('purple', .95))
    return sheet
