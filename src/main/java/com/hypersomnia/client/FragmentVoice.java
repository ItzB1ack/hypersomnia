package com.hypersomnia.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModSounds;
import com.hypersomnia.voice.VoiceLine;
import com.hypersomnia.voice.VoiceTuning;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Голос осколка: реплика (звук) и её субтитр. Субтитр — крупный тонкий шрифт (FragmentFont): буквы
 * проявляются вслед за речью (куски речи из voice/timing.json, по фразам), каждая дрожит сама по себе,
 * изредка дёргается, строка иногда срывается, как сбой развёртки; под ней тёмная полоса, рядом серо-синий
 * двойник. Показывается всегда: это часть сцены, а не ванильные субтитры. Одновременно звучит одна реплика.
 * Голос всегда английский; субтитр — на языке игры (русский из ru_ru.json, иначе английский), по таймингам английской речи.
 * Без открытого экрана рисует слой voice_subtitles (регистрирует ChaseOverlay), с экраном — поверх него.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class FragmentVoice {
    private FragmentVoice() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TIMING =
            ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "voice/timing.json");
    private static final RandomSource RANDOM = RandomSource.create();

    /** Тайминг реплики в тиках от начала звука: начало и конец речи, длина файла, куски речи между паузами. */
    private record Timing(int on, int end, int len, int[][] seg) {}

    private static Map<String, Map<String, Timing>> timing;
    private static boolean timingTried;

    private static VoiceLine line;
    private static Timing time = new Timing(0, 0, 0, new int[0][]);
    private static int[] cps = new int[0];
    private static float[] reveal = new float[0];
    private static int age;
    private static long tickNanos;
    private static boolean pauseProof;
    private static boolean low;
    private static float shake = 1f;
    private static long seed;
    private static SoundInstance sound;

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
     * Сказать реплику: с места at (null — «в голове», без затухания), громкость volume, сила дрожи субтитра
     * shakeMul. inPause — время идёт и в меню паузы (сцена у кровати), low — субтитр ниже (под кнопками меню).
     * Прежняя реплика обрывается.
     */
    public static void say(VoiceLine l, Vec3 at, float volume, float shakeMul, boolean inPause, boolean lowSub) {
        Minecraft mc = Minecraft.getInstance();
        stop(mc);
        DeferredHolder<SoundEvent, SoundEvent> holder = ModSounds.VOICE.get(l.sound());
        if (holder != null) {
            SoundEvent ev = holder.get();
            SoundInstance s = at == null
                    ? new SimpleSoundInstance(ev.getLocation(), SoundSource.VOICE, volume, 1f,
                    SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true)
                    : new SimpleSoundInstance(ev, SoundSource.VOICE, volume, 1f, SoundInstance.createUnseededRandom(),
                    at.x, at.y, at.z);
            mc.getSoundManager().play(s);
            sound = s;
        }
        line = l;
        time = timing(mc, l);
        cps = Component.translatableWithFallback(l.key(), l.en).getString().codePoints().toArray();
        reveal = revealTimes(cps, time);
        age = 0;
        tickNanos = System.nanoTime();
        pauseProof = inPause;
        low = lowSub;
        shake = shakeMul;
        seed = RANDOM.nextLong();
    }

    /** Оборвать реплику (звук и субтитр). */
    public static void stop(Minecraft mc) {
        if (sound != null) mc.getSoundManager().stop(sound);
        sound = null;
        line = null;
    }

    /** Перезагрузка ресурсов: тайминги и шрифт перечитаются при следующем обращении. */
    static void clearCaches() {
        timing = null;
        timingTried = false;
        FragmentFont.clear();
    }

    // ------------------------------------------------------------------
    // Тайминги
    // ------------------------------------------------------------------
    private static Timing timing(Minecraft mc, VoiceLine l) {
        if (!timingTried) loadTiming(mc);
        Timing t = null;
        if (timing != null) {
            Map<String, Timing> m = timing.get(VoiceLine.AUDIO_LANG);
            if (m != null) t = m.get(l.id);
        }
        if (t != null) return t;
        int end = 10 + l.en.length() * 2; // запасной: около десяти букв в секунду
        return new Timing(10, end, end + 20, new int[0][]);
    }

    private static void loadTiming(Minecraft mc) {
        timingTried = true;
        try (Reader r = mc.getResourceManager().openAsReader(TIMING)) {
            Map<String, Map<String, Timing>> all = new HashMap<>();
            for (Map.Entry<String, JsonElement> le : JsonParser.parseReader(r).getAsJsonObject().entrySet()) {
                Map<String, Timing> m = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : le.getValue().getAsJsonObject().entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    JsonArray sa = o.getAsJsonArray("seg");
                    int[][] seg = new int[sa.size()][];
                    for (int i = 0; i < seg.length; i++) {
                        JsonArray p = sa.get(i).getAsJsonArray();
                        seg[i] = new int[]{p.get(0).getAsInt(), p.get(1).getAsInt()};
                    }
                    m.put(e.getKey(), new Timing(o.get("on").getAsInt(), o.get("end").getAsInt(),
                            o.get("len").getAsInt(), seg));
                }
                all.put(le.getKey(), m);
            }
            timing = all;
        } catch (Exception ex) {
            LOGGER.warn("Fragment voice timing not loaded ({}): {}", TIMING, ex.toString());
            timing = null;
        }
    }

    /** Когда проявляется каждая буква: фразы по кускам речи (если их столько же), иначе ровно по всей речи. */
    private static float[] revealTimes(int[] text, Timing t) {
        int n = text.length;
        float[] r = new float[n];
        List<int[]> sentences = new ArrayList<>();
        int from = 0;
        for (int i = 0; i < n; i++) {
            int c = text[i];
            boolean end = c == '.' || c == '?' || c == '!' || c == '\u2026';
            if (end && (i + 1 == n || text[i + 1] == ' ')) {
                sentences.add(new int[]{from, i + 1});
                from = i + 1;
            }
        }
        if (from < n) sentences.add(new int[]{from, n});
        if (t.seg().length == sentences.size() && !sentences.isEmpty()) {
            for (int s = 0; s < sentences.size(); s++) {
                spread(r, sentences.get(s)[0], sentences.get(s)[1], t.seg()[s][0], t.seg()[s][1]);
            }
        } else {
            spread(r, 0, n, t.on(), t.end());
        }
        return r;
    }

    private static void spread(float[] r, int from, int to, float a, float b) {
        float span = Math.max(1f, (b - a) * VoiceTuning.SUB_REVEAL);
        int steps = Math.max(1, to - from - 1);
        for (int i = from; i < to; i++) r[i] = a + span * (i - from) / steps;
    }

    // ------------------------------------------------------------------
    // Время
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (line == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            stop(mc);
            return;
        }
        if (mc.isPaused() && !pauseProof) return; // звук в паузе стоит — и субтитр стоит
        age++;
        tickNanos = System.nanoTime();
        if (age > time.end() + VoiceTuning.SUB_HOLD + VoiceTuning.SUB_FADE) {
            line = null;
            sound = null;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stop(Minecraft.getInstance());
    }

    // ------------------------------------------------------------------
    // Субтитр
    // ------------------------------------------------------------------
    /** Слой интерфейса: только без открытого экрана (с экраном рисует onScreenRender поверх него). */
    public static void renderLayer(GuiGraphics g, DeltaTracker delta) {
        if (Minecraft.getInstance().screen != null) return;
        render(g, g.guiWidth(), g.guiHeight());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        render(event.getGuiGraphics(), event.getScreen().width, event.getScreen().height);
    }

    private static void render(GuiGraphics g, int w, int h) {
        if (line == null || cps.length == 0) return;
        Minecraft mc = Minecraft.getInstance();
        boolean frozen = mc.isPaused() && !pauseProof;
        float now = age + (frozen ? 0f : Mth.clamp((System.nanoTime() - tickNanos) / 5.0e7f, 0f, 1f));
        float fade = 1f - Mth.clamp((now - time.end() - VoiceTuning.SUB_HOLD) / VoiceTuning.SUB_FADE, 0f, 1f);
        if (fade <= 0f || now < reveal[0]) return;
        boolean own = FragmentFont.ready();
        for (int cp : cps) {
            if (cp != ' ' && !FragmentFont.has(cp)) own = false;
        }
        float px = Mth.clamp(h * VoiceTuning.SUB_SIZE, VoiceTuning.SUB_SIZE_MIN, VoiceTuning.SUB_SIZE_MAX);
        float track = px * VoiceTuning.SUB_TRACKING;

        // Перенос по словам; строки выравниваются по длине (без одинокого слова внизу)
        float maxW = w * VoiceTuning.SUB_WIDTH;
        List<int[]> lines = wrap(mc, own, cps, px, track, maxW);
        if (lines.size() > 1) {
            float lo = 0f, hi = maxW;
            for (int it = 0; it < 12; it++) {
                float mid = (lo + hi) * 0.5f;
                if (wrap(mc, own, cps, px, track, mid).size() > lines.size()) lo = mid;
                else hi = mid;
            }
            lines = wrap(mc, own, cps, px, track, hi);
        }

        float lineH = px * VoiceTuning.SUB_LINE;
        float lastBase = h * (low ? VoiceTuning.SUB_Y_PAUSE : VoiceTuning.SUB_Y);
        float firstBase = lastBase - (lines.size() - 1) * lineH;

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 500f);

        // Тёмная полоса под текстом: проступает с первой буквой
        int band = (int) (fade * VoiceTuning.SUB_BAND_ALPHA * Mth.clamp((now - reveal[0]) / 6f, 0f, 1f) * 255f);
        if (band > 2) {
            int top = (int) (firstBase - px * 1.35f);
            int mid = (int) (firstBase + (lastBase - firstBase) * 0.5f - px * 0.3f);
            int bottom = (int) (lastBase + px * 0.75f);
            int c = (band << 24) | VoiceTuning.SUB_BAND_RGB;
            g.fillGradient(0, top, w, mid, VoiceTuning.SUB_BAND_RGB, c);
            g.fillGradient(0, mid, w, bottom, c, VoiceTuning.SUB_BAND_RGB);
        }

        long ms = System.currentTimeMillis();
        long step = ms / VoiceTuning.SHAKE_STEP_MS;
        float amp = px * VoiceTuning.SHAKE_AMP * shake;
        for (int li = 0; li < lines.size(); li++) {
            int[] ln = lines.get(li);
            float width = 0f;
            for (int i = ln[0]; i < ln[1]; i++) width += adv(mc, own, cps[i], px) + track;
            width -= track;
            float slip = hash(seed, -1 - li, step / 2) < VoiceTuning.SLIP_CHANCE
                    ? (hash(seed, -9 - li, step / 2) < 0.5f ? -1f : 1f) * px * VoiceTuning.SLIP : 0f;
            float x = w / 2f - width / 2f + slip;
            float y = firstBase + li * lineH;
            for (int i = ln[0]; i < ln[1]; i++) {
                int cp = cps[i];
                float a = fade * Mth.clamp((now - reveal[i]) / VoiceTuning.SUB_CHAR_FADE, 0f, 1f);
                if (cp != ' ' && a > 0.01f) {
                    float age0 = now - reveal[i];
                    float am = amp * (1f + VoiceTuning.SHAKE_FRESH
                            * (1f - Mth.clamp(age0 / VoiceTuning.SHAKE_FRESH_TICKS, 0f, 1f)));
                    float jx = (hash(seed, i * 2, step) - 0.5f) * 2f * am * VoiceTuning.SHAKE_X;
                    float jy = (hash(seed, i * 2 + 1, step) - 0.5f) * 2f * am;
                    if (hash(seed ^ 0x5DEECE66DL, i, step) < VoiceTuning.SHAKE_JERK_CHANCE) {
                        jx *= VoiceTuning.SHAKE_JERK;
                        jy *= VoiceTuning.SHAKE_JERK;
                        a *= 0.6f;
                    }
                    jx += (float) Math.sin(ms * 0.023 + i * 1.7) * am * VoiceTuning.SHAKE_TREMOR * VoiceTuning.SHAKE_X;
                    jy += (float) Math.cos(ms * 0.019 + i * 2.3) * am * VoiceTuning.SHAKE_TREMOR;
                    draw(g, mc, own, cp, x + jx + px * VoiceTuning.SUB_GHOST_SHIFT, y + jy, px,
                            VoiceTuning.SUB_GHOST_RGB, a * VoiceTuning.SUB_GHOST_ALPHA);
                    draw(g, mc, own, cp, x + jx, y + jy, px, VoiceTuning.SUB_RGB, a);
                }
                x += adv(mc, own, cp, px) + track;
            }
        }
        g.setColor(1f, 1f, 1f, 1f);
        g.pose().popPose();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /** Ширина символа: свой шрифт или (если в атласе нет символа) ванильный в том же кегле. */
    private static float adv(Minecraft mc, boolean own, int cp, float px) {
        if (own) return FragmentFont.advance(cp, px);
        return mc.font.width(Character.toString(cp)) * px / 9f;
    }

    private static void draw(GuiGraphics g, Minecraft mc, boolean own, int cp, float x, float y, float px, int rgb, float a) {
        if (own) {
            g.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, a);
            FragmentFont.glyph(g, cp, x, y, px);
            return;
        }
        float s = px / 9f;
        g.setColor(1f, 1f, 1f, 1f);
        g.pose().pushPose();
        g.pose().translate(x, y - 7f * s, 0f);
        g.pose().scale(s, s, 1f);
        g.drawString(mc.font, Character.toString(cp), 0, 0, (Math.max(4, (int) (a * 255f)) << 24) | rgb, false);
        g.pose().popPose();
    }

    /** Детерминированный шум 0..1 по (seed, i, step): буква держит положение весь шаг дрожи. */
    /** Жадный перенос по пробелам: [начало, конец) каждой строки, пробел на стыке не входит. */
    private static List<int[]> wrap(Minecraft mc, boolean own, int[] cps, float px, float track, float maxW) {
        List<int[]> lines = new ArrayList<>();
        int start = 0;
        int lastSpace = -1;
        float lineW = 0f;
        for (int i = 0; i < cps.length; i++) {
            if (cps[i] == ' ') lastSpace = i;
            lineW += adv(mc, own, cps[i], px) + track;
            if (lineW > maxW && lastSpace > start) {
                lines.add(new int[]{start, lastSpace});
                start = lastSpace + 1;
                lineW = 0f;
                for (int k = start; k <= i; k++) lineW += adv(mc, own, cps[k], px) + track;
            }
        }
        lines.add(new int[]{start, cps.length});
        return lines;
    }

    private static float hash(long s, long i, long step) {
        long z = s + i * 0x9E3779B97F4A7C15L + step * 0xD1B54A32D192ED03L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 40) / (float) (1 << 24);
    }
}
