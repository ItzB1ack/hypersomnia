package com.hypersomnia.trace;

/** Числа детских следов. Общий код, без клиентских импортов. */
public final class TraceTuning {
    private TraceTuning() {}

    /** С какой стадии осколка появляются следы при входе в сон (мир снов, копия дома). */
    public static final int MIN_STAGE = 3;
    /** Шанс, что при входе в сон появится след. Было 0.7 и 1-3 следа после пробуждения. [гипотеза] */
    public static final float WAKE_CHANCE = 0.35f;
    /** Один след за сон: эпизод «след» — одно событие, а не россыпь. */
    public static final int PER_WAKE_MIN = 1;
    public static final int PER_WAKE_MAX = 1;
    /** Отладка (/hypersomnia_traces wake): сколько следов за раз. */
    public static final int DEBUG_MIN = 1;
    public static final int DEBUG_MAX = 3;
    /** Знакомая точка маршрута (память привычек) ближе этого к кровати: след ставится у неё, в радиусе ANCHOR_RADIUS. */
    public static final int ROUTE_RADIUS = 20;
    public static final int ANCHOR_RADIUS = 3;
    /** Доля следов у точки маршрута (остальные, как раньше, вокруг кровати). */
    public static final float ROUTE_SHARE = 0.6f;
    /**
     * «Где стоял — там след»: с этой стадии при входе в сон в доле SEEN_SHARE случаев след ставится туда, где игрок
     * недавно прогнал фигуру взглядом в реальном мире (ObserverProgressData.seen): шаги STEPS носками к кровати.
     * Нет такого места — как раньше. [гипотеза]
     */
    public static final int SEEN_MIN_STAGE = 3;
    public static final float SEEN_SHARE = 0.7f;
    /** След не ставится ближе этого к самой кровати (блоки). */
    public static final int MIN_FROM_BED = 3;
    /** Всего следов у игрока; при переполнении самые старые убираются. */
    public static final int MAX_TOTAL = 24;
    /** Радиус поиска места вокруг кровати (блоки) и число попыток на один след. Первую половину попыток только в помещении. */
    public static final int RADIUS = 12;
    public static final int ATTEMPTS = 24;
    // Отложенные персональные надписи, без изменения мира [новое].
    public static final float WRITING_CHANCE = .35f;
    public static final int WRITING_DELAY_MIN = 600;
    public static final int WRITING_DELAY_RANGE = 600;
    public static final int WRITING_RETRY_TICKS = 600;
    public static final int WRITING_NEAR_RADIUS = 16;
    public static final float WRITING_MAX_WIDTH = .82f;
    public static final float WRITING_MAX_SCALE = .012f;
    public static final float WRITING_TILT_DEGREES = 3f;
    /**
     * Порядок личных надписей: {вариант, минимальная стадия}. После последней — снова сначала,
     * чтобы к стадии 5 игрок прошёл вопросы, зацикливание и обвинение, а не увидел случайную строку. [новое]
     */
    public static final int[][] WRITING_ORDER = {
            {0, 3}, {1, 3}, {2, 3},
            {3, 4}, {4, 4}, {8, 4}, {9, 4},
            {5, 5}, {6, 5}, {7, 5}, {10, 5}, {11, 5}
    };
    /** Вариант следующей надписи по шагу и стадии; шаг растёт только после успешной установки. */
    public static int writingVariant(int step, int stage) {
        int n = WRITING_ORDER.length;
        for (int i = 0; i < n; i++) {
            int[] e = WRITING_ORDER[Math.floorMod(step + i, n)];
            if (e[1] <= stage) return e[0];
        }
        return 0;
    }
    /** Клиент: дальше этой дистанции не рисуем; отступ «наклейки» от поверхности, чтобы не мерцала. */
    public static final double RENDER_DISTANCE = 48.0;
    public static final float DECAL_OFFSET = 0.015f;
    /**
     * Предмет-след (ITEM) лежит плашмя: подъём над полом (половина толщины плоской модели 1/32 в масштабе GROUND и запас
     * от мерцания) и обратный сдвиг смещения GROUND у item/generated (2/16 вверх), чтобы предмет лежал в центре клетки.
     */
    public static final float ITEM_LIFT = 0.02f;
    public static final float ITEM_GROUND_SHIFT = 0.125f;
}