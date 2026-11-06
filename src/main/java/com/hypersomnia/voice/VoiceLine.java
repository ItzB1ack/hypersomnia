package com.hypersomnia.voice;

/**
 * Реплики осколка. id — имя файла (sounds/voice/en/<id>.ogg) и ванильного субтитра (sounds.json "subtitle":
 * hypersomnia.subtitle.voice.<id>, тексты в en_us.json и ru_ru.json; в комментарии — английский текст).
 * Аудио делает tools/make_fragment_voice.py (тот же список LINES). Каждая реплика читается двояко:
 * сейчас угроза, при повторном прочтении — просьба о помощи. Эскалация по стадии: вопросы и мольбы,
 * зацикленность, обвинение, ярость.
 */
public enum VoiceLine {
    // Погоня: он встал на след
    START_EARLY("start_early"), // Who are you?
    START_LOOP("start_loop"), // There you are.
    START_ACCUSE("start_accuse"), // It's you. It's always you.
    START_RAGE("start_rage"), // How can you sleep?
    // Погоня: бег
    RUN_EARLY_0("run_early_0"), // Wait. Please.
    RUN_EARLY_1("run_early_1"), // Where am I?
    RUN_LOOP_0("run_loop_0"), // Don't go. Not again.
    RUN_LOOP_1("run_loop_1"), // I was too late. Too late.
    RUN_ACCUSE_0("run_accuse_0"), // You were asleep.
    RUN_ACCUSE_1("run_accuse_1"), // You forgot her.
    RUN_RAGE_0("run_rage_0"), // Wake up.
    RUN_RAGE_1("run_rage_1"), // This isn't a dream.
    // Погоня: исходы (ушёл, упёрся в дверь, поймал, прогнал взглядом)
    ESCAPE_0("escape_0"), // Don't leave me here.
    ESCAPE_1("escape_1"), // You always leave.
    BLOCKED_0("blocked_0"), // Let me in.
    BLOCKED_1("blocked_1"), // Open the door.
    CAUGHT_0("caught_0"), // I've got you.
    CAUGHT_1("caught_1"), // Now you're awake.
    STARED("stared"), // You see me.
    // Меню паузы в сцене у кровати (по кругу); вторая — пункт 4 контракта C-47
    PAUSE_0("pause_0"), // There's no pause here.
    PAUSE_1("pause_1"), // You may not end a session early.
    PAUSE_2("pause_2"); // Why won't you listen? Stay. With me.

    /** Озвучка только английская (решение пользователя); ванильный субтитр — на языке игры. */
    public static final String AUDIO_LANG = "en";

    private static final VoiceLine[] START = {START_EARLY, START_LOOP, START_ACCUSE, START_RAGE};
    private static final VoiceLine[][] RUN = {
            {RUN_EARLY_0, RUN_EARLY_1}, {RUN_LOOP_0, RUN_LOOP_1}, {RUN_ACCUSE_0, RUN_ACCUSE_1}, {RUN_RAGE_0, RUN_RAGE_1}};
    private static final VoiceLine[] PAUSE = {PAUSE_0, PAUSE_1, PAUSE_2};

    public final String id;

    VoiceLine(String id) {
        this.id = id;
    }

    /** Звуковое событие реплики: voice.en.<id>. */
    public String sound() {
        return "voice." + AUDIO_LANG + "." + id;
    }

    /** Ступень эскалации по стадии: 0 вопросы и мольбы (до 2), 1 зацикленность (3), 2 обвинение (4), 3 ярость (5). */
    public static int tier(int stage) {
        return Math.max(0, Math.min(3, stage - 2));
    }

    public static VoiceLine start(int stage) {
        return START[tier(stage)];
    }

    public static VoiceLine[] run(int stage) {
        return RUN[tier(stage)];
    }

    public static VoiceLine escape(int stage) {
        return stage <= 3 ? ESCAPE_0 : ESCAPE_1;
    }

    public static VoiceLine blocked(int stage) {
        return stage <= 3 ? BLOCKED_0 : BLOCKED_1;
    }

    public static VoiceLine caught(int stage) {
        return stage <= 4 ? CAUGHT_0 : CAUGHT_1;
    }

    public static VoiceLine pause(int count) {
        return PAUSE[Math.floorMod(count, PAUSE.length)];
    }
}
