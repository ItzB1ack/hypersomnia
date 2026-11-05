"""
Звуки погони (client/ChaseMusic): нагнетающая музыка и шёпот осколка.
Пишет assets/hypersomnia/sounds/:
  chase_drone.ogg            петля 24 с, стерео: низкий гул ре и диссонансный кластер ре - ми-бемоль - соль-диез,
                             медленно «дышит»; далёкие стоны, шорох, звон в ушах. Звучит всю погоню.
  chase_rise.ogg             петля 12 с, стерео: тон, который всё время поднимается и никогда не кончается
                             (глиссандо Шепарда - Риссе), такой же ползущий вверх шум, дрожь 6 Гц. Громче, чем ближе он.
  chase_near.ogg             петля 8 с, стерео: бормотание пяти голосов вокруг головы и треск. Когда он в нескольких блоках.
  shard_whisper.ogg, _2.._6  моно, 1.4-2.8 с: бессвязный шёпот осколка (звучит с места фигуры): со вдохом,
                             выше и быстрее, задом наперёд, два голоса разом, медленный и низкий, рваный (заикается).
Запуск из корня: python run_tool.py make_chase_sounds (или двойной клик tools.bat).
Нужны numpy (pip install numpy) и ffmpeg в PATH (без ffmpeg WAV остаются в build/, конвертни вручную:
    ffmpeg -y -i build/chase_drone.wav -c:a libvorbis -q:a 4 src/main/resources/assets/hypersomnia/sounds/chase_drone.ogg).

Шёпот - не запись: белый шум, у которого каждый кадр (23 мс) умножен на спектр звука речи: форманты гласных,
полосы шипящих «с/ш/ф», щелчки «т/к/п», придыхание «х», носовые, дрожащее «р». Слоги случайные - слов нет, а ухо
слышит речь. Петли бесшовные: частоты кратны 1/длина петли, шум собран из спектра, реверберация идёт по кругу
(хвост с конца петли звучит в её начале). Зёрна фиксированы: повторный запуск даёт те же файлы.
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
TAU = 2.0 * math.pi

# ------------------------------------------------------------------
# Шёпот: синтез речи из шума (STFT)
# ------------------------------------------------------------------
SR = 44100
N = 1024
HOP = 256
FREQ = np.fft.rfftfreq(N, 1.0 / SR)

# Форманты шёпота (Гц): F1..F4; у шёпота F1 чуть выше, чем у голоса
VOWELS = {
    'a': (780, 1280, 2550, 3500), 'o': (590, 930, 2450, 3400), 'u': (370, 840, 2300, 3300),
    'e': (580, 1850, 2550, 3500), 'i': (340, 2250, 3000, 3700), 'y': (450, 1550, 2450, 3400),
}
BW = np.array([190.0, 220.0, 300.0, 380.0])
AMP = np.array([1.0, 0.75, 0.42, 0.22])

def lorentz(f, F, B):
    return 1.0 / (1.0 + ((f - F) / (B / 2.0)) ** 2)

def tilt(f):
    lo = (f / 320.0) ** 2 / (1.0 + (f / 320.0) ** 2)          # без гула ниже ~300 Гц
    hi = 1.0 / (1.0 + (f / 7500.0) ** 4)                       # завал выше 7.5 кГц
    return lo * hi * np.minimum((f + 1.0) / 1000.0, 2.5) ** 0.35

TILT = tilt(FREQ)

def env_vowel(F, scale):
    """F: (M,4) форманты -> (M,bins) огибающая."""
    Fs = F * scale
    e = np.zeros((F.shape[0], FREQ.size))
    for k in range(4):
        e += AMP[k] * lorentz(FREQ[None, :], Fs[:, k:k + 1], BW[k] * scale)
    return e

def band(Fc, B, steep_lo=None):
    e = lorentz(FREQ, Fc, B)
    if steep_lo:
        e *= (FREQ / steep_lo) ** 6 / (1.0 + (FREQ / steep_lo) ** 6)
    return e

FRIC = {
    's': band(6300, 3200, 3600),
    'sh': band(2900, 2300, 1700) + 0.45 * band(5200, 2200),
    'f': 0.35 * band(4500, 9000, 1200),
    'burst_t': band(4600, 4200, 1500),
    'burst_k': band(2100, 1600, 800),
    'burst_p': band(1000, 1800, 300),
}

CONSONANTS = ['s', 's', 'sh', 'sh', 't', 'k', 'p', 'h', 'h', 'f', 'n', 'm', 'r', 'ch', 'shch']
VOWEL_KEYS = ['a', 'o', 'u', 'e', 'i', 'y', 'a', 'o', 'e']

def phrase(rng, n_syll, rate=1.0):
    """Случайная бессвязная фраза: список фонем (вид, длительность с, гласная/параметры, громкость)."""
    seq = [('pause', 0.04, None, 0.0)]
    for i in range(n_syll):
        emph = rng.uniform(0.55, 1.0) * (1.0 - 0.35 * i / max(1, n_syll - 1))
        v = rng.choice(VOWEL_KEYS)
        if rng.random() < 0.85:
            c = rng.choice(CONSONANTS)
            if c in ('s', 'sh', 'shch'):
                seq.append((c, rng.uniform(0.07, 0.15) * (1.4 if c == 'shch' else 1.0) / rate, v, emph * rng.uniform(0.7, 1.0)))
            elif c in ('t', 'k', 'p'):
                seq.append(('closure', rng.uniform(0.03, 0.05) / rate, v, 0.0))
                seq.append(('burst_' + c, 0.012, v, emph))
                seq.append(('h', 0.025 / rate, v, emph * 0.6))
            elif c == 'ch':
                seq.append(('closure', 0.035 / rate, v, 0.0))
                seq.append(('burst_t', 0.010, v, emph))
                seq.append(('sh', rng.uniform(0.05, 0.08) / rate, v, emph * 0.9))
            elif c == 'h':
                seq.append(('h', rng.uniform(0.05, 0.09) / rate, v, emph * 0.7))
            elif c == 'f':
                seq.append(('f', rng.uniform(0.05, 0.09) / rate, v, emph * 0.8))
            elif c in ('n', 'm'):
                seq.append(('nasal', rng.uniform(0.04, 0.07) / rate, v, emph * 0.35))
            elif c == 'r':
                seq.append(('r', rng.uniform(0.05, 0.08) / rate, v, emph * 0.55))
        seq.append(('V', rng.uniform(0.07, 0.15) / rate, v, emph))
        if rng.random() < 0.18:
            seq.append(('s', rng.uniform(0.06, 0.12) / rate, v, emph * 0.8))
        if rng.random() < 0.12 and i < n_syll - 1:
            seq.append(('pause', rng.uniform(0.06, 0.16), v, 0.0))
    seq.append(('pause', 0.05, None, 0.0))
    return seq

def smooth(a, k):
    if k <= 1:
        return a
    ker = np.ones(k) / k
    pad = np.pad(a, ((k // 2, k - 1 - k // 2),) + ((0, 0),) * (a.ndim - 1), mode='edge')
    if a.ndim == 1:
        return np.convolve(pad, ker, mode='valid')
    return np.stack([np.convolve(pad[:, j], ker, mode='valid') for j in range(a.shape[1])], axis=1)

def render(seq, rng, scale=1.0, inhale=False):
    """Фонемы -> сигнал: белый шум, каждый кадр STFT умножен на огибающую (форманты, полосы шипящих, взрывы)."""
    if inhale:
        seq = [('inhale', 0.28, 'a', 0.3)] + seq
    total = sum(d for _, d, _, _ in seq) + 0.1
    M = int(total * SR / HOP) + 4
    F = np.zeros((M, 4))
    gains = {k: np.zeros(M) for k in ('V', 'h', 's', 'sh', 'f', 'burst_t', 'burst_k', 'burst_p', 'nasal', 'r', 'inhale')}
    t = 0.0
    lastF = np.array(VOWELS['a'], dtype=float)
    jitter = {}
    for kind, dur, v, g in seq:
        m0, m1 = int(t * SR / HOP), max(int(t * SR / HOP) + 1, int((t + dur) * SR / HOP))
        if v is not None:
            if (v, m0) not in jitter:
                jitter[(v, m0)] = np.array(VOWELS[v]) * rng.uniform(0.95, 1.05, 4)
            target = jitter[(v, m0)]
        else:
            target = lastF
        F[m0:m1] = target
        lastF = target
        if kind in gains:
            gains[kind][m0:m1] = g
        t += dur
    F[int(t * SR / HOP):] = lastF
    F = smooth(F, 7)                       # коартикуляция ~40 мс
    for k in gains:
        gains[k] = smooth(gains[k], 2 if k.startswith('burst') else 4)
    # дрожь потока воздуха (чтобы не было «синтетической ровности»)
    flutter = 1.0 + 0.18 * smooth(rng.standard_normal(M), 9)
    ev = env_vowel(F, scale)
    E = ev * (gains['V'] * flutter)[:, None]
    E += env_vowel(F, scale * 1.08) * 0.55 * (gains['h'])[:, None] * 1.4   # придыхание: те же форманты, шире
    E += 0.7 * FRIC['s'][None, :] * gains['s'][:, None]
    E += 0.65 * FRIC['sh'][None, :] * gains['sh'][:, None]
    E += 0.5 * FRIC['f'][None, :] * gains['f'][:, None]
    for b in ('burst_t', 'burst_k', 'burst_p'):
        E += 1.1 * FRIC[b][None, :] * gains[b][:, None]
    nasal = 0.6 * lorentz(FREQ, 280 * scale, 300) + 0.25 * lorentz(FREQ, 2200 * scale, 700)
    E += nasal[None, :] * gains['nasal'][:, None]
    trill = 0.5 + 0.5 * np.sin(2 * np.pi * 26.0 * np.arange(M) * HOP / SR)
    E += env_vowel(np.tile([[520, 1350, 1750, 3300]], (M, 1)).astype(float), scale) * (gains['r'] * trill)[:, None]
    inh = env_vowel(np.tile([[900, 1500, 2600, 3600]], (M, 1)).astype(float), scale * 1.1)
    ramp = gains['inhale'] * np.clip(np.cumsum(gains['inhale'] > 0) / (0.28 * SR / HOP), 0, 1) ** 1.5
    E += 0.5 * inh * ramp[:, None]
    E *= TILT[None, :]
    # синтез: STFT белого шума, умножение, обратное STFT с перекрытием
    L = (M - 1) * HOP + N
    noise = rng.standard_normal(L)
    win = np.hanning(N)
    out = np.zeros(L)
    norm = np.zeros(L)
    for m in range(M):
        a = m * HOP
        seg = noise[a:a + N] * win
        S = np.fft.rfft(seg) * E[m]
        y = np.fft.irfft(S, N) * win
        out[a:a + N] += y
        norm[a:a + N] += win ** 2
    out /= np.maximum(norm, 1e-3)
    return out

def reverb_ir(rng, rt60, length, predelay=0.012, early=6):
    n = int(length * SR)
    t = np.arange(n) / SR
    ir = rng.standard_normal(n) * np.exp(-6.91 * t / rt60)
    ir[: int(predelay * SR)] = 0
    # ранние отражения
    for _ in range(early):
        k = int(rng.uniform(0.004, 0.035) * SR)
        ir[k] += rng.uniform(0.3, 0.8) * rng.choice([-1, 1])
    # глуше со временем: ФНЧ через свёртку с коротким окном
    ir = np.convolve(ir, np.hanning(9) / np.hanning(9).sum(), mode='same')
    return ir / np.sqrt(np.sum(ir ** 2))

def convolve(x, ir):
    n = x.size + ir.size - 1
    nf = 1 << (n - 1).bit_length()
    return np.fft.irfft(np.fft.rfft(x, nf) * np.fft.rfft(ir, nf), nf)[:n]

def add_room(x, rng, rt60=0.45, wet=0.22):
    ir = reverb_ir(rng, rt60, rt60 * 1.2)
    w = convolve(x, ir)
    y = np.zeros(w.size)
    y[: x.size] += x
    return y * (1 - wet) + w * wet * (np.max(np.abs(x)) / max(1e-9, np.max(np.abs(w))))

def fade(x, a=0.01, b=0.08):
    na, nb = int(a * SR), int(b * SR)
    x = x.copy()
    if na:
        x[:na] *= np.linspace(0, 1, na)
    if nb:
        x[-nb:] *= np.cos(np.linspace(0, np.pi / 2, nb)) ** 2
    return x

# ------------------------------------------------------------------
# Общее: запись, громкость, панорама, петли по кругу
# ------------------------------------------------------------------
def save(name, x):
    """x: (n,) моно или (n, 2) стерео, -1..1. WAV в build/, OGG (Vorbis q4) в assets."""
    os.makedirs(BUILD, exist_ok=True)
    os.makedirs(SOUNDS, exist_ok=True)
    wav_path = os.path.join(BUILD, name + ".wav")
    pcm = np.round(np.clip(x, -1.0, 1.0) * 32767).astype("<i2")
    with wave.open(wav_path, "wb") as w:
        w.setnchannels(1 if x.ndim == 1 else x.shape[1])
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


def level(x, rms_db, peak=0.9):
    """Громкость по среднеквадратичному (дБ полной шкалы), но пик не выше peak."""
    rms = math.sqrt(float(np.mean(x ** 2))) or 1.0
    g = 10.0 ** (rms_db / 20.0) / rms
    top = float(np.max(np.abs(x))) * g
    if top > peak:
        g *= peak / top
    return x * g


def pan(p):
    """Панорама -1 (лево) .. 1 (право) с постоянной мощностью: (левый, правый)."""
    a = (p + 1.0) * math.pi / 4.0
    return math.cos(a), math.sin(a)


def put(mix, x, at, p, g=1.0):
    """Кладёт моно x в стерео-петлю mix с отсчёта at; что не влезло в конец, звучит в начале."""
    n = mix.shape[0]
    idx = (at + np.arange(x.size)) % n
    gl, gr = pan(p)
    np.add.at(mix[:, 0], idx, x * g * gl)
    np.add.at(mix[:, 1], idx, x * g * gr)


def loop_noise(n, rng, shape):
    """Шум длиной ровно в петлю: случайный спектр (амплитуда shape(f)) -> обратное FFT, конец сходится с началом."""
    f = np.fft.rfftfreq(n, 1.0 / SR)
    spec = (rng.standard_normal(f.size) + 1j * rng.standard_normal(f.size)) * shape(f)
    spec[0] = 0.0
    x = np.fft.irfft(spec, n)
    return x / (math.sqrt(float(np.mean(x ** 2))) + 1e-12)


def loop_filter(x, shape):
    """Фильтр по кругу: спектр петли умножается на shape(f)."""
    f = np.fft.rfftfreq(x.shape[0], 1.0 / SR)
    return np.fft.irfft(np.fft.rfft(x, axis=0) * (shape(f)[:, None] if x.ndim == 2 else shape(f)), x.shape[0], axis=0)


def loop_reverb(x, rng, rt60, wet, dark=4000.0):
    """Реверберация по кругу (свёртка через FFT длиной в петлю): хвост конца петли звучит в начале - шва нет."""
    n = x.size
    ln = min(n - 1, int(rt60 * 1.4 * SR))
    t = np.arange(ln) / SR
    ir = rng.standard_normal(ln) * np.exp(-6.91 * t / rt60)
    ir[: int(0.015 * SR)] = 0.0
    for _ in range(8):                                    # ранние отражения
        ir[int(rng.uniform(0.005, 0.045) * SR)] += rng.uniform(0.5, 1.2) * rng.choice([-1.0, 1.0])
    f = np.fft.rfftfreq(n, 1.0 / SR)
    h = np.fft.rfft(ir, n) / (1.0 + (f / dark) ** 2)
    w = np.fft.irfft(np.fft.rfft(x) * h, n)
    w *= math.sqrt(float(np.mean(x ** 2))) / (math.sqrt(float(np.mean(w ** 2))) + 1e-12)
    return x * (1.0 - wet) + w * wet


def lowpass(fc, order=2):
    return lambda f: 1.0 / np.sqrt(1.0 + (f / fc) ** (2 * order))


def highpass(fc, order=2):
    return lambda f: 1.0 / np.sqrt(1.0 + (fc / np.maximum(f, 1e-3)) ** (2 * order))


def bump(u, m, phase, power=1.0):
    """Плавная волна 0..1 с m подъёмами за петлю (u - доля петли 0..1)."""
    return (0.5 - 0.5 * np.cos(TAU * (m * u + phase))) ** power


# ------------------------------------------------------------------
# Шёпот осколка: шесть вариантов
# ------------------------------------------------------------------
def stutter(x, rng):
    """Рваный шёпот: куски 50-120 мс; часть повторяется 2-3 раза, часть задом наперёд, часть выпадает."""
    out = []
    i = 0
    ramp = int(0.004 * SR)
    while i < x.size:
        g = int(rng.uniform(0.05, 0.12) * SR)
        seg = x[i:i + g].copy()
        i += g
        if seg.size <= 2 * ramp:
            break
        seg[:ramp] *= np.linspace(0.0, 1.0, ramp)
        seg[-ramp:] *= np.linspace(1.0, 0.0, ramp)
        r = rng.random()
        if r < 0.10:
            out.append(np.zeros(int(rng.uniform(0.03, 0.08) * SR)))   # выпал кусок
            continue
        reps = int(rng.integers(2, 4)) if r < 0.34 else 1
        for _ in range(reps):
            out.append(seg[::-1] if rng.random() < 0.18 else seg)
    return np.concatenate(out)


def trim(x, db=-60.0):
    """Срезает почти тишину по краям (хвост реверберации ниже db от пика)."""
    keep = np.nonzero(np.abs(x) > np.max(np.abs(x)) * 10.0 ** (db / 20.0))[0]
    return x[keep[0]:keep[-1] + 1] if keep.size else x


def whisper(name, seed, syll, scale, rate, kind):
    rng = np.random.default_rng(seed)
    seq = phrase(rng, syll, rate)
    if kind == "slow":
        seq = seq[:-1] + [("s", 0.42, "a", 0.3), ("pause", 0.05, None, 0.0)]    # тянет «ссс» в конце
    x = render(seq, rng, scale, inhale=(kind == "inhale"))
    if kind == "two":
        y = render(phrase(rng, syll, rate * 0.92), rng, scale * 1.16)
        off = int(0.13 * SR)
        z = np.zeros(max(x.size, y.size + off))
        z[:x.size] += x
        z[off:off + y.size] += 0.8 * y
        x = z
    if kind == "stutter":
        x = stutter(x, rng)
    x = add_room(x, rng, rt60=0.55, wet=0.26)
    if kind == "reverse":
        x = x[::-1].copy()                  # эхо идёт впереди слов и нарастает к ним
    return level(fade(trim(x), 0.012, 0.12), -20.0, 0.9)


WHISPERS = [
    # имя, зерно, слогов, сдвиг формант (1 - взрослый, выше - моложе), темп, вид
    ("shard_whisper", 11, 9, 1.00, 1.00, "inhale"),
    ("shard_whisper_2", 23, 9, 1.10, 1.22, "plain"),
    ("shard_whisper_3", 37, 8, 0.96, 1.00, "reverse"),
    ("shard_whisper_4", 41, 6, 0.93, 1.05, "two"),
    ("shard_whisper_5", 53, 4, 0.88, 0.70, "slow"),
    ("shard_whisper_6", 67, 9, 1.04, 1.10, "stutter"),
]


# ------------------------------------------------------------------
# chase_drone: гул всей погони (24 с)
# ------------------------------------------------------------------
def drone():
    T = 24.0
    n = int(round(T * SR))
    idx = np.arange(n, dtype=np.int64)
    u = idx / n
    rng = np.random.default_rng(7)
    mix = np.zeros((n, 2))

    def sine(k, h=1, phase=0.0):
        # частота h*k/T: ровно h*k периодов за петлю
        return np.sin(TAU * ((h * k * idx) % n) / n + phase)

    # суб: ре D1 (881/24 = 36.7 Гц) + октава, медленно «дышит»
    sub = sine(881) + 0.3 * sine(881, 2, 0.7)
    sub *= 0.7 + 0.3 * bump(u, 1, 0.1)
    put(mix, 0.55 * sub, 0, 0.0)

    # кластер: k/24 Гц - ре D2, ми-бемоль Eb2, соль-диез G#2 (тритон), ре D3, до-диез C#3; громкость, панорама
    notes = [(1762, 1.0, -0.15), (1867, 0.55, 0.35), (2492, 0.45, -0.4), (3524, 0.22, 0.2), (3326, 0.16, 0.5)]
    for k, amp, p in notes:
        env = 0.45 + 0.55 * bump(u, int(rng.integers(1, 4)), rng.uniform(0, 1), 1.5)
        fc = 260.0 * 2.0 ** (1.4 * bump(u, 1, rng.uniform(0, 1)))       # «фильтр» медленно открывается и закрывается
        for dk in (-2, 2):                                              # два голоса: биения 4 раза за петлю
            kk = k + dk
            voice = np.zeros(n)
            for h in range(1, 25):
                f = h * kk / T
                if f > 3000:
                    break
                voice += sine(kk, h, rng.uniform(0, TAU)) * (h ** -1.4) / np.sqrt(1.0 + (f / fc) ** 4)
            put(mix, 0.5 * amp * env * voice, 0, p + 0.25 * dk / 2)

    # далёкие стоны: тон 98-125 Гц сползает на 2-4 полутона, гласная «у -> о» (форманты по гармоникам)
    for j in range(3):
        dur = rng.uniform(3.5, 5.0)
        m = int(dur * SR)
        tt = np.arange(m) / SR
        x = tt / dur
        f0 = rng.uniform(98.0, 125.0) * 2.0 ** (-rng.uniform(2.0, 4.0) / 12.0 * x)
        f0 = f0 * (1.0 + 0.006 * np.sin(TAU * 4.8 * tt))
        ph = TAU * np.cumsum(f0) / SR
        F1 = 330.0 + 190.0 * x
        F2 = 800.0 + 150.0 * x
        s = np.zeros(m)
        for h in range(1, 30):
            fh = h * f0
            wgt = 1.0 / (1.0 + ((fh - F1) / 70.0) ** 2) + 0.5 / (1.0 + ((fh - F2) / 90.0) ** 2) + 0.08 / (1.0 + ((fh - 2500.0) / 200.0) ** 2)
            s += np.sin(h * ph) * wgt * h ** -0.5
        s *= bump(x, 1, 0.0, 1.3)
        at = int((j + rng.uniform(0.1, 0.6)) / 3.0 * n)
        put(mix, 0.16 * s / np.max(np.abs(s)), at, rng.uniform(-0.7, 0.7))

    # шорох: шум 120-900 Гц, три волны за петлю, слева и справа свой
    def wind(f):
        lf = np.log2(np.maximum(f, 1.0) / 320.0)
        return np.exp(-0.5 * (lf / 1.0) ** 2) / np.sqrt(np.maximum(f, 20.0) / 100.0)
    for ch in (0, 1):
        w = loop_noise(n, rng, wind) * (0.35 + 0.65 * bump(u, 3, rng.uniform(0, 1), 1.5))
        mix[:, ch] += 0.12 * w

    # звон в ушах: два близких тона 2349 Гц (биения 0.5 Гц), один раз за петлю проступает и уходит
    ring = (sine(56384) + sine(56396, 1, 1.3)) * bump(u, 1, 0.62, 3.0)
    put(mix, 0.010 * ring, 0, 0.1)

    for ch in (0, 1):
        mix[:, ch] = loop_reverb(mix[:, ch], rng, rt60=3.2, wet=0.35, dark=2500.0)
    mix = loop_filter(mix, lambda f: highpass(30.0)(f) * lowpass(7000.0)(f))
    return level(mix, -19.0, 0.9)


# ------------------------------------------------------------------
# chase_rise: бесконечный подъём (12 с)
# ------------------------------------------------------------------
def shepard(n, T, fmin, K, rng, harmonics=((1, 1.0), (3, 0.22), (5, 0.08)), power=1.6):
    """Глиссандо Шепарда - Риссе: K октавных тонов ползут вверх на октаву за петлю, громкость - окно по высоте
    (ноль на краях). Тон i в конце петли становится тоном i+1 в начале: фазы связаны цепочкой, шва нет."""
    u = np.arange(n) / n
    lift = (2.0 ** u - 1.0) / math.log(2.0)
    out = np.zeros(n)
    for h, ha in harmonics:
        ph0 = rng.uniform(0, TAU)
        for i in range(K):
            f0 = h * fmin * 2.0 ** i
            w = (0.5 - 0.5 * np.cos(TAU * (i + u) / K)) ** power
            out += ha * w * np.sin(ph0 + TAU * f0 * T * lift)
            ph0 = (ph0 + TAU * f0 * T / math.log(2.0)) % TAU
    return out


def shepard_noise(n, T, fmin, K, rng, width=0.2, nfft=1764, hop=441):
    """Шум, чьи полосы тоже ползут вверх (STFT по кругу): полоса через октаву, окно по высоте как у тона."""
    noise = rng.standard_normal(n)
    win = np.hanning(nfft + 1)[:-1]
    f = np.fft.rfftfreq(nfft, 1.0 / SR)
    lf = np.log2(np.maximum(f, 1.0) / fmin)
    out = np.zeros(n)
    norm = np.zeros(n)
    for m in range(n // hop):
        ids = (m * hop + np.arange(nfft)) % n
        u = ((m * hop + nfft / 2) / n) % 1.0
        env = np.zeros(f.size)
        for i in range(K):
            x = i + u
            env += (0.5 - 0.5 * math.cos(TAU * x / K)) ** 1.6 * np.exp(-0.5 * ((lf - x) / width) ** 2)
        y = np.fft.irfft(np.fft.rfft(noise[ids] * win) * env, nfft) * win
        np.add.at(out, ids, y)
        np.add.at(norm, ids, win ** 2)
    return out / np.maximum(norm, 1e-6)


def rise():
    T = 12.0
    n = int(round(T * SR))
    u = np.arange(n) / n
    rng = np.random.default_rng(11)
    mix = np.zeros((n, 2))
    left = shepard(n, T, 20.6, 7, np.random.default_rng(101))
    right = shepard(n, T, 20.6 * 1.0025, 7, np.random.default_rng(101))   # чуть выше справа: «плавает» в ширину
    tri = shepard(n, T, 20.6 * 2.0 ** 0.5, 7, rng)           # тот же подъём тритоном выше, тише
    trem = 1.0 - 0.28 * (0.5 + 0.5 * np.sin(TAU * 72 * u))   # дрожь 6 Гц (72 за петлю)
    mix[:, 0] = (left + 0.32 * tri) * trem
    mix[:, 1] = (right + 0.32 * tri) * trem
    mix /= math.sqrt(float(np.mean(mix ** 2)))
    for ch in (0, 1):
        sn = shepard_noise(n, T, 80.0, 7, rng)
        mix[:, ch] += 0.35 * sn / math.sqrt(float(np.mean(sn ** 2)))
    for ch in (0, 1):
        mix[:, ch] = loop_reverb(mix[:, ch], rng, rt60=2.0, wet=0.3, dark=3500.0)
    mix = loop_filter(mix, lambda f: highpass(40.0)(f) * lowpass(9000.0)(f))
    return level(mix, -20.0, 0.9)


# ------------------------------------------------------------------
# chase_near: бормотание вокруг головы (8 с)
# ------------------------------------------------------------------
def near():
    T = 8.0
    n = int(round(T * SR))
    u = np.arange(n) / n
    rng = np.random.default_rng(13)
    mix = np.zeros((n, 2))
    for p in (-0.85, -0.45, 0.0, 0.45, 0.85):
        parts = []
        for _ in range(2):
            parts.append(render(phrase(rng, int(rng.integers(9, 13)), rng.uniform(1.05, 1.3)),
                                rng, rng.uniform(0.86, 1.12)))
            parts.append(np.zeros(int(rng.uniform(0.15, 0.5) * SR)))
        x = np.concatenate(parts)
        if x.size > n - SR // 4:
            x = fade(x[: n - SR // 4], 0.0, 0.2)
        x /= math.sqrt(float(np.mean(x ** 2))) + 1e-12
        put(mix, x, int(rng.integers(0, n)), p, rng.uniform(0.6, 1.0))
    # треск: короткие щелчки (как помехи в проводе), ~9 в секунду
    count = int(rng.poisson(9 * T))
    for _ in range(count):
        m = int(rng.uniform(0.001, 0.005) * SR)
        click = rng.standard_normal(m) * np.exp(-np.arange(m) / (m / 4.0))
        click = np.diff(click, prepend=0.0)
        put(mix, click * rng.lognormal(-0.5, 0.6), int(rng.integers(0, n)), rng.uniform(-1, 1), 0.9)
    # шипение 2.5-9 кГц, три волны за петлю
    for ch in (0, 1):
        hiss = loop_noise(n, rng, lambda f: highpass(2500.0)(f) * lowpass(8000.0)(f))
        mix[:, ch] += 0.12 * hiss * (0.3 + 0.7 * bump(u, 3, rng.uniform(0, 1)))
    for ch in (0, 1):
        mix[:, ch] = loop_reverb(mix[:, ch], rng, rt60=0.7, wet=0.25, dark=5000.0)
    mix = loop_filter(mix, lambda f: highpass(120.0)(f) * lowpass(9500.0)(f))
    return level(mix, -21.0, 0.9)


if __name__ == "__main__":
    only = set(sys.argv[1:])
    for args in WHISPERS:
        if not only or args[0] in only:
            save(args[0], whisper(*args))
    for name, fn in (("chase_drone", drone), ("chase_rise", rise), ("chase_near", near)):
        if not only or name in only:
            save(name, fn())
