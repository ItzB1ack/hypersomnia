package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Погоня на экране (ChaseClient): виньетка в такт сердцу (под интерфейсом) и чёрный кадр поимки (поверх всего). */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ChaseOverlay {
    private ChaseOverlay() {}

    /** Та же текстура, что у усталости: прозрачный центр, тёмные края. */
    private static final ResourceLocation VIGNETTE =
            ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "textures/misc/fatigue_vignette.png");
    /** Не чистый чёрный: «темнота за веками». */
    private static final int BLACK_RGB = 0x03050A;

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS,
                ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "chase_vignette"), ChaseOverlay::renderVignette);
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "chase_black"), ChaseOverlay::renderBlack);
    }

    private static void renderVignette(GuiGraphics g, DeltaTracker delta) {
        float a = ChaseClient.vignette(delta.getGameTimeDeltaPartialTick(false));
        if (a <= 0.005f) return;
        int w = g.guiWidth();
        int h = g.guiHeight();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1f, 1f, 1f, a);
        g.blit(VIGNETTE, 0, 0, -90, 0f, 0f, w, h, w, h);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static void renderBlack(GuiGraphics g, DeltaTracker delta) {
        float a = ChaseClient.blackout(delta.getGameTimeDeltaPartialTick(false));
        if (a <= 0.003f) return;
        int alpha = Math.round(a * 255f) & 0xFF;
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (alpha << 24) | BLACK_RGB);
    }
}
