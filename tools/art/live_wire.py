#!/usr/bin/env python3
"""Animated arcs for live Barbed Wire: barbed_wire_post_live.png and barbed_wire_side_live.png as frame strips.

Usage: python3 live_wire.py <textures/block dir>

Every frame draws new lightning arcs inside the same faces the live models already use (their UVs are unchanged), so the arcs
jump around the coils and posts; a .png.mcmeta beside each file plays the frames. The arcs keep the original two colours
(cyan, with a white-hot core) on a clear background.
"""
import json, os, random, sys
from PIL import Image

FRAMES = 8
FRAME_TIME = 2  # ticks per frame
CYAN, CORE, GLOW = (122, 240, 255, 255), (232, 254, 255, 255), (154, 246, 255, 255)


def bolt(px, points, rng, core=0.35):
    """A jagged line through the points: each step one pixel, wandering a little sideways, parts of it white-hot."""
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        steps = max(int(round(max(abs(x1 - x0), abs(y1 - y0)))), 1)
        hot = rng.random() < core
        for i in range(steps + 1):
            x = round(x0 + (x1 - x0) * i / steps); y = round(y0 + (y1 - y0) * i / steps)
            px.add((x, y, CORE if hot and i % 3 else CYAN))


def ring_arcs(rect, rng, count):
    """Short arcs crawling along a coil: a circle filling the face."""
    import math
    x0, y0, x1, y1 = rect; cx, cy = (x0 + x1) / 2, (y0 + y1) / 2; r = (min(x1 - x0, y1 - y0) / 2) - 2
    px = set()
    for _ in range(count):
        a = rng.uniform(0, 2 * math.pi); span = rng.uniform(0.35, 0.9); pts = []
        for k in range(5):
            t = a + span * k / 4; rr = r + rng.uniform(-2.5, 2.5)
            pts.append((cx + math.cos(t) * rr, cy + math.sin(t) * rr))
        bolt(px, pts, rng)
    return px


def line_arc(rect, rng, vertical, gaps):
    """One arc running the length of a thin face, broken in places so it flickers."""
    x0, y0, x1, y1 = rect; px = set()
    length = (y1 - y0) if vertical else (x1 - x0); width = (x1 - x0) if vertical else (y1 - y0)
    pos, pts = 0, []
    while pos <= length:
        side = rng.randint(0, max(0, width - 1))
        pts.append((x0 + side, y0 + pos) if vertical else (x0 + pos, y0 + side))
        pos += rng.randint(2, 4)
    pieces = [pts[i:i + rng.randint(2, 4)] for i in range(0, len(pts), 3)]
    for piece in pieces:
        if len(piece) > 1 and rng.random() > gaps:
            bolt(px, piece, rng, core=0.5)
    return px


def frame(size, faces, rng):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for kind, rect, arg in faces:
        x0, y0, x1, y1 = rect
        px = ring_arcs(rect, rng, arg) if kind == "ring" else line_arc(rect, rng, kind == "vertical", arg)
        for x, y, c in px:
            if x0 <= x < x1 and y0 <= y < y1:
                im.putpixel((x, y), c)
                for dx, dy in ((1, 0), (0, 1)):
                    if rng.random() < 0.15 and x0 <= x + dx < x1 and y0 <= y + dy < y1 and im.getpixel((x + dx, y + dy))[3] == 0:
                        im.putpixel((x + dx, y + dy), GLOW)
    return im


# The faces the live models draw, in pixels (UV x 8 on the 128 px side texture, x 4 on the 64 px post texture)
SIDE = (128, [("ring", (1, 1, 29, 29), 5), ("ring", (31, 1, 59, 29), 5), ("ring", (61, 1, 89, 29), 5), ("ring", (91, 1, 119, 29), 5),
              ("horizontal", (1, 31, 17, 35), 0.3), ("horizontal", (19, 31, 35, 35), 0.3), ("horizontal", (37, 31, 53, 35), 0.3), ("horizontal", (55, 31, 71, 35), 0.3)])
POST = (64, [("vertical", (1, 1, 3, 23), 0.3), ("vertical", (5, 1, 7, 23), 0.3), ("horizontal", (9, 1, 15, 3), 0.2), ("horizontal", (17, 1, 23, 3), 0.2)])


def main():
    out = sys.argv[1]
    for name, (size, faces), seed in (("barbed_wire_side_live", SIDE, 11), ("barbed_wire_post_live", POST, 23)):
        rng = random.Random(seed); strip = Image.new("RGBA", (size, size * FRAMES), (0, 0, 0, 0))
        for f in range(FRAMES):
            strip.paste(frame(size, faces, rng), (0, f * size))
        strip.save(os.path.join(out, name + ".png"), optimize=True)
        with open(os.path.join(out, name + ".png.mcmeta"), "w") as m:
            json.dump({"animation": {"frametime": FRAME_TIME, "interpolate": False}}, m, indent=2); m.write("\n")
        print(name, size, "x", size * FRAMES)


if __name__ == "__main__":
    main()
