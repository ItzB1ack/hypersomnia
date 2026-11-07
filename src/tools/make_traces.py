"""Текстуры детских следов 32x32 в textures/misc/trace_<asset>_<n>.png и иконка Зайки (след TOY) 16x16 в textures/item/bunny.png.
Запуск: python run_tool.py make_traces (из корня проекта)."""
import math
import os
import random
from PIL import Image, ImageDraw

OUT = "src/main/resources/assets/hypersomnia/textures/misc"
N = 32
SS = 6
os.makedirs(OUT, exist_ok=True)

DUST = (206, 216, 222)
CHALK = (221, 230, 236)


def canvas(col):
    img = Image.new("RGBA", (N * SS, N * SS), col + (0,))
    return img, ImageDraw.Draw(img)


def save(img, name):
    img.resize((N, N), Image.LANCZOS).save(os.path.join(OUT, name))
    with open(os.path.join(OUT, name + ".mcmeta"), "w") as f:
        f.write('{"texture":{"blur":true}}')


def ell(d, cx, cy, rx, ry, ang, fill):
    pts = []
    for i in range(28):
        t = i / 28 * 2 * math.pi
        x, y = rx * math.cos(t), ry * math.sin(t)
        a = math.radians(ang)
        pts.append(((cx + x * math.cos(a) - y * math.sin(a)) * SS,
                    (cy + x * math.sin(a) + y * math.cos(a)) * SS))
    d.polygon(pts, fill=fill)


def foot(d, cx, cy, mirror, fill):
    m = -1 if mirror else 1
    ell(d, cx, cy + 1.5, 2.3, 3.6, 0, fill)             # стопа
    ell(d, cx, cy + 5.2, 1.6, 1.4, 0, fill)             # пятка
    for i, (dx, dy, r) in enumerate([(-1.9, -3.4, 0.9), (-0.8, -4.3, 0.8), (0.4, -4.5, 0.75),
                                     (1.4, -4.1, 0.7), (2.2, -3.2, 0.6)]):
        ell(d, cx + m * dx, cy + dy, r, r, 0, fill)


def steps(v):
    img, d = canvas(DUST)
    fill = DUST + (170,)
    if v == 0:
        foot(d, 10, 17, False, fill)
        foot(d, 21, 9, True, fill)
    else:
        foot(d, 15, 16, True, DUST + (120,))
    save(img, "trace_steps_%d.png" % v)


def line(d, a, b, w, fill, rnd, jit=0.5):
    d.line([(a[0] + rnd.uniform(-jit, jit)) * SS, (a[1] + rnd.uniform(-jit, jit)) * SS,
            (b[0] + rnd.uniform(-jit, jit)) * SS, (b[1] + rnd.uniform(-jit, jit)) * SS],
           fill=fill, width=int(w * SS))


def group5(d, x0, y0, fill, rnd, strokes=4, strike=True):
    for i in range(strokes):
        x = x0 + i * 3.2
        line(d, (x, y0), (x + rnd.uniform(-0.4, 0.4), y0 + 8), 1.1, fill, rnd)
    if strike:
        line(d, (x0 - 1.5, y0 + 6.5), (x0 + 3.2 * (strokes - 1) + 1.5, y0 + 1.5), 1.1, fill, rnd)


def tally(v):
    rnd = random.Random(47 + v)
    img, d = canvas(CHALK)
    fill = CHALK + (190,)
    if v == 0:
        group5(d, 9, 11, fill, rnd)
    else:
        group5(d, 6, 6, fill, rnd)
        group5(d, 6, 18, fill, rnd)
        for i in range(2):
            line(d, (22 + i * 3.2, 18), (22 + i * 3.2, 26), 1.1, fill, rnd)
    save(img, "trace_tally_%d.png" % v)


def hopscotch():
    rnd = random.Random(1947)
    img, d = canvas(CHALK)
    fill = CHALK + (170,)
    c = 5.0
    rows = [(1, 0), (2, 1), (1, 3), (2, 4), (1, 6)]   # (клеток в ряду, y)
    for cells, ry in rows:
        y0 = 1.5 + ry * 0 + [0, 5, 10, 15, 20][[r[1] for r in rows].index(ry) if False else rows.index((cells, ry))]
        xs = [11] if cells == 1 else [8, 13]
        for x0 in xs:
            for a, b in [((x0, y0), (x0 + c, y0)), ((x0 + c, y0), (x0 + c, y0 + c)),
                         ((x0 + c, y0 + c), (x0, y0 + c)), ((x0, y0 + c), (x0, y0))]:
                line(d, a, b, 0.8, fill, rnd, 0.25)
    save(img, "trace_hopscotch_0.png")


def hand(v):
    img, d = canvas(DUST)
    fill = DUST + (160,)
    s = 1.0 if v == 0 else 0.85
    cx, cy = 16, 20
    ell(d, cx, cy, 6.2 * s, 6.0 * s, 0, fill)                        # ладонь
    for dx, ln, ang in [(-4.5, 9.5, -12), (-1.6, 11.5, -4), (1.4, 11.0, 4), (4.2, 8.5, 12)]:
        ell(d, cx + dx * s, cy - (5 + ln / 2) * s, 1.5 * s, ln / 2 * s, ang, fill)
    ell(d, cx - 6.8 * s, cy - 1.0 * s, 1.7 * s, 4.2 * s, -55, fill)  # большой палец
    save(img, "trace_hand_%d.png" % v)


def scribble(v):
    rnd = random.Random(8 + v)
    col = [(200, 70, 60), (70, 110, 200), (222, 192, 60)][v]
    img, d = canvas(col)
    fill = col + (210,)
    x, y = 16.0, 16.0
    pts = []
    for i in range(46):
        a = i * 0.9 + rnd.uniform(-0.6, 0.6)
        r = 2 + i * 0.26 + rnd.uniform(-1, 1)
        pts.append(((16 + r * math.cos(a)) * SS, (16 + r * 0.8 * math.sin(a)) * SS))
    d.line(pts, fill=fill, width=int(1.4 * SS), joint="curve")
    save(img, "trace_scribble_%d.png" % v)


ITEM_OUT = "src/main/resources/assets/hypersomnia/textures/item"
# Зайка: потёртая красная игрушка с рисунка 4 и из записи «Норы» («He's on the floor.»);
# одно ухо надорвано, светлая заплатка, глаз-пуговица, швы. 16x16, мягкое затенение.
BUNNY_PAL = {
    "O": (40, 18, 24), "R": (132, 50, 54), "H": (168, 80, 78), "S": (96, 34, 40),
    "P": (196, 182, 184), "T": (74, 30, 36), "I": (170, 120, 122),
    "E": (12, 8, 10), "W": (222, 212, 202),
}
BUNNY = [
    "................",
    "....OO...OO.....",
    "...OHRO.OHRO....",
    "...OHIO.OHIO....",
    "...OHIO.OHRO....",
    "...OHROOOHRO....",
    "..OHRRRRRRRSO...",
    "..OHREWRRTRSO...",
    "..OHRRRIRRRSO...",
    "..OHRRRRRRRSO...",
    ".OHHRRRRRRRRSO..",
    ".OHRRPPPPPRRSO..",
    ".OHRRPPPPPRRSO..",
    ".OHRRRRRRRRRSO..",
    "..OORRRRRRRSOO..",
    "...OOOOOOOOO....",
]


def bunny():
    os.makedirs(ITEM_OUT, exist_ok=True)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(BUNNY):
        for x, c in enumerate(row):
            if c != ".":
                img.putpixel((x, y), BUNNY_PAL[c] + (255,))
    # крестик-шов на месте второго глаза
    for x, y in [(9, 6), (9, 8), (8, 7), (10, 7)]:
        img.putpixel((x, y), BUNNY_PAL["T"] + (255,))
    # швы на заплатке
    for x, y in [(5, 12), (9, 11), (6, 13), (8, 13)]:
        img.putpixel((x, y), BUNNY_PAL["T"] + (255,))
    # надорванное ухо и торчащая нитка
    img.putpixel((4, 1), (0, 0, 0, 0))
    img.putpixel((13, 10), BUNNY_PAL["S"] + (255,))
    img.putpixel((13, 11), BUNNY_PAL["T"] + (255,))
    img.save(os.path.join(ITEM_OUT, "bunny.png"))


if __name__ == "__main__":
    for v in range(2):
        steps(v)
        tally(v)
        hand(v)
    for v in range(3):
        scribble(v)
    hopscotch()
    bunny()
    print("ok")