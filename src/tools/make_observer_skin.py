import random
from PIL import Image

OUT = "src/main/resources/assets/hypersomnia/textures/entity/"
rng = random.Random(47)

BASE = (5, 7, 10)
ECHO = (12, 16, 22)
SCLERA = (190, 202, 210, 255)
IRIS = (28, 34, 40, 255)

# (x0, y0, x1, y1) блоков UV скина 64x64: голова, тело, ноги, руки
BLOCKS = [(0, 0, 32, 16), (16, 16, 40, 32), (0, 16, 16, 32), (16, 48, 32, 64),
          (40, 16, 56, 32), (32, 48, 48, 64)]
LEGS = [(0, 16), (16, 48)]
ARMS = [(40, 16), (32, 48)]


def keep_prob(x, y):
    """Вероятность, что пиксель остаётся: ноги растворяются к ступням, кисти слегка."""
    for (bx, by) in LEGS:
        if bx <= x < bx + 16 and by + 4 <= y < by + 16:
            i = y - (by + 4)
            return 1.0 if i < 3 else max(0.06, 1.0 - (i - 2) / 9.0)
    for (bx, by) in ARMS:
        if bx <= x < bx + 16 and by + 4 <= y < by + 16:
            i = y - (by + 4)
            return 1.0 if i < 9 else 1.0 - (i - 8) * 0.22
    return 1.0


def make(echo, eyes):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    for (x0, y0, x1, y1) in BLOCKS:
        for y in range(y0, y1):
            for x in range(x0, x1):
                p = 0.22 if echo else keep_prob(x, y)
                if rng.random() > p:
                    continue
                n = rng.randint(-2, 2)
                c = ECHO if echo else BASE
                px[x, y] = (max(0, c[0] + n), max(0, c[1] + n), max(0, c[2] + n), 255)
    if eyes:  # лицо спереди: x 8..15, y 8..15; радужки смотрят внутрь, как в ИК-съёмке
        px[9, 11], px[10, 11] = SCLERA, IRIS
        px[13, 11], px[14, 11] = IRIS, SCLERA
    return img


make(False, False).save(OUT + "observer.png")
make(False, True).save(OUT + "observer_eyes.png")
make(True, False).save(OUT + "observer_echo.png")