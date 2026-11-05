"""
Дыхание: короткий тихий вдох и долгий выдох «хааа», три варианта (игра выбирает случайно, sounds.json -> breath_exhale).
Пишет assets/hypersomnia/sounds/breath_exhale.ogg, breath_exhale_2.ogg, breath_exhale_3.ogg.
Запуск из корня: python run_tool.py make_breath_sound (или двойной клик tools.bat).
Нужны numpy (pip install numpy) и ffmpeg в PATH (без ffmpeg WAV остаются в build/, конвертни вручную:
    ffmpeg -y -i build/breath_exhale.wav -c:a libvorbis -q:a 4 src/main/resources/assets/hypersomnia/sounds/breath_exhale.ogg).

Почему прежний выдох не узнавался: одиночная волна шума любой формы звучит как ветер или шипение. Дыхание ухо узнаёт
  1) по паре «тихий короткий вдох -> пауза -> громкий долгий выдох»;
  2) по тому, что выдох окрашен открытым ртом: шум идёт через гласную «а» (форманты ~700/1200/2500 Гц);
  3) по мягкому толчку воздуха в начале выдоха (низкий «пух» у губ) и едва слышному голосу под ним (~100 Гц, как во вздохе);
  4) по концу: поток слабеет, звук темнеет и сужается (рот прикрывается).
Выдох всегда начинается ровно через EXHALE_AT = 0.50 с после начала файла: в этот момент клиент выпускает пар
(client/BreathClient, ObserverTuning.BREATH_INHALE_TICKS = 10). Меняешь EXHALE_AT - поменяй и тики.

Синтез: белый шум, каждый кадр STFT (23 мс) умножен на спектр рта; голос - сумма гармоник с дрожью высоты, окрашенная
тем же спектром. Зёрна фиксированы: повторный запуск даёт те же файлы.
"""
import math
import os
import shutil
import subprocess
import sys
import wave

try:
    import numpy as np
except ImportError:
    sys.exit("Нужен numpy: pip install numpy")

SOUNDS = os.path.join("src", "main", "resources", "assets", "hypersomnia", "sounds")
BUILD = "build"
SR = 44100
N = 1024
HOP = 256
FREQ = np.fft.rfftfreq(N, 1.0 / SR)
EXHALE_AT = 0.50        # с: начало выдоха (= BREATH_INHALE_TICKS / 20)
GAP = 0.09              # с: пауза между вдохом и выдохом

# name, зерно, вдох (длительность с, громкость, форманты), выдох (длительность с, форманты в начале, в конце,
#   яркость в начале Гц, в конце Гц), толчок воздуха, голос (громкость, f0 начала, f0 конца), итоговый пик
VARIANTS = [
    # обычный: вдох ртом, выдох «хааа» с еле слышным голосом
    ("breath_exhale", 11,
     (0.38, 0.30, (430, 1750, 2650, 3600)),
     (1.10, (720, 1230, 2500, 3450), (540, 1120, 2380, 3300), 6400, 1700),
     0.40, (0.17, 112, 96), 0.80),
    # короче и глуше: вдох носом (выше, уже), выдох без голоса
    ("breath_exhale_2", 23,
     (0.34, 0.24, (520, 1350, 2900, 3900)),
     (0.95, (640, 1150, 2450, 3400), (480, 1060, 2350, 3250), 5200, 1500),
     0.30, (0.0, 110, 100), 0.74),
    # усталый вздох: длиннее, голос заметнее
    ("breath_exhale_3", 37,
     (0.40, 0.32, (450, 1650, 2600, 3550)),
     (1.40, (760, 1260, 2520, 3480), (520, 1100, 2350, 3280), 6000, 1500),
     0.45, (0.30, 104, 88), 0.82),
]
BW = np.array([200.0, 240.0, 320.0, 400.0])     # ширина формант (Гц), у дыхания шире, чем у голоса
AMP = np.array([1.0, 0.70, 0.40, 0.20])


def lorentz(f, F, B):
    return 1.0 / (1.0 + ((f - F) / (B / 2.0)) ** 2)


def mouth(F, widen=1.0):
    """Огибающая рта: сумма четырёх формант."""
    e = np.zeros(FREQ.size)
    for k in range(4):
        e += AMP[k] * lorentz(FREQ, F[k], BW[k] * widen)
    return e


def lowpass(fc, order=4):
    return 1.0 / (1.0 + (FREQ / fc) ** order)


def highpass(fc, order=4):
    return (FREQ / fc) ** order / (1.0 + (FREQ / fc) ** order)


def smooth_noise(rng, m, k):
    a = rng.standard_normal(m + k)
    a = np.convolve(a, np.hanning(k) / np.hanning(k).sum(), mode="same")[:m]
    return a / max(1e-9, np.std(a))


def voice_source(n, rng, f0a, f0b, t0, dur):
    """Голосовой источник: гармоники 1/k (до 4 кГц), высота плывёт от f0a к f0b, дрожь 1.5 %."""
    t = np.arange(n) / SR
    u = np.clip((t - t0) / dur, 0.0, 1.0)
    jit = 1.0 + 0.015 * np.interp(t, np.linspace(0, t[-1], 40), rng.standard_normal(40))
    f0 = (f0a + (f0b - f0a) * u) * jit
    ph = 2 * math.pi * np.cumsum(f0) / SR
    out = np.zeros(n)
    for k in range(1, int(4000 / f0a) + 1):
        out += np.sin(k * ph + rng.uniform(0, 2 * math.pi)) / k ** 1.1
    return out / np.max(np.abs(out))


def render(seed, inh, exh, puff, voice, peak):
    rng = np.random.default_rng(seed)
    t_in, g_in, F_in = inh
    t_ex, F_a, F_b, bright_a, bright_b = exh
    v_gain, f0a, f0b = voice
    total = EXHALE_AT + t_ex + 0.15
    M = int(total * SR / HOP) + 4
    L = (M - 1) * HOP + N
    E_noise = np.zeros((M, FREQ.size))
    E_voice = np.zeros((M, FREQ.size))
    flutter = 1.0 + 0.12 * smooth_noise(rng, M, 15)       # поток воздуха неровный
    in_start = EXHALE_AT - GAP - t_in
    hp = highpass(140.0)
    for m in range(M):
        t = (m * HOP + N / 2) / SR
        # вдох: нарастает, к концу мягко обрывается, когда лёгкие полны
        w = (t - in_start) / t_in
        if 0.0 <= w <= 1.0:
            a = math.sin(math.pi * min(1.0, w * 0.85)) ** 0.9 * (0.55 + 0.45 * w)
            if w > 0.85:
                a *= 0.5 + 0.5 * math.cos(math.pi * (w - 0.85) / 0.15)
            spec = mouth(F_in, 1.8) + 0.35 * lorentz(FREQ, 4600, 3200)
            E_noise[m] += g_in * a * flutter[m] * spec * lowpass(7000) * hp
        # выдох
        u = (t - EXHALE_AT) / t_ex
        if 0.0 <= u <= 1.0:
            att = 0.045 / t_ex
            if u < att:
                a = 0.5 - 0.5 * math.cos(math.pi * u / att)
                v = 0.0
            else:
                v = (u - att) / (1.0 - att)
                a = (1.0 - v) ** 1.7 * (1.0 - 0.2 * v)
            F = tuple(F_a[k] + (F_b[k] - F_a[k]) * v for k in range(4))
            bright = bright_b + (bright_a - bright_b) * (1.0 - v) ** 1.3
            spec = mouth(F, 1.35 + 0.5 * v) * lowpass(bright) * hp
            E_noise[m] += a * flutter[m] * spec
            # толчок воздуха у губ: низкий «пух» в первые 0.15 с
            dt = t - EXHALE_AT
            if puff > 0:
                p = (1.0 - math.exp(-dt / 0.012)) * math.exp(-dt / 0.075)
                E_noise[m] += puff * p * lorentz(FREQ, 150, 260) * highpass(55.0, 2)
            # голос под началом выдоха: входит через 25 мс, тает к трети выдоха
            if v_gain > 0:
                vt = dt - 0.025
                if vt >= 0.0:
                    va = (1.0 - math.exp(-vt / 0.03)) * math.exp(-vt / (0.18 * t_ex))
                    E_voice[m] += v_gain * va * mouth(F, 1.0) * lowpass(3500) * highpass(80.0)
    # синтез: STFT шума и голоса, умножение, обратное STFT с перекрытием
    noise = rng.standard_normal(L)
    src_v = voice_source(L, rng, f0a, f0b, EXHALE_AT, t_ex * 0.5)
    win = np.hanning(N)
    out = np.zeros(L)
    norm = np.zeros(L)
    for m in range(M):
        a0 = m * HOP
        S = np.fft.rfft(noise[a0:a0 + N] * win) * E_noise[m]
        if v_gain > 0 and E_voice[m].any():
            S += np.fft.rfft(src_v[a0:a0 + N] * win) * E_voice[m] * 6.0
        y = np.fft.irfft(S, N) * win
        out[a0:a0 + N] += y
        norm[a0:a0 + N] += win ** 2
    out /= np.maximum(norm, 1e-3)
    n_out = int(total * SR)
    out = out[:n_out]
    nf = int(0.05 * SR)
    out[-nf:] *= np.cos(np.linspace(0, math.pi / 2, nf)) ** 2
    return out * (peak / np.max(np.abs(out)))


def save(name, x):
    """x: (n,) моно, -1..1. WAV в build/, OGG (Vorbis q4) в assets."""
    os.makedirs(BUILD, exist_ok=True)
    os.makedirs(SOUNDS, exist_ok=True)
    wav_path = os.path.join(BUILD, name + ".wav")
    pcm = np.round(np.clip(x, -1.0, 1.0) * 32767).astype("<i2")
    with wave.open(wav_path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    if not shutil.which("ffmpeg"):
        print("нет ffmpeg в PATH, оставил", wav_path)
        return
    out = os.path.join(SOUNDS, name + ".ogg")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path,
                    "-c:a", "libvorbis", "-q:a", "4", out], check=True)
    print("wrote %s (%.2f s)" % (out, x.shape[0] / SR))


if __name__ == "__main__":
    only = set(sys.argv[1:])
    for name, seed, inh, exh, puff, voice, peak in VARIANTS:
        if not only or name in only:
            save(name, render(seed, inh, exh, puff, voice, peak))
