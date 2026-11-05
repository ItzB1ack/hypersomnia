"""Текстуры рабочей станции CRADLE WORKSTATION (блок hypersomnia:workstation), 16x16.
workstation_case   — корпус ЭЛТ-монитора: лицевая рамка с лампой питания, бока, решётка вентиляции.
workstation_screen — экран: рабочий стол CRADLE (обои, два значка, панель задач). Светится (neoforge_data в модели).
workstation_keys   — клавиатура и мышь.
Раскладка областей совпадает с uv в models/block/workstation.json: менять только вместе.
Запуск из корня: python run_tool.py make_workstation"""
import os
import random
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hypersomnia", "textures", "block")

# Палитра мода (ARCHIVEPROMT): #03050A, #1E2A34, #4A5E70, #DDE6EC. Пластик корпуса — холодный серый.
GLASS = (3, 5, 10)
PANEL = (30, 42, 52)
LIGHT = (74, 94, 112)
TEXT = (221, 230, 236)
PLASTIC = (138, 147, 154)
EDGE = (104, 112, 119)
SLOT = (58, 64, 70)
LED = (96, 150, 110)
PLATE = (46, 52, 58)
KEY = (150, 158, 164)

rnd = random.Random(47)


def noisy(c, amp=4):
    d = rnd.randint(-amp, amp)
    return tuple(max(0, min(255, v + d)) for v in c) + (255,)


def case():
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = noisy(PLASTIC)
    # A: лицевая рамка 10x7 (0..9, 0..6): тёмная кромка, лампа питания справа внизу
    for x in range(10):
        px[x, 0] = noisy(EDGE, 2)
        px[x, 6] = noisy(EDGE, 2)
    for y in range(7):
        px[0, y] = noisy(EDGE, 2)
        px[9, y] = noisy(EDGE, 2)
    px[8, 5] = LED + (255,)
    # C: решётка 10x7 (0..9, 9..15): прорези через строку
    for y in range(10, 16, 2):
        for x in range(1, 9):
            px[x, y] = noisy(SLOT, 2)
    return img


def screen():
    img = Image.new("RGBA", (16, 16), GLASS + (255,))
    px = img.load()
    # 16x10: обои с лёгким свечением к центру, развёртка через строку
    for y in range(10):
        for x in range(16):
            glow = max(0, 6 - abs(x - 8) - abs(y - 4)) * 2
            c = tuple(min(255, v + 10 + glow) for v in PANEL)
            if y % 2:
                c = tuple(int(v * 0.92) for v in c)
            px[x, y] = c + (255,)
    for (ix, iy) in ((1, 1), (1, 4)):          # значки (квадраты, без деталей)
        for dx in range(2):
            for dy in range(2):
                px[ix + dx, iy + dy] = TEXT + (255,)
    for x in range(16):                         # панель задач
        px[x, 9] = LIGHT + (255,)
    for x in range(0, 3):                       # кнопка LOG OFF
        px[x, 9] = (150, 166, 178, 255)
    for x in range(13, 16):                     # часы
        px[x, 9] = (110, 128, 142, 255)
    return img


def keys():
    img = Image.new("RGBA", (16, 16), PLATE + (255,))
    px = img.load()
    # 16x6: два ряда клавиш и пробел
    for x in range(1, 15, 2):
        px[x, 1] = noisy(KEY, 3)
    for x in range(2, 15, 2):
        px[x, 3] = noisy(KEY, 3)
    for x in range(4, 12):
        px[x, 5] = noisy(KEY, 3)
    for x in (1, 2, 13, 14):
        px[x, 5] = noisy(KEY, 3)
    # мышь 4x4 (0..3, 8..11): две кнопки сверху
    for y in range(8, 12):
        for x in range(4):
            px[x, y] = noisy(KEY, 3)
    px[1, 8] = noisy(EDGE, 2)
    px[1, 9] = noisy(EDGE, 2)
    return img


os.makedirs(OUT, exist_ok=True)
for name, fn in (("workstation_case", case), ("workstation_screen", screen), ("workstation_keys", keys)):
    fn().save(os.path.join(OUT, name + ".png"))
print("workstation ok")
