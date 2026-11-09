package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.chase.ChaseTuning;
import com.mojang.blaze3d.audio.ListenerTransform;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * [новое] Спецэффект только у субтитра с войслайном осколка: строка постоянно дрожит, а на
 * кадрах сбоя (2 из 6 при 20 fps) глитчит — красный и голубой двойники, смещённые полосы и
 * повреждённые линии. Остальные субтитры (другие звуки) рисуются как ванильные, без эффекта.
 *
 * Текст остаётся ванильным (SubtitleOverlay), без §k-обманок и фиолетового цвета в lang.
 * Эффект держится, пока виден субтитр реплики, включая ~3 с после конца речи. Тот же рендер
 * используется поверх меню паузы (FragmentVoice.renderSubtitlesOnTop), иначе там эффект терялся.
 * При выключенных субтитрах и F1 слой не трогаем. Числа — ChaseTuning.SUBTITLE_*.
 *
 * Строки ванильного SubtitleOverlay недоступны по API, поэтому список его субтитров читается
 * рефлексией: по тексту реплики (FragmentVoice.subtitleText) находится нужная строка, остальные
 * рисуются обычными. Если рефлексия не удалась, слой отдаётся ванили без эффекта.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class ChaseSubtitles {
    private ChaseSubtitles() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.SUBTITLE_OVERLAY.equals(event.getName())) return;
        if (event.isCanceled() || !effectsActive()) return;
        SubtitleOverlay o = FragmentVoice.overlay(Minecraft.getInstance());
        // Если слой не достать, не отменяем ванильный: лучше без эффекта, чем без субтитров.
        if (o == null) return;
        event.setCanceled(true);
        render(event.getGuiGraphics(), o);
    }

    /** Эффект нужен: идёт погоня и виден субтитр реплики, субтитры включены, HUD не скрыт. */
    public static boolean effectsActive() {
        Minecraft mc = Minecraft.getInstance();
        // Слой SUBTITLE_OVERLAY и так скрыт при F1; событие при этом всё равно
        // приходит, поэтому без этой проверки ручная отрисовка вернула бы HUD.
        return ChaseClient.active()
                && FragmentVoice.subtitleShowing()
                && mc.options.showSubtitles().get() && !mc.options.hideGui;
    }

    /** Рисует все видимые субтитры; строка реплики — с дрожью и глитчем. */
    public static void render(GuiGraphics g, SubtitleOverlay o) {
        if (!ready() || !renderSubtitles(g, o)) {
            // Рефлексия недоступна: обычная ванильная отрисовка без эффекта.
            o.render(g);
        }
    }

    /** @return false, если строки прочитать не удалось (тогда нужен ванильный проход). */
    private static boolean renderSubtitles(GuiGraphics g, SubtitleOverlay o) {
        Minecraft mc = Minecraft.getInstance();
        syncListener(mc, o);
        List<?> all = all(o);
        if (all == null) return false;

        SoundManager sm = mc.getSoundManager();
        ListenerTransform lt = sm.getListenerTransform();
        Vec3 listener = lt.position();
        Vec3 forward = lt.forward();
        Vec3 right = lt.right();

        // Как в SubtitleOverlay.render: audibleSubtitles = subtitles, слышимые из точки слушателя.
        List<Object> audible = new ArrayList<>();
        for (Object s : all) if (audible(s, listener)) audible.add(s);
        if (audible.isEmpty()) return true;

        double display = 3000.0 * mc.options.notificationDisplayTime().get();
        int maxWidth = 0;
        for (Iterator<Object> it = audible.iterator(); it.hasNext(); ) {
            Object s = it.next();
            purge(s, display);
            if (!stillActive(s)) {
                it.remove();
                continue;
            }
            Component text = text(s);
            if (text != null) maxWidth = Math.max(maxWidth, mc.font.width(text));
        }
        if (audible.isEmpty()) return true;
        maxWidth += mc.font.width("<") + mc.font.width(" ") + mc.font.width(">") + mc.font.width(" ");

        long frame = System.nanoTime() / 50_000_000L; // 20 fps, как у остальных эффектов
        float t = frame * 0.45f;
        float panic = Mth.clamp(ChaseClient.panicNow(), 0f, 1f);
        float k = 0.60f + 0.40f * panic;
        RandomSource jr = RandomSource.create(frame * 0x9E3779B97F4A7C15L + 7L);
        float jx = (Mth.sin(t * 1.7f) + 0.45f * Mth.sin(t * 4.3f)) * ChaseTuning.SUBTITLE_SHAKE_X * k
                + (jr.nextFloat() * 2f - 1f) * 1.8f * k;
        float jy = Mth.sin(t * 2.3f + 0.8f) * ChaseTuning.SUBTITLE_SHAKE_Y * k
                + (jr.nextFloat() * 2f - 1f) * 0.9f * k;
        boolean glitch = Math.floorMod(frame, ChaseTuning.SUBTITLE_GLITCH_EVERY) < 2;
        if (glitch) jx += (jr.nextFloat() * 2f - 1f) * ChaseTuning.SUBTITLE_JOLT;

        Component voice = FragmentVoice.subtitleText();
        int row = 0;
        for (Object s : audible) {
            Object closest = closest(s, listener);
            if (closest == null) continue;
            Component text = text(s);
            if (text == null) continue;
            boolean isVoice = voice != null && voice.equals(text);
            drawRow(g, mc, row, maxWidth, text, closest, listener, forward, right, display,
                    isVoice, frame, jx, jy, glitch);
            row++;
        }
        return true;
    }

    /** Одна строка субтитров как в ванили; для строки реплики — дрожь и глитч. */
    private static void drawRow(GuiGraphics g, Minecraft mc, int row, int width, Component text,
                                Object closest, Vec3 listener, Vec3 forward, Vec3 right,
                                double display, boolean voice, long frame, float jx, float jy,
                                boolean glitch) {
        Vec3 at = location(closest);
        if (at == null) return;
        long playedAt = time(closest);
        Vec3 dir = at.subtract(listener).normalize();
        double d1 = right.dot(dir);
        double d2 = forward.dot(dir);

        int j = width / 2;
        int k = 9;
        int l = k / 2;
        int textWidth = mc.font.width(text);
        int fade = Mth.floor(Mth.clampedLerp(255.0F, 75.0F,
                (float) (Util.getMillis() - playedAt) / (float) display));
        int color = 0xFF000000 | (fade << 16) | (fade << 8) | fade;

        float bx = g.guiWidth() - j - 2f + (voice ? jx : 0f);
        float by = (g.guiHeight() - 35) - row * (k + 1) + (voice ? jy : 0f);

        // Фон строки (ванильный), сдвигается вместе со строкой реплики.
        g.pose().pushPose();
        g.pose().translate(bx, by, 0f);
        g.fill(-j - 1, -l - 1, j + 1, l + 1, mc.options.getBackgroundColor(0.8F));
        g.pose().popPose();

        if (voice && glitch) drawGhosts(g, mc, text, textWidth, l, fade, bx, by);

        g.pose().pushPose();
        g.pose().translate(bx, by, 0f);
        // Как в ванили: стрелка направления только если звук не спереди.
        if (d2 <= 0.5) {
            if (d1 > 0.0) {
                g.drawString(mc.font, ">", j - mc.font.width(">"), -l, color);
            } else if (d1 < 0.0) {
                g.drawString(mc.font, "<", -j, -l, color);
            }
        }
        g.drawString(mc.font, text, -textWidth / 2, -l, color);
        g.pose().popPose();

        if (voice && glitch) drawDamage(g, mc, text, textWidth, l, fade, bx, by, j, row, frame);
    }

    /** Красный и голубой двойники текста реплики. */
    private static void drawGhosts(GuiGraphics g, Minecraft mc, Component text, int textWidth, int l,
                                   int fade, float bx, float by) {
        int redA = (int) (fade * ChaseTuning.SUBTITLE_GHOST_ALPHA);
        int cyanA = (int) (fade * ChaseTuning.SUBTITLE_GHOST_ALPHA * 0.75f);
        g.pose().pushPose();
        g.pose().translate(bx + ChaseTuning.SUBTITLE_GHOST_OFFSET, by + 1f, 0f);
        g.drawString(mc.font, text, -textWidth / 2, -l, (redA << 24) | 0xB03038);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(bx - ChaseTuning.SUBTITLE_CHROMA_OFFSET, by - 1f, 0f);
        g.drawString(mc.font, text, -textWidth / 2, -l, (cyanA << 24) | 0x2FB8D8);
        g.pose().popPose();
    }

    /** Смещённые полосы и короткие повреждённые линии — только внутри прямоугольника строки. */
    private static void drawDamage(GuiGraphics g, Minecraft mc, Component text, int textWidth, int l,
                                   int fade, float bx, float by, int j, int row, long frame) {
        int left = Math.max(0, Mth.floor(bx - j - 1));
        int rightEdge = Math.min(g.guiWidth(), Mth.ceil(bx + j + 1));
        int top = Math.max(0, Mth.floor(by - l - 1));
        int bottom = Math.min(g.guiHeight(), Mth.ceil(by + l + 1));
        int height = bottom - top;
        int lineWidth = rightEdge - left;
        if (height <= 0 || lineWidth <= 0) return;

        RandomSource rnd = RandomSource.create(frame * 0x9E3779B97F4A7C15L + row);
        int bands = 1 + rnd.nextInt(2);
        for (int i = 0; i < bands; i++) {
            int bh = Math.min(height, ChaseTuning.SUBTITLE_BAND_MIN
                    + rnd.nextInt(ChaseTuning.SUBTITLE_BAND_MAX - ChaseTuning.SUBTITLE_BAND_MIN + 1));
            int y = top + rnd.nextInt(Math.max(1, height - bh + 1));
            int shift = (rnd.nextBoolean() ? 1 : -1) * (2 + rnd.nextInt(ChaseTuning.SUBTITLE_BAND_SHIFT));
            int a = Math.max(0x60, fade / 2);
            g.enableScissor(left, y, rightEdge, Math.min(bottom, y + bh));
            g.pose().pushPose();
            g.pose().translate(bx + shift, by, 0f);
            g.drawString(mc.font, text, -textWidth / 2, -l, (a << 24) | 0xD24A55);
            g.pose().popPose();
            g.disableScissor();
        }

        int lines = 2 + rnd.nextInt(2);
        for (int i = 0; i < lines; i++) {
            int y = top + rnd.nextInt(height);
            int x = left + rnd.nextInt(lineWidth);
            int len = Math.min(rightEdge - x, 10 + rnd.nextInt(48));
            int rgb = rnd.nextBoolean() ? 0xAA1622 : 0xDDE6EC;
            int a = 0x40 + rnd.nextInt(ChaseTuning.SUBTITLE_BAND_ALPHA - 0x40 + 1);
            g.fill(x, y, x + len, y + 1, (a << 24) | rgb);
        }
    }

    // ------------------------------------------------------------------
    // Рефлексия в SubtitleOverlay
    // ------------------------------------------------------------------
    private static Field subtitlesField;
    private static Field listeningField;
    private static Method mAudible;
    private static Method mPurge;
    private static Method mActive;
    private static Method mText;
    private static Method mClosest;
    private static Method mLocation;
    private static Method mTime;
    private static boolean reflectTried;
    private static boolean reflectOk;

    private static boolean ready() {
        if (reflectTried) return reflectOk;
        reflectTried = true;
        try {
            Class<?> subtitle = null;
            Class<?> playedAt = null;
            for (Class<?> c : SubtitleOverlay.class.getDeclaredClasses()) {
                if (c.getSimpleName().equals("Subtitle")) subtitle = c;
                else if (c.getSimpleName().equals("SoundPlayedAt")) playedAt = c;
            }
            Field list = null;
            for (Field f : SubtitleOverlay.class.getDeclaredFields()) {
                if (f.getName().equals("subtitles") && List.class.isAssignableFrom(f.getType())) {
                    list = f;
                    break;
                }
            }
            if (list == null) {
                // Имена могут быть переименованы: берём первый List, который не audibleSubtitles.
                for (Field f : SubtitleOverlay.class.getDeclaredFields()) {
                    if (!List.class.isAssignableFrom(f.getType())) continue;
                    if (f.getName().toLowerCase().contains("audible")) continue;
                    list = f;
                    break;
                }
            }
            if (subtitle == null || playedAt == null || list == null) {
                throw new NoSuchFieldException("SubtitleOverlay internals");
            }
            list.setAccessible(true);
            subtitlesField = list;
            // Пока слой рисуется вручную, ванильный render не выставляет isListening. Повторяем
            // его роль: иначе новые звуки не попадут в список субтитров, пока эффект активен.
            Field listening = null;
            for (Field f : SubtitleOverlay.class.getDeclaredFields()) {
                if (f.getName().equals("isListening") && f.getType() == boolean.class) {
                    listening = f;
                    break;
                }
            }
            if (listening == null) {
                for (Field f : SubtitleOverlay.class.getDeclaredFields()) {
                    if (f.getType() == boolean.class) {
                        listening = f;
                        break;
                    }
                }
            }
            if (listening != null) {
                listening.setAccessible(true);
                listeningField = listening;
            }
            mAudible = method(subtitle, "isAudibleFrom", Vec3.class);
            mPurge = method(subtitle, "purgeOldInstances", double.class);
            mActive = method(subtitle, "isStillActive");
            mText = method(subtitle, "getText");
            mClosest = method(subtitle, "getClosest", Vec3.class);
            mLocation = method(playedAt, "location");
            mTime = method(playedAt, "time");
            reflectOk = true;
        } catch (Throwable t) {
            LOGGER.warn("Chase subtitles: SubtitleOverlay internals not reachable: {}", t.toString());
            reflectOk = false;
        }
        return reflectOk;
    }

    private static Method method(Class<?> c, String name, Class<?>... args) throws NoSuchMethodException {
        Method m = c.getDeclaredMethod(name, args);
        m.setAccessible(true);
        return m;
    }

    private static void syncListener(Minecraft mc, SubtitleOverlay o) {
        if (listeningField == null) return;
        try {
            if (!listeningField.getBoolean(o)) {
                mc.getSoundManager().addListener(o);
                listeningField.setBoolean(o, true);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static List<?> all(SubtitleOverlay o) {        try {
            return (List<?>) subtitlesField.get(o);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    private static boolean audible(Object s, Vec3 listener) {
        try {
            return (Boolean) mAudible.invoke(s, listener);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private static void purge(Object s, double display) {
        try {
            mPurge.invoke(s, display);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static boolean stillActive(Object s) {
        try {
            return (Boolean) mActive.invoke(s);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private static Component text(Object s) {
        try {
            return (Component) mText.invoke(s);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    private static Object closest(Object s, Vec3 listener) {
        try {
            return mClosest.invoke(s, listener);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    private static Vec3 location(Object playedAt) {
        try {
            return (Vec3) mLocation.invoke(playedAt);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    private static long time(Object playedAt) {
        try {
            return (Long) mTime.invoke(playedAt);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return 0L;
        }
    }
}
