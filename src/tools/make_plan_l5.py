"""План зала сеансов (5 этаж): 6 постов, у каждого стрелка направления взгляда. Фон прозрачный.
SEQ должен совпадать с LabTuning.TERMINAL_ARROWS. Запуск из корня: python run_tool.py make_plan_l5"""
from PIL import Image, ImageDraw, ImageFont

W, H = 176, 132
OUT = "src/main/resources/assets/hypersomnia/textures/misc/"
INK = (42, 49, 56, 255)
RED = (138, 32, 48, 255)
SEQ = "DLURRU"
# (x, y, сторона зала): 1-3 вдоль левой стены снизу вверх, 4-6 вдоль правой сверху вниз
POSTS = [(50, 100, "L"), (50, 66, "L"), (50, 32, "L"), (126, 32, "R"), (126, 66, "R"), (126, 100, "R")]
VEC = {"U": (0, -1), "D": (0, 1), "L": (-1, 0), "R": (1, 0)}

img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
font = ImageFont.load_default()

d.rectangle((38, 12, 138, 120), outline=INK, width=2)
d.line((80, 120, 96, 120), fill=(0, 0, 0, 0), width=2)           # вход
d.rectangle((73, 58, 103, 86), outline=INK, width=1)             # платформа
d.rectangle((85, 69, 91, 75), fill=RED)                          # терминал

for i, (x, y, side) in enumerate(POSTS):
    dx, dy = VEC[SEQ[i]]
    ex, ey = x + dx * 11, y + dy * 11
    d.line((x, y, ex, ey), fill=RED, width=2)
    px, py = -dy, dx                                             # перпендикуляр для наконечника
    d.line((ex, ey, ex - dx * 4 + px * 3, ey - dy * 4 + py * 3), fill=RED, width=2)
    d.line((ex, ey, ex - dx * 4 - px * 3, ey - dy * 4 - py * 3), fill=RED, width=2)
    d.ellipse((x - 4, y - 4, x + 4, y + 4), outline=INK, width=2)
    d.text((x + 8 if side == "L" else x - 14, y - 5), str(i + 1), font=font, fill=INK)

img.save(OUT + "plan_l5.png")
print("plan_l5 ok")