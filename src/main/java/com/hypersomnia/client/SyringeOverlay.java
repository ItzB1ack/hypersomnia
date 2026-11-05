package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.minecraft.resources.ResourceLocation;

/** Сердцебиение: красноватые удары по краям экрана при побочках (уровень 1 и выше). */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SyringeOverlay {
    private SyringeOverlay() {}

    private static final ResourceLocation VIGNETTE =
            ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "textures/misc/fatigue_vignette.png");

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.CAMERA_OVERLAYS,
                ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "syringe_pulse"),
                SyringeOverlay::render
        );
    }

    private static void render(GuiGraphics g, DeltaTracker dt) {
        if (!SyringeClientState.active || SyringeClientState.level < 1) return;

        double bpm = 78.0 + 18.0 * SyringeClientState.level;
        double phase = (System.currentTimeMillis() / 1000.0 * bpm / 60.0) % 1.0;
        // Двойной удар: два быстрых всплеска за период
        double beat = Math.max(spike(phase, 0.00), 0.7 * spike(phase, 0.22));
        float a = (float) (beat * (0.10 + 0.08 * SyringeClientState.level));
        if (a <= 0.005f) return;

        int w = g.guiWidth();
        int h = g.guiHeight();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1f, 0.25f, 0.3f, Math.min(a, 0.6f));
        g.blit(VIGNETTE, 0, 0, -90, 0f, 0f, w, h, w, h);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static double spike(double phase, double at) {
        double d = phase - at;
        if (d < 0) return 0;
        return Math.exp(-d * 28.0);
    }
}
