package com.hypersomnia.dream;

/** Числа мира снов. */
public final class DreamTuning {
    private DreamTuning() {}

    /** Сколько длится сон до пробуждения (тики). 6000 = 5 минут. */
    public static final int DURATION_TICKS = 6000;

    /** Сколько тиков нужно пролежать в кровати, чтобы уйти в мир снов (в ваниле ночь пропускается на 100-м тике). */
    public static final int BED_SLEEP_TICKS = 80;

    /** Поиск безопасной точки при входе/выходе: на сколько блоков вверх и вниз искать. */
    public static final int SAFE_SEARCH_RANGE = 16;
    /** Если исходная колонка небезопасна, проверяем поверхность соседних загруженных колонок. */
    public static final int SAFE_SURFACE_RADIUS = 4;
    /** При неудачном пробуждении сохраняем состояние сна и повторяем попытку не чаще раза в 10 секунд. */
    public static final int WAKE_RETRY_TICKS = 200;

    /** Если игрок спит в Незере, в мир снов он попадает в координаты Верхнего мира (x8). */
    public static final double NETHER_SCALE = 8.0;

    /** После пробуждения в Верхнем мире ночь пропускается (время идёт на утро). */
    public static final boolean WAKE_SKIPS_NIGHT = true;
    /** Сон в Верхнем мире прекращает дождь и грозу, как ванильный сон. */
    public static final boolean WAKE_CLEARS_WEATHER = true;
    /** Время суток начала ночи (с него в ваниле можно спать) и время суток после пробуждения (0 = утро). */
    public static final long NIGHT_STARTS_AT = 12542L;
    public static final long WAKE_TIME_OF_DAY = 0L;

    // ---- Небо: глаз вместо луны (DreamSkyEye) ----
    /** Половина ширины глаза в единицах неба (у луны 20, расстояние 100). Должна закрывать луну с запасом. */
    public static final float SKY_EYE_HALF_WIDTH = 44f;
    public static final float SKY_EYE_ALPHA = 0.92f;
    public static final int SKY_EYE_BLINK_PERIOD_MS = 5200;
    /** С какой стадии осколка в небе мира снов открывается глаз; раньше только тёмный диск без луны. */
    public static final int SKY_EYE_MIN_STAGE = 4;
}
