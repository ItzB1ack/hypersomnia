package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.chase.ChaseTuning;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * [новое] Ванильные субтитры голоса осколка в погоне: строку дрожит, а на редких
 * кадрах она глитчит — красный двойник и смещённые полосы. Текст остаётся
 * ванильным (SubtitleOverlay), без §k-обманок и фиолетового цвета в lang.
 * Эффект включается только когда идёт погоня и FragmentVoice реально говорит;
 * при выключенных субтитрах слой не трогаем. Числа — ChaseTuning.SUBTITLE_*.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class ChaseSubtitles {
    private ChaseSubtitles() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.SUBTITLE_OVERLAY.equals(event.getName())) return;
        if (event.isCanceled() || !active()) return;
        // Слой рисуем сами, чтобы применить к нему дрожь и глитч.
        event.setCanceled(true);
        render(event.getGuiGraphics(), event.getPartialTick(), event.getLayer());
    }

    private static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        // Слой SUBTITLE_OVERLAY и так скрыт при F1; событие при этом всё равно
        // приходит, поэтому без этой проверки ручная отрисовка вернула бы HUD.
        return ChaseClient.active() && FragmentVoice.speaking()
                && mc.options.showSubtitles().get() && !mc.options.hideGui;
    }

    private static void render(GuiGraphics g, DeltaTracker delta, LayeredDraw.Layer layer) {
        long frame = System.nanoTime() / 50_000_000L; // 20 fps, как у остальных эффектов
        float t = frame * 0.45f;
        float panic = Mth.clamp(ChaseClient.panicNow(), 0f, 1f);
        float k = 0.55f + 0.45f * panic;
        float jx = (Mth.sin(t * 1.7f) + 0.45f * Mth.sin(t * 4.3f)) * ChaseTuning.SUBTITLE_SHAKE_X * k;
        float jy = Mth.sin(t * 2.3f + 0.8f) * ChaseTuning.SUBTITLE_SHAKE_Y * k;
        boolean glitch = Math.floorMod(frame, ChaseTuning.SUBTITLE_GLITCH_EVERY) == 0;

        int w = g.guiWidth();
        int h = g.guiHeight();
        int x0 = Math.max(0, w - 340);
        int top = Math.max(0, h - 64);
        int bottom = Math.max(top + 1, h - 10);

        if (glitch) {
            RandomSource rnd = RandomSource.create(frame);
            int bands = 1 + rnd.nextInt(2);
            for (int i = 0; i < bands; i++) {
                int y = top + rnd.nextInt(bottom - top);
                int bh = ChaseTuning.SUBTITLE_BAND_MIN
                        + rnd.nextInt(ChaseTuning.SUBTITLE_BAND_MAX - ChaseTuning.SUBTITLE_BAND_MIN + 1);
                int shift = (rnd.nextBoolean() ? 1 : -1) * (3 + rnd.nextInt(4));
                g.enableScissor(x0, y, w, Math.min(bottom, y + bh));
                g.pose().pushPose();
                g.pose().translate(jx + shift, jy, 0f);
                g.setColor(0.72f, 0.28f, 0.32f, 0.70f);
                layer.render(g, delta);
                g.setColor(1f, 1f, 1f, 1f);
                g.pose().popPose();
                g.disableScissor();
            }

            g.pose().pushPose();
            g.pose().translate(jx + ChaseTuning.SUBTITLE_GHOST_OFFSET, jy, 0f);
            g.setColor(0.78f, 0.25f, 0.30f, ChaseTuning.SUBTITLE_GHOST_ALPHA);
            layer.render(g, delta);
            g.setColor(1f, 1f, 1f, 1f);
            g.pose().popPose();
        }

        g.pose().pushPose();
        g.pose().translate(jx, jy, 0f);
        layer.render(g, delta);
        g.pose().popPose();

        if (glitch) {
            RandomSource rnd = RandomSource.create(frame ^ 0x9E3779B97F4A7C15L);
            int lines = 1 + rnd.nextInt(3);
            for (int i = 0; i < lines; i++) {
                int y = top + rnd.nextInt(bottom - top);
                int x = x0 + rnd.nextInt(Math.max(1, w - x0));
                int len = 18 + rnd.nextInt(90);
                int color = rnd.nextBoolean() ? 0xAA1622 : 0xDDE6EC;
                g.fill(x, y, Math.min(w, x + len), y + 1, (ChaseTuning.SUBTITLE_BAND_ALPHA << 24) | color);
            }
        }
    }
}
