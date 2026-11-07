package com.hypersomnia.syringe;

/** Числа адреналиновых шприцев. Общий код (без клиентских импортов). */
public final class  SyringeTuning {
    private SyringeTuning() {}

    /** Длительность искусственного бодрствования при нулевой толерантности (тики). 4800 = 4 минуты. */
    public static final int BASE_WAKE_TICKS = 4800;
    public static final int MIN_WAKE_TICKS = 600;
    /** Эффективность = BASE^толерантность (длительность и глубина). */
    public static final float EFFICIENCY_BASE = 0.8f;

    /** Уровень, до которого падает усталость на время действия (доля максимума), при нулевой толерантности. */
    public static final float WAKE_LEVEL = 0.08f;
    /** Насколько этот уровень поднимается при нулевой эффективности (прибавка = (1 - eff) * это). */
    public static final float PIN_TOLERANCE_GAIN = 0.25f;

    /** Откат: снятое * (1 + база + за толерантность) + накопленное под спудом. */
    public static final float INTEREST_BASE = 0.25f;
    public static final float INTEREST_PER_TOLERANCE = 0.05f;
    /** Если после отката усталость >= этой доли максимума, игрок сразу падает в принудительный сон. */
    public static final float COLLAPSE_RATIO = 0.75f;

    /** Побочки: с какого уровня что включается (уровень 0..3 считается из толерантности). */
    /** Голод во время действия: изнурение за тик = база + за уровень побочек (у усталости максимум 0.006). */
    public static final float EXHAUSTION_BASE = 0.005f;
    public static final float EXHAUSTION_PER_LEVEL = 0.002f;
    public static int sideLevel(int tolerance) {
        return Math.max(0, Math.min(3, (tolerance - 1) / 2));
    }

    /** Пауза между уколами (тики) и самоподавление повторного использования. */
    public static final int USE_COOLDOWN = 40;

    /** Длительность применения шприца (тики): анимация удержания, затем эффект. */
    public static final int APPLY_TICKS = 20;

    /** Крах после отката: замедление и тошнота (тики). */
    public static final int CRASH_SLOW_TICKS = 160;
    public static final int CRASH_NAUSEA_TICKS = 80;

    // ---- Клиент ----
    /** За сколько тиков до конца действия начинается предупредительная дрожь. */
    public static final int WARN_TICKS = 100;
    /**
     * Прибавка к «давлению» наблюдателя во время бодрствования: база + за уровень. Давление меняет только частоту
     * и выбор эпизодов внутри стадии; сумма ограничена ObserverTuning.PRESSURE_SYRINGE_MAX. Было 0.35 + 0.15. [гипотеза]
     */
    public static final float OBSERVER_BOOST_BASE = 0.15f;
    public static final float OBSERVER_BOOST_PER_LEVEL = 0.05f;
}
