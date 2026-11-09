package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.chase.ChaseTuning;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * [новое] Ванильные субтитры голоса осколка в погоне: строка постоянно дрожит, а на
 * кадрах сбоя (2 из 6 при 20 fps) глитчит — красный и голубой двойники, смещённые
 * полосы и рывок всей строки. Текст остаётся ванильным (SubtitleOverlay), без
 * §k-обманок и фиолетового цвета в lang. Эффект держится, пока виден ванильный
 * субтитр реплики, включая ~3 с после конца речи. Тот же рендер используется поверх
 * меню паузы (FragmentVoice.renderSubtitlesOnTop), иначе там эффект терялся.
 * При выключенных субтитрах и F1 слой не трогаем. Числа — ChaseTuning.SUBTITLE_*.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class ChaseSubtitles {
    private ChaseSubtitles() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.SUBTITLE_OVERLAY.equals(event.getName())) return;
        if (event.isCanceled() || !effectsActive()) return;
        // Слой рисуем сами, чтобы применить к нему дрожь и глитч.
        event.setCanceled(true);
        render(event.getGuiGraphics(), () -> event.getLayer().render(event.getGuiGraphics(), event.getPartialTick()));
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

    /** Рисует субтитры через draw с дрожью и глитчем. */
    public static void render(GuiGraphics g, Runnable draw) {
        long frame = System.nanoTime() / 50_000_000L; // 20 fps, как у остальных эффектов
        float t = frame * 0.45f;
        float panic = Mth.clamp(ChaseClient.panicNow(), 0f, 1f);
        float k = 0.60f + 0.40f * panic;
        RandomSource jr = RandomSource.create(frame * 0x9E3779B97F4A7C15L + 7L);
        float jx = (Mth.sin(t * 1.7f) + 0.45f * Mth.sin(t * 4.3f)) * ChaseTuning.SUBTITLE_SHAKE_X * k
                + (jr.nextFloat() * 2f - 1f) * 1.8f * k;
        float jy = Mth.sin(t * 2.3f + 0.8f) * ChaseTuning.SUBTITLE_SHAKE_Y * k
                + (jr.nextFloat() * 2f - 1f) * 0.9f * k;

        int cycle = Math.floorMod(frame, ChaseTuning.SUBTITLE_GLITCH_EVERY);
        boolean glitch = cycle < 2;
        if (glitch) jx += (jr.nextFloat() * 2f - 1f) * ChaseTuning.SUBTITLE_JOLT;

        int w = g.guiWidth();
        int h = g.guiHeight();
        int x0 = Math.max(0, w - 340);
        int top = Math.max(0, h - 64);
        int bottom = Math.max(top + 1, h - 10);

        if (glitch) {
            RandomSource rnd = RandomSource.create(frame);
            int bands = 2 + rnd.nextInt(2);
            for (int i = 0; i < bands; i++) {
                int y = top + rnd.nextInt(bottom - top);
                int bh = ChaseTuning.SUBTITLE_BAND_MIN
                        + rnd.nextInt(ChaseTuning.SUBTITLE_BAND_MAX - ChaseTuning.SUBTITLE_BAND_MIN + 1);
                int shift = (rnd.nextBoolean() ? 1 : -1)
                        * (3 + rnd.nextInt(ChaseTuning.SUBTITLE_BAND_SHIFT));
                g.enableScissor(x0, y, w, Math.min(bottom, y + bh));
                g.pose().pushPose();
                g.pose().translate(jx + shift, jy, 0f);
                g.setColor(0.76f, 0.30f, 0.34f, 0.80f);
                draw.run();
                g.setColor(1f, 1f, 1f, 1f);
                g.pose().popPose();
                g.disableScissor();
            }

            // Цветовой раскол: красный двойник вправо, голубой влево.
            g.pose().pushPose();
            g.pose().translate(jx + ChaseTuning.SUBTITLE_GHOST_OFFSET, jy + 1f, 0f);
            g.setColor(0.82f, 0.22f, 0.28f, ChaseTuning.SUBTITLE_GHOST_ALPHA);
            draw.run();
            g.setColor(1f, 1f, 1f, 1f);
            g.pose().popPose();

            g.pose().pushPose();
            g.pose().translate(jx - ChaseTuning.SUBTITLE_CHROMA_OFFSET, jy - 1f, 0f);
            g.setColor(0.22f, 0.72f, 0.86f, ChaseTuning.SUBTITLE_GHOST_ALPHA * 0.75f);
            draw.run();
            g.setColor(1f, 1f, 1f, 1f);
            g.pose().popPose();
        }

        g.pose().pushPose();
        g.pose().translate(jx, jy, 0f);
        draw.run();
        g.pose().popPose();

        if (glitch) {
            // Повреждённые строки и развёртка поверх самого текста.
            RandomSource rnd = RandomSource.create(frame ^ 0x9E3779B97F4A7C15L);
            int lines = 3 + rnd.nextInt(3);
            for (int i = 0; i < lines; i++) {
                int y = top + rnd.nextInt(bottom - top);
                int x = x0 + rnd.nextInt(Math.max(1, w - x0));
                int len = 24 + rnd.nextInt(110);
                int color = rnd.nextBoolean() ? 0xAA1622 : 0xDDE6EC;
                int a = 0x40 + rnd.nextInt(ChaseTuning.SUBTITLE_BAND_ALPHA - 0x40 + 1);
                g.fill(x, y, Math.min(w, x + len), y + 1, (a << 24) | color);
            }
            for (int y = top; y < bottom; y += 3) {
                g.fill(x0, y, w, y + 1, 0x28000000);
            }
        }
    }
}
