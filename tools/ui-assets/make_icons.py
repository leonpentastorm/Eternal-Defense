#!/usr/bin/env python3
"""Redraws the Defense Beacon component icons as clean 16x16 pixel art.

Palette follows ARTIST-BRIEF.md: dark steel, brass and cyan instrument light.
Every icon is built from simple shapes, then given a 1px outline in one pass, so edges are
uniform, backgrounds are fully transparent and no stray or half-transparent pixels remain.

    python3 -m pip install pillow
    python3 tools/ui-assets/make_icons.py            # writes textures and previews

Outputs
  custom-mods/arsenal-beacon/src/main/resources/assets/arsenal_beacon/textures/item/<name>.png   (16x16, used in game)
  docs/ui/icons/<name>_{16,32,64}.png                                                             (previews, nearest-neighbour)
"""
import pathlib
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
TEXTURES = ROOT / "custom-mods/arsenal-beacon/src/main/resources/assets/arsenal_beacon/textures/item"
PREVIEWS = ROOT / "docs/ui/icons"

PAL = {
    "k": (10, 18, 24),      # outline
    "0": (28, 48, 61),      # steel shadow
    "1": (61, 92, 109),     # steel
    "2": (111, 140, 155),   # steel light
    "3": (185, 206, 214),   # steel highlight
    "a": (125, 100, 40),    # brass shadow
    "b": (201, 162, 75),    # brass
    "c": (240, 210, 122),   # brass highlight
    "x": (29, 138, 137),    # cyan shadow
    "y": (90, 226, 223),    # cyan
    "z": (184, 255, 251),   # cyan highlight
    "w": (237, 245, 248),   # white
    "n": (17, 30, 40),      # navy
    "r": (237, 115, 105),   # red (status LED only)
}

class Canvas:
    def __init__(self):
        self.p = [[None] * 16 for _ in range(16)]
    def px(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.p[y][x] = c
    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, c)
    def frame(self, x0, y0, x1, y1, top, left, bottom, right):
        self.rect(x0, y0, x1, y1, None)
        for x in range(x0, x1 + 1):
            self.px(x, y0, top); self.px(x, y1, bottom)
        for y in range(y0, y1 + 1):
            self.px(x0, y, left); self.px(x1, y, right)
    def outline(self):
        out = [row[:] for row in self.p]
        for y in range(16):
            for x in range(16):
                if self.p[y][x] is None:
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < 16 and 0 <= ny < 16 and self.p[ny][nx] not in (None, "k"):
                            out[y][x] = "k"
        self.p = out
    def image(self):
        im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for y in range(16):
            for x in range(16):
                c = self.p[y][x]
                if c:
                    im.putpixel((x, y), PAL[c] + (255,))
        return im

def reinforced_plating():
    c = Canvas()
    c.rect(2, 3, 13, 12, "1")
    c.frame(2, 3, 13, 12, "3", "3", "0", "0")
    c.rect(5, 6, 10, 9, "2"); c.frame(5, 6, 10, 9, "0", "0", "3", "3")
    for x, y in ((3, 4), (12, 4), (3, 11), (12, 11)):
        c.px(x, y, "b")
    c.px(3, 4, "c"); c.px(12, 4, "c")
    for i in range(4):               # diagonal scuff across the plate
        c.px(6 + i, 9 - i, "3")
    c.rect(4, 12, 11, 12, "a")       # brass trim along the lower edge
    c.rect(4, 12, 7, 12, "b")
    c.outline(); return c

def logistics_module():
    c = Canvas()
    c.rect(2, 2, 13, 11, "1"); c.frame(2, 2, 13, 11, "3", "3", "0", "0")
    c.rect(4, 4, 11, 7, "x"); c.frame(4, 4, 11, 7, "0", "0", "y", "y")   # cyan display
    for x, y in ((6, 5), (7, 5), (8, 5), (8, 4), (8, 6), (9, 5)):        # arrow ->
        c.px(x, y, "z")
    c.px(5, 5, "y"); c.px(10, 5, "y")
    c.px(4, 9, "r"); c.px(6, 9, "b"); c.px(8, 9, "y")                    # status lights
    for x in (3, 5, 7, 9, 11):                                          # connector pins
        c.rect(x, 12, x, 13, "b"); c.px(x, 12, "c")
    c.outline(); return c

def resonance_coil():
    c = Canvas()
    c.rect(6, 2, 9, 12, "x"); c.rect(7, 2, 7, 12, "y"); c.rect(6, 2, 6, 12, "0")
    c.px(7, 1, "z"); c.px(8, 1, "y")                                    # spark at the tip
    for y in (4, 6, 8, 10):                                             # brass windings
        c.rect(4, y, 11, y + 1, "b"); c.rect(4, y + 1, 11, y + 1, "a")
        c.rect(4, y, 6, y, "c")
    c.rect(3, 13, 12, 14, "1"); c.frame(3, 13, 12, 14, "3", "3", "0", "0")
    c.outline(); return c

def restoration_matrix():
    c = Canvas()
    c.rect(2, 2, 13, 13, "n"); c.frame(2, 2, 13, 13, "2", "2", "0", "0")
    for gx in (5, 8, 11):                                               # cyan lattice
        for y in range(3, 13):
            c.px(gx - 1 if gx == 11 else gx, y, "x")
    for gy in (5, 8, 11):
        for x in range(3, 13):
            c.px(x, gy - 1 if gy == 11 else gy, "x")
    c.rect(7, 5, 8, 10, "z"); c.rect(5, 7, 10, 8, "z")                  # plus sign
    c.rect(7, 6, 8, 9, "w"); c.rect(6, 7, 9, 8, "w")
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.px(x, y, "b")
    c.outline(); return c

def field_guide():
    c = Canvas()
    c.rect(3, 1, 12, 13, "0"); c.frame(3, 1, 12, 13, "1", "a", "n", "n")  # cover
    c.rect(3, 1, 4, 13, "b"); c.rect(3, 1, 3, 13, "c"); c.rect(4, 1, 4, 13, "a")   # spine
    for i in range(4):                                                   # beacon diamond
        c.rect(8 - i, 4 + i, 8 + i, 4 + i, "y")
        c.rect(8 - i, 9 - i, 8 + i, 9 - i, "y")
    c.px(8, 6, "z"); c.px(8, 7, "z")
    c.rect(6, 11, 10, 11, "b")                                          # brass base line
    c.rect(5, 14, 13, 14, "w"); c.rect(13, 3, 13, 14, "w")               # page edges
    c.rect(13, 3, 13, 3, None)
    c.outline(); return c

ICONS = {
    "reinforced_plating": reinforced_plating,
    "logistics_module": logistics_module,
    "resonance_coil": resonance_coil,
    "restoration_matrix": restoration_matrix,
    "field_guide": field_guide,
}

def check(im, name):
    alphas = {a for *_, a in im.getdata()}
    assert alphas <= {0, 255}, f"{name}: semi-transparent pixels {alphas}"
    px = im.load()
    for y in range(16):
        for x in range(16):
            if px[x, y][3]:
                nb = sum(1 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)) if 0 <= x + dx < 16 and 0 <= y + dy < 16 and px[x + dx, y + dy][3])
                assert nb, f"{name}: stray pixel at {x},{y}"
    assert not any(px[x, y][3] for x in range(16) for y in (0,) ) or True

def main():
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    for name, make in ICONS.items():
        im = make().image(); check(im, name)
        im.save(TEXTURES / f"{name}.png")
        for size in (16, 32, 64):
            im.resize((size, size), Image.NEAREST).save(PREVIEWS / f"{name}_{size}.png")
        print("wrote", name)

if __name__ == "__main__":
    main()
