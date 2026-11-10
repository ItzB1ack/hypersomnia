package com.hypersomnia;

import net.minecraft.nbt.CompoundTag;

/** Таблица условий сна и кривая усталости (N24). */
public final class FatigueSelfTest {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    private static void close(float actual, float expected, String why) {
        check(Math.abs(actual - expected) < 0.0001f, why + ": " + actual + " != " + expected);
    }

    public static void main(String[] args) {
        curve();
        bounds();
        serialization();
        penalties();
        daySleepTable();
        dreamStartTable();
        System.out.println("FatigueSelfTest: " + checks + " checks passed.");
    }

    private static void curve() {
        check(FatigueTuning.MAX_FATIGUE == 48000, "40 minutes at one fatigue per tick");
        check(FatigueTuning.GAIN_PER_TICK == 1, "awake gain is one per tick");
        check(FatigueTuning.SLEEP_RECOVERY_PER_TICK == 400, "sleep recovery is 400 per tick");

        close(FatigueTuning.ratio(0), 0f, "ratio at zero");
        close(FatigueTuning.ratio(FatigueTuning.MAX_FATIGUE / 4), 0.25f, "ratio at quarter");
        close(FatigueTuning.ratio(FatigueTuning.MAX_FATIGUE / 2), 0.5f, "ratio at half");
        close(FatigueTuning.ratio(FatigueTuning.MAX_FATIGUE), 1f, "ratio at max");
        close(FatigueTuning.ratio(-1), 0f, "negative fatigue clamps to zero");
        close(FatigueTuning.ratio(FatigueTuning.MAX_FATIGUE + 1), 1f, "overflow clamps to one");

        close(FatigueTuning.progress(0.2f, FatigueTuning.HUNGER_START), 0f, "progress below start");
        close(FatigueTuning.progress(FatigueTuning.HUNGER_START, FatigueTuning.HUNGER_START), 0f,
                "progress at start");
        close(FatigueTuning.progress(0.7f, 0.4f), 0.5f, "progress halfway");
        close(FatigueTuning.progress(1f, 0.4f), 1f, "progress at max");
        close(FatigueTuning.progress(2f, 0.4f), 1f, "progress above max clamps");
        close(FatigueTuning.progress(-1f, 0.4f), 0f, "progress below zero clamps");

        check(FatigueTuning.HUNGER_START < FatigueTuning.DROWSY_START, "hunger before drowsy");
        check(FatigueTuning.DROWSY_START < FatigueTuning.BLACKOUT_START, "drowsy before blackout");
        check(FatigueTuning.BLACKOUT_START < FatigueTuning.NO_SPRINT_START, "blackout before no-sprint");
        check(FatigueTuning.NO_SPRINT_START < FatigueTuning.SLEEP_ANYTIME_FATIGUE,
                "no-sprint before day sleep");
        check(FatigueTuning.SLEEP_ANYTIME_FATIGUE < FatigueTuning.BLUR_START, "day sleep before blur");
        check(FatigueTuning.BLUR_START == FatigueTuning.CLUMSY_START, "blur and clumsy start together");

        int threshold = Math.round(FatigueTuning.MAX_FATIGUE * FatigueTuning.SLEEP_ANYTIME_FATIGUE);
        check(threshold == 36000, "day sleep threshold is 75 percent");
        check(!FatigueTuning.canSleepAtAnyTime(threshold - 1), "day sleep locked one point below");
        check(FatigueTuning.canSleepAtAnyTime(threshold), "day sleep allowed at threshold");
        check(FatigueTuning.canSleepAtAnyTime(FatigueTuning.MAX_FATIGUE), "day sleep allowed at max");
        check(!FatigueTuning.canSleepAtAnyTime(-1), "negative fatigue cannot sleep");

        FatigueData data = new FatigueData();
        for (int i = 0; i < FatigueTuning.MAX_FATIGUE; i++) data.addFatigue(FatigueTuning.GAIN_PER_TICK);
        check(data.getFatigue() == FatigueTuning.MAX_FATIGUE, "awake curve reaches max");
        for (int i = 0; i < 80; i++) data.addFatigue(-FatigueTuning.SLEEP_RECOVERY_PER_TICK);
        check(data.getFatigue() == 16000, "80 bed ticks recover 32000");
        for (int i = 0; i < 20; i++) data.addFatigue(-FatigueTuning.SLEEP_RECOVERY_PER_TICK);
        check(data.getFatigue() == 8000, "100 bed ticks recover 40000");
        for (int i = 0; i < 20; i++) data.addFatigue(-FatigueTuning.SLEEP_RECOVERY_PER_TICK);
        check(data.getFatigue() == 0, "sleep recovery stops at zero");

        int failed = Math.round(FatigueTuning.MAX_FATIGUE * FatigueTuning.FAILED_SLEEP_FATIGUE);
        check(failed == 38400, "failed sleep leaves 80 percent");
        check(FatigueTuning.canSleepAtAnyTime(failed), "failed sleep still allows day sleep");
    }

    private static void bounds() {
        FatigueData data = new FatigueData();
        data.setFatigue(-5);
        check(data.getFatigue() == 0, "setFatigue clamps below zero");
        data.setFatigue(FatigueTuning.MAX_FATIGUE + 100);
        check(data.getFatigue() == FatigueTuning.MAX_FATIGUE, "setFatigue clamps above max");
        data.setFatigue(50);
        data.addFatigue(-100);
        check(data.getFatigue() == 0, "addFatigue clamps below zero");
        data.addFatigue(FatigueTuning.MAX_FATIGUE);
        check(data.getFatigue() == FatigueTuning.MAX_FATIGUE, "addFatigue clamps above max");
        data.addFatigue(Integer.MAX_VALUE);
        check(data.getFatigue() == FatigueTuning.MAX_FATIGUE, "huge addFatigue does not overflow");
        data.setFatigue(100);
        data.addFatigue(Integer.MIN_VALUE);
        check(data.getFatigue() == 0, "huge negative addFatigue does not overflow");

        data.addMadness(-5);
        check(data.getMadness() == 0, "addMadness clamps below zero");
        data.addMadness(3);
        check(data.getMadness() == 3, "addMadness adds");
        data.addMadness(Integer.MAX_VALUE);
        check(data.getMadness() == Integer.MAX_VALUE, "huge addMadness does not overflow");
        data.addMadness(1);
        check(data.getMadness() == Integer.MAX_VALUE, "madness saturates at int max");

        data.setCollapseTimer(-1);
        check(data.getCollapseTimer() == 0, "collapse timer clamps");
        data.setCollapseGraceTicks(-1);
        check(data.getCollapseGraceTicks() == 0, "collapse grace clamps");
        check(data.incrementForcedSleepAttempts() == 1, "forced sleep attempt increments");
        check(data.incrementForcedSleepAttempts() == 2, "forced sleep attempts count");
        data.resetForcedSleepAttempts();
        check(data.incrementForcedSleepAttempts() == 1, "forced sleep attempts reset");
    }

    private static void serialization() {
        FatigueData data = new FatigueData();
        data.setFatigue(12345);
        data.addMadness(4);
        data.setCollapseTimer(77);
        data.setCollapseGraceTicks(33);
        data.setSyringeCollapse(true);
        data.setAnyTimeSleep(true);
        data.incrementForcedSleepAttempts();
        CompoundTag tag = data.serializeNBT(null);

        FatigueData loaded = new FatigueData();
        loaded.deserializeNBT(null, tag);
        check(loaded.getFatigue() == 12345, "fatigue survives reload");
        check(loaded.getMadness() == 4, "madness survives reload");
        check(loaded.getCollapseTimer() == 77, "collapse timer survives reload");
        check(loaded.getCollapseGraceTicks() == 33, "collapse grace survives reload");
        check(loaded.isSyringeCollapse(), "syringe collapse survives reload");
        check(!loaded.isAnyTimeSleep(), "day sleep permission is session-only");
        check(loaded.incrementForcedSleepAttempts() == 1, "forced sleep attempts are session-only");

        CompoundTag broken = new CompoundTag();
        broken.putInt("fatigue", -100);
        broken.putInt("madness", -7);
        broken.putInt("collapse", -3);
        broken.putInt("collapseGrace", -2);
        loaded.deserializeNBT(null, broken);
        check(loaded.getFatigue() == 0, "old negative fatigue clamps");
        check(loaded.getMadness() == 0, "old negative madness clamps");
        check(loaded.getCollapseTimer() == 0, "old negative collapse clamps");
        check(loaded.getCollapseGraceTicks() == 0, "old negative grace clamps");
        check(!loaded.isSyringeCollapse(), "missing syringe flag is false");

        broken.putInt("fatigue", FatigueTuning.MAX_FATIGUE + 500);
        broken.putInt("madness", 9);
        loaded.deserializeNBT(null, broken);
        check(loaded.getFatigue() == FatigueTuning.MAX_FATIGUE, "old high fatigue clamps");
        check(loaded.getMadness() == 9, "madness above max is allowed");

        FatigueData empty = new FatigueData();
        empty.setFatigue(100);
        empty.deserializeNBT(null, new CompoundTag());
        check(empty.getFatigue() == 0, "empty tag resets fatigue");
        check(empty.getMadness() == 0, "empty tag resets madness");
    }

    private static void penalties() {
        check(SleepPenalty.restFatigue(0) == 4800, "no madness leaves 10 percent");
        check(SleepPenalty.restFatigue(1) == 7200, "one madness leaves 15 percent");
        check(SleepPenalty.restFatigue(2) == 9600, "two madness leave 20 percent");
        check(SleepPenalty.restFatigue(4) == 14400, "four madness leave 30 percent");
        check(SleepPenalty.restFatigue(5) == 16800, "five madness hit 35 percent cap");
        check(SleepPenalty.restFatigue(100) == 16800, "rest floor is capped");
        check(SleepPenalty.restFatigue(-3) == 4800, "negative madness uses base floor");
        check(SleepPenalty.restFatigue(Integer.MAX_VALUE) == 16800, "huge madness keeps floor capped");
        check(FatigueTuning.FATIGUE_AFTER_FORCED_SLEEP == SleepPenalty.restFatigue(0),
                "forced sleep floor matches penalty");

        FatigueData data = new FatigueData();
        data.addMadness(2);
        check(SleepPenalty.restFatigue(data) == 9600, "data overload matches madness formula");

        check(SleepPenalty.inertiaSeconds(0) == 8, "inertia starts at 8 seconds");
        check(SleepPenalty.inertiaSeconds(1) == 12, "inertia grows by 4 seconds");
        check(SleepPenalty.inertiaSeconds(5) == 28, "inertia at strong slow");
        check(SleepPenalty.inertiaSeconds(13) == 60, "inertia caps at 60 seconds");
        check(SleepPenalty.inertiaSeconds(100) == 60, "inertia stays capped");
        check(SleepPenalty.inertiaSeconds(-1) == 8, "negative madness uses base inertia");
        check(SleepPenalty.inertiaSeconds(Integer.MAX_VALUE) == 60, "huge madness does not overflow inertia");

        check(SleepPenalty.slowAmplifier(4) == 0, "slow I below strong threshold");
        check(SleepPenalty.slowAmplifier(5) == 1, "slow II at strong threshold");
        check(SleepPenalty.slowAmplifier(100) == 1, "slow stays at II");
        check(SleepPenalty.slowAmplifier(-1) == 0, "negative madness uses slow I");

        check(SleepPenalty.nauseaSeconds(0) == 0, "no nausea without madness");
        check(SleepPenalty.nauseaSeconds(1) == 0, "no nausea at one madness");
        check(SleepPenalty.nauseaSeconds(2) == 5, "nausea starts at two madness");
        check(SleepPenalty.nauseaSeconds(5) == 8, "nausea grows with madness");
        check(SleepPenalty.nauseaSeconds(9) == 12, "nausea caps at 12 seconds");
        check(SleepPenalty.nauseaSeconds(100) == 12, "nausea stays capped");
        check(SleepPenalty.nauseaSeconds(-1) == 0, "negative madness has no nausea");
        check(SleepPenalty.nauseaSeconds(Integer.MAX_VALUE) == 12, "huge madness does not overflow nausea");
    }

    private static void daySleepTable() {
        int threshold = Math.round(FatigueTuning.MAX_FATIGUE * FatigueTuning.SLEEP_ANYTIME_FATIGUE);
        check(SleepRules.maySleepThroughDay(false, false, false, false, false, threshold),
                "day sleep allowed at threshold");
        check(!SleepRules.maySleepThroughDay(false, false, false, false, false, threshold - 1),
                "day sleep locked below threshold");
        check(SleepRules.maySleepThroughDay(false, false, false, false, false, FatigueTuning.MAX_FATIGUE),
                "day sleep allowed at max");
        check(!SleepRules.maySleepThroughDay(false, false, false, false, false, 0),
                "day sleep locked at zero");
        check(!SleepRules.maySleepThroughDay(true, false, false, false, false, threshold),
                "completed finale blocks day sleep");
        check(!SleepRules.maySleepThroughDay(false, true, false, false, false, threshold),
                "creative blocks day sleep");
        check(!SleepRules.maySleepThroughDay(false, false, true, false, false, threshold),
                "spectator blocks day sleep");
        check(!SleepRules.maySleepThroughDay(false, false, false, true, false, threshold),
                "chase blocks day sleep");
        check(!SleepRules.maySleepThroughDay(false, false, false, false, true, threshold),
                "collapse blocks day sleep");
        check(!SleepRules.maySleepThroughDay(true, true, true, true, true, FatigueTuning.MAX_FATIGUE),
                "all blockers together");

        check(SleepRules.mayContinueDaySleep(true, false, false), "allowed day sleep continues");
        check(!SleepRules.mayContinueDaySleep(false, false, false), "session flag is required");
        check(!SleepRules.mayContinueDaySleep(true, true, false), "chase interrupts day sleep");
        check(!SleepRules.mayContinueDaySleep(true, false, true), "finale interrupts day sleep");
        check(!SleepRules.mayContinueDaySleep(false, true, true), "nothing continues without flag");
    }

    private static void dreamStartTable() {
        int bed = 80;
        check(SleepRules.shouldEnterDream(false, false, true, bed, bed, false, 0, false),
                "bed entry at 80 ticks");
        check(!SleepRules.shouldEnterDream(false, false, true, bed - 1, bed, false, 0, false),
                "bed entry waits for 80 ticks");
        check(SleepRules.shouldEnterDream(false, false, true, bed + 1, bed, false, 0, false),
                "bed entry after 80 ticks");
        check(!SleepRules.shouldEnterDream(true, false, true, bed, bed, false, 0, false),
                "already in dream blocks entry");
        check(!SleepRules.shouldEnterDream(false, true, true, bed, bed, false, 0, false),
                "dream level blocks entry");
        check(!SleepRules.shouldEnterDream(false, false, false, bed, bed, false, 0, false),
                "not sleeping blocks entry");
        check(!SleepRules.shouldEnterDream(false, false, true, bed, bed, true, 0, false),
                "syringe blocks entry");
        check(!SleepRules.shouldEnterDream(false, false, true, bed, bed, false, 1, false),
                "scene blocks entry");
        check(SleepRules.shouldEnterDream(false, false, true, bed, bed, false, -1, false),
                "negative scene hold is free");
        check(!SleepRules.shouldEnterDream(false, false, true, bed, bed, false, 0, true),
                "completed finale blocks dream entry");
        check(!SleepRules.shouldEnterDream(true, true, false, bed, bed, true, 1, true),
                "all blockers together");
    }
}
