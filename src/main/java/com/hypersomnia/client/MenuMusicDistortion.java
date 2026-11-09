package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * [новое] Музыка главного меню играет «через плохую ленту»: ниже и медленнее оригинала, высота
 * медленно гуляет, иногда лента «проскакивает» и громкость на пару тиков проваливается. Ванильный
 * MusicManager не умеет менять высоту, поэтому его SimpleSoundInstance подменяется в PlaySoundEvent
 * на DistortedMenuMusic (AbstractTickableSoundInstance): SoundEngine каждый тик перечитывает у неё
 * громкость и высоту. Повторы MusicManager (он не знает о подмене и считает трек неиграющим)
 * глушатся, пока наша лента активна; при входе в мир или смене музыки она останавливается.
 * Числа — ГИПОТЕЗА на слух.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class MenuMusicDistortion {
    private MenuMusicDistortion() {}

    /** Событие ванильной музыки меню: minecraft:music.menu. */
    private static final ResourceLocation MENU_ID =
            ResourceLocation.fromNamespaceAndPath("minecraft", "music.menu");

    /** Базовая высота: заметно ниже оригинала, но трек остаётся узнаваемым. */
    private static final float BASE_PITCH = 0.72f;
    /** Медленное «гуляние» высоты: две синусоиды с разными периодами (тики). */
    private static final float WOBBLE = 0.035f;
    private static final float WOBBLE_TICKS = 92f;
    private static final float WOBBLE2 = 0.016f;
    private static final float WOBBLE2_TICKS = 37f;
    /** Редкий проскок ленты: шанс 1/N за тик, длительность и множитель высоты. */
    private static final int SLIP_CHANCE = 900;
    private static final int SLIP_MIN = 6;
    private static final int SLIP_MAX = 18;
    /** Провал громкости: шанс 1/N за тик, длительность и уровень. */
    private static final int DROPOUT_CHANCE = 700;
    private static final int DROPOUT_MIN = 3;
    private static final int DROPOUT_MAX = 10;
    private static final float DROPOUT_VOLUME = 0.15f;
    private static final float BASE_VOLUME = 1.0f;

    private static DistortedMenuMusic current;

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || sound.getSource() != SoundSource.MUSIC) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null || mc.player != null) return;
        if (!sound.getLocation().equals(MENU_ID)) return;
        // MusicManager после подмены считает трек неиграющим и пробует запустить его снова —
        // пока наша лента звучит, эти повторы глушим.
        if (current != null && mc.getSoundManager().isActive(current)) {
            event.setSound(null);
            return;
        }
        current = new DistortedMenuMusic();
        event.setSound(current);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (current == null) return;
        Minecraft mc = Minecraft.getInstance();
        boolean menu = mc.level == null && mc.player == null
                && mc.getSituationalMusic().getEvent().value().getLocation().equals(MENU_ID);
        if (!menu || !mc.getSoundManager().isActive(current)) {
            mc.getSoundManager().stop(current);
            current = null;
        }
    }

    /** Одна «лента» меню: замедленная высота гуляет, иногда проскакивает и проваливается по громкости. */
    private static final class DistortedMenuMusic extends AbstractTickableSoundInstance {
        private final RandomSource random = SoundInstance.createUnseededRandom();
        private int ticks;
        private int slip;
        private int dropout;
        private float slipPitch = 1f;

        private DistortedMenuMusic() {
            super(SoundEvents.MUSIC_MENU.value(), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.volume = BASE_VOLUME;
            this.pitch = BASE_PITCH;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public void tick() {
            ticks++;
            if (dropout <= 0 && random.nextInt(DROPOUT_CHANCE) == 0) {
                dropout = DROPOUT_MIN + random.nextInt(DROPOUT_MAX - DROPOUT_MIN + 1);
            }
            if (slip <= 0 && random.nextInt(SLIP_CHANCE) == 0) {
                slip = SLIP_MIN + random.nextInt(SLIP_MAX - SLIP_MIN + 1);
                slipPitch = 0.90f + random.nextFloat() * 0.16f;
            }
            float wobble = WOBBLE * Mth.sin(ticks / WOBBLE_TICKS)
                    + WOBBLE2 * Mth.sin(ticks / WOBBLE2_TICKS + 1.7f);
            float slipMul = 1f;
            if (slip > 0) {
                slip--;
                slipMul = slipPitch;
            }
            pitch = (BASE_PITCH + wobble) * slipMul;
            if (dropout > 0) {
                dropout--;
                volume = DROPOUT_VOLUME;
            } else {
                volume = BASE_VOLUME * (1f + 0.03f * Mth.sin(ticks / 53f));
            }
        }
    }
}
