package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.chase.AmbushTuning;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * [новое] Клиентская часть засады: короткий кадр-испуг и дрожь камеры в момент, когда фигура уже стоит вплотную.
 * Сервер присылает ChasePayload с флагом I_AMBUSH; ObserverClient вызывает trigger. Состояние только клиентское.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class AmbushClient {
    private AmbushClient() {}

    private static final RandomSource RANDOM = RandomSource.create();

    private static int ticks;
    private static int stage;

    public static void trigger(int stageNow) {
        ticks = AmbushTuning.SCARE_TICKS;
        stage = stageNow;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        mc.level.playLocalSound(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(),
                SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE,
                AmbushTuning.SCARE_SOUND_VOLUME, AmbushTuning.SCARE_SOUND_PITCH, false);
        mc.level.playLocalSound(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(),
                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS,
                AmbushTuning.SCARE_GLASS_VOLUME, AmbushTuning.SCARE_GLASS_PITCH, false);
    }

    public static boolean active() {
        return ticks > 0;
    }

    /** Сколько тиков кадра-испуга осталось. */
    public static int ticks() {
        return ticks;
    }

    /** Тики с начала кадра (0..SCARE_TICKS). */
    public static int elapsed() {
        return AmbushTuning.SCARE_TICKS - ticks;
    }

    public static int stage() {
        return stage;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // Кадр-испуг идёт и в паузе: иначе чёрный экран зависнет, если игрок успел нажать ESC.
        if (ticks > 0) ticks--;
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (ticks <= 0) return;
        int e = elapsed();
        if (e >= AmbushTuning.SCARE_SHAKE_TICKS) return;
        float k = 1f - e / (float) Math.max(1, AmbushTuning.SCARE_SHAKE_TICKS);
        event.setYaw(event.getYaw() + (RANDOM.nextFloat() * 2f - 1f) * AmbushTuning.SCARE_SHAKE_YAW * k);
        event.setPitch(event.getPitch() + (RANDOM.nextFloat() * 2f - 1f) * AmbushTuning.SCARE_SHAKE_PITCH * k);
        event.setRoll(event.getRoll() + (RANDOM.nextFloat() * 2f - 1f) * AmbushTuning.SCARE_SHAKE_ROLL * k);
    }

    public static void reset() {
        ticks = 0;
        stage = 0;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }
}
