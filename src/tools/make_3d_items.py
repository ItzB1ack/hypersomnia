"""HYPERSOMNIA 3D item models: adrenaline syringe + diary book.

The syringe is a cuboid model whose plunger slides back as the item is used.
The diary is a closed 3D book; two extra frames lift the front cover by
22.5 / 45 degrees, which the item switches through while it is being opened.

Files are generated into:
  assets/hypersomnia/textures/item/syringe_*.png
  assets/hypersomnia/textures/item/diary_*.png
  assets/hypersomnia/models/item/adrenaline_syringe*.json
  assets/hypersomnia/models/item/diary*.json

Run: python src/tools/make_3d_items.py
"""
import json
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
    return {"texture": "#" + tex}


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
    "firstperson_righthand": {"rotation": [0, 0, 35], "translation": [0, 3, 2],
                              "scale": [0.55, 0.55, 0.55]},
    "firstperson_lefthand": {"rotation": [0, 0, 35], "translation": [0, 3, 2],
                             "scale": [0.55, 0.55, 0.55]},
    "head": {"rotation": [0, 0, 45], "translation": [0, 0, 0],
             "scale": [0.6, 0.6, 0.6]},
}


def syringe_elements(push=0.0):
    shift = push * 3.0
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
        box((4.0, 6.5, 6.5), (12.5, 9.5, 9.5), "fluid", barrel_faces),
        # red dose ring
        box((8.0, 6.2, 6.2), (9.0, 9.8, 9.8), "red"),
        # finger flange
        box((3.5, 5.0, 5.0), (4.2, 11.0, 11.0), "steel"),
        # plunger rod + thumb pad (slide into the barrel as push -> 1)
        box((1.0 + shift, 7.6, 7.6), (4.0 + shift, 8.4, 8.4), "steel_light"),
        box((0.0 + shift, 6.0, 6.0), (1.2 + shift, 10.0, 10.0), "rubber"),
        # needle hub + needle
        box((12.5, 7.3, 7.3), (13.2, 8.7, 8.7), "steel"),
        box((13.2, 7.85, 7.85), (16.0, 8.15, 8.15), "steel_light"),
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

    frames = [
        ("adrenaline_syringe", 0.00),
        ("adrenaline_syringe_use_0", 0.10),
        ("adrenaline_syringe_use_1", 0.55),
        ("adrenaline_syringe_use_2", 1.00),
    ]
    overrides = [
        {"predicate": {"hypersomnia:using": 0.001},
         "model": "hypersomnia:item/adrenaline_syringe_use_0"},
        {"predicate": {"hypersomnia:using": 0.45},
         "model": "hypersomnia:item/adrenaline_syringe_use_1"},
        {"predicate": {"hypersomnia:using": 0.80},
         "model": "hypersomnia:item/adrenaline_syringe_use_2"},
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


def diary_elements(angle=0.0):
    rot = None
    if angle:
        rot = {"origin": [2.0, 2.3, 8.0], "axis": "z",
               "angle": angle, "rescale": False}
    return [
        # back cover
        box((2.0, 0.0, 2.0), (14.0, 1.0, 14.0), "cover"),
        # paper block
        box((2.5, 1.0, 2.5), (13.5, 1.8, 13.5), "pages"),
        # front cover (lifts around the spine)
        box((2.0, 1.8, 2.0), (14.0, 2.8, 14.0), "cover", rot=rot),
        # red strap over the cover
        box((9.5, 2.81, 2.0), (11.0, 3.1, 14.0), "strap", rot=rot),
        # metal clasp near the opening edge
        box((9.5, 2.81, 12.4), (11.0, 3.5, 14.0), "metal", rot=rot),
        # bookmark sticking out of the pages
        box((11.3, 1.7, 13.4), (11.9, 1.78, 15.2), "strap"),
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
    ]
    overrides = [
        {"predicate": {"hypersomnia:using": 0.18},
         "model": "hypersomnia:item/diary_open_0"},
        {"predicate": {"hypersomnia:using": 0.55},
         "model": "hypersomnia:item/diary_open_1"},
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
    print("3d items: syringe 4 frames, diary 3 frames")


if __name__ == "__main__":
    main()
