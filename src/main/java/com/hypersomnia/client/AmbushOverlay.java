package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.chase.AmbushTuning;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * [новое] Короткий кадр-испуг засады: чёрный провал, вспышка, затем статика и глаза поверх кадра.
 * Рисуется только клиентом, поверх интерфейса, и живёт SCARE_TICKS тиков.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AmbushOverlay {
    private AmbushOverlay() {}

    private static final int BLACK = AmbushTuning.SCARE_BLACK_TICKS;
    private static final int FLASH = AmbushTuning.SCARE_FLASH_TICKS;

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "ambush_scare"),
                AmbushOverlay::render
        );
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        if (!AmbushClient.active()) return;
        int w = g.guiWidth();
        int h = g.guiHeight();
        int e = AmbushClient.elapsed();

        if (e < BLACK) {
            g.fill(0, 0, w, h, 0xFF000000);
            return;
        }
        if (e < BLACK + FLASH) {
            float k = 1f - (e - BLACK) / (float) Math.max(1, FLASH);
            int alpha = Math.round((0.30f + AmbushTuning.SCARE_FLASH_ALPHA * k) * 255f) & 0xFF;
            g.fill(0, 0, w, h, (alpha << 24) | 0xE8E2D8);
            g.fill(0, 0, w, 3, 0x66AA1622);
            g.fill(0, h - 3, w, h, 0x66AA1622);
            return;
        }

        RandomSource rnd = RandomSource.create(System.nanoTime());
        g.fill(0, 0, w, h, (86 << 24) | 0x03050A);
        SleepSceneOverlay.drawScareEyes(g, w, h);

        int specks = Math.max(220, w * h / 550);
        for (int i = 0; i < specks; i++) {
            int x = rnd.nextInt(w);
            int y = rnd.nextInt(h);
            int a = 10 + rnd.nextInt(40);
            int c = rnd.nextBoolean() ? 0xFFFFFF : 0x8A2030;
            g.fill(x, y, x + 1 + rnd.nextInt(2), y + 1, (a << 24) | c);
        }
        for (int i = 0; i < 6; i++) {
            int y = rnd.nextInt(h);
            int hh = 1 + rnd.nextInt(3);
            g.fill(0, y, w, y + hh, ((5 + rnd.nextInt(18)) << 24) | (rnd.nextBoolean() ? 0xFFFFFF : 0x000000));
        }
        for (int i = 0; i < 4; i++) {
            int x = rnd.nextInt(w);
            int ww = 2 + rnd.nextInt(Math.max(2, w / 18));
            g.fill(x, 0, x + ww, h, ((3 + rnd.nextInt(10)) << 24) | 0xFFFFFF);
        }
        g.fill(0, 0, w, 4, 0x55AA1622);
        g.fill(0, h - 4, w, h, 0x55AA1622);
    }
}
