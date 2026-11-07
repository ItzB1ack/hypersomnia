"""Иконка дневника: 16x16, потёртая тёмная обложка, красный ремешок и застёжка.
Запуск: python src/tools/make_diary.py (нужен Pillow)."""
import os
from PIL import Image

RES = "src/main/resources/assets/hypersomnia"
CLEAR = (0, 0, 0, 0)
BLACK = (3, 5, 10, 255)
SPINE = (18, 22, 28, 255)
COVER = (34, 40, 48, 255)
COVER_HI = (50, 58, 68, 255)
COVER_DK = (24, 29, 36, 255)
RED = (122, 46, 54, 255)
RED_HI = (170, 72, 80, 255)
STEEL = (108, 116, 124, 255)
STEEL_HI = (162, 170, 176, 255)
PAGES = (196, 190, 172, 255)

img = Image.new("RGBA", (16, 16), CLEAR)
px = img.load()

# корпус
for y in range(1, 15):
    for x in range(3, 13):
        if x in (3, 12) or y in (1, 14):
            px[x, y] = BLACK
        elif x in (4, 5):
            px[x, y] = SPINE if x == 4 else (24, 29, 36, 255)
        else:
            px[x, y] = COVER

# верхняя кромка и потёртости
for x in range(6, 12):
    px[x, 2] = COVER_HI
for x, y in [(6, 6), (8, 9), (11, 5), (7, 12), (9, 11)]:
    px[x, y] = COVER_DK
for x, y in [(6, 5), (8, 8), (11, 4)]:
    px[x, y] = COVER_HI

# красный ремешок
for y in range(2, 13):
    px[10, y] = RED
    px[11, y] = (96, 34, 42, 255)
px[10, 2] = RED_HI

# застёжка
for x in range(9, 13):
    px[x, 7] = STEEL
    px[x, 8] = (78, 84, 90, 255)
px[10, 7] = STEEL_HI
px[11, 8] = BLACK
px[9, 7] = STEEL_HI

# кромка страниц снизу
for x in range(6, 12):
    px[x, 13] = PAGES
for x in range(7, 12):
    px[x, 14] = BLACK

# тиснение на обложке
for x, y in [(6, 4), (7, 4), (6, 5), (7, 5)]:
    px[x, y] = (52, 60, 68, 255)
px[6, 4] = (74, 82, 90, 255)

os.makedirs(RES + "/textures/item", exist_ok=True)
os.makedirs(RES + "/models/item", exist_ok=True)
img.save(RES + "/textures/item/diary.png")
with open(RES + "/models/item/diary.json", "w", encoding="utf-8") as f:
    f.write('{\n  "parent": "minecraft:item/generated",\n'
            '  "textures": { "layer0": "hypersomnia:item/diary" }\n}\n')
print("diary")
