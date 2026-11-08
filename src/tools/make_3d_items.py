"""HYPERSOMNIA 3D item models: adrenaline syringe + diary book.

The syringe is a cuboid model whose plunger slides back as the item is used.
The diary is a closed 3D book; extra frames lift the front cover up to 180
degrees, which the item switches through while it is being opened.

Files are generated into:
  assets/hypersomnia/textures/item/syringe_*.png
  assets/hypersomnia/textures/item/diary_*.png
  assets/hypersomnia/models/item/adrenaline_syringe*.json
  assets/hypersomnia/models/item/diary*.json

Run: python src/tools/make_3d_items.py
"""
import json
import math
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.abspath(os.path.join(HERE, "..", "main", "resources",
                                      "assets", "hypersomnia"))
TEX = os.path.join(ASSETS, "textures", "item")
MODELS = os.path.join(ASSETS, "models", "item")
os.makedirs(TEX, exist_ok=True)
os.makedirs(MODELS, exist_ok=True)

# ---- palette (same family as the block / document v2 textures) ----
GLASS = (150, 168, 176)
FLUID = (92, 150, 166)
STEEL = (99, 107, 113)
STEEL_HI = (146, 154, 160)
RUBBER = (26, 30, 34)
RED = (126, 29, 44)

COVER = (34, 40, 48)
PAPER = (196, 190, 172)
STRAP = (122, 46, 54)
METAL = (108, 116, 124)
METAL_HI = (162, 170, 176)


def shade(c, d):
    return tuple(max(0, min(255, c[i] + d)) for i in range(3))


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def bevel(base, amount=14, size=16):
    """Soft 1px bevel + gentle streaks, tiled over every cuboid face."""
    hi = shade(base, amount)
    lo = shade(base, -amount)
    img = Image.new("RGBA", (size, size), base + (255,))
    px = img.load()
    for i in range(size):
        px[i, 0] = hi + (255,)
        px[0, i] = hi + (255,)
        px[i, size - 1] = lo + (255,)
        px[size - 1, i] = lo + (255,)
    for y in range(3, size - 3, 5):
        for x in range(3, size - 3, 4):
            px[x, y] = shade(base, (x * 3 + y) % 7 - 3) + (255,)
    return img


def save_tex(name, img):
    img.save(os.path.join(TEX, name + ".png"))


def face(tex):
    # Явный UV на весь тайл 16x16. Без него Minecraft берёт UV из координат
    # элемента; у открытой обложки x уходит в минус, UV выходит за тайл и
    # читает соседние спрайты атласа (дырки и чужие цвета).
    return {"texture": "#" + tex, "uv": [0, 0, 16, 16]}


DIRS = ("north", "east", "south", "west", "up", "down")


def box(frm, to, tex, faces=None, rot=None):
    element = {
        "from": [round(v, 4) for v in frm],
        "to": [round(v, 4) for v in to],
        "faces": faces or {d: face(tex) for d in DIRS},
    }
    if rot:
        element["rotation"] = rot
    return element


# ---------------------------------------------------------------------------
# Syringe
# ---------------------------------------------------------------------------
SYRINGE_TEX = {
    "particle": "hypersomnia:item/syringe_fluid",
    "fluid": "hypersomnia:item/syringe_fluid",
    "fluid_dark": "hypersomnia:item/syringe_fluid_dark",
    "glass": "hypersomnia:item/syringe_glass",
    "steel": "hypersomnia:item/syringe_steel",
    "steel_light": "hypersomnia:item/syringe_steel_light",
    "rubber": "hypersomnia:item/syringe_rubber",
    "red": "hypersomnia:item/syringe_red",
}

SYRINGE_DISPLAY = {
    "gui": {"rotation": [0, 0, 45], "translation": [0, 0, 0],
            "scale": [0.62, 0.62, 0.62]},
    "ground": {"rotation": [0, 0, 45], "translation": [0, 2, 0],
               "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 0, 45], "translation": [0, 0, 0],
              "scale": [0.55, 0.55, 0.55]},
    "thirdperson_righthand": {"rotation": [0, 0, 45], "translation": [0, 4, 2],
                              "scale": [0.5, 0.5, 0.5]},
    "thirdperson_lefthand": {"rotation": [0, 0, 45], "translation": [0, 4, 2],
                             "scale": [0.5, 0.5, 0.5]},
    # В руке шприц стоит вертикально иглой вниз, как в анимации укола (кулак
    # держит заднюю часть корпуса, поршень торчит вверх над кистью).
    "firstperson_righthand": {"rotation": [0, -20, -100], "translation": [0, 0, 2],
                              "scale": [0.55, 0.55, 0.55]},
    "firstperson_lefthand": {"rotation": [0, -20, -100], "translation": [0, 0, 2],
                             "scale": [0.55, 0.55, 0.55]},
    "head": {"rotation": [0, 0, 45], "translation": [0, 0, 0],
             "scale": [0.6, 0.6, 0.6]},
}


SYRINGE_STEPS = 8


def syringe_elements(push=0.0):
    # Модель вытянута до 20 единиц (x от -2 до 18, центр остаётся в 8): задняя
    # часть с поршнем торчит из кулака и хорошо видна, пока он идёт в корпус.
    shift = push * 6.0
    barrel_faces = {
        "north": face("fluid"),
        "south": face("fluid"),
        "up": face("glass"),
        "down": face("fluid_dark"),
        "east": face("glass"),
        "west": face("glass"),
    }
    return [
        # glass barrel with the fluid showing through the long sides
        box((6.0, 6.9, 6.9), (14.5, 9.1, 9.1), "fluid", barrel_faces),
        # red dose ring
        box((10.0, 6.7, 6.7), (11.0, 9.3, 9.3), "red"),
        # finger flange
        box((5.5, 5.9, 5.9), (6.2, 10.1, 10.1), "steel"),
        # plunger rod + thumb pad (slide into the barrel as push -> 1)
        box((-0.8 + shift, 7.7, 7.7), (6.0 + shift, 8.3, 8.3), "steel_light"),
        box((-2.0 + shift, 6.4, 6.4), (-0.8 + shift, 9.6, 9.6), "rubber"),
        # needle hub + needle
        box((14.5, 7.45, 7.45), (15.2, 8.55, 8.55), "steel"),
        box((15.2, 7.925, 7.925), (18.0, 8.075, 8.075), "steel_light"),
    ]


def write_model(name, data):
    with open(os.path.join(MODELS, name + ".json"), "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)


def syringe():
    save_tex("syringe_glass", bevel(GLASS, 12))
    save_tex("syringe_fluid", bevel(FLUID, 12))
    save_tex("syringe_fluid_dark", bevel(shade(FLUID, -30), 10))
    save_tex("syringe_steel", bevel(STEEL, 16))
    save_tex("syringe_steel_light", bevel(STEEL_HI, 18))
    save_tex("syringe_rubber", bevel(RUBBER, 10))
    save_tex("syringe_red", bevel(RED, 18))

    # hypersomnia:using для шприца — положение поршня 0..1 (кривую и субтики
    # считает SyringeViewModel), поэтому кадры идут равномерно.
    frames = [("adrenaline_syringe", 0.0)] + [
        ("adrenaline_syringe_use_%d" % k, (k + 1) / SYRINGE_STEPS)
        for k in range(SYRINGE_STEPS)
    ]
    overrides = [
        {"predicate": {"hypersomnia:using": round((k + 0.5) / SYRINGE_STEPS, 4)},
         "model": "hypersomnia:item/adrenaline_syringe_use_%d" % k}
        for k in range(SYRINGE_STEPS)
    ]
    for name, push in frames:
        data = {
            "parent": "minecraft:block/block",
            "textures": SYRINGE_TEX,
            "elements": syringe_elements(push),
            "display": SYRINGE_DISPLAY,
        }
        if name == "adrenaline_syringe":
            data["overrides"] = overrides
        write_model(name, data)


# ---------------------------------------------------------------------------
# Diary
# ---------------------------------------------------------------------------
DIARY_TEX = {
    "particle": "hypersomnia:item/diary_cover",
    "cover": "hypersomnia:item/diary_cover",
    "pages": "hypersomnia:item/diary_pages",
    "strap": "hypersomnia:item/diary_strap",
    "metal": "hypersomnia:item/diary_metal",
}

DIARY_DISPLAY = {
    "gui": {"rotation": [30, 45, 0], "translation": [0, 0, 0],
            "scale": [0.72, 0.72, 0.72]},
    "ground": {"rotation": [90, 45, 0], "translation": [0, 2, 0],
               "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [30, 45, 0], "translation": [0, 0, 0],
              "scale": [0.6, 0.6, 0.6]},
    "thirdperson_righthand": {"rotation": [30, -35, 0], "translation": [0, 3, 1],
                              "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [30, 35, 0], "translation": [0, 3, 1],
                             "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [55, -20, 0], "translation": [0, 2, 2],
                              "scale": [0.62, 0.62, 0.62]},
    "firstperson_lefthand": {"rotation": [55, 20, 0], "translation": [0, 2, 2],
                             "scale": [0.62, 0.62, 0.62]},
    "head": {"rotation": [90, 0, 0], "translation": [0, 0, 0],
             "scale": [0.6, 0.6, 0.6]},
}


# Шарнир обложки: линия корешка x=2, середина стопки (между нижней и верхней
# половинами бумаги). Поворот на 180 градусов кладёт обложку вместе с верхней
# половиной страниц ровно рядом с задней обложкой, на тот же уровень.
HINGE_X, HINGE_Y = 2.0, 1.4


def _rotate_box(frm, to, degrees):
    """Поворот коробки вокруг шарнира книги на 90/180 градусов.

    BlockElement разрешает только -45..45 (шаг 22.5), поэтому 90/180 запекаются
    в координатах (коробка остаётся осевой), а остаток доворачивается обычным
    rotation вокруг того же шарнира.
    """
    if not degrees:
        return frm, to
    a = math.radians(degrees)
    c, s = math.cos(a), math.sin(a)
    xs, ys = [], []
    for x in (frm[0], to[0]):
        for y in (frm[1], to[1]):
            dx, dy = x - HINGE_X, y - HINGE_Y
            xs.append(HINGE_X + c * dx - s * dy)
            ys.append(HINGE_Y + s * dx + c * dy)
    return (min(xs), min(ys), frm[2]), (max(xs), max(ys), to[2])


def diary_elements(angle=0.0):
    """Кадр обложки: angle 0..180. 90/180 запекаются, остальное — rotation.

    Вместе с обложкой поворачивается верхняя половина страниц, поэтому открытая
    книга лежит как настоящая: слева обложка со своими страницами, справа задняя
    обложка со своими, ничего не висит в воздухе.
    """
    if angle <= 45.0:
        bake, rot = 0.0, angle
    elif angle <= 135.0:
        bake, rot = 90.0, angle - 90.0
    else:
        bake, rot = 180.0, angle - 180.0
    rotation = None
    if rot:
        rotation = {"origin": [HINGE_X, HINGE_Y, 8.0], "axis": "z",
                    "angle": rot, "rescale": False}

    def moving_box(frm, to, tex):
        baked = _rotate_box(frm, to, bake)
        return box(baked[0], baked[1], tex, rot=rotation)

    return [
        # back cover
        box((2.0, 0.0, 2.0), (14.0, 1.0, 14.0), "cover"),
        # lower half of the paper block (stays)
        box((2.5, 1.0, 2.5), (13.5, 1.4, 13.5), "pages"),
        # upper half of the paper block (turns with the front cover)
        moving_box((2.5, 1.4, 2.5), (13.5, 1.8, 13.5), "pages"),
        # front cover (turns around the spine)
        moving_box((2.0, 1.8, 2.0), (14.0, 2.8, 14.0), "cover"),
        # red strap over the cover
        moving_box((9.5, 2.81, 2.0), (11.0, 3.1, 14.0), "strap"),
        # metal clasp near the opening edge
        moving_box((9.5, 2.81, 12.4), (11.0, 3.5, 14.0), "metal"),
        # bookmark sticking out of the pages
        box((11.3, 1.2, 13.4), (11.9, 1.28, 15.2), "strap"),
    ]


def diary():
    save_tex("diary_cover", bevel(COVER, 16))
    save_tex("diary_pages", bevel(PAPER, 14))
    save_tex("diary_strap", bevel(STRAP, 18))
    save_tex("diary_metal", bevel(METAL_HI, 18))

    frames = [
        ("diary", 0.0),
        ("diary_open_0", 22.5),
        ("diary_open_1", 45.0),
        ("diary_open_2", 67.5),
        ("diary_open_3", 90.0),
        ("diary_open_4", 112.5),
        ("diary_open_5", 135.0),
        ("diary_open_6", 157.5),
        ("diary_open_7", 180.0),
    ]
    overrides = [
        {"predicate": {"hypersomnia:using": t},
         "model": "hypersomnia:item/" + name}
        for (name, _), t in zip(frames[1:],
                                (0.10, 0.21, 0.32, 0.43,
                                 0.55, 0.66, 0.77, 0.88))
    ]
    for name, angle in frames:
        data = {
            "parent": "minecraft:block/block",
            "textures": DIARY_TEX,
            "elements": diary_elements(angle),
            "display": DIARY_DISPLAY,
        }
        if name == "diary":
            data["overrides"] = overrides
        write_model(name, data)


def main():
    syringe()
    diary()
    print("3d items: syringe %d frames, diary 9 frames" % (SYRINGE_STEPS + 1))


if __name__ == "__main__":
    main()
