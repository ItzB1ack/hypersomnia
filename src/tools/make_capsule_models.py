"""Детали моделей капсул (CapsuleBlock и UprightCapsuleBlock): models/block/capsule_*.json.
Только внешний вид: габариты корпуса, крышки и формы столкновения (CapsuleBlock.GLASS и др.) не меняются.
Что добавляется (идемпотентно: элементы detail_* пересоздаются, остальное правится по имени):
- цоколь: вентиляционные прорези (capsule_plinth, uv режет по высоте цоколя);
- полоса-акцент на внешних гранях бортов (capsule_trim) — детали detail_trim_*; на 0.05 впереди борта, без z-fighting;
- изголовье: экран пульса на консоли (capsule_console, uv 3,0..13,4) и порт капельницы на восточном борту (detail_iv_port);
- подушка (capsule_pillow) вместо общей обивки ложа;
- стоячая капсула: прорези цоколя base, полоса на бортах, порт капельницы на mid, экран-табло на шапке top (detail_status).
Стекло у обеих капсул общее: capsule_lid снаружи и capsule_lid_inner изнутри, оба прозрачные (make_furniture.py).
Текстуры рисует make_furniture.py. Запуск из корня: python run_tool.py make_capsule_models"""
import glob
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "hypersomnia", "models", "block")

TEX = {
    "trim": "hypersomnia:block/capsule_trim",
    "plinth": "hypersomnia:block/capsule_plinth",
    "console": "hypersomnia:block/capsule_console",
    "pillow": "hypersomnia:block/capsule_pillow",
}
BAND_UV = [0, 0, 16, 3]        # светлая полоса trim: строки 0..2
SCREEN_UV = [3, 0, 13, 4]      # экран консоли
PORT_UV = [0, 8, 4, 12]        # порт капельницы


def face(tex, uv=None):
    f = {"texture": "#" + tex}
    if uv is not None:
        f["uv"] = uv
    return f


def element(name, a, b, faces):
    return {"name": name, "from": a, "to": b, "faces": faces}


def find(d, name):
    for e in d["elements"]:
        if e["name"] == name:
            return e
    return None


def use(d, *keys):
    for k in keys:
        d["textures"][k] = TEX[k]


def strips(d, y1, y2, z1, z2):
    """Полоса-акцент на внешних гранях западного (x=1) и восточного (x=15) бортов."""
    use(d, "trim")
    d["elements"].append(element("detail_trim_west", [0.95, y1, z1], [1, y2, z2], {"west": face("trim", BAND_UV)}))
    d["elements"].append(element("detail_trim_east", [15, y1, z1], [15.05, y2, z2], {"east": face("trim", BAND_UV)}))


def horizontal(d, part):
    use(d, "plinth")
    plinth = find(d, "plinth")
    for side in ("north", "south"):
        plinth["faces"][side] = face("plinth", [1, 0, 15, 2])
    for side in ("east", "west"):
        plinth["faces"][side] = face("plinth", [0, 0, 16, 2])
    strips(d, 6.5, 9.5, 0, 16)
    if part == "head":
        use(d, "console", "pillow")
        console = find(d, "console")
        console["faces"]["south"] = face("console", SCREEN_UV)
        pillow = find(d, "pillow")
        for side in pillow["faces"]:
            pillow["faces"][side] = face("pillow")
        d["elements"].append(element("detail_iv_port", [15, 7, 1], [15.8, 9, 3], {
            "east": face("console", PORT_UV), "north": face("console", PORT_UV),
            "south": face("console", PORT_UV), "up": face("console", PORT_UV),
            "down": face("console", PORT_UV)}))


def upright(d, part):
    use(d, "trim")
    if part == "base":
        use(d, "plinth")
        plinth = find(d, "plinth")
        for side in ("north", "south", "east", "west"):
            plinth["faces"][side] = face("plinth", [1, 0, 15, 6])
    strips(d, 6.5, 9.5, 1, 15)
    if part == "mid":                                  # тот же порт капельницы, что у изголовья лежачей
        use(d, "console")
        d["elements"].append(element("detail_iv_port", [15, 7, 1], [15.8, 9, 3], {
            "east": face("console", PORT_UV), "north": face("console", PORT_UV),
            "south": face("console", PORT_UV), "up": face("console", PORT_UV),
            "down": face("console", PORT_UV)}))
    if part == "top":
        use(d, "console")
        d["elements"].append(element("detail_status", [4, 12.5, 0.95], [12, 15.5, 1],
                                     {"north": face("console", SCREEN_UV)}))


def main():
    for path in sorted(glob.glob(os.path.join(DIR, "capsule_*.json"))):
        name = os.path.basename(path)[:-5]
        with open(path, encoding="utf-8") as fh:
            d = json.load(fh)
        d["elements"] = [e for e in d["elements"] if not e["name"].startswith("detail_")]
        parts = name.split("_")
        if parts[1] == "upright":
            upright(d, parts[2])
        else:
            horizontal(d, parts[1])
        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(d, fh, indent=2, ensure_ascii=False)
            fh.write("\n")
        print(name, len(d["elements"]), "elements")
    print("capsule models ok")


if __name__ == "__main__":
    main()
