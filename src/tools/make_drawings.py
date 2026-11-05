import math
import random
from PIL import Image, ImageChops, ImageDraw

W, H = 176, 132  # = ширина бумаги в DocumentScreen (200 - 2*12), высота Picture(.., 132)
OUT = "src/main/resources/assets/hypersomnia/textures/misc/"
rng = random.Random(8)

PAPER = (222, 214, 196)  # = 0xFFDED6C4 в DocumentScreen
RED, BLUE, YEL, GRN = (196, 58, 48), (52, 92, 182), (232, 192, 52), (62, 140, 72)
BRN, GRY, BLK, ORG, SKY = (132, 92, 62), (88, 90, 100), (40, 40, 48), (228, 130, 40), (130, 176, 222)


class Sheet:
    """Лист бумаги + цветные карандаши: неровные двойные линии, штриховка, зерно."""

    def __init__(self):
        self.base = Image.new("RGB", (W, H), PAPER)
        px = self.base.load()
        for y in range(H):
            for x in range(W):
                n = rng.randint(-6, 6)
                r, g, b = px[x, y]
                px[x, y] = (r + n, g + n, b + n)
        self.layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.layer)

    def stroke(self, pts, col, w=1, wob=0.9, passes=2, alpha=215):
        for _ in range(passes):
            path = []
            for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
                n = max(1, int(math.hypot(x1 - x0, y1 - y0) / 5))
                for i in range(n):
                    t = i / n
                    path.append((x0 + (x1 - x0) * t + rng.uniform(-wob, wob),
                                 y0 + (y1 - y0) * t + rng.uniform(-wob, wob)))
            path.append((pts[-1][0] + rng.uniform(-wob, wob), pts[-1][1] + rng.uniform(-wob, wob)))
            self.d.line(path, fill=col + (alpha,), width=w, joint="curve")

    def poly(self, pts, col, **kw):
        self.stroke(list(pts) + [pts[0]], col, **kw)

    def circle(self, cx, cy, r, col, **kw):
        self.stroke([(cx + r * math.cos(i * math.pi / 8), cy + r * math.sin(i * math.pi / 8)) for i in range(17)], col, **kw)

    def fill(self, pts, col, step=2.0, alpha=170, cross=False):
        mask = Image.new("L", (W, H), 0)
        ImageDraw.Draw(mask).polygon(pts, fill=255)
        tmp = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        td = ImageDraw.Draw(tmp)
        for flip in ((False, True) if cross else (False,)):
            k = -H
            while k < W:
                a = (k + rng.uniform(-1, 1), 0)
                b = (k + H + rng.uniform(-1, 1), H)
                td.line([(a[0], H - a[1]), (b[0], H - b[1])] if flip else [a, b], fill=col + (alpha,), width=1)
                k += step * rng.uniform(0.8, 1.3)
        tmp.putalpha(ImageChops.multiply(tmp.getchannel("A"), mask))
        self.layer.alpha_composite(tmp)

    def figure(self, x, y, h, col, arm_l=None, arm_r=None):
        """Палочка: (x, y) = ступни, h = рост; arm_* = смещение кисти от плеча."""
        hr = h * 0.14
        hy = y - h + hr
        self.circle(x, hy, hr, col)
        sh = y - h * 0.66
        self.stroke([(x, hy + hr), (x, y - h * 0.38)], col)
        al = arm_l or (-h * 0.22, h * 0.12)
        ar = arm_r or (h * 0.22, h * 0.12)
        self.stroke([(x + al[0], sh + al[1]), (x, sh), (x + ar[0], sh + ar[1])], col)
        self.stroke([(x - h * 0.2, y), (x, y - h * 0.38), (x + h * 0.2, y)], col)

    def save(self, name):
        a = self.layer.getchannel("A")
        px = a.load()
        for y in range(H):
            for x in range(W):
                if px[x, y] and rng.random() < 0.22:
                    px[x, y] = int(px[x, y] * 0.45)
        self.layer.putalpha(a)
        out = self.base.convert("RGBA")
        out.alpha_composite(self.layer)
        out.convert("RGB").save(OUT + name + ".png")


def rrect(x0, y0, x1, y1, r):
    pts = []
    for cx, cy, a0 in ((x1 - r, y0 + r, -90), (x1 - r, y1 - r, 0), (x0 + r, y1 - r, 90), (x0 + r, y0 + r, 180)):
        for i in range(6):
            a = math.radians(a0 + i * 18)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def star(cx, cy, r):
    return [(cx + (r if i % 2 == 0 else r * 0.45) * math.sin(i * math.pi / 5),
             cy - (r if i % 2 == 0 else r * 0.45) * math.cos(i * math.pi / 5)) for i in range(10)]


def drawing_0():
    """Дом, две фигуры у окна; окно закрашено глухо: снаружи ничего нет."""
    s = Sheet()
    s.fill([(8, 112), (168, 112), (168, 124), (8, 124)], GRN, step=3, alpha=110)
    s.stroke([(8, 112), (168, 112)], GRN)
    s.poly([(36, 60), (140, 60), (140, 112), (36, 112)], BRN)
    s.fill([(28, 60), (88, 22), (148, 60)], RED, step=2.2)
    s.poly([(28, 60), (88, 22), (148, 60)], RED, w=2)
    s.poly([(112, 112), (112, 84), (130, 84), (130, 112)], BRN)
    win = [(52, 72), (100, 72), (100, 106), (52, 106)]
    s.fill(win, BLK, step=1.3, alpha=240, cross=True)
    s.poly(win, BLK, w=2)
    s.figure(68, 106, 30, YEL)
    s.figure(86, 106, 21, ORG)
    s.save("drawing_0")


def drawing_1():
    """Капсула, внутри палочка с закрытыми глазами; рядом маленькая фигурка с протянутой рукой."""
    s = Sheet()
    cap = rrect(18, 38, 130, 84, 16)
    s.fill(cap, SKY, step=3, alpha=90)
    s.poly(cap, GRY, w=2)
    s.stroke([(34, 84), (28, 102)], GRY)
    s.stroke([(114, 84), (120, 102)], GRY)
    s.stroke([(20, 102), (130, 102)], GRY)
    s.circle(40, 61, 7, BLK)
    s.stroke([(37, 60), (40, 60)], BLK)
    s.stroke([(42, 60), (45, 60)], BLK)
    s.stroke([(47, 62), (100, 62)], BLK)
    s.stroke([(62, 62), (70, 53)], BLK)
    s.stroke([(100, 62), (114, 55)], BLK)
    s.stroke([(100, 62), (114, 69)], BLK)
    s.poly([(144, 36), (168, 36), (168, 62), (144, 62)], GRY, w=2)
    s.stroke([(146, 50), (151, 50), (154, 42), (158, 56), (161, 50), (166, 50)], RED)
    s.stroke([(130, 58), (138, 58), (138, 50), (144, 50)], GRY)
    s.figure(150, 124, 36, BLUE, arm_l=(-22, -12))
    s.save("drawing_1")


def drawing_2():
    """Солнце с закрытыми глазами и ресницами; вокруг звёзды, хотя день."""
    s = Sheet()
    s.fill([(0, 0), (W, 0), (W, 92), (0, 92)], SKY, step=2.6, alpha=120)
    s.fill([(0, 92), (W, 92), (W, H), (0, H)], GRN, step=2.4, alpha=140)
    cx, cy, r = 88, 50, 22
    for i in range(12):
        a = i * math.pi / 6
        s.stroke([(cx + (r + 4) * math.cos(a), cy + (r + 4) * math.sin(a)),
                  (cx + (r + 13) * math.cos(a), cy + (r + 13) * math.sin(a))], ORG, w=2)
    disc = [(cx + r * math.cos(i * math.pi / 8), cy + r * math.sin(i * math.pi / 8)) for i in range(16)]
    s.fill(disc, YEL, step=1.6, alpha=210)
    s.circle(cx, cy, r, ORG, w=2)
    for ex in (cx - 9, cx + 9):
        arc = [(ex + 6 * math.cos(math.radians(a)), cy - 3 + 3 * math.sin(math.radians(a))) for a in range(20, 161, 20)]
        s.stroke(arc, BLK)
        for (px_, py_) in (arc[1], arc[4], arc[6]):
            s.stroke([(px_, py_), (px_ + (px_ - ex) * 0.4, py_ + 4)], BLK)
    s.stroke([(cx - 5, cy + 10), (cx, cy + 12), (cx + 5, cy + 10)], BLK)
    for (sx, sy, sr) in ((20, 18, 6), (36, 52, 5), (60, 10, 4), (150, 20, 6), (160, 58, 5), (128, 86, 4)):
        s.fill(star(sx, sy, sr), YEL, step=1.5, alpha=220)
        s.poly(star(sx, sy, sr), (250, 240, 170), alpha=240)
    s.save("drawing_2")


def drawing_3():
    """Птица в клетке; дверца открыта, птица всё равно внутри."""
    s = Sheet()
    s.stroke([(24, 110), (152, 110)], BRN, w=2)
    s.fill([(44, 98), (132, 98), (132, 108), (44, 108)], BRN, step=2.5, alpha=150)
    s.poly([(44, 98), (132, 98), (132, 108), (44, 108)], BRN)

    def dome(x):
        return 62 - 40 * math.sqrt(max(0.0, 1 - ((x - 88) / 44) ** 2))

    s.stroke([(44 + i * 4.4, dome(44 + i * 4.4)) for i in range(21)], GRY, w=2)
    s.stroke([(44, 62), (44, 98)], GRY, w=2)
    for bx in range(52, 110, 10):
        s.stroke([(bx, dome(bx)), (bx, 98)], GRY)
    s.stroke([(132, 62), (132, 66)], GRY, w=2)
    s.stroke([(132, 96), (132, 98)], GRY, w=2)
    s.poly([(132, 66), (152, 60), (152, 92), (132, 96)], GRY, w=2)
    s.stroke([(88, 22), (88, 14)], GRY)
    s.circle(88, 11, 4, GRY)
    s.stroke([(60, 92), (112, 92)], BRN)
    body = [(84 + 10 * math.cos(i * math.pi / 8), 82 + 10 * math.sin(i * math.pi / 8)) for i in range(16)]
    s.fill(body, BLUE, step=1.6, alpha=210)
    s.circle(84, 82, 10, BLUE, w=2)
    s.circle(93, 72, 6, BLUE, w=2)
    s.fill([(99, 71), (106, 73), (99, 76)], ORG, step=1.2)
    s.stroke([(76, 86), (62, 94)], BLUE)
    s.stroke([(76, 82), (60, 86)], BLUE)
    s.stroke([(84, 91), (84, 93)], BLK)
    s.stroke([(88, 91), (88, 93)], BLK)
    s.save("drawing_3")


def drawing_4():
    """Два одинаковых папы: один спит в кровати, второй стоит рядом и не спит; между ними игрушка."""
    s = Sheet()
    s.stroke([(8, 112), (168, 112)], BRN, w=2)
    bed = [(18, 84), (104, 84), (104, 100), (18, 100)]
    s.fill(bed, SKY, step=3, alpha=90)
    s.poly(bed, BRN)
    s.stroke([(18, 100), (18, 112)], BRN)
    s.stroke([(104, 100), (104, 112)], BRN)
    s.stroke([(18, 84), (18, 68)], BRN, w=2)
    # спящий: голова, закрытые глаза, тело под одеялом
    s.circle(32, 76, 7, YEL)
    s.stroke([(29, 75), (32, 75)], BLK)
    s.stroke([(34, 75), (37, 75)], BLK)
    s.stroke([(39, 78), (96, 78)], YEL)
    s.fill([(46, 74), (104, 74), (104, 84), (46, 84)], GRN, step=2.4, alpha=120)
    # стоящий: тот же цвет, тот же рост, глаза открыты
    s.figure(138, 112, 44, YEL)
    s.stroke([(136, 73), (136, 74)], BLK)
    s.stroke([(141, 73), (141, 74)], BLK)
    # игрушка-зайчик на полу между ними
    s.circle(118, 107, 5, RED)
    s.stroke([(116, 102), (115, 94)], RED)
    s.stroke([(121, 102), (122, 94)], RED)
    s.save("drawing_4")

def drawing_blank():
    """Пустой лист: в углу едва заметное начало линии."""
    s = Sheet()
    s.stroke([(148, 114), (154, 109), (162, 108)], (120, 118, 110), passes=1, alpha=110)
    s.save("drawing_blank")


for f in (drawing_0, drawing_1, drawing_2, drawing_3, drawing_blank, drawing_4):  # drawing_4 последним: остальные листы не меняются
    f()