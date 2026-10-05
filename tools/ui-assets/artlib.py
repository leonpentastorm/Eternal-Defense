"""Small pixel-art toolkit used by make_sprites.py. Pure Pillow, deterministic, no randomness.

Ramps are dark -> light colour lists. Shapes are shaded per pixel with a light from the upper left so that
every sprite and tile reads like the painted steel / brass / copper / cyan icons in industrial_atlas.png.
"""
import math
from PIL import Image, ImageDraw

def hx(s):
    s = s.lstrip('#'); return tuple(int(s[i:i+2], 16) for i in (0, 2, 4)) + (255,)

RAMPS = {
    'steel':  [hx(c) for c in ('#1d2430', '#313b4a', '#4a5666', '#6b7a8c', '#98a8b8', '#c9d6e0')],
    'dark':   [hx(c) for c in ('#0b0f14', '#141a22', '#1e2631', '#2b3544', '#3a4659')],
    'brass':  [hx(c) for c in ('#3f2c0c', '#6e4e17', '#a9792a', '#d49f43', '#f1cc78', '#fff0b8')],
    'copper': [hx(c) for c in ('#40190d', '#6f3016', '#a74d28', '#d07842', '#eea572')],
    'cyan':   [hx(c) for c in ('#0a3c44', '#12727a', '#25b5bd', '#5ae2df', '#a8fffb', '#e6ffff')],
    'ember':  [hx(c) for c in ('#3a0c04', '#7a1c06', '#c2410a', '#f97316', '#ffb347', '#fff0b8')],
    'purple': [hx(c) for c in ('#1c1038', '#3a2070', '#6a3cc0', '#9d6bff', '#d0b0ff', '#f1e6ff')],
    'red':    [hx(c) for c in ('#3a0a0e', '#7a1620', '#bd2a35', '#ed5560', '#ff9aa0', '#ffe0e2')],
    'cloth':  [hx(c) for c in ('#5b6a78', '#8497a8', '#aebfce', '#d3e0ea', '#f0f6fa', '#ffffff')],
    'wood':   [hx(c) for c in ('#2a1a0e', '#4a2f19', '#6e4727', '#946236', '#b98249', '#d8a566')],
}
OUTLINE = hx('#0d0c14')

def pick(ramp, v):
    v = max(0.0, min(0.999, v)); return RAMPS[ramp][int(v * len(RAMPS[ramp]))]

def new(w, h): return Image.new('RGBA', (w, h), (0, 0, 0, 0))

def light(nx, ny, nz):
    """Brightness 0..1 for a surface normal, light from upper left and front."""
    l = (-0.5, -0.55, 0.67); n = math.sqrt(nx*nx + ny*ny + nz*nz) or 1
    d = (nx*l[0] + ny*l[1] + nz*l[2]) / n
    return max(0.0, min(1.0, 0.18 + 0.82 * d))

def vcylinder(im, x0, x1, y0, y1, ramp, rim=True):
    """Upright cylinder spanning [x0,x1] x [y0,y1] inclusive, shaded across its width."""
    xc = (x0 + x1) / 2; r = (x1 - x0) / 2 + 0.5
    for x in range(x0, x1 + 1):
        u = (x - xc) / r; nz = math.sqrt(max(0, 1 - u*u))
        v = light(u, 0, nz)
        if rim and u > 0.7: v += 0.12
        for y in range(y0, y1 + 1): im.putpixel((x, y), pick(ramp, v))

def hcylinder(im, x0, x1, y0, y1, ramp):
    yc = (y0 + y1) / 2; r = (y1 - y0) / 2 + 0.5
    for y in range(y0, y1 + 1):
        u = (y - yc) / r; nz = math.sqrt(max(0, 1 - u*u)); v = light(0, u, nz)
        for x in range(x0, x1 + 1): im.putpixel((x, y), pick(ramp, v))

def sphere(im, cx, cy, r, ramp):
    for y in range(int(cy - r), int(cy + r) + 1):
        for x in range(int(cx - r), int(cx + r) + 1):
            dx, dy = (x - cx) / r, (y - cy) / r; d = dx*dx + dy*dy
            if d <= 1: im.putpixel((x, y), pick(ramp, light(dx, dy, math.sqrt(1 - d))))

def box_face(im, x0, y0, x1, y1, ramp, bevel=True):
    """Flat panel with a lit top/left bevel and shaded bottom/right."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            v = 0.55
            if bevel:
                if y == y0 or x == x0: v = 0.9
                elif y == y1 or x == x1: v = 0.18
                elif y == y0 + 1 or x == x0 + 1: v = 0.7
            im.putpixel((x, y), pick(ramp, v))

def rivet(im, x, y, ramp='brass'):
    im.putpixel((x, y), pick(ramp, .95)); im.putpixel((x + 1, y), pick(ramp, .55)); im.putpixel((x, y + 1), pick(ramp, .5)); im.putpixel((x + 1, y + 1), pick(ramp, .1))

def outline(im, color=OUTLINE):
    w, h = im.size; src = im.copy(); px = src.load()
    for y in range(h):
        for x in range(w):
            if px[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and px[nx, ny][3] and px[nx, ny] != color:
                        im.putpixel((x, y), color); break

def glow(im, cx, cy, r, ramp, strength=1.0):
    """Soft additive-looking radial highlight that only recolours already-opaque or empty pixels near the centre."""
    for y in range(int(cy - r), int(cy + r) + 1):
        for x in range(int(cx - r), int(cx + r) + 1):
            d = math.hypot(x - cx, y - cy) / r
            if d <= 1 and 0 <= x < im.width and 0 <= y < im.height: im.putpixel((x, y), pick(ramp, (1 - d) * strength))

def spark(im, cx, cy, ramp, size=5):
    """Four-point star used for flare flames and gem glints."""
    for i in range(-size, size + 1):
        t = 1 - abs(i) / (size + 1)
        for (x, y) in ((cx + i, cy), (cx, cy + i)):
            if 0 <= x < im.width and 0 <= y < im.height: im.putpixel((x, y), pick(ramp, .45 + .55 * t))
    for d in (-1, 1):
        for e in (-1, 1):
            x, y = cx + d, cy + e
            if 0 <= x < im.width and 0 <= y < im.height: im.putpixel((x, y), pick(ramp, .75))
    im.putpixel((cx, cy), pick(ramp, .999))
