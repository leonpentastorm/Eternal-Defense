"""128x128 tile kit: sixteen 32x32 painted tiles shared by the exchange shop, support platform, cannon and parcel models."""
from artlib import *

T = 32
def tile():
    return new(T, T)

def panel(ramp, seam=False, rivets=True, base=.58):
    im = tile()
    for y in range(T):
        for x in range(T):
            v = base - .14 * (y / T) + (.03 if (x * 7 + y * 3) % 11 == 0 else 0)
            if y <= 1 or x <= 1: v = .92 if (y == 0 or x == 0) else .74
            elif y >= T - 2 or x >= T - 2: v = .12 if (y == T - 1 or x == T - 1) else .26
            im.putpixel((x, y), pick(ramp, v))
    if seam:
        for y in range(2, T - 2): im.putpixel((T // 2, y), pick(ramp, .1)); im.putpixel((T // 2 + 1, y), pick(ramp, .85))
    if rivets:
        for (x, y) in ((4, 4), (T - 6, 4), (4, T - 6), (T - 6, T - 6)): rivet(im, x, y, 'brass' if ramp != 'brass' else 'steel')
    return im

def steel_plate(): return panel('steel')
def steel_dark(): return panel('dark', seam=True, base=.7)
def brass_plate(): return panel('brass', rivets=True, base=.62)

def copper_pipe():
    im = tile(); hcylinder(im, 0, T - 1, 0, T - 1, 'copper')
    for x in (0, 1, T // 2 - 1, T // 2, T - 2, T - 1):                    # clamp rings
        for y in range(T): im.putpixel((x, y), pick('brass', .25 + .6 * (1 - abs(y - T / 2) / (T / 2)) * (1 if y < T / 2 else .5)))
    return im

def cyan_screen():
    im = tile()
    for y in range(T):
        for x in range(T): im.putpixel((x, y), pick('dark', .22 + (.06 if y % 2 == 0 else 0)))
    for i in range(0, T, 6):
        for k in range(T): im.putpixel((i, k), pick('cyan', .15)); im.putpixel((k, i), pick('cyan', .15))
    for j, (w, v) in enumerate(((20, .75), (14, .6), (24, .85), (9, .55))):
        for x in range(4, 4 + w):
            for y in (5 + j * 6, 6 + j * 6): im.putpixel((x, y), pick('cyan', v - (.25 if y % 2 else 0)))
    for k in range(T):
        for e in (0, 1): im.putpixel((k, e), pick('cyan', .5)); im.putpixel((k, T - 1 - e), pick('cyan', .2)); im.putpixel((e, k), pick('cyan', .5)); im.putpixel((T - 1 - e, k), pick('cyan', .2))
    return im

def amber_window():
    im = tile()
    for y in range(T):
        for x in range(T):
            d = math.hypot((x - 15.5) / 15, (y - 15.5) / 15); im.putpixel((x, y), pick('ember', .85 - .7 * min(1, d)))
    d = ImageDraw.Draw(im); R = RAMPS['ember']
    d.polygon([(16, 7), (9, 14), (23, 14)], fill=R[5]); d.polygon([(9, 14), (23, 14), (16, 26)], fill=R[4])   # energy cell silhouette
    d.line([(9, 14), (23, 14)], fill=RAMPS['brass'][4])
    for k in range(T):
        for e in range(3): 
            v = .5 - e * .17
            for (x, y) in ((k, e), (k, T - 1 - e), (e, k), (T - 1 - e, k)): im.putpixel((x, y), pick('dark', v))
    return im

def hazard():
    im = tile()
    for y in range(T):
        for x in range(T): im.putpixel((x, y), pick('brass', .78) if ((x + y) // 5) % 2 == 0 else pick('dark', .1))
    for k in range(T): im.putpixel((k, 0), pick('steel', .8)); im.putpixel((k, T - 1), pick('dark', 0))
    return im

def vents():
    im = tile()
    for y in range(T):
        for x in range(T):
            slat = y % 5
            im.putpixel((x, y), pick('dark', .9 if slat == 0 else .5 if slat == 1 else .08))
    for k in range(T): im.putpixel((0, k), pick('steel', .7)); im.putpixel((T - 1, k), pick('dark', .02))
    return im

def grid_pad(n):
    im = tile()
    for y in range(T):
        for x in range(T): im.putpixel((x, y), pick('steel', .3 + (.05 if (x + y) % 8 == 0 else 0)))
    for k in range(T): im.putpixel((k, 0), pick('brass', .8)); im.putpixel((0, k), pick('brass', .8)); im.putpixel((k, T - 1), pick('brass', .2)); im.putpixel((T - 1, k), pick('brass', .2))
    gap = 2 if n <= 4 else 1; margin = 3; size = (T - 2 * margin - (n - 1) * gap) // n
    start = (T - (n * size + (n - 1) * gap)) // 2
    for i in range(n):
        for j in range(n):
            x0 = start + i * (size + gap); y0 = start + j * (size + gap)
            for y in range(size):
                for x in range(size):
                    v = .78 if (x == 0 or y == 0) else .38 if (x == size - 1 or y == size - 1) else .6
                    im.putpixel((x0 + x, y0 + y), pick('cyan', v))
            if size >= 3: im.putpixel((x0 + 1, y0 + 1), pick('cyan', .999))
    return im

def gauge():
    im = panel('steel', rivets=False, base=.5); d = ImageDraw.Draw(im)
    d.ellipse([5, 5, 26, 26], fill=RAMPS['dark'][0]); d.ellipse([7, 7, 24, 24], fill=RAMPS['dark'][2])
    for a in range(-120, 121, 30):
        x = 15.5 + 8.5 * math.sin(math.radians(a)); y = 15.5 - 8.5 * math.cos(math.radians(a)); im.putpixel((int(x), int(y)), pick('cyan', .8))
    d.line([(15, 16), (21, 10)], fill=RAMPS['brass'][4]); im.putpixel((15, 16), pick('brass', .2)); im.putpixel((16, 16), pick('brass', .2))
    for k in range(5, 27): im.putpixel((k, 5), pick('steel', .85)); im.putpixel((5, k), pick('steel', .85))
    return im

def crate():
    im = tile()
    for p in range(4):
        for y in range(p * 8, p * 8 + 8):
            for x in range(T):
                v = .55 + .12 * math.sin(x * .7 + p * 2) - (.4 if y % 8 == 7 else 0) + (.2 if y % 8 == 0 else 0)
                im.putpixel((x, y), pick('wood', v))
    for (x0, x1) in ((0, 4), (T - 5, T - 1)):
        for x in range(x0, x1 + 1):
            for y in range(T): im.putpixel((x, y), pick('brass', .7 if x in (x0, ) else .5 if x != x1 else .25))
    glyph = ["...XX...", "...XX...", "...XX...", ".XXXXXX.", "..XXXX..", "...XX..."]       # arrow down: delivery
    for j, row in enumerate(glyph):
        for i, c in enumerate(row):
            if c == 'X':
                for dx in (0, 1):
                    for dy in (0, 1): im.putpixel((8 + i * 2 + dx, 7 + j * 2 + dy), pick('cyan', .9))
    return im

def ember_core():
    im = tile()
    for y in range(T):
        for x in range(T):
            d = math.hypot(x - 15.5, y - 15.5) / 18; im.putpixel((x, y), pick('ember', 1 - min(1, d) * .85))
    return im

def cloth():
    im = tile()
    for y in range(T):
        for x in range(T):
            gore = (x // 8) % 2; edge = x % 8 in (0,)
            ramp = 'cyan' if gore else 'cloth'; v = .7 - .2 * (y / T) + (-.35 if edge else 0)
            im.putpixel((x, y), pick(ramp, v))
    return im

TILES = ['steel_plate', 'steel_dark', 'brass_plate', 'copper_pipe', 'cyan_screen', 'amber_window', 'hazard', 'vents',
         'grid1', 'grid2', 'grid3', 'grid4', 'gauge', 'crate', 'ember_core', 'cloth']
PAINT = {'steel_plate': steel_plate, 'steel_dark': steel_dark, 'brass_plate': brass_plate, 'copper_pipe': copper_pipe, 'cyan_screen': cyan_screen,
         'amber_window': amber_window, 'hazard': hazard, 'vents': vents, 'grid1': lambda: grid_pad(3), 'grid2': lambda: grid_pad(4),
         'grid3': lambda: grid_pad(5), 'grid4': lambda: grid_pad(6), 'gauge': gauge, 'crate': crate, 'ember_core': ember_core, 'cloth': cloth}

def build_kit():
    kit = new(128, 128)
    for i, name in enumerate(TILES): kit.paste(PAINT[name](), ((i % 4) * T, (i // 4) * T))
    return kit
