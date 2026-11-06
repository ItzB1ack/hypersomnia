package com.hypersomnia.client;

import com.hypersomnia.network.SleepScenePayload;
import com.hypersomnia.scene.SleepSceneTuning;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Состояние сцены на клиенте. Без клиентских импортов (класс ссылается из регистрации пакета). */
public final class SleepSceneState {
    private SleepSceneState() {}

    /** -1 = сцена не идёт. Считает тики с самого начала, включая вступление. */
    public static int ticks = -1;
    public static int seed;

    /** Сколько тиков игрок просидел в меню паузы; сколько тиков ещё показывать укор; сколько раз его закрывали. */
    public static int pauseTicks;
    public static int scoldTicks;
    public static int scoldCount;
    /** Тик меню паузы, на котором он его закроет (0 — ещё не заговорил). */
    public static int pauseCloseAt;
    /** После автоматического закрытия меню повторная пауза запрещена до конца этой сцены. */
    public static boolean pauseLocked;

    /** Сколько тиков ещё держится полная тишина (сцена + хвост после неё). */
    private static int silenceTicks;

    // Визуальное сердцебиение: звука нет, поэтому пульс рисует картинка
    private static int beatAge = 1000;
    private static int beatCountdown;

    public static boolean active() { return ticks >= 0; }

    /** true, пока в игре нельзя проигрывать никакие звуки. */
    public static boolean silent() { return silenceTicks > 0; }

    /** Время тела сцены; отрицательное, пока идёт вступление. */
    public static int bodyTicks() { return ticks - SleepSceneTuning.LEAD_IN_TICKS; }

    public static void handle(SleepScenePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            seed = payload.seed();
            pauseLocked = false;
            ticks = 0;
            silenceTicks = SleepSceneTuning.SCENE_TICKS + SleepSceneTuning.SILENCE_TAIL_TICKS;
            beatAge = 1000;
            beatCountdown = 0;
        });
    }

    public static void tick() {
        if (silenceTicks > 0) silenceTicks--;
        if (scoldTicks > 0) scoldTicks--;
        if (ticks < 0) return;
        if (++ticks >= SleepSceneTuning.SCENE_TICKS) {
            ticks = -1;
            pauseLocked = false;
            return;
        }

        if (beatAge < 1000) beatAge++;
        int body = bodyTicks();
        // Сердце бьётся только в теле сцены, всё чаще, и ОСТАНАВЛИВАЕТСЯ вместе с замиранием кадра
        if (body >= 8 && body < SleepSceneTuning.BODY_TICKS - SleepSceneTuning.FREEZE_TICKS) {
            if (--beatCountdown <= 0) {
                beatAge = 0;
                float p = body / (float) SleepSceneTuning.BODY_TICKS;
                beatCountdown = Math.round(40f - 24f * p);
            }
        }
    }

    /** 0..1: сила удара пульса «тук-тук» в текущий момент. */
    public static float pulse(float partialTick) {
        float age = beatAge + partialTick;
        float lub = (float) Math.exp(-age / 3.0);
        float dub = age >= 5f ? 0.6f * (float) Math.exp(-(age - 5f) / 3.0) : 0f;
        return Math.min(1f, lub + dub);
    }

    public static void reset() {
        ticks = -1;
        silenceTicks = 0;
        beatAge = 1000;
        pauseTicks = 0;
        pauseCloseAt = 0;
        pauseLocked = false;
        scoldTicks = 0;
        scoldCount = 0;
    }
}
