"""Фото корпоратива (mem_1_photo): textures/misc/photo_staff.png, 352x264 RGBA.

Старый чёрно-белый отпечаток: Э. Марш (слева, лабораторный халат) и директор Хэллоран
(справа, тёмный костюм, очки) жмут руки перед капсулой 47; слева табличка PROTOTYPE.

Исходный кадр — photo_staff_raw.png рядом со скриптом (512x384, оттенки серого; откуда он и
как получен — в make_photo_raw.py). Этот скрипт только печатает: надписи «47» и «PROTOTYPE»
(свой растровый шрифт, до размытия, чтобы легли в зерно кадра; табличку наполовину закрывает
халат Марша), затем копия с копии: низкое разрешение, мягкость, ореол по контурам, низкий
контраст, пятнистый шум и зерно, пыль, светлая полоса-царапина, залом, белое поле с неровным
краем (альфа только 0/255). Результат детерминирован (фиксированный seed).
Размер должен совпадать с DiaryTuning.PHOTOS (texW, texH).

Запуск из корня: python run_tool.py make_photo
Или: python src/tools/make_photo.py [результат.png] [--raw исходник.png]
Нужны numpy и Pillow. Несколько секунд.
"""
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hypersomnia", "textures", "misc", "photo_staff.png")
RAW = os.path.join(HERE, "photo_staff_raw.png")

W, H = 352, 264          # текстура (DiaryTuning.PHOTOS: texW, texH)
BORDER = 12              # белое поле
IW, IH = W - 2 * BORDER, H - 2 * BORDER
SS = 2                   # обработка в двойном размере, в конце уменьшение
SEED = 47

# Разметка в координатах исходника 512x384: четыре угла (лв, пв, пн, лн).
NUMBER_QUAD = ((251, 36), (290, 36), (290, 62), (251, 62))         # «47» на капсуле под тёмным ободом
PLACARD_QUAD = ((12, 229), (80, 229), (80, 262), (12, 262))        # поле таблички (затирается)
PLACARD_TEXT = ((14.5, 238.5), (109.0, 238.5), (109.0, 252.5), (14.5, 252.5))  # PROTOTYPE, клетка 1.75
PLACARD_CLIP = 79.0      # правее — халат Марша: видно PROTOT, остальное за ним
CROP_Y = (5, 379)        # кадр по высоте под пропорцию IW:IH
LOSS = 1.7               # во сколько раз копия беднее кадра по разрешению


# ---------------------------------------------------------------- шум и размытие

def gauss_kernel(sigma):
    r = max(1, int(math.ceil(sigma * 3)))
    x = np.arange(-r, r + 1)
    k = np.exp(-0.5 * (x / sigma) ** 2)
    return k / k.sum()


def blur(a, sigma):
    if sigma <= 0:
        return a
    k = gauss_kernel(sigma)
    r = len(k) // 2
    p = np.pad(a, ((r, r), (0, 0)), mode="edge")
    a = sum(k[i] * p[i:i + a.shape[0]] for i in range(len(k)))
    p = np.pad(a, ((0, 0), (r, r)), mode="edge")
    return sum(k[i] * p[:, i:i + a.shape[1]] for i in range(len(k)))


def resize(a, w, h, method=Image.BICUBIC):
    im = Image.fromarray(a.astype(np.float32), mode="F").resize((w, h), method)
    return np.asarray(im, dtype=np.float64)


def low_noise(rng, h, w, cell):
    """Плавный шум 0..1: решётка случайных чисел, растянутая бикубически."""
    gh, gw = max(2, h // cell + 2), max(2, w // cell + 2)
    return resize(rng.random((gh, gw)), w, h)


# ---------------------------------------------------------------- надписи (свой растровый шрифт 5x7)

GLYPHS = {
    "P": ["1111.", "1...1", "1...1", "1111.", "1....", "1....", "1...."],
    "R": ["1111.", "1...1", "1...1", "1111.", "1.1..", "1..1.", "1...1"],
    "O": [".111.", "1...1", "1...1", "1...1", "1...1", "1...1", ".111."],
    "T": ["11111", "..1..", "..1..", "..1..", "..1..", "..1..", "..1.."],
    "Y": ["1...1", "1...1", ".1.1.", "..1..", "..1..", "..1..", "..1.."],
    "E": ["11111", "1....", "1....", "1111.", "1....", "1....", "11111"],
    "4": ["...1.", "..11.", ".1.1.", "1..1.", "11111", "...1.", "...1."],
    "7": ["11111", "....1", "...1.", "..1..", ".1...", ".1...", ".1..."],
}


def glyph_raster(text, cell=8, bold=0):
    """Надпись в растре: 1 — краска. Столбец между буквами, поле в полклетки; bold — утолщение вправо (px)."""
    cols, rows = len(text) * 6 - 1, 7
    m = np.zeros((rows * cell + cell, cols * cell + cell))
    for i, ch in enumerate(text):
        for r, line in enumerate(GLYPHS[ch]):
            for c, v in enumerate(line):
                if v == "1":
                    y0, x0 = cell // 2 + r * cell, cell // 2 + (i * 6 + c) * cell
                    m[y0:y0 + cell, x0:x0 + cell + bold] = 1.0
    return m


def homography(src, dst):
    """Матрица 3x3: src (4 точки) -> dst (4 точки)."""
    a, b = [], []
    for (x, y), (u, v) in zip(src, dst):
        a.append([x, y, 1, 0, 0, 0, -u * x, -u * y]); b.append(u)
        a.append([0, 0, 0, x, y, 1, -v * x, -v * y]); b.append(v)
    h = np.linalg.solve(np.array(a, dtype=np.float64), np.array(b, dtype=np.float64))
    return np.append(h, 1.0).reshape(3, 3)


def warp_to_quad(src, quad, shape):
    """Растр src (h, w) в четырёхугольник quad кадра shape; билинейно, вне — 0."""
    sh, sw = src.shape
    hm = homography(quad, ((0, 0), (sw, 0), (sw, sh), (0, sh)))
    yy, xx = np.mgrid[0:shape[0], 0:shape[1]].astype(np.float64) + 0.5
    d = hm[2, 0] * xx + hm[2, 1] * yy + hm[2, 2]
    u = (hm[0, 0] * xx + hm[0, 1] * yy + hm[0, 2]) / d - 0.5
    v = (hm[1, 0] * xx + hm[1, 1] * yy + hm[1, 2]) / d - 0.5
    ok = (u >= 0) & (u <= sw - 1) & (v >= 0) & (v <= sh - 1)
    u0 = np.clip(np.floor(u).astype(int), 0, sw - 2)
    v0 = np.clip(np.floor(v).astype(int), 0, sh - 2)
    fu, fv = np.clip(u - u0, 0, 1), np.clip(v - v0, 0, 1)
    out = (src[v0, u0] * (1 - fu) * (1 - fv) + src[v0, u0 + 1] * fu * (1 - fv)
           + src[v0 + 1, u0] * (1 - fu) * fv + src[v0 + 1, u0 + 1] * fu * fv)
    return np.where(ok, out, 0.0)


def quad_mask(quad, shape):
    return warp_to_quad(np.ones((8, 8)), quad, shape)


def paint(y, text, quad, ink, alpha, soft, clip_x=None, bold=0):
    """Краска по поверхности: тень и свет кадра сохраняются (умножение), края мягкие.
    clip_x: правее этой x надпись закрыта передним планом."""
    m = blur(warp_to_quad(glyph_raster(text, bold=bold), quad, y.shape), soft) * alpha
    if clip_x is not None:
        xx = np.arange(y.shape[1])[None, :]
        m = m * np.clip(clip_x - xx, 0, 1)
    return y * (1 - m) + y * ink * m


def wipe(y, quad, rng, clip_x=None):
    """Поле таблички: гладкая заливка по краю четырёхугольника, чтобы снять случайные буквы кадра."""
    m = blur(quad_mask(quad, y.shape), 1.0)
    if clip_x is not None:
        m = m * np.clip(clip_x - np.arange(y.shape[1])[None, :], 0, 1)
    edge = (m > 0.05) & (m < 0.6)
    base = np.median(y[edge]) if edge.any() else float(y[m > 0.5].mean())
    smooth = blur(np.where(m > 0.5, base, y), 6.0)
    fill = 0.65 * base + 0.35 * smooth + (low_noise(rng, *y.shape, 6) - 0.5) * 0.02
    return y * (1 - m) + fill * m


# ---------------------------------------------------------------- печать

def stamp_disc(img, cx, cy, r, val, alpha):
    h, w = img.shape
    x0, x1 = max(0, int(cx - r - 2)), min(w, int(cx + r + 3))
    y0, y1 = max(0, int(cy - r - 2)), min(h, int(cy + r + 3))
    if x0 >= x1 or y0 >= y1:
        return
    yy, xx = np.mgrid[y0:y1, x0:x1]
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    a = np.clip(r + 0.5 - d, 0, 1) * alpha
    img[y0:y1, x0:x1] = img[y0:y1, x0:x1] * (1 - a) + val * a


def stroke(img, pts, width, val, alpha):
    for (x, y) in pts:
        stamp_disc(img, x, y, width * 0.5, val, alpha)


def generation_loss(y, rng):
    """Копия с копии: мало деталей, мягкие края со светлым ореолом, лёгкое двоение."""
    h, w = y.shape
    y = blur(y, 0.9 * SS)
    small = resize(y, int(w / (LOSS * SS)), int(h / (LOSS * SS)), Image.BOX)
    y = resize(small, w, h, Image.BICUBIC)
    y = y + 0.55 * (y - blur(y, 2.6 * SS))                 # ореол по контурам
    y = 0.9 * y + 0.1 * np.roll(y, int(2 * SS), axis=1)   # двоение
    return y


def tone(y):
    """Отпечаток: светлое не доходит до белого, тёмное — до чёрного, середина плоская."""
    y = np.clip(y, 0, 1) ** 1.08
    return 0.11 + y * (0.84 - 0.11)


def vignette(y):
    h, w = y.shape
    yy, xx = np.mgrid[0:h, 0:w]
    r2 = ((xx - w / 2) / (w / 2)) ** 2 * 0.55 + ((yy - h / 2) / (h / 2)) ** 2 * 0.45
    return y * (1.0 - 0.18 * r2)


def noise(y, rng):
    h, w = y.shape
    blot = (low_noise(rng, h, w, int(5 * SS)) - 0.5) * 0.045
    fine = blur(rng.normal(0.0, 1.0, (h, w)), 0.6 * SS)
    fine /= fine.std() + 1e-9
    amp = 0.02 * (0.4 + 2.4 * y * (1.0 - y))
    return y + blot + fine * amp


def damage(y, rng):
    """Пыль, волосок, светлая горизонтальная полоса по эмульсии, стёртые края кадра."""
    h, w = y.shape
    for _ in range(14):
        stamp_disc(y, rng.uniform(0, w), rng.uniform(0, h), rng.uniform(0.3, 0.9), 0.92, rng.uniform(0.4, 0.8))
    for _ in range(4):
        stamp_disc(y, rng.uniform(0, w), rng.uniform(0, h), rng.uniform(0.3, 0.7), 0.1, rng.uniform(0.35, 0.6))
    x, yy0 = rng.uniform(0, w), rng.uniform(0, h)
    ang = rng.uniform(0, math.pi * 2)
    pts = []
    for _ in range(26):
        ang += rng.normal(0, 0.22)
        x += math.cos(ang) * 0.7
        yy0 += math.sin(ang) * 0.7
        pts.append((x, yy0))
    stroke(y, pts, 0.6, 0.9, 0.45)
    # светлая полоса: короткая, горизонтальная, с мягкими концами
    sx, sy, ln = w * 0.13, h * 0.43, w * 0.08
    for i in range(int(ln)):
        t = i / ln
        stamp_disc(y, sx + i, sy + 0.15 * math.sin(t * 3), 0.55, 0.97, 0.85 * math.sin(math.pi * t) ** 0.4)
    yy, xx = np.mgrid[0:h, 0:w]
    edge = np.minimum(np.minimum(xx, w - 1 - xx), np.minimum(yy, h - 1 - yy))
    wear = np.clip(1.0 - edge / 5.0, 0, 1) * (low_noise(rng, h, w, 6) > 0.62)
    return y + wear * 0.1


def develop(raw, rng):
    y = wipe(raw, PLACARD_QUAD, rng, PLACARD_CLIP)
    y = paint(y, "PROTOTYPE", PLACARD_TEXT, 0.12, 0.95, 0.5, PLACARD_CLIP, bold=3)
    y = paint(y, "47", NUMBER_QUAD, 0.3, 0.85, 0.6)
    y = y[CROP_Y[0]:CROP_Y[1]]
    y = resize(y, IW * SS, IH * SS, Image.LANCZOS)
    y = generation_loss(y, rng)
    y = vignette(tone(y))
    y = noise(y, rng)
    y = resize(y, IW, IH, Image.BOX)
    y = damage(y, rng)
    return np.clip(y, 0.0, 1.0)


def deckle_mask(rng):
    """Неровный край отпечатка: мелкие фестоны + шум; 1 — бумага, 0 — прозрачно."""
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float64)

    def edge(t, n):
        sc = 0.5 + 0.5 * np.cos(2 * math.pi * t / 8.6)
        return 0.6 + 1.5 * sc ** 0.8 + 0.7 * n
    nt = low_noise(rng, 1, W, 5)[0]
    nb = low_noise(rng, 1, W, 5)[0]
    nl = low_noise(rng, H, 1, 5)[:, 0]
    nr = low_noise(rng, H, 1, 5)[:, 0]
    top = edge(xx, nt[None, :].repeat(H, 0))
    bot = edge(xx + 3.0, nb[None, :].repeat(H, 0))
    lef = edge(yy, nl[:, None].repeat(W, 1))
    rig = edge(yy + 4.0, nr[:, None].repeat(W, 1))
    inside = (yy >= top) & (H - 1 - yy >= bot) & (xx >= lef) & (W - 1 - xx >= rig)
    for cx, cy in ((3.5, 3.5), (W - 4.5, 3.5), (3.5, H - 4.5), (W - 4.5, H - 4.5)):
        corner = (np.abs(xx - cx) <= 4) & (np.abs(yy - cy) <= 4) & ((xx - cx) * (cx - W / 2) > 0) & ((yy - cy) * (cy - H / 2) > 0)
        inside &= ~(corner & ((xx - cx) ** 2 + (yy - cy) ** 2 > 3.2 ** 2))
    return inside


def crease(rgb, rng):
    """Залом через левый нижний угол: светлая трещина эмульсии и тень с одной стороны."""
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float64)
    a, b = np.array([0.0, H * 0.74]), np.array([W * 0.2, H * 1.0])
    d = b - a
    nrm = np.array([-d[1], d[0]]) / np.linalg.norm(d)
    s = (xx - a[0]) * nrm[0] + (yy - a[1]) * nrm[1]
    s = s + 0.6 * (low_noise(rng, H, W, 9) - 0.5)
    crack = np.clip(1.0 - np.abs(s) / 0.6, 0, 1) * 0.5
    shade = np.clip(1.0 - np.abs(s - 1.6) / 1.6, 0, 1) * 0.08
    out = rgb * (1 - shade)[..., None]
    return out + (0.93 - out) * crack[..., None]


def compose(y, rng):
    """Кадр в белом поле, холодно-нейтральный тон, неровный край, альфа только 0/255."""
    paper = np.array([0.905, 0.897, 0.872])
    rgb = np.empty((H, W, 3))
    rgb[:] = paper
    rgb *= (0.985 + 0.03 * low_noise(rng, H, W, 12))[..., None]
    yy, xx = np.mgrid[0:H, 0:W]
    edge = np.minimum(np.minimum(xx, W - 1 - xx), np.minimum(yy, H - 1 - yy))
    rgb *= (1.0 - 0.05 * np.clip(1.0 - edge / 8.0, 0, 1))[..., None]
    tint = np.stack([y - 0.012 * (1 - y), y - 0.004 * (1 - y), y + 0.014 * (1 - y)], -1)
    rgb[BORDER:BORDER + IH, BORDER:BORDER + IW] = tint
    rgb = crease(rgb, rng)
    mask = deckle_mask(rng)
    out = np.empty((H, W, 4), dtype=np.uint8)
    out[..., :3] = np.clip(rgb * 255.0 + 0.5, 0, 255).astype(np.uint8)
    out[..., 3] = np.where(mask, 255, 0).astype(np.uint8)
    out[~mask, :3] = np.clip(paper * 255.0, 0, 255).astype(np.uint8)
    return Image.fromarray(out, mode="RGBA")


def load_raw(path):
    a = np.asarray(Image.open(path).convert("RGB"), dtype=np.float64) / 255.0
    return a @ np.array([0.299, 0.587, 0.114])


def make(raw_path=RAW):
    rng = np.random.default_rng(SEED)
    return compose(develop(load_raw(raw_path), rng), rng)


def main(argv):
    args = argv[1:]
    raw = RAW
    if "--raw" in args:
        i = args.index("--raw")
        raw = args[i + 1]
        args = args[:i] + args[i + 2:]
    args = [a for a in args if not a.startswith("--")]
    dst = args[0] if args else OUT
    im = make(raw)
    os.makedirs(os.path.dirname(os.path.abspath(dst)), exist_ok=True)
    im.save(dst)
    print(dst)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
