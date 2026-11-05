"""Иконки предметов-документов 16x16: официальный лист, мятая записка осколка, детский рисунок, кассета.
Запуск: python tools/make_documents.py (нужен Pillow)."""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "main", "resources",
                   "assets", "hypersomnia", "textures", "item")
os.makedirs(OUT, exist_ok=True)

CLEAR = (0, 0, 0, 0)


def official():
    K = (30, 42, 52, 255)      # контур
    P = (201, 207, 210, 255)   # бумага
    S = (168, 177, 183, 255)   # тень и загнутый угол
    T = (110, 124, 134, 255)   # строки текста
    R = (122, 46, 54, 255)     # штамп
    D = (84, 28, 36, 255)      # тёмные точки штампа
    img = Image.new("RGBA", (16, 16), CLEAR)
    px = img.load()
    for y in range(1, 15):
        for x in range(3, 13):
            px[x, y] = K if (x in (3, 12) or y in (1, 14)) else P
    # загнутый угол справа сверху
    for x, y in [(10, 1), (11, 1), (12, 1), (11, 2), (12, 2), (12, 3)]:
        px[x, y] = CLEAR
    for x, y in [(10, 2), (11, 3)]:
        px[x, y] = K
    for x, y in [(9, 2), (9, 3), (10, 3)]:
        px[x, y] = S
    # тень у нижнего края
    for x in range(4, 12):
        px[x, 13] = S
    # строки текста
    for y, x_end in ((5, 10), (7, 10), (9, 8)):
        for x in range(5, x_end + 1):
            px[x, y] = T
    # штамп
    for y in (11, 12):
        for x in range(7, 12):
            px[x, y] = R
    for x, y in [(8, 11), (10, 12)]:
        px[x, y] = D
    return img


def note():
    EDGE = (88, 80, 70, 255)
    N = (190, 181, 156, 255)       # пожелтевшая бумага
    DARK = (150, 141, 122, 255)    # складка
    LIGHT = (218, 211, 190, 255)   # свет на складке
    INK = (36, 32, 32, 255)
    SMUDGE = (110, 50, 46, 255)
    # неровные, «оторванные» края: (x от, x до) для каждой строки
    spans = {1: (5, 10), 2: (4, 12), 3: (3, 12), 4: (3, 11), 5: (4, 12), 6: (3, 12), 7: (3, 11),
             8: (4, 12), 9: (3, 12), 10: (3, 12), 11: (4, 11), 12: (3, 12), 13: (4, 11), 14: (5, 10)}
    shape = {(x, y) for y, (a, b) in spans.items() for x in range(a, b + 1)}
    img = Image.new("RGBA", (16, 16), CLEAR)
    px = img.load()
    for x, y in shape:
        inside = all((x + dx, y + dy) in shape for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        px[x, y] = N if inside else EDGE
    # складки мятой бумаги
    for x, y in [(6, 4), (7, 5), (8, 6), (9, 7), (5, 10), (6, 11), (7, 11)]:
        px[x, y] = DARK
    for x, y in [(7, 4), (8, 5), (9, 6), (10, 7), (8, 11)]:
        px[x, y] = LIGHT
    # неровный почерк и пятно
    for y, xs in ((5, (5, 6, 8, 9, 10)), (7, (5, 7, 8, 10)), (9, (5, 6, 7, 9)), (11, (6, 9, 10))):
        for x in xs:
            px[x, y] = INK
    for x, y in [(10, 9), (11, 9)]:
        px[x, y] = SMUDGE
    return img


def drawing(accent):
    """Свёрнутый пополам детский лист; accent = цвет карандаша по сюжету, None = пустой лист."""
    K = (58, 50, 48, 255)
    P = (222, 216, 200, 255)
    S = (186, 178, 160, 255)
    img = Image.new("RGBA", (16, 16), CLEAR)
    px = img.load()
    for y in range(2, 14):
        for x in range(3, 13):
            px[x, y] = K if (x in (3, 12) or y in (2, 13)) else P
    for x in range(4, 12):          # сгиб
        px[x, 8] = S
    for x in range(4, 12):
        px[x, 9] = (236, 231, 218, 255)
    if accent is None:
        px[10, 11] = S              # едва заметное начало линии
        px[11, 11] = S
    else:
        for y in range(4, 8):       # цветная кромка слева
            px[4, y] = accent
        for x, y in [(6, 5), (7, 4), (8, 5), (9, 6), (7, 6), (8, 11), (9, 11), (10, 10)]:
            px[x, y] = accent       # каракули
    return img

def tape(label=None):
    """Кассета с наклейкой: аудиозаписи (Style.TAPE). Катушки в окне — зубчатые втулки, без кружков.
    label — цвет наклейки (монтажная копия tape_edited — винная)."""
    K = (30, 42, 52, 255)      # контур
    B = (74, 94, 112, 255)     # корпус
    S = (56, 72, 86, 255)      # нижняя площадка
    L = label or (201, 207, 210, 255)   # наклейка
    T = (110, 124, 134, 255)   # строка на наклейке
    W = (3, 5, 10, 255)        # окно
    H = (170, 180, 188, 255)   # зубцы втулок
    img = Image.new("RGBA", (16, 16), CLEAR)
    px = img.load()
    for y in range(3, 13):
        for x in range(1, 15):
            px[x, y] = K if (x in (1, 14) or y in (3, 12)) else B
    for (x, y) in [(1, 3), (14, 3), (1, 12), (14, 12)]:   # скруглённые углы корпуса
        px[x, y] = CLEAR
    for y in range(4, 10):                                # наклейка
        for x in range(3, 13):
            px[x, y] = L
    for x in range(4, 10):                                # строка подписи
        px[x, 5] = T
    for y in (7, 8):                                      # окно
        for x in range(5, 11):
            px[x, y] = W
    for (x, y) in [(6, 7), (6, 8), (9, 7), (9, 8)]:       # втулки
        px[x, y] = H
    for x in range(4, 12):                                # нижняя площадка с отверстиями
        px[x, 11] = S
    for x in range(5, 11):
        px[x, 10] = S
    for (x, y) in [(5, 11), (10, 11)]:
        px[x, y] = W
    return img


def syringe():
    """Шприц глубокой седации (sedation_47): форма адреналинового шприца, другой цвет раствора и колпачка (канон, пункт 9)."""
    grid = ["..............a.",
            ".............aba",
            "..........c.cba.",
            "...........cbc..",
            "............c...",
            "..........dd.c..",
            ".........ddd....",
            "........edd.....",
            ".......ddf......",
            "......eef.......",
            ".....ddf........",
            "....eef.........",
            "...gdf..........",
            "..g.............",
            ".g..............",
            "c..............."]
    pal = {"a": (74, 94, 112, 255),    # колпачок: холодный серый вместо красного
           "b": (150, 158, 165, 255),  # поршень
           "c": (70, 80, 90, 255),     # упор и игла
           "d": (170, 200, 215, 255),  # стекло
           "e": (200, 184, 188, 255),  # раствор: винный
           "f": (138, 112, 122, 255),  # раствор в тени
           "g": (190, 200, 208, 255)}  # игла
    img = Image.new("RGBA", (16, 16), CLEAR)
    px = img.load()
    for y, row in enumerate(grid):
        for x, ch in enumerate(row):
            if ch in pal:
                px[x, y] = pal[ch]
    return img


import json

# Порядок = порядок add(...) в DocumentDefs. Модель предмета: custom_model_data = индекс + 1.
PAGES = [
    ("morse_sample", official),
    ("note_l1_0", note), ("note_l1_1", note), ("note_l1_2", note),
    ("drawing_0", lambda: drawing((214, 132, 56, 255))),    # дом
    ("drawing_1", lambda: drawing((70, 120, 200, 255))),    # капсула
    ("drawing_2", lambda: drawing((226, 196, 60, 255))),    # солнце
    ("drawing_3", lambda: drawing((84, 160, 84, 255))),     # птица
    ("drawing_blank", lambda: drawing(None)),
    ("mem_1_photo", official), ("mem_2_discharge", official), ("mem_3_contract", official),
    ("mem_4_session12", tape), ("mem_5_memo", official), ("mem_6_protocol", official),
    ("mem_7_log", tape), ("employee_pass", official), ("voss_note", note),
    ("drawing_4", lambda: drawing((200, 70, 60, 255))),
    ("note_l2_a", note), ("note_l2_b", note),
    ("plan_l5", official), ("note_l3", note),
    ("bay_transfer", official),
    ("cab_intake", official), ("cab_night_memo", official), ("cab_evac", official), ("cab_maint", official),
    ("cab_dnote_clock", note), ("cab_dnote_wake", note), ("cab_dnote_lift", note), ("cab_dnote_chair", note),
    ("op_index", official), ("op_retention", official),
    ("cat_extract", official),
    ("tape_edited", lambda: tape((200, 184, 188, 255))), ("sedation_47", syringe),
    ("cab_b2_copy", official), ("cab_b3_racks", official), ("cab_b4_roster", official), ("cab_b5_session", official),
    ("b2_check", official), ("note_2d_knock", note),
]
MODELS = os.path.join(OUT, "..", "..", "models", "item")
os.makedirs(MODELS, exist_ok=True)

official().save(os.path.join(OUT, "document_official.png"))
overrides = []
for i, (pid, fn) in enumerate(PAGES, start=1):
    fn().save(os.path.join(OUT, "doc_%s.png" % pid))
    with open(os.path.join(MODELS, "doc_%s.json" % pid), "w") as f:
        json.dump({"parent": "minecraft:item/generated",
                   "textures": {"layer0": "hypersomnia:item/doc_%s" % pid}}, f, indent=2)
    overrides.append({"predicate": {"custom_model_data": i}, "model": "hypersomnia:item/doc_%s" % pid})
with open(os.path.join(MODELS, "document.json"), "w") as f:
    json.dump({"parent": "minecraft:item/generated",
               "textures": {"layer0": "hypersomnia:item/document_official"},
               "overrides": overrides}, f, indent=2)
print("ok")