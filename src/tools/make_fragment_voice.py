"""
Голос осколка (client/FragmentVoice): реплики в погоне и в сцене у кровати (меню паузы).
Пишет:
  src/main/resources/assets/hypersomnia/sounds/voice/en/<id>.ogg        моно 44.1 кГц, Vorbis q4
  src/main/resources/assets/hypersomnia/voice/timing.json               тайминги субтитров в тиках (20 в секунду):
                                                                        on/end - начало и конец речи, len - длина файла,
                                                                        seg - куски речи между паузами (по фразам)
Озвучка только английская (решение пользователя): русские субтитры берутся из ru_ru.json и идут по таймингам
английской речи. Сухой голос (исходник) лежит в src/tools/voice_src/en/<id>.flac. Его можно заменить своей записью
(wav/flac/ogg/mp3, любая частота, моно или стерео) с тем же именем: скрипт обработает её так же, как синтез.
Если исходника нет (или флаг --tts), скрипт синтезирует его через Piper (pip install piper-tts):
  голос CC0 - en_US-joe-medium (https://huggingface.co/rhasspy/piper-voices),
  положить .onnx и .onnx.json в build/voice_models/ (или указать папку: --models DIR).
Запуск из корня: python run_tool.py make_fragment_voice [--tts] [--only id1,id2]
Нужны numpy и ffmpeg в PATH (смена высоты - фильтр rubberband; если ffmpeg собран без него, высота меняется
через asetrate и голос звучит «плёночнее»).

Обработка («голос из записи»): основной голос ниже (PITCH, 0.93 - на 1.2 тона) и на 6% медленнее; под ним тот же голос ниже
на 3 тона со сдвигом формант (тёмный двойник, тихо), над ним - на пятую тона выше с задержкой 27 мс (голос
раздваивается) и шёпот (тот же спектр со случайной фазой: слова без голоса). Потом лента: полоса 110-7200 Гц,
мягкое насыщение, плавание скорости (wow/flutter); перед словами нарастает обратная реверберация («вдох»
комнаты), после - короткое эхо; тихое шипение плёнки. near (в голове): меньше эха, больше шёпота.
Зёрна фиксированы: повторный запуск даёт те же файлы.
"""
import argparse
import json
import os
import shutil
import subprocess
import sys
import zlib

try:
    import numpy as np
except ImportError:
    sys.exit("Нужен numpy: pip install numpy")

RES = os.path.join("src", "main", "resources", "assets", "hypersomnia")
SOUNDS = os.path.join(RES, "sounds", "voice")
TIMING = os.path.join(RES, "voice", "timing.json")
SRC = os.path.join("src", "tools", "voice_src")
MODELS = os.path.join("build", "voice_models")
SR = 44100
TPS = 20
VOICES = {"en": "en_US-joe-medium"}
# Сдвиг высоты основного голоса (множитель частоты): свою запись подстрой здесь
PITCH = {"en": 0.93}
# Громкость слоёв (дБ к основному голосу): far - с места фигуры, near - в голове
MIX = {
    "far": {"dark": -16.0, "up": -14.0, "whisper": -11.0, "swell": -19.0, "room": -16.0, "rt60": 0.9, "pre": 0.6},
    "near": {"dark": -17.0, "up": -15.0, "whisper": -7.0, "swell": -24.0, "room": -21.0, "rt60": 0.45, "pre": 0.35},
}
# Пауза между фразами при синтезе (с): реплики медленные, с паузами
SENTENCE_GAP = 0.42

# id (как в VoiceLine.java), манера: far - с места фигуры, near - вплотную/в голове; текст (русский - в ru_ru.json)
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
    """Куски речи между паузами (секунды): по ним субтитры открывают фразы."""
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


def process(dry, style, seed, pitch):
    m = MIX[style]
    x = trim(dry)
    x = x / (np.max(np.abs(x)) + 1e-9) * 0.7
    tempo = 0.94
    main = shift(x, pitch, tempo)
    dark = shift(x, pitch * 0.9, tempo, "shifted")
    up = shift(x, pitch * 1.012, tempo)
    wh = whisper(main, seed)
    voice = mix(main,
                delay(up, 27.0) * db(m["up"]),
                fft_filter(dark, hi=2400.0) * db(m["dark"]),
                delay(fft_filter(wh, lo=1100.0), 9.0) * db(m["whisper"]))
    near = style == "near"
    voice = fft_filter(voice, lo=110.0, hi=7600.0 if near else 7000.0)
    peak = np.max(np.abs(voice)) + 1e-9
    voice = np.tanh(1.6 * voice / peak) / np.tanh(1.6) * peak
    voice = wow(voice, seed + 1)
    segs = segments(main)

    pre_roll = m["pre"]
    swell = conv(voice[::-1], impulse(2.2, seed + 2, 3200.0))[::-1]
    pre = len(swell) - len(voice)
    room = conv(voice, impulse(m["rt60"], seed + 3, 5200.0))
    out = np.zeros(pre + len(room))
    out[:len(swell)] += swell * db(m["swell"])
    out[pre:pre + len(room)] += room * db(m["room"])
    out[pre:pre + len(voice)] += voice
    cut = max(0, pre - int(pre_roll * SR))
    out = out[cut:]
    offset = (pre - cut) / SR

    # Хвост: до -60 дБ, затухание в конце; плавный вход «вдоха»
    e, k = envelope(out, 0.01)
    alive = np.nonzero(e > np.max(e) * db(-60.0))[0]
    if len(alive):
        out = out[:min(len(out), (alive[-1] + 1) * k)]
    fin = min(len(out), int(0.2 * SR))
    out[:fin] *= np.linspace(0.0, 1.0, fin)
    fout = min(len(out), int(0.15 * SR))
    out[-fout:] *= np.linspace(1.0, 0.0, fout)

    # Громкость по речи: -20 дБ (в голове -22), пик не выше -2 дБ
    sp = np.concatenate([out[int((a + offset) * SR):int((b + offset) * SR)] for a, b in segs]) if segs else out
    rms = np.sqrt(np.mean(sp * sp)) + 1e-9
    out *= db(-22.0 if near else -20.0) / rms
    pk = np.max(np.abs(out))
    if pk > db(-2.0):
        lim = db(-2.0)
        out = np.where(np.abs(out) > lim * 0.8,
                       np.sign(out) * (lim * 0.8 + lim * 0.2 * np.tanh((np.abs(out) - lim * 0.8) / (lim * 0.2))), out)

    # Шипение плёнки (розоватое), очень тихо
    rng = np.random.default_rng(seed + 4)
    hiss = fft_filter(rng.standard_normal(len(out)), lo=900.0, hi=9000.0, order=1)
    hiss *= db(-56.0) / (np.sqrt(np.mean(hiss * hiss)) + 1e-9)
    ramp = min(len(out), int(0.08 * SR))
    hiss[:ramp] *= np.linspace(0, 1, ramp)
    hiss[-ramp:] *= np.linspace(1, 0, ramp)
    out += hiss
    t = {
        "on": int(round((segs[0][0] + offset) * TPS)) if segs else 0,
        "end": int(round((segs[-1][1] + offset) * TPS)) if segs else int(len(out) / SR * TPS),
        "len": int(np.ceil(len(out) / SR * TPS)),
        "seg": [[int(round((a + offset) * TPS)), int(round((b + offset) * TPS))] for a, b in segs],
    }
    return out, t


VOICE_CACHE = {}


def synth(lang, text, models):
    try:
        from piper import PiperVoice, SynthesisConfig
    except ImportError:
        sys.exit("Нет исходника и нет Piper: pip install piper-tts (или положи свою запись в " + SRC + ")")
    if lang not in VOICE_CACHE:
        path = os.path.join(models, VOICES[lang] + ".onnx")
        if not os.path.exists(path):
            sys.exit("Нет модели " + path + " (скачай с https://huggingface.co/rhasspy/piper-voices)")
        VOICE_CACHE[lang] = PiperVoice.load(path)
    v = VOICE_CACHE[lang]
    cfg = SynthesisConfig(length_scale=1.0, noise_scale=0.62, noise_w_scale=0.75)
    parts = []
    sr = 22050
    for i, ch in enumerate(v.synthesize(text, syn_config=cfg)):
        sr = ch.sample_rate
        if i:
            parts.append(np.zeros(int(sr * SENTENCE_GAP)))
        parts.append(trim(np.asarray(ch.audio_float_array, dtype=np.float64), sr, margin=0.02))
    return np.concatenate(parts), sr


def save_flac(x, sr, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    subprocess.run([ffmpeg(), "-v", "error", "-y", "-f", "f32le", "-ar", str(sr), "-ac", "1", "-i", "-",
                    "-c:a", "flac", "-sample_fmt", "s16", path], input=x.astype(np.float32).tobytes(), check=True)


def save_ogg(x, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    subprocess.run([ffmpeg(), "-v", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", "-",
                    "-c:a", "libvorbis", "-q:a", "4", "-map_metadata", "-1", "-fflags", "+bitexact",
                    "-flags:a", "+bitexact", path], input=x.astype(np.float32).tobytes(), check=True)


def find_src(lang, lid):
    for ext in (".flac", ".wav", ".ogg", ".mp3"):
        p = os.path.join(SRC, lang, lid + ext)
        if os.path.exists(p):
            return p
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tts", action="store_true", help="пересинтезировать исходники Piper")
    ap.add_argument("--only", default="", help="id через запятую")
    ap.add_argument("--models", default=MODELS)
    a = ap.parse_args()
    only = {s.strip() for s in a.only.split(",") if s.strip()}
    langs = ["en"]
    timing = {}
    if os.path.exists(TIMING):
        with open(TIMING, encoding="utf-8") as f:
            timing = json.load(f)
    for lang in langs:
        timing.setdefault(lang, {})
        for lid, style, en in LINES:
            if only and lid not in only:
                continue
            src = find_src(lang, lid)
            if a.tts or src is None:
                x, sr = synth(lang, en, a.models)
                src = os.path.join(SRC, lang, lid + ".flac")
                save_flac(x, sr, src)
            dry = load(src)
            seed = zlib.crc32((lang + ":" + lid).encode("utf-8")) & 0xFFFF
            out, t = process(dry, style, seed, PITCH[lang])
            save_ogg(out, os.path.join(SOUNDS, lang, lid + ".ogg"))
            timing[lang][lid] = t
            print("%s/%-13s %.2f c, речь %d-%d тиков, кусков %d" % (lang, lid, len(out) / SR, t["on"], t["end"],
                                                                   len(t["seg"])))
    os.makedirs(os.path.dirname(TIMING), exist_ok=True)
    ordered = {lang: {lid: timing[lang][lid] for lid, _, _ in LINES if lid in timing[lang]}
               for lang in langs if lang in timing}
    with open(TIMING, "w", encoding="utf-8", newline="\n") as f:
        f.write("{\n")
        for li, (lang, d) in enumerate(ordered.items()):
            f.write('  "%s": {\n' % lang)
            items = list(d.items())
            for i, (lid, t) in enumerate(items):
                f.write('    "%s": %s%s\n' % (lid, json.dumps(t, ensure_ascii=False), "," if i < len(items) - 1 else ""))
            f.write("  }%s\n" % ("," if li < len(ordered) - 1 else ""))
        f.write("}\n")
    print("Готово:", TIMING)


if __name__ == "__main__":
    main()
