"""Иконки улик HYPERSOMNIA, v2: 16x16, холодная индустриальная палитра,
мягкое объёмное затенение, читаемые силуэты. Документы, записки, детские
рисунки, кассеты, пропуск, чертёж и шприц-улика (sedation_47).

Документы = PAGES внизу; custom_model_data = индекс + 1.
Запуск: python src/tools/make_documents.py (нужен Pillow).
"""
import json
import math
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "main", "resources", "assets", "hypersomnia")
OUT = os.path.abspath(os.path.join(ASSETS, "textures", "item"))
MODELS = os.path.abspath(os.path.join(ASSETS, "models", "item"))
os.makedirs(OUT, exist_ok=True)
os.makedirs(MODELS, exist_ok=True)

CLEAR = (0, 0, 0, 0)

# ---- палитра (сверена с block v2) ----
BLACK = (3, 5, 10, 255)
INK = (18, 24, 30, 255)
SLATE0 = (30, 38, 46, 255)
SLATE1 = (41, 49, 57, 255)
SLATE2 = (62, 70, 78, 255)
STEEL = (99, 107, 113, 255)
STEEL_HI = (146, 154, 160, 255)
STEEL_DK = (68, 76, 82, 255)
GLASS = (150, 168, 176, 255)
GLASS_HI = (196, 210, 216, 255)
GLASS_DK = (96, 112, 120, 255)
RUBBER = (26, 30, 34, 255)
RUBBER_HI = (66, 72, 78, 255)

PAPER = (206, 202, 188, 255)
PAPER_HI = (232, 228, 214, 255)
PAPER_DK = (170, 164, 148, 255)
PAPER_EDGE = (118, 110, 96, 255)
PAPER_SHADOW = (92, 84, 72, 255)
FOLD = (186, 180, 164, 255)

RED = (126, 29, 44, 255)
RED_HI = (198, 74, 78, 255)
AMBER = (148, 112, 44, 255)
AMBER_HI = (216, 178, 96, 255)
CYAN = (58, 104, 120, 255)
CYAN_HI = (126, 176, 190, 255)
GREEN = (38, 124, 88, 255)
GREEN_HI = (116, 196, 154, 255)
PURPLE = (92, 58, 96, 255)
PURPLE_HI = (154, 112, 160, 255)
PHOTO_DARK = (34, 40, 48, 255)
PHOTO_MID = (58, 66, 74, 255)
PHOTO_PALE = (158, 158, 148, 255)

BLUE_INK = (42, 54, 84, 255)
RED_INK = (104, 34, 38, 255)


def canvas():
    return Image.new("RGBA", (16, 16), CLEAR)


def put(px, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        px[x, y] = c


def sh(c, d):
    return (max(0, min(255, c[0] + d)), max(0, min(255, c[1] + d)),
            max(0, min(255, c[2] + d)), 255)


def mix(a, b, t):
    return (int(a[0] + (b[0] - a[0]) * t), int(a[1] + (b[1] - a[1]) * t),
            int(a[2] + (b[2] - a[2]) * t), 255)


def paper_sheet(px, tone=PAPER):
    """Лист 10x14 с мягкой кромкой и загнутым верхним правым углом."""
    x0, y0, x1, y1 = 3, 1, 12, 14
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if x == x0 or y == y1:
                c = PAPER_EDGE
            elif y == y0 or x == x1:
                c = PAPER_HI
            else:
                c = tone
            put(px, x, y, c)
    # мягкая тень внизу справа
    for x in range(x0 + 1, x1):
        put(px, x, y1 - 1, sh(tone, -10))
    # загнутый угол
    fold = [(10, 1), (11, 1), (12, 1), (11, 2), (12, 2), (12, 3)]
    for x, y in fold:
        put(px, x, y, FOLD)
    put(px, 12, 1, PAPER_EDGE)
    put(px, 11, 2, PAPER_EDGE)
    put(px, 10, 1, PAPER_HI)


def official(accent=RED, accent_hi=RED_HI, header=SLATE1, style="text"):
    img = canvas()
    px = img.load()
    paper_sheet(px)
    # шапка
    for x in range(4, 11):
        put(px, x, 3, header)
        put(px, x, 4, sh(header, -8))
    for x in range(4, 11):
        put(px, x, 5, accent)
    # строки текста
    if style == "morse":
        y = 7
        xs = 4
        pattern = [1, 0, 1, 1, 0, 1, 0, 1, 1, 0]
        for p in pattern:
            if p:
                put(px, xs, y, INK)
                put(px, xs + 1, y, INK)
                xs += 3
            else:
                put(px, xs, y, INK)
                xs += 2
        for x in range(5, 10, 2):
            put(px, x, 9, PAPER_DK)
    else:
        for y in (7, 9):
            for x in range(4, 11):
                if x % 3 != 0:
                    put(px, x, y, PAPER_DK)
        for x in range(4, 8):
            put(px, x, 11, PAPER_DK)
    # подпись
    put(px, 4, 13, INK)
    put(px, 5, 13, INK)
    put(px, 6, 12, INK)
    # печать/отметка
    if style == "check":
        for x, y in [(8, 12), (9, 12), (10, 11), (11, 10), (10, 10), (9, 11)]:
            put(px, x, y, accent)
        put(px, 10, 10, accent_hi)
    else:
        for y in range(11, 14):
            for x in range(8, 12):
                put(px, x, y, sh(accent, -18) if (x in (8, 11) or y in (11, 13)) else accent)
        put(px, 9, 12, accent_hi)
        put(px, 10, 12, accent_hi)
    return img


def note(ink=INK, style="text"):
    img = canvas()
    px = img.load()
    spans = {1: (5, 10), 2: (4, 12), 3: (3, 12), 4: (2, 12), 5: (3, 13),
             6: (2, 12), 7: (3, 13), 8: (2, 12), 9: (3, 12), 10: (2, 13),
             11: (3, 12), 12: (4, 12), 13: (5, 11), 14: (6, 10)}
    shape = {(x, y) for y, (a, b) in spans.items() for x in range(a, b + 1)}
    for x, y in shape:
        inside = all((x + dx, y + dy) in shape for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        put(px, x, y, PAPER if inside else PAPER_EDGE)
    # подорванные края
    for x, y in [(2, 4), (13, 5), (2, 8), (13, 10), (2, 10)]:
        put(px, x, y, CLEAR)
    # складки
    for x, y in [(6, 4), (7, 5), (8, 6), (9, 7)]:
        put(px, x, y, PAPER_HI)
    for x, y in [(10, 8), (11, 8), (5, 10), (6, 11)]:
        put(px, x, y, PAPER_DK)
    # почерк
    if style == "knock":
        for x, y in [(5, 5), (6, 5), (8, 5), (9, 5), (11, 5), (12, 5)]:
            put(px, x, y, ink)
        for x, y in [(5, 7), (6, 7), (8, 7), (9, 7), (11, 7), (12, 7)]:
            put(px, x, y, ink)
        for x, y in [(6, 11), (8, 11), (10, 11)]:
            put(px, x, y, ink)
        put(px, 8, 11, ink)
    else:
        for y, xs in ((5, (5, 6, 8, 9, 10)), (7, (5, 7, 8, 10, 11)),
                      (9, (4, 5, 7, 9, 10)), (11, (6, 8, 9))):
            for x in xs:
                put(px, x, y, ink)
        # пятно
        for x, y in [(10, 10), (11, 10), (10, 11)]:
            put(px, x, y, (110, 50, 46, 255))
    return img


def drawing(accent=None):
    img = canvas()
    px = img.load()
    spans = {1: (6, 10), 2: (5, 11), 3: (4, 12), 4: (3, 12), 5: (4, 13),
             6: (3, 12), 7: (4, 12), 8: (3, 13), 9: (4, 12), 10: (3, 13),
             11: (4, 12), 12: (5, 12), 13: (6, 11), 14: (7, 10)}
    shape = {(x, y) for y, (a, b) in spans.items() for x in range(a, b + 1)}
    for x, y in shape:
        inside = all((x + dx, y + dy) in shape for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        put(px, x, y, PAPER if inside else PAPER_EDGE)
    # сгиб пополам
    for x in range(4, 12):
        put(px, x, 8, PAPER_DK)
    for x in range(4, 12):
        put(px, x, 9, PAPER_HI)
    if accent is None:
        for x, y in [(9, 11), (10, 11), (10, 10)]:
            put(px, x, y, sh(PAPER_DK, -12))
        return img
    a = accent
    ah = mix(accent, (255, 255, 255), 0.28)
    # солнце
    for x, y in [(10, 3), (11, 3), (12, 3), (10, 4), (12, 4), (10, 5), (11, 5), (12, 5)]:
        put(px, x, y, a)
    put(px, 11, 4, ah)
    # человечек
    for x, y in [(6, 4), (7, 4), (6, 5), (7, 5)]:
        put(px, x, y, a)
    for y in (6, 7, 8):
        put(px, 6, y, a)
    put(px, 5, 7, a)
    put(px, 7, 7, a)
    put(px, 5, 9, a)
    put(px, 7, 9, a)
    put(px, 6, 6, ah)
    # земля
    for x in range(4, 12):
        put(px, x, 12, a)
    return img


def tape(label=None):
    img = canvas()
    px = img.load()
    body = (74, 94, 112, 255)
    dark = (52, 68, 82, 255)
    for y in range(3, 13):
        for x in range(1, 15):
            put(px, x, y, BLACK if (x in (1, 14) or y in (3, 12)) else body)
    for x, y in [(1, 3), (14, 3), (1, 12), (14, 12)]:
        put(px, x, y, CLEAR)
    for y in range(4, 10):
        for x in range(3, 13):
            put(px, x, y, label or PAPER)
    for x in range(4, 10):
        put(px, x, 5, (110, 124, 134, 255))
    # окно с катушками
    for y in (7, 8):
        for x in range(5, 11):
            put(px, x, y, BLACK)
    for x, y in [(6, 7), (6, 8), (9, 7), (9, 8)]:
        put(px, x, y, (170, 180, 188, 255))
    put(px, 7, 7, (110, 124, 134, 255))
    put(px, 8, 8, (110, 124, 134, 255))
    # нижняя площадка
    for x in range(4, 12):
        put(px, x, 10, dark)
    for x in range(5, 11):
        put(px, x, 11, dark)
    for x, y in [(5, 11), (10, 11)]:
        put(px, x, y, BLACK)
    # красная точка записи
    put(px, 12, 4, RED_HI)
    # винты
    for x, y in [(2, 4), (13, 4), (2, 11), (13, 11)]:
        put(px, x, y, STEEL_HI)
    return img


def photo():
    img = canvas()
    px = img.load()
    for y in range(3, 13):
        for x in range(2, 14):
            put(px, x, y, PAPER_HI if (x in (2, 13) or y in (3, 12)) else PAPER)
    for y in range(5, 11):
        for x in range(4, 12):
            put(px, x, y, PHOTO_DARK)
    # дверной проём
    for y in range(5, 11):
        put(px, 9, y, PHOTO_MID)
        put(px, 10, y, PHOTO_MID)
    # фигура
    for x, y in [(6, 6), (7, 6), (6, 7), (7, 7)]:
        put(px, x, y, PHOTO_PALE)
    for y in (8, 9):
        put(px, 7, y, PHOTO_PALE)
    put(px, 6, 8, (128, 128, 120, 255))
    put(px, 8, 8, (128, 128, 120, 255))
    # царапина
    for x, y in [(5, 5), (6, 6), (7, 7), (8, 8)]:
        put(px, x, y, (210, 206, 196, 255))
    return img


def pass_card():
    img = canvas()
    px = img.load()
    for y in range(1, 15):
        for x in range(4, 12):
            put(px, x, y, PAPER_EDGE if (x in (4, 11) or y in (1, 14)) else PAPER_HI)
    # прорезь для шнурка
    put(px, 7, 2, BLACK)
    put(px, 8, 2, BLACK)
    # шапка
    for x in range(5, 11):
        put(px, x, 4, SLATE1)
        put(px, x, 5, SLATE0)
    # фото
    for y in range(7, 11):
        for x in range(5, 8):
            put(px, x, y, PHOTO_MID if (x, y) != (6, 8) else PHOTO_PALE)
    # строки
    for y in (7, 9):
        for x in range(9, 11):
            put(px, x, y, PAPER_DK)
    # красная полоса
    for x in range(4, 12):
        put(px, x, 12, RED)
    put(px, 5, 12, RED_HI)
    return img


def plan():
    img = canvas()
    px = img.load()
    base = (34, 70, 84, 255)
    grid = (48, 90, 104, 255)
    for y in range(2, 14):
        for x in range(2, 14):
            put(px, x, y, CYAN_HI if (x in (2, 13) or y in (2, 13)) else base)
    for x in range(4, 13, 3):
        for y in range(4, 13):
            put(px, x, y, grid)
    for y in range(4, 13, 3):
        for x in range(4, 13):
            put(px, x, y, grid)
    # план коридора
    for x in range(4, 11):
        put(px, x, 5, (176, 214, 222, 255))
        put(px, x, 10, (176, 214, 222, 255))
    for y in range(5, 11):
        put(px, 4, y, (176, 214, 222, 255))
        put(px, 10, y, (176, 214, 222, 255))
    # красная отметка
    for x, y in [(7, 7), (8, 7), (7, 8), (8, 8), (9, 9)]:
        put(px, x, y, RED_HI)
    return img


# ---- шприц ----
def _dist(px_, py_, a, b):
    ax, ay = a
    bx, by = b
    dx, dy = bx - ax, by - ay
    l2 = dx * dx + dy * dy
    t = 0.0 if l2 == 0 else ((px_ - ax) * dx + (py_ - ay) * dy) / l2
    t = max(0.0, min(1.0, t))
    cx, cy = ax + t * dx, ay + t * dy
    return math.hypot(px_ - cx, py_ - cy), t, (dx * (py_ - ay) - dy * (px_ - ax))


def draw_syringe(fluid, fluid_hi, label, push=0.0):
    img = canvas()
    px = img.load()
    tip = (14.4, 1.2)
    front = (10.4, 4.0)
    back = (4.2, 10.2)
    tail = (1.4, 13.0)
    dx, dy = front[0] - back[0], front[1] - back[1]
    length = math.hypot(dx, dy)
    ux, uy = dx / length, dy / length
    pf = (back[0] + (front[0] - back[0]) * push, back[1] + (front[1] - back[1]) * push)

    # шток
    for y in range(16):
        for x in range(16):
            d, t, cross = _dist(x + 0.5, y + 0.5, tail, pf)
            if d <= 0.8:
                put(px, x, y, STEEL_HI if cross > 0 and d <= 0.3 else STEEL_DK)

    # головка поршня
    for x in range(0, 3):
        for y in range(12, 15):
            if (x, y) in [(0, 12), (2, 14)]:
                continue
            put(px, x, y, RUBBER_HI if (x, y) in [(1, 12), (0, 13)] else RUBBER)

    # стекло и жидкость
    for y in range(16):
        for x in range(16):
            d, t, cross = _dist(x + 0.5, y + 0.5, back, front)
            if d <= 1.75:
                if d > 1.25:
                    put(px, x, y, GLASS_HI if cross > 0 else GLASS_DK)
                else:
                    s = ((x + 0.5) - back[0]) * ux + ((y + 0.5) - back[1]) * uy
                    if s > push * length:
                        c = fluid_hi if cross > 0 else fluid
                    else:
                        c = GLASS_HI if cross > 0 else GLASS_DK
                    put(px, x, y, c)
    # красное кольцо-доза
    for off in (-1.2, -0.6, 0.0, 0.6, 1.2):
        cx = back[0] + ux * length * 0.45 - uy * off
        cy = back[1] + uy * length * 0.45 + ux * off
        put(px, round(cx), round(cy), label)
    put(px, round(back[0] + ux * length * 0.45 - uy * 1.2),
        round(back[1] + uy * length * 0.45 + ux * 1.2), mix(label, (255, 255, 255), 0.25))

    # фланец
    for off in (-2, -1, 0, 1, 2):
        cx = back[0] - uy * off
        cy = back[1] + ux * off
        put(px, round(cx), round(cy), STEEL_HI if off <= 0 else STEEL_DK)

    # игла
    for y in range(16):
        for x in range(16):
            d, t, cross = _dist(x + 0.5, y + 0.5, front, tip)
            if d <= 0.62:
                put(px, x, y, GLASS_HI if cross > 0 else STEEL)
    # муфта иглы
    for x, y in [(10, 4), (11, 3), (10, 3)]:
        put(px, x, y, STEEL_HI)
    # капля при полном нажатии
    if push > 0.85:
        put(px, 14, 0, fluid_hi)
    return img


def sedative():
    return draw_syringe((92, 74, 120, 255), (140, 118, 168, 255), PURPLE_HI, push=0.25)


# ---- страницы: id = custom_model_data - 1 ----
PAGES = [
    ("morse_sample", lambda: official(STEEL, STEEL_HI, style="morse")),
    ("note_l1_0", note), ("note_l1_1", lambda: note(ink=BLUE_INK)),
    ("note_l1_2", lambda: note(ink=RED_INK)),
    ("drawing_0", lambda: drawing((214, 132, 56))),   # оранжевый
    ("drawing_1", lambda: drawing((70, 120, 200))),   # синий
    ("drawing_2", lambda: drawing((226, 196, 60))),   # жёлтый
    ("drawing_3", lambda: drawing((84, 160, 84))),    # зелёный
    ("drawing_blank", lambda: drawing(None)),
    ("mem_1_photo", photo), ("mem_2_discharge", lambda: official(RED, RED_HI)),
    ("mem_3_contract", lambda: official(STEEL, STEEL_HI)),
    ("mem_4_session12", tape),
    ("mem_5_memo", lambda: official(AMBER, AMBER_HI)),
    ("mem_6_protocol", lambda: official(CYAN, CYAN_HI)),
    ("mem_7_log", tape), ("employee_pass", pass_card),
    ("voss_note", lambda: note(ink=RED_INK)),
    ("drawing_4", lambda: drawing((200, 70, 60))),
    ("note_l2_a", lambda: note(ink=BLUE_INK)), ("note_l2_b", note),
    ("plan_l5", plan), ("note_l3", lambda: note(ink=RED_INK)),
    ("bay_transfer", lambda: official(STEEL, STEEL_HI)),
    ("cab_intake", lambda: official(RED, RED_HI)),
    ("cab_night_memo", lambda: official(AMBER, AMBER_HI)),
    ("cab_evac", lambda: official(RED, RED_HI)),
    ("cab_maint", lambda: official(AMBER, AMBER_HI)),
    ("cab_dnote_clock", note), ("cab_dnote_wake", note),
    ("cab_dnote_lift", note), ("cab_dnote_chair", note),
    ("op_index", lambda: official(RED, RED_HI)),
    ("op_retention", lambda: official(RED, RED_HI)),
    ("cat_extract", lambda: official(CYAN, CYAN_HI)),
    ("tape_edited", lambda: tape((200, 184, 188))), ("sedation_47", sedative),
    ("cab_b2_copy", lambda: official(STEEL, STEEL_HI)),
    ("cab_b3_racks", lambda: official(CYAN, CYAN_HI)),
    ("cab_b4_roster", lambda: official(AMBER, AMBER_HI)),
    ("cab_b5_session", lambda: official(PURPLE, PURPLE_HI)),
    ("b2_check", lambda: official(GREEN, GREEN_HI, style="check")),
    ("note_2d_knock", lambda: note(style="knock")),
]


def main():
    official(RED, RED_HI).save(os.path.join(OUT, "document_official.png"))
    note().save(os.path.join(OUT, "document_note.png"))
    overrides = []
    for i, (pid, fn) in enumerate(PAGES, start=1):
        fn().save(os.path.join(OUT, "doc_%s.png" % pid))
        with open(os.path.join(MODELS, "doc_%s.json" % pid), "w", encoding="utf-8") as f:
            json.dump({"parent": "minecraft:item/generated",
                       "textures": {"layer0": "hypersomnia:item/doc_%s" % pid}},
                      f, indent=2)
        overrides.append({"predicate": {"custom_model_data": i},
                          "model": "hypersomnia:item/doc_%s" % pid})
    with open(os.path.join(MODELS, "document.json"), "w", encoding="utf-8") as f:
        json.dump({"parent": "minecraft:item/generated",
                   "textures": {"layer0": "hypersomnia:item/document_official"},
                   "overrides": overrides}, f, indent=2)
    print("documents: %d" % len(PAGES))


if __name__ == "__main__":
    main()
