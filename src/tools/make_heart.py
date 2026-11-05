import sys
from collections import deque
from PIL import Image, ImageFilter

# Источник: картинка сердца с нарисованной шахматкой вместо прозрачности.
SRC = sys.argv[1] if len(sys.argv) > 1 else "heart_source.png"
OUT = "src/main/resources/assets/hypersomnia/textures/misc/heart.png"
N = 96            # итоговый холст N x N
BG_MIN = 190      # фон шахматки: белый (~255) и светло-серый (~203)
GAMMA = 1.8       # >1 = темнее; сердце должно быть почти чёрным
GAIN = 0.80

im = Image.open(SRC).convert("L")
w, h = im.size
px = im.load()

# Фон = светлые пиксели, связанные с краем картинки (внутренние блики сердца не трогаем)
bg = bytearray(w * h)
q = deque()
for x in range(w):
    q.append((x, 0))
    q.append((x, h - 1))
for y in range(h):
    q.append((0, y))
    q.append((w - 1, y))
while q:
    x, y = q.popleft()
    if x < 0 or y < 0 or x >= w or y >= h or bg[y * w + x] or px[x, y] < BG_MIN:
        continue
    bg[y * w + x] = 1
    q.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))

mask = Image.new("L", (w, h))
mask.putdata([0 if b else 255 for b in bg])
mask = mask.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(0.8))

dark = im.point(lambda v: int(255 * GAIN * (v / 255.0) ** GAMMA))
rgba = Image.merge("RGBA", (dark, dark, dark, mask))

box = mask.point(lambda v: 255 if v > 16 else 0).getbbox()
rgba = rgba.crop(box)
side = max(rgba.size) + 6
canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
canvas.paste(rgba, ((side - rgba.width) // 2, (side - rgba.height) // 2))
canvas.resize((N, N), Image.LANCZOS).save(OUT)
with open(OUT + ".mcmeta", "w") as f:
    f.write('{\n  "texture": {\n    "blur": true\n  }\n}\n')