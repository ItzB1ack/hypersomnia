"""Исходник фото корпоратива: src/tools/photo_staff_raw.png (512x384, ч/б). Нужен только для пересборки.

Готовый исходник лежит в репозитории; make_photo.py печатает из него текстуру. Этот скрипт
показывает, как исходник получен, и позволяет повторить:
  1. грубая раскладка кадра (пятна яркости: капсула, табличка, двое, рукопожатие) -> img2img;
  2. на лицо Хэллорана дорисована толстая оправа -> второй img2img, слабее (очки «врастают»);
  3. из второго прохода берётся только лицо Хэллорана (мягкая маска, тон подогнан к первому).

Модель: Stable Diffusion v1.5 (лицензия CreativeML OpenRAIL-M; права на результат за
пользователем, ограничения лицензии касаются способов использования модели), сборка для
OpenVINO с 8-битными весами: AIFunOver/stable-diffusion-v1-5-openvino-8bit (Hugging Face).
Сэмплер DPM++ 2M Karras, считается на CPU; шум из numpy, поэтому seed воспроизводим
(на другом процессоре возможны мелкие отличия).

Запуск: python src/tools/make_photo_raw.py [папка_модели] [--out файл.png]
Модель (~1 ГБ) скачивается при первом запуске (по умолчанию в ~/.cache/hypersomnia_sd15_ov). Нужны: openvino, transformers,
huggingface_hub, numpy, Pillow. Памяти хватает 4 ГБ: веса свёрток один раз распаковываются
в unet_f32/ (~2.6 ГБ на диске), иначе CPU-плагин распаковывает их в памяти дважды.
Около 6 минут на 2 ядрах.
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "photo_staff_raw.png")
MODEL_REPO = "AIFunOver/stable-diffusion-v1-5-openvino-8bit"
W, H = 512, 384
STEPS, CFG = 20, 7.5
SEED_1, STRENGTH_1 = 41, 0.62        # проход 1: из раскладки
SEED_2, STRENGTH_2 = 141, 0.4        # проход 2: очки
FACE_H = (306, 88, 376, 140)         # эллипс лица Хэллорана (x0, y0, x1, y1)
PROMPT = ("1970s black and white photograph, two men shaking hands and smiling for the camera, "
          "the man on the left wears a white lab coat, the man on the right is older and wears a dark suit, "
          "white shirt, tie and thick black rimmed glasses, behind them stands a large white rounded capsule, "
          "a small sign on a stand at the left, plain grey studio backdrop, flash photo, old faded print, "
          "soft focus, film grain")
NEGATIVE = ("color, sepia, modern, digital, sharp, painting, drawing, illustration, cartoon, 3d, cgi, render, "
            "text, watermark, signature, deformed, bad anatomy, extra fingers, extra arms, mutated hands, "
            "cropped head, out of frame, stethoscope, shelves, bottles, window, door, crowd")


# ---------------------------------------------------------------- раскладка

def layout():
    """Пятна яркости в 4x, затем уменьшение и мягкость: композиция без деталей."""
    s = 4
    yy, xx = np.mgrid[0:H * s, 0:W * s] / s
    bgv = 168 + 26 * (xx / W) * (1 - yy / H) - 15 * (((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2)
    im = Image.fromarray(bgv.clip(0, 255).astype(np.uint8))
    d = ImageDraw.Draw(im)

    def E(x0, y0, x1, y1, v): d.ellipse([x0 * s, y0 * s, x1 * s, y1 * s], fill=v)
    def R(x0, y0, x1, y1, v, r=0): d.rounded_rectangle([x0 * s, y0 * s, x1 * s, y1 * s], radius=r * s, fill=v)
    def P(pts, v): d.polygon([(x * s, y * s) for x, y in pts], fill=v)
    def L(a, b, v, w): d.line([(a[0] * s, a[1] * s), (b[0] * s, b[1] * s)], fill=v, width=w * s)

    # капсула 47: купол, тёмный обод, шов
    cx, top, rw = 256, -40, 112
    pod = Image.new("L", im.size, 0)
    pd = ImageDraw.Draw(pod)
    pd.ellipse([(cx - rw) * s, top * s, (cx + rw) * s, (top + 2 * rw) * s], fill=255)
    pd.rectangle([(cx - rw) * s, (top + rw) * s, (cx + rw) * s, H * s], fill=255)
    shade = (1 - 0.6 * ((xx - cx) / rw) ** 2).clip(0, 1)
    im.paste(Image.fromarray((200 * shade + 30).clip(0, 255).astype(np.uint8)), (0, 0), pod)
    R(cx - rw + 6, 22, cx + rw - 6, 34, 70)
    R(cx - rw, 150, cx + rw, 154, 130)
    # табличка на стойке слева
    R(60, 262, 68, 384, 70)
    R(8, 224, 122, 264, 60, 2); R(11, 227, 119, 261, 236, 1); R(24, 239, 106, 250, 130)
    # Марш: халат, рубашка, галстук, бейдж, голова
    P([(80, 384), (92, 196), (120, 172), (165, 162), (210, 172), (238, 196), (250, 384)], 226)
    P([(84, 210), (108, 200), (114, 384), (84, 384)], 214)
    P([(148, 168), (165, 214), (182, 168)], 200)
    P([(160, 182), (170, 182), (174, 290), (165, 302), (156, 290)], 55)
    R(126, 236, 152, 254, 246, 2)
    E(132, 72, 198, 154, 150); E(130, 64, 200, 104, 50)
    R(152, 146, 178, 172, 142)
    # Хэллоран: тёмный костюм, рубашка, галстук, очки, голова
    P([(262, 384), (274, 196), (304, 172), (350, 162), (396, 172), (426, 196), (440, 384)], 64)
    P([(332, 168), (350, 228), (368, 168)], 226)
    P([(345, 180), (355, 180), (359, 276), (350, 288), (341, 276)], 36)
    E(317, 70, 383, 152, 152); E(318, 64, 382, 98, 78)
    R(337, 146, 363, 172, 142)
    R(322, 104, 347, 122, 30, 3); R(353, 104, 378, 122, 30, 3); R(345, 109, 355, 113, 30)
    R(327, 108, 342, 118, 172, 2); R(358, 108, 373, 118, 172, 2)
    # руки: рукав Хэллорана (тёмный) через просвет к рукопожатию перед халатом Марша
    P([(84, 214), (108, 206), (112, 330), (88, 334)], 214)
    P([(418, 206), (440, 220), (444, 340), (420, 336)], 54)
    P([(206, 196), (232, 204), (252, 310), (228, 322)], 214)
    L((206, 198), (228, 322), 168, 3)
    P([(292, 192), (320, 206), (262, 326), (236, 312)], 58)
    L((318, 206), (262, 326), 100, 2)
    R(232, 304, 252, 324, 238, 3)
    E(214, 300, 252, 338, 165)
    L((220, 318), (248, 312), 120, 2)
    return im.resize((W, H), Image.LANCZOS).filter(ImageFilter.GaussianBlur(1.4)).convert("RGB")


def paint_glasses(im):
    """Толстая тёмная оправа и мутные стёкла поверх лица Хэллорана (подсказка второму проходу)."""
    s = 4
    big = im.resize((W * s, H * s), Image.LANCZOS).convert("RGBA")
    ov = Image.new("RGBA", big.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    frame = (28, 28, 28, 255)
    for x0, y0, x1, y1 in ((317, 109, 337, 123), (341, 107, 361, 121)):
        d.rounded_rectangle([x0 * s, y0 * s, x1 * s, y1 * s], radius=4 * s, fill=(150, 150, 150, 120), outline=frame, width=int(2.6 * s))
    for a, b in (((336, 114), (342, 113)), ((317, 113), (311, 112)), ((361, 111), (367, 110))):
        d.line([(a[0] * s, a[1] * s), (b[0] * s, b[1] * s)], fill=frame, width=int(2.2 * s))
    return Image.alpha_composite(big, ov).convert("RGB").resize((W, H), Image.LANCZOS)


def blend_face(base, face):
    """Лицо из второго прохода в первый: мягкий эллипс, среднее и разброс яркости как у первого."""
    a = np.asarray(base.convert("L"), dtype=np.float64)
    b = np.asarray(face.convert("L"), dtype=np.float64)
    m = Image.new("L", (W, H), 0)
    ImageDraw.Draw(m).ellipse(list(FACE_H), fill=255)
    m = np.asarray(m.filter(ImageFilter.GaussianBlur(6)), dtype=np.float64) / 255
    inside = m > 0.5
    b = (b - b[inside].mean()) * (a[inside].std() / b[inside].std()) + a[inside].mean()
    c = a * (1 - m) + b * m
    return Image.fromarray(np.clip(np.round(c), 0, 255).astype(np.uint8))


# ---------------------------------------------------------------- Stable Diffusion на OpenVINO

def prepare(model_dir):
    """Скачать модель; свёртки UNet один раз распаковать из u8 в f32 (unet_f32/, читается через mmap)."""
    if not os.path.exists(os.path.join(model_dir, "model_index.json")):
        from huggingface_hub import snapshot_download
        snapshot_download(MODEL_REPO, local_dir=model_dir)
    dst = os.path.join(model_dir, "unet_f32", "openvino_model.xml")
    if os.path.exists(dst):
        return dst
    import openvino as ov
    from openvino import opset13 as ops
    m = ov.Core().read_model(os.path.join(model_dir, "unet", "openvino_model.xml"))
    for op in m.get_ordered_ops():
        if op.get_type_name() != "Convolution":
            continue
        w = op.input_value(1).get_node()
        if w.get_type_name() == "Convert":
            w = w.input_value(0).get_node()
        sub, scale = w.input_value(0).get_node(), w.input_value(1).get_node()
        q = sub.input_value(0).get_node().input_value(0).get_node()
        zp = sub.input_value(1).get_node().input_value(0).get_node()
        wf = (q.get_data().astype(np.float32) - zp.get_data().astype(np.float32)) * scale.get_data().astype(np.float32)
        op.input(1).replace_source_output(ops.constant(np.ascontiguousarray(wf)).output(0))
    m.validate_nodes_and_infer_types()
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    ov.save_model(m, dst, compress_to_fp16=False)
    return dst


class Diffusion:
    def __init__(self, model_dir):
        import openvino as ov
        from transformers import CLIPTokenizer
        self.dir = model_dir
        self.unet_xml = prepare(model_dir)
        self.core = ov.Core()
        self.tok = CLIPTokenizer.from_pretrained(os.path.join(model_dir, "tokenizer"))
        betas = np.linspace(0.00085 ** 0.5, 0.012 ** 0.5, 1000, dtype=np.float64) ** 2
        self.sig_all = np.sqrt((1 - np.cumprod(1.0 - betas)) / np.cumprod(1.0 - betas))

    def compile(self, name):
        path = self.unet_xml if name == "unet" else os.path.join(self.dir, name, "openvino_model.xml")
        return self.core.compile_model(path, "CPU")

    def embed(self, te, text):
        ids = self.tok(text, padding="max_length", max_length=77, truncation=True, return_tensors="np").input_ids
        return te(ids.astype(np.int32))[0].astype(np.float32)

    def karras(self, n, rho=7.0):
        smin, smax = float(self.sig_all[0]), float(self.sig_all[-1])
        r = np.linspace(0, 1, n)
        return np.append((smax ** (1 / rho) + r * (smin ** (1 / rho) - smax ** (1 / rho))) ** rho, 0.0)

    def img2img(self, init, prompt, negative, seed, steps, strength, cfg):
        rng = np.random.default_rng(seed)
        te = self.compile("text_encoder")
        cond, uncond = self.embed(te, prompt), self.embed(te, negative)
        del te
        ve = self.compile("vae_encoder")
        a = np.asarray(init, dtype=np.float32) / 127.5 - 1.0
        par = ve(a.transpose(2, 0, 1)[None])[0]
        del ve
        mean, logvar = par[:, :4], par[:, 4:]
        x0 = (mean + np.exp(0.5 * np.clip(logvar, -30, 20)) * rng.standard_normal(mean.shape)).astype(np.float32) * 0.18215
        sig = self.karras(steps)
        start = int(round(steps * (1 - strength)))
        x = x0 + rng.standard_normal(x0.shape).astype(np.float32) * sig[start]
        unet = self.compile("unet")
        log_all = np.log(self.sig_all)
        old, t_prev = None, None
        for i in range(start, steps):
            sg, sn = sig[i], sig[i + 1]
            xin = (x / math.sqrt(sg * sg + 1)).astype(np.float32)
            t = np.array([int(np.argmin(np.abs(log_all - math.log(sg))))], dtype=np.int64)
            eu = unet({"sample": xin, "timestep": t, "encoder_hidden_states": uncond})[0]
            ec = unet({"sample": xin, "timestep": t, "encoder_hidden_states": cond})[0]
            den = x - sg * (eu + cfg * (ec - eu))
            if sn == 0:
                x = den
                break
            h = math.log(sg) - math.log(sn)
            if old is None:
                x = (sn / sg) * x - math.expm1(-h) * den
            else:
                r = (math.log(1 / sg) - t_prev) / h
                x = (sn / sg) * x - math.expm1(-h) * ((1 + 1 / (2 * r)) * den - (1 / (2 * r)) * old)
            old, t_prev = den, math.log(1 / sg)
            print(f"step {i + 1}/{steps}", flush=True)
        del unet
        vd = self.compile("vae_decoder")
        img = vd((x / 0.18215).astype(np.float32))[0][0]
        img = np.clip(img / 2 + 0.5, 0, 1).transpose(1, 2, 0)
        return Image.fromarray((img * 255).round().astype(np.uint8))


def main(argv):
    args = argv[1:]
    out = OUT
    if "--out" in args:
        i = args.index("--out"); out = args[i + 1]; args = args[:i] + args[i + 2:]
    model_dir = args[0] if args else os.path.join(os.path.expanduser("~"), ".cache", "hypersomnia_sd15_ov")
    sd = Diffusion(model_dir)
    first = sd.img2img(layout(), PROMPT, NEGATIVE, SEED_1, STEPS, STRENGTH_1, CFG)
    second = sd.img2img(paint_glasses(first), PROMPT, NEGATIVE, SEED_2, STEPS, STRENGTH_2, CFG)
    blend_face(first, second).save(out)
    print(out)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
