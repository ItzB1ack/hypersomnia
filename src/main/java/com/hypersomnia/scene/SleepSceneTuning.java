package com.hypersomnia.scene;

/** Числа и размеры пулов фраз сцены «This isn't a dream» (общий код, без клиентских импортов). */
public final class SleepSceneTuning {
    private SleepSceneTuning() {}

    /** На каком тике лежания в кровати может случиться сцена (до BED_SLEEP_TICKS=80). */
    public static final int TRIGGER_TICK = 30;

    // ---- Тайминг: вступление -> тело сцены -> чёрная пауза ----
    /** Вступление: звук обрывается, кадр медленно темнеет, в проёме появляется свет. Фигуры ещё нет. */
    public static final int LEAD_IN_TICKS = 36;
    /** Тело сцены: стадии фигуры и строки (все времена STAGE_AT / LINE_START считаются от его начала). */
    public static final int BODY_TICKS = 240;
    /** Чёрный экран в тишине после обрыва, прежде чем вернётся игра. */
    public static final int END_HOLD_TICKS = 16;
    /** Длительность сцены целиком (тики). Всё это время игрок почти не может двигаться. */
    public static final int SCENE_TICKS = LEAD_IN_TICKS + BODY_TICKS + END_HOLD_TICKS;

    /** Последние тики тела сцены: кадр замирает целиком (камера, зерно, сердцебиение), потом обрыв. */
    public static final int FREEZE_TICKS = 26;
    /** Сколько тиков тишина держится ПОСЛЕ сцены (мир возвращается без единого звука). */
    public static final int SILENCE_TAIL_TICKS = 100;

    /** Шанс при каждой попытке уснуть: база + за единицу безумия, потолок 1. */
    public static final float CHANCE_BASE = 0.30f;
    public static final float CHANCE_PER_MADNESS = 0.15f;
    /** Гарантия: на столько-й попытке сцена случится обязательно. */
    public static final int GUARANTEED_ATTEMPT = 4;

    // ---- Пулы фраз (ключи: hypersomnia.scene.t2.0 ... ) ----
    /** t1: всегда одна фраза «This isn't a dream» (ключ hypersomnia.scene.t1). */
    public static final int T2_COUNT = 6; // «ты не можешь спать»
    public static final int T3_COUNT = 7; // голос Осколка, вина
    public static final int T4_COUNT = 3; // финальная короткая строка

    /** Все фразы показываются одним облачком. Центр (доли экрана), полуширина облачка (доля ширины), максимум ширины строки (доля ширины). */
    public static final float CLOUD_CX = 0.50f;
    public static final float CLOUD_CY = 0.64f;
    public static final float CLOUD_HALF_W = 0.20f;
    public static final float CLOUD_MAX_W = 0.50f;
    /** Шаг между этажами в долях высоты строки: меньше 1 = строки перекрываются. */
    public static final float CLOUD_LINE_STEP = 0.90f;
    /** Фразы живут по кругу: каждая держится LIFE_MIN..LIFE_MAX тиков, гаснет за FADE_OUT и возвращается в другом месте (t1 не гаснет). */
    public static final int TEXT_LIFE_MIN = 70;
    public static final int TEXT_LIFE_MAX = 150;
    public static final int TEXT_FADE_OUT = 16;
    /** Медленный дрейф фразы на месте (GUI-юниты). */
    public static final float TEXT_DRIFT = 5f;
    /**
     * Меню паузы во время сцены, по шагам (тики с открытия меню): темнеет и открывается один глаз (EYE1), за ним
     * второй (EYE2), каждый за EYE_OPEN_TICKS; потом проступает рой мечущегося текста (CROWD..CROWD_FULL,
     * до PAUSE_SWARM_MAX строк; TextSwarm); на VOICE он говорит (client/FragmentVoice, реплики VoiceLine.PAUSE_*). Меню он закрывает
     * сам через AFTER_VOICE тиков после конца речи, но не раньше PAUSE_CLOSE_TICKS и не позже CLOSE_MAX;
     * потом ярость в сцене (дрожь зрачков, красная вспышка) держится SCOLD_SHOW_TICKS.
     */
    public static final int PAUSE_EYE1_TICKS = 8;
    public static final int PAUSE_EYE2_TICKS = 26;
    public static final int PAUSE_EYE_OPEN_TICKS = 12;
    public static final int PAUSE_CROWD_TICKS = 42;
    public static final int PAUSE_CROWD_FULL_TICKS = 74;
    public static final int PAUSE_VOICE_TICKS = 52;
    public static final int PAUSE_AFTER_VOICE_TICKS = 8;
    public static final int PAUSE_CLOSE_TICKS = 70;
    public static final int PAUSE_CLOSE_MAX_TICKS = 170;
    public static final int SCOLD_SHOW_TICKS = 80;
    /** Редкие фразы в меню: число строк в пике и общая яркость [новое, вместо толпы глаз]. */
    public static final int PAUSE_SWARM_MAX = 12;
    public static final float PAUSE_SWARM_ALPHA = 0.9f;
    /** Пиксельная эрозия краёв экрана и меню (PixelDecay); на гневе она доходит до кнопок [новое]. */
    public static final float PAUSE_DECAY_MAX = 0.95f;
    /** Затемнение поверх меню (0..1): когда открыты оба глаза и когда проступил весь рой. */
    public static final float PAUSE_DARK_EYES = 0.55f;
    public static final float PAUSE_DARK_CROWD = 0.85f;
    /** Дрожь зрачков в гневе: шум меняется раз в PAUSE_TREMOR_MS, а не каждый кадр. Взгляд догоняет цель с постоянной PAUSE_GAZE_TAU_MS. */
    public static final int PAUSE_TREMOR_MS = 60;
    public static final float PAUSE_GAZE_TAU_MS = 90f;
    /** Пара в центре тоже следит за курсором: максимальный сдвиг зрачка (доли размера глаза). */
    public static final float PAUSE_PAIR_LOOK_X = 0.30f;
    public static final float PAUSE_PAIR_LOOK_Y = 0.12f;
    /** Окно проявления фраз (тики от начала тела); t1 идёт первой. Каждая проявляется за TEXT_FADE_IN тиков и висит до обрыва. */
    public static final int TEXT_APPEAR_MIN = 12;
    public static final int TEXT_APPEAR_MAX = 80;
    public static final int TEXT_FADE_IN = 12;
    /** Наклон (градусы в обе стороны), разброс масштаба, минимальная яркость, масштаб t1. */
    public static final float TEXT_MAX_TILT = 4f;
    public static final float TEXT_SCALE_MIN = 0.85f;
    public static final float TEXT_SCALE_MAX = 1.35f;
    public static final float TEXT_MIN_BRIGHTNESS = 0.6f;
    public static final float TEXT_T1_SCALE = 1.7f;
}
