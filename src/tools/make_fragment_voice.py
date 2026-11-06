"""
Голос осколка (client/FragmentVoice): реплики в погоне и в сцене у кровати (меню паузы). Только английский.
Голос — старый синтезатор речи, как в analog horror: Microsoft SAPI 4 «Sam» (через https://www.tetyys.com/SAPI4/).
Субтитры ванильные: тексты — hypersomnia.subtitle.voice.<id> в en_us.json и ru_ru.json (не здесь).
Пишет:
  src/main/resources/assets/hypersomnia/sounds/voice/en/<id>.ogg        моно 44.1 кГц, Vorbis q4
  src/main/resources/assets/hypersomnia/voice/timing.json               в тиках (20 в секунду): on/end - начало и конец
                                                                        речи, len - длина файла (сколько он «говорит»)
Сухие слои (исходники, 16 кГц FLAC) лежат в src/tools/voice_src/en/:
  <id>.flac          основной голос (Sam);
  <id>_low.flac      тот же Sam на самой низкой высоте (тёмный двойник, совпадает по времени);
  <id>_whisper.flac  Male Whisper (шёпот под голосом).
Нет основного слоя (или флаг --fetch) - скрипт берёт все три с сайта (нужен интернет). Свою запись положи вместо
<id>.flac (wav/flac/ogg/mp3, то же имя) и удали <id>_low/_whisper: скрипт сделает их из записи (сдвиг высоты и шёпот
из шума со спектром голоса).
Голоса SAPI 4 - старые движки Microsoft (проприетарные); их запись широко используется в видео, но права на
распространение в моде не оформлены. Для публикации можно заменить исходники своей записью или свободным синтезом.
Запуск из корня: python run_tool.py make_fragment_voice [--fetch] [--only id1,id2]
Нужны numpy и ffmpeg в PATH (сдвиг высоты для своей записи - фильтр rubberband, без него - asetrate).
Обработка («запись с плёнки»): основной голос, под ним тёмный двойник и шёпот; плёнка (полоса, мягкое насыщение,
плавание скорости; провалы - MIX drops, сейчас выключены ради разборчивости), перед словами нарастает обратная
реверберация, после - комната; шипение и тихий фон сети. near (вплотную, в голове): шёпот громче, комната меньше.
Зёрна фиксированы: из тех же исходников - те же файлы.
"""
import argparse
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.parse
import urllib.request
import zlib
try:
    import numpy as np
except ImportError:
    sys.exit("Нужен numpy: pip install numpy")

RES = os.path.join("src", "main", "resources", "assets", "hypersomnia")
SOUNDS = os.path.join(RES, "sounds", "voice", "en")
TIMING = os.path.join(RES, "voice", "timing.json")
SRC = os.path.join("src", "tools", "voice_src", "en")
SR = 44100
SRC_SR = 16000
TPS = 20
API = "https://www.tetyys.com/SAPI4/SAPI4?text=%s&voice=%s&pitch=%d&speed=%d"
# Слои SAPI 4: голос, высота (Sam: 50..200, обычно 100), скорость (30..450, обычно 150). Скорость у слоёв одна.
SPEED = 118
LAYERS = {
    "": ("Sam", 82),
    "_low": ("Sam", 50),
    "_whisper": ("Male Whisper", 70),
}
# Громкость слоёв и комнаты (дБ к основному голосу): far - с места фигуры, near - вплотную/в голове.
# Подобрано по разборчивости (Vosk): громче шёпот, длиннее комната и провалы плёнки - и слова уже не разобрать.
MIX = {
    "far": {"low": -17.0, "whisper": -22.0, "swell": -24.0, "room": -20.0, "rt60": 0.6, "pre": 0.5,
            "band": (140.0, 5600.0), "drive": 1.4, "wow": 0.6, "drops": 0, "hiss": -50.0, "level": -20.0},
    "near": {"low": -18.0, "whisper": -12.0, "swell": -30.0, "room": -26.0, "rt60": 0.3, "pre": 0.3,
             "band": (110.0, 7000.0), "drive": 1.2, "wow": 0.4, "drops": 0, "hiss": -54.0, "level": -22.0},
}

# id (как в VoiceLine.java), манера: far - с места фигуры, near - вплотную/в голове; текст для синтеза
LINES = [
    ("start_early", "far", "Who are you?"),
    ("start_loop", "far", "There you are."),
    ("start_accuse", "far", "It's you. It's always you."),
    ("start_rage", "far", "How can you sleep?"),
    ("run_early_0", "far", "Wait. Please."),
    ("run_early_1", "far", "Where am I?"),
    ("run_loop_0", "far", "Don't go. Not again."),
    ("run_loop_1", "far", "I was too late. Too late."),
    ("run_accuse_0", "far", "You were asleep."),
    ("run_accuse_1", "far", "You forgot her."),
    ("run_rage_0", "far", "Wake up."),
    ("run_rage_1", "far", "This isn't a dream."),
    ("escape_0", "far", "Don't leave me here."),
    ("escape_1", "far", "You always leave."),
    ("blocked_0", "far", "Let me in."),
    ("blocked_1", "far", "Open the door."),
    ("caught_0", "near", "I've got you."),
    ("caught_1", "near", "Now you're awake."),
    ("stared", "far", "You see me."),
    ("pause_0", "near", "There's no pause here."),
    ("pause_1", "near", "You may not end a session early."),
    ("pause_2", "near", "Why won't you listen? Stay. With me."),
]

FF = None
HAS_RB = None


def db(v):
    return 10.0 ** (v / 20.0)


def ffmpeg():
    global FF
    if FF is None:
        FF = shutil.which("ffmpeg")
        if not FF:
            sys.exit("Нужен ffmpeg в PATH")
    return FF


def load(path):
    raw = subprocess.run([ffmpeg(), "-v", "error", "-i", path, "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).astype(np.float64)


def run_filter(x, af):
    p = subprocess.run([ffmpeg(), "-v", "error", "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", "-",
                        "-af", af, "-f", "f32le", "-"], input=x.astype(np.float32).tobytes(), capture_output=True)
    if p.returncode != 0:
        return None
    return np.frombuffer(p.stdout, np.float32).astype(np.float64)


def shift(x, pitch, tempo, formant="preserved"):
    """Высота pitch (множитель частоты) и скорость tempo (>1 быстрее)."""
    global HAS_RB
    if HAS_RB is None:
        out = subprocess.run([ffmpeg(), "-hide_banner", "-filters"], capture_output=True, text=True).stdout
        HAS_RB = " rubberband " in out
        if not HAS_RB:
            print("  (ffmpeg без rubberband: высота через asetrate)")
    if HAS_RB:
        y = run_filter(x, "rubberband=pitch=%.5f:tempo=%.5f:formant=%s:transients=smooth:detector=soft:pitchq=quality"
                       % (pitch, tempo, formant))
        if y is not None:
            return y
    return run_filter(x, "asetrate=%d,aresample=%d,atempo=%.5f" % (int(SR * pitch), SR, tempo / pitch))


def fft_filter(x, lo=None, hi=None, order=2):
    """Полоса без фазовых сдвигов (офлайн): мягкие склоны как у Баттерворта."""
    n = len(x)
    nfft = 1 << max(1, (n - 1).bit_length())
    spec = np.fft.rfft(x, nfft)
    f = np.fft.rfftfreq(nfft, 1.0 / SR)
    h = np.ones_like(f)
    if hi:
        h /= np.sqrt(1.0 + (f / hi) ** (2 * order))
    if lo:
        h /= np.sqrt(1.0 + (lo / np.maximum(f, 1e-3)) ** (2 * order))
    return np.fft.irfft(spec * h, nfft)[:n]


def conv(x, h):
    n = len(x) + len(h) - 1
    nfft = 1 << (n - 1).bit_length()
    return np.fft.irfft(np.fft.rfft(x, nfft) * np.fft.rfft(h, nfft), nfft)[:n]


def impulse(rt60, seed, lp):
    """Синтетический отклик комнаты: затухающий шум + ранние отражения, энергия 1."""
    rng = np.random.default_rng(seed)
    n = int(SR * rt60 * 1.15)
    t = np.arange(n) / SR
    h = rng.standard_normal(n) * np.exp(-6.91 * t / rt60)
    h = fft_filter(h, lo=160.0, hi=lp, order=1)
    for d, g in ((0.011, 0.5), (0.019, 0.36), (0.029, 0.3), (0.043, 0.22)):
        h[int(d * SR)] += g * (1.0 if rng.random() < 0.5 else -1.0) * np.max(np.abs(h))
    h[:int(0.008 * SR)] = 0.0
    return h / np.sqrt(np.sum(h * h))


def whisper(x, seed):
    """Тот же спектр, случайная фаза: остаётся артикуляция без тона голоса."""
    rng = np.random.default_rng(seed)
    n, hop = 1024, 256
    win = np.hanning(n)
    pad = np.concatenate([np.zeros(n), x, np.zeros(n)])
    out = np.zeros(len(pad))
    norm = np.zeros(len(pad))
    for i in range(0, len(pad) - n, hop):
        fr = np.fft.rfft(pad[i:i + n] * win)
        ph = rng.uniform(-np.pi, np.pi, len(fr))
        out[i:i + n] += np.fft.irfft(np.abs(fr) * np.exp(1j * ph), n) * win
        norm[i:i + n] += win * win
    out /= np.maximum(norm, 1e-6)
    return out[n:n + len(x)]


def wow(x, seed, depth_ms=1.4, rate=0.55, flutter_ms=0.22, frate=6.3):
    """Плавание скорости ленты: переменная задержка."""
    rng = np.random.default_rng(seed)
    t = np.arange(len(x)) / SR
    p1, p2 = rng.uniform(0.0, 2.0 * np.pi, 2)
    d = depth_ms / 1000.0 * (1.0 + np.sin(2 * np.pi * rate * t + p1)) / 2.0 \
        + flutter_ms / 1000.0 * (1.0 + np.sin(2 * np.pi * frate * t + p2)) / 2.0
    return np.interp(t - d, t, x, left=0.0, right=0.0)


def delay(x, ms):
    return np.concatenate([np.zeros(int(ms / 1000.0 * SR)), x])


def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[:len(p)] += p
    return out


def envelope(x, frame=0.01):
    k = max(1, int(frame * SR))
    m = len(x) // k
    if m == 0:
        return np.zeros(1), k
    e = np.sqrt(np.mean(x[:m * k].reshape(m, k) ** 2, axis=1))
    return e, k


def trim(x, sr=SR, rel_db=-45.0, margin=0.03):
    k = max(1, int(0.01 * sr))
    m = len(x) // k
    if m == 0:
        return x
    e = np.sqrt(np.mean(x[:m * k].reshape(m, k) ** 2, axis=1))
    idx = np.nonzero(e > np.max(e) * db(rel_db))[0]
    if len(idx) == 0:
        return x
    a = max(0, idx[0] * k - int(margin * sr))
    b = min(len(x), (idx[-1] + 1) * k + int(margin * sr))
    return x[a:b]


def segments(x, rel_db=-30.0, min_gap=0.22, min_len=0.08):
    """Куски речи между паузами (секунды): начало и конец речи для timing.json и громкость по речи."""
    e, k = envelope(x, 0.02)
    ref = np.percentile(e, 95) + 1e-9
    on = e > ref * db(rel_db)
    segs = []
    start = None
    for i, v in enumerate(on):
        if v and start is None:
            start = i
        elif not v and start is not None:
            segs.append([start, i])
            start = None
    if start is not None:
        segs.append([start, len(on)])
    merged = []
    for s in segs:
        if merged and (s[0] - merged[-1][1]) * k / SR < min_gap:
            merged[-1][1] = s[1]
        else:
            merged.append(s)
    return [[a * k / SR, b * k / SR] for a, b in merged if (b - a) * k / SR >= min_len]


def fetch(text, voice, pitch, speed, path):
    """Сухой слой с сайта SAPI 4 (WAV) -> 16 кГц FLAC в voice_src."""
    url = API % (urllib.parse.quote(text), urllib.parse.quote(voice), pitch, speed)
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (hypersomnia voice tool)"})
            data = urllib.request.urlopen(req, timeout=40).read()
            if data[:4] != b"RIFF":
                raise IOError("не WAV: " + data[:60].decode("latin-1"))
            break
        except Exception as ex:
            if attempt == 3:
                sys.exit("Сайт SAPI 4 не ответил (%s): %s" % (voice, ex))
            time.sleep(2.0 * (attempt + 1))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    subprocess.run([ffmpeg(), "-v", "error", "-y", "-i", "-", "-ac", "1", "-ar", str(SRC_SR), "-c:a", "flac",
                    "-sample_fmt", "s16", path], input=data, check=True)
    time.sleep(0.4)


def find_src(lid, layer):
    for ext in (".flac", ".wav", ".ogg", ".mp3"):
        p = os.path.join(SRC, lid + layer + ext)
        if os.path.exists(p):
            return p
    return None


def align(ref, x, max_s=0.35):
    """Сдвинуть слой x так, чтобы его речь совпала с ref (по огибающей, шаг 5 мс)."""
    er, k = envelope(ref, 0.005)
    ex, _ = envelope(x, 0.005)
    n = max(len(er), len(ex))
    er = np.pad(er, (0, n - len(er)))
    ex = np.pad(ex, (0, n - len(ex)))
    m = int(max_s * SR / k)
    best, lag = -1.0, 0
    for L in range(-m, m + 1):
        c = np.dot(er[max(0, L):n + min(0, L)], ex[max(0, -L):n - max(0, L)])
        if c > best:
            best, lag = c, L
    s = lag * k
    y = np.zeros(len(ref))
    if s >= 0:
        seg = x[:max(0, len(ref) - s)]
        y[s:s + len(seg)] = seg
    else:
        seg = x[-s:-s + len(ref)]
        y[:len(seg)] = seg
    return y


def dropouts(x, seed, count):
    """Редкие провалы плёнки: на 40-90 мс громкость падает на 6-10 дБ (только внутри речи)."""
    rng = np.random.default_rng(seed)
    segs = segments(x)
    if not segs or count <= 0:
        return x
    y = x.copy()
    for _ in range(count):
        a, b = segs[rng.integers(len(segs))]
        if b - a < 0.3:
            continue
        t0 = rng.uniform(a + 0.1, b - 0.15)
        ln = rng.uniform(0.04, 0.09)
        depth = db(-rng.uniform(6.0, 10.0))
        i0, i1 = int(t0 * SR), int((t0 + ln) * SR)
        ramp = int(0.008 * SR)
        g = np.ones(i1 - i0) * depth
        g[:ramp] = np.linspace(1.0, depth, ramp)
        g[-ramp:] = np.linspace(depth, 1.0, ramp)
        y[i0:i1] *= g
    return y


def process(main, low, wh, style, seed):
    m = MIX[style]
    near = style == "near"
    peak = np.max(np.abs(main)) + 1e-9
    main = main / peak * 0.7
    low = align(main, low)
    low = low / (np.max(np.abs(low)) + 1e-9) * 0.7
    wh = align(main, wh)
    wh = wh / (np.max(np.abs(wh)) + 1e-9) * 0.7
    voice = mix(main,
                fft_filter(low, hi=1800.0) * db(m["low"]),
                fft_filter(wh, lo=900.0) * db(m["whisper"]))
    lo, hi = m["band"]
    voice = fft_filter(voice, lo=lo, hi=hi)
    pk = np.max(np.abs(voice)) + 1e-9
    voice = np.tanh(m["drive"] * voice / pk) / np.tanh(m["drive"]) * pk
    voice = wow(voice, seed + 1, depth_ms=1.0 * m["wow"], flutter_ms=0.18 * m["wow"])
    voice = dropouts(voice, seed + 5, m["drops"])
    segs = segments(main)

    swell = conv(voice[::-1], impulse(2.0, seed + 2, 3000.0))[::-1]
    pre = len(swell) - len(voice)
    room = conv(voice, impulse(m["rt60"], seed + 3, 4800.0))
    out = np.zeros(pre + len(room))
    out[:len(swell)] += swell * db(m["swell"])
    out[pre:pre + len(room)] += room * db(m["room"])
    out[pre:pre + len(voice)] += voice
    cut = max(0, pre - int(m["pre"] * SR))
    out = out[cut:]
    offset = (pre - cut) / SR

    # Хвост до -60 дБ, мягкие вход и выход
    e, k = envelope(out, 0.01)
    alive = np.nonzero(e > np.max(e) * db(-60.0))[0]
    if len(alive):
        out = out[:min(len(out), (alive[-1] + 1) * k)]
    fin = min(len(out), int(0.15 * SR))
    out[:fin] *= np.linspace(0.0, 1.0, fin)
    fout = min(len(out), int(0.15 * SR))
    out[-fout:] *= np.linspace(1.0, 0.0, fout)

    # Громкость по речи, пик не выше -2 дБ
    sp = np.concatenate([out[int((a + offset) * SR):int((b + offset) * SR)] for a, b in segs]) if segs else out
    rms = np.sqrt(np.mean(sp * sp)) + 1e-9
    out *= db(m["level"]) / rms
    lim = db(-2.0)
    if np.max(np.abs(out)) > lim * 0.8:
        out = np.where(np.abs(out) > lim * 0.8,
                       np.sign(out) * (lim * 0.8 + lim * 0.2 * np.tanh((np.abs(out) - lim * 0.8) / (lim * 0.2))), out)

    # Шипение плёнки и тихий фон сети 50 Гц (с гармониками)
    rng = np.random.default_rng(seed + 4)
    hiss = fft_filter(rng.standard_normal(len(out)), lo=900.0, hi=9000.0, order=1)
    hiss *= db(m["hiss"]) / (np.sqrt(np.mean(hiss * hiss)) + 1e-9)
    tt = np.arange(len(out)) / SR
    hum = (np.sin(2 * np.pi * 50 * tt) + 0.5 * np.sin(2 * np.pi * 100 * tt) + 0.25 * np.sin(2 * np.pi * 150 * tt))
    hum *= db(m["hiss"] - 8.0) / (np.sqrt(np.mean(hum * hum)) + 1e-9)
    bed = hiss + hum
    ramp = min(len(out), int(0.08 * SR))
    bed[:ramp] *= np.linspace(0, 1, ramp)
    bed[-ramp:] *= np.linspace(1, 0, ramp)
    out += bed
    t = {
        "on": int(round((segs[0][0] + offset) * TPS)) if segs else 0,
        "end": int(round((segs[-1][1] + offset) * TPS)) if segs else int(len(out) / SR * TPS),
        "len": int(np.ceil(len(out) / SR * TPS)),
    }
    return out, t


def save_ogg(x, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    subprocess.run([ffmpeg(), "-v", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", "-",
                    "-c:a", "libvorbis", "-q:a", "4", "-map_metadata", "-1", "-fflags", "+bitexact",
                    "-flags:a", "+bitexact", path], input=x.astype(np.float32).tobytes(), check=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fetch", action="store_true", help="заново взять слои с сайта SAPI 4")
    ap.add_argument("--only", default="", help="id через запятую")
    a = ap.parse_args()
    only = {s.strip() for s in a.only.split(",") if s.strip()}
    timing = {}
    if os.path.exists(TIMING):
        with open(TIMING, encoding="utf-8") as f:
            timing = json.load(f).get("en", {})
    for lid, style, text in LINES:
        if only and lid not in only:
            continue
        # Нет основного слоя (или --fetch) - все три слоя с сайта; иначе недостающие слои делаются из основного
        refetch = a.fetch or find_src(lid, "") is None
        layers = {}
        for layer, (voice, pitch) in LAYERS.items():
            src = find_src(lid, layer)
            if refetch:
                src = os.path.join(SRC, lid + layer + ".flac")
                fetch(text, voice, pitch, SPEED, src)
            layers[layer] = load(src) if src else None
        seed = zlib.crc32(("en:" + lid).encode("utf-8")) & 0xFFFF
        main_v = trim(layers[""])
        low = layers["_low"] if layers["_low"] is not None else shift(main_v, 0.75, 1.0, "shifted")
        wh = layers["_whisper"] if layers["_whisper"] is not None else whisper(main_v, seed)
        out, t = process(main_v, trim(low), trim(wh), style, seed)
        save_ogg(out, os.path.join(SOUNDS, lid + ".ogg"))
        timing[lid] = t
        print("%-13s %.2f c, речь %d-%d тиков" % (lid, len(out) / SR, t["on"], t["end"]))
    os.makedirs(os.path.dirname(TIMING), exist_ok=True)
    d = {lid: timing[lid] for lid, _, _ in LINES if lid in timing}
    with open(TIMING, "w", encoding="utf-8", newline="\n") as f:
        f.write('{\n  "en": {\n')
        items = list(d.items())
        for i, (lid, t) in enumerate(items):
            f.write('    "%s": %s%s\n' % (lid, json.dumps(t, ensure_ascii=False), "," if i < len(items) - 1 else ""))
        f.write("  }\n}\n")
    print("Готово:", TIMING)


if __name__ == "__main__":
    main()
