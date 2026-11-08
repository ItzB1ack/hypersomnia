package com.hypersomnia.trace;

/**
 * Виды следов. Новый след = новая строка здесь + текстуры trace_<asset>_<n>.png (для ITEM asset = id предмета).
 * FLOOR/WALL: плоская «наклейка» на поверхности, ITEM: предмет на полу.
 * TOY — Зайка (ModItems.BUNNY, текстура из tools/make_traces.py): игрушка с рисунка 4, которую Nora просит вернуть.
 */
public enum TraceType {
    //          вид,        asset,         вариантов, размер, мин. стадия, вес
    STEPS(Kind.FLOOR,     "steps",     2, 0.9f, 3, 3),
    TALLY(Kind.FLOOR,     "tally",     2, 0.8f, 3, 2),
    HOPSCOTCH(Kind.FLOOR, "hopscotch", 1, 1.6f, 4, 1),
    HAND(Kind.WALL,       "hand",      2, 0.4f, 3, 2),
    SCRIBBLE(Kind.WALL,   "scribble",  3, 0.6f, 3, 2),
    TOY(Kind.ITEM,        "hypersomnia:bunny", 1, 1.0f, 4, 1),
    WRITING(Kind.TEXT,    "writing",   12, 0.8f, 3, 0);

    public enum Kind { FLOOR, WALL, ITEM, TEXT }

    public final Kind kind;
    public final String asset;
    public final int variants;
    public final float size;
    public final int minStage;
    public final int weight;

    TraceType(Kind kind, String asset, int variants, float size, int minStage, int weight) {
        this.kind = kind;
        this.asset = asset;
        this.variants = variants;
        this.size = size;
        this.minStage = minStage;
        this.weight = weight;
    }
}