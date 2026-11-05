import math
from PIL import Image

N = 128
OUT = "src/main/resources/assets/hypersomnia/textures/misc/dream_sky_void.png"
img = Image.new("RGBA", (N, N))
px = img.load()
for y in range(N):
    for x in range(N):
        r = math.hypot(x + 0.5 - N / 2, y + 0.5 - N / 2) / (N / 2)
        a = 1.0 if r < 0.75 else max(0.0, 1.0 - (r - 0.75) / 0.25)
        a = a * a * (3 - 2 * a)
        px[x, y] = (3, 5, 10, round(a * 255))
img.save(OUT)
with open(OUT + ".mcmeta", "w") as f:
    f.write('{\n  "texture": {\n    "blur": true\n  }\n}\n')