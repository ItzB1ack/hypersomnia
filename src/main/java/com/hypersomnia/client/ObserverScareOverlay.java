package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
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

/** ПЛЕЙСХОЛДЕР кадра-сюрприза осколка (фаза эпизода GHOST во сне, стадия 2+). Позже заменить настоящим кадром. */
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

        g.fill(0, 0, w, h, 0xFF000000);
        SleepSceneOverlay.drawScareEyes(g, w, h);

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

        RandomSource rnd = RandomSource.create(System.nanoTime());
        for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, 0x22000000);
        for (int n = 0; n < Math.max(100, w * h / 1200); n++) {
            int x = rnd.nextInt(w);
            int y = rnd.nextInt(h);
            g.fill(x, y, x + 1, y + 1, ((8 + rnd.nextInt(20)) << 24) | 0xFFFFFF);
        }
    }
}