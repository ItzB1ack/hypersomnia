package com.hypersomnia.dream;

/** Числа зеркала дома в мире снов (DreamMirror). */
public final class DreamMirrorTuning {
    private DreamMirrorTuning() {}

    /** До этой стадии дом в сон не переносится: сон остаётся пустым, как раньше. */
    public static final int MIN_STAGE = 3;

    /**
     * Со стадии 4 в доме появляется портал-петля. Снаружи дом остаётся прежним:
     * геометрия не растягивается, невозможность делает только портал внутри.
     */
    public static final int LOOP_MIN_STAGE = 4;
    /** С этой стадии петля невидима: игрок просто оказывается в начале коридора. */
    public static final int LOOP_INVISIBLE_STAGE = 5;

    /** Радиус захвата вокруг чанка кровати: 1 = 3x3 чанка (48x48 блоков). */
    public static final int CHUNK_RADIUS = 1;
    /** Вертикальный захват вокруг кровати. */
    public static final int VERTICAL_BELOW = 24;
    public static final int VERTICAL_ABOVE = 40;

    /**
     * Сколько секций 16x16x16 обрабатывать за тик. Сон в кровати длится BED_SLEEP_TICKS (80)
     * тиков, а захват и перенос вместе занимают ~46 секций: по 2 за тик зеркало успевает
     * полностью лечь в мир снов ещё до телепорта, без «достройки» на глазах.
     */
    public static final int CAPTURE_PER_TICK = 2;
    public static final int APPLY_PER_TICK = 2;
    /** Сколько секций доработать прямо перед телепортом, если сон короче захвата. */
    public static final int TRANSITION_BUDGET = 24;

    // ---- Портал-петля: снаружи дом прежний, внутри коридор не кончается ----
    /** Насколько ниже и выше кровати искать коридор (этажи дома). */
    public static final int LOOP_DEPTH_BELOW = 16;
    public static final int LOOP_DEPTH_ABOVE = 8;
    /** Сначала ищем коридор на этаже кровати (±6), и только потом на остальных этажах. */
    public static final int LOOP_NEAR_DEPTH = 6;
    /**
     * Коридор — это узкий проход: не шире 5 блоков между стенами. Всё шире считается
     * комнатой или улицей и петлю не получает (иначе петля оказывается в спальне).
     */
    public static final int LOOP_MAX_WIDTH = 5;
    /** Высота портала берётся по потолку коридора: 2..4 блока. */
    public static final int LOOP_MAX_HEIGHT = 4;
    /** Петля короче 6 блоков выглядит как обычный шаг назад — не ставим. */
    public static final int LOOP_MIN_LENGTH = 6;
    /** Насколько далеко назад по коридору возвращать игрока. */
    public static final int LOOP_BACK_MAX = 10;
    /** Сколько тиков ждать загрузки сущностей в мире снов, прежде чем бросить попытку. */
    public static final int LOOP_RETRY_TICKS = 60;
}
