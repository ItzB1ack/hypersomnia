"""
Тонкий шрифт субтитров осколка (client/FragmentFont): атлас глифов и их метрики.
Пишет:
  src/main/resources/assets/hypersomnia/textures/font/fragment.png (+ .mcmeta: мягкая фильтрация)
  src/main/resources/assets/hypersomnia/voice/font.json   метрики: size, ascent, descent, atlas [w, h],
                                                           glyphs {символ: [x, y, w, h, ox, oy, advance]}
ox/oy - сдвиг левого верхнего угла картинки глифа от точки пера на базовой линии (oy < 0 - выше линии).
Шрифт: Montserrat (вариативный, OFL 1.1, src/tools/fonts/OFL_Montserrat.txt), начертание WEIGHT (200 = ExtraLight).
Ванильный шрифт Minecraft пиксельный и не умеет тонко: свой атлас рисуется мягко (blur в .mcmeta) в любом масштабе.
Глифы растрируются в SIZE px: при интерфейсе x4 на 1080p субтитр выходит почти 1:1.
Запуск из корня: python run_tool.py make_fragment_font. Нужен Pillow (pip install pillow).
"""
import json
import os
import sys

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    sys.exit("Нужен Pillow: pip install pillow")

FONT = os.path.join("src", "tools", "fonts", "Montserrat[wght].ttf")
RES = os.path.join("src", "main", "resources", "assets", "hypersomnia")
ATLAS = os.path.join(RES, "textures", "font", "fragment.png")
METRICS = os.path.join(RES, "voice", "font.json")
WEIGHT = 200
SIZE = 64
PAD = 4
ATLAS_W = 1024

CHARS = ("".join(chr(c) for c in range(0x20, 0x7F))
         + "".join(chr(c) for c in range(0x0410, 0x0450)) + "Ёё"
         + "«»“”„‘’—–…·•№")


def main():
    font = ImageFont.truetype(FONT, SIZE)
    try:
        axes = font.get_variation_axes()
        font.set_variation_by_axes([WEIGHT if b"eight" in (a.get("name") or b"") or "eight" in str(a.get("name"))
                                    else a.get("default") for a in axes])
    except OSError:
        print("  (шрифт не вариативный: беру как есть)")
    ascent, descent = font.getmetrics()
    boxes = []
    for ch in CHARS:
        adv = font.getlength(ch)
        x0, y0, x1, y1 = font.getbbox(ch, anchor="ls")
        boxes.append((ch, adv, x0, y0, x1, y1))
    # Полки: глифы по высоте, слева направо
    order = sorted(range(len(boxes)), key=lambda i: -(boxes[i][5] - boxes[i][3]))
    x = y = shelf = 0
    place = {}
    for i in order:
        ch, adv, x0, y0, x1, y1 = boxes[i]
        w, h = x1 - x0, y1 - y0
        if w <= 0 or h <= 0:
            continue
        cw, chh = w + 2 * PAD, h + 2 * PAD
        if x + cw > ATLAS_W:
            x = 0
            y += shelf
            shelf = 0
        place[i] = (x, y)
        x += cw
        shelf = max(shelf, chh)
    height = (y + shelf + 3) // 4 * 4
    alpha = Image.new("L", (ATLAS_W, height), 0)
    d = ImageDraw.Draw(alpha)
    glyphs = {}
    for i, (ch, adv, x0, y0, x1, y1) in enumerate(boxes):
        if i in place:
            px, py = place[i]
            d.text((px + PAD - x0, py + PAD - y0), ch, font=font, anchor="ls", fill=255)
            glyphs[ch] = [px, py, x1 - x0 + 2 * PAD, y1 - y0 + 2 * PAD, x0 - PAD, y0 - PAD, round(adv, 2)]
        else:
            glyphs[ch] = [0, 0, 0, 0, 0, 0, round(adv, 2)]
    img = Image.merge("RGBA", (Image.new("L", alpha.size, 255),) * 3 + (alpha,))
    os.makedirs(os.path.dirname(ATLAS), exist_ok=True)
    img.save(ATLAS, optimize=True)
    with open(ATLAS + ".mcmeta", "w", encoding="utf-8", newline="\n") as f:
        f.write('{\n  "texture": {\n    "blur": true,\n    "clamp": true\n  }\n}\n')
    os.makedirs(os.path.dirname(METRICS), exist_ok=True)
    with open(METRICS, "w", encoding="utf-8", newline="\n") as f:
        f.write('{\n  "size": %d,\n  "ascent": %d,\n  "descent": %d,\n  "atlas": [%d, %d],\n  "glyphs": {\n'
                % (SIZE, ascent, descent, ATLAS_W, height))
        items = list(glyphs.items())
        for k, (ch, g) in enumerate(items):
            f.write("    %s: %s%s\n" % (json.dumps(ch, ensure_ascii=False), json.dumps(g),
                                        "," if k < len(items) - 1 else ""))
        f.write("  }\n}\n")
    print("Глифов %d, атлас %dx%d -> %s" % (len(glyphs), ATLAS_W, height, ATLAS))


if __name__ == "__main__":
    main()
