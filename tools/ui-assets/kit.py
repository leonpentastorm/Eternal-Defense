"""512x512 tile kit: sixty-four 64x64 tiles painted in the pack's own look (charcoal brushed metal, gold plates, cyan glow, hazard
stripes - the same palette as defense_beacon_level4.png), shared by the exchange shop, support platform, cannon and parcel models.
A model face of up to 16 units uses one tile at 4 texels per unit."""
import math
from artlib import *

T = 64
RAMPS['char'] = [hx(c) for c in ('#0f1114', '#17191c', '#1f2226', '#272b30', '#2f343a', '#393e45', '#454b53', '#59606a')]
RAMPS['gold'] = [hx(c) for c in ('#4a3512', '#6e5320', '#8f6e2b', '#b08a35', '#c3963f', '#d9b15a', '#ecca7d', '#fbe6ae')]
RAMPS['glow'] = [hx(c) for c in ('#06222b', '#0a4a5a', '#1494b0', '#24c4e8', '#3fe3ff', '#8df0ff', '#d2fbff', '#ffffff')]
RAMPS['ledred'] = [hx(c) for c in ('#3a0808', '#7a1414', '#c42a2a', '#ff4a44', '#ff8a80', '#ffd6d0')]
RAMPS['pale'] = [hx(c) for c in ('#6a737c', '#8c969f', '#aeb8c0', '#cfd7dd', '#e8edf0', '#ffffff')]

def hsh(x, y, s=0):
    n = (x * 374761393 + y * 668265263 + s * 2147483647) & 0xffffffff
    n = ((n ^ (n >> 13)) * 1274126177) & 0xffffffff
    return ((n ^ (n >> 16)) & 0xffff) / 65535.0

def tile(): return new(T, T)
def px(im, x, y, ramp, v):
    if 0 <= x < T and 0 <= y < T: im.putpixel((x, y), pick(ramp, v))

def bevel(im, ramp, w=2, hi=.92, lo=.12):
    for k in range(w):
        for i in range(T):
            px(im, i, k, ramp, hi - k * .12); px(im, k, i, ramp, hi - k * .12)
            px(im, i, T - 1 - k, ramp, lo + k * .1); px(im, T - 1 - k, i, ramp, lo + k * .1)

def bolt(im, x, y, ramp='char', big=False):
    s = 3 if big else 2
    for dx in range(s):
        for dy in range(s):
            v = .85 if (dx == 0 and dy == 0) else .15 if (dx == s - 1 and dy == s - 1) else .5
            px(im, x + dx, y + dy, ramp, v)

def brushed(ramp, base, spread=.10, streak=.05, seed=1):
    im = tile()
    for y in range(T):
        row = (hsh(0, y, seed) - .5) * streak * 2
        for x in range(T):
            n = (hsh(x // 3, y, seed + 7) - .5) * spread
            px(im, x, y, ramp, base + row + n)
    return im

def plate(base=.5, seed=1, ramp='char', bolts=True):
    im = brushed(ramp, base, seed=seed)
    bevel(im, ramp, 1, base + .22, base - .22)
    if bolts:
        for (x, y) in ((4, 4), (T - 7, 4), (4, T - 7), (T - 7, T - 7)): bolt(im, x, y, ramp)
    return im

def perforated():
    im = brushed('char', .34, spread=.06, seed=3)
    for y in range(4, T - 4, 4):
        for x in range(4, T - 4, 4):
            px(im, x, y, 'char', .16); px(im, x + 1, y + 1, 'char', .62)
    bevel(im, 'char', 2, .6, .1)
    return im

def gold_plate(base=.62, seed=2):
    im = brushed('gold', base, spread=.05, streak=.03, seed=seed)
    bevel(im, 'gold', 3, base + .3, base - .38)
    for k in range(6, T - 6):                          # engraved inner frame
        px(im, k, 7, 'gold', base - .22); px(im, k, T - 8, 'gold', base + .12); px(im, 7, k, 'gold', base - .22); px(im, T - 8, k, 'gold', base + .12)
    for (x, y) in ((10, 10), (T - 13, 10), (10, T - 13), (T - 13, T - 13)): bolt(im, x, y, 'gold')
    return im

def cyan_stripes():
    im = tile()
    for x in range(T):
        col = x // 16; u = (x % 16) / 15.0
        for y in range(T):
            v = .35 + .5 * (1 - abs(u - .5) * 2) + (.05 if y % 4 == 0 else 0)
            px(im, x, y, 'glow', v if col % 2 == 0 else v * .8)
    for x in (15, 31, 47): 
        for y in range(T): px(im, x, y, 'glow', .3)
    return im

def hazard():
    im = tile()
    for y in range(T):
        for x in range(T):
            stripe = ((x + y) // 8) % 2 == 0
            px(im, x, y, 'gold' if stripe else 'char', .72 + (hsh(x, y, 5) - .5) * .12 if stripe else .12)
    for k in range(T): px(im, k, 0, 'char', .5); px(im, k, T - 1, 'char', .04); px(im, 0, k, 'char', .5); px(im, T - 1, k, 'char', .04)
    return im

def vents():
    im = tile()
    for y in range(T):
        for x in range(T):
            slat = x % 8
            px(im, x, y, 'char', .55 if slat == 0 else .42 if slat == 1 else .06 if slat in (3, 4, 5) else .26)
    bevel(im, 'char', 2, .6, .08)
    return im

FONT = {
 'A': ('.X.', 'X.X', 'XXX', 'X.X', 'X.X'), 'B': ('XX.', 'X.X', 'XX.', 'X.X', 'XX.'), 'C': ('.XX', 'X..', 'X..', 'X..', '.XX'),
 'D': ('XX.', 'X.X', 'X.X', 'X.X', 'XX.'), 'E': ('XXX', 'X..', 'XX.', 'X..', 'XXX'), 'F': ('XXX', 'X..', 'XX.', 'X..', 'X..'),
 'G': ('.XX', 'X..', 'X.X', 'X.X', '.XX'), 'H': ('X.X', 'X.X', 'XXX', 'X.X', 'X.X'), 'I': ('XXX', '.X.', '.X.', '.X.', 'XXX'),
 'K': ('X.X', 'X.X', 'XX.', 'X.X', 'X.X'), 'L': ('X..', 'X..', 'X..', 'X..', 'XXX'), 'M': ('X.X', 'XXX', 'XXX', 'X.X', 'X.X'),
 'N': ('XX.', 'X.X', 'X.X', 'X.X', 'X.X'), 'O': ('.X.', 'X.X', 'X.X', 'X.X', '.X.'), 'P': ('XX.', 'X.X', 'XX.', 'X..', 'X..'),
 'R': ('XX.', 'X.X', 'XX.', 'X.X', 'X.X'), 'S': ('.XX', 'X..', '.X.', '..X', 'XX.'), 'T': ('XXX', '.X.', '.X.', '.X.', '.X.'),
 'U': ('X.X', 'X.X', 'X.X', 'X.X', 'XXX'), 'X': ('X.X', 'X.X', '.X.', 'X.X', 'X.X'), 'Y': ('X.X', 'X.X', '.X.', '.X.', '.X.'),
 'W': ('X.X', 'X.X', 'XXX', 'XXX', 'X.X'), 'V': ('X.X', 'X.X', 'X.X', 'X.X', '.X.'), 'Z': ('XXX', '..X', '.X.', 'X..', 'XXX'),
 '-': ('...', '...', 'XXX', '...', '...'), '1': ('.X.', 'XX.', '.X.', '.X.', 'XXX'), '2': ('XX.', '..X', '.X.', 'X..', 'XXX'),
 '3': ('XX.', '..X', '.X.', '..X', 'XX.'), '4': ('X.X', 'X.X', 'XXX', '..X', '..X'), ' ': ('...', '...', '...', '...', '...')}

def text(im, s, x, y, ramp, v, scale=2):
    for ch in s:
        for j, row in enumerate(FONT[ch]):
            for i, c in enumerate(row):
                if c == 'X':
                    for dx in range(scale):
                        for dy in range(scale): px(im, x + i * scale + dx, y + j * scale + dy, ramp, v - (.12 if dy == scale - 1 else 0))
        x += 3 * scale + 1
    return x

def text_width(s, scale=2): return len(s) * (3 * scale + 1) - 1

FULL = {   # tiles whose face is mapped whole (not at 4 texels per unit); value = authored texel size, matching the face they are used on
    'screen_support': (64, 42), 'screen_exchange': (64, 37), 'screen_fire': (64, 26), 'mk1': (64, 46), 'mk2': (64, 46), 'mk3': (64, 46), 'mk4': (64, 46),
    'sign_cannon': (64, 16), 'sign_ardent': (64, 32), 'sign_support': (64, 16), 'sign_exchange': (64, 16), 'gauge': (64, 64), 'crate': (64, 64),
    'grid1': (64, 64), 'grid2': (64, 64), 'grid3': (64, 64), 'grid4': (64, 64), 'core': (64, 64), 'led': (64, 64)}

def text_xy(im, s, x, y, ramp, v, sx, sy):
    for ch in s:
        for j, row in enumerate(FONT[ch]):
            for i, c in enumerate(row):
                if c == 'X':
                    for dx in range(sx):
                        for dy in range(sy): px(im, x + i * sx + dx, y + j * sy + dy, ramp, v - (.12 if dy == sy - 1 else 0))
        x += 3 * sx + 1
    return x

def screen(label, w, h, sx=2, sy=3, bars=True):
    im = tile()
    for y in range(h):
        for x in range(w): px(im, x, y, 'glow', .02 + (.03 if y % 2 == 0 else 0))
    for k in range(w):
        for e in (0, 1): px(im, k, e, 'glow', .55); px(im, k, h - 1 - e, 'glow', .18)
    for k in range(h):
        for e in (0, 1): px(im, e, k, 'glow', .55); px(im, w - 1 - e, k, 'glow', .18)
    tw = len(label) * (3 * sx + 1) - 1; ty = 5 if bars else (h - 5 * sy) // 2
    text_xy(im, label, (w - tw) // 2, ty, 'glow', .95, sx, sy)
    if bars:
        for j, (ww, v) in enumerate(((int(w * .62), .75), (int(w * .4), .6))):
            for x in range(6, 6 + ww):
                for y in (h - 14 + j * 6, h - 13 + j * 6): px(im, x, y, 'glow', v - (.22 if y % 2 else 0))
    return im

def mk_screen(n, w=64, h=46):
    im = tile()
    for y in range(h):
        for x in range(w): px(im, x, y, 'glow', .02)
    for k in range(w):
        for e in (0, 1): px(im, k, e, 'glow', .55); px(im, k, h - 1 - e, 'glow', .18)
    for k in range(h):
        for e in (0, 1): px(im, e, k, 'glow', .55); px(im, w - 1 - e, k, 'glow', .18)
    s = f'MK-{n}'; sx, sy = 3, 4; tw = len(s) * (3 * sx + 1) - 1; text_xy(im, s, (w - tw) // 2, 7, 'glow', .95, sx, sy)
    for i in range(n):
        for x in range(8 + i * 13, 18 + i * 13):
            for y in range(h - 11, h - 6): px(im, x, y, 'glow', .85)
    return im

def sign(label, w, h, sx, sy, bg='gold'):
    base = gold_plate(.6, seed=9) if bg == 'gold' else plate(.3, seed=9)
    im = tile(); im.paste(base.crop((0, 0, w, h)), (0, 0))
    tw = len(label) * (3 * sx + 1) - 1; text_xy(im, label, (w - tw) // 2, (h - 5 * sy) // 2, 'char' if bg == 'gold' else 'gold', .12 if bg == 'gold' else .75, sx, sy)
    return im

def led_red():
    im = tile()
    for y in range(T):
        for x in range(T): px(im, x, y, 'ledred', .55 + .25 * (1 - math.hypot(x - 22, y - 22) / 60) - (.2 if x > 44 or y > 44 else 0))
    bevel(im, 'ledred', 3, .98, .15)
    for x in range(10, 20):
        for y in range(10, 14): px(im, x, y, 'ledred', .999)
    return im

def pipe(ramp='gold'):
    im = tile()
    for y in range(T):
        u = (y - 31.5) / 32.0; nz = math.sqrt(max(0, 1 - u * u)); v = light(0, u, nz)
        for x in range(T): px(im, x, y, ramp, v + (hsh(x // 2, y, 4) - .5) * .06)
    for x in (0, 1, 30, 31, 32, 33, 62, 63):
        for y in range(T): im.putpixel((x, y), pick('char', .08 + .25 * (1 - abs(y - 31.5) / 32)))
    return im

def gauge():
    im = plate(.38, seed=4, bolts=False)
    cx = cy = 31.5
    for y in range(T):
        for x in range(T):
            d = math.hypot(x - cx, y - cy)
            if d < 25: px(im, x, y, 'char', .12 + (.12 if d > 22 else 0))
            if 25 <= d < 27: px(im, x, y, 'gold', .8)
    for a in range(-130, 131, 26):
        px(im, int(cx + 20 * math.sin(math.radians(a))), int(cy - 20 * math.cos(math.radians(a))), 'glow', .9)
        px(im, int(cx + 19 * math.sin(math.radians(a))), int(cy - 19 * math.cos(math.radians(a))), 'glow', .6)
    for k in range(0, 20):
        px(im, int(cx + k * .55), int(cy - k * .8), 'ledred', .75)
    for dx in range(-2, 3):
        for dy in range(-2, 3): px(im, int(cx) + dx, int(cy) + dy, 'gold', .8 - .1 * max(abs(dx), abs(dy)))
    return im

def grid_pad(n):
    im = brushed('char', .22, spread=.04, seed=11)
    gap = 3 if n <= 4 else 2; margin = 5; size = (T - 2 * margin - (n - 1) * gap) // n
    start = (T - (n * size + (n - 1) * gap)) // 2
    for i in range(n):
        for j in range(n):
            x0 = start + i * (size + gap); y0 = start + j * (size + gap)
            for y in range(size):
                for x in range(size):
                    v = .72 if (x == 0 or y == 0) else .3 if (x == size - 1 or y == size - 1) else .5 + .08 * (1 - math.hypot(x - size / 2, y - size / 2) / size)
                    px(im, x0 + x, y0 + y, 'glow', v)
            px(im, x0 + 1, y0 + 1, 'glow', .999)
    for k in range(T):
        px(im, k, 0, 'gold', .85); px(im, 0, k, 'gold', .85); px(im, k, T - 1, 'gold', .3); px(im, T - 1, k, 'gold', .3)
        px(im, k, 1, 'gold', .6); px(im, 1, k, 'gold', .6)
    return im

def crate():
    im = tile()
    for p in range(4):
        for y in range(p * 16, p * 16 + 16):
            for x in range(T):
                v = .36 + (hsh(x // 2, y, p) - .5) * .12 - (.2 if y % 16 == 15 else 0) + (.12 if y % 16 == 0 else 0)
                px(im, x, y, 'char', v)
    for x0 in (0, T - 10):                              # gold straps
        for x in range(x0, x0 + 10):
            for y in range(T): px(im, x, y, 'gold', .74 - (.3 if x in (x0 + 9,) else 0) + (.15 if x == x0 else 0))
    for (x, y) in ((3, 6), (3, T - 10), (T - 8, 6), (T - 8, T - 10)): bolt(im, x, y, 'gold')
    arrow = ('..XX..', '..XX..', '..XX..', 'XXXXXX', '.XXXX.', '..XX..')
    for j, row in enumerate(arrow):
        for i, c in enumerate(row):
            if c == 'X':
                for dx in range(3):
                    for dy in range(3): px(im, 20 + i * 4 + dx, 18 + j * 4 + dy, 'glow', .9 - dy * .08)
    return im

def cloth():
    im = tile()
    for x in range(T):
        gore = (x // 16) % 2
        for y in range(T):
            ramp = 'glow' if gore else 'pale'
            v = .62 - .15 * (y / T) + (hsh(x, y, 8) - .5) * .05 - (.35 if x % 16 == 0 else 0)
            px(im, x, y, ramp, v)
    return im

def core():
    im = tile()
    for y in range(T):
        for x in range(T): px(im, x, y, 'glow', 1 - min(1, math.hypot(x - 31.5, y - 31.5) / 36) * .9)
    bevel(im, 'glow', 2, .99, .35)
    return im

def glass():
    im = tile()
    for y in range(T):
        for x in range(T): px(im, x, y, 'glow', .12 + (.28 if (x + y) % 22 < 4 else 0) + (.1 if (x + y) % 22 < 2 else 0))
    bevel(im, 'glow', 2, .8, .22)
    return im

def dark_plate(): return plate(.26, seed=5)
def light_plate(): return plate(.62, seed=6)
def gold_dark(): return gold_plate(.4, seed=7)

TILES = ['plate', 'dark_plate', 'light_plate', 'perforated', 'gold', 'gold_dark', 'cyan', 'hazard',
         'vents', 'screen_support', 'screen_exchange', 'screen_fire', 'mk1', 'mk2', 'mk3', 'mk4',
         'grid1', 'grid2', 'grid3', 'grid4', 'crate', 'cloth', 'pipe_gold', 'pipe_steel',
         'sign_support', 'sign_exchange', 'sign_cannon', 'gauge', 'core', 'glass', 'led', 'sign_ardent']
PAINT = {'plate': lambda: plate(.5, seed=1), 'dark_plate': dark_plate, 'light_plate': light_plate, 'perforated': perforated, 'gold': gold_plate, 'gold_dark': gold_dark,
         'cyan': cyan_stripes, 'hazard': hazard, 'vents': vents, 'screen_support': lambda: screen('SUPPORT', 64, 42, 2, 3), 'screen_exchange': lambda: screen('EXCHANGE', 64, 37, 2, 3, False),
         'screen_fire': lambda: screen('READY', 64, 26, 3, 3, False), 'mk1': lambda: mk_screen(1), 'mk2': lambda: mk_screen(2), 'mk3': lambda: mk_screen(3), 'mk4': lambda: mk_screen(4),
         'grid1': lambda: grid_pad(3), 'grid2': lambda: grid_pad(4), 'grid3': lambda: grid_pad(5), 'grid4': lambda: grid_pad(6), 'crate': crate, 'cloth': cloth,
         'pipe_gold': lambda: pipe('gold'), 'pipe_steel': lambda: pipe('char'), 'sign_support': lambda: sign('SUPPORT', 64, 16, 2, 2), 'sign_exchange': lambda: sign('EXCHANGE', 64, 16, 2, 2),
         'sign_cannon': lambda: sign('CANNON', 64, 16, 2, 2), 'gauge': gauge, 'core': core, 'glass': glass, 'led': led_red, 'sign_ardent': lambda: sign('ARDENT', 64, 32, 2, 4, 'dark')}

def build_kit():
    kit = new(512, 512)
    for i, name in enumerate(TILES): kit.paste(PAINT[name](), ((i % 8) * T, (i // 8) * T))
    return kit
