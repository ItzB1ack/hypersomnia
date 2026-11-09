"""Модели капсул (CapsuleBlock и UprightCapsuleBlock): models/block/capsule_*.json — генерируются целиком, руками не править.
Источник один: лежачая капсула (cell: foot/mid/head x open/closed), по длине z (изголовье z=0, изножье z=16),
по ширине x1..15, по высоте y0..16. Стоячая капсула — ТА ЖЕ геометрия, повёрнутая на конец:
(x, y, z) -> (x, 16 - z, 16 - y); грани: up->north, down->south, north->up, south->down. Так изножье внизу колонны,
изголовье с консолью сверху, крышка-стекло становится дверцей (north), ложе — задней стенкой.
Формы столкновения и blockstates не меняются (CapsuleBlock/UprightCapsuleBlock); меняется только вид.
Объём: ступенчатый цоколь с кантом, пояс на бортах с полосой-акцентом, рельсы по верху бортов, консоль уступами
с экраном, подушка, порт капельницы, купол стекла двумя ступенями (capsule_lid снаружи, capsule_lid_inner изнутри).
Текстуры рисует make_furniture.py (корпус цельный, без швов). Запуск из корня: python run_tool.py make_capsule_models"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "hypersomnia", "models", "block")

TEX = {
    "particle": "hypersomnia:block/capsule_frame",
    "frame": "hypersomnia:block/capsule_frame",
    "rim": "hypersomnia:block/capsule_rim",
    "lid": "hypersomnia:block/capsule_lid",
    "lid_inner": "hypersomnia:block/capsule_lid_inner",
    "pad": "hypersomnia:block/capsule_pad",
    "pillow": "hypersomnia:block/capsule_pillow",
    "plinth": "hypersomnia:block/capsule_plinth",
    "trim": "hypersomnia:block/capsule_trim",
    "console": "hypersomnia:block/capsule_console",
}
BAND_UV = [0, 0, 16, 3]        # светлая полоса trim: строки 0..2
SCREEN_UV = [3, 0, 13, 4]      # экран консоли
PORT_UV = [0, 8, 4, 12]        # порт капельницы
ALL = ("north", "south", "east", "west", "up", "down")
OPP = {"up": "north", "down": "south", "north": "up", "south": "down", "east": "east", "west": "west"}


def box(name, a, b, faces):
    """faces: {грань: текстура | (текстура, uv)}; грани не из словаря не рисуются."""
    out = {}
    for side, v in faces.items():
        tex, uv = (v, None) if isinstance(v, str) else v
        f = {"texture": "#" + tex}
        if uv is not None:
            f["uv"] = uv
        out[side] = f
    return {"name": name, "from": a, "to": b, "faces": out}


def solid(tex, skip=()):
    return {s: tex for s in ALL if s not in skip}


def cell(part, closed):
    """Элементы одной клетки лежащей капсулы. part: foot/mid/head."""
    foot, head = part == "foot", part == "head"
    e = []
    # цоколь: узкий низ с прорезями и выступающий кант
    e.append(box("plinth", [1, 0, 0], [15, 1.5, 16], {
        "north": ("plinth", [1, 0, 15, 2]), "south": ("plinth", [1, 0, 15, 2]),
        "east": ("plinth", [0, 0, 16, 2]), "west": ("plinth", [0, 0, 16, 2]), "down": "frame"}))
    e.append(box("lip", [0.5, 1.5, 0], [15.5, 3, 16], solid("rim")))
    # ложе
    pad = solid("frame")
    pad["up"] = "pad"
    e.append(box("pad", [3, 3, 0], [13, 6, 16], pad))
    # борта и пояс на них
    e.append(box("wall_west", [1, 3, 0], [3, 12, 16], solid("frame")))
    e.append(box("wall_east", [13, 3, 0], [15, 12, 16], solid("frame")))
    e.append(box("belt_west", [0.5, 4.5, 0], [1, 10.5, 16], solid("frame", ("east",))))
    e.append(box("belt_east", [15, 4.5, 0], [15.5, 10.5, 16], solid("frame", ("west",))))
    e.append(box("trim_west", [0.45, 6.5, 0], [0.5, 9.5, 16], {"west": ("trim", BAND_UV)}))
    e.append(box("trim_east", [15.5, 6.5, 0], [15.55, 9.5, 16], {"east": ("trim", BAND_UV)}))
    # рельсы по верху бортов
    e.append(box("rail_west", [0.5, 12, 0], [3.5, 12.75, 16], solid("rim")))
    e.append(box("rail_east", [12.5, 12, 0], [15.5, 12.75, 16], solid("rim")))
    if head:
        e.append(box("headboard", [3, 6, 0], [13, 12, 2], solid("frame")))
        e.append(box("console_base", [3, 12, 0], [13, 14, 3], solid("frame")))
        top = solid("frame")
        top["south"] = ("console", SCREEN_UV)
        top["up"] = "rim"
        e.append(box("console_top", [3.5, 14, 0], [12.5, 16, 2], top))
        e.append(box("pillow", [3, 6, 2], [13, 7.5, 5], solid("pillow")))
        port = {s: ("console", PORT_UV) for s in ("east", "north", "south", "up", "down")}
        e.append(box("iv_port", [15.5, 7, 1], [16, 9, 3], port))
    if foot:
        e.append(box("footboard", [3, 6, 13], [13, 12, 16], solid("frame")))
        e.append(box("footboard_cap", [3, 12, 13], [13, 12.75, 16], solid("rim")))
    if closed:
        z0 = 3.02 if head else 0
        end = "north" if head else "south"       # торец купола у изголовья / изножья
        for name, x1, x2, outer, inner in (("lid_wall_west", 1, 3, "west", "east"),
                                           ("lid_wall_east", 13, 15, "east", "west")):
            faces = {outer: "lid", inner: "lid_inner"}   # верх закрывает roof_low (общая плоскость y14 без z-fighting)
            if head or foot:
                faces[end] = "lid"
            e.append(box(name, [x1, 12.75, z0], [x2, 14, 16], faces))
        roof1 = {"up": "lid", "down": "lid_inner", "east": "lid", "west": "lid"}
        roof2 = {"up": "lid", "east": "lid", "west": "lid"}
        if head or foot:
            roof1[end] = "lid"
            roof2[end] = "lid"
        e.append(box("lid_roof_low", [1, 14, z0], [15, 15, 16], roof1))
        e.append(box("lid_roof_high", [3, 15, z0], [13, 16, 16], roof2))
        if foot:
            e.append(box("lid_end_south", [3, 12.75, 15], [13, 14, 16], {"north": "lid_inner", "south": "lid"}))
    return e


def rotate_up(elements):
    """Лежачая -> стоячая: (x, y, z) -> (x, 16 - z, 16 - y), грани по OPP."""
    out = []
    for el in elements:
        a, b = el["from"], el["to"]
        out.append({
            "name": el["name"],
            "from": [a[0], 16 - b[2], 16 - b[1]],
            "to": [b[0], 16 - a[2], 16 - a[1]],
            "faces": {OPP[s]: f for s, f in el["faces"].items()},
        })
    return out


def write(name, elements):
    used = {f["texture"].lstrip("#") for el in elements for f in el["faces"].values()}
    textures = {"particle": TEX["particle"]}
    textures.update({k: TEX[k] for k in TEX if k in used})
    d = {"parent": "minecraft:block/block", "render_type": "minecraft:translucent",
         "textures": textures, "elements": elements}
    with open(os.path.join(DIR, name + ".json"), "w", encoding="utf-8", newline="\n") as fh:
        json.dump(d, fh, indent=2, ensure_ascii=False)
        fh.write("\n")
    print(name, len(elements), "elements")


def main():
    upright_part = {"foot": "base", "mid": "mid", "head": "top"}
    for part in ("foot", "mid", "head"):
        for closed in (False, True):
            state = "closed" if closed else "open"
            els = cell(part, closed)
            write("capsule_%s_%s" % (part, state), els)
            write("capsule_upright_%s_%s" % (upright_part[part], state), rotate_up(els))
    print("capsule models ok")


if __name__ == "__main__":
    main()
