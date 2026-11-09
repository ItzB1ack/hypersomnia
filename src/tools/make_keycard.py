"""Иконка ключ-карты капсул: textures/item/keycard.png (16x16). Предмет ModItems.KEYCARD, модель models/item/keycard.json.
Холодная палитра мода: тёмный корпус, магнитная полоса, светлая метка, чип. Запуск из корня: python run_tool.py make_keycard"""
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hypersomnia", "textures", "item", "keycard.png")

OUTLINE = (14, 19, 25, 255)
BODY = (74, 94, 112, 255)
BODY_LIGHT = (96, 118, 136, 255)
STRIPE = (10, 14, 18, 255)
LABEL = (221, 230, 236, 255)
CHIP = (150, 190, 205, 255)
CHIP_DARK = (92, 130, 146, 255)

im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = im.load()
X1, X2, Y1, Y2 = 1, 14, 3, 12
for y in range(Y1, Y2 + 1):
    for x in range(X1, X2 + 1):
        edge = x in (X1, X2) or y in (Y1, Y2)
        px[x, y] = OUTLINE if edge else (BODY_LIGHT if y == Y1 + 1 else BODY)
for x in (X1, X2):                                  # скруглённые углы
    for y in (Y1, Y2):
        px[x, y] = (0, 0, 0, 0)
for x in range(X1 + 1, X2):                         # магнитная полоса
    px[x, Y1 + 3] = STRIPE
    px[x, Y1 + 4] = STRIPE
for x in range(X1 + 2, X1 + 6):                     # светлая метка
    px[x, Y2 - 2] = LABEL
for y in range(Y2 - 3, Y2):                         # чип
    for x in range(X2 - 4, X2 - 1):
        px[x, y] = CHIP if (x + y) % 2 == 0 else CHIP_DARK
os.makedirs(os.path.dirname(OUT), exist_ok=True)
im.save(OUT)
print("keycard ok")
