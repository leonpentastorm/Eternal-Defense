#!/usr/bin/env python3
"""Writes the raider bridge plank texture (assets/eternal_defense/textures/block/raider_bridge.png).

16 x 16: three rough dark planks lashed with red rope. Only IHDR, IDAT and IEND chunks are written.
"""
import random, struct, zlib
from pathlib import Path

OUT = Path(__file__).resolve().parents[2] / "custom-mods/eternal-defense/src/main/resources/assets/eternal_defense/textures/block/raider_bridge.png"
WOOD = [(92, 64, 42), (84, 58, 38), (101, 71, 47), (76, 52, 34)]
GAP = (38, 26, 18)
ROPE = [(150, 34, 30), (122, 24, 22)]
NAIL = (140, 140, 132)

def png(path, w, h, pixels):
    raw = b"".join(b"\x00" + bytes(c for p in pixels[y * w:(y + 1) * w] for c in p) for y in range(h))
    chunk = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))

def main():
    rnd = random.Random(1111)
    planks = [(0, 4), (5, 10), (11, 15)]
    px = []
    for y in range(16):
        for x in range(16):
            gap = y in (4, 10) or y == 15
            if gap:
                c = GAP
            else:
                band = next(i for i, (a, b) in enumerate(planks) if a <= y <= b)
                c = WOOD[(band + (x + 2 * band) // 5 + rnd.randrange(2)) % len(WOOD)]
                if rnd.random() < .08:
                    c = tuple(max(0, v - 18) for v in c)
            if x in (3, 12) and not gap:
                c = ROPE[(y + x) % 2]
            if (x, y) in ((7, 2), (8, 7), (7, 13)):
                c = NAIL
            px.append(c + (255,))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    png(OUT, 16, 16, px)
    print("written to", OUT)

if __name__ == "__main__":
    main()
