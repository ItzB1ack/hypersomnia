package com.hypersomnia.document;

/** Рисунки Nora. Общий код, без клиентских импортов. */
public final class DrawingTuning {
    private DrawingTuning() {}

    public static final String PREFIX = "drawing_";
    public static final String BLANK_ID = "drawing_blank";
    /** Сколько рисунков нужно для секрета «Нора» (ids drawing_0..4). */
    public static final int COUNT = 5;
    /** Пустой лист (бонус: реплика в финале) хранится в маске под этим битом. */
    public static final int BLANK_INDEX = 5;
    /** Как часто сервер проверяет инвентарь игрока (тики). */
    public static final int SCAN_INTERVAL_TICKS = 20;

    public static String idOf(int index) {
        return index == BLANK_INDEX ? BLANK_ID : PREFIX + index;
    }

    /** 0..COUNT-1 рисунок, BLANK_INDEX пустой лист, -1 не рисунок. */
    public static int indexOf(String id) {
        if (BLANK_ID.equals(id)) return BLANK_INDEX;
        if (!id.startsWith(PREFIX)) return -1;
        try {
            int i = Integer.parseInt(id.substring(PREFIX.length()));
            return i >= 0 && i < COUNT ? i : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}