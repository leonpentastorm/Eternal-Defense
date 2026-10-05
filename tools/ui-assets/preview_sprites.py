import sys; sys.path.insert(0, '.')
from sprites import SPRITES
from PIL import Image
sheet = Image.new('RGBA', (len(SPRITES) * 200 + 10, 420), (40, 50, 60, 255))
for i, (n, f) in enumerate(SPRITES.items()):
    im = f(); sheet.paste(im.resize((192, 192), Image.NEAREST), (i * 200 + 8, 8), im.resize((192, 192), Image.NEAREST))
    sheet.paste(im, (i * 200 + 8, 220), im); sheet.paste(im.resize((32, 32), Image.NEAREST), (i * 200 + 90, 220), im.resize((32, 32), Image.NEAREST))
    sheet.paste(im.resize((16, 16), Image.LANCZOS), (i * 200 + 140, 220), im.resize((16, 16), Image.LANCZOS))
sheet.save(sys.argv[1])
