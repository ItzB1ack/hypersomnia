package com.hypersomnia.client;

import com.hypersomnia.ModSounds;
import com.hypersomnia.document.TapeTuning;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * Шипение ленты, пока экран кассеты проигрывает запись: петля без позиции в мире (как звук интерфейса).
 * На провале записи стихает. Гаснет сама, если экран кассеты больше не открыт.
 */
final class TapeHiss extends AbstractTickableSoundInstance {
    private final Screen owner;

    TapeHiss(Screen owner) {
        super(ModSounds.TAPE_HISS.get(), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.owner = owner;
        this.looping = true;
        this.delay = 0;
        this.volume = TapeTuning.HISS_VOLUME;
        this.pitch = 1.0f;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
    }

    /** true: провал записи, шипение тише. */
    void duck(boolean on) {
        volume = on ? TapeTuning.HISS_VOLUME * TapeTuning.DROPOUT_HISS_FACTOR : TapeTuning.HISS_VOLUME;
    }

    void halt() {
        stop();
    }

    @Override
    public void tick() {
        if (Minecraft.getInstance().screen != owner || SleepSceneState.silent()) stop();
    }
}
