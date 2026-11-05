package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModParticles;
import com.hypersomnia.ObserverTuning;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Дыхание на холоде: слышно тихий вдох, потом выдох «хааа» - и в этот же момент изо рта идёт струя пара
 * (частица hypersomnia:breath, BreathParticle): сначала густая и быстрая, к концу слабее и медленнее.
 * Холодно: ночь, снежный биом или высота (мир снов - всегда ночь, значит всегда).
 * Только клиент, чисто визуально, никакой серверной логики.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class BreathClient {
    private BreathClient() {}

    private static final RandomSource RANDOM = RandomSource.create();
    private static int cooldown = 40;
    /** Тиков до выдоха (идёт вдох); 0 - вдоха нет. */
    private static int inhaleLeft = 0;
    /** Тиков струи пара осталось. */
    private static int puffLeft = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            reset();
            return;
        }
        if (mc.isPaused()) return;
        tickPuff(mc, player);
        if (--cooldown > 0) return;
        if (!isEligible(mc, player) || !isCold(mc, player)) {
            cooldown = 20;
            return;
        }
        breathe(mc, player);
        cooldown = Mth.randomBetweenInclusive(RANDOM,
                ObserverTuning.BREATH_INTERVAL_MIN, ObserverTuning.BREATH_INTERVAL_MAX);
    }

    private static boolean isEligible(Minecraft mc, LocalPlayer player) {
        if (player.isSleeping() || mc.screen != null) return false;
        if (SleepSceneState.active() || SleepSceneState.silent()) return false;
        return player.isAlive() && !player.isUnderWater();
    }

    /** Холодно ли: ночь, снежный биом или высота. Мир снов — всегда ночь, значит всегда. */
    private static boolean isCold(Minecraft mc, LocalPlayer player) {
        long time = mc.level.getDayTime() % 24000L;
        if (time >= 13000L && time < 23000L) return true;
        if (player.getY() > ObserverTuning.BREATH_COLD_Y) return true;
        BlockPos pos = player.blockPosition();
        return mc.level.getBiome(pos).value().coldEnoughToSnow(pos);
    }

    /** Вдох-выдох: звук сразу, пар - когда в звуке начинается выдох (питч сдвигает это время). */
    private static void breathe(Minecraft mc, LocalPlayer player) {
        float pitch = ObserverTuning.BREATH_SOUND_PITCH
                * (1f + (RANDOM.nextFloat() * 2f - 1f) * ObserverTuning.BREATH_SOUND_PITCH_SPREAD);
        Vec3 mouth = mouth(player);
        mc.level.playLocalSound(mouth.x, mouth.y, mouth.z,
                com.hypersomnia.ModSounds.BREATH_EXHALE.get(), SoundSource.PLAYERS,
                ObserverTuning.BREATH_SOUND_VOLUME, pitch, false);
        inhaleLeft = Math.max(1, Math.round(ObserverTuning.BREATH_INHALE_TICKS / pitch));
        puffLeft = 0;
    }

    /** Ведёт вдох и струю пара: каждый тик несколько частиц изо рта, сила струи падает к концу выдоха. */
    private static void tickPuff(Minecraft mc, LocalPlayer player) {
        if (inhaleLeft > 0 && --inhaleLeft == 0) puffLeft = ObserverTuning.BREATH_PUFF_TICKS;
        if (puffLeft <= 0) return;
        if (!player.isAlive() || player.isSleeping() || player.isUnderWater()) {
            puffLeft = 0;
            return;
        }
        float k = puffLeft / (float) ObserverTuning.BREATH_PUFF_TICKS;          // 1 -> 0 к концу выдоха
        int n = k > 0.5f ? ObserverTuning.BREATH_PUFF_PER_TICK : Math.max(1, ObserverTuning.BREATH_PUFF_PER_TICK / 2);
        Vec3 mouth = mouth(player);
        Vec3 look = player.getLookAngle();
        Vec3 move = player.getDeltaMovement();
        for (int i = 0; i < n; i++) {
            double speed = ObserverTuning.BREATH_PUFF_SPEED * (0.35 + 0.65 * k) * (0.85 + 0.3 * RANDOM.nextDouble());
            double vx = look.x * speed + (RANDOM.nextDouble() - 0.5) * 0.012 + move.x * 0.8;
            double vy = (look.y - ObserverTuning.BREATH_PUFF_DOWN) * speed + (RANDOM.nextDouble() - 0.5) * 0.008;
            double vz = look.z * speed + (RANDOM.nextDouble() - 0.5) * 0.012 + move.z * 0.8;
            mc.level.addParticle(ModParticles.BREATH.get(),
                    mouth.x + (RANDOM.nextDouble() - 0.5) * 0.04,
                    mouth.y + (RANDOM.nextDouble() - 0.5) * 0.03,
                    mouth.z + (RANDOM.nextDouble() - 0.5) * 0.04, vx, vy, vz);
        }
        puffLeft--;
    }

    /** Рот: чуть впереди глаз по взгляду и ниже. */
    private static Vec3 mouth(LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        return new Vec3(eye.x + look.x * ObserverTuning.BREATH_MOUTH_FORWARD,
                eye.y - ObserverTuning.BREATH_MOUTH_DOWN + look.y * ObserverTuning.BREATH_MOUTH_FORWARD,
                eye.z + look.z * ObserverTuning.BREATH_MOUTH_FORWARD);
    }

    /** Ручной запуск вдоха-выдоха (для /hypersomnia_observer breath и начала погони). */
    public static boolean triggerNow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        breathe(mc, mc.player);
        return true;
    }

    private static void reset() {
        cooldown = 40;
        inhaleLeft = 0;
        puffLeft = 0;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }
}
