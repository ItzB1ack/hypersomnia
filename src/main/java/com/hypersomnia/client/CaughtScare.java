package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModSounds;
import com.hypersomnia.chase.CaughtTuning;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * [новое] Скример поимки: осколок догнал игрока. ChaseClient.caught вызывает trigger() после
 * hushWorld, поэтому звук не глушится тишиной эпизода. Визуал рисует CaughtScareOverlay поверх
 * чёрного кадра; голос осколка (ChaseVoice.caught) идёт следом и не заглушается — вспышка
 * короткая, а кадр гаснет задолго до конца чёрной паузы.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class CaughtScare {
    private CaughtScare() {}

    private static int ticks;

    public static boolean active() {
        return ticks > 0;
    }

    /** Тики с начала кадра (0..SCARE_TICKS). */
    public static int elapsed() {
        return CaughtTuning.SCARE_TICKS - ticks;
    }

    public static void trigger(Minecraft mc) {
        ticks = CaughtTuning.SCARE_TICKS;
        if (mc.player == null || mc.level == null) return;
        var p = mc.player;
        mc.level.playLocalSound(p.getX(), p.getEyeY(), p.getZ(), ModSounds.SHARD_WHISPER.get(),
                SoundSource.HOSTILE, CaughtTuning.WHISPER_VOLUME, CaughtTuning.WHISPER_PITCH, false);
        mc.level.playLocalSound(p.getX(), p.getEyeY(), p.getZ(), ModSounds.TAPE_DROPOUT.get(),
                SoundSource.MASTER, CaughtTuning.TAPE_VOLUME, CaughtTuning.TAPE_PITCH, false);
        mc.level.playLocalSound(p.getX(), p.getEyeY(), p.getZ(), ModSounds.BREATH_EXHALE.get(),
                SoundSource.PLAYERS, CaughtTuning.BREATH_VOLUME, CaughtTuning.BREATH_PITCH, false);
        mc.level.playLocalSound(p.getX(), p.getEyeY(), p.getZ(), ModSounds.HEARTBEAT.get(),
                SoundSource.PLAYERS, CaughtTuning.HEART_VOLUME, CaughtTuning.HEART_PITCH, false);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // Идёт и в паузе: иначе кадр зависнет, если игрок успел нажать ESC.
        if (ticks > 0) ticks--;
    }

    public static void reset() {
        ticks = 0;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }
}
