package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Погоня на экране (ChaseClient): виньетка в такт сердцу (под интерфейсом), глаза по углам (ChaseEyes, над
 * виньеткой) и чёрный кадр поимки — поверх интерфейса, но под ванильными субтитрами: его слова при поимке
 * (FragmentVoice) читаются на чёрном. Слои регистрируются в одном месте, чтобы порядок был точным.
 */
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
        ResourceLocation vignette = ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "chase_vignette");
        event.registerAbove(vignette,
                ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "chase_eyes"), ChaseEyes::render);
        event.registerBelow(VanillaGuiLayers.SUBTITLE_OVERLAY,
                ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "chase_rage"), ChaseOverlay::renderRage);
        event.registerBelow(VanillaGuiLayers.SUBTITLE_OVERLAY,
                ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "chase_black"), ChaseOverlay::renderBlack);
    }

    /** F3+T и смена пакетов ресурсов: тайминги реплик перечитываются. */
    @SubscribeEvent
    public static void registerReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) rm -> FragmentVoice.clearCaches());
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

    private static void renderRage(GuiGraphics g, DeltaTracker delta) {
        int line = ChaseClient.rageLine();
        if (line < 0) return;
        int w = g.guiWidth(), h = g.guiHeight();
        g.fill(0, 0, w, 2, 0x668A2030);
        g.fill(0, h - 2, w, h, 0x668A2030);
        Component text = Component.translatable("hypersomnia.chase.rage." + line);
        int textWidth = Minecraft.getInstance().font.width(text);
        int y = (int)(h * .72f);
        g.fill(w / 2 - textWidth / 2 - 5, y - 3, w / 2 + textWidth / 2 + 5, y + 12, 0x9903050A);
        g.drawCenteredString(Minecraft.getInstance().font, text, w / 2, y, 0xFFE0B7BA);
    }

    private static void renderBlack(GuiGraphics g, DeltaTracker delta) {
        float a = ChaseClient.blackout(delta.getGameTimeDeltaPartialTick(false));
        if (a <= 0.003f) return;
        int alpha = Math.round(a * 255f) & 0xFF;
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (alpha << 24) | BLACK_RGB);
    }
}
