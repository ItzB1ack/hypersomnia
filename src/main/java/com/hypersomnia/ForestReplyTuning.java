package com.hypersomnia;

/** Стартовый баланс «Лес отвечает». Независим от усталости, стадии и бюджета режиссёра. */
public final class ForestReplyTuning {
    private ForestReplyTuning() {}

    public static final long DAY_TICKS = 24_000L;
    public static final double CHANCE = 0.20;
    public static final int PAUSE_MIN = 80;
    public static final int PAUSE_MAX = 140;
    public static final int COOLDOWN_DAYS_MIN = 3;
    public static final int COOLDOWN_DAYS_MAX = 5;
    public static final double DISTANCE_MIN = 8.0;
    public static final double DISTANCE_MAX = 12.0;
    public static final float VOLUME = 1.0f;
    public static final float PITCH = 0.8f; // обычный звук разрушения деревянного блока

    // Технические границы ритма, поиска и сетевого запроса, не отдельные игровые кулдауны.
    public static final int GAP_DEFAULT = 12;
    public static final int GAP_MIN = 6;
    public static final int GAP_MAX = 60;
    public static final int REQUEST_TTL = 200;
    public static final int AUDIO_START_TIMEOUT = 5;
    public static final int AUDIO_TAIL = 20;
    public static final int MAX_BREAKS_BUFFERED = 64;
    public static final int MAX_SOURCE_CHECKS = 32;
    public static final double MAX_LOOK_DOT = 0.25;

    public static int clampGap(long ticks) {
        return (int) Math.clamp(ticks, GAP_MIN, GAP_MAX);
    }

    public static boolean chancePasses(double draw) {
        return draw >= 0.0 && draw < CHANCE;
    }
}