#!/usr/bin/env python3
"""
Генератор текстур сцены «This isn't a dream»: силуэт «потерянного себя» и аналоговые глаза.

Силуэт: пропорции самого игрока (голова-куб, широкий торс с руками, узкие ноги), но вытянутый
вверх и без деталей. Края плывут (три варианта с разным искажением, которые в игре
перетекают друг в друга), ноги растворяются.
Глаза: человеческий глаз «как в инфракрасной съёмке» из трёх слоёв (склера, радужка, веки).

Запуск:  python3 make_silhouette.py <папка_вывода>
Нужны: pip install pillow numpy scipy

Единицы: 1 юнит = 1/50 роста фигуры; 8 пикселей на юнит (низкое разрешение — часть стиля).
"""
import sys, os
import numpy as np
from PIL import Image, ImageDraw
from scipy.ndimage import gaussian_filter, map_coordinates

PX = 8                      # пикселей на юнит (в Java: TEX_PX)
STRETCH = 1.4               # во сколько раз игрок вытянут по вертикали
FIG_H = 50.0                # рост фигуры в юнитах
CANVAS_W, CANVAS_H = 26.0, 52.0   # холст тела (юниты); ноль X по центру, верх холста = 1 юнит над макушкой
BODY_RGB = np.array([5, 7, 11], dtype=np.float32)
EYE_RGB = np.array([214, 218, 222], dtype=np.float32)


def smooth_noise(shape, sigma, rng):
    n = gaussian_filter(rng.standard_normal(shape), sigma)
    return n / (np.abs(n).max() + 1e-9)


def player_mask():
    """Силуэт игрока (вид спереди), растянутый по вертикали. Рисуется с запасом разрешения."""
    SS = 4
    W, H = int(CANVAS_W * PX), int(CANVAS_H * PX)
    img = Image.new("L", (W * SS, H * SS), 0)
    d = ImageDraw.Draw(img)
    a = FIG_H / (32 * STRETCH)       # юнитов на «пиксель игрока» по горизонтали
    b = a * STRETCH                  # и по вертикали
    cx = CANVAS_W / 2
    top = 1.0

    def box(x0, y0, x1, y1, r):
        px = lambda v: v * PX * SS
        d.rounded_rectangle([px(cx + x0), px(top + y0), px(cx + x1), px(top + y1)], radius=px(r), fill=255)

    r = 1.4  # скругление: углов нет, всё «оплыло»
    box(-4 * a, 0, 4 * a, 8 * b, r)                 # голова
    box(-8 * a, 7.4 * b, 8 * a, 20 * b, r)          # торс вместе с руками (руки слиты с телом)
    box(-4 * a, 19 * b, 4 * a, 32 * b, r * 0.8)     # ноги слиты в один столб
    return np.asarray(img.resize((W, H), Image.LANCZOS), dtype=np.float32) / 255.0


def body_variant(seed, path):
    rng = np.random.default_rng(seed)
    m = player_mask()
    H, W = m.shape
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    # Искажение краёв: крупное и мелкое, к низу сильнее (оплывает)
    down = np.clip(yy / H, 0, 1)
    amp = (1.5 + 2.4 * down ** 1.6) * PX            # пикселей
    dx = smooth_noise((H, W), 7 * PX / 8 * 2.2, rng) * amp + smooth_noise((H, W), 2.2, rng) * 0.35 * PX
    dy = smooth_noise((H, W), 7 * PX / 8 * 2.6, rng) * amp * 0.9
    # В нижней части растягиваем по горизонтали: «стекает»
    dx += smooth_noise((H, W), 9, rng) * 1.8 * PX * (down ** 2.5)
    w = map_coordinates(m, [yy + dy, xx + dx], order=1, mode="constant")
    w = gaussian_filter(w, 0.75 * PX)               # мягкие, неопределённые границы
    # Растворение ног: к низу прозрачнее
    fade_start = 0.66
    fade = np.clip(1 - (down - fade_start) / (1 - fade_start), 0, 1) ** 0.9
    w = w * (0.22 + 0.78 * fade)
    # Лёгкая «зернистость» самой массы (пересжатое видео)
    grain = 1 + gaussian_filter(rng.standard_normal((H, W)), 0.9) * 0.55
    w = np.clip(w * grain, 0, 1)
    rgb = BODY_RGB[None, None, :] + gaussian_filter(rng.standard_normal((H, W)), 1.2)[..., None] * 3.0
    out = np.dstack([rgb, w * 255.0]).clip(0, 255).astype(np.uint8)
    Image.fromarray(out, "RGBA").save(path)
    return (W, H)


# ---------------------------------------------------------------- глаза
# Человеческий глаз «как в инфракрасной съёмке»: серая неровно освещённая склера, почти чёрная
# огромная радужка с тремя мелкими бликами, тёмное верхнее веко, влажная светлая кромка нижнего.
# Три слоя, чтобы взгляд мог двигаться: склера (низ) -> радужка со зрачком (двигается) -> веки (верх).
EYE_PX = 24                 # пикселей на юнит у глаз (в Java: EYE_TEX_PX)
EYE_W, EYE_H = 5.0, 3.4     # холст склеры/век, юниты; центр глаза в середине
IRIS_W = 2.2                # холст радужки, юниты
A = 1.75                    # полуширина раскрытого глаза


def lid_curves(x):
    top = -0.82 * np.clip(1 - ((x - 0.18) / 1.9) ** 2, 0, 1) ** 0.75
    bot = 0.58 * np.clip(1 - ((x + 0.10) / 1.85) ** 2, 0, 1) ** 0.9
    return top, bot


def eye_grid():
    W, H = int(EYE_W * EYE_PX), int(EYE_H * EYE_PX)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    return W, H, (xx - W / 2) / EYE_PX, (yy - H / 2) / EYE_PX


def eye_sclera(path, seed=21):
    rng = np.random.default_rng(seed)
    W, H, x, y = eye_grid()
    top, bot = lid_curves(x)
    d = np.minimum(y - top, bot - y)                      # >0 внутри раскрытия
    mask = np.clip(d / 0.05, 0, 1) * (np.abs(x) < A + 0.15)
    v = 0.44 + 0.18 * (-x / A)                            # слева светлее, справа темнее (как на референсе)
    v += 0.10 * np.clip(y / 0.6, -1, 1)
    v *= 0.45 + 0.55 * np.clip((y - top) / 0.45, 0, 1)    # тень под верхним веком
    v *= 1 - 0.55 * (np.abs(x) / A) ** 4                  # края глаза темнее
    veins = gaussian_filter(rng.standard_normal((H, W)), 2.2)
    v += 0.09 * veins / (np.abs(veins).max() + 1e-9)
    v *= 1 + 0.10 * gaussian_filter(rng.standard_normal((H, W)), 0.6) * 2.0
    v = gaussian_filter(np.clip(v, 0, 1), 0.7)
    mask = gaussian_filter(mask.astype(np.float32), 0.8)
    g = (np.clip(v, 0, 1) * 255.0)
    out = np.dstack([g, g, g, mask * 255.0]).clip(0, 255).astype(np.uint8)
    Image.fromarray(out, "RGBA").save(path)
    return (W, H)


def eye_iris(path, seed=22):
    """Огромный тёмный зрачок/радужка с тремя бликами. Центр холста = центр зрачка."""
    rng = np.random.default_rng(seed)
    W = H = int(IRIS_W * EYE_PX)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    x, y = (xx - W / 2) / EYE_PX, (yy - H / 2) / EYE_PX
    r = np.sqrt(x * x + y * y)
    R = 0.74
    disc = np.clip((R - r) / 0.045, 0, 1)
    v = 0.03 + 0.06 * np.clip((r - 0.50) / 0.24, 0, 1) ** 2    # к краю радужка чуть светлее
    v += gaussian_filter(rng.standard_normal((H, W)), 0.9) * 0.012
    # три мелких блика в верхней части (как на референсе)
    for cx, cy, rr, inten in ((-0.22, -0.34, 0.058, 1.0), (0.02, -0.33, 0.050, 0.9), (0.42, -0.36, 0.044, 0.65)):
        glint = np.clip(1 - np.sqrt((x - cx) ** 2 + (y - cy) ** 2) / rr, 0, 1) ** 0.7
        v = np.maximum(v, glint * inten)
    v = gaussian_filter(np.clip(v, 0, 1), 0.45)
    g = np.clip(v, 0, 1) * 255.0
    out = np.dstack([g, g, g, gaussian_filter(disc, 0.6) * 255.0]).clip(0, 255).astype(np.uint8)
    Image.fromarray(out, "RGBA").save(path)
    return (W, H)


def eye_lids(path, seed=23):
    """Верхний слой: тёмная кожа вокруг раскрытия (обрезает радужку), тень от верхнего века, светлая кромка внизу."""
    from scipy.ndimage import distance_transform_edt
    rng = np.random.default_rng(seed)
    W, H, x, y = eye_grid()
    top, bot = lid_curves(x)
    inside = ((y >= top) & (y <= bot) & (np.abs(x) < A + 0.15))
    dout = distance_transform_edt(~inside) / EYE_PX                  # расстояние от раскрытия наружу, юниты
    halo = np.clip(1 - dout / 0.5, 0, 1) ** 0.8
    halo[inside] = 0
    # тень верхнего века внутри глаза
    shadow = 0.62 * np.clip(1 - (y - top) / 0.34, 0, 1) ** 1.5 * inside
    a_black = np.clip(halo + shadow * (1 - halo), 0, 1)
    rgb = np.broadcast_to(BODY_RGB, (H, W, 3)).copy()
    alpha = a_black
    # светлая влажная кромка нижнего века + искры
    rim = (np.abs(y - bot) < 0.055) & (x > -1.5) & (x < 1.4)
    rim_a = gaussian_filter(rim.astype(np.float32), 0.8) * 0.85
    sparks = (rng.random((H, W)) < 0.10) & (np.abs(y - bot - 0.03) < 0.07) & (x > -1.1) & (x < 0.9)
    sparks = gaussian_filter(sparks.astype(np.float32), 0.6) * 2.4
    rim_v = np.clip(0.50 + 0.18 * (-x / A) + sparks, 0, 1)
    top_a = np.clip(rim_a + np.clip(sparks, 0, 1) * 0.6, 0, 1)
    out_a = top_a + alpha * (1 - top_a)
    rgb = (rim_v[..., None] * 255.0 * top_a[..., None] + rgb * alpha[..., None] * (1 - top_a[..., None])) / np.maximum(out_a[..., None], 1e-4)
    out = np.dstack([rgb, out_a * 255.0]).clip(0, 255).astype(np.uint8)
    Image.fromarray(out, "RGBA").save(path)
    return (W, H)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out, exist_ok=True)
    names = []
    for i, seed in enumerate((3, 17, 41)):
        n = f"silhouette_self_{'abc'[i]}"
        print(n, body_variant(seed, os.path.join(out, n + ".png"))); names.append(n)
    for n, f in (("silhouette_eye_sclera", eye_sclera), ("silhouette_eye_iris", eye_iris), ("silhouette_eye_lids", eye_lids)):
        pth = os.path.join(out, n + ".png")
        print(n, f(pth)); names.append(n)
        # зеркальная копия для второго глаза (отдельный файл *_r)
        Image.open(pth).transpose(Image.FLIP_LEFT_RIGHT).save(os.path.join(out, n + "_r.png")); names.append(n + "_r")
    for n in names:   # мягкая (линейная) фильтрация: размытие — часть стиля
        with open(os.path.join(out, n + ".png.mcmeta"), "w") as fh:
            fh.write('{\n  "texture": {\n    "blur": true\n  }\n}\n')
