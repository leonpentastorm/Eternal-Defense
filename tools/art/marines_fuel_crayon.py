# Marines Fuel: a 16 x 16 wax crayon lying corner to corner (flat end bottom left, point top right): olive wax, a tan paper
# wrapper with two dark printed bands and a stencilled mark, a vanilla-style dark outline. u runs along the crayon, v across it.
from PIL import Image
import sys
W = 16
img = Image.new('RGBA', (W, W), (0, 0, 0, 0)); px = img.load()
WAX = {-2: (0xb9,0xd0,0x6a), -1: (0x9c,0xb6,0x4f), 0: (0x7f,0x98,0x3c), 1: (0x64,0x7a,0x2e), 2: (0x4c,0x5d,0x22)}
PAPER = {-2: (0xf0,0xe2,0xb8), -1: (0xdf,0xcd,0x9b), 0: (0xc9,0xb4,0x80), 1: (0xaf,0x99,0x66), 2: (0x8f,0x7b,0x4f)}
BAND = {-2: (0x3a,0x44,0x1e), -1: (0x31,0x3a,0x19), 0: (0x29,0x30,0x15), 1: (0x22,0x28,0x11), 2: (0x1c,0x21,0x0e)}
OUT = (0x1b,0x1f,0x10)
C = 15        # the axis: x + y = 15
for y in range(W):
    for x in range(W):
        u, v = x - y, x + y - C
        if u < -11 or u > 12: continue
        half = 2 if u <= 6 else {7: 2, 8: 1, 9: 1, 10: 0, 11: 0, 12: -1}[u]
        if abs(v) > half: continue
        if u <= -9 or u >= 6: pal = WAX                      # bare wax: the flat end, the body and the point
        elif u in (-8, -7) or u in (3, 4): pal = BAND        # the two printed bands
        else: pal = PAPER
        c = pal[max(-2, min(2, v))]
        if pal is PAPER and u in (-3, -2, -1) and v in (-1, 0, 1): c = BAND[v]   # the stencilled mark
        if u >= 9: c = WAX[-2] if v <= 0 else WAX[0]                              # the worn point catches the light
        px[x, y] = c + (255,)
out = img.copy(); po = out.load()
for y in range(W):
    for x in range(W):
        if px[x, y][3]: continue
        if any(0 <= x+dx < W and 0 <= y+dy < W and px[x+dx, y+dy][3] for dx, dy in ((1,0),(-1,0),(0,1),(0,-1))):
            po[x, y] = OUT + (255,)
out.save(sys.argv[1]); out.resize((256, 256), Image.NEAREST).save(sys.argv[2])
