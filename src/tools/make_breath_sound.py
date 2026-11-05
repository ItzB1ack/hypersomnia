"""
Дыхание: спокойный выдох, три варианта (игра выбирает случайно, sounds.json -> breath_exhale).
Пишет assets/hypersomnia/sounds/breath_exhale.ogg, breath_exhale_2.ogg, breath_exhale_3.ogg.
Запуск из корня: python run_tool.py make_breath_sound (или двойной клик tools.bat).

Как устроен выдох: шум воздуха через рот/нос, окрашенный резонансами (форманты ~300-2500 Гц), без гула ниже 120 Гц;
мягкое начало (пик через 0.25-0.35 с), долгий плавный спад к тишине; чем слабее поток, тем темнее звук.
Прежний звук (0.45 с, резкая атака, низкий гул) звучал как пыхтение паровоза.

Нужен либо ffmpeg в PATH, либо пакет soundfile (pip install soundfile numpy).
Если ни того, ни другого — WAV остаются в build/, конвертни вручную:
    ffmpeg -y -i build/breath_exhale.wav -c:a libvorbis -q:a 4 src/main/resources/assets/hypersomnia/sounds/breath_exhale.ogg
"""
import math
import os
import random
import shutil
import struct
import subprocess
import wave

SR = 44100
BLOCK = 64  # коэффициенты фильтров пересчитываются каждые BLOCK сэмплов

# name, длительность, пик огибающей (с), крутизна, форманты (f начала, f конца, Q, громкость),
# воздух: срез ФНЧ на спаде и на пике (Гц), громкость; ФВЧ (Гц); пиковый уровень; зерно
VARIANTS = [
    ("breath_exhale", 1.25, 0.30, 1.7,
     ((560, 470, 2.2, 1.00), (1150, 1000, 2.8, 0.60), (2450, 2300, 3.5, 0.22)),
     (900, 2800, 0.50), 140, 0.27, 11),
    # через нос: ниже и глуше
    ("breath_exhale_2", 1.05, 0.24, 1.9,
     ((320, 280, 2.0, 1.00), (1050, 950, 3.0, 0.35), (2600, 2500, 4.0, 0.12)),
     (700, 2000, 0.45), 120, 0.25, 23),
    # чуть длиннее и медленнее
    ("breath_exhale_3", 1.50, 0.36, 1.5,
     ((600, 450, 2.0, 1.00), (1250, 1050, 2.6, 0.55), (2500, 2350, 3.5, 0.20)),
     (900, 2600, 0.50), 140, 0.26, 37),
]


def bandpass(f0, q):
    """RBJ band-pass с пиком 0 дБ -> (b0, b1, b2, a1, a2)."""
    w = 2 * math.pi * f0 / SR
    alpha = math.sin(w) / (2 * q)
    a0 = 1 + alpha
    return alpha / a0, 0.0, -alpha / a0, -2 * math.cos(w) / a0, (1 - alpha) / a0


def highpass(f0, q=0.7071):
    w = 2 * math.pi * f0 / SR
    alpha = math.sin(w) / (2 * q)
    c = math.cos(w)
    a0 = 1 + alpha
    return (1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0


class Biquad:
    def __init__(self, coef):
        self.c = coef
        self.x1 = self.x2 = self.y1 = self.y2 = 0.0

    def run(self, x):
        b0, b1, b2, a1, a2 = self.c
        y = b0 * x + b1 * self.x1 + b2 * self.x2 - a1 * self.y1 - a2 * self.y2
        self.x2, self.x1, self.y2, self.y1 = self.x1, x, self.y1, y
        return y


def envelope(t, dur, tp, a):
    """Гамма-образная огибающая (1 в момент tp), в последние 15% длины — плавно в ноль."""
    if t <= 0:
        return 0.0
    x = t / tp
    e = x ** a * math.exp(a * (1 - x))
    tail = dur * 0.15
    if t > dur - tail:
        e *= 0.5 + 0.5 * math.cos(math.pi * (t - (dur - tail)) / tail)
    return e


def flutter(rng, dur, step=0.04, depth=0.06):
    """Медленная неровность потока: случайные точки раз в step с, косинусная интерполяция (без периодики)."""
    pts = [rng.uniform(-1, 1) for _ in range(int(dur / step) + 3)]

    def at(t):
        i = int(t / step)
        f = (t / step) - i
        k = 0.5 - 0.5 * math.cos(math.pi * f)
        return 1 + depth * (pts[i] * (1 - k) + pts[i + 1] * k)
    return at


def exhale(dur, tp, a, formants, air, hp, peak, seed):
    rng = random.Random(seed)
    n = int(SR * dur)
    fl = flutter(rng, dur)
    bands = [Biquad(bandpass(f0, q)) for f0, _, q, _ in formants]
    hpf = Biquad(highpass(hp))
    lp1 = lp2 = 0.0
    air_lo, air_hi, air_gain = air
    out = []
    k_air = 0.0
    gains = [g for _, _, _, g in formants]
    for i in range(n):
        t = i / SR
        e = envelope(t, dur, tp, a)
        if i % BLOCK == 0:
            p = t / dur
            for b, (f0, f1, q, g) in zip(bands, formants):
                b.c = bandpass(f0 + (f1 - f0) * p, q)
            bright = min(1.0, e) ** 0.8
            fc = air_lo + (air_hi - air_lo) * bright
            k_air = 1 - math.exp(-2 * math.pi * fc / SR)
            # верхние форманты слабеют вместе с потоком
            gains = [g * (1.0 if j == 0 else 0.4 + 0.6 * bright) for j, (_, _, _, g) in enumerate(formants)]
        # у каждой полосы свой шум: суммы одного шума через разные фильтры гасят друг друга (провалы в спектре)
        y = sum(g * b.run(rng.gauss(0.0, 1.0)) for g, b in zip(gains, bands))
        x = rng.gauss(0.0, 1.0)
        lp1 += k_air * (x - lp1)
        lp2 += k_air * (lp1 - lp2)
        y += air_gain * lp2
        y = hpf.run(y)
        out.append(y * e * fl(t))
    m = max(abs(s) for s in out) or 1.0
    return [s / m * peak for s in out]


def write_wav(path, sig):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767)) for s in sig))


os.makedirs("build", exist_ok=True)
sounds = os.path.join("src", "main", "resources", "assets", "hypersomnia", "sounds")
os.makedirs(sounds, exist_ok=True)
for name, dur, tp, a, formants, air, hp, peak, seed in VARIANTS:
    sig = exhale(dur, tp, a, formants, air, hp, peak, seed)
    wav_path = os.path.join("build", name + ".wav")
    write_wav(wav_path, sig)
    out = os.path.join(sounds, name + ".ogg")
    if shutil.which("ffmpeg"):
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error",
                        "-i", wav_path, "-c:a", "libvorbis", "-q:a", "4", out], check=True)
        print("wrote", out)
    else:
        try:
            import soundfile as sf
            import numpy as np
            sf.write(out, np.array(sig, dtype=np.float32), SR, format="OGG", subtype="VORBIS")
            print("wrote", out, "(via soundfile)")
        except ImportError:
            print("ERROR: нет ffmpeg в PATH и нет пакета soundfile.")
            print("Установи одно из: 'pip install soundfile numpy' или ffmpeg.")
            print("WAV лежит в:", wav_path)
