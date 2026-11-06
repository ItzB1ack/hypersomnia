package com.hypersomnia.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModSounds;
import com.hypersomnia.voice.VoiceLine;
import com.hypersomnia.voice.VoiceTuning;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.slf4j.Logger;

import java.io.Reader;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Голос осколка: одна реплика за раз. Своего текста на экране нет: субтитр ванильный (поле "subtitle" в
 * sounds.json, ключ hypersomnia.subtitle.voice.<id>, текст на языке игры) и виден, только если в настройках
 * включены субтитры. Пока он говорит, субтитр держится (ванильный гас бы через 3 с после начала звука);
 * «в голове» — звук без затухания в точке слушателя, поэтому у субтитра нет стрелки направления.
 * Когда он замолчит — из voice/timing.json (делает tools/make_fragment_voice.py).
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class FragmentVoice {
    private FragmentVoice() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TIMING =
            ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "voice/timing.json");

    /** Тайминг реплики в тиках от начала звука: конец речи и длина файла. */
    private record Timing(int end, int len) {}

    private static final Timing FALLBACK = new Timing(VoiceTuning.FALLBACK_TICKS, VoiceTuning.FALLBACK_TICKS);

    private static Map<String, Timing> timing;
    private static boolean timingTried;

    private static VoiceLine line;
    private static Timing time = FALLBACK;
    private static int age;
    private static boolean pauseProof;
    private static SoundInstance sound;

    private static Field overlayField;
    private static boolean overlayTried;

    /** Говорит сейчас (или замолчал меньше gap тиков назад). */
    public static boolean speaking(int gap) {
        return line != null && age < time.end() + gap;
    }

    public static boolean speaking() {
        return speaking(0);
    }

    /** Сколько тиков он ещё говорит (0 и меньше — уже молчит). */
    public static int speechLeft() {
        return line == null ? 0 : time.end() - age;
    }

    /** Звук — реплика осколка (её не глушит тишина сцены у кровати). */
    public static boolean isVoice(SoundInstance s) {
        if (s == null) return false;
        ResourceLocation id = s.getLocation();
        return id.getNamespace().equals(Hypersomnia.MOD_ID) && id.getPath().startsWith("voice.");
    }

    /**
     * Сказать реплику: с места at (null — «в голове»: без затухания, в точке слушателя), громкость volume.
     * inPause — время идёт и в меню паузы (сцена у кровати). Прежняя реплика обрывается.
     */
    public static void say(VoiceLine l, Vec3 at, float volume, boolean inPause) {
        Minecraft mc = Minecraft.getInstance();
        stop(mc);
        DeferredHolder<SoundEvent, SoundEvent> holder = ModSounds.VOICE.get(l.sound());
        if (holder != null) {
            SoundEvent ev = holder.get();
            SoundInstance s = at == null
                    ? new HeadSound(ev, volume)
                    : new SimpleSoundInstance(ev, SoundSource.VOICE, volume, 1f, SoundInstance.createUnseededRandom(),
                    at.x, at.y, at.z);
            mc.getSoundManager().play(s);
            sound = s;
        }
        line = l;
        time = timing(mc, l);
        age = 0;
        pauseProof = inPause;
    }

    /** Оборвать реплику (ванильный субтитр догаснет сам). */
    public static void stop(Minecraft mc) {
        if (sound != null) mc.getSoundManager().stop(sound);
        sound = null;
        line = null;
    }

    /** Перезагрузка ресурсов: тайминги перечитаются при следующей реплике. */
    static void clearCaches() {
        timing = null;
        timingTried = false;
    }

    /**
     * Ванильные субтитры поверх открытого экрана: меню паузы размывает и затемняет интерфейс под собой, а его
     * реплику в паузе (сцена у кровати) надо прочесть. Рисует тот же SubtitleOverlay, что и слой интерфейса.
     */
    public static void renderSubtitlesOnTop(GuiGraphics g) {
        SubtitleOverlay o = overlay(Minecraft.getInstance());
        if (o == null) return;
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 500f);
        o.render(g);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------
    // Тайминги
    // ------------------------------------------------------------------
    private static Timing timing(Minecraft mc, VoiceLine l) {
        if (!timingTried) loadTiming(mc);
        Timing t = timing == null ? null : timing.get(l.id);
        return t != null ? t : FALLBACK;
    }

    private static void loadTiming(Minecraft mc) {
        timingTried = true;
        try (Reader r = mc.getResourceManager().openAsReader(TIMING)) {
            JsonObject lang = JsonParser.parseReader(r).getAsJsonObject().getAsJsonObject(VoiceLine.AUDIO_LANG);
            Map<String, Timing> m = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : lang.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                m.put(e.getKey(), new Timing(o.get("end").getAsInt(), o.get("len").getAsInt()));
            }
            timing = m;
        } catch (Exception ex) {
            LOGGER.warn("Fragment voice timing not loaded ({}): {}", TIMING, ex.toString());
            timing = null;
        }
    }

    // ------------------------------------------------------------------
    // Время и субтитр
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (line == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            stop(mc);
            return;
        }
        if (mc.isPaused() && !pauseProof) return; // в паузе звук стоит — и счёт стоит
        age++;
        if (sound != null && age < time.end()) holdSubtitle(mc);
        if (age > Math.max(time.len(), time.end() + VoiceTuning.WHISPER_GAP)) {
            line = null;
            sound = null;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stop(Minecraft.getInstance());
    }

    /**
     * Ванильный субтитр показывает звук 3 с от начала (× настройка «время показа уведомлений»): пока он говорит,
     * обновляем его, как повтор того же звука (дальше он гаснет по-ванильному). «В голове» — всякий раз в точке
     * слушателя (без стрелки); дальность — как у самого звука (sounds.json attenuation_distance).
     */
    private static void holdSubtitle(Minecraft mc) {
        if (!mc.options.showSubtitles().get()) return;
        // Звук без громкости движок не играет — и ванильного субтитра у него нет
        if (mc.options.getSoundSourceVolume(SoundSource.MASTER) <= 0f
                || mc.options.getSoundSourceVolume(SoundSource.VOICE) <= 0f) return;
        SubtitleOverlay o = overlay(mc);
        WeighedSoundEvents events = mc.getSoundManager().getSoundEvent(sound.getLocation());
        if (o == null || events == null || events.getSubtitle() == null) return;
        float range = Float.POSITIVE_INFINITY;
        if (sound instanceof HeadSound head) head.follow();
        else if (sound.getSound() != null) range = Math.max(sound.getVolume(), 1f) * sound.getSound().getAttenuationDistance();
        o.onPlaySound(sound, events, range);
    }

    /** SubtitleOverlay в Gui: поле private, геттера нет — ищем по типу (не зависит от имён). */
    private static SubtitleOverlay overlay(Minecraft mc) {
        if (!overlayTried) {
            overlayTried = true;
            for (Field f : Gui.class.getDeclaredFields()) {
                if (f.getType() != SubtitleOverlay.class) continue;
                try {
                    f.setAccessible(true);
                    overlayField = f;
                } catch (RuntimeException ex) {
                    LOGGER.warn("Subtitle overlay not reachable: {}", ex.toString());
                }
                break;
            }
        }
        if (overlayField == null || mc.gui == null) return null;
        try {
            return (SubtitleOverlay) overlayField.get(mc.gui);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    /** «В голове»: без затухания и всё время в точке слушателя (не уплывает в сторону, если игрок сдвинулся). */
    private static final class HeadSound extends AbstractTickableSoundInstance {
        HeadSound(SoundEvent ev, float volume) {
            super(ev, SoundSource.VOICE, SoundInstance.createUnseededRandom());
            this.volume = volume;
            this.attenuation = Attenuation.NONE;
            follow();
        }

        private void follow() {
            Vec3 p = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            x = p.x;
            y = p.y;
            z = p.z;
        }

        @Override
        public void tick() {
            follow();
        }
    }
}
