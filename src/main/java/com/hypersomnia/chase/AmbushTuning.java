package com.hypersomnia.chase;

/**
 * Засада — короткий экшен-момент: игрок открывает дверь или закрывает контейнер, а осколок уже стоит
 * вплотную. Серверная часть (server/AmbushHandler, ChaseHandler.startAmbush), клиентская
 * (client/AmbushClient, AmbushOverlay). Числа — ГИПОТЕЗА для ручного прохождения.
 */
public final class AmbushTuning {
    private AmbushTuning() {}

    /** С этой стадии засада вообще бывает: раньше осколок ещё не действует так близко. */
    public static final int MIN_STAGE = 3;
    /** Шанс за одно открытие двери / закрытие контейнера, когда расписание погонь уже позволяет. */
    public static final float DOOR_CHANCE = 0.04f;
    public static final float CONTAINER_CHANCE = 0.03f;
    /** Сколько тиков ждать подтверждения (дверь открылась, экран контейнера закрылся). */
    public static final int PENDING_TIMEOUT = 400;
    /** Контейнер считается «тем самым», если игрок не отошёл от него дальше этого (блоки). */
    public static final double CONTAINER_RADIUS = 6.0;
    /** Дверь: фигура встаёт с противоположной от игрока стороны, на таком удалении от центра двери. */
    public static final double DOOR_BEHIND = 1.5;
    /** Контейнер: фигура встаёт за спиной игрока (он смотрит в открытый экран), на таком удалении. */
    public static final double CONTAINER_BEHIND = 2.5;
    /** Где искать пол рядом с желаемой точкой, если сама точка занята (блоки). */
    public static final double SPOT_SEARCH = 2.25;

    /** Сколько тиков он стоит вплотную, прежде чем сорваться. Взгляд в засаде не сокращает это время. */
    public static final int WARN_TICKS = 18;

    /** Клиент: вспышка/чёрный кадр, дрожь камеры и статика. */
    public static final int SCARE_TICKS = 10;
    public static final int SCARE_BLACK_TICKS = 1;
    public static final int SCARE_FLASH_TICKS = 2;
    public static final int SCARE_SHAKE_TICKS = 7;
    public static final float SCARE_SHAKE_YAW = 2.8f;
    public static final float SCARE_SHAKE_PITCH = 2.2f;
    public static final float SCARE_SHAKE_ROLL = 3.5f;
    public static final float SCARE_FLASH_ALPHA = 0.75f;
    public static final float SCARE_SOUND_VOLUME = 1.0f;
    public static final float SCARE_SOUND_PITCH = 0.65f;
    public static final float SCARE_GLASS_VOLUME = 0.7f;
    public static final float SCARE_GLASS_PITCH = 0.55f;
}
