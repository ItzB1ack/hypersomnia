package com.hypersomnia.client;

import com.hypersomnia.network.MadnessSyncPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** Данные для наблюдателя на клиенте. Без клиентских импортов, как ClientFatigueState (безопасно на сервере). */
public final class ObserverState {
    private ObserverState() {}

    public static int madness;
    public static int days;
    public static int memories;
    /** Стадия, заданная отладкой на сервере, или -1. */
    public static int stageOverride = -1;
    /** Копия памяти привычек с сервера (ObserverProgressData): маршрут x,y,z,вес и места сна x,y,z,счёт четвёрками. */
    public static int[] route = new int[0];
    public static int[] sleep = new int[0];
    /** Финал (FinaleTuning.pack с сервера): выбранная концовка (-1 = не пройден) и «Фрагмент услышал правду». */
    public static int ending = -1;
    public static boolean truth;

    /** Финал пройден: Фрагмент, сцены и сны выключены (сервер решает так же, FinaleLogic.completed). */
    public static boolean completed() {
        return ending >= 0;
    }

    /** Стадия осколка 0-5: от прожитых дней и найденных воспоминаний (или заданная отладкой). */
    public static int stage() {
        if (stageOverride >= 0) return stageOverride;
        return com.hypersomnia.ObserverTuning.stage(days, Integer.bitCount(memories));
    }

    /** Самое частое место сна {x, y, z} или null. */
    public static int[] home() {
        int best = -1;
        int bestCount = 0;
        for (int i = 0; i + 3 < sleep.length; i += 4) {
            if (sleep[i + 3] > bestCount) {
                bestCount = sleep[i + 3];
                best = i;
            }
        }
        return best < 0 ? null : new int[]{sleep[best], sleep[best + 1], sleep[best + 2]};
    }

    public static void handleStage(com.hypersomnia.network.ObserverStagePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            days = payload.days();
            memories = payload.memories();
            stageOverride = payload.stageOverride();
            route = toArray(payload.route());
            sleep = toArray(payload.sleep());
            ending = com.hypersomnia.finale.FinaleTuning.endingOf(payload.finale());
            truth = com.hypersomnia.finale.FinaleTuning.truthOf(payload.finale());
        });
    }

    private static int[] toArray(List<Integer> list) {
        int[] a = new int[list.size() - list.size() % 4];
        for (int i = 0; i < a.length; i++) a[i] = list.get(i);
        return a;
    }

    public static void handleMadness(MadnessSyncPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> madness = payload.madness());
    }

    public static void reset() {
        madness = 0;
        days = 0;
        memories = 0;
        stageOverride = -1;
        route = new int[0];
        sleep = new int[0];
        ending = -1;
        truth = false;
        ObserverScare.reset();
        ObserverDirector.reset();
    }
}
