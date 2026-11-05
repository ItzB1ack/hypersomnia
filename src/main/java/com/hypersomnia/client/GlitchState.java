package com.hypersomnia.client;

import net.minecraft.util.RandomSource;

/** Состояние сбоя экспозиции: активен ли, сколько тиков осталось, какая строка. */
public final class GlitchState {
    private GlitchState() {}

    public static int activeTicks;
    public static int totalTicks;
    public static int lineIndex;
    public static final RandomSource RANDOM = RandomSource.create();

    public static boolean active() {
        return activeTicks > 0;
    }
}