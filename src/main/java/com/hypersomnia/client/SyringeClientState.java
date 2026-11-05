package com.hypersomnia.client;

import com.hypersomnia.network.SyringeSyncPayload;
import com.hypersomnia.syringe.SyringeTuning;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиентское состояние шприца. Без клиентских импортов (ссылается из регистрации пакета). */
public final class SyringeClientState {
    private SyringeClientState() {}

    public static boolean active;
    public static int wakeTicks;
    public static int level;

    public static void handle(SyringeSyncPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            wakeTicks = p.wakeTicks();
            level = p.level();
            active = wakeTicks > 0;
        });
    }

    public static void tick() {
        if (wakeTicks > 0 && --wakeTicks <= 0) active = false;
    }

    /** 0..1: предупреждающая дрожь в последние секунды. */
    public static float warning() {
        if (!active || wakeTicks > SyringeTuning.WARN_TICKS) return 0f;
        return 1f - wakeTicks / (float) SyringeTuning.WARN_TICKS;
    }

    /** Прибавка к давлению наблюдателя. */
    public static float observerBoost() {
        return active ? SyringeTuning.OBSERVER_BOOST_BASE + SyringeTuning.OBSERVER_BOOST_PER_LEVEL * level : 0f;
    }

    public static void reset() {
        active = false;
        wakeTicks = 0;
        level = 0;
    }
}
