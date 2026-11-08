package com.hypersomnia.dream;

/**
 * Напольные часы в мире снов: единственный «чистый» способ проснуться раньше таймера.
 * Часы ставятся в случайном месте недалеко от точки входа и тикают; если игрок не нашёл их
 * за FIND_TICKS, часы бьют (число ударов растёт со стадией) и начинается погоня.
 */
public final class DreamClockTuning {
    private DreamClockTuning() {}

    /** Сколько тиков даётся на поиск часов (3 минуты). */
    public static final int FIND_TICKS = 3 * 60 * 20;
    /** Тиканье: ровно раз в секунду (тик-так: два разных тона).
     *  Дальность: fixed-range 192 блока (ModSounds) + attenuation 192 (sounds.json). */
    public static final int TICK_EVERY_TICKS = 20;
    public static final float TICK_VOLUME = 1.3f;
    public static final float TICK_PITCH_HIGH = 1.35f;
    public static final float TICK_PITCH_LOW = 1.05f;

    /** Анимация маятника: полный цикл 2 с, крайние точки — на каждом тике (раз в секунду). */
    public static final int PENDULUM_PERIOD_TICKS = 40;
    public static final float PENDULUM_SWING_DEGREES = 7.0f;
    /** Стрелки идут ускоренно, как во сне: минутная делает оборот за 10 реальных минут. */
    public static final int MINUTE_HAND_TICKS = 12000;
    public static final int HOUR_HAND_TICKS = MINUTE_HAND_TICKS * 12;

    /** Сколько раз часы бьют после истечения времени; индекс — стадия 0..5. */
    public static final int[] STRIKES_BY_STAGE = {2, 3, 4, 5, 6, 7};
    /** Задержка перед первым ударом и между ударами: у напольных часов удары реже тиканья. */
    public static final int STRIKE_FIRST_TICKS = 30;
    public static final int STRIKE_EVERY_TICKS = 26;
    /** После последнего удара погоня начинается не сразу: игрок успевает понять, что произошло. */
    public static final int CHASE_DELAY_TICKS = 30;
    public static final float STRIKE_VOLUME = 1.2f;
    /** Если погоню не удалось начать (место/чанки), повтор через столько тиков. */
    public static final int CHASE_RETRY_TICKS = 40;

    /** Где искать место для часов: кольцо вокруг точки входа в сон. */
    public static final double PLACE_DIST_MIN = 14.0;
    public static final double PLACE_DIST_MAX = 34.0;
    /** Сколько случайных точек проверить, прежде чем сдаться. */
    public static final int PLACE_TRIES = 64;
    /** Насколько выше и ниже точки входа искать пол под часы. */
    public static final int PLACE_UP = 8;
    public static final int PLACE_DOWN = 12;

    /** Усталость после поимки погоней из часов: доля от максимума. */
    public static final float CATCH_FATIGUE_RATIO = 0.5f;
}
