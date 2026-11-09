package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModSounds;
import com.hypersomnia.chase.ChaseTuning;
import com.hypersomnia.network.ChasePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Тело игрока в погоне (только клиент): паника 0..1, сердцебиение «в голове» (чаще и громче с паникой: ~70 -> ~126
 * ударов в минуту, интервал и высота каждого удара чуть гуляют, как у живого), пульс виньетки в такт ударам и чёрный
 * кадр поимки. Цель паники каждый тик погони задаёт ObserverClient (track);
 * после конца паника спадает за PANIC_FADE_TICKS и сердце успокаивается само. Рисует ChaseOverlay.
 * Сердцебиение — категория «Игроки» (громкость настраивается), без затухания по расстоянию.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class ChaseClient {
    private ChaseClient() {}

    private static final RandomSource RANDOM = RandomSource.create();
    private static boolean active;
    /** Пауза уже была принудительно закрыта в этой погоне. */
    private static boolean pauseLocked;
    private static float target;
    private static float panic;
    private static float panicO;
    /** Удар сердца: 1 в тик удара, дальше гаснет (BEAT_DECAY за тик). */
    private static float pulse;
    private static float pulseO;
    /** Тики до удара (дробные: остаток переходит в следующий интервал). */
    private static float beatIn;
    /** Чёрный кадр поимки: тики с начала, -1 — нет. */
    private static int black = -1;
    private static int rageTicks;

    /** Погоня началась (он стоит на следе): сердце начинает биться сразу. */
    public static void begin() {
        pauseLocked = false;
        active = true;
        rageTicks = 0;
        target = Math.max(target, ChaseTuning.PANIC_WARN);
        if (panic < ChaseTuning.HEART_MIN_PANIC) panic = ChaseTuning.HEART_MIN_PANIC;
        beatIn = Math.min(beatIn, 1f);
        ChaseText.begin();
    }

    /** Каждый тик погони: фаза ChasePayload.PH_* и расстояние до него (блоки). */
    public static void track(int phase, double dist) {
        if (!active) return;
        target = switch (phase) {
            case ChasePayload.PH_WARN -> ChaseTuning.PANIC_WARN;
            case ChasePayload.PH_BLOCKED -> ChaseTuning.PANIC_BLOCKED;
            case ChasePayload.PH_CAUGHT -> 1f;
            default -> {
                double k = (dist - ChaseTuning.PANIC_NEAR) / (ChaseTuning.PANIC_FAR - ChaseTuning.PANIC_NEAR);
                yield Math.max(ChaseTuning.PANIC_RUN_MIN, 1f - (float) Mth.clamp(k, 0.0, 1.0));
            }
        };
        if (ObserverState.stage() >= 5) target = Math.max(target, ChaseTuning.RAGE_MIN_PANIC);
    }

    /** Индекс краткой реплики ярости или -1. Не меняет урон и не ломает правило взгляда. */
    public static int rageLine() {
        if (!active || ObserverState.stage() < 5 || rageTicks < ChaseTuning.RAGE_FIRST_TICK || black >= 0) return -1;
        int elapsed = rageTicks - ChaseTuning.RAGE_FIRST_TICK;
        return elapsed % ChaseTuning.RAGE_PERIOD_TICKS < ChaseTuning.RAGE_TEXT_TICKS
                ? (elapsed / ChaseTuning.RAGE_PERIOD_TICKS) % ChaseTuning.RAGE_TEXT_VARIANTS : -1;
    }

    /**
     * [новое] Засада: фигура уже вплотную. Паника сразу максимальная, сердце бьётся в полную силу;
     * дальше обычный track() погони плавно снижает её по расстоянию.
     */
    public static void ambush() {
        if (!active) return;
        target = 1f;
        panic = 1f;
        beatIn = Math.min(beatIn, 1f);
    }

    /** Поймал: мир смолкает, один сильный удар и чёрный кадр. */
    public static void caught(Minecraft mc) {
        if (black >= 0) return;
        black = 0;
        target = 1f;
        panic = 1f;
        ObserverClient.hushWorld(mc);
        CaughtScare.trigger(mc);
        ChaseMusic.caught();
        ChaseVoice.caught();
        beat(mc, false);
    }

    /** Погоня кончилась любым исходом: паника спадает сама, чёрный кадр (если был) доигрывает. */
    public static void end() {
        active = false;
        pauseLocked = false;
        target = 0f;
        ChaseText.end();
    }

    public static boolean active() {
        return active;
    }

    /** Запрещаем повторное открытие паузы до конца текущей погони. */
    public static void lockPause() {
        if (active) pauseLocked = true;
    }

    public static boolean pauseLocked() {
        return active && pauseLocked;
    }

    /** Паника сейчас 0..1 (рой текста: чем она выше, тем больше строк и сильнее дрожь). */
    public static float panicNow() {
        return panic;
    }

    /** Удар сердца 0..1 (1 в тик удара, дальше гаснет). */
    public static float pulse(float partial) {
        return Mth.lerp(partial, pulseO, pulse);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }
        if (mc.isPaused()) return;
        if (active && rageTicks < Integer.MAX_VALUE) rageTicks++;
        panicO = panic;
        pulseO = pulse;
        pulse *= ChaseTuning.BEAT_DECAY;
        int blackEnd = ChaseTuning.BLACK_IN + ChaseTuning.BLACK_HOLD + ChaseTuning.BLACK_OUT;
        if (black >= 0) {
            // Умер (урон поимки на поздней стадии): кадр не держим, у экрана смерти свой
            if (!mc.player.isAlive()) black = Math.max(black, ChaseTuning.BLACK_IN + ChaseTuning.BLACK_HOLD);
            if (++black > blackEnd) black = -1;
        }
        if (!mc.player.isAlive()) {
            active = false;
            target = 0f;
        }
        if (panic < target) panic = Math.min(target, panic + ChaseTuning.PANIC_RISE);
        else panic = Math.max(target, panic - 1f / ChaseTuning.PANIC_FADE_TICKS);

        boolean hold = black >= 0 && black < ChaseTuning.BLACK_IN + ChaseTuning.BLACK_HOLD;
        if (panic < ChaseTuning.HEART_MIN_PANIC || hold) return; // в темноте поимки — тишина после удара
        if ((beatIn -= 1f) <= 0f) beat(mc, true);
    }

    /** Удар; inRhythm — по расписанию (дробный остаток идёт в следующий интервал), иначе отсчёт заново. */
    private static void beat(Minecraft mc, boolean inRhythm) {
        float p = Mth.clamp(panic, 0f, 1f);
        float volume = Mth.lerp(p, ChaseTuning.HEART_VOLUME_MIN, ChaseTuning.HEART_VOLUME_MAX);
        float pitch = Mth.lerp(p, ChaseTuning.HEART_PITCH_MIN, ChaseTuning.HEART_PITCH_MAX)
                * (1f + (RANDOM.nextFloat() * 2f - 1f) * ChaseTuning.HEART_PITCH_JITTER);
        mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.HEARTBEAT.get().getLocation(), SoundSource.PLAYERS,
                volume, pitch, SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE,
                0.0, 0.0, 0.0, true));
        pulse = 1f;
        float interval = Mth.lerp(p, ChaseTuning.HEART_SLOW_TICKS, ChaseTuning.HEART_FAST_TICKS)
                * (1f + (RANDOM.nextFloat() * 2f - 1f) * ChaseTuning.HEART_TIME_JITTER);
        beatIn = inRhythm ? Math.max(beatIn, -1f) + interval : interval;
    }

    /** Сила виньетки 0..1: растёт с паникой и вздрагивает на каждом ударе. */
    public static float vignette(float partial) {
        float p = Mth.lerp(partial, panicO, panic);
        if (p < ChaseTuning.HEART_MIN_PANIC) return 0f;
        float b = Mth.lerp(partial, pulseO, pulse);
        return Mth.clamp(p * (ChaseTuning.VIGNETTE_BASE + ChaseTuning.VIGNETTE_PULSE * b), 0f, 1f);
    }

    /** Непрозрачность чёрного кадра 0..1. */
    public static float blackout(float partial) {
        if (black < 0) return 0f;
        float t = black + partial;
        if (t < ChaseTuning.BLACK_IN) return t / ChaseTuning.BLACK_IN;
        t -= ChaseTuning.BLACK_IN;
        if (t < ChaseTuning.BLACK_HOLD) return 1f;
        t -= ChaseTuning.BLACK_HOLD;
        return Mth.clamp(1f - t / ChaseTuning.BLACK_OUT, 0f, 1f);
    }

    public static void reset() {
        active = false;
        pauseLocked = false;
        target = 0f;
        panic = 0f;
        panicO = 0f;
        pulse = 0f;
        pulseO = 0f;
        beatIn = 0f;
        black = -1;
        rageTicks = 0;
        ChaseText.reset();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }
}
