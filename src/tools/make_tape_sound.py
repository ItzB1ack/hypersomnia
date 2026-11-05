"""
Кассета: шипение ленты (петля), щелчок механизма и короткий провал записи.
Пишет assets/hypersomnia/sounds/tape_hiss.ogg, tape_click.ogg, tape_dropout.ogg.
Запуск из корня: python run_tool.py make_tape_sound (или двойной клик tools.bat).

Нужен либо ffmpeg в PATH, либо пакет soundfile (pip install soundfile numpy).
Если ни того, ни другого — WAV остаются в build/, конвертни вручную:
    ffmpeg -y -i build/tape_hiss.wav -c:a libvorbis -q:a 4 src/main/resources/assets/hypersomnia/sounds/tape_hiss.ogg
"""
import math
import os
import random
import shutil
import struct
import subprocess
import wave

SR = 44100


def one_pole(sig, alpha):
    """Простой low-pass: alpha 0..1 (меньше — глуше)."""
    out = []
    prev = 0.0
    for s in sig:
        prev += alpha * (s - prev)
        out.append(prev)
    return out


def hiss():
    """4 с шипения: светлый шум минус низ, медленное «плавание» громкости, слабый фон 50 Гц.
    Конец плавно переходит в начало, чтобы петля не щёлкала."""
    dur = 4.0
    n = int(SR * dur)
    fade = int(SR * 0.25)
    rng = random.Random(4712)
    raw = [rng.uniform(-1.0, 1.0) for _ in range(n + fade)]
    low = one_pole(raw, 0.35)
    bright = [r - l for r, l in zip(raw, low)]          # убрать низ: остаётся «ш»
    soft = one_pole(bright, 0.55)                        # и самый верх тоже
    sig = []
    for i in range(n + fade):
        t = i / SR
        wow = 1.0 + 0.12 * math.sin(2 * math.pi * 0.5 * t) + 0.05 * math.sin(2 * math.pi * 1.3 * t + 1.0)
        hum = 0.018 * math.sin(2 * math.pi * 50 * t) + 0.008 * math.sin(2 * math.pi * 100 * t)
        sig.append(soft[i] * wow + hum)
    # петля: последние fade сэмплов смешиваются с началом
    for i in range(fade):
        k = i / fade
        sig[i] = sig[i] * k + sig[n + i] * (1.0 - k)
    sig = sig[:n]
    return normalize(sig, 0.30)


def click():
    """Щелчок клавиши магнитофона: короткий удар (низкий тон с быстрым спадом) и сухой треск."""
    dur = 0.18
    n = int(SR * dur)
    rng = random.Random(1947)
    noise = one_pole([rng.uniform(-1.0, 1.0) for _ in range(n)], 0.6)
    sig = []
    for i in range(n):
        t = i / SR
        thump = math.sin(2 * math.pi * 95 * t) * math.exp(-t * 45)
        snap = noise[i] * math.exp(-t * 160)
        rattle = noise[i] * 0.25 * math.exp(-max(0.0, t - 0.05) * 60) if t > 0.05 else 0.0
        sig.append(thump * 0.8 + snap * 0.9 + rattle)
    return normalize(sig, 0.55)


def dropout():
    """Провал записи (вымаранное имя): шум с уходящим вниз фильтром, 0.45 с."""
    dur = 0.45
    n = int(SR * dur)
    rng = random.Random(312)
    sig = []
    prev = 0.0
    for i in range(n):
        k = i / n
        alpha = 0.9 * (1.0 - k) + 0.04
        prev += alpha * (rng.uniform(-1.0, 1.0) - prev)
        env = min(1.0, i / (SR * 0.01)) * (1.0 - k) ** 1.5
        sig.append(prev * env)
    return normalize(sig, 0.45)


def normalize(sig, peak_to):
    peak = max(abs(s) for s in sig) or 1.0
    return [s / peak * peak_to for s in sig]


def write(name, sig):
    os.makedirs("build", exist_ok=True)
    wav_path = os.path.join("build", name + ".wav")
    with wave.open(wav_path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767)) for s in sig))
    out = os.path.join("src", "main", "resources", "assets", "hypersomnia", "sounds", name + ".ogg")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    if shutil.which("ffmpeg"):
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error",
                        "-i", wav_path, "-c:a", "libvorbis", "-q:a", "4", out], check=True)
        print("wrote", out)
        return
    try:
        import soundfile as sf
        import numpy as np
        sf.write(out, np.array(sig, dtype=np.float32), SR, format="OGG", subtype="VORBIS")
        print("wrote", out, "(via soundfile)")
    except ImportError:
        print("ERROR: нет ffmpeg в PATH и нет пакета soundfile.")
        print("Установи одно из: 'pip install soundfile numpy' или ffmpeg.")
        print("WAV лежит в:", wav_path)
        raise SystemExit(1)


write("tape_hiss", hiss())
write("tape_click", click())
write("tape_dropout", dropout())
