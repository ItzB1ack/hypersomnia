package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ObserverTuning;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Кадр-сюрприз осколка (фаза GHOST во сне). До стадии 4 — мягкая заглушка: затемнение, пара глаз,
 * строка вопроса и статика. С стадии 4 (DREAM_APPROACH_MIN_STAGE) — резкий кадр-рывок (NoticeScare):
 * белая вспышка, глаза наезжают на камеру с наклоном, полосы развёртки и красная линия; строка
 * остаётся сверху. Засада (AmbushOverlay) выглядит иначе: там чёрный кадр и случайная дрожь.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ObserverScareOverlay {
    private ObserverScareOverlay() {}

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "observer_scare"),
                ObserverScareOverlay::render
        );
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        // Засада (AmbushOverlay) рисует свой чёрный кадр и глаза — не накладывать поверх неё второй.
        if (!ObserverScare.active() || SleepSceneState.active() || AmbushClient.active()) return;
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth();
        int h = g.guiHeight();
        boolean hard = ObserverScare.stage >= ObserverTuning.SCARE_HARD_MIN_STAGE;

        if (hard) {
            drawHard(g, w, h);
        } else {
            g.fill(0, 0, w, h, 0xFF000000);
            SleepSceneOverlay.drawScareEyes(g, w, h);
        }

        drawLine(g, mc, w, h);

        if (!hard) {
            RandomSource rnd = RandomSource.create(System.nanoTime());
            for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, 0x22000000);
            for (int n = 0; n < Math.max(100, w * h / 1200); n++) {
                int x = rnd.nextInt(w);
                int y = rnd.nextInt(h);
                g.fill(x, y, x + 1, y + 1, ((8 + rnd.nextInt(20)) << 24) | 0xFFFFFF);
            }
        }
    }

    /** Стадия 4+: короткая белая вспышка, затем наезд глаз с наклоном, полосы и красная линия. */
    private static void drawHard(GuiGraphics g, int w, int h) {
        int total = ObserverTuning.SCARE_TICKS;
        int e = Math.max(0, total - ObserverScare.ticks);
        if (e < ObserverTuning.SCARE_HARD_FLASH_TICKS) {
            g.fill(0, 0, w, h, 0xFFF3EEE6);
            int y = h / 2 + (e == 0 ? -h / 6 : h / 8);
            g.fill(0, y, w, y + Math.max(2, h / 100), 0xFF08080A);
            return;
        }
        int span = Math.max(1, total - ObserverTuning.SCARE_HARD_FLASH_TICKS);
        float t = (e - ObserverTuning.SCARE_HARD_FLASH_TICKS) / (float) span;
        float zoom = 1.35f + 2.1f * t;
        float tilt = -7f + 10f * t;

        g.pose().pushPose();
        g.pose().translate(w / 2f, h * 0.42f, 0f);
        g.pose().mulPose(Axis.ZP.rotationDegrees(tilt));
        g.pose().scale(zoom, zoom, 1f);
        g.pose().translate(-w / 2f, -h * 0.42f, 0f);
        SleepSceneOverlay.drawScareEyes(g, w, h);
        g.pose().popPose();

        RandomSource rnd = RandomSource.create(System.nanoTime());
        for (int i = 0; i < 5; i++) {
            int y = rnd.nextInt(Math.max(1, h));
            int hh = Math.max(1, h / 100 + rnd.nextInt(Math.max(2, h / 45)));
            int c = rnd.nextBoolean() ? 0xFFFFFF : 0x08080A;
            int a = 35 + rnd.nextInt(90);
            g.fill(0, y, w, Math.min(h, y + hh), (a << 24) | c);
        }
        int x = (int) (w * (0.12f + 0.76f * t));
        g.fill(x - 1, 0, x + 1, h, 0x88AA1622);
        g.fill(0, 0, w, 2, 0x66FFFFFF);
        g.fill(0, h - 2, w, h, 0x66FFFFFF);
    }

    private static void drawLine(GuiGraphics g, Minecraft mc, int w, int h) {
        int i = Math.max(0, Math.min(ObserverScare.LINES.length - 1, ObserverScare.line));
        Component text = Component.translatableWithFallback("hypersomnia.observer.scare." + i,
                ObserverScare.LINES[i]);
        int tw = Math.max(1, mc.font.width(text));
        g.pose().pushPose();
        g.pose().translate(w / 2f, h * 0.80f, 0f);
        g.pose().scale(2f, 2f, 1f);
        g.fill(-tw / 2 - 4, -3, tw / 2 + 4, 11, 0x99000000);
        g.drawCenteredString(mc.font, text, 0, 0, 0xFFC8B8BC);
        g.pose().popPose();
    }
}
