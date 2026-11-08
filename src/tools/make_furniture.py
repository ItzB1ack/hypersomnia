"""Текстуры мебели и щита лаборатории, 16x16 (блоки hypersomnia:desk, cabinet, monitor, chair, breaker_panel).
desk_top / desk_front / desk_side / desk_back — стол: столешница из холодного пластика, тумба с ящиками, ниша для ног.
cabinet_front / cabinet_side / cabinet_top — картотечный шкаф: три ящика с окошками под ярлык.
monitor_screen_off — погасший кинескоп (тёмное стекло); monitor_screen_on — «снег», анимация (.mcmeta).
chair_fabric / chair_metal — офисный стул: обивка сиденья и спинки, крестовина и газлифт (uv модели — автоматические).
breaker_panel_front / breaker_panel_side — дверца и корпус щита: пустой ярлык, предупреждающий треугольник;
breaker_panel_lamp_off / _on / _tripped — лампа.
keypad_front — кодовая панель замка: табло, 12 клавиш; keypad_led_locked / _open — лампа (красная = закрыто).
server_rack_* — серверная стойка B3: лицо верха (гнёзда лампы состояния и ламп адреса 8-4-2-1), лицо низа, бок, верх;
server_rack_lamp / _hold — горящие лампы (светлая; красная = буфер удержан).
lift_panel_front_0.._5 — панель лифта: табло с номером этажа (G, B1..B5) и шесть кнопок, кнопка этажа светлая.
lab_sign_front — табличка: тёмное поле в светлой рамке (текст рисует LabSignRenderer).
cabinet_drawer_inner — выдвинутый ящик шкафа сверху: кромка и бумаги.
crac_* / ups_* / battery_* — оборудование серверной B3 (LabEquipmentBlock, 2 блока): лицо верха и низа, бок, верх.
Кондиционер — светлый корпус, пульт и жалюзи; ИБП — тёмный шкаф, экран, вентиляция; батарейный шкаф — щели, знак.
Новые текстуры генерируются после старых (общий rnd): старые не меняются.
capsule_frame / capsule_lid / capsule_pad — капсула сна B5 (CapsuleBlock): корпус, полупрозрачное стекло купола, ложе.
Корпус монитора — workstation_case из make_workstation.py (тот же ЭЛТ).
Раскладка областей совпадает с uv в models/block/*.json: менять только вместе.
Запуск из корня: python run_tool.py make_furniture"""
import os
import random
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hypersomnia", "textures", "block")

# Палитра мода (ARCHIVEPROMT): #03050A, #1E2A34, #4A5E70, #DDE6EC; красный #8A2030 только для ограничений (лампа TRIPPED).
GLASS = (3, 5, 10)
PANEL = (30, 42, 52)
LIGHT = (74, 94, 112)
TEXT = (221, 230, 236)
RED = (138, 32, 48)
# Металл и пластик офиса 2000-х — холодные серые.
LAMINATE = (106, 115, 122)
LAMINATE_EDGE = (124, 133, 140)
DESK_METAL = (70, 80, 89)
CAB_METAL = (92, 104, 113)
STEEL = (80, 92, 102)
SEAM = (40, 48, 56)
DARK = (22, 28, 34)
HANDLE = (152, 161, 168)
LABEL = (186, 194, 200)
FABRIC = (36, 46, 56)
CHAIR_METAL = (52, 60, 68)

rnd = random.Random(53)


def noisy(c, amp=3):
    d = rnd.randint(-amp, amp)
    return tuple(max(0, min(255, v + d)) for v in c) + (255,)


def fill(px, x1, y1, x2, y2, c, amp=3):
    for y in range(y1, y2 + 1):
        for x in range(x1, x2 + 1):
            px[x, y] = noisy(c, amp)


def img(c, amp=3):
    im = Image.new("RGBA", (16, 16))
    fill(im.load(), 0, 0, 15, 15, c, amp)
    return im


def desk_top():
    im = img(LAMINATE, 2)
    px = im.load()
    for x in range(16):  # передняя кромка (север модели = сторона ящиков)
        px[x, 0] = noisy(LAMINATE_EDGE, 2)
    return im


def desk_front():
    im = img(DESK_METAL)
    px = im.load()
    fill(px, 0, 0, 15, 1, LAMINATE_EDGE, 2)       # кромка столешницы
    for x in range(16):
        px[x, 2] = noisy(SEAM, 2)
    fill(px, 1, 3, 8, 15, DARK, 2)                 # ниша для ног
    for y in range(3, 16):
        px[9, y] = noisy(SEAM, 2)                  # стенка тумбы
    for (y1, y2) in ((3, 8), (10, 15)):            # два ящика тумбы
        for x in range(10, 16):
            px[x, y1] = noisy(SEAM, 2)
        px[12, y1 + 2] = noisy(HANDLE, 2)
        px[13, y1 + 2] = noisy(HANDLE, 2)
    return im


def desk_side():
    im = img(DESK_METAL)
    px = im.load()
    fill(px, 0, 0, 15, 1, LAMINATE_EDGE, 2)
    for x in range(16):
        px[x, 2] = noisy(SEAM, 2)
        px[x, 15] = noisy(SEAM, 2)                 # опора у пола
    for y in range(3, 15):
        px[0, y] = noisy(SEAM, 2)
        px[15, y] = noisy(SEAM, 2)
    return im


def desk_back():
    im = img(DESK_METAL, 2)
    px = im.load()
    fill(px, 0, 0, 15, 1, LAMINATE_EDGE, 2)
    for x in range(16):
        px[x, 2] = noisy(SEAM, 2)
    for y in range(3, 16):                         # стык двух листов задней стенки
        px[8, y] = noisy(SEAM, 2)
    return im


def cabinet_front():
    im = img(CAB_METAL)
    px = im.load()
    for x in range(16):
        px[x, 0] = noisy(SEAM, 2)
    for (y1, y2) in ((1, 5), (6, 10), (11, 15)):    # три ящика: стык, окошко ярлыка, ручка
        for x in range(16):
            px[x, y2] = noisy(SEAM, 2)
        for y in range(y1, y2):
            px[0, y] = noisy(SEAM, 2)
            px[15, y] = noisy(SEAM, 2)
        for x in range(6, 10):
            px[x, y1 + 1] = noisy(LABEL, 2)
        for x in range(6, 10):
            px[x, y1 + 3] = noisy(HANDLE, 2)
        px[5, y1 + 3] = noisy(SEAM, 2)
        px[10, y1 + 3] = noisy(SEAM, 2)
    return im


def cabinet_side():
    im = img(CAB_METAL, 2)
    px = im.load()
    for x in range(16):
        px[x, 0] = noisy(SEAM, 2)
        px[x, 15] = noisy(SEAM, 2)
    for y in range(16):
        px[0, y] = noisy(SEAM, 2)
        px[15, y] = noisy(SEAM, 2)
    for y in (5, 10):                              # рёбра жёсткости
        for x in range(2, 14):
            px[x, y] = noisy((84, 95, 104), 2)
    return im


def cabinet_top():
    im = img(CAB_METAL, 2)
    px = im.load()
    for i in range(16):
        px[i, 0] = noisy(SEAM, 2)
        px[i, 15] = noisy(SEAM, 2)
        px[0, i] = noisy(SEAM, 2)
        px[15, i] = noisy(SEAM, 2)
    return im


def screen_off():
    im = Image.new("RGBA", (16, 16), GLASS + (255,))
    px = im.load()
    for y in range(10):                            # 16x10: тёмное стекло, едва заметный блик сверху слева
        for x in range(16):
            v = 9 + max(0, 4 - (x + y) // 2)
            px[x, y] = (v - 4, v - 2, v + 3, 255)
    return im


def screen_on(frames=8):
    im = Image.new("RGBA", (16, 16 * frames), GLASS + (255,))
    px = im.load()
    for f in range(frames):
        roll = (f * 3) % 10                        # медленная светлая полоса прокатки кадра
        for y in range(10):
            for x in range(16):
                n = rnd.random()
                base = 26 + int(n * n * 120)
                if abs(y - roll) <= 0:
                    base += 22
                px[x, f * 16 + y] = (base - 6, base + 2, base + 10, 255)
        for y in range(10, 16):
            for x in range(16):
                px[x, f * 16 + y] = GLASS + (255,)
    return im


def panel_front():
    im = img(STEEL)
    px = im.load()
    # Область лица корпуса: x 2..13, y 1..14 (uv модели); по краю — фальц дверцы.
    for x in range(2, 14):
        px[x, 1] = noisy(SEAM, 2)
        px[x, 14] = noisy(SEAM, 2)
    for y in range(1, 15):
        px[2, y] = noisy(SEAM, 2)
        px[13, y] = noisy(SEAM, 2)
    # Лампа (элемент модели) закрывает x 7..8, y 3; под ней — пустой ярлык щита, сбоку от центра.
    fill(px, 4, 6, 9, 6, LABEL, 2)
    # Предупреждающий треугольник с «!». Никаких парных пятен: дверца не должна читаться лицом.
    for y, (a, b) in zip(range(8, 13), ((7, 7), (6, 8), (5, 9), (4, 10), (3, 11))):
        for x in range(a, b + 1):
            px[x, y] = noisy(LABEL, 2)
    for y in (9, 10, 12):
        px[7, y] = noisy(DARK, 2)
    return im


def panel_side():
    im = img(STEEL, 2)
    px = im.load()
    for i in range(16):
        px[i, 1] = noisy(SEAM, 2)
        px[i, 14] = noisy(SEAM, 2)
    return im


def lamp(c, glow):
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            k = 1.0 if not glow else 0.85 + 0.15 * (1 - (abs(x - 7.5) + abs(y - 7.5)) / 15)
            px[x, y] = tuple(max(0, min(255, int(v * k))) for v in c) + (255,)
    return im


def chair_fabric():
    im = img(FABRIC, 2)
    px = im.load()
    for y in range(16):  # мелкое плетение обивки
        for x in range(16):
            if (x + y) % 2 == 0:
                px[x, y] = noisy(tuple(v - 4 for v in FABRIC), 1)
    return im


def chair_metal():
    im = img(CHAIR_METAL, 3)
    px = im.load()
    for x in range(16):  # блик кромки
        px[x, 0] = noisy(STEEL, 2)
    return im


os.makedirs(OUT, exist_ok=True)
for name, fn in (("desk_top", desk_top), ("desk_front", desk_front), ("desk_side", desk_side), ("desk_back", desk_back),
                 ("cabinet_front", cabinet_front), ("cabinet_side", cabinet_side), ("cabinet_top", cabinet_top),
                 ("monitor_screen_off", screen_off), ("monitor_screen_on", screen_on),
                 ("chair_fabric", chair_fabric), ("chair_metal", chair_metal),
                 ("breaker_panel_front", panel_front), ("breaker_panel_side", panel_side),
                 ("breaker_panel_lamp_off", lambda: lamp((18, 24, 30), False)),
                 ("breaker_panel_lamp_on", lambda: lamp(TEXT, True)),
                 ("breaker_panel_lamp_tripped", lambda: lamp(RED, True))):
    fn().save(os.path.join(OUT, name + ".png"))
with open(os.path.join(OUT, "monitor_screen_on.png.mcmeta"), "w", encoding="utf-8") as f:
    f.write('{\n  "animation": {\n    "frametime": 3\n  }\n}\n')


def keypad_front():
    im = img(STEEL)
    px = im.load()
    # Пластина: uv x 3..12, y 2..13; по краю — фальц.
    for x in range(3, 13):
        px[x, 2] = noisy(SEAM, 2)
        px[x, 13] = noisy(SEAM, 2)
    for y in range(2, 14):
        px[3, y] = noisy(SEAM, 2)
        px[12, y] = noisy(SEAM, 2)
    fill(px, 4, 3, 9, 4, GLASS, 1)                 # табло кода
    for x in range(5, 9):
        px[x, 4] = noisy((14, 19, 25), 1)          # погасшие разряды
    fill(px, 11, 3, 11, 4, DARK, 1)                # гнездо лампы (лампа — элемент модели)
    fill(px, 4, 6, 11, 12, DARK, 2)                # поле клавиш
    for y in (6, 8, 10, 12):                       # 3 x 4 клавиши
        for x0 in (4, 7, 10):
            px[x0, y] = noisy(HANDLE, 2)
            px[x0 + 1, y] = noisy(HANDLE, 2)
    return im


GLYPHS = {
    "G": (".##", "#..", "#.#", "#.#", ".##"),
    "B": ("##.", "#.#", "##.", "#.#", "##."),
    "1": (".#.", "##.", ".#.", ".#.", "###"),
    "2": ("##.", "..#", ".#.", "#..", "###"),
    "3": ("##.", "..#", ".#.", "..#", "##."),
    "4": ("#.#", "#.#", "###", "..#", "..#"),
    "5": ("###", "#..", "##.", "..#", "##."),
}


def lift_panel_front(stop):
    im = img(STEEL)
    px = im.load()
    # Пластина: uv x 3..12, y 1..14.
    for x in range(3, 13):
        px[x, 1] = noisy(SEAM, 2)
        px[x, 14] = noisy(SEAM, 2)
    for y in range(1, 15):
        px[3, y] = noisy(SEAM, 2)
        px[12, y] = noisy(SEAM, 2)
    # Табло x 4..11, y 2..8 (отдельный светящийся элемент модели): номер этажа шрифтом 3x5.
    fill(px, 4, 2, 11, 8, GLASS, 1)
    label = "G" if stop == 0 else "B%d" % stop
    width = 4 * len(label) - 1
    x0 = 4 + (8 - width) // 2
    for i, ch in enumerate(label):
        for dy, row in enumerate(GLYPHS[ch]):
            for dx, c in enumerate(row):
                if c == "#":
                    px[x0 + i * 4 + dx, 3 + dy] = noisy(TEXT, 2)
    # Кнопки G B1 B2 / B3 B4 B5: кнопка своего этажа светлая.
    fill(px, 4, 10, 11, 12, DARK, 2)
    for n in range(6):
        bx = 4 + (n % 3) * 3
        by = 10 if n < 3 else 12
        c = TEXT if n == stop else LIGHT
        px[bx, by] = noisy(c, 2)
        px[bx + 1, by] = noisy(c, 2)
    return im


def lab_sign_front():
    im = img(PANEL, 2)
    px = im.load()
    # Пластина: uv x 1..14, y 4..11; светлая рамка, поле текста — тёмное.
    for x in range(1, 15):
        px[x, 4] = noisy(LIGHT, 2)
        px[x, 11] = noisy(LIGHT, 2)
    for y in range(4, 12):
        px[1, y] = noisy(LIGHT, 2)
        px[14, y] = noisy(LIGHT, 2)
    return im


def cabinet_drawer_inner():
    im = img(DARK, 2)
    px = im.load()
    # Ящик сверху (uv x 1..14, y 0..3): y 0 — кромка лицевой панели, дальше стопка бумаг между стенками.
    for x in range(1, 15):
        px[x, 0] = noisy(CAB_METAL, 2)
    for y in range(1, 4):
        px[1, y] = noisy(SEAM, 2)
        px[14, y] = noisy(SEAM, 2)
        for x in range(2, 14):
            px[x, y] = noisy(LABEL if (x + y) % 5 else (160, 168, 174), 3)
    return im


# ---- серверная стойка B3 (ServerRackBlock): лицевые панели низа и верха, бока, верх ----
RACK_STATUS = (3, 1, 12, 2)          # гнездо лампы состояния (tx1, ty1, tx2, ty2), под элементом модели
RACK_BITS = ((6, 4, 9, 5), (6, 7, 9, 8), (6, 10, 9, 11), (6, 13, 9, 14))  # лампы адреса 8, 4, 2, 1 сверху вниз


def rack_frame():
    im = img(PANEL, 2)
    px = im.load()
    for i in range(16):
        px[i, 0] = noisy(LIGHT, 2)
        px[i, 15] = noisy(SEAM, 2)
        px[0, i] = noisy(SEAM, 2)
        px[15, i] = noisy(SEAM, 2)
    return im, px


def rack_front_upper():
    im, px = rack_frame()
    fill(px, 3, 1, 12, 2, GLASS, 1)
    for (x1, y1, x2, y2) in RACK_BITS:
        fill(px, x1, y1, x2, y2, GLASS, 1)
        px[x2 + 2, y1] = noisy(SEAM, 2)           # риска веса справа от лампы
    for y in range(4, 15, 2):                    # решётка обдува слева
        for x in (2, 3):
            px[x, y] = noisy(DARK, 2)
    return im


def rack_front_lower():
    im, px = rack_frame()
    for y1 in (2, 5, 8, 11):                     # отсеки накопителей
        fill(px, 2, y1, 13, y1 + 1, DARK, 2)
        for x in range(3, 13, 3):
            px[x, y1] = noisy(SEAM, 2)
        px[12, y1 + 1] = noisy(LIGHT, 3)         # погасший индикатор отсека
    fill(px, 5, 13, 10, 13, HANDLE, 4)           # ручка
    return im


def rack_side():
    im = img(PANEL, 2)
    px = im.load()
    for x in range(2, 15, 3):
        for y in range(2, 14):
            px[x, y] = noisy(DARK, 2)
    for i in range(16):
        px[i, 0] = noisy(LIGHT, 2)
        px[i, 15] = noisy(SEAM, 2)
    return im


def rack_top():
    im = img(DARK, 2)
    px = im.load()
    for i in range(16):
        for j in (0, 15):
            px[i, j] = noisy(PANEL, 2)
            px[j, i] = noisy(PANEL, 2)
    return im


for name, fn in (("keypad_front", keypad_front),
                 ("keypad_led_locked", lambda: lamp(RED, True)),
                 ("keypad_led_open", lambda: lamp(TEXT, True)),
                 *(("lift_panel_front_%d" % n, (lambda n=n: lift_panel_front(n))) for n in range(6)),
                 ("lab_sign_front", lab_sign_front),
                 ("cabinet_drawer_inner", cabinet_drawer_inner),
                 ("server_rack_front_upper", rack_front_upper),
                 ("server_rack_front_lower", rack_front_lower),
                 ("server_rack_side", rack_side),
                 ("server_rack_top", rack_top),
                 ("server_rack_lamp", lambda: lamp(TEXT, True)),
                 ("server_rack_lamp_hold", lambda: lamp(RED, True))):
    fn().save(os.path.join(OUT, name + ".png"))

# ---- оборудование серверной B3 (LabEquipmentBlock): кондиционер, шкаф ИБП, батарейный шкаф ----
CRAC_BODY = (140, 148, 154)          # светлый эмалированный корпус: читается иначе, чем тёмные стойки
CRAC_EDGE = (160, 167, 172)
CRAC_SHADE = (112, 120, 127)
CRAC_SLOT = (52, 60, 68)
UPS_BODY = (62, 70, 78)
UPS_EDGE = (84, 94, 102)
BATT_BODY = (96, 104, 110)
BATT_SHADE = (78, 86, 93)


def body(c, edge, shade):
    """Корпус: светлая кромка сверху, тёмные боковые фальцы (соседние шкафы не сливаются)."""
    im = img(c, 2)
    px = im.load()
    for i in range(16):
        px[i, 0] = noisy(edge, 2)
        px[0, i] = noisy(shade, 2)
        px[15, i] = noisy(shade, 2)
    return im, px


def louvres(px, x1, x2, y1, y2, slat, slot):
    """Горизонтальные жалюзи: щель, планка, щель..."""
    for y in range(y1, y2 + 1):
        for x in range(x1, x2 + 1):
            px[x, y] = noisy(slot if (y - y1) % 2 == 0 else slat, 2)


def plinth(px, c):
    """Цоколь: тёмная полоса у пола."""
    fill(px, 0, 14, 15, 15, c, 2)


def crac_front_upper():
    im, px = body(CRAC_BODY, CRAC_EDGE, CRAC_SHADE)
    fill(px, 3, 2, 8, 4, GLASS, 1)                 # пульт: погасший экран с остатком цифр
    for x in (4, 5, 7):
        px[x, 3] = noisy(LIGHT, 3)
    px[10, 3] = noisy(LIGHT, 3)                    # индикаторы
    px[12, 3] = noisy(DARK, 2)
    for x in range(1, 15):                         # рамка решётки
        px[x, 6] = noisy(CRAC_SHADE, 2)
    louvres(px, 2, 13, 7, 15, CRAC_EDGE, CRAC_SLOT)
    return im


def crac_front_lower():
    im, px = body(CRAC_BODY, CRAC_BODY, CRAC_SHADE)
    louvres(px, 2, 13, 0, 8, CRAC_SLOT, CRAC_EDGE)   # продолжение решётки верха: сначала планка
    for x in range(1, 15):
        px[x, 9] = noisy(CRAC_SHADE, 2)
    fill(px, 5, 11, 10, 12, LABEL, 2)              # шильдик
    px[6, 11] = noisy(SEAM, 2)
    px[8, 12] = noisy(SEAM, 2)
    plinth(px, SEAM)
    return im


def crac_side():
    im, px = body(CRAC_BODY, CRAC_BODY, CRAC_SHADE)
    for y in range(16):                            # стык панелей посередине
        px[8, y] = noisy(CRAC_SHADE, 2)
    return im


def crac_top():
    im = img(CRAC_SHADE, 2)
    px = im.load()
    fill(px, 2, 2, 13, 13, CRAC_SLOT, 2)           # решётка забора воздуха
    for i in range(2, 14):
        for j in (4, 7, 10):
            px[i, j] = noisy(CRAC_BODY, 2)
            px[j, i] = noisy(CRAC_BODY, 2)
    return im


def ups_front_upper():
    im, px = body(UPS_BODY, UPS_EDGE, SEAM)
    fill(px, 3, 2, 10, 5, GLASS, 1)                # экран ИБП: тусклые строки
    for x in (4, 5, 6, 8):
        px[x, 3] = noisy(LIGHT, 3)
    for x in (4, 5, 7, 8, 9):
        px[x, 4] = noisy(LIGHT, 3)
    px[12, 2] = noisy(LIGHT, 3)                    # лампы: одна тускло горит
    px[12, 4] = noisy(DARK, 2)
    for x in (4, 6, 8, 10):                        # кнопки под экраном
        px[x, 7] = noisy(HANDLE, 3)
    for y in (10, 12, 14):                         # вентиляционные щели
        for x in range(3, 13):
            px[x, y] = noisy(DARK, 2)
    return im


def ups_front_lower():
    im, px = body(UPS_BODY, UPS_BODY, SEAM)
    for y in range(1, 10, 2):                      # перфорация
        for x in range(3, 13, 2):
            px[x, y] = noisy(DARK, 2)
    fill(px, 13, 1, 13, 4, HANDLE, 3)              # ручка дверцы
    fill(px, 4, 11, 11, 11, LABEL, 2)              # ярлык
    plinth(px, DARK)
    return im


def ups_side():
    im, px = body(UPS_BODY, UPS_BODY, SEAM)
    for y in range(2, 14, 3):                      # рёбра
        for x in range(3, 13):
            px[x, y] = noisy(UPS_EDGE, 2)
    return im


def ups_top():
    im = img(UPS_BODY, 2)
    px = im.load()
    for i in range(16):
        for j in (0, 15):
            px[i, j] = noisy(SEAM, 2)
            px[j, i] = noisy(SEAM, 2)
    for x in range(4, 12):                         # решётка вытяжки
        for y in range(4, 12, 2):
            px[x, y] = noisy(DARK, 2)
    return im


def battery_front_upper():
    im, px = body(BATT_BODY, (112, 120, 126), BATT_SHADE)
    for y in (2, 4):                               # щели вытяжки
        for x in range(3, 13):
            px[x, y] = noisy(DARK, 2)
    # знак опасности: светлый треугольник с «!», как на щите
    for y, (a, b) in zip(range(7, 12), ((7, 8), (6, 9), (5, 10), (4, 11), (3, 12))):
        for x in range(a, b + 1):
            px[x, y] = noisy(LABEL, 2)
    for y in (8, 9, 11):
        px[7, y] = noisy(DARK, 2)
        px[8, y] = noisy(DARK, 2)
    fill(px, 4, 13, 11, 14, BATT_SHADE, 2)         # ярлык с номером ряда (пустой)
    return im


def battery_front_lower():
    im, px = body(BATT_BODY, BATT_BODY, BATT_SHADE)
    for x in range(3, 13, 3):                      # вертикальные щели
        for y in range(1, 11):
            if y % 3 != 0:
                px[x, y] = noisy(DARK, 2)
    fill(px, 13, 4, 13, 7, HANDLE, 3)              # ручка
    plinth(px, DARK)
    return im


def battery_side():
    im, px = body(BATT_BODY, BATT_BODY, BATT_SHADE)
    for y in (5, 10):
        for x in range(2, 14):
            px[x, y] = noisy(BATT_SHADE, 2)
    return im


def battery_top():
    im = img(BATT_SHADE, 2)
    px = im.load()
    for i in range(16):
        for j in (0, 15):
            px[i, j] = noisy(SEAM, 2)
            px[j, i] = noisy(SEAM, 2)
    return im


for name, fn in (("crac_front_upper", crac_front_upper), ("crac_front_lower", crac_front_lower),
                 ("crac_side", crac_side), ("crac_top", crac_top),
                 ("ups_front_upper", ups_front_upper), ("ups_front_lower", ups_front_lower),
                 ("ups_side", ups_side), ("ups_top", ups_top),
                 ("battery_front_upper", battery_front_upper), ("battery_front_lower", battery_front_lower),
                 ("battery_side", battery_side), ("battery_top", battery_top)):
    fn().save(os.path.join(OUT, name + ".png"))

# ---- капсула сна B5 (CapsuleBlock): светлый корпус и тёмное стекло крышки ----
CAPSULE_FRAME = (116, 126, 135)
CAPSULE_EDGE = (138, 147, 155)
CAPSULE_SHADE = (86, 96, 105)
CAPSULE_GLASS = (12, 19, 27)


def capsule_frame():
    im = img(CAPSULE_FRAME, 2)
    px = im.load()
    for i in range(16):                            # фальц по краю
        px[i, 0] = noisy(CAPSULE_EDGE, 2)
        px[i, 15] = noisy(CAPSULE_SHADE, 2)
        px[0, i] = noisy(CAPSULE_SHADE, 2)
        px[15, i] = noisy(CAPSULE_SHADE, 2)
    for y in range(16):                            # стык половин корпуса
        px[8, y] = noisy(CAPSULE_SHADE, 1)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):  # заклёпки
        px[x, y] = noisy(CAPSULE_EDGE, 1)
    return im


def capsule_lid():
    # Стекло крышки: полупрозрачное (render_type translucent), блик по диагонали и тёмная рамка.
    im = Image.new("RGBA", (16, 16), CAPSULE_GLASS + (130,))
    px = im.load()
    for y in range(16):                            # блик стекла по диагонали
        for x in range(16):
            v = 8 + max(0, 7 - abs((x + y) - 12))
            px[x, y] = (v + 40, v + 50, v + 62, 195)
    for i in range(16):                            # тёмная рамка стекла
        px[i, 0] = px[i, 15] = (6, 10, 15, 235)
        px[0, i] = px[15, i] = (6, 10, 15, 235)
    for x in range(2, 14):                         # поперечная стяжка
        px[x, 7] = noisy((40, 52, 64), 1)[:3] + (180,)
    return im


def capsule_pad():
    # Ложе капсулы: светлая ткань с поперечным швом и мягким краем.
    im = img((188, 192, 195), 2)
    px = im.load()
    for i in range(16):
        px[i, 0] = noisy((206, 210, 213), 2)
        px[i, 15] = noisy((166, 171, 175), 2)
        px[0, i] = noisy((172, 177, 181), 2)
        px[15, i] = noisy((172, 177, 181), 2)
    for x in range(1, 15):                         # шов поперёк ложа
        px[x, 8] = noisy((158, 164, 169), 1)
    return im


for name, fn in (("capsule_frame", capsule_frame), ("capsule_lid", capsule_lid), ("capsule_pad", capsule_pad)):
    fn().save(os.path.join(OUT, name + ".png"))
print("furniture ok")
