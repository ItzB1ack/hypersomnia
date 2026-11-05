"""Иконка и модель дневника. Запуск: python run_tool.py make_diary (из корня проекта)."""
import os
from PIL import Image

RES = "src/main/resources/assets/hypersomnia"
CLEAR = (0, 0, 0, 0)
K = (18, 22, 28, 255)      # контур
C = (44, 50, 60, 255)      # обложка
D = (30, 35, 43, 255)      # корешок
L = (190, 196, 200, 255)   # ярлык
R = (122, 46, 54, 255)     # резинка

img = Image.new("RGBA", (16, 16), CLEAR)
px = img.load()
for y in range(1, 15):
    for x in range(3, 13):
        edge = x in (3, 12) or y in (1, 14)
        px[x, y] = K if edge else (D if x in (4, 5) else C)
for y in range(2, 14):
    px[10, y] = R
for x, y in [(7, 4), (8, 4), (7, 5), (8, 5), (7, 6), (8, 6)]:
    px[x, y] = L
os.makedirs(RES + "/textures/item", exist_ok=True)
os.makedirs(RES + "/models/item", exist_ok=True)
img.save(RES + "/textures/item/diary.png")
with open(RES + "/models/item/diary.json", "w") as f:
    f.write('{\n  "parent": "minecraft:item/generated",\n  "textures": { "layer0": "hypersomnia:item/diary" }\n}\n')
print("ok")