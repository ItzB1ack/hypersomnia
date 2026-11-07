"""HYPERSOMNIA block textures.

Перерисовывает все текстуры блоков мода в единой индустриальной палитре.
16x16 для обычных блоков; анимации сохраняют исходную раскладку кадров
(monitor_screen_on 16x16 x8, ward_ecg 32x16 x24).

Запуск: python src/tools/make_block_textures.py
"""
from PIL import Image
import os
import random

OUT = os.path.join(os.path.dirname(__file__), '..', 'main', 'resources',
                   'assets', 'hypersomnia', 'textures', 'block')
OUT = os.path.abspath(OUT)

# ---------------------------------------------------------------------------
# палитра
# ---------------------------------------------------------------------------
BLACK = (11, 13, 15)
INK = (20, 24, 27)
DARK = (31, 37, 42)
COAL = (40, 47, 52)
STEEL_D = (52, 62, 69)
STEEL = (70, 81, 89)
STEEL_L = (91, 104, 113)
STEEL_H = (119, 133, 141)
SILVER = (156, 168, 174)
WHITE = (214, 221, 222)
CREAM = (209, 207, 194)
CONCRETE_D = (77, 79, 76)
CONCRETE = (99, 101, 96)
CONCRETE_L = (124, 126, 119)
RUST_D = (78, 48, 34)
RUST = (121, 76, 50)
AMBER_D = (112, 74, 26)
AMBER = (218, 164, 72)
AMBER_L = (246, 205, 128)
RED_D = (92, 27, 30)
RED = (205, 66, 62)
RED_L = (242, 130, 116)
GREEN_D = (28, 74, 42)
GREEN = (93, 192, 112)
GREEN_L = (165, 238, 174)
CYAN_D = (19, 62, 66)
CYAN = (93, 191, 187)
CYAN_L = (170, 230, 224)
BLUE = (93, 134, 198)
BLUE_L = (154, 190, 236)
SCREEN = (9, 20, 22)
SCREEN_L = (24, 49, 52)
FABRIC = (53, 64, 66)


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


def sh(c, d):
    return tuple(max(0, min(255, int(v + d))) for v in c[:3])


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def new(w=16, h=16, color=(0, 0, 0)):
    return Image.new('RGBA', (w, h), rgba(color))


def put(px, x, y, c, a=255):
    if x < 0 or y < 0:
        return
    try:
        px[x, y] = rgba(c, a)
    except IndexError:
        pass


def rect(px, x0, y0, x1, y1, c, a=255):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(px, x, y, c, a)


def hline(px, x0, x1, y, c, a=255):
    for x in range(x0, x1 + 1):
        put(px, x, y, c, a)


def vline(px, x, y0, y1, c, a=255):
    for y in range(y0, y1 + 1):
        put(px, x, y, c, a)


def grain(img, x0, y0, x1, y1, base, amp, seed, gradient=0):
    rnd = random.Random(seed)
    px = img.load()
    h = max(1, y1 - y0)
    for y in range(y0, y1 + 1):
        g = int(gradient * ((y - y0) / h - 0.5) * 2)
        for x in range(x0, x1 + 1):
            d = rnd.randint(-amp, amp) + g
            put(px, x, y, sh(base, d))


def bevel(px, x0, y0, x1, y1, light, dark):
    hline(px, x0, x1, y0, light)
    vline(px, x0, y0, y1, light)
    hline(px, x0, x1, y1, dark)
    vline(px, x1, y0, y1, dark)


def frame(px, x0, y0, x1, y1, c):
    hline(px, x0, x1, y0, c)
    hline(px, x0, x1, y1, c)
    vline(px, x0, y0, y1, c)
    vline(px, x1, y0, y1, c)


def screws(px, points, dark=INK, light=STEEL_H):
    for x, y in points:
        put(px, x, y, dark)
        put(px, x + 1, y, light)
        put(px, x, y + 1, sh(light, -35))
        put(px, x + 1, y + 1, dark)


def metal(img, base, seed, x0=0, y0=0, x1=15, y1=15, amp=4, gradient=-4):
    grain(img, x0, y0, x1, y1, base, amp, seed, gradient)
    rnd = random.Random(seed + 99)
    px = img.load()
    for _ in range(5):
        y = rnd.randint(y0, y1)
        x0s = rnd.randint(x0, max(x0, x1 - 4))
        x1s = min(x1, x0s + rnd.randint(2, 7))
        for x in range(x0s, x1s + 1):
            put(px, x, y, sh(base, rnd.choice([5, 6, 7])))


def panel(img, x0, y0, x1, y1, base, seed, border=INK, inset=False, screw=False):
    metal(img, base, seed, x0, y0, x1, y1, 3, -4)
    px = img.load()
    if inset:
        frame(px, x0, y0, x1, y1, border)
        bevel(px, x0 + 1, y0 + 1, x1 - 1, y1 - 1, sh(base, 10), sh(base, -14))
    else:
        frame(px, x0, y0, x1, y1, border)
        bevel(px, x0 + 1, y0 + 1, x1 - 1, y1 - 1, sh(base, 12), sh(base, -18))
    if screw:
        screws(px, [(x0 + 2, y0 + 2), (x1 - 3, y0 + 2), (x0 + 2, y1 - 3), (x1 - 3, y1 - 3)])


# ---------------------------------------------------------------------------
# пиксельный шрифт 3x5
# ---------------------------------------------------------------------------
FONT = {
    '0': ('111', '101', '101', '101', '111'),
    '1': ('010', '110', '010', '010', '111'),
    '2': ('111', '001', '111', '100', '111'),
    '3': ('111', '001', '111', '001', '111'),
    '4': ('101', '101', '111', '001', '001'),
    '5': ('111', '100', '111', '001', '111'),
    '6': ('111', '100', '111', '101', '111'),
    '7': ('111', '001', '010', '010', '010'),
    '8': ('111', '101', '111', '101', '111'),
    '9': ('111', '101', '111', '001', '111'),
    'A': ('010', '101', '111', '101', '101'),
    'B': ('110', '101', '110', '101', '110'),
    'C': ('011', '100', '100', '100', '011'),
    'D': ('110', '101', '101', '101', '110'),
    'E': ('111', '100', '110', '100', '111'),
    'F': ('111', '100', '110', '100', '100'),
    'G': ('011', '100', '101', '101', '011'),
    'H': ('101', '101', '111', '101', '101'),
    'I': ('111', '010', '010', '010', '111'),
    'K': ('101', '101', '110', '101', '101'),
    'L': ('100', '100', '100', '100', '111'),
    'M': ('101', '111', '111', '101', '101'),
    'N': ('101', '111', '111', '111', '101'),
    'O': ('111', '101', '101', '101', '111'),
    'P': ('110', '101', '110', '100', '100'),
    'R': ('110', '101', '110', '101', '101'),
    'S': ('011', '100', '010', '001', '110'),
    'T': ('111', '010', '010', '010', '010'),
    'U': ('101', '101', '101', '101', '111'),
    'V': ('101', '101', '101', '101', '010'),
    'W': ('101', '101', '111', '111', '101'),
    'X': ('101', '101', '010', '101', '101'),
    'Y': ('101', '101', '010', '010', '010'),
    ' ': ('000', '000', '000', '000', '000'),
    '-': ('000', '000', '111', '000', '000'),
    ':': ('000', '010', '000', '010', '000'),
    '.': ('000', '000', '000', '000', '010'),
}


def text(px, x, y, s, color, shadow=None, spacing=1):
    cx = x
    for ch in s:
        g = FONT.get(ch.upper(), FONT[' '])
        if shadow:
            for j, row in enumerate(g):
                for i, bit in enumerate(row):
                    if bit == '1':
                        put(px, cx + i, y + j + 1, shadow)
        for j, row in enumerate(g):
            for i, bit in enumerate(row):
                if bit == '1':
                    put(px, cx + i, y + j, color)
        cx += 3 + spacing


# ---------------------------------------------------------------------------
# общие поверхности
# ---------------------------------------------------------------------------
def cabinet_side():
    img = new()
    panel(img, 0, 0, 15, 15, STEEL, 101, screw=False)
    px = img.load()
    # вертикальный шов и вентиляция
    vline(px, 8, 2, 13, sh(STEEL, -18))
    vline(px, 9, 2, 13, sh(STEEL, 8))
    for y in (4, 6, 8, 10):
        hline(px, 3, 6, y, sh(STEEL, -20))
        hline(px, 3, 6, y + 1, sh(STEEL, 10))
    # потёртости
    rnd = random.Random(7)
    for _ in range(10):
        x = rnd.randint(2, 13)
        y = rnd.randint(2, 13)
        put(px, x, y, sh(STEEL, rnd.choice([-22, -18, 14])))
    screws(px, [(2, 2), (12, 2), (2, 12), (12, 12)])
    return img


def cabinet_top():
    img = new()
    panel(img, 0, 0, 15, 15, sh(STEEL, 8), 102)
    px = img.load()
    hline(px, 2, 13, 7, sh(STEEL, -12))
    hline(px, 2, 13, 8, sh(STEEL, 14))
    for x in (4, 6, 8, 10, 12):
        hline(px, x, x + 1, 4, sh(STEEL, -20))
    screws(px, [(2, 2), (12, 2), (2, 12), (12, 12)])
    return img


def cabinet_front():
    img = new()
    panel(img, 0, 0, 15, 15, STEEL, 103)
    px = img.load()
    # три ящика
    for top in (1, 6, 11):
        rect(px, 1, top, 14, top + 3, sh(STEEL, 4))
        bevel(px, 1, top, 14, top + 3, sh(STEEL, 16), sh(STEEL, -24))
        # табличка
        rect(px, 6, top + 1, 9, top + 1, INK)
        hline(px, 6, 9, top + 2, CREAM)
        # ручка-скоба
        hline(px, 6, 9, top + 3, sh(STEEL, -30))
        hline(px, 6, 9, top + 2, sh(STEEL, 18))
    # грязь внизу
    rnd = random.Random(11)
    for x in range(2, 14):
        if rnd.random() < 0.5:
            put(px, x, 15, sh(STEEL, -16))
    return img


def drawer_inner():
    img = new()
    grain(img, 0, 0, 15, 15, (42, 39, 34), 3, 104)
    px = img.load()
    # папки
    for i, x in enumerate((2, 5, 8, 11)):
        rect(px, x, 1, x + 2, 4, (112, 100, 74))
        hline(px, x, x + 2, 1, (146, 132, 96))
        put(px, x, 1, (198, 178, 126))
        if i % 2 == 0:
            hline(px, x, x + 1, 0, (96, 86, 64))
    hline(px, 1, 14, 5, (23, 21, 19))
    return img


def desk_top():
    img = new()
    grain(img, 0, 0, 15, 15, (66, 55, 44), 3, 105, 4)
    px = img.load()
    # ламинат: прожилки
    rnd = random.Random(12)
    for y in range(0, 16, 2):
        for x in range(16):
            if rnd.random() < 0.6:
                put(px, x, y, sh((66, 55, 44), rnd.choice([-10, 9, 14])))
    frame(px, 0, 0, 15, 15, (40, 33, 27))
    hline(px, 1, 14, 1, (96, 80, 62))
    vline(px, 1, 1, 14, (88, 73, 57))
    # след от кружки
    for x, y in ((10, 11), (11, 10), (12, 10), (13, 11), (12, 12), (11, 12)):
        put(px, x, y, (53, 42, 33))
    put(px, 12, 9, (78, 62, 47))
    # царапины
    for x, y in ((3, 5), (4, 5), (5, 5), (6, 6), (2, 9), (3, 9)):
        put(px, x, y, (94, 79, 62))
    return img


def desk_front():
    img = new()
    panel(img, 0, 0, 15, 15, STEEL, 106)
    px = img.load()
    # один ящик
    rect(px, 2, 4, 13, 10, sh(STEEL, 3))
    bevel(px, 2, 4, 13, 10, sh(STEEL, 17), sh(STEEL, -25))
    hline(px, 6, 9, 6, INK)
    hline(px, 6, 9, 7, sh(STEEL, 20))
    rect(px, 6, 8, 9, 9, CREAM)
    hline(px, 7, 8, 9, sh(CREAM, -30))
    # вентиляция снизу
    for y in (12, 13):
        for x in (4, 6, 8, 10, 12):
            put(px, x, y, INK)
    # потёртости
    rnd = random.Random(13)
    for _ in range(8):
        put(px, rnd.randint(2, 13), rnd.randint(2, 13), sh(STEEL, rnd.choice([-24, 15])))
    return img


def desk_side():
    img = new()
    panel(img, 0, 0, 15, 15, STEEL, 107)
    px = img.load()
    vline(px, 8, 2, 13, sh(STEEL, -18))
    hline(px, 2, 13, 8, sh(STEEL, -12))
    for y in (4, 5, 6):
        hline(px, 3, 6, y, sh(STEEL, -22))
    rnd = random.Random(14)
    for _ in range(6):
        put(px, rnd.randint(2, 13), rnd.randint(2, 13), sh(STEEL, -20))
    return img


def desk_back():
    img = new()
    grain(img, 0, 0, 15, 15, sh(STEEL, -14), 3, 108)
    px = img.load()
    frame(px, 0, 0, 15, 15, INK)
    # вентиляционные щели
    for y in (3, 5, 7):
        rect(px, 3, y, 12, y, INK)
        hline(px, 3, 12, y + 1, sh(STEEL, -3))
    # кабельный вырез
    rect(px, 6, 11, 9, 14, INK)
    hline(px, 6, 9, 11, sh(STEEL, 8))
    return img


def chair_fabric():
    img = new()
    grain(img, 0, 0, 15, 15, FABRIC, 3, 109, 2)
    px = img.load()
    # плетение (мягкое, без резкой шахматки)
    for y in range(1, 15, 2):
        for x in range(1, 15, 2):
            put(px, x, y, sh(FABRIC, 5))
            put(px, x + 1, y + 1, sh(FABRIC, -4))
    # шов
    hline(px, 2, 13, 8, sh(FABRIC, -12))
    hline(px, 2, 13, 9, sh(FABRIC, 6))
    # потёртое пятно
    for x, y in ((6, 4), (7, 4), (8, 4), (7, 5), (8, 5), (9, 5), (10, 6)):
        put(px, x, y, sh(FABRIC, 8))
    return img


def chair_metal():
    img = new()
    grain(img, 0, 0, 15, 15, COAL, 3, 110)
    px = img.load()
    for x in (2, 5, 8, 11, 14):
        vline(px, x, 0, 15, sh(COAL, 8))
        vline(px, x + 1, 0, 15, sh(COAL, -10))
    screws(px, [(2, 2), (12, 2), (2, 12), (12, 12)], INK, STEEL_H)
    return img


# ---------------------------------------------------------------------------
# панели и индикаторы
# ---------------------------------------------------------------------------
def keypad_front():
    img = new()
    grain(img, 0, 0, 15, 15, sh(STEEL, -12), 3, 111)
    px = img.load()
    # пластина (видимая часть x3..12, y2..13)
    rect(px, 3, 2, 12, 13, sh(STEEL, 4))
    bevel(px, 3, 2, 12, 13, sh(STEEL, 20), sh(STEEL, -30))
    frame(px, 3, 2, 12, 13, INK)
    # дисплей (видимая часть x4..10, y3..4): статусные штрихи
    rect(px, 4, 3, 10, 4, SCREEN)
    hline(px, 4, 10, 3, CYAN_D)
    hline(px, 4, 10, 4, sh(SCREEN, 5))
    rect(px, 4, 3, 6, 3, GREEN_D)
    put(px, 4, 3, GREEN_L)
    put(px, 5, 3, GREEN)
    put(px, 8, 3, GREEN)
    put(px, 10, 3, AMBER)
    put(px, 8, 4, sh(GREEN_D, -10))
    rect(px, 11, 3, 11, 4, SCREEN)
    # клавиши 3x3
    for row, y in enumerate((6, 9, 12)):
        for col, x in enumerate((4, 7, 10)):
            rect(px, x, y, x + 1, y + 1, sh(STEEL, -18))
            hline(px, x, x + 1, y, sh(STEEL, 18))
            vline(px, x, y, y + 1, sh(STEEL, 10))
            # символы/точки
            put(px, x, y + 1, AMBER if row == 2 and col == 2 else STEEL_H)
    # нижние отверстия динамика
    for x in (5, 7, 9):
        put(px, x, 13, INK)
    return img


def led_texture(color, glow, locked):
    img = new(16, 16, (0, 0, 0))
    px = img.load()
    # прозрачный фон
    for y in range(16):
        for x in range(16):
            px[x, y] = (0, 0, 0, 0)
    # север (uv 11..12, 3..5)
    rect(px, 10, 2, 11, 5, glow, 70)
    rect(px, 11, 3, 11, 4, color)
    # east (uv 7..7.25) / west (uv 8.75..9)
    rect(px, 7, 3, 7, 4, glow, 55)
    rect(px, 8, 3, 8, 4, glow, 55)
    put(px, 7, 3, color)
    put(px, 8, 4, color)
    # up (uv 7..8, 7..7.25) / down (uv 7..8, 8.75..9)
    put(px, 7, 7, color)
    put(px, 7, 8, color)
    if locked:
        put(px, 11, 4, sh(color, -40))
    return img


def breaker_front():
    img = new()
    grain(img, 0, 0, 15, 15, sh(STEEL, -18), 3, 112)
    px = img.load()
    frame(px, 2, 1, 13, 14, INK)
    bevel(px, 2, 1, 13, 14, sh(STEEL, 4), sh(STEEL, -34))
    # верхний слот
    rect(px, 4, 3, 11, 4, SCREEN)
    hline(px, 4, 11, 3, CYAN_D)
    for x in (5, 7, 9):
        put(px, x, 3, GREEN)
    # предупреждающий треугольник
    tri = [(7, 5), (8, 5),
           (6, 6), (7, 6), (8, 6), (9, 6),
           (6, 7), (7, 7), (8, 7), (9, 7),
           (5, 8), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8),
           (5, 9), (6, 9), (7, 9), (8, 9), (9, 9), (10, 9),
           (4, 10), (5, 10), (6, 10), (7, 10), (8, 10), (9, 10), (10, 10), (11, 10)]
    for x, y in tri:
        put(px, x, y, AMBER)
    for x, y in [(7, 6), (7, 7), (7, 8)]:
        put(px, x, y, INK)
    put(px, 7, 9, INK)
    hline(px, 4, 11, 12, sh(STEEL, -34))
    # винты
    screws(px, [(3, 2), (11, 2), (3, 12), (11, 12)])
    # царапины
    rnd = random.Random(15)
    for _ in range(7):
        put(px, rnd.randint(3, 12), rnd.randint(2, 13), sh(STEEL, -34))
    return img


def breaker_side():
    img = new()
    grain(img, 0, 0, 15, 15, sh(STEEL, -16), 3, 113)
    px = img.load()
    vline(px, 8, 0, 15, sh(STEEL, -30))
    vline(px, 9, 0, 15, sh(STEEL, 8))
    for y in (2, 5, 8, 11, 14):
        hline(px, 1, 14, y, sh(STEEL, 4))
    # ржавая потёк
    for y in (10, 11, 12):
        put(px, 4, y, RUST_D)
    put(px, 5, 12, RUST_D)
    return img


def breaker_lamp(color, glow):
    img = new(16, 16, (0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = (0, 0, 0, 0)
    # north (7,2.5)-(9,4)
    rect(px, 7, 2, 8, 3, glow, 80)
    rect(px, 7, 2, 8, 3, color)
    # east/west
    rect(px, 5, 2, 5, 3, color)
    rect(px, 10, 2, 10, 3, color)
    # up/down
    put(px, 7, 10, color)
    put(px, 8, 10, color)
    put(px, 7, 5, color)
    put(px, 8, 5, color)
    return img


def lift_panel(floor):
    img = new()
    grain(img, 0, 0, 15, 15, sh(STEEL, -6), 3, 114 + floor)
    px = img.load()
    # видимая часть x3..12, y1..14
    rect(px, 3, 1, 12, 14, sh(STEEL, 4))
    bevel(px, 3, 1, 12, 14, sh(STEEL, 22), sh(STEEL, -32))
    frame(px, 3, 1, 12, 14, INK)
    # дисплей x4..11, y2..8
    rect(px, 4, 2, 11, 8, SCREEN)
    frame(px, 4, 2, 11, 8, sh(STEEL, -40))
    hline(px, 4, 11, 2, CYAN_D)
    hline(px, 4, 11, 8, sh(CYAN_D, -8))
    label = 'G' if floor == 0 else 'B' + str(floor)
    col = AMBER_L if floor == 0 else GREEN_L
    dim = AMBER_D if floor == 0 else GREEN_D
    w = len(label) * 4 - 1
    x = 8 - w // 2
    text(px, x, 3, label, dim)
    text(px, x, 2, label, col)
    # кнопки
    for i in range(3):
        x0 = 5 + i * 3
        rect(px, x0, 10, x0 + 1, 11, sh(STEEL, -20))
        hline(px, x0, x0 + 1, 10, sh(STEEL, 18))
        vline(px, x0, 10, 11, sh(STEEL, 12))
        if i == floor % 3:
            rect(px, x0, 10, x0, 10, GREEN)
        else:
            put(px, x0, 10, sh(STEEL, 24))
    # динамик
    for x0 in (5, 7, 9):
        put(px, x0, 13, INK)
    return img


def sign_front():
    img = new()
    grain(img, 0, 0, 15, 15, sh(COAL, -4), 2, 120)
    px = img.load()
    # видимая часть x1..14, y4..11
    rect(px, 1, 4, 14, 11, INK)
    bevel(px, 1, 4, 14, 11, sh(COAL, 16), BLACK)
    hline(px, 2, 13, 5, sh(COAL, 5))
    hline(px, 2, 13, 10, sh(COAL, -10))
    screws(px, [(3, 6), (11, 6)], BLACK, sh(COAL, 26))
    return img


def crac_front_upper():
    img = new()
    panel(img, 0, 0, 15, 15, CREAM, 121, screw=False)
    px = img.load()
    # жалюзи
    for y in (3, 5, 7):
        rect(px, 3, y, 12, y, sh(CREAM, -38))
        hline(px, 3, 12, y + 1, sh(CREAM, 14))
    # панель управления
    rect(px, 9, 8, 12, 9, sh(CREAM, -20))
    frame(px, 9, 8, 12, 9, sh(CREAM, -48))
    put(px, 10, 8, GREEN_L)
    put(px, 11, 8, GREEN_D)
    # аварийный светодиод
    put(px, 3, 9, RED)
    put(px, 4, 9, RED_D)
    # шильдик
    text(px, 1, 10, 'CRAC', (78, 80, 74), spacing=0)
    screws(px, [(2, 2), (13, 2), (2, 9), (13, 9)], sh(CREAM, -50), CREAM)
    return img


def crac_front_lower():
    img = new()
    panel(img, 0, 0, 15, 15, CREAM, 122, screw=False)
    px = img.load()
    for y in (2, 4, 6, 8):
        rect(px, 2, y, 13, y, sh(CREAM, -40))
        hline(px, 2, 13, y + 1, sh(CREAM, 16))
    # нижняя крышка
    rect(px, 3, 10, 12, 14, sh(CREAM, -8))
    bevel(px, 3, 10, 12, 14, sh(CREAM, 18), sh(CREAM, -30))
    hline(px, 6, 9, 12, INK)
    screws(px, [(2, 2), (13, 2), (2, 13), (13, 13)], sh(CREAM, -50), CREAM)
    # грязь
    for x in (4, 5, 10, 11):
        put(px, x, 15, sh(CREAM, -35))
    return img


def crac_side():
    img = new()
    panel(img, 0, 0, 15, 15, sh(CREAM, -8), 123, screw=False)
    px = img.load()
    vline(px, 11, 2, 13, sh(CREAM, -34))
    vline(px, 12, 2, 13, sh(CREAM, 14))
    for y in (3, 5, 7, 9):
        hline(px, 2, 8, y, sh(CREAM, -36))
        hline(px, 2, 8, y + 1, sh(CREAM, 14))
    screws(px, [(2, 2), (13, 2), (2, 13), (13, 13)], sh(CREAM, -50), CREAM)
    return img


def crac_top():
    img = new()
    panel(img, 0, 0, 15, 15, sh(CREAM, 6), 124, screw=False)
    px = img.load()
    # круглый вентилятор
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = (dx * dx + dy * dy) ** 0.5
            if 5.0 <= d <= 6.2:
                put(px, x, y, sh(CREAM, -30))
            elif d < 1.2:
                put(px, x, y, sh(CREAM, -42))
            elif d < 5.0 and (abs(dx) < 0.8 or abs(dy) < 0.8):
                put(px, x, y, sh(CREAM, -16))
    screws(px, [(2, 2), (12, 2), (2, 12), (12, 12)], sh(CREAM, -50), CREAM)
    return img


def ups_front_upper():
    img = new()
    panel(img, 0, 0, 15, 15, COAL, 125, screw=False)
    px = img.load()
    # дисплей
    rect(px, 3, 4, 10, 8, SCREEN)
    frame(px, 3, 4, 10, 8, INK)
    hline(px, 4, 9, 4, CYAN_D)
    text(px, 4, 4, 'ON', GREEN, spacing=0)
    # кнопки
    for x in (12, 14):
        rect(px, x, 4, x + 1, 5, sh(COAL, -18))
        hline(px, x, x + 1, 4, sh(COAL, 18))
    # индикаторы
    put(px, 3, 9, GREEN)
    put(px, 5, 9, AMBER)
    put(px, 7, 9, RED_D)
    # шильдик
    text(px, 2, 10, 'UPS', STEEL_H, spacing=0)
    screws(px, [(2, 2), (13, 2), (2, 13), (13, 13)], INK, STEEL_H)
    return img


def ups_front_lower():
    img = new()
    panel(img, 0, 0, 15, 15, sh(COAL, -4), 126, screw=False)
    px = img.load()
    for y in (2, 4, 6):
        rect(px, 2, y, 13, y, INK)
        hline(px, 2, 13, y + 1, sh(COAL, 18))
    # аккумуляторный отсек
    rect(px, 3, 9, 12, 14, sh(COAL, -20))
    bevel(px, 3, 9, 12, 14, sh(COAL, 14), INK)
    hline(px, 6, 9, 11, sh(COAL, 22))
    rect(px, 6, 11, 9, 11, INK)
    # предупреждающая полоса
    for x in (3, 5, 7, 9, 11):
        put(px, x, 8, AMBER_D)
    screws(px, [(2, 2), (13, 2), (2, 13), (13, 13)], INK, STEEL_H)
    return img


def ups_side():
    img = new()
    panel(img, 0, 0, 15, 15, COAL, 127, screw=False)
    px = img.load()
    for y in (3, 5, 7, 9):
        hline(px, 2, 13, y, INK)
        hline(px, 2, 13, y + 1, sh(COAL, 16))
    vline(px, 8, 11, 14, sh(COAL, -20))
    screws(px, [(2, 2), (13, 2), (2, 13), (13, 13)], INK, STEEL_H)
    return img


def ups_top():
    img = new()
    panel(img, 0, 0, 15, 15, sh(COAL, 6), 128, screw=False)
    px = img.load()
    for y in (4, 6, 8, 10):
        hline(px, 3, 12, y, INK)
    hline(px, 3, 12, 5, sh(COAL, 20))
    screws(px, [(2, 2), (13, 2), (2, 13), (13, 13)], INK, STEEL_H)
    return img


def battery_front_upper():
    img = new()
    panel(img, 0, 0, 15, 15, (64, 74, 71), 129, screw=False)
    px = img.load()
    # жалюзи сверху
    for y in (2, 4):
        rect(px, 3, y, 12, y, INK)
        hline(px, 3, 12, y + 1, sh((64, 74, 71), 18))
    # знак
    tri = []
    for i in range(5):
        for x in range(7 - i, 8 + i + 1):
            tri.append((x, 5 + i))
    for x, y in tri:
        put(px, x, y, AMBER)
    for x, y in [(7, 6), (7, 7), (7, 8)]:
        put(px, x, y, INK)
    put(px, 7, 9, INK)
    # шильдик
    rect(px, 3, 10, 12, 14, sh((64, 74, 71), -18))
    frame(px, 3, 10, 12, 14, INK)
    text(px, 6, 10, 'DC', CREAM, spacing=0)
    screws(px, [(2, 2), (13, 2), (2, 13), (13, 13)], INK, STEEL_H)
    return img


def battery_front_lower():
    img = new()
    panel(img, 0, 0, 15, 15, (64, 74, 71), 130, screw=False)
    px = img.load()
    # вертикальные рёбра вентиляции
    for x in (3, 5, 7, 9, 11):
        vline(px, x, 2, 8, INK)
        vline(px, x + 1, 2, 8, sh((64, 74, 71), 16))
    rect(px, 2, 9, 13, 9, sh((64, 74, 71), -26))
    # табличка
    rect(px, 3, 10, 12, 14, sh((64, 74, 71), -18))
    bevel(px, 3, 10, 12, 14, sh((64, 74, 71), 12), INK)
    text(px, 5, 10, '48', CREAM, spacing=0)
    # ржавчина
    for x, y in ((2, 14), (3, 14), (4, 15), (12, 14), (13, 14)):
        put(px, x, y, RUST_D)
    screws(px, [(2, 2), (13, 2), (2, 13), (13, 13)], INK, STEEL_H)
    return img


def battery_side():
    img = new()
    panel(img, 0, 0, 15, 15, (58, 68, 65), 131, screw=False)
    px = img.load()
    vline(px, 8, 2, 13, sh((58, 68, 65), -22))
    vline(px, 9, 2, 13, sh((58, 68, 65), 12))
    for y in (4, 6, 8):
        hline(px, 3, 6, y, INK)
    # жёлто-чёрная полоса
    for x in range(2, 14):
        put(px, x, 13, AMBER_D if (x // 2) % 2 == 0 else INK)
    screws(px, [(2, 2), (13, 2), (2, 12), (13, 12)], INK, STEEL_H)
    return img


def battery_top():
    img = new()
    panel(img, 0, 0, 15, 15, sh((64, 74, 71), 8), 132, screw=False)
    px = img.load()
    for y in (3, 5, 7):
        hline(px, 3, 12, y, INK)
        hline(px, 3, 12, y + 1, sh((64, 74, 71), 14))
    screws(px, [(2, 2), (13, 2), (2, 13), (13, 13)], INK, STEEL_H)
    return img


# ---------------------------------------------------------------------------
# серверная стойка
# ---------------------------------------------------------------------------
def server_front_lower():
    img = new()
    grain(img, 0, 0, 15, 15, (28, 33, 37), 3, 133)
    px = img.load()
    frame(px, 0, 0, 15, 15, INK)
    bevel(px, 1, 1, 14, 14, (55, 64, 70), (16, 20, 23))
    # четыре юнита
    for i, y in enumerate((2, 5, 8, 11)):
        rect(px, 1, y, 14, y + 2, (38, 45, 50))
        bevel(px, 1, y, 14, y + 2, (62, 72, 78), (20, 24, 27))
        # вентиляция
        for x in (3, 5, 7, 9):
            put(px, x, y + 1, (14, 17, 19))
        # индикаторы
        put(px, 12, y + 1, GREEN if i % 2 == 0 else AMBER)
        put(px, 13, y + 1, GREEN_D if i % 3 else RED)
    # кабели
    for x, col in ((14, BLUE), (15, AMBER)):
        for y in range(2, 14, 2):
            put(px, x, y, col)
    return img


def server_front_upper():
    img = new()
    grain(img, 0, 0, 15, 15, (28, 33, 37), 3, 134)
    px = img.load()
    frame(px, 0, 0, 15, 15, INK)
    bevel(px, 1, 1, 14, 14, (55, 64, 70), (16, 20, 23))
    # верхний юнит с массивом ламп
    rect(px, 1, 2, 14, 6, (38, 45, 50))
    bevel(px, 1, 2, 14, 6, (62, 72, 78), (20, 24, 27))
    for row in (3, 5):
        for x in (3, 5, 7, 9, 11):
            on = random.Random(135 + x * 7 + row).random() > 0.4
            put(px, x, row, GREEN_L if on else GREEN_D)
    # ручка
    rect(px, 3, 8, 12, 8, (18, 22, 25))
    hline(px, 3, 12, 9, (55, 64, 70))
    # шильдик юнита
    rect(px, 3, 10, 12, 14, (30, 36, 40))
    frame(px, 3, 10, 12, 14, INK)
    text(px, 5, 10, 'U3', AMBER_L, spacing=0)
    # кабели
    for x, col in ((14, BLUE), (15, AMBER)):
        for y in range(2, 14, 2):
            put(px, x, y, col)
    return img


def server_side():
    img = new()
    grain(img, 0, 0, 15, 15, (31, 37, 42), 3, 136)
    px = img.load()
    frame(px, 0, 0, 15, 15, INK)
    vline(px, 8, 1, 14, (18, 22, 25))
    vline(px, 9, 1, 14, (49, 57, 63))
    # вентиляция
    for y in (3, 5, 7, 9, 11):
        hline(px, 2, 6, y, (16, 20, 23))
        hline(px, 2, 6, y + 1, (52, 61, 67))
    screws(px, [(2, 2), (12, 2), (2, 12), (12, 12)], INK, (98, 110, 118))
    return img


def server_top():
    img = new()
    grain(img, 0, 0, 15, 15, (34, 40, 45), 3, 137)
    px = img.load()
    frame(px, 0, 0, 15, 15, INK)
    for y in (4, 6, 8):
        hline(px, 3, 12, y, (18, 22, 25))
        hline(px, 3, 12, y + 1, (52, 61, 67))
    # кабельный ввод
    rect(px, 6, 10, 9, 13, (14, 17, 19))
    hline(px, 6, 9, 10, (58, 68, 74))
    screws(px, [(2, 2), (12, 2), (2, 12), (12, 12)], INK, (98, 110, 118))
    return img


def rack_lamp(base, bright, accent=None):
    img = new(16, 16, (0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = (0, 0, 0, 0)
    # видимая часть x4..11, y4..11
    rect(px, 4, 4, 11, 11, (18, 22, 24))
    bevel(px, 4, 4, 11, 11, (55, 64, 70), (8, 10, 12))
    rect(px, 5, 5, 10, 10, base)
    hline(px, 5, 10, 5, bright)
    hline(px, 5, 10, 6, sh(bright, -20))
    for x in (6, 8):
        put(px, x, 8, accent or bright)
    put(px, 6, 9, sh(base, -25))
    put(px, 7, 9, sh(base, -25))
    return img


# ---------------------------------------------------------------------------
# рабочая станция / монитор
# ---------------------------------------------------------------------------
def workstation_case():
    img = new()
    metal(img, (74, 82, 86), 138, 0, 0, 15, 15, 3, -3)
    px = img.load()
    # передняя панель (0,0)-(9,6)
    rect(px, 0, 0, 9, 6, sh((74, 82, 86), 6))
    bevel(px, 0, 0, 9, 6, sh((74, 82, 86), 20), sh((74, 82, 86), -26))
    put(px, 8, 5, GREEN)
    put(px, 9, 5, GREEN_D)
    rect(px, 1, 1, 6, 1, sh((74, 82, 86), -18))
    # задняя/верхняя часть (0,9)-(9,15)
    for y in (9, 11, 13, 15):
        hline(px, 1, 8, y, INK)
        hline(px, 1, 8, y + 1, sh((74, 82, 86), 14))
    rect(px, 2, 14, 4, 15, INK)
    # правые боковины (10,0)-(13,9)
    vline(px, 12, 0, 15, sh((74, 82, 86), -18))
    vline(px, 13, 0, 15, sh((74, 82, 86), 9))
    # нога (10,8)-(15,13)
    rect(px, 10, 8, 15, 13, sh((74, 82, 86), -10))
    bevel(px, 10, 8, 15, 13, sh((74, 82, 86), 12), sh((74, 82, 86), -24))
    # трубка (10,7)-(14,11) и (1,10)-(7,14)
    rect(px, 1, 10, 7, 14, sh((74, 82, 86), -4))
    rect(px, 10, 7, 14, 11, sh((74, 82, 86), -4))
    return img


def workstation_screen():
    img = new()
    rect(img.load(), 0, 0, 15, 15, SCREEN)
    px = img.load()
    # экран (0,0)-(15,9), лёгкие скан-линии
    grain(img, 0, 0, 15, 9, SCREEN, 2, 139)
    for y in (1, 3, 5, 7, 9):
        for x in range(16):
            put(px, x, y, sh(SCREEN, 4))
    # верхняя строка состояния
    hline(px, 0, 15, 0, CYAN_D)
    put(px, 1, 0, CYAN)
    put(px, 4, 0, GREEN)
    put(px, 7, 0, GREEN_D)
    # строки "текста" из коротких штрихов
    rnd = random.Random(139)
    lines = ((2, 1, 7), (4, 1, 12), (6, 3, 8), (8, 1, 6))
    for y, x0, x1 in lines:
        x = x0
        while x <= x1:
            seg = rnd.randint(1, 3)
            for i in range(seg):
                if x + i <= x1:
                    put(px, x + i, y, GREEN if rnd.random() > 0.25 else GREEN_L)
            x += seg + rnd.randint(1, 2)
    # курсор и полоса прогресса
    rect(px, 9, 8, 10, 8, GREEN_L)
    rect(px, 12, 6, 15, 6, GREEN_D)
    put(px, 12, 6, GREEN)
    put(px, 13, 6, GREEN)
    # блик
    for x, y in ((13, 1), (14, 2), (14, 3), (15, 4)):
        put(px, x, y, sh(SCREEN, 12))
    # нижняя кромка (10..15) тёмная
    rect(px, 0, 10, 15, 15, sh(SCREEN, 4))
    hline(px, 0, 15, 10, sh(SCREEN, 12))
    vline(px, 0, 10, 15, sh(SCREEN, 7))
    return img


def workstation_keys():
    img = new()
    rect(img.load(), 0, 0, 15, 15, (24, 28, 31))
    px = img.load()
    # клавиатура (0,0)-(15,5)
    rect(px, 0, 0, 15, 5, (31, 36, 40))
    for row, y in enumerate((1, 3)):
        for x in range(1, 15, 2):
            put(px, x, y, (86, 96, 102))
            put(px, x, y + 1, (49, 57, 62))
    rect(px, 4, 5, 11, 5, (49, 57, 62))
    # мышь (0,8)-(3,11)
    rect(px, 0, 8, 3, 11, (31, 36, 40))
    rect(px, 1, 8, 2, 10, (78, 88, 94))
    hline(px, 1, 2, 8, (124, 136, 142))
    put(px, 1, 9, (37, 43, 47))
    return img


def monitor_off():
    img = new()
    rect(img.load(), 0, 0, 15, 15, sh(SCREEN, 2))
    px = img.load()
    grain(img, 0, 0, 15, 9, sh(SCREEN, 2), 2, 140)
    # слабый блик
    for x, y, d in ((12, 1, 10), (13, 2, 10), (14, 3, 8), (11, 1, 6)):
        put(px, x, y, sh(SCREEN, d))
    # рамка экрана
    frame(px, 0, 0, 15, 9, BLACK)
    # дежурный светодиод
    put(px, 14, 9, RED_D)
    # нижняя кромка
    rect(px, 0, 10, 15, 15, sh(SCREEN, 5))
    hline(px, 0, 15, 10, sh(SCREEN, 13))
    return img


def monitor_on_frames():
    frames = []
    for f in range(8):
        img = new()
        px = img.load()
        rect(px, 0, 0, 15, 15, sh(SCREEN, 2))
        rnd = random.Random(200 + f * 17)
        # снег: строки разной плотности
        for y in range(10):
            for x in range(16):
                r = rnd.random()
                if y % 2 == 0 and r < 0.55:
                    v = rnd.choice([30, 42, 56, 72])
                elif r < 0.38:
                    v = rnd.choice([22, 34, 48, 64])
                else:
                    v = rnd.choice([12, 18, 24])
                put(px, x, y, (v, v + 5, v + 7))
        # бегущая вниз полоса развёртки (2 строки)
        band = f % 8
        for y in range(band, min(10, band + 2)):
            for x in range(16):
                b = px[x, y]
                put(px, x, y, sh((b[0], b[1], b[2]), 40))
        # горизонтальный сбой
        gy = (f * 3 + 2) % 10
        for x in range(0, 16, 2):
            if rnd.random() < 0.6:
                put(px, x, gy, (92, 104, 110))
        # выпавшие блоки
        for _ in range(3):
            bx = rnd.randint(0, 12)
            by = rnd.randint(1, 7)
            rect(px, bx, by, bx + rnd.randint(1, 3), by + 1, (8, 11, 13))
        # рамка и REC
        frame(px, 0, 0, 15, 9, BLACK)
        put(px, 1, 1, RED)
        put(px, 2, 1, sh(RED, -70))
        # нижняя кромка
        rect(px, 0, 10, 15, 15, sh(SCREEN, 5))
        hline(px, 0, 15, 10, sh(SCREEN, 14))
        vline(px, 0, 10, 15, sh(SCREEN, 8))
        frames.append(img)
    return frames


# ---------------------------------------------------------------------------
# палата
# ---------------------------------------------------------------------------
def ward_frame():
    img = new()
    grain(img, 0, 0, 15, 15, (150, 158, 159), 3, 141, 2)
    px = img.load()
    frame(px, 0, 0, 15, 15, (92, 99, 101))
    hline(px, 1, 14, 1, (188, 195, 195))
    vline(px, 1, 1, 14, (180, 187, 188))
    # сварные швы и царапины
    for x, y in ((4, 6), (5, 6), (6, 7), (9, 11), (10, 11), (11, 12)):
        put(px, x, y, (120, 127, 128))
    for x, y in ((3, 13), (4, 13), (12, 3)):
        put(px, x, y, RUST_D)
    return img


def ward_sheet():
    img = new()
    grain(img, 0, 0, 15, 15, (219, 219, 209), 2, 142)
    px = img.load()
    # ткань
    for y in range(1, 15, 2):
        for x in range(1, 15, 2):
            put(px, x, y, (228, 228, 219))
            put(px, x + 1, y + 1, (205, 205, 196))
    # складки
    for x, y in ((3, 2), (4, 3), (5, 4), (11, 10), (12, 11), (10, 12)):
        put(px, x, y, (190, 191, 183))
    # бледное пятно
    for x, y in ((7, 6), (8, 6), (7, 7), (8, 7), (9, 8)):
        put(px, x, y, (203, 201, 189))
    return img


def ward_pillow():
    img = new()
    grain(img, 0, 0, 15, 15, (224, 222, 211), 2, 143)
    px = img.load()
    # шов по краю
    frame(px, 2, 2, 13, 13, (198, 196, 185))
    hline(px, 3, 12, 3, (238, 236, 226))
    vline(px, 3, 3, 12, (235, 233, 223))
    # мягкая тень
    hline(px, 3, 12, 12, (205, 202, 192))
    vline(px, 12, 3, 12, (208, 205, 195))
    # пятнышко
    for x, y in ((9, 8), (10, 8), (10, 9)):
        put(px, x, y, (204, 201, 189))
    return img


def ward_blanket():
    img = new()
    grain(img, 0, 0, 15, 15, (81, 101, 111), 2, 144)
    px = img.load()
    # плетение
    for y in range(1, 15, 2):
        for x in range(1, 15, 2):
            put(px, x, y, (96, 118, 128))
            put(px, x + 1, y + 1, (69, 87, 97))
    # тёмная кайма
    hline(px, 0, 15, 0, (56, 72, 81))
    hline(px, 0, 15, 15, (56, 72, 81))
    vline(px, 0, 0, 15, (56, 72, 81))
    vline(px, 15, 0, 15, (56, 72, 81))
    hline(px, 2, 13, 7, (62, 79, 88))
    # застарелое пятно
    for x, y in ((5, 10), (6, 10), (6, 11), (7, 11)):
        put(px, x, y, (67, 77, 81))
    return img


def ward_bezel():
    img = new()
    grain(img, 0, 0, 15, 15, (45, 50, 52), 2, 145)
    px = img.load()
    frame(px, 0, 0, 15, 15, INK)
    bevel(px, 1, 1, 14, 14, (78, 85, 87), (24, 27, 29))
    frame(px, 3, 3, 12, 12, (17, 19, 20))
    screws(px, [(2, 2), (12, 2), (2, 12), (12, 12)], INK, (112, 120, 122))
    return img


def ward_machine_case():
    img = new()
    grain(img, 0, 0, 15, 15, (200, 200, 189), 2, 146)
    px = img.load()
    frame(px, 0, 0, 15, 15, (150, 150, 141))
    bevel(px, 1, 1, 14, 14, (224, 224, 214), (168, 168, 158))
    # вентиляция
    for y in (3, 5):
        for x in (3, 5, 7, 9, 11):
            put(px, x, y, (160, 160, 150))
    # шильдик
    rect(px, 2, 9, 13, 14, (176, 176, 166))
    frame(px, 2, 9, 13, 14, (150, 150, 141))
    text(px, 3, 9, 'ECG', (96, 96, 90), spacing=0)
    # царапины
    for x, y in ((4, 12), (5, 12), (12, 4), (13, 5)):
        put(px, x, y, (176, 176, 166))
    return img


def ecg_frames():
    frames = []
    # форма одного удара: смещение вверх от базовой линии (y=12)
    beat = [0, 0, 0, 0, 1, 0, 0, -1, 0, 0, 2, 0, 0, -2, 5, 6, 5, -4, -2,
            0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
    for f in range(24):
        img = new(32, 16, (4, 14, 9))
        px = img.load()
        # сетка
        for x in range(0, 32, 4):
            vline(px, x, 0, 15, (11, 36, 23))
        for y in range(0, 16, 4):
            hline(px, 0, 31, y, (11, 36, 23))
        hline(px, 0, 31, 12, (24, 66, 40))
        # волна; сдвиг 4 px за кадр, 24 кадра = точный цикл 32
        shift = (f * 4) % 32
        for x in range(32):
            v = beat[(x + shift) % 32]
            y = 12 - v
            if v == 0:
                put(px, x, y, sh(GREEN_D, -4))
            else:
                put(px, x, y, GREEN_L)
                put(px, x, y + 1, GREEN_D)
        # подписи поверх волны
        text(px, 1, 1, 'HR', GREEN, spacing=0)
        text(px, 7, 1, '72', GREEN_L, spacing=0)
        frames.append(img)
    return frames


# ---------------------------------------------------------------------------
# запись
# ---------------------------------------------------------------------------
def save(img, name):
    path = os.path.join(OUT, name)
    img.save(path)
    print('wrote', name, img.size, img.mode)


def main():
    save(cabinet_side(), 'cabinet_side.png')
    save(cabinet_top(), 'cabinet_top.png')
    save(cabinet_front(), 'cabinet_front.png')
    save(drawer_inner(), 'cabinet_drawer_inner.png')
    save(desk_top(), 'desk_top.png')
    save(desk_front(), 'desk_front.png')
    save(desk_side(), 'desk_side.png')
    save(desk_back(), 'desk_back.png')
    save(chair_fabric(), 'chair_fabric.png')
    save(chair_metal(), 'chair_metal.png')
    save(keypad_front(), 'keypad_front.png')
    save(led_texture(RED_L, RED_D, True), 'keypad_led_locked.png')
    save(led_texture(GREEN_L, GREEN_D, False), 'keypad_led_open.png')
    save(breaker_front(), 'breaker_panel_front.png')
    save(breaker_side(), 'breaker_panel_side.png')
    save(breaker_lamp(GREEN_L, GREEN_D), 'breaker_panel_lamp_on.png')
    save(breaker_lamp(STEEL_D, INK), 'breaker_panel_lamp_off.png')
    save(breaker_lamp(RED_L, RED_D), 'breaker_panel_lamp_tripped.png')
    for floor in range(6):
        save(lift_panel(floor), 'lift_panel_front_%d.png' % floor)
    save(sign_front(), 'lab_sign_front.png')
    save(crac_front_upper(), 'crac_front_upper.png')
    save(crac_front_lower(), 'crac_front_lower.png')
    save(crac_side(), 'crac_side.png')
    save(crac_top(), 'crac_top.png')
    save(ups_front_upper(), 'ups_front_upper.png')
    save(ups_front_lower(), 'ups_front_lower.png')
    save(ups_side(), 'ups_side.png')
    save(ups_top(), 'ups_top.png')
    save(battery_front_upper(), 'battery_front_upper.png')
    save(battery_front_lower(), 'battery_front_lower.png')
    save(battery_side(), 'battery_side.png')
    save(battery_top(), 'battery_top.png')
    save(server_front_lower(), 'server_rack_front_lower.png')
    save(server_front_upper(), 'server_rack_front_upper.png')
    save(server_side(), 'server_rack_side.png')
    save(server_top(), 'server_rack_top.png')
    save(rack_lamp(GREEN_D, GREEN_L), 'server_rack_lamp.png')
    save(rack_lamp(AMBER_D, AMBER_L, RED), 'server_rack_lamp_hold.png')
    save(workstation_case(), 'workstation_case.png')
    save(workstation_screen(), 'workstation_screen.png')
    save(workstation_keys(), 'workstation_keys.png')
    save(monitor_off(), 'monitor_screen_off.png')
    frames = monitor_on_frames()
    sheet = Image.new('RGBA', (16, 16 * len(frames)))
    for i, fr in enumerate(frames):
        sheet.paste(fr, (0, i * 16))
    save(sheet, 'monitor_screen_on.png')
    save(ward_frame(), 'ward_frame.png')
    save(ward_sheet(), 'ward_sheet.png')
    save(ward_pillow(), 'ward_pillow.png')
    save(ward_blanket(), 'ward_blanket.png')
    save(ward_bezel(), 'ward_bezel.png')
    save(ward_machine_case(), 'ward_machine_case.png')
    ecg = ecg_frames()
    sheet = Image.new('RGB', (32, 16 * len(ecg)))
    for i, fr in enumerate(ecg):
        sheet.paste(fr.convert('RGB'), (0, i * 16))
    save(sheet, 'ward_ecg.png')


if __name__ == '__main__':
    main()
