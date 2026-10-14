# Draws the Weapon Display Tuner icon, a 16x16 screwdriver: python3 tools/ui-assets/display_tuner_icon.py <out.png>
import sys
from PIL import Image
out=sys.argv[1]
cells={}
def put(a,b,col):
    # a = x-y (along the tool), b = x+y (across it); parity must match
    if (a+b)%2:return
    x,y=(a+b)//2,(b-a)//2
    if 0<=x<16 and 0<=y<16:cells[(x,y)]=col
HI,RED,RED_DK,BAND=(236,96,78),(196,48,38),(128,28,26),(36,30,32)
G_HI,G_DK,S_HI,S_DK,TIP=(176,182,190),(112,118,128),(226,232,238),(160,168,178),(96,102,112)
for a in range(-13,-3):
    across=range(14,18) if a in (-13,-12,-4) else range(13,19)
    for b in across:put(a,b,{13:HI,14:HI if a%3 else RED,17:RED_DK,18:RED_DK}.get(b,RED))
for a in (-3,-2):
    for b in range(14,18):put(a,b,BAND)
for a in (-1,0,1):
    for b in range(14,18):put(a,b,G_HI if b<=15 else G_DK)
for a in range(2,10):
    for b in (15,16):put(a,b,S_HI if b==15 else S_DK)
for a,b in ((10,16),(11,15),(12,16),(13,15)):put(a,b,TIP)
img=Image.new("RGBA",(16,16),(0,0,0,0));px=img.load()
for (x,y),c in cells.items():px[x,y]=c+(255,)
for y in range(16):
    for x in range(16):
        if (x,y) not in cells and any((x+dx,y+dy) in cells for dx,dy in ((1,0),(-1,0),(0,1),(0,-1))):px[x,y]=(24,20,22,255)
img.save(out);img.resize((256,256),Image.NEAREST).save(out.replace(".png","_x16.png"))
