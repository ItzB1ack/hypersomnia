"""HYPERSOMNIA block textures, v2.

Стиль исходных текстур мода: спокойные серо-стальные поля, простые формы,
минимум деталей. Объём добавлен мягкими вертикальными градиентами, фасками,
внутренними тенями и выпуклыми клавишами/индикаторами. Без грязи и шума.

16x16 для обычных блоков; анимации сохраняют раскладку кадров
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
# палитра (снята с исходных текстур)
# ---------------------------------------------------------------------------
BLACK = (3, 5, 10)
INK = (18, 24, 30)
SLATE0 = (30, 38, 46)
SLATE1 = (41, 49, 57)
SLATE2 = (62, 70, 78)

BAT_T = (99, 107, 113)
BAT_B = (83, 91, 97)
BRK_T = (84, 96, 106)
BRK_B = (70, 82, 92)
CAB_T = (97, 109, 118)
CAB_B = (81, 93, 102)
CRAC_T = (147, 155, 161)
CRAC_B = (128, 136, 142)
CRAC_HI = (172, 179, 184)
SRV_T = (36, 48, 58)
SRV_B = (21, 31, 39)
UPS_T = (69, 77, 85)
UPS_B = (52, 60, 68)
WS_T = (147, 156, 163)
WS_B = (125, 134, 141)
WARD_T = (130, 143, 145)
WARD_B = (111, 124, 126)
DESK_T = (112, 120, 127)
DESK_B = (92, 100, 107)
DESK_FRONT = (23, 29, 35)
WHITE = (214, 221, 223)
WHITE_HI = (238, 241, 242)
RED = (126, 29, 44)
RED_HI = (198, 74, 78)
GREEN = (40, 166, 114)
GREEN_HI = (138, 226, 172)
CYAN = (72, 122, 138)
CYAN_HI = (146, 198, 208)
BLUE = (93, 134, 198)
BLUE_HI = (158, 194, 232)
AMBER = (188, 148, 56)
AMBER_HI = (232, 202, 122)


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


def sh(c, d):
    return tuple(max(0, min(255, int(v + d))) for v in c[:3])


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def new16(color=(0, 0, 0)):
    return Image.new('RGBA', (16, 16), rgba(color))


def new(w, h, color=(0, 0, 0)):
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
    if y < 0:
        return
    for x in range(x0, x1 + 1):
        put(px, x, y, c, a)


def vline(px, x, y0, y1, c, a=255):
    if x < 0:
        return
    for y in range(y0, y1 + 1):
        put(px, x, y, c, a)


def vgrad(img, x0, y0, x1, y1, top, bot):
    px = img.load()
    h = max(1, y1 - y0)
    for y in range(y0, y1 + 1):
        c = mix(top, bot, (y - y0) / h)
        for x in range(x0, x1 + 1):
            put(px, x, y, c)


def hgrad(img, x0, y0, x1, y1, left, right):
    px = img.load()
    w = max(1, x1 - x0)
    for x in range(x0, x1 + 1):
        c = mix(left, right, (x - x0) / w)
        for y in range(y0, y1 + 1):
            put(px, x, y, c)


def bevel(img, x0, y0, x1, y1, top, bot, inset=False, strength=1.0):
    """Заливка с фаской: выпуклая или утопленная."""
    vgrad(img, x0, y0, x1, y1, top, bot)
    px = img.load()
    hi = 24 * strength
    lo = 24 * strength
    if inset:
        hline(px, x0, x1, y0, sh(top, -lo))
        vline(px, x0, y0, y1, sh(top, -lo * 0.6))
        hline(px, x0, x1, y1, sh(bot, hi))
        vline(px, x1, y0, y1, sh(bot, hi * 0.6))
        if x1 - x0 > 2 and y1 - y0 > 2:
            hline(px, x0 + 1, x1 - 1, y0 + 1, sh(top, -lo * 0.4))
            hline(px, x0 + 1, x1 - 1, y1 - 1, sh(bot, hi * 0.4))
    else:
        hline(px, x0, x1, y0, sh(top, hi))
        vline(px, x0, y0, y1, sh(top, hi * 0.6))
        hline(px, x0, x1, y1, sh(bot, -lo))
        vline(px, x1, y0, y1, sh(bot, -lo * 0.6))
        if x1 - x0 > 2 and y1 - y0 > 2:
            hline(px, x0 + 1, x1 - 1, y0 + 1, sh(top, hi * 0.35))
            hline(px, x0 + 1, x1 - 1, y1 - 1, sh(bot, -lo * 0.35))


def drop_shadow(img, x0, y0, x1, y1, base, depth=1):
    px = img.load()
    for d in range(1, depth + 1):
        c = sh(base, -15 * d)
        hline(px, x0 + d, x1 + d, y1 + d, c)
        vline(px, x1 + d, y0 + d, y1 + d, c)


def plate(img, x0, y0, x1, y1, top, bot, base=None, depth=1):
    if base is not None and depth:
        drop_shadow(img, x0, y0, x1, y1, base, depth)
    bevel(img, x0, y0, x1, y1, top, bot)


def slot_h(img, x0, y, x1, base, depth=26):
    """Утопленная горизонтальная щель/жалюзи."""
    px = img.load()
    rect(px, x0, y, x1, y + 1, sh(base, -depth))
    hline(px, x0, x1, y, sh(base, -depth - 12))
    hline(px, x0, x1, y + 1, sh(base, depth * 0.55))


def slot_v(img, x, y0, x1, y1, base, depth=26):
    """Утопленный вертикальный канал."""
    px = img.load()
    rect(px, x, y0, x1, y1, sh(base, -depth))
    vline(px, x, y0, y1, sh(base, -depth - 12))
    vline(px, x1, y0, y1, sh(base, depth * 0.55))


def key2(px, x, y, base, lit=None):
    """Выпуклая клавиша 2x2 с бликом и тенью."""
    c = lit if lit else base
    put(px, x + 1, y + 1, sh(c, -40))
    put(px, x, y, sh(c, 24))
    put(px, x + 1, y, sh(c, 4))
    put(px, x, y + 1, sh(c, -8))
    put(px, x + 1, y + 1, sh(c, -24))


def screw(img, x, y, base):
    px = img.load()
    put(px, x, y, sh(base, -40))
    put(px, x + 1, y, sh(base, 28))
    put(px, x, y + 1, sh(base, 20))
    put(px, x + 1, y + 1, sh(base, -30))
    put(px, x, y, sh(base, -52))


def indicator(img, x, y, color, bright=None):
    px = img.load()
    put(px, x, y, bright or color)
    put(px, x + 1, y, color)
    put(px, x, y + 1, sh(color, -26))
    put(px, x + 1, y + 1, sh(color, -44))


def dome(img, x0, y0, x1, y1, color):
    """Выпуклый индикатор/лампа с бликом сверху и тенью снизу."""
    px = img.load()
    rect(px, x0, y0, x1, y1, color)
    hline(px, x0, x1, y0, sh(color, 55))
    vline(px, x0, y0, y1, sh(color, 25))
    hline(px, x0, x1, y1, sh(color, -35))
    vline(px, x1, y0, y1, sh(color, -20))
    put(px, x0 + 1, y0 + 1, sh(color, 90))


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
# батарейный шкаф
# ---------------------------------------------------------------------------
def battery_front_upper():
    im = new16()
    bevel(im, 0, 0, 15, 15, BAT_T, BAT_B)
    px = im.load()
    for y0 in (2, 4):
        slot_h(im, 4, y0, 12, BAT_T)
    # предупреждающий треугольник
    for i in range(6):
        y = 6 + i
        for x in range(7 - i, 8 + i + 1):
            put(px, x, y, WHITE if x < 7 else sh(WHITE, -14))
    for x in range(2, 14):
        put(px, x, 12, sh(BAT_B, -18))
    for y in (7, 8, 9):
        put(px, 7, y, SLATE1)
    put(px, 7, 10, SLATE1)
    for p in ((2, 2), (13, 2), (2, 13), (13, 13)):
        screw(im, p[0], p[1], BAT_T)
    return im


def battery_front_lower():
    im = new16()
    bevel(im, 0, 0, 15, 15, BAT_T, BAT_B)
    px = im.load()
    for x in (3, 6, 9, 12):
        for y in (1, 4, 7, 10):
            slot_h(im, x, y, x + 1, BAT_T)
    # светлая ручка/шильдик
    plate(im, 12, 4, 14, 8, (176, 183, 188), (146, 153, 158), BAT_B)
    for p in ((2, 2), (13, 2), (2, 13), (13, 13)):
        screw(im, p[0], p[1], BAT_T)
    # цоколь
    rect(px, 0, 14, 15, 15, sh(BAT_B, -14))
    hline(px, 0, 15, 14, sh(BAT_B, 16))
    return im


def battery_side():
    im = new16()
    bevel(im, 0, 0, 15, 15, BAT_T, BAT_B)
    bevel(im, 1, 1, 14, 14, BAT_T, BAT_B, inset=True)
    px = im.load()
    slot_h(im, 2, 5, 13, BAT_B)
    slot_h(im, 2, 10, 13, BAT_B)
    for p in ((2, 2), (12, 2), (2, 12), (12, 12)):
        screw(im, p[0], p[1], BAT_T)
    return im


def battery_top():
    im = new16()
    bevel(im, 0, 0, 15, 15, sh(BAT_T, 6), sh(BAT_B, 2))
    bevel(im, 1, 1, 14, 14, sh(BAT_T, 4), sh(BAT_B, 2), inset=True)
    px = im.load()
    slot_h(im, 3, 7, 12, BAT_T)
    for p in ((2, 2), (12, 2), (2, 12), (12, 12)):
        screw(im, p[0], p[1], sh(BAT_T, 4))
    return im


# ---------------------------------------------------------------------------
# рубильник / панель предохранителей
# ---------------------------------------------------------------------------
def breaker_front():
    im = new16()
    bevel(im, 0, 0, 15, 15, BRK_T, BRK_B)
    bevel(im, 2, 1, 13, 14, BRK_T, BRK_B, inset=True)
    px = im.load()
    # окно статуса
    slot_h(im, 4, 3, 11, BRK_B, depth=34)
    for x in (5, 7, 9):
        put(px, x, 3, GREEN)
    # треугольник
    for i in range(6):
        y = 5 + i
        for x in range(7 - i, 8 + i + 1):
            put(px, x, y, WHITE if x < 7 else sh(WHITE, -14))
    for x in range(2, 14):
        put(px, x, 11, sh(BRK_B, -16))
    for y in (6, 7, 8):
        put(px, 7, y, SLATE1)
    put(px, 7, 9, SLATE1)
    slot_h(im, 3, 12, 12, BRK_B)
    for p in ((3, 2), (11, 2), (3, 12), (11, 12)):
        screw(im, p[0], p[1], BRK_T)
    return im


def breaker_side():
    im = new16()
    bevel(im, 0, 0, 15, 15, BRK_T, BRK_B)
    px = im.load()
    slot_h(im, 0, 1, 15, BRK_B)
    slot_h(im, 0, 13, 15, BRK_B)
    slot_v(im, 8, 3, 9, 11, BRK_B)
    for p in ((2, 4), (13, 4), (2, 10), (13, 10)):
        screw(im, p[0], p[1], BRK_T)
    return im


def breaker_lamp(color, bright):
    im = new(16, 16, sh(color, -55))
    px = im.load()
    dome(im, 7, 2, 8, 3, color)
    put(px, 7, 2, bright)
    dome(im, 5, 2, 5, 3, color)
    dome(im, 10, 2, 10, 3, color)
    dome(im, 7, 10, 8, 10, color)
    dome(im, 7, 5, 8, 5, color)
    return im


# ---------------------------------------------------------------------------
# шкаф
# ---------------------------------------------------------------------------
def cabinet_front():
    im = new16()
    bevel(im, 0, 0, 15, 15, CAB_T, CAB_B)
    px = im.load()
    for top in (0, 5, 10):
        plate(im, 1, top, 14, top + 4, CAB_T, CAB_B, CAB_B)
        # ручка-скоба
        plate(im, 5, top + 1, 10, top + 1, (170, 177, 182), (140, 147, 152), CAB_B)
        slot_h(im, 5, top + 3, 10, CAB_B)
    return im


def cabinet_side():
    im = new16()
    bevel(im, 0, 0, 15, 15, CAB_T, CAB_B)
    bevel(im, 2, 2, 13, 13, CAB_T, CAB_B, inset=True)
    px = im.load()
    slot_v(im, 7, 4, 8, 11, CAB_B)
    for p in ((2, 2), (13, 2), (2, 13), (13, 13)):
        screw(im, p[0], p[1], CAB_T)
    return im


def cabinet_top():
    im = new16()
    bevel(im, 0, 0, 15, 15, sh(CAB_T, 6), sh(CAB_B, 2))
    bevel(im, 1, 1, 14, 14, sh(CAB_T, 6), sh(CAB_B, 2), inset=True)
    px = im.load()
    slot_h(im, 3, 7, 12, CAB_T)
    for p in ((2, 2), (12, 2), (2, 12), (12, 12)):
        screw(im, p[0], p[1], sh(CAB_T, 6))
    return im


def cabinet_drawer_inner():
    im = new16()
    bevel(im, 0, 0, 15, 15, (38, 34, 29), (24, 22, 19))
    px = im.load()
    papers = ((2, 1, 5, 4), (7, 1, 10, 4), (10, 1, 13, 4),
              (3, 6, 6, 9), (8, 6, 11, 9))
    for i, (x0, y0, x1, y1) in enumerate(papers):
        c = (172, 152, 108) if i % 2 == 0 else (146, 128, 90)
        plate(im, x0, y0, x1, y1, sh(c, 12), sh(c, -14), (30, 27, 22))
        hline(px, x0 + 1, x1 - 1, y0 + 1, sh(c, 30))
    hline(px, 1, 14, 11, (16, 15, 13))
    return im


# ---------------------------------------------------------------------------
# клавиатура кода
# ---------------------------------------------------------------------------
def keypad_front():
    im = new16()
    bevel(im, 0, 0, 15, 15, BRK_T, BRK_B)
    plate(im, 3, 2, 12, 13, BRK_T, BRK_B, BRK_B)
    px = im.load()
    # дисплей
    slot_h(im, 4, 3, 10, BRK_B, depth=36)
    put(px, 5, 3, CYAN_HI)
    put(px, 6, 3, CYAN)
    put(px, 9, 3, GREEN)
    # клавиши 3x3
    for ry in (6, 9, 12):
        for cx in (4, 7, 10):
            key2(px, cx, ry, (152, 160, 167))
            put(px, cx, ry + 1, sh(BRK_B, -34))
    # динамик
    for x in (5, 7, 9):
        put(px, x, 13, sh(BRK_B, -30))
    return im


def keypad_led(color, bright):
    im = new(16, 16, sh(color, -55))
    dome(im, 11, 3, 11, 4, color)
    put(im.load(), 11, 3, bright)
    dome(im, 7, 3, 7, 4, color)
    put(im.load(), 7, 3, bright)
    dome(im, 8, 3, 8, 4, color)
    put(im.load(), 8, 4, bright)
    dome(im, 7, 7, 7, 7, color)
    dome(im, 7, 8, 7, 8, color)
    return im


# ---------------------------------------------------------------------------
# панель лифта
# ---------------------------------------------------------------------------
def lift_panel(floor):
    im = new16()
    bevel(im, 0, 0, 15, 15, BRK_T, BRK_B)
    plate(im, 3, 1, 12, 14, BRK_T, BRK_B, BRK_B)
    px = im.load()
    # табло
    bevel(im, 4, 2, 11, 8, sh(BRK_B, -28), sh(BRK_B, -18), inset=True)
    # текст
    label = 'G' if floor == 0 else 'B' + str(floor)
    col = AMBER_HI if floor == 0 else GREEN_HI
    glow = AMBER if floor == 0 else GREEN
    w = len(label) * 4 - 1
    x = 8 - w // 2
    text(px, x, 4, label, sh(glow, -50))
    text(px, x, 3, label, col)
    # кнопки
    for i in range(3):
        x0 = 5 + i * 2
        key2(px, x0, 10, (134, 144, 154),
             GREEN_HI if i == floor % 3 else None)
    for x0 in (5, 7, 9):
        put(px, x0, 13, sh(BRK_B, -30))
    return im


# ---------------------------------------------------------------------------
# табличка лаборатории (текст рисует LabSignRenderer)
# ---------------------------------------------------------------------------
def sign_front():
    im = new16()
    bevel(im, 0, 0, 15, 15, sh(SRV_T, 8), SRV_B)
    bevel(im, 1, 4, 14, 11, sh(SRV_T, 4), SLATE1, inset=True)
    px = im.load()
    hline(px, 2, 13, 5, sh(SRV_T, 22))
    hline(px, 2, 13, 10, sh(SRV_B, -8))
    for p in ((3, 7), (11, 7)):
        screw(im, p[0], p[1], sh(SRV_T, 6))
    return im


# ---------------------------------------------------------------------------
# прецизионный кондиционер (CRAC)
# ---------------------------------------------------------------------------
def crac_front_upper():
    im = new16()
    bevel(im, 0, 0, 15, 15, CRAC_T, CRAC_B)
    px = im.load()
    # панель управления
    bevel(im, 3, 2, 9, 4, sh(CRAC_B, -8), sh(CRAC_B, -20), inset=True)
    for x in (4, 5, 6):
        put(px, x, 3, CYAN_HI if x == 4 else CYAN)
    put(px, 8, 3, BLUE)
    # жалюзи
    for y in (7, 9, 11, 13):
        slot_h(im, 3, y, 12, CRAC_T)
    for p in ((2, 2), (13, 2), (2, 13), (13, 13)):
        screw(im, p[0], p[1], CRAC_T)
    return im


def crac_front_lower():
    im = new16()
    bevel(im, 0, 0, 15, 15, CRAC_T, CRAC_B)
    px = im.load()
    for y in (2, 4, 6, 8):
        slot_h(im, 3, y, 12, CRAC_T)
    plate(im, 3, 10, 12, 14, sh(CRAC_T, 4), sh(CRAC_B, -2), CRAC_B)
    slot_h(im, 6, 12, 9, CRAC_B)
    rect(px, 0, 15, 15, 15, sh(CRAC_B, -18))
    for p in ((2, 2), (13, 2), (2, 13), (13, 13)):
        screw(im, p[0], p[1], CRAC_T)
    return im


def crac_side():
    im = new16()
    bevel(im, 0, 0, 15, 15, CRAC_T, CRAC_B)
    bevel(im, 1, 1, 14, 14, CRAC_T, CRAC_B, inset=True)
    px = im.load()
    for y in (3, 5, 7, 9):
        slot_h(im, 2, y, 8, CRAC_T)
    slot_v(im, 11, 3, 12, 12, CRAC_B)
    for p in ((2, 2), (13, 2), (2, 13), (13, 13)):
        screw(im, p[0], p[1], CRAC_T)
    return im


def crac_top():
    im = new16()
    bevel(im, 0, 0, 15, 15, sh(CRAC_T, 4), sh(CRAC_B, -2))
    bevel(im, 1, 1, 14, 14, sh(CRAC_T, 2), sh(CRAC_B, -2), inset=True)
    px = im.load()
    for x0 in (2, 6, 10):
        for y0 in (2, 6, 10):
            bevel(im, x0, y0, x0 + 2, y0 + 2,
                  sh(CRAC_T, 8), sh(CRAC_B, -10), inset=True)
    for p in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(px, p[0], p[1], sh(CRAC_B, -10))
    return im


# ---------------------------------------------------------------------------
# серверная стойка
# ---------------------------------------------------------------------------
def server_front_lower():
    im = new16()
    bevel(im, 0, 0, 15, 15, SRV_T, SRV_B)
    px = im.load()
    hline(px, 1, 14, 0, sh(SRV_T, 26))
    hline(px, 1, 14, 1, sh(SRV_T, 12))
    for x in (3, 6, 9, 12):
        slot_v(im, x, 2, x + 1, 12, SRV_B)
    for y in (6, 10):
        slot_h(im, 1, y, 14, SRV_B)
    for y in (3, 5, 8, 11):
        put(px, 13, y, sh(SRV_T, 60) if y % 2 else sh(SRV_T, 25))
    plate(im, 5, 13, 10, 13, (140, 148, 155), (108, 116, 123), SRV_B)
    return im


def server_front_upper():
    im = new16()
    bevel(im, 0, 0, 15, 15, SRV_T, SRV_B)
    px = im.load()
    hline(px, 1, 14, 0, sh(SRV_T, 26))
    hline(px, 1, 14, 1, sh(SRV_T, 12))
    for ry in (2, 5, 8, 11, 14):
        for cx in (3, 6, 9, 12):
            slot_h(im, cx, ry, cx + 1, SRV_B)
        put(px, 14, ry, sh(SRV_T, 70) if ry % 2 else sh(SRV_T, 30))
    return im


def server_side():
    im = new16()
    bevel(im, 0, 0, 15, 15, SRV_T, SRV_B)
    px = im.load()
    hline(px, 1, 14, 0, sh(SRV_T, 24))
    hline(px, 1, 14, 1, sh(SRV_T, 10))
    for x in (3, 6, 9, 12):
        slot_v(im, x, 2, x + 1, 14, SRV_B)
    return im


def server_top():
    im = new16()
    bevel(im, 0, 0, 15, 15, sh(SRV_T, 4), SRV_B)
    bevel(im, 1, 1, 14, 14, sh(SRV_T, 2), SRV_B, inset=True)
    px = im.load()
    slot_h(im, 3, 7, 12, SRV_B)
    for p in ((2, 2), (12, 2), (2, 12), (12, 12)):
        screw(im, p[0], p[1], sh(SRV_T, 2))
    return im


def rack_lamp(color, bright, accent=None):
    im = new(16, 16, sh(color, -50))
    dome(im, 4, 4, 11, 11, color)
    px = im.load()
    rect(px, 5, 5, 10, 10, sh(color, 6))
    hline(px, 5, 10, 5, bright)
    hline(px, 5, 10, 6, sh(bright, -25))
    if accent:
        put(px, 6, 8, accent)
        put(px, 8, 8, accent)
    else:
        put(px, 6, 8, bright)
        put(px, 8, 8, bright)
    hline(px, 6, 9, 10, sh(color, -35))
    return im


# ---------------------------------------------------------------------------
# ИБП
# ---------------------------------------------------------------------------
def ups_front_upper():
    im = new16()
    bevel(im, 0, 0, 15, 15, UPS_T, UPS_B)
    px = im.load()
    bevel(im, 2, 1, 8, 5, sh(UPS_B, -24), sh(UPS_B, -12), inset=True)
    for x in (3, 4, 5):
        put(px, x, 2, CYAN if x > 3 else CYAN_HI)
    put(px, 7, 3, BLUE)
    # кнопки
    for x in (3, 5, 7, 9):
        key2(px, x, 7, sh(UPS_T, -4), WHITE if x == 3 else None)
    # жалюзи
    for y in (10, 12, 14):
        slot_h(im, 2, y, 13, UPS_T)
    for p in ((2, 2), (13, 2), (2, 13), (13, 13)):
        screw(im, p[0], p[1], UPS_T)
    return im


def ups_front_lower():
    im = new16()
    bevel(im, 0, 0, 15, 15, UPS_T, UPS_B)
    px = im.load()
    for y in (2, 4, 6, 8):
        for x in (3, 5, 7, 9, 11):
            put(px, x, y, sh(UPS_B, -28))
            put(px, x + 1, y + 1, sh(UPS_T, 20))
    plate(im, 4, 11, 11, 11, (150, 158, 165), (118, 126, 133), UPS_B)
    slot_h(im, 2, 13, 13, UPS_T)
    for p in ((2, 2), (13, 2), (2, 13), (13, 13)):
        screw(im, p[0], p[1], UPS_T)
    return im


def ups_side():
    im = new16()
    bevel(im, 0, 0, 15, 15, UPS_T, UPS_B)
    px = im.load()
    for y in (2, 5, 8, 11):
        slot_h(im, 1, y, 14, UPS_T)
    slot_v(im, 7, 13, 8, 15, UPS_B)
    for p in ((2, 2), (13, 2), (2, 13), (13, 13)):
        screw(im, p[0], p[1], UPS_T)
    return im


def ups_top():
    im = new16()
    bevel(im, 0, 0, 15, 15, sh(UPS_T, 6), sh(UPS_B, 2))
    bevel(im, 1, 1, 14, 14, sh(UPS_T, 4), sh(UPS_B, 2), inset=True)
    px = im.load()
    for y in (4, 7, 10):
        slot_h(im, 3, y, 12, sh(UPS_T, 2))
    for p in ((2, 2), (12, 2), (2, 12), (12, 12)):
        screw(im, p[0], p[1], sh(UPS_T, 4))
    return im


# ---------------------------------------------------------------------------
# стол и стул
# ---------------------------------------------------------------------------
def desk_top():
    im = new16()
    vgrad(im, 0, 0, 15, 15, sh(DESK_T, 8), sh(DESK_B, 2))
    bevel(im, 0, 0, 15, 15, sh(DESK_T, 8), sh(DESK_B, 2))
    bevel(im, 1, 1, 14, 14, sh(DESK_T, 6), sh(DESK_B, 2), inset=True)
    px = im.load()
    for y in (4, 7, 10, 13):
        hline(px, 2, 13, y, sh(DESK_T, 16))
        hline(px, 2, 13, y + 1, sh(DESK_B, -8))
    for p in ((2, 2), (12, 2), (2, 12), (12, 12)):
        screw(im, p[0], p[1], DESK_T)
    return im


def desk_front():
    im = new16()
    vgrad(im, 0, 0, 15, 15, DESK_FRONT, sh(DESK_FRONT, -8))
    px = im.load()
    # верхняя кромка столешницы
    vgrad(im, 0, 0, 15, 2, sh(DESK_T, 8), sh(DESK_T, -4))
    hline(px, 0, 15, 0, sh(DESK_T, 28))
    hline(px, 0, 15, 2, sh(DESK_T, -18))
    # правая стойка
    vgrad(im, 10, 3, 14, 14, sh(DESK_B, 6), DESK_B)
    bevel(im, 10, 3, 14, 14, sh(DESK_B, 6), DESK_B, inset=True)
    # ящик
    bevel(im, 1, 4, 9, 14, sh(DESK_FRONT, 8), sh(DESK_FRONT, -4), inset=True)
    # ручки
    plate(im, 11, 5, 12, 6, (170, 177, 182), (138, 145, 150), DESK_B)
    plate(im, 11, 11, 12, 12, (170, 177, 182), (138, 145, 150), DESK_B)
    return im


def desk_side():
    im = new16()
    vgrad(im, 0, 0, 15, 15, sh(DESK_B, 4), sh(DESK_B, -6))
    px = im.load()
    vgrad(im, 0, 0, 15, 2, sh(DESK_T, 4), sh(DESK_T, -8))
    hline(px, 0, 15, 0, sh(DESK_T, 22))
    bevel(im, 1, 3, 14, 14, sh(DESK_B, 2), sh(DESK_B, -6), inset=True)
    hline(px, 2, 13, 10, sh(DESK_B, -14))
    hline(px, 2, 13, 11, sh(DESK_B, 10))
    return im


def desk_back():
    im = new16()
    vgrad(im, 0, 0, 15, 15, sh(DESK_B, 4), sh(DESK_B, -8))
    px = im.load()
    vgrad(im, 0, 0, 15, 2, sh(DESK_T, 4), sh(DESK_T, -10))
    hline(px, 0, 15, 0, sh(DESK_T, 20))
    slot_v(im, 7, 3, 8, 14, sh(DESK_B, -8))
    bevel(im, 1, 3, 6, 14, sh(DESK_B, 2), sh(DESK_B, -8), inset=True)
    return im


def chair_fabric():
    im = new16()
    vgrad(im, 0, 0, 15, 15, (34, 44, 54), (25, 34, 43))
    px = im.load()
    for y in range(1, 15, 2):
        for x in range(1, 15, 2):
            put(px, x, y, sh((37, 47, 57), 6))
            put(px, x + 1, y + 1, sh((29, 38, 47), -4))
    hline(px, 1, 14, 7, sh((29, 38, 47), -12))
    hline(px, 1, 14, 8, sh((40, 50, 60), 10))
    hline(px, 0, 15, 0, sh((44, 55, 66), 10))
    hline(px, 0, 15, 15, (18, 25, 32))
    return im


def chair_metal():
    im = new16()
    vgrad(im, 0, 0, 15, 15, (54, 62, 70), (42, 50, 58))
    px = im.load()
    hline(px, 0, 15, 0, (92, 103, 112))
    hline(px, 0, 15, 1, (68, 78, 86))
    for x in (2, 6, 10, 14):
        vline(px, x, 2, 15, (59, 68, 76))
        vline(px, x + 1, 2, 15, (34, 41, 48))
    for p in ((3, 3), (12, 3), (3, 12), (12, 12)):
        screw(im, p[0], p[1], (54, 62, 70))
    return im


# ---------------------------------------------------------------------------
# рабочая станция / монитор
# ---------------------------------------------------------------------------
def workstation_case():
    im = new16()
    vgrad(im, 0, 0, 15, 15, WS_T, WS_B)
    px = im.load()
    # передняя панель (uv 0..10, 0..7)
    bevel(im, 0, 0, 9, 6, sh(WS_T, 8), sh(WS_B, 2))
    slot_h(im, 1, 2, 7, WS_B)
    put(px, 8, 5, GREEN_HI)
    put(px, 9, 5, GREEN)
    # задняя часть с вентиляцией (uv 0..10, 9..16)
    for y in (10, 12, 14):
        slot_h(im, 1, y, 8, WS_B)
    # правая боковина (uv 10..14, 0..9)
    bevel(im, 10, 0, 13, 9, sh(WS_T, 2), sh(WS_B, -2))
    vline(px, 12, 1, 8, sh(WS_B, -12))
    vline(px, 13, 1, 8, sh(WS_T, 14))
    # нога / трубки (uv 10..16, 8..14 и 1..7, 10..14)
    bevel(im, 10, 8, 15, 13, sh(WS_T, -4), sh(WS_B, -6))
    bevel(im, 1, 10, 7, 14, sh(WS_T, -2), sh(WS_B, -4))
    hline(px, 0, 15, 15, sh(WS_B, -20))
    return im


def workstation_screen():
    im = new16()
    vgrad(im, 0, 0, 15, 15, (32, 42, 52), (18, 25, 33))
    px = im.load()
    # тёмная нижняя кромка (uv 10..15)
    rect(px, 0, 10, 15, 15, (8, 11, 16))
    hline(px, 0, 15, 9, (48, 64, 78))
    hline(px, 0, 15, 10, (20, 27, 34))
    # скан-линии
    for y in (1, 3, 5, 7):
        hline(px, 0, 15, y, (28, 38, 48))
    # два «значка» как в исходнике, с объёмом
    for (x, y) in ((1, 1), (1, 4)):
        plate(im, x, y, x + 2, y + 1, (224, 230, 232), (176, 184, 188), (32, 42, 52))
    put(px, 2, 2, (250, 252, 252))
    put(px, 2, 5, (250, 252, 252))
    return im


def workstation_keys():
    im = new16()
    vgrad(im, 0, 0, 15, 15, (50, 56, 62), (38, 44, 50))
    px = im.load()
    # клавиши
    for x in (1, 3, 5, 7, 9, 11, 13):
        key2(px, x, 1, (146, 154, 160))
    for x in (2, 4, 6, 8, 10, 12, 14):
        key2(px, x, 3, (140, 148, 154))
    for x in range(0, 16, 2):
        key2(px, x, 5, (132, 140, 146))
    # мышь
    plate(im, 0, 8, 3, 11, (148, 156, 162), (108, 116, 122), (40, 46, 52))
    hline(px, 1, 2, 9, (60, 67, 74))
    put(px, 1, 8, (186, 193, 198))
    return im


def monitor_off():
    im = new16()
    vgrad(im, 0, 0, 15, 9, (4, 6, 11), (2, 3, 7))
    px = im.load()
    hline(px, 0, 15, 9, (18, 24, 30))
    # лёгкий блик
    for x, y in ((12, 1), (13, 2), (14, 3)):
        put(px, x, y, (14, 18, 23))
    rect(px, 0, 10, 15, 15, (3, 4, 8))
    return im


def monitor_on_frames():
    frames = []
    shades = [(20, 28, 36), (28, 36, 44), (36, 44, 52), (44, 52, 60),
              (52, 60, 68), (62, 70, 78), (72, 80, 88)]
    for f in range(8):
        im = new16()
        px = im.load()
        rect(px, 0, 0, 15, 9, (19, 27, 35))
        rnd = random.Random(400 + f * 31)
        # статика в исходной сине-серой гамме
        for y in range(10):
            for x in range(16):
                c = shades[rnd.randrange(len(shades))]
                if y % 2 == 0:
                    c = sh(c, 6)
                put(px, x, y, c)
        # бегущая полоса
        band = f % 8
        for y in (band, band + 1):
            if y < 10:
                for x in range(16):
                    b = px[x, y]
                    put(px, x, y, sh((b[0], b[1], b[2]), 26))
        # редкие тёмные провалы
        for _ in range(4):
            bx = rnd.randint(0, 13)
            by = rnd.randint(1, 8)
            rect(px, bx, by, bx + rnd.randint(1, 2), by + 1, (12, 17, 22))
        rect(px, 0, 10, 15, 15, (3, 4, 8))
        hline(px, 0, 15, 9, (18, 24, 30))
        frames.append(im)
    return frames


# ---------------------------------------------------------------------------
# палата
# ---------------------------------------------------------------------------
def ward_frame():
    im = new16()
    vgrad(im, 0, 0, 15, 15, WARD_T, WARD_B)
    bevel(im, 0, 0, 15, 15, WARD_T, WARD_B)
    bevel(im, 1, 1, 14, 14, WARD_T, WARD_B, inset=True)
    px = im.load()
    hline(px, 2, 13, 10, sh(WARD_B, -14))
    hline(px, 2, 13, 11, sh(WARD_T, 14))
    hline(px, 2, 13, 2, sh(WARD_T, 16))
    return im


def ward_sheet():
    im = new16()
    vgrad(im, 0, 0, 15, 15, (218, 225, 221), (198, 206, 201))
    px = im.load()
    for y in (3, 7, 11):
        hline(px, 1, 14, y, (206, 213, 209))
        hline(px, 1, 14, y + 1, (226, 232, 228))
    hline(px, 0, 15, 0, (232, 238, 234))
    hline(px, 0, 15, 15, (184, 192, 188))
    vline(px, 0, 0, 15, (224, 230, 226))
    vline(px, 15, 0, 15, (188, 196, 192))
    return im


def ward_pillow():
    im = new16()
    px = im.load()
    for y in range(16):
        for x in range(16):
            dx = (x - 7.5) / 8.0
            dy = (y - 7.5) / 8.0
            d = min(1.0, (dx * dx + dy * dy) ** 0.5)
            c = mix((246, 248, 243), (214, 218, 211), d * 0.9)
            put(px, x, y, c)
    bevel(im, 1, 1, 14, 14, (246, 248, 243), (214, 218, 211), inset=True)
    hline(px, 2, 13, 3, (250, 251, 247))
    hline(px, 2, 13, 12, (206, 211, 204))
    return im


def ward_blanket():
    im = new16()
    vgrad(im, 0, 0, 15, 15, (98, 145, 150), (78, 122, 127))
    px = im.load()
    for y in range(1, 15, 2):
        for x in range(1, 15, 2):
            put(px, x, y, (104, 151, 156))
            put(px, x + 1, y + 1, (76, 119, 124))
    hline(px, 0, 15, 0, (66, 104, 109))
    hline(px, 0, 15, 15, (62, 100, 105))
    vline(px, 0, 0, 15, (58, 96, 101))
    vline(px, 15, 0, 15, (58, 96, 101))
    hline(px, 2, 13, 7, (72, 114, 119))
    hline(px, 2, 13, 8, (92, 138, 143))
    return im


def ward_bezel():
    im = new16()
    vgrad(im, 0, 0, 15, 15, (38, 50, 52), (27, 37, 39))
    bevel(im, 0, 0, 15, 15, (38, 50, 52), (27, 37, 39))
    bevel(im, 3, 3, 12, 12, (20, 29, 31), (14, 22, 24), inset=True)
    for p in ((2, 2), (12, 2), (2, 12), (12, 12)):
        screw(im, p[0], p[1], (44, 57, 59))
    return im


def ward_machine_case():
    im = new16()
    vgrad(im, 0, 0, 15, 15, (200, 210, 205), (176, 187, 181))
    bevel(im, 0, 0, 15, 15, (200, 210, 205), (176, 187, 181))
    px = im.load()
    for y in (3, 5):
        for x in (3, 5, 7, 9, 11):
            put(px, x, y, (150, 161, 155))
            put(px, x, y + 1, (214, 222, 218))
    plate(im, 3, 9, 12, 13, (186, 197, 191), (158, 169, 163), (180, 190, 184))
    put(px, 4, 11, GREEN_HI)
    put(px, 5, 11, GREEN)
    hline(px, 6, 9, 11, (146, 156, 150))
    return im


# ---------------------------------------------------------------------------
# анимация ЭКГ
# ---------------------------------------------------------------------------
def ecg_frames():
    frames = []
    # один удар: смещение вверх от базовой линии y=6
    beat = [0, 0, 0, 0, 0, 1, 0, 0, -1, 0, 0, 1, 0, 0, 2, -2,
            4, 1, -1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0]
    for f in range(24):
        im = new(32, 16, (6, 16, 22))
        px = im.load()
        # сетка
        for x in range(0, 32, 4):
            vline(px, x, 0, 15, (10, 30, 26))
        for y in range(0, 16, 4):
            hline(px, 0, 31, y, (10, 30, 26))
        hline(px, 0, 31, 6, (18, 54, 44))
        shift = (f * 4) % 32
        ys = [6 - beat[(x + shift) % 32] for x in range(32)]
        for x in range(32):
            y0 = ys[x - 1] if x > 0 else ys[0]
            y1 = ys[x]
            hi = (60, 200, 142) if ys[x] != 6 else (28, 132, 96)
            for y in range(min(y0, y1), max(y0, y1) + 1):
                put(px, x, y, hi)
            put(px, x, y1 + 1, (16, 78, 60))
        # скан-головка
        put(px, 31, ys[31] - 1, (210, 240, 228))
        put(px, 31, ys[31], (236, 250, 242))
        # подписи внизу, как в исходнике
        text(px, 1, 9, 'HR', (60, 200, 150))
        text(px, 10, 9, '72', (130, 226, 176))
        text(px, 19, 9, '98', (120, 170, 220))
        hline(px, 0, 31, 14, (8, 24, 28))
        frames.append(im)
    return frames


# ---------------------------------------------------------------------------
# запись
# ---------------------------------------------------------------------------
def save(img, name):
    img.save(os.path.join(OUT, name))
    print('wrote', name, img.size, img.mode)


def main():
    save(cabinet_side(), 'cabinet_side.png')
    save(cabinet_top(), 'cabinet_top.png')
    save(cabinet_front(), 'cabinet_front.png')
    save(cabinet_drawer_inner(), 'cabinet_drawer_inner.png')
    save(desk_top(), 'desk_top.png')
    save(desk_front(), 'desk_front.png')
    save(desk_side(), 'desk_side.png')
    save(desk_back(), 'desk_back.png')
    save(chair_fabric(), 'chair_fabric.png')
    save(chair_metal(), 'chair_metal.png')
    save(keypad_front(), 'keypad_front.png')
    save(keypad_led(RED, RED_HI), 'keypad_led_locked.png')
    save(keypad_led((203, 211, 217), (248, 250, 252)), 'keypad_led_open.png')
    save(breaker_front(), 'breaker_panel_front.png')
    save(breaker_side(), 'breaker_panel_side.png')
    save(breaker_lamp(GREEN, GREEN_HI), 'breaker_panel_lamp_on.png')
    save(breaker_lamp(SLATE0, sh(SLATE0, 30)), 'breaker_panel_lamp_off.png')
    save(breaker_lamp(RED, RED_HI), 'breaker_panel_lamp_tripped.png')
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
    save(rack_lamp(GREEN, GREEN_HI), 'server_rack_lamp.png')
    save(rack_lamp(RED, RED_HI, RED_HI), 'server_rack_lamp_hold.png')
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
