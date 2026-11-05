package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ObserverTuning;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Дыхание: короткий пар изо рта. Ночь, снежный биом или высота — виден всегда.
 * Только клиент, чисто визуально, никакой серверной логики.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class BreathClient {
    private BreathClient() {}

    private static final RandomSource RANDOM = RandomSource.create();
    private static final net.minecraft.core.particles.SimpleParticleType PARTICLE =
            net.minecraft.core.particles.ParticleTypes.CLOUD;
    private static int cooldown = 40;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused()) {
            cooldown = 40;
            return;
        }
        if (--cooldown > 0) return;
        if (!isEligible(mc, player) || !isCold(mc, player)) {
            cooldown = 20;
            return;
        }
        spawnBurst(mc, player);
        cooldown = Mth.randomBetweenInclusive(RANDOM,
                ObserverTuning.BREATH_INTERVAL_MIN, ObserverTuning.BREATH_INTERVAL_MAX);
    }

    private static boolean isEligible(Minecraft mc, LocalPlayer player) {
        if (player.isSleeping() || mc.screen != null) return false;
        if (SleepSceneState.active() || SleepSceneState.silent()) return false;
        return player.isAlive();
    }

    /** Холодно ли: ночь, снежный биом или высота. Мир снов — всегда ночь, значит всегда. */
    private static boolean isCold(Minecraft mc, LocalPlayer player) {
        long time = mc.level.getDayTime() % 24000L;
        if (time >= 13000L && time < 23000L) return true;
        if (player.getY() > ObserverTuning.BREATH_COLD_Y) return true;
        BlockPos pos = player.blockPosition();
        return mc.level.getBiome(pos).value().coldEnoughToSnow(pos);
    }

    /** Пар изо рта: несколько рыхлых белых точек в направлении взгляда. */
    private static void spawnBurst(Minecraft mc, LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double mx = eye.x + look.x * 0.25;
        double my = eye.y - 0.14 + look.y * 0.25;
        double mz = eye.z + look.z * 0.25;
        for (int i = 0; i < ObserverTuning.BREATH_PARTICLES; i++) {
            double jx = (RANDOM.nextDouble() - 0.5) * 0.10;
            double jy = (RANDOM.nextDouble() - 0.5) * 0.08;
            double jz = (RANDOM.nextDouble() - 0.5) * 0.10;
            double vx = look.x * 0.03 + (RANDOM.nextDouble() - 0.5) * 0.01;
            double vy = look.y * 0.03 + (RANDOM.nextDouble() - 0.5) * 0.01 - 0.004;
            double vz = look.z * 0.03 + (RANDOM.nextDouble() - 0.5) * 0.01;
            mc.level.addParticle(PARTICLE, mx + jx, my + jy, mz + jz, vx, vy, vz);
        }
        // Один звук на выдох (раньше звучал на каждую частицу)
        mc.level.playLocalSound(mx, my, mz,
                com.hypersomnia.ModSounds.BREATH_EXHALE.get(), SoundSource.PLAYERS,
                ObserverTuning.BREATH_SOUND_VOLUME,
                ObserverTuning.BREATH_SOUND_PITCH, false);
    }

    /** Ручной запуск выдоха (для /hypersomnia_observer breath). */
    public static boolean triggerNow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        spawnBurst(mc, mc.player);
        return true;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        cooldown = 40;
    }
}