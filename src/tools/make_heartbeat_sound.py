"""
Сердцебиение в погоне (ChaseClient): «тук-тук», три варианта (игра выбирает случайно, sounds.json -> heartbeat).
Пишет assets/hypersomnia/sounds/heartbeat.ogg, heartbeat_2.ogg, heartbeat_3.ogg.
Запуск из корня: python run_tool.py make_heartbeat_sound (или двойной клик tools.bat).

Как устроен удар (вторая версия). Прежний звучал как боевой барабан: чистый тон с падением высоты 115 -> 52 Гц (так
звучит том), резкая атака 4 мс, звон ~0.1 с и насыщение tanh. Настоящий стук сердца — не тон, а глухие толчки
сквозь тело: несколько коротких неровных колебаний 45-110 Гц, каждое затухает за 2-3 периода.
Здесь каждый тон (S1 «тук», через ~0.17 с S2 «тук» тише и выше) — 2-3 под-толчка: короткий всплеск шума через
резонатор с низкой добротностью (Q 2-2.7, затухание ~10 мс). Шум делает колебание неровным (нет «высоты» барабана),
низкая добротность убирает звон. Сверху ФНЧ 240 Гц (глухо, как изнутри), снизу ФВЧ 38 Гц, плюс тихий
«шум крови» после удара. Без насыщения. Пик 0.8 при запасе ~17 дБ (как у ванильного сердца вардена).
Частоту, громкость и чуть-чуть высоту в игре меняет ChaseClient по панике.

Нужен либо ffmpeg в PATH, либо пакет soundfile (pip install soundfile numpy).
Если ни того, ни другого — WAV остаются в build/, конвертни вручную:
    ffmpeg -y -i build/heartbeat.wav -c:a libvorbis -q:a 4 src/main/resources/assets/hypersomnia/sounds/heartbeat.ogg
"""
import math
import os
import random
import shutil
import struct
import subprocess
import wave

SR = 44100
DUR = 0.60

# name, зерно, S1: под-толчки (время с, частота Гц, добротность, громкость), S2 через (с), S2: под-толчки, шум крови
VARIANTS = [
    ("heartbeat", 11,
     ((0.000, 62, 2.2, 1.00), (0.017, 90, 2.6, 0.85), (0.036, 50, 2.0, 0.55)),
     0.170, ((0.000, 82, 2.4, 0.85), (0.015, 108, 2.4, 0.60)), 0.20),
    ("heartbeat_2", 23,
     ((0.000, 58, 2.0, 1.00), (0.020, 84, 2.5, 0.90), (0.040, 47, 2.0, 0.50)),
     0.182, ((0.000, 78, 2.3, 0.90), (0.016, 102, 2.5, 0.62)), 0.22),
    ("heartbeat_3", 37,
     ((0.000, 66, 2.3, 1.00), (0.015, 95, 2.7, 0.80), (0.033, 54, 2.1, 0.60)),
     0.160, ((0.000, 86, 2.5, 0.85), (0.013, 114, 2.4, 0.60)), 0.18),
]
START = 0.012   # тишина в начале файла (с)
BURST = 0.005   # длина всплеска шума, возбуждающего резонатор (с)


class Biquad:
    def __init__(self, b0, b1, b2, a1, a2):
        self.c = (b0, b1, b2, a1, a2)
        self.x1 = self.x2 = self.y1 = self.y2 = 0.0

    def run(self, x):
        b0, b1, b2, a1, a2 = self.c
        y = b0 * x + b1 * self.x1 + b2 * self.x2 - a1 * self.y1 - a2 * self.y2
        self.x2, self.x1 = self.x1, x
        self.y2, self.y1 = self.y1, y
        return y


def bandpass(f0, q):
    w = 2 * math.pi * f0 / SR
    al = math.sin(w) / (2 * q)
    a0 = 1 + al
    return Biquad(al / a0, 0.0, -al / a0, -2 * math.cos(w) / a0, (1 - al) / a0)


def lowpass(f0, q=0.7071):
    w = 2 * math.pi * f0 / SR
    al = math.sin(w) / (2 * q)
    c = math.cos(w)
    a0 = 1 + al
    return Biquad((1 - c) / 2 / a0, (1 - c) / a0, (1 - c) / 2 / a0, -2 * c / a0, (1 - al) / a0)


def highpass(f0, q=0.7071):
    w = 2 * math.pi * f0 / SR
    al = math.sin(w) / (2 * q)
    c = math.cos(w)
    a0 = 1 + al
    return Biquad((1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - al) / a0)


def push(rng, n, at, f, q, amp):
    """Один под-толчок: всплеск шума (окно Ханна) через резонатор f/Q."""
    out = [0.0] * n
    bp = bandpass(f, q)
    i0 = int(at * SR)
    nb = int(BURST * SR)
    for i in range(i0, n):
        k = i - i0
        x = 0.0
        if k < nb:
            x = rng.gauss(0.0, 1.0) * (0.5 - 0.5 * math.cos(2 * math.pi * k / nb))
        out[i] = bp.run(x) * amp
    return out


def beat(seed, s1, s2_at, s2, blood):
    rng = random.Random(seed)
    n = int(SR * DUR)
    sig = [0.0] * n
    parts = [(START + t, f, q, a) for t, f, q, a in s1] + [(START + s2_at + t, f, q, a) for t, f, q, a in s2]
    for at, f, q, a in parts:
        p = push(rng, n, at, f, q, a)
        for i in range(n):
            sig[i] += p[i]
    m = max(abs(s) for s in sig) or 1.0
    sig = [s / m for s in sig]
    # шум крови: бурый шум через ФНЧ 110 Гц, вспухает после каждого тона и гаснет
    lp_b = lowpass(110)
    brown = 0.0
    for i in range(n):
        t = i / SR - START
        brown = 0.995 * brown + 0.05 * rng.gauss(0.0, 1.0)
        e = 0.0
        if t > 0:
            e += (1 - math.exp(-t / 0.012)) * math.exp(-t / 0.09)
        if t > s2_at:
            e += 0.6 * (1 - math.exp(-(t - s2_at) / 0.012)) * math.exp(-(t - s2_at) / 0.07)
        sig[i] += blood * 4.0 * lp_b.run(brown) * e
    # глухо (ФНЧ 240 Гц, дважды) и без инфразвука (ФВЧ 38 Гц, дважды: гул ниже 40 Гц в наушниках давит, как бочка)
    lp1, lp2, hp1, hp2 = lowpass(240), lowpass(240), highpass(38), highpass(38)
    sig = [hp2.run(hp1.run(lp2.run(lp1.run(s)))) for s in sig]
    tail = 0.05
    for i in range(n):
        t = i / SR
        if t > DUR - tail:
            sig[i] *= 0.5 + 0.5 * math.cos(math.pi * (t - (DUR - tail)) / tail)
    m = max(abs(s) for s in sig) or 1.0
    return [s / m * 0.8 for s in sig]


def write_wav(path, sig):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767)) for s in sig))


os.makedirs("build", exist_ok=True)
sounds = os.path.join("src", "main", "resources", "assets", "hypersomnia", "sounds")
os.makedirs(sounds, exist_ok=True)
for name, seed, s1, s2_at, s2, blood in VARIANTS:
    sig = beat(seed, s1, s2_at, s2, blood)
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
