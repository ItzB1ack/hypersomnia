package com.hypersomnia;

/**
 * Чистые условия сна и пробуждения. Без ServerPlayer и событий, чтобы таблицу
 * «можно/нельзя» можно было проверить обычным тестом.
 */
public final class SleepRules {
    private SleepRules() {}

    /**
     * Можно ли лечь днём: ванильный NOT_POSSIBLE_NOW снимается только при
     * сильной усталости и если игрок не в погоне, не падает, не в креативе,
     * не наблюдатель и финал ещё не пройден.
     */
    public static boolean maySleepThroughDay(boolean finaleCompleted, boolean creative,
                                             boolean spectator, boolean chaseActive,
                                             boolean collapsing, int fatigue) {
        return !finaleCompleted && !creative && !spectator && !chaseActive
                && !collapsing && FatigueTuning.canSleepAtAnyTime(fatigue);
    }

    /**
     * Можно ли продолжить уже разрешённый дневной сон. Проверяем флаг сессии,
     * а не число усталости: за время сна она успевает опуститься ниже порога.
     */
    public static boolean mayContinueDaySleep(boolean anyTimeSleep, boolean chaseActive,
                                              boolean finaleCompleted) {
        return anyTimeSleep && !chaseActive && !finaleCompleted;
    }

    /**
     * Пора ли уходить из кровати в мир снов. Шприц и сцена блокируют переход,
     * после финала кровать работает как ванильная.
     */
    public static boolean shouldEnterDream(boolean inDream, boolean inDreamLevel, boolean sleeping,
                                           int sleepTimer, int bedSleepTicks, boolean syringeAwake,
                                           int sceneHold, boolean finaleCompleted) {
        return !inDream && !inDreamLevel && sleeping
                && sleepTimer >= bedSleepTicks
                && !syringeAwake && sceneHold <= 0 && !finaleCompleted;
    }
}
