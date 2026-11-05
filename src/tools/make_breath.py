"""
Дыхание: мягкое пятно-туман для BreathOverlay.
Запуск из корня: python run_tool.py make_breath (или двойной клик tools.bat).
Кладёт src/main/resources/assets/hypersomnia/textures/misc/breath.png (+ .mcmeta blur).
"""
import os
from PIL import Image, ImageDraw, ImageFilter

W = H = 512
out_dir = os.path.join("src", "main", "resources", "assets", "hypersomnia", "textures", "misc")
os.makedirs(out_dir, exist_ok=True)

# Несколько наложенных эллипсов, чуть смещённых — пятно несимметричное, «дышит»
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
blobs = [
    (256, 280, 200, 140, 90),
    (200, 250, 160, 120, 70),
    (310, 300, 150, 110, 60),
    (260, 340, 220, 90,  55),
    (240, 220, 130, 100, 50),
]
for cx, cy, rx, ry, a in blobs:
    d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=(221, 230, 236, a))

# Размытие — мягкая кромка, как у пара
img = img.filter(ImageFilter.GaussianBlur(radius=48))

# Радиальная маска: к краям альфа к нулю, пятно не обрезается рамкой
px = img.load()
for y in range(H):
    for x in range(W):
        dx = (x - W / 2) / (W / 2)
        dy = (y - H / 2) / (H / 2)
        r = (dx * dx + dy * dy) ** 0.5
        k = max(0.0, 1.0 - r)
        k = k * k
        if k <= 0:
            px[x, y] = (0, 0, 0, 0)
        else:
            r0, g0, b0, a0 = px[x, y]
            px[x, y] = (r0, g0, b0, int(a0 * k))

out = os.path.join(out_dir, "breath.png")
img.save(out)
with open(out + ".mcmeta", "w", encoding="utf-8") as f:
    f.write('{"texture":{"blur":true}}')
print("wrote", out)