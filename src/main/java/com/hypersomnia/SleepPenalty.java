package com.hypersomnia;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Наказание за принудительный сон, растущее с безумием: «пол» усталости и сонная инерция. */
public final class SleepPenalty {
    private SleepPenalty() {}

    // ---- Числа (правь здесь) ----
    /** Базовый уровень усталости после сна (доля от максимума). */
    public static final float BASE_FLOOR = 0.10f;
    /** Прибавка к полу за единицу безумия. */
    public static final float FLOOR_PER_MADNESS = 0.05f;
    /** Потолок пола. */
    public static final float FLOOR_CAP = 0.35f;

    /** Инерция: базовая длительность и прибавка за безумие (секунды). */
    public static final int INERTIA_BASE_SEC = 8;
    public static final int INERTIA_PER_MADNESS_SEC = 4;
    public static final int INERTIA_CAP_SEC = 60;
    /** Замедление усиливается (уровень II) начиная с этого безумия. */
    public static final int STRONG_SLOW_MADNESS = 5;
    /** Тошнота: с какого безумия, и её длительность (секунды). */
    public static final int NAUSEA_FROM_MADNESS = 2;
    public static final int NAUSEA_BASE_SEC = 3;
    public static final int NAUSEA_PER_MADNESS_SEC = 1;
    public static final int NAUSEA_CAP_SEC = 12;

    /** Уровень усталости, до которого она сбрасывается после сна (с учётом безумия). */
    public static int restFatigue(FatigueData data) {
        return restFatigue(data.getMadness());
    }

    /** То же по числу безумия: чистая формула для тестов и старых сохранений. */
    public static int restFatigue(int madness) {
        float frac = Math.min(FLOOR_CAP, BASE_FLOOR + FLOOR_PER_MADNESS * Math.max(0, madness));
        return Math.round(FatigueTuning.MAX_FATIGUE * frac);
    }

    /** Длительность сонной инерции в секундах; длинное умножение не переполняется. */
    public static int inertiaSeconds(int madness) {
        long sec = INERTIA_BASE_SEC + (long) INERTIA_PER_MADNESS_SEC * Math.max(0, madness);
        return (int) Math.min(INERTIA_CAP_SEC, sec);
    }

    /** Усиление замедления: II уровень с STRONG_SLOW_MADNESS. */
    public static int slowAmplifier(int madness) {
        return Math.max(0, madness) >= STRONG_SLOW_MADNESS ? 1 : 0;
    }

    /** Длительность тошноты в секундах; 0 — тошноты нет. */
    public static int nauseaSeconds(int madness) {
        int m = Math.max(0, madness);
        if (m < NAUSEA_FROM_MADNESS) return 0;
        long sec = NAUSEA_BASE_SEC + (long) NAUSEA_PER_MADNESS_SEC * m;
        return (int) Math.min(NAUSEA_CAP_SEC, sec);
    }

    /** Сонная инерция после пробуждения. Вызывать на сервере. */
    public static void applyInertia(ServerPlayer player) {
        int madness = player.getData(ModAttachments.FATIGUE).getMadness();
        int ticks = inertiaSeconds(madness) * 20;
        int slowAmp = slowAmplifier(madness);

        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, slowAmp, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 0, false, false, true));

        int nSec = nauseaSeconds(madness);
        if (nSec > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, nSec * 20, 0, false, false, true));
        }
    }
}
