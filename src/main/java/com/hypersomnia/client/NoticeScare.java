package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModSounds;
import com.hypersomnia.ObserverTuning;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * [новое] «Заметил во сне» на стадии 4+ — резкий кадр, но не такой, как засада. Засада трясёт камеру
 * случайно; здесь кадр на короткий миг «влечёт» к лицу: FOV сжимается, камера плавно наклоняется,
 * в ухо бьют замедленный шёпот, обрыв ленты и выдох. Визуал рисует ObserverScareOverlay
 * (белая вспышка, наезд глаз, полосы развёртки). Своё состояние не хранит: живёт по ObserverScare.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class NoticeScare {
    private NoticeScare() {}

    /** Звуки в момент, когда ObserverScare.trigger реально запустил кадр. */
    public static void trigger() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        var p = mc.player;
        mc.level.playLocalSound(p.getX(), p.getEyeY(), p.getZ(), ModSounds.SHARD_WHISPER.get(),
                SoundSource.HOSTILE, ObserverTuning.SCARE_HARD_WHISPER_VOLUME,
                ObserverTuning.SCARE_HARD_WHISPER_PITCH, false);
        mc.level.playLocalSound(p.getX(), p.getEyeY(), p.getZ(), ModSounds.TAPE_DROPOUT.get(),
                SoundSource.MASTER, ObserverTuning.SCARE_HARD_TAPE_VOLUME,
                ObserverTuning.SCARE_HARD_TAPE_PITCH, false);
        mc.level.playLocalSound(p.getX(), p.getEyeY(), p.getZ(), ModSounds.BREATH_EXHALE.get(),
                SoundSource.PLAYERS, ObserverTuning.SCARE_HARD_BREATH_VOLUME,
                ObserverTuning.SCARE_HARD_BREATH_PITCH, false);
    }

    private static boolean hard() {
        return ObserverScare.active() && ObserverScare.stage >= ObserverTuning.SCARE_HARD_MIN_STAGE;
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (!hard()) return;
        float k = ObserverScare.ticks / (float) Math.max(1, ObserverTuning.SCARE_TICKS);
        event.setPitch(event.getPitch() + ObserverTuning.SCARE_HARD_PITCH * k);
        event.setRoll(event.getRoll() + ObserverTuning.SCARE_HARD_ROLL * k);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (!hard()) return;
        int e = Math.max(0, ObserverTuning.SCARE_TICKS - ObserverScare.ticks);
        if (e >= ObserverTuning.SCARE_HARD_FOV_TICKS) return;
        float k = 1f - e / (float) ObserverTuning.SCARE_HARD_FOV_TICKS;
        event.setFOV(event.getFOV() * (1f - (1f - ObserverTuning.SCARE_HARD_FOV) * k));
    }
}
