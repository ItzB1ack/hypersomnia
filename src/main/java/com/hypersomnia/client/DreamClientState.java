package com.hypersomnia.client;

import com.hypersomnia.dream.DreamDimension;
import net.minecraft.client.Minecraft;

/** Клиентское состояние мира снов: находимся ли мы в нём, счётчик «ленты» и затемнение при смене мира. */
public final class DreamClientState {
    private DreamClientState() {}

    /** Сколько тиков длится затемнение при входе и выходе. */
    public static final int FADE_TICKS = 50;

    public static int ticksInDream;
    public static int fadeTicks;

    private static Object lastLevel;
    private static boolean wasDream;

    public static boolean isDream(Minecraft mc) {
        return mc.level != null && mc.level.dimension() == DreamDimension.LEVEL;
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }
        if (mc.isPaused()) return;

        boolean dream = isDream(mc);
        if (lastLevel != mc.level) {
            // Сменился мир: если это переход между реальным миром и сном, открываем глаза из темноты
            if (lastLevel != null && dream != wasDream) fadeTicks = FADE_TICKS;
            lastLevel = mc.level;
            wasDream = dream;
            ticksInDream = 0;
        }
        if (dream) ticksInDream++;
        if (fadeTicks > 0) fadeTicks--;
    }

    public static void reset() {
        ticksInDream = 0;
        fadeTicks = 0;
        lastLevel = null;
        wasDream = false;
    }
}
