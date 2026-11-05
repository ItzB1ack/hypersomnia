package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Шкала усталости в правом нижнем углу: сегментная, «аналоговая». Во сне не показывается. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FatigueGauge {
    private FatigueGauge() {}

    private static final int SEGMENTS = 20;
    private static final int SEG_W = 4;
    private static final int SEG_H = 6;
    private static final int GAP = 1;
    private static final int MARGIN_X = 10;
    private static final int MARGIN_Y = 12;

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "fatigue_gauge"),
                FatigueGauge::render
        );
    }

    private static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (DreamClientState.isDream(mc)) return;

        float ratio = ClientFatigueState.ratio();
        boolean awake = SyringeClientState.active;
        // Показываем всегда (в креативе клиент видит 0, шкала будет пустой)

        int w = g.guiWidth();
        int h = g.guiHeight();
        int barW = SEGMENTS * (SEG_W + GAP) - GAP;
        int x0 = w - MARGIN_X - barW;
        int y0 = h - MARGIN_Y - SEG_H;

        long now = System.currentTimeMillis();
        RandomSource rnd = RandomSource.create(now / 90L);

        // Подпись
        Component label = Component.translatable("hypersomnia.hud.fatigue");
        g.drawString(mc.font, label, w - MARGIN_X - mc.font.width(label), y0 - 11, 0xFFB8C4CC, true);
        if (awake) {
            Component wake = Component.translatable("hypersomnia.hud.awake");
            boolean blink = (now / 500L) % 2 == 0;
            if (blink) g.drawString(mc.font, wake, x0, y0 - 11, 0xFF6FD8FF, true);
        }

        // Рамка
        int frame = awake ? 0xFF4FA8C8 : 0xFF2A3640;
        g.fill(x0 - 2, y0 - 2, x0 + barW + 2, y0 + SEG_H + 2, 0xB0000000);
        g.fill(x0 - 2, y0 - 2, x0 + barW + 2, y0 - 1, frame);
        g.fill(x0 - 2, y0 + SEG_H + 1, x0 + barW + 2, y0 + SEG_H + 2, frame);
        g.fill(x0 - 2, y0 - 2, x0 - 1, y0 + SEG_H + 2, frame);
        g.fill(x0 + barW + 1, y0 - 2, x0 + barW + 2, y0 + SEG_H + 2, frame);

        // Сегменты
        float filled = Mth.clamp(ratio, 0f, 1f) * SEGMENTS;
        boolean pulse = ratio >= 0.85f && (now / 350L) % 2 == 0;
        for (int i = 0; i < SEGMENTS; i++) {
            int sx = x0 + i * (SEG_W + GAP);
            int color;
            if (i + 1 <= filled) {
                float pos = i / (float) (SEGMENTS - 1);
                color = lerpColor(0xB8CCD8, 0xE05050, Mth.clamp((pos - 0.45f) / 0.55f, 0f, 1f));
                if (pulse) color = lerpColor(color, 0xFFFFFF, 0.35f);
                if (awake && rnd.nextInt(14) == 0) color = 0x2A3640; // под уколом шкала мерцает
            } else if (i < filled) {
                color = 0x5A6A74; // частично заполненный сегмент
            } else {
                color = 0x1A2228;
            }
            g.fill(sx, y0, sx + SEG_W, y0 + SEG_H, 0xFF000000 | color);
        }
    }

    private static int lerpColor(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int gr = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (gr << 8) | bl;
    }
}
