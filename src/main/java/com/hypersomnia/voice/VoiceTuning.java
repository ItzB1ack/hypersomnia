package com.hypersomnia.voice;

/**
 * Голос осколка (client/FragmentVoice, ChaseVoice). Время — в тиках. Субтитры ванильные (sounds.json "subtitle"),
 * их чисел здесь нет.
 */
public final class VoiceTuning {
    private VoiceTuning() {}

    // ------------------------------------------------------------------
    // Звук (категория «Голос/речь»)
    // ------------------------------------------------------------------
    /** Громкость реплики с места фигуры и «в голове» (поимка, меню паузы). */
    public static final float VOLUME_FAR = 1.0f;
    public static final float VOLUME_NEAR = 0.85f;
    /** Ближе HEAD_DIST блоков реплика звучит у самой головы (смещение HEAD_OFFSET), а не с места фигуры. */
    public static final double HEAD_DIST = 4.0;
    public static final double HEAD_OFFSET = 0.6;
    /**
     * Дальше FAR_DIST блоков с места фигуры почти не слышно (затухание 32 блока, sounds.json): реплика звучит тихо
     * со стороны фигуры, в FAR_PROXY блоках от игрока, — и ванильный субтитр показывает, откуда.
     */
    public static final double FAR_DIST = 22.0;
    public static final double FAR_PROXY = 12.0;
    public static final float VOLUME_DISTANT = 0.45f;
    /** Если voice/timing.json не прочитан: считаем, что реплика длится столько тиков. */
    public static final int FALLBACK_TICKS = 60;

    // ------------------------------------------------------------------
    // Погоня (ChaseVoice)
    // ------------------------------------------------------------------
    /** Он встал на след: с таким шансом заговорит через START_DELAY тиков. */
    public static final float START_CHANCE = 0.75f;
    public static final int START_DELAY = 26;
    /** На бегу: первая реплика не раньше RUN_FIRST, дальше раз в RUN_MIN..RUN_MAX_GAP, не больше RUN_MAX за погоню и только ближе RUN_DIST блоков. */
    public static final int RUN_FIRST = 120;
    public static final int RUN_MIN = 170;
    public static final int RUN_MAX_GAP = 300;
    public static final int RUN_MAX = 2;
    public static final double RUN_DIST = 20.0;
    /** Упёрся в закрытую дверь: после стольких тиков стука просит впустить. */
    public static final int BLOCKED_DELAY = 18;
    /** Шанс реплики, когда погоня кончилась (ушёл, прогнал взглядом). */
    public static final float END_CHANCE = 0.8f;
    /** Шёпот молчит, пока он говорит, и ещё столько тиков после. */
    public static final int WHISPER_GAP = 16;
}
