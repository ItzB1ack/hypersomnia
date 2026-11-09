package com.hypersomnia.chase;

/**
 * Скример поимки: в момент, когда осколок догнал игрока, поверх чёрного кадра на пару тиков
 * вспыхивает белое, затем в камеру влетает его лицо (пара глаз) с тряской по кадру, а звук
 * бьёт низким шёпотом, провалом ленты и выдохом. Похоже на засаду, но без дрожи камеры:
 * там испуг — статика и случайная тряска, здесь — «лицо вплотную» с наездом.
 * Клиент: client/CaughtScare (звук, тики) и client/CaughtScareOverlay (кадр).
 * Числа — ГИПОТЕЗА для ручного прохождения.
 */
public final class CaughtTuning {
    private CaughtTuning() {}

    /** Длительность кадра. Укладывается в чёрный кадр поимки (BLACK_IN + BLACK_HOLD = 34). */
    public static final int SCARE_TICKS = 18;
    /** Белая вспышка в начале, до появления глаз. */
    public static final int FLASH_TICKS = 2;
    /** За столько тиков глаза наезжают от почти нормального размера до предела. */
    public static final int LUNGE_TICKS = 5;
    /** С этого тика кадр гаснет в чёрный. */
    public static final int FADE_START = 12;
    public static final float FLASH_ALPHA = 0.97f;
    /** Масштаб пары глаз в начале наезда и в пределе. */
    public static final float ZOOM_NEAR = 1.6f;
    public static final float ZOOM_FAR = 4.4f;
    /** Наклон кадра в начале и в пределе (градусы). */
    public static final float TILT_FROM = -5f;
    public static final float TILT_TO = 4f;
    /** Тряска самого кадра (не камеры): доля ширины/высоты экрана. */
    public static final float JITTER_X = 0.012f;
    public static final float JITTER_Y = 0.012f;
    /** Звук: низкий шёпот осколка, провал ленты, выдох и тяжёлый удар сердца. */
    public static final float WHISPER_VOLUME = 0.9f;
    public static final float WHISPER_PITCH = 0.32f;
    public static final float TAPE_VOLUME = 0.9f;
    public static final float TAPE_PITCH = 0.40f;
    public static final float BREATH_VOLUME = 0.8f;
    public static final float BREATH_PITCH = 0.45f;
    public static final float HEART_VOLUME = 1.0f;
    public static final float HEART_PITCH = 0.70f;
}
