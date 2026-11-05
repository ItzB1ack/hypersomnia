package com.hypersomnia;

import net.minecraft.util.Mth;

/** Все числа баланса в одном месте. Позже можно вынести в ModConfigSpec. */
public final class FatigueTuning {
    private FatigueTuning() {}

    // ---- Усталость ----
    /** 48000 тиков = 40 минут реального времени без сна. */
    public static final int MAX_FATIGUE = 48000;
    public static final int GAIN_PER_TICK = 1;
    /** Пока игрок лежит в кровати (100 тиков до пропуска ночи = -40000). */
    public static final int SLEEP_RECOVERY_PER_TICK = 400;
    /** Сколько усталости остаётся после принудительного сна. */
    public static final int FATIGUE_AFTER_FORCED_SLEEP = (int) (MAX_FATIGUE * 0.10f);
    /** Сколько тиков длится падение, прежде чем начнётся сон. */
    public static final int COLLAPSE_TICKS = 100;

    // ---- Пороги (доля от максимума) ----
    public static final float BLACKOUT_START = 0.45f;   // потемнения в глазах
    public static final float HUNGER_START = 0.25f;     // ускоренный голод
    public static final float BLUR_START = 0.85f;       // «забытые» предметы
    public static final float CLUMSY_START = 0.85f;     // моторика: блоки и цифры
    public static final float NO_SPRINT_START = 0.65f;  // нельзя бегать
    public static final float NO_SCROLL_START = 0.85f;  // колесико мыши отключено
    public static final float DROWSY_START = 0.40f;

    // ---- Голод ----
    /** Максимальная добавка к истощению за тик (4.0 истощения = 1 единица голода). */
    public static final float EXTRA_EXHAUSTION_PER_TICK_MAX = 0.006f;

    // ---- Синхронизация ----
    public static final int SYNC_THRESHOLD = 100;
    /** Раз в столько тиков усталость уходит клиенту принудительно (при смене мира клиент мог потерять значение). */
    public static final int RESYNC_INTERVAL_TICKS = 100;

    public static float ratio(int fatigue) {
        return Mth.clamp(fatigue / (float) MAX_FATIGUE, 0f, 1f);
    }

    /** 0..1: насколько ratio продвинулся от порога start до максимума. */
    public static float progress(float ratio, float start) {
        return Mth.clamp((ratio - start) / (1f - start), 0f, 1f);
    }
}
