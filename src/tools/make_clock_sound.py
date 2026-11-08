"""
Бой напольных часов в мире снов (DreamClock): assets/hypersomnia/sounds/dream_clock_chime.ogg.
Запуск из корня: python run_tool.py make_clock_sound (или двойной клик tools.bat).

Почему свой файл. Раньше удар был ванильным bell_use01 — это церковный колокол: низкий гул
и долгий хвост, а у напольных часов бьют не колокола, а камертонные стержни. У свободного
стержня частичные тоны негармонические (1, 2.756, 5.404, 8.933), основной тон высокий и
сухой, низкого гула почти нет.

Здесь: сумма четырёх затухающих частичных тонов около D5 (587 Гц) с разными временами
затухания, чуть расстроенный двойник основного тона (медленные биения, как у настоящего
стержня) и короткий шумовой щелчок молоточка. ФНЧ 8 кГц и ФВЧ 180 Гц убирают и «песок»,
и низкий гул. Пик 0.82.

Нужен ffmpeg в PATH (как у остальных звуковых генераторов). Без него WAV остаётся в build/:
    ffmpeg -y -i build/dream_clock_chime.wav -c:a libvorbis -q:a 4 src/main/resources/assets/hypersomnia/sounds/dream_clock_chime.ogg
"""
import math
import os
import random
import shutil
import struct
import subprocess
import wave

SR = 44100
DUR = 2.0
F0 = 587.33          # D5: спокойный, «домашний» тон, не церковный
PEAK = 0.82

# (отношение к основному тону, громкость, время затухания с) — частичные тоны свободного стержня
PARTIALS = (
    (1.000, 1.00, 1.15),
    (1.0015, 0.30, 1.15),   # расстроенный двойник: медленное биение
    (2.756, 0.52, 0.62),
    (5.404, 0.26, 0.30),
    (8.933, 0.09, 0.15),
)


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


def chime():
    rng = random.Random(47)
    n = int(SR * DUR)
    sig = [0.0] * n
    for ratio, amp, tau in PARTIALS:
        w = 2 * math.pi * F0 * ratio / SR
        for i in range(n):
            t = i / SR
            sig[i] += amp * math.exp(-t / tau) * math.sin(w * i)
    # молоточек: очень короткий шумовой щелчок, иначе атака слишком синтетическая
    bp = bandpass(4200.0, 1.6)
    for i in range(n):
        t = i / SR
        if t < 0.025:
            sig[i] += 0.16 * bp.run(rng.gauss(0.0, 1.0)) * math.exp(-t / 0.004)
    # мягкая атака 2 мс (без щелчка постоянного тока) и чистое затухание в конце
    tail = 0.12
    for i in range(n):
        t = i / SR
        sig[i] *= 1.0 - math.exp(-t / 0.002)
        if t > DUR - tail:
            sig[i] *= 0.5 + 0.5 * math.cos(math.pi * (t - (DUR - tail)) / tail)
    lp, hp = lowpass(8000.0), highpass(180.0)
    sig = [hp.run(lp.run(s)) for s in sig]
    m = max(abs(s) for s in sig) or 1.0
    return [s / m * PEAK for s in sig]


def write_wav(path, sig):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767)) for s in sig))


os.makedirs("build", exist_ok=True)
sounds = os.path.join("src", "main", "resources", "assets", "hypersomnia", "sounds")
os.makedirs(sounds, exist_ok=True)

wav_path = os.path.join("build", "dream_clock_chime.wav")
write_wav(wav_path, chime())
out = os.path.join(sounds, "dream_clock_chime.ogg")
if shutil.which("ffmpeg"):
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error",
                    "-i", wav_path, "-c:a", "libvorbis", "-q:a", "4", out], check=True)
    print("wrote", out)
else:
    print("ERROR: нет ffmpeg в PATH. WAV лежит в:", wav_path)
