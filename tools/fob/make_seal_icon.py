#!/usr/bin/env python3
"""Draws the Warlord Seal's 16 x 16 item icon (0.0.27): a red wax seal with a gold rim and crossed blades. Our own art, not the owner's.
Usage: python3 tools/fob/make_seal_icon.py <output.png>"""
import sys
from PIL import Image
OUT=sys.argv[1]
img=Image.new('RGBA',(16,16),(0,0,0,0));px=img.load()
GOLD,GOLD_D,RED,RED_D,RED_L,STEEL,STEEL_D=(232,186,64,255),(160,112,30,255),(156,28,34,255),(96,14,22,255),(196,52,52,255),(220,224,232,255),(120,124,136,255)
cx,cy=7.5,7.5
for y in range(16):
    for x in range(16):
        d=((x-cx)**2+(y-cy)**2)**.5
        if d<=7.4:px[x,y]=GOLD_D if d>6.6 else GOLD if d>5.7 else (RED_L if x+y<12 else RED if x+y<19 else RED_D)
# the wax drip at the bottom right
for x,y in [(12,13),(13,13),(13,14),(12,14),(11,14)]:px[x,y]=RED_D
# crossed blades
for i in range(4,12):
    px[i,i]=STEEL;px[15-i,i]=STEEL
for x,y in [(4,4),(11,4)]:px[x,y]=STEEL_D
for x,y in [(4,11),(11,11),(5,10),(10,10)]:px[x,y]=GOLD
img.save(OUT)
print('wrote',OUT)
