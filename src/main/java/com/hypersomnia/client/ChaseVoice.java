package com.hypersomnia.client;

import com.hypersomnia.chase.ChaseTuning;
import com.hypersomnia.network.ChasePayload;
import com.hypersomnia.voice.VoiceLine;
import com.hypersomnia.voice.VoiceTuning;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Что осколок говорит в погоне (звук и субтитр — FragmentVoice). Дозированно: встал на след — одна реплика
 * (не всегда), на бегу — не больше VoiceTuning.RUN_MAX, у закрытой двери — просит впустить (из-за двери),
 * поймал — вплотную, «в голове», ушёл — вслед. Заметил его, пока он стоял на следе (он сразу срывается с места,
 * ChaseTuning.WARN_SEEN_TICKS), — «ты меня видишь». Реплики по ступени эскалации стадии.
 * Вызывает ObserverClient (begin/tick/end/reset) и ChaseClient (caught).
 */
public final class ChaseVoice {
    private ChaseVoice() {}

    private static final RandomSource RANDOM = RandomSource.create();

    private static int startIn = -1;
    private static int runIn;
    private static int runSaid;
    private static int blockedFor;
    private static boolean blockedSaid;
    private static boolean caughtSaid;
    private static VoiceLine lastRun;
    private static int lastPhase = -1;

    /** Он встал на след. */
    public static void begin() {
        startIn = RANDOM.nextFloat() < VoiceTuning.START_CHANCE ? VoiceTuning.START_DELAY : -1;
        runIn = VoiceTuning.RUN_FIRST;
        runSaid = 0;
        blockedFor = 0;
        blockedSaid = false;
        caughtSaid = false;
        lastPhase = -1;
    }

    /** Каждый тик погони: фаза ChasePayload.PH_*, где он (ноги) и расстояние до него. */
    public static void tick(LocalPlayer player, int phase, Vec3 at, double dist) {
        int stage = ObserverState.stage();
        boolean bolted = lastPhase == ChasePayload.PH_WARN && phase == ChasePayload.PH_RUN;
        lastPhase = phase;
        if (bolted && seen(player, at)) {
            startIn = -1;
            if (!FragmentVoice.speaking()) say(VoiceLine.STARED, player, at, dist);
        }
        if (startIn > 0 && --startIn == 0 && phase != ChasePayload.PH_CAUGHT) {
            say(VoiceLine.start(stage), player, at, dist);
        }
        if (phase == ChasePayload.PH_BLOCKED) {
            if (!blockedSaid && ++blockedFor >= VoiceTuning.BLOCKED_DELAY && !FragmentVoice.speaking()) {
                blockedSaid = true;
                say(VoiceLine.blocked(stage), player, at, dist);
            }
        } else {
            blockedFor = 0;
        }
        if (phase == ChasePayload.PH_RUN && runSaid < VoiceTuning.RUN_MAX && --runIn <= 0) {
            runIn = Mth.randomBetweenInclusive(RANDOM, VoiceTuning.RUN_MIN, VoiceTuning.RUN_MAX_GAP);
            if (!FragmentVoice.speaking(VoiceTuning.WHISPER_GAP) && dist < VoiceTuning.RUN_DIST) {
                VoiceLine[] pool = VoiceLine.run(stage);
                VoiceLine l = pool[RANDOM.nextInt(pool.length)];
                if (l == lastRun) l = pool[(l.ordinal() - pool[0].ordinal() + 1) % pool.length];
                lastRun = l;
                runSaid++;
                say(l, player, at, dist);
            }
        }
    }

    /** Поймал: вплотную, «в голове», в чёрном кадре. */
    public static void caught() {
        if (caughtSaid) return;
        caughtSaid = true;
        startIn = -1;
        FragmentVoice.say(VoiceLine.caught(ObserverState.stage()), null, VoiceTuning.VOLUME_NEAR,
                VoiceTuning.SHAKE_CAUGHT, false, false);
    }

    /** Сервер закончил погоню исходом ChasePayload.O_*: ушёл — «не оставляй» (O_STARED сервер больше не шлёт). */
    public static void end(int outcome, LocalPlayer player, Vec3 at) {
        startIn = -1;
        if (caughtSaid || FragmentVoice.speaking() || RANDOM.nextFloat() >= VoiceTuning.END_CHANCE) return;
        int stage = ObserverState.stage();
        VoiceLine l = switch (outcome) {
            case ChasePayload.O_ESCAPED -> VoiceLine.escape(stage);
            case ChasePayload.O_STARED -> VoiceLine.STARED;
            default -> null;
        };
        if (l != null) say(l, player, at, player.position().distanceTo(at));
    }

    /** Фигура убрана: больше ни слова до следующей погони (начатая реплика договаривается). */
    public static void reset() {
        startIn = -1;
        runSaid = VoiceTuning.RUN_MAX;
        blockedSaid = true;
    }

    /** Смотрит ли игрок на фигуру (как сервер: конус ChaseTuning.STARE_HALF_DEG на точки тела STARE_POINTS). */
    private static boolean seen(LocalPlayer player, Vec3 at) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1f);
        double cos = Math.cos(Math.toRadians(ChaseTuning.STARE_HALF_DEG));
        for (double off : ChaseTuning.STARE_POINTS) {
            Vec3 d = new Vec3(at.x - eye.x, at.y + off - eye.y, at.z - eye.z);
            double len = d.length();
            if (len > 1e-3 && look.dot(d) / len >= cos) return true;
        }
        return false;
    }

    /** С места фигуры (голова); вплотную — у самого уха, совсем далеко — тихо «в голове». */
    private static void say(VoiceLine l, LocalPlayer player, Vec3 at, double dist) {
        float shake = VoiceTuning.SHAKE_TIER[VoiceLine.tier(ObserverState.stage())];
        if (dist > VoiceTuning.FAR_HEAD_DIST) {
            FragmentVoice.say(l, null, VoiceTuning.VOLUME_FAR_HEAD, shake, false, false);
            return;
        }
        Vec3 src = new Vec3(at.x, at.y + 1.6, at.z);
        if (dist < VoiceTuning.HEAD_DIST) {
            float a = RANDOM.nextFloat() * Mth.TWO_PI;
            Vec3 eye = player.getEyePosition();
            src = new Vec3(eye.x + Mth.cos(a) * VoiceTuning.HEAD_OFFSET, eye.y, eye.z + Mth.sin(a) * VoiceTuning.HEAD_OFFSET);
        }
        FragmentVoice.say(l, src, VoiceTuning.VOLUME_FAR, shake, false, false);
    }
}
