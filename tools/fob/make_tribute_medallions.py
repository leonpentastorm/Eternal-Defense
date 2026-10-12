#!/usr/bin/env python3
"""Draws the 0.0.32 tribute icons as round medallions (32 x 32, our own pixel art, not the owner's): inside a wooden ring, a villager
doing the job the icon stands for, in the manner of Minecraft's wall paintings (a muted earthy palette, grainy flat backgrounds, chunky
shapes). The screens draw them at 16 x 16 GUI pixels, so they show every pixel from GUI scale 2 up.

  slot_emerald    an unemployed villager holds up an emerald (the tribute in emeralds)
  slot_mine       a toolsmith swings a pickaxe at iron ore
  slot_farm       a farmer in a straw hat with a hoe, wheat in front
  slot_specialty  a nitwit holds up a gold star (not shown: the specialty slot shows the village's specialty)
  spec_fishing    a fisherman's rod and line over the water, a fish on the hook
  spec_husbandry  a shepherd with shears beside a sheep
  spec_potions    a cleric holds up a potion in a dark brewing room
  spec_trader     a wandering trader in his blue hood holds out an emerald, a llama beside
  spec_monsters   a hunter with an eye patch and a sword at night, a creeper's head below
  research        a librarian reads a book in front of a bookshelf
  lock            the padlock of make_tribute_icons.py, doubled to 32 x 32 (not a job, so no medallion)

The flat 16 x 16 icons of the first 0.0.32 draft are kept in textures/gui/tribute/flat (tools/fob/make_tribute_icons.py).
Usage: python3 tools/fob/make_tribute_medallions.py <textures/gui/tribute directory>"""
import math, os, sys
from PIL import Image

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
N = 32
CX = CY = 15.5
R_OUT, R_FRAME, R_IN = 15.6, 14.6, 12.7


def c(h):
    h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def shade(col, f):
    return tuple(max(0, min(255, int(v * f))) for v in col[:3]) + (255,)


def grain(x, y, seed):
    """A fixed 'random' number 0..1 for a pixel, so the painting grain is the same every run."""
    v = (x * 73856093 ^ y * 19349663 ^ seed * 83492791) & 0xffffffff
    v = (v ^ (v >> 13)) * 1274126177 & 0xffffffff
    return ((v ^ (v >> 16)) & 0xffff) / 0xffff


# the palette: muted, earthy, as the paintings are
K = c('#1e140d')          # outline
PAL = {
    'k': K,
    's': c('#b98468'), 'h': c('#cf9c7f'), 'S': c('#94634d'), 'n': c('#94604a'), 'N': c('#6e4434'),  # villager skin, light, shadow, nose
    'b': c('#3b2618'),                                                                                # brow
    'e': c('#e8e2d4'), 'i': c('#2f6b3a'),                                                             # eye white, green iris
    'W': c('#e8e2d4'), 'w': c('#b8ae9c'),                                                             # white cloth, its shadow
    'Y': c('#d9b55c'), 'y': c('#a2803a'), 'u': c('#7a5c26'),                                          # straw
    'G': c('#47c27a'), 'g': c('#23874c'), 'L': c('#a8f0c4'), 'd': c('#135c32'),                       # emerald
    'I': c('#c9c3b8'), 'J': c('#8c8780'), 'j': c('#5c5852'),                                          # iron
    'o': c('#7a5434'), 'O': c('#9c7048'), 'q': c('#4d331f'),                                          # wood
    'P': c('#8d5bb8'), 'p': c('#5e3a85'), 'Q': c('#c9a6ea'),                                          # cleric purple, potion light
    'A': c('#e6b93a'), 'a': c('#a77b1c'),                                                             # gold
    'B': c('#3d6fb0'), 'v': c('#284c80'), 'x': c('#6f9bd6'),                                          # trader blue
    'C': c('#5aa83e'), 'c': c('#2f6b22'), 'z': c('#0f240b'),                                          # creeper green, dark, face
    'R': c('#a8423a'), 'r': c('#6f2a24'),                                                             # red
    'F': c('#5f86a8'), 'f': c('#c9874a'),                                                             # fish blue, fish orange
    'm': c('#6b4a33'), 'M': c('#4f3523'),                                                             # brown robe, its shadow
    'l': c('#c8b89a'), 'T': c('#a8956f'),                                                             # llama cream, its shadow
    'X': c('#d99a9a'),                                                                                # pink
    'E': c('#3c3c44'), 'e2': c('#26262c'),                                                            # apron black
}

# ---- backgrounds: a sky (or a wall) over a ground, with grain ---------------------------------------------------------------------------------


def bands(spec, seed):
    """spec: list of (until_row, base colour hex, grain strength). Returns a 32 x 32 grid of colours."""
    grid = [[None] * N for _ in range(N)]
    for y in range(N):
        for until, col, g in spec:
            if y < until:
                base = c(col); break
        for x in range(N):
            r = grain(x, y, seed)
            f = 1.0 + (g if r > .82 else -g if r < .18 else 0)
            grid[y][x] = shade(base, f)
    return grid


def put(grid, art, ox, oy):
    rows = art.strip('\n').split('\n')
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in ' .':
                continue
            X, Y = ox + x, oy + y
            if 0 <= X < N and 0 <= Y < N:
                grid[Y][X] = PAL['e2'] if ch == '%' else PAL[ch]


# ---- the villager ------------------------------------------------------------------------------------------------------------------------------
# A bust: the head (8 wide), the long nose, the robe, the folded arms with the hands in the middle. Robe letters are swapped per job:
# '1' robe, '2' robe shadow, '3' sleeve, '4' trim/apron. hx, hy = the head's top-left corner.
VILLAGER = """
..khhhhhhhhk..
..khssssssSk..
..khssssssSk..
..kbbbbbbbbk..
..keisnnsiek..
..khssnnssSk..
..khssnnssSk..
..kSSSnnSSSk..
.kkkkknNkkkkk.
k1111knNk2222k
k11111kk12222k
k333333333333k
k3333hssh3333k
k333333333333k
k111111122222k
k111111122222k
k111111122222k
k111111122222k
"""


def villager(grid, hx, hy, robe, shadow, sleeve, trim=None, eye_patch=False):
    rows = VILLAGER.strip('\n').split('\n')
    m = {'1': robe, '2': shadow, '3': sleeve, '4': trim or robe}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == '.':
                continue
            X, Y = hx - 2 + x, hy + y
            if not (0 <= X < N and 0 <= Y < N):
                continue
            col = m[ch] if ch in m else PAL[ch]
            if isinstance(col, str):
                col = c(col)
            grid[Y][X] = col
    if eye_patch:  # a black patch over the left eye, its strap up across the forehead
        for (x, y) in [(1, 4), (2, 4), (3, 4), (1, 5), (2, 5), (3, 2), (4, 1), (5, 0)]:
            grid[hy + y][hx + x] = PAL['k']


def line(grid, x0, y0, x1, y1, col, edge=None):
    """A one pixel line (a rod, a handle) with an optional dark edge under it."""
    n = max(abs(x1 - x0), abs(y1 - y0))
    for t in range(n + 1):
        x = round(x0 + (x1 - x0) * t / n); y = round(y0 + (y1 - y0) * t / n)
        if edge is not None and 0 <= y + 1 < N:
            grid[y + 1][x] = edge
        grid[y][x] = col


def frame_and_clip(grid, seed):
    img = Image.new('RGBA', (N, N), (0, 0, 0, 0)); px = img.load()
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - CX, y - CY)
            if d > R_OUT:
                continue
            if d > R_FRAME:
                px[x, y] = K
            elif d > R_IN:
                # the ring: oak, lit from the top left, a darker grain
                light = ((x - CX) + (y - CY)) / (2 * R_FRAME)
                base = PAL['O'] if light < -.25 else PAL['q'] if light > .3 else PAL['o']
                r = grain(x, y, seed + 7)
                px[x, y] = shade(base, .86) if r < .2 else base
            elif d > R_IN - 1:
                px[x, y] = shade(grid[y][x], .62)    # a thin shadow inside the ring
            else:
                px[x, y] = grid[y][x]
    return img


# ---- the icons ---------------------------------------------------------------------------------------------------------------------------------
ICONS = {}


def icon(fn):
    ICONS[fn.__name__] = fn; return fn


@icon
def slot_emerald():
    g = bands([(20, '#8fb4c9', .05), (23, '#6f9a4a', .08), (32, '#5a7f3a', .1)], 1)
    put(g, """
.kkkk.
kooook
kOOOok
""", 21, 15)  # a house's roof far away
    villager(g, 5, 6, PAL['m'], PAL['M'], PAL['M'])
    put(g, """
...kkk...
..kLGGk..
.kLLGGgk.
kLGGGGggk
kGGGGGgdk
.kgGGgdk.
..kggdk..
...kdk...
....k....
""", 15, 14)  # the emerald held up beside the hands
    return g


@icon
def slot_mine():
    g = bands([(32, '#7c7a76', .12)], 2)
    for (x, y) in [(5, 6), (22, 19), (25, 15), (19, 23), (8, 4)]:
        put(g, "kfk\nfOf\nkfk", x, y)  # iron ore specks
    villager(g, 4, 9, PAL['m'], PAL['M'], PAL['M'])
    put(g, """
EEEEEEEE
EEEEEEEE
%EEEEEE%
%EEEEEE%
""", 5, 23)  # the toolsmith's black apron
    put(g, """
....kkkkkkkkk.
...kIIIIIIIIIk
..kIJkkkkkkJIk
..kJk..kqk.kJk
..kk..kqk...kk
.....kqk......
....kqk.......
...kqk........
..kqk.........
.kqk..........
kqk...........
kk............
""", 12, 10)  # the pickaxe raised
    return g


@icon
def slot_farm():
    g = bands([(18, '#a9c4cf', .05), (32, '#6b4a2e', .1)], 3)
    for y in (21, 24, 27):
        for x in range(3, 29):
            g[y][x] = shade(g[y][x], .72)  # furrows
    villager(g, 6, 9, PAL['m'], PAL['M'], PAL['M'])
    put(g, """
....kkkkkkkk....
...kYYYYYYYYk...
.kkYyyyyyyyyYkk.
kYYYYYYYYYYYYYYk
.kkkkkkkkkkkkkk.
""", 3, 5)  # the straw hat
    put(g, """
.kIIk
kIJk.
.kok.
.kok.
.kok.
.kok.
.kok.
.kok.
.kok.
.kok.
.kok.
""", 17, 12)  # the hoe
    put(g, """
.k...k...k.
kYk.kYk.kYk
kYyk.Yyk.Yk
.kYkkYkkYk.
..kYkYkYk..
...kTkTk...
...kTkTk...
""", 18, 20)  # wheat in front
    return g


@icon
def slot_specialty():
    g = bands([(32, '#8a6f9c', .08)], 4)
    villager(g, 8, 8, c('#4e7a3a'), c('#35562a'), c('#35562a'))
    put(g, """
....k....
...kAk...
kkkkAkkkk
kAAAAAAak
.kAAAAak.
.kAakAak.
kAak.kaak
kk.....kk
""", 18, 6)  # a gold star held up
    return g


@icon
def spec_fishing():
    g = bands([(19, '#9fbfd0', .05), (32, '#3f6f96', .12)], 5)
    for x in range(3, 29):
        if x % 4 != 1:
            g[19][x] = c('#a8cde0')  # the water's shine
    villager(g, 5, 7, c('#b8a27a'), c('#8d7a58'), c('#8d7a58'))
    put(g, """
....kkkk....
...kFFFFk...
.kkFvvvvFkk.
kFFFFFFFFFFk
.kkkkkkkkkk.
""", 4, 3)  # the fisherman's hat
    line(g, 13, 19, 25, 7, PAL['q'], PAL['k'])  # the rod from the hands up to the right
    for y in range(8, 21):
        g[y][25] = c('#e8e2d4')# the line
    put(g, """
.kkk.kk
kFFFkkFk
kWkFFFFk
kFFFkkFk
.kkk.kk
""", 21, 21)  # the fish on the hook
    return g


@icon
def spec_husbandry():
    g = bands([(17, '#a9c8d6', .05), (32, '#5f8a3e', .1)], 6)
    for x in (4, 5, 27, 28):
        for y in range(13, 22):
            g[y][x] = PAL['o']
    for x in range(3, 29):
        g[15][x] = PAL['o']  # a fence behind
    villager(g, 6, 8, c('#a27a52'), c('#7a5a3a'), c('#7a5a3a'))
    put(g, """
kkkkkkkkkk
kWWWWWWWWk
""", 6, 7)  # the shepherd's cap
    put(g, """
.kkkkkkkk.
kWWWWWWWWk
kWWWWWWWWk
kWllllllWk
kWWkllkWWk
kwllllllwk
kwllXXllwk
.kkllllkk.
...kkkk...
""", 18, 14)  # a sheep's head, its wool round a pale face
    put(g, """
kIk
kIIk
.kJk
""", 13, 18)  # the shears in the hands
    return g


@icon
def spec_potions():
    g = bands([(32, '#3a2f3f', .14)], 7)
    put(g, """
.k.
kAk
kYk
.k.
""", 24, 8)  # a candle's flame
    villager(g, 6, 7, PAL['P'], PAL['p'], PAL['p'])
    for y in range(21, 32):
        g[y][10] = PAL['A']  # the cleric's gold trim
    put(g, """
..kkk..
..kok..
..kWk..
.kWWWk.
kQPPPPk
kPQPPpk
kPPPPpk
.kkkkk.
""", 17, 13)  # the potion held up
    return g


@icon
def spec_trader():
    g = bands([(18, '#d79a5e', .06), (32, '#7a8a46', .1)], 8)
    put(g, """
..k..k..
.klkklk.
.kllllk.
kllllllk
kzllllTk
kXlllTTk
.kkkllTk
...kllTk
...kllTk
...kllTk
...kllTk
""", 20, 9)  # a llama's head and neck, looking left
    villager(g, 6, 7, PAL['B'], PAL['v'], PAL['v'])
    put(g, """
...kkkkkk...
.kkBBBBBBkk.
kBBxxxxxxBBk
kBx......xBk
kBk......kBk
kBk......kBk
kBk......kBk
kBk......kBk
kBk......kBk
kBk......kBk
""", 5, 5)  # the hood round the face
    put(g, """
..k..
.kGk.
kLGgk
.kgk.
..k..
""", 16, 17)  # an emerald held out
    return g


@icon
def spec_monsters():
    g = bands([(21, '#1f2a44', .1), (32, '#2d3f26', .1)], 9)
    put(g, """
kkkk
kWWk
kWwk
kkkk
""", 7, 4)  # the moon
    villager(g, 5, 7, c('#5a4434'), c('#3e2f24'), c('#3e2f24'), eye_patch=True)
    put(g, """
..........kkk
.........kIIk
........kIIk.
.......kIIk..
......kIIk...
.....kIIk....
..k.kIIk.....
..kakIk......
...kak.......
..kakak......
.kak.kk......
kak..........
kk...........
""", 10, 10)  # the sword raised
    put(g, """
kkkkkkk
kCcCCck
kzzCzzk
kzzCzzk
kCCzzCk
kCzzzzk
kCzCCzk
kkkkkkk
""", 19, 19)  # a creeper's head
    return g


@icon
def research():
    g = bands([(32, '#5b3d26', .08)], 10)
    for row, y0 in enumerate((4, 13, 22)):
        for x in range(3, 29):
            g[y0][x] = PAL['O']; g[y0 + 8 if y0 + 8 < N else y0][x] = PAL['q']
        for i, x in enumerate(range(4, 28, 2)):
            col = [PAL['R'], c('#3d6fb0'), c('#4e7a3a'), PAL['Y'], PAL['p']][(i + row * 2) % 5]
            for y in range(y0 + 2, min(y0 + 8, N)):
                g[y][x] = col
                g[y][x + 1] = shade(col, .75)  # the books on the shelf
    villager(g, 12, 7, c('#e3ddd0'), c('#b8ae9c'), PAL['R'])
    put(g, """
....kkkk..
...kRRRRk.
kkkkkkkkkk
""", 12, 4)  # the librarian's cap
    put(g, """
kkkkkkkkkk
kWWWWkWWWWk
kWwwWkWwwWk
kWWWWkWWWWk
kRRRRkRRRRk
.kkkkkkkkk.
""", 11, 18)  # the open book held up
    return g


for name, fn in ICONS.items():
    img = frame_and_clip(fn(), len(name))
    img.save(os.path.join(OUT, name + '.png'))
    print('wrote', name)

# the padlock: the flat one doubled, so every icon of the set is 32 x 32
flat = os.path.join(OUT, 'flat', 'lock.png')
Image.open(flat).resize((N, N), Image.NEAREST).save(os.path.join(OUT, 'lock.png'))
print('wrote lock')
