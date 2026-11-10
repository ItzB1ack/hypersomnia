package com.hypersomnia.client;

import com.hypersomnia.ObserverTuning;

/**
 * Числовые правила режиссёра без состояния: прирост и трата бюджета, лимит
 * сильных эпизодов, тишина после эпизода. Вынесено из ObserverDirector, чтобы
 * формулы проверялись отдельно от очереди эпизодов.
 */
public final class DirectorRules {
    private DirectorRules() {}

    /** Прирост бюджета за тик; выше потолка не поднимается. */
    public static float regen(float budget, float pressure, int stage) {
        return Math.min(ObserverTuning.DIRECTOR_BUDGET_CAP, budget
                + ObserverTuning.DIRECTOR_REGEN_BASE
                + ObserverTuning.DIRECTOR_REGEN_PER_PRESSURE * pressure
                + ObserverTuning.DIRECTOR_REGEN_PER_STAGE * stage);
    }

    /** Трата бюджета на эпизод; ниже нуля не уходит. */
    public static float spend(float budget, int kind) {
        return Math.max(0f, budget - ObserverTuning.EP_COST[kind]);
    }

    /** Сколько сильных эпизодов допустимо в окне на этой стадии. */
    public static int strongLimit(int stage) {
        return ObserverTuning.STRONG_PER_WINDOW_BY_STAGE[Math.max(0, Math.min(5, stage))];
    }

    /** Есть ли место для ещё одного сильного эпизода в окне. */
    public static boolean strongRoom(int recent, int stage) {
        return recent < strongLimit(stage);
    }

    /** Можно ли начать сильный эпизод: не два подряд и не больше лимита окна. */
    public static boolean mayStartStrong(boolean lastStrong, int recent, int stage) {
        return !lastStrong && strongRoom(recent, stage);
    }

    /** Тишина после эпизода: короткая у незамеченного, иначе по стадии и силе. */
    public static int silenceAfter(boolean noticed, boolean strong, int stage) {
        if (!noticed) return ObserverTuning.SILENCE_AFTER_ABORT;
        return Math.max(ObserverTuning.SILENCE_AFTER_BY_STAGE[stage],
                strong ? ObserverTuning.SILENCE_AFTER_STRONG : 0);
    }
}
