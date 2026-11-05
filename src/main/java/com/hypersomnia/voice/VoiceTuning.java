package com.hypersomnia.voice;

/** Голос осколка и субтитры (client/FragmentVoice, ChaseVoice, FragmentFont). Время — в тиках. */
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
    /** Дальше FAR_HEAD_DIST блоков с места фигуры уже не слышно: реплика звучит тихо «в голове». */
    public static final double FAR_HEAD_DIST = 22.0;
    public static final float VOLUME_FAR_HEAD = 0.45f;

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

    // ------------------------------------------------------------------
    // Субтитры: крупный тонкий шрифт, буквы дрожат
    // ------------------------------------------------------------------
    /** Размер шрифта (кегль): доля высоты экрана, пределы в пикселях интерфейса. */
    public static final float SUB_SIZE = 0.064f;
    public static final float SUB_SIZE_MIN = 10f;
    public static final float SUB_SIZE_MAX = 48f;
    /** Базовая линия последней строки (доля высоты); в меню паузы ниже кнопок. */
    public static final float SUB_Y = 0.74f;
    public static final float SUB_Y_PAUSE = 0.86f;
    /** Ширина строки (доля экрана), межстрочие и разрядка (доли кегля). */
    public static final float SUB_WIDTH = 0.82f;
    public static final float SUB_LINE = 1.2f;
    public static final float SUB_TRACKING = 0.035f;
    /** Цвет текста осколка («вина», как его строки в ObserverScareOverlay и EndingScreen) и двойника — сдвиг плёнки
     *  (доля кегля, прозрачность); двойник из палитры #4A5E70: красный только для ограничений и штампов. */
    public static final int SUB_RGB = 0xC8B8BC;
    public static final int SUB_GHOST_RGB = 0x4A5E70;
    public static final float SUB_GHOST_SHIFT = 0.05f;
    public static final float SUB_GHOST_ALPHA = 0.45f;
    /** Тёмная полоса под текстом (непрозрачность 0..1, цвет вуали). */
    public static final float SUB_BAND_ALPHA = 0.45f;
    public static final int SUB_BAND_RGB = 0x03050A;
    /** Буква проявляется за SUB_CHAR_FADE тиков; фраза открывается за долю SUB_REVEAL своего куска речи. */
    public static final float SUB_CHAR_FADE = 3f;
    public static final float SUB_REVEAL = 0.85f;
    /** После конца речи субтитр держится SUB_HOLD и гаснет за SUB_FADE тиков. */
    public static final int SUB_HOLD = 30;
    public static final int SUB_FADE = 14;
    /** Дрожь: каждая буква прыгает раз в SHAKE_STEP_MS мс на долю кегля SHAKE_AMP; поверх — тремор (доля размаха). */
    public static final int SHAKE_STEP_MS = 70;
    public static final float SHAKE_AMP = 0.04f;
    /** Доля дрожи по горизонтали: буквы трясутся в основном вверх-вниз, чтобы слово не разваливалось. */
    public static final float SHAKE_X = 0.45f;
    public static final float SHAKE_TREMOR = 0.35f;
    /** Редкий рывок одной буквы: шанс на шаг и множитель размаха. Только что проявленная буква дрожит сильнее. */
    public static final float SHAKE_JERK_CHANCE = 0.03f;
    public static final float SHAKE_JERK = 3.2f;
    public static final float SHAKE_FRESH = 2f;
    public static final float SHAKE_FRESH_TICKS = 8f;
    /** Срыв строки (как сбой развёртки): шанс на шаг и сдвиг (доля кегля). */
    public static final float SLIP_CHANCE = 0.05f;
    public static final float SLIP = 0.08f;
    /** Сила дрожи: в погоне по ступени эскалации (0..3), при поимке, в меню паузы. */
    public static final float[] SHAKE_TIER = {0.75f, 0.95f, 1.15f, 1.45f};
    public static final float SHAKE_CAUGHT = 1.4f;
    public static final float SHAKE_PAUSE = 1.6f;
}
