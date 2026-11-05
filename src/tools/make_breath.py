"""
Дыхание: текстуры пара изо рта для частицы hypersomnia:breath (client/BreathParticle).
Запуск из корня: python run_tool.py make_breath (или двойной клик tools.bat).
Кладёт src/main/resources/assets/hypersomnia/textures/particle/breath_0.png .. breath_3.png (32x32);
список кадров - assets/hypersomnia/particles/breath.json (его скрипт не трогает).

Каждая текстура - рыхлое облачко: мягкий круг, изъеденный шумом, края уходят в прозрачность. Цвет #DDE6EC
(светлый из палитры мода); прозрачность и рост задаёт сама частица. Зёрна фиксированы: те же файлы при повторном запуске.
"""
import os
import random
from PIL import Image, ImageFilter

S = 32
COLOR = (221, 230, 236)
out_dir = os.path.join("src", "main", "resources", "assets", "hypersomnia", "textures", "particle")
os.makedirs(out_dir, exist_ok=True)

# зерно, вытянутость по x и y, сила шума, плотность
VARIANTS = [(11, 1.00, 0.92, 0.55, 1.00), (23, 1.10, 0.85, 0.65, 0.92),
            (37, 0.90, 1.00, 0.60, 0.95), (41, 1.05, 1.05, 0.75, 0.85)]


def value_noise(rnd, cells):
    """Плавный шум 0..1: случайная сетка cells x cells, растянутая бикубически до S x S."""
    small = Image.new("L", (cells, cells))
    small.putdata([rnd.randint(0, 255) for _ in range(cells * cells)])
    return small.resize((S, S), Image.BICUBIC)


for i, (seed, sx, sy, rough, dens) in enumerate(VARIANTS):
    rnd = random.Random(seed)
    n1 = value_noise(rnd, 4).load()
    n2 = value_noise(rnd, 7).load()
    alpha = Image.new("L", (S, S))
    pa = alpha.load()
    for y in range(S):
        for x in range(S):
            dx = (x + 0.5 - S / 2) / (S / 2) / sx
            dy = (y + 0.5 - S / 2) / (S / 2) / sy
            r = (dx * dx + dy * dy) ** 0.5
            base = max(0.0, 1.0 - r) ** 1.6                       # мягкий круг
            n = 0.65 * n1[x, y] / 255.0 + 0.35 * n2[x, y] / 255.0  # рыхлость
            k = base * (1.0 - rough + rough * n * 1.6) * dens
            pa[x, y] = max(0, min(255, int(255 * k)))
    alpha = alpha.filter(ImageFilter.GaussianBlur(radius=0.8))
    img = Image.new("RGBA", (S, S), COLOR + (0,))
    img.putalpha(alpha)
    out = os.path.join(out_dir, "breath_%d.png" % i)
    img.save(out)
    print("wrote", out)
