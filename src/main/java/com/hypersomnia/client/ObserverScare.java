package com.hypersomnia.client;

import com.hypersomnia.ObserverTuning;

import java.util.Random;

/**
 * Кадр-сюрприз осколка в мире снов: фаза реакции внутри эпизода GHOST (её разрешает план режиссёра).
 * Состояние и выбор строки, без клиентских импортов Minecraft. Ключи lang: hypersomnia.observer.scare.0-5
 * (запасной текст ниже). Стадии 2-4: вопросы (0-2); стадия 5: обвинение (3-5). Одна строка не идёт два раза подряд.
 */
public final class ObserverScare {
    private ObserverScare() {}

    public static final String[] LINES = {"What am I?", "Is this my fault?", "Why can't I sleep?",
            "How can you sleep?", "You forgot.", "Why do you get to forget?"};
    /** Первая строка обвинения и число строк в каждой группе. */
    public static final int ACCUSE_FIRST = 3;
    public static final int GROUP = 3;
    private static final Random RANDOM = new Random();

    /** Сколько тиков кадр ещё должен показываться (0 = не идёт). */
    public static int ticks;
    public static int line;
    public static int stage;
    private static int cooldown;
    private static int last = -1;

    public static boolean active() { return ticks > 0; }

    /** Запустить кадр; false — уже идёт другой или не вышел кулдаун. */
    public static boolean trigger(int stageNow) {
        if (ticks > 0 || cooldown > 0) return false;
        ticks = ObserverTuning.SCARE_TICKS;
        cooldown = ObserverTuning.SCARE_COOLDOWN_TICKS;
        line = pick(stageNow);
        last = line;
        stage = stageNow;
        return true;
    }

    /** Индекс строки для стадии: группа по стадии, без повтора прошлой строки. */
    public static int pick(int stageNow) {
        int first = stageNow >= ObserverTuning.SCARE_ACCUSE_STAGE ? ACCUSE_FIRST : 0;
        int i = first + RANDOM.nextInt(GROUP);
        if (i == last) i = first + (i - first + 1 + RANDOM.nextInt(GROUP - 1)) % GROUP;
        return i;
    }

    public static void tick() {
        if (ticks > 0) ticks--;
        if (cooldown > 0) cooldown--;
    }

    public static void reset() {
        ticks = 0;
        cooldown = 0;
        last = -1;
    }
}
