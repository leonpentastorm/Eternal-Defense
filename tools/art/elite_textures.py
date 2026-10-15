#!/usr/bin/env python3
"""Writes the elite overlay textures (assets/eternal_defense/textures/entity/elite/).

Their colours are in the pixels, not in a vertex tint: shader packs (Oculus with MakeUp, for one) drop the tint and drew every
ring and health bar white. Run it again after changing a colour in Elites.Kind.

  ring_<kind>.png   64 x 72: rows 0-63 the ring (premultiplied, drawn additive), rows 66-69 a solid strip the sparks sample
  bar_fill.png      the health bar's fill (red)
  bar_track.png     the dark track behind it
Only IHDR, IDAT and IEND chunks are written.
"""
import struct, zlib, math
from pathlib import Path

OUT = Path(__file__).resolve().parents[2] / "custom-mods/eternal-defense/src/main/resources/assets/eternal_defense/textures/entity/elite"
KINDS = {"badass": 0xFFC233, "badass_top": 0xFF3B30, "speedster": 0xFFF04D, "healer": 0x4DFF73, "matrioska": 0xFF66CC,
         "long_shot": 0x4DD2FF, "flak_armor": 0xC8D2E6, "body_armor": 0xE0A050, "vampiric": 0xE0245E, "frostbite": 0x9BE7FF,
         "berserker": 0xFF7A1A}

def png(path, w, h, pixels):
    raw = b"".join(b"\x00" + bytes(c for p in pixels[y * w:(y + 1) * w] for c in p) for y in range(h))
    chunk = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))

def ring_intensity(x, y):
    # A soft ring in steps: bright band near the edge, a fainter inner band
    d = math.hypot(x + .5 - 32, y + .5 - 32) / 32
    for lo, hi, v in ((.90, .98, 255), (.84, .90, 191), (.98, 1.0, 127), (.70, .76, 127), (.76, .84, 63)):
        if lo <= d < hi: return v
    return 0

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for kind, colour in KINDS.items():
        r, g, b = colour >> 16 & 255, colour >> 8 & 255, colour & 255
        px = []
        for y in range(72):
            for x in range(64):
                i = ring_intensity(x, y) if y < 64 else (255 if 66 <= y <= 69 else 0)
                px.append((r * i // 255, g * i // 255, b * i // 255, i))
        png(OUT / f"ring_{kind}.png", 64, 72, px)
    shade = [(255, 96, 86), (226, 34, 34), (226, 34, 34), (150, 16, 16)]
    png(OUT / "bar_fill.png", 16, 4, [shade[y] + (255,) for y in range(4) for _ in range(16)])
    png(OUT / "bar_track.png", 16, 4, [(28, 8, 8, 255)] * 64)
    print("written to", OUT)

if __name__ == "__main__":
    main()
