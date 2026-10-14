#!/usr/bin/env python3
"""Pixel-art icon and banner for each of the three mods.

Each picture is drawn pixel by pixel on a small canvas (icon 32 x 32, banner 240 x 80) and scaled up with hard edges:
docs/branding/<mod>/icon.png (512 x 512) and banner.png (1920 x 640) for mod pages, and <mod id>_logo.png (the banner at 480 x 160)
in each mod's resources for the in-game mod list. Re-run after editing:

    python3 tools/ui-assets/branding.py
"""
import math, pathlib, random
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / 'docs/branding'
MODS = ROOT / 'custom-mods'

# The mods' screen palette
NAVY, DEEP, SLATE, SLATE_HI, EDGE = (17, 30, 40), (10, 18, 24), (28, 48, 61), (42, 69, 85), (61, 92, 109)
CYAN, CYAN_DIM, CYAN_FILL, INK, MUTED = (90, 226, 223), (47, 141, 140), (23, 66, 74), (237, 245, 248), (159, 179, 188)
BRASS, BRASS_DK, ORANGE, RED = (201, 162, 75), (125, 100, 40), (255, 163, 108), (237, 115, 105)
GUNMETAL, STEEL, STEEL_HI, WOOD, WOOD_DK, WOOD_HI = (52, 58, 64), (96, 104, 112), (150, 158, 166), (122, 74, 42), (84, 50, 28), (156, 98, 58)
GRASS, GRASS_DK, DIRT, NIGHT_TOP, NIGHT_LOW = (58, 94, 52), (38, 64, 36), (70, 52, 38), (8, 12, 26), (28, 40, 70)

FONT = {
    'A': ".###.|#...#|#...#|#####|#...#|#...#|#...#", 'B': "####.|#...#|#...#|####.|#...#|#...#|####.",
    'C': ".###.|#...#|#....|#....|#....|#...#|.###.", 'D': "####.|#...#|#...#|#...#|#...#|#...#|####.",
    'E': "#####|#....|#....|####.|#....|#....|#####", 'F': "#####|#....|#....|####.|#....|#....|#....",
    'G': ".###.|#...#|#....|#.###|#...#|#...#|.####", 'H': "#...#|#...#|#...#|#####|#...#|#...#|#...#",
    'I': ".###.|..#..|..#..|..#..|..#..|..#..|.###.", 'J': "..###|...#.|...#.|...#.|...#.|#..#.|.##..",
    'K': "#...#|#..#.|#.#..|##...|#.#..|#..#.|#...#", 'L': "#....|#....|#....|#....|#....|#....|#####",
    'M': "#...#|##.##|#.#.#|#.#.#|#...#|#...#|#...#", 'N': "#...#|##..#|#.#.#|#..##|#...#|#...#|#...#",
    'O': ".###.|#...#|#...#|#...#|#...#|#...#|.###.", 'P': "####.|#...#|#...#|####.|#....|#....|#....",
    'Q': ".###.|#...#|#...#|#...#|#.#.#|#..#.|.##.#", 'R': "####.|#...#|#...#|####.|#.#..|#..#.|#...#",
    'S': ".####|#....|#....|.###.|....#|....#|####.", 'T': "#####|..#..|..#..|..#..|..#..|..#..|..#..",
    'U': "#...#|#...#|#...#|#...#|#...#|#...#|.###.", 'V': "#...#|#...#|#...#|#...#|#...#|.#.#.|..#..",
    'W': "#...#|#...#|#...#|#.#.#|#.#.#|#.#.#|.#.#.", 'X': "#...#|#...#|.#.#.|..#..|.#.#.|#...#|#...#",
    'Y': "#...#|#...#|.#.#.|..#..|..#..|..#..|..#..", 'Z': "#####|....#|...#.|..#..|.#...|#....|#####",
    ' ': ".....|.....|.....|.....|.....|.....|.....",
}

class Canvas:
    def __init__(self, w, h, fill=(0, 0, 0, 0)):
        self.im = Image.new('RGBA', (w, h), fill); self.px = self.im.load(); self.w, self.h = w, h
    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h: self.px[x, y] = c if len(c) == 4 else (*c, 255)
    def get(self, x, y): return self.px[x, y]
    def blend(self, x, y, c, a):
        if 0 <= x < self.w and 0 <= y < self.h: self.px[x, y] = (*mix(self.px[x, y][:3], c, a), 255)
    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1): self.put(x, y, c)
    def outline(self, c, inside=None):
        """A 1 px ring around every opaque pixel (inside: only pixels where that predicate holds)."""
        filled = {(x, y) for y in range(self.h) for x in range(self.w) if self.px[x, y][3] > 0}
        for y in range(self.h):
            for x in range(self.w):
                if (x, y) in filled: continue
                if any((x + dx, y + dy) in filled for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))): self.put(x, y, c)
    def paste(self, other, x, y):
        for j in range(other.h):
            for i in range(other.w):
                c = other.px[i, j]
                if c[3] > 0: self.put(x + i, y + j, c)
    def save(self, path, scale):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.im.resize((self.w * scale, self.h * scale), Image.NEAREST).save(path)

def mix(a, b, t): return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))

def text(c, s, x, y, colour, scale=1, shadow=DEEP):
    """The 5 x 7 font, 1 px between letters; a hard shadow one pixel down and right."""
    for layer, col, off in ((0, shadow, scale), (1, colour, 0)):
        if layer == 0 and shadow is None: continue
        cx = x
        for ch in s:
            rows = FONT[ch].split('|')
            for j, row in enumerate(rows):
                for i, bit in enumerate(row):
                    if bit == '#':
                        for sy in range(scale):
                            for sx in range(scale):
                                shade = col if layer == 0 else (mix(col, INK, .35) if j < 2 else col)
                                c.put(cx + i * scale + sx + off, y + j * scale + sy + off, shade)
            cx += 6 * scale
def text_width(s, scale=1): return len(s) * 6 * scale - scale

def night(c, top, low, seed, stars=True, y1=None, clear=None):
    """A sky that darkens upward, dithered between bands, with a few stars (none inside the clear box)."""
    rnd = random.Random(seed); y1 = c.h if y1 is None else y1
    for y in range(y1):
        t = y / max(1, y1 - 1)
        for x in range(c.w):
            band = t + ((x + y) % 2) * .04
            c.put(x, y, mix(top, low, min(1, band)))
    if stars:
        for _ in range(c.w * y1 // 160):
            x, y = rnd.randrange(c.w), rnd.randrange(int(y1 * .7))
            if clear and clear[0] <= x <= clear[2] and clear[1] <= y <= clear[3]: continue
            c.put(x, y, mix(INK, CYAN, rnd.random() * .5) if rnd.random() < .8 else BRASS)

def beacon(c, cx, base_y, beam_top=0, small=False):
    """The Defense Beacon: a stepped gunmetal pedestal, brass trim, a cyan crystal and its beam."""
    widths = [5, 7, 9] if small else [7, 11, 15, 19]
    y = base_y
    for k, w in enumerate(reversed(widths)):
        h = 2 if small else 3
        c.rect(cx - w // 2, y - h + 1, cx + w // 2, y, GUNMETAL)
        c.rect(cx - w // 2, y - h + 1, cx + w // 2, y - h + 1, STEEL)
        c.put(cx - w // 2, y, BRASS_DK); c.put(cx + w // 2, y, BRASS_DK)
        y -= h
    c.rect(cx - (widths[0] // 2), y + 1, cx + widths[0] // 2, y + 1, BRASS)
    # the crystal: a diamond, light at its upper left
    r = 3 if small else 5
    top = y - 2 * r
    for dy in range(-r, r + 1):
        half = r - abs(dy)
        for dx in range(-half, half + 1):
            col = CYAN if dx + dy < 0 else CYAN_DIM
            if abs(dx) == half or abs(dy) == r: col = mix(CYAN, INK, .5) if dx + dy < 0 else CYAN_FILL
            c.put(cx + dx, y - r + dy, col)
    # the beam: a bright core, a softer glow beside it, fading toward the top
    for yy in range(beam_top, y - 2 * r):
        t = (yy - beam_top) / max(1, (y - 2 * r) - beam_top)
        for dx, strength in ((0, 1.0), (-1, .55), (1, .55), (-2, .25), (2, .25)):
            under = c.get(cx + dx, yy) if 0 <= cx + dx < c.w and 0 <= yy < c.h else (0, 0, 0, 0)
            base = under[:3] if under[3] else DEEP
            c.put(cx + dx, yy, mix(base, mix(CYAN, INK, .5 if dx == 0 else 0), strength * (.35 + .65 * t)))

def rifle(length=40, stock=WOOD, small=False):
    """A side-on rifle, muzzle to the right: stock, receiver, magazine, grip, barrel, front sight."""
    g = Canvas(length + 2, 14)
    s = length // 4
    for x in range(s):                                     # the stock, deeper at the butt
        top = 4 + x // 5; g.rect(x, top, x, 8, stock)
        g.put(x, top, mix(stock, INK, .25))
    g.rect(0, 4, 0, 9, WOOD_DK)
    r0, r1 = s, s + length // 3                            # the receiver
    g.rect(r0, 4, r1, 7, GUNMETAL); g.rect(r0, 4, r1, 4, STEEL)
    g.rect(r0 + 2, 3, r0 + 5, 3, STEEL_HI)                 # rear sight block
    g.rect(r0 + 2, 8, r0 + 3, 11, GUNMETAL)                # grip
    m = r0 + (r1 - r0) // 2                                # a curved magazine
    for k in range(5): g.rect(m + k // 2, 8 + k, m + 2 + k // 2, 8 + k, mix(GUNMETAL, STEEL, .3))
    g.rect(r1 + 1, 5, r1 + length // 5, 7, stock)           # the handguard
    g.rect(r1 + 1, 5, r1 + length // 5, 5, mix(stock, INK, .25))
    g.rect(r1, 5, length, 5, STEEL)                         # the barrel
    g.rect(r1 + length // 5, 6, length - 2, 6, GUNMETAL)
    g.put(length - 1, 4, STEEL_HI); g.put(length - 1, 3, STEEL_HI)   # front sight
    g.outline(DEEP)
    return g

def raider(c, x, ground, flip=False):
    """A raid zombie walking toward the beacon: head, two red eyes, arms reaching forward, stepping legs."""
    skin, cloth, dark = (64, 104, 62), (46, 58, 78), (30, 40, 34)
    f = (lambda dx: x + 4 - dx) if flip else (lambda dx: x + dx)
    for dx in range(1, 4):
        for dy in range(11, 14): c.put(f(dx), ground - dy, skin)
    c.put(f(3), ground - 12, RED)
    for dx in range(0, 5):
        for dy in range(5, 10): c.put(f(dx), ground - dy, cloth if dx else dark)
    for dx in range(4, 8): c.put(f(dx), ground - 9, skin); c.put(f(dx), ground - 8, mix(skin, dark, .4))
    for dy in range(0, 5): c.put(f(1), ground - dy, dark); c.put(f(3 + (dy < 2)), ground - dy, dark)

def ground(c, y, seed):
    rnd = random.Random(seed)
    for x in range(c.w):
        h = y + (1 if rnd.random() < .2 else 0)
        c.rect(x, h, x, h, GRASS); c.rect(x, h + 1, x, c.h - 1, GRASS_DK if (x // 3) % 2 else mix(GRASS_DK, DIRT, .4))

def frame(c, colour=EDGE, corner=2):
    """Round the corners off and draw a 1 px border inside the canvas."""
    for y in range(c.h):
        for x in range(c.w):
            dx, dy = min(x, c.w - 1 - x), min(y, c.h - 1 - y)
            if dx + dy < corner: c.put(x, y, (0, 0, 0, 0))
            elif dx == 0 or dy == 0 or dx + dy == corner: c.put(x, y, colour)

# ---- Eternal Defense
def ed_icon():
    c = Canvas(32, 32); night(c, NIGHT_TOP, NIGHT_LOW, 3)
    ground(c, 27, 5); beacon(c, 16, 27, beam_top=1)
    frame(c, BRASS)
    return c

def ed_banner():
    c = Canvas(240, 80); night(c, NIGHT_TOP, NIGHT_LOW, 7, clear=(6, 6, 190, 50))
    hill = [58 + int(4 * math.sin(x / 17) + 3 * math.sin(x / 7 + 1)) for x in range(240)]
    for x in range(240): c.rect(x, hill[x], x, 79, mix(NIGHT_LOW, DEEP, .55))
    ground(c, 66, 13)
    beacon(c, 204, 66, beam_top=0)
    for x in (110, 128, 146, 162): raider(c, x, 66)
    # sandbags either side of the beacon
    for x0 in (176, 222):
        for row, y in enumerate((62, 59)):
            for x in range(x0 + row * 2, x0 + 14 - row * 2, 5):
                c.rect(x, y, x + 4, y + 3, mix(DIRT, BRASS_DK, .5)); c.rect(x, y, x + 4, y, mix(BRASS, DIRT, .3))
    title = 'ETERNAL DEFENSE'
    text(c, title, 10, 12, INK, scale=2)
    sub = 'DEFEND THE BEACON'
    text(c, sub, 10, 34, CYAN, scale=1)
    c.rect(10, 44, 10 + text_width(sub), 44, BRASS)
    frame(c, BRASS, corner=3)
    return c

# ---- Gun Guide
def keycap(c, x, y, letter, colour=INK):
    c.rect(x, y, x + 8, y + 9, SLATE_HI); c.rect(x, y + 9, x + 8, y + 10, DEEP); c.rect(x + 1, y + 1, x + 7, y + 7, SLATE)
    rows = FONT[letter].split('|')
    for j, row in enumerate(rows):
        for i, bit in enumerate(row):
            if bit == '#': c.put(x + 2 + i, y + 1 + j, colour)

def guide_icon():
    c = Canvas(32, 32, (*NAVY, 255))
    for y in range(32):
        for x in range(32):
            if (x + y) % 4 == 0: c.put(x, y, mix(NAVY, SLATE, .5))
    c.rect(4, 4, 27, 17, SLATE); c.rect(4, 4, 27, 4, SLATE_HI)          # the card
    gun = rifle(20); c.paste(gun, 5, 6)
    keycap(c, 3, 19, 'R', CYAN); keycap(c, 12, 19, 'V', BRASS); keycap(c, 21, 19, 'B', INK)
    frame(c, CYAN)
    return c

def guide_banner():
    c = Canvas(240, 80, (*NAVY, 255))
    for y in range(80):
        for x in range(240):
            if (x + y) % 6 == 0: c.put(x, y, mix(NAVY, SLATE, .45))
    c.rect(132, 10, 229, 68, SLATE); c.rect(132, 10, 229, 12, SLATE_HI); c.rect(132, 66, 229, 68, DEEP)
    gun = rifle(56); c.paste(gun, 138, 18)
    for k, (key, col) in enumerate((('R', CYAN), ('V', BRASS), ('B', INK), ('F', ORANGE), ('G', CYAN))):
        keycap(c, 140 + k * 17, 48, key, col)
    text(c, 'GUN GUIDE', 12, 16, INK, scale=2)
    sub = 'KNOW EVERY GUN'
    text(c, sub, 12, 38, CYAN)
    c.rect(12, 48, 12 + text_width(sub), 48, BRASS)
    text(c, 'ETERNAL DEFENSE', 12, 56, MUTED)
    frame(c, CYAN, corner=3)
    return c

# ---- Displays
def planks(c, y0, y1):
    for y in range(y0, y1):
        for x in range(c.w):
            band = (y // 5) % 2
            col = WOOD if band else mix(WOOD, WOOD_DK, .45)
            if y % 5 == 0 or (x + (y // 5) * 13) % 31 == 0: col = WOOD_DK
            c.put(x, y, col)

def rack(c, x, y, w):
    c.rect(x, y, x + w, y + 1, GUNMETAL); c.rect(x, y, x + w, y, STEEL)
    for px in (x + 2, x + w - 2): c.rect(px, y - 3, px, y - 1, BRASS)

def displays_icon():
    c = Canvas(32, 32); planks(c, 0, 32)
    for y in (10, 22): rack(c, 3, y, 25)
    c.paste(rifle(24), 4, 1); c.paste(rifle(24, stock=GUNMETAL), 4, 13)
    frame(c, BRASS)
    return c

def displays_banner():
    c = Canvas(240, 80); planks(c, 0, 80)
    c.rect(0, 70, 239, 79, mix(WOOD_DK, DEEP, .5))
    for k, (x, y, w, stock) in enumerate(((128, 26, 48, WOOD), (184, 26, 48, GUNMETAL), (128, 54, 48, GUNMETAL), (184, 54, 48, WOOD))):
        rack(c, x, y, w + 2); c.paste(rifle(w, stock=stock), x, y - 10)
    # a glass case on the left half of the floor
    c.rect(8, 50, 104, 70, mix(SLATE, CYAN_FILL, .5)); c.rect(8, 50, 104, 50, mix(CYAN, INK, .3)); c.rect(8, 68, 104, 70, GUNMETAL)
    c.paste(rifle(44), 30, 54)
    for x in range(10, 104, 9): c.blend(x, 52, INK, .5)
    for y in range(4, 44):
        for x in range(6, 122): c.blend(x, y, DEEP, .8)
    text(c, 'DISPLAYS', 12, 10, INK, scale=2)
    text(c, 'SHOW OFF YOUR GUNS', 12, 30, BRASS)
    frame(c, BRASS, corner=3)
    return c

ARTS = {
    'eternal_defense': ('eternal-defense', ed_icon, ed_banner),
    'eternal_defense_gun_guide': ('eternal-defense-gun-guide', guide_icon, guide_banner),
    'eternal_defense_displays': ('eternal-defense-displays', displays_icon, displays_banner),
}

def main():
    for modid, (folder, icon, banner) in ARTS.items():
        i, b = icon(), banner()
        i.save(OUT / folder / 'icon.png', 16); b.save(OUT / folder / 'banner.png', 8)
        b.save(MODS / folder / 'src/main/resources' / f'{modid}_logo.png', 2)
        print(modid, 'icon 512 x 512, banner 1920 x 640, logo 480 x 160')

if __name__ == '__main__':
    main()
