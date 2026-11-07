package com.hypersomnia.nightmare;

/** Числа кошмаров. Общий код, без клиентских импортов. */
public final class NightmareTuning {
    private NightmareTuning() {}

    /** Фиксированная зона кошмаров в измерении снов: начало и шаг между слотами по X. [проверить: не пересекается ли с LabPlacement] */
    public static final int ZONE_X = 20000;
    public static final int ZONE_Y = 100;
    public static final int ZONE_Z = 20000;
    public static final int SLOT_STRIDE = 512;

    // Шахта: независимые секции, расположенные над обычной поверхностью.
    public static final int SHAFT_LANES = 8;
    public static final int SHAFT_Y = 240;
    public static final int SHAFT_LANE_STRIDE = 16;
    public static final int SHAFT_STOP_STRIDE = 16;
    public static final int SHAFT_TRIP_TICKS = 80;
    /** Время после шахты, чтобы игрок мог найти обычный сон, прежде чем снова упасть. */
    public static final int SHAFT_COLLAPSE_GRACE_TICKS = 600;
    public static final int ROUNDS_Y = 240;
    public static final int ROUNDS_CAPSULES = 47;
    public static final int ROUNDS_CAPSULE_STRIDE = 4;
    public static final int ROUNDS_SYRINGE_EVERY = 6;
    public static final int ROUNDS_SYRINGE_PICKUP_DELAY = 32767;
    public static final int ROUNDS_EXIT_LIE_TICKS = 40;
    public static final int SESSION_Y = 240;
    public static final int SESSION_CLOCK_UPDATE_TICKS = 20;
    public static final int SESSION_CONSOLE_RETRY_TICKS = 40;
    public static final int SESSION_CLOCK_0307_AT = 1200;
    public static final int SESSION_CLOCK_0312_AT = 3000;
    public static final int SESSION_CLOCK_0313_AT = 4760;

    // ---- «Коридор палат»: X вдоль коридора, пол на ZONE_Y, внутри z 0..WIDTH-1 ----
    /** Период рисунка по X (палата + перегородка) и число периодов. Телепорт цикла сдвигает ровно на период. */
    public static final int WARD_PERIOD = 8;
    public static final int WARD_PERIODS = 6;
    public static final int CORRIDOR_WIDTH = 3;
    public static final int CORRIDOR_HEIGHT = 4;
    public static final int WARD_DEPTH = 5;
    /** Смещение проёма (и светильника) внутри периода. */
    public static final int DOOR_OFFSET = 4;
    /** Уровни света невидимых блоков LIGHT. */
    public static final int LIGHT_CORRIDOR = 7;
    public static final int LIGHT_WARD = 3;
    /** Плоскости на одинаковой фазе рисунка, вдали от дверных проёмов. */
    public static final int WARD_LOOP_BACK_X = WARD_PERIOD + 2;
    public static final int WARD_LOOP_FORWARD_X = WARD_PERIOD * 4 + 2;
    /** Поворот при появлении: лицом по +X. */
    public static final float SPAWN_YAW = -90f;

    // Клиентские осколки у коек: период обновления и запас на задержку пакета.
    public static final int WARD_GHOST_SYNC_TICKS = 40;
    public static final int WARD_GHOST_TIMEOUT_TICKS = 160;
    public static final double WARD_GHOST_RENDER_DISTANCE = 40.0;

    /** После трёх кругов одна северная палата становится пустой для конкретного игрока. */
    public static final int WARD_EMPTY_AFTER_LOOPS = 3;
    public static final int WARD_EMPTY_PERIOD = 2;
    public static final int WARD_EMPTY_BED_X = 2;
    public static final int WARD_DIALOGUE_CHECK_TICKS = 10;
    public static final int WARD_DIALOGUE_LINES = 3;

    /** Запасной выход по таймеру (тики): 6000 = 5 минут. */
    public static final int WARD_DURATION_TICKS = 6000;
    public static final int SHAFT_DURATION_TICKS = 6000;
    public static final int ROUNDS_DURATION_TICKS = 6000;
    /** Сеанс 47 заканчивается часами, таймер только запасной. */
    public static final int SESSION_DURATION_TICKS = 4800;
}