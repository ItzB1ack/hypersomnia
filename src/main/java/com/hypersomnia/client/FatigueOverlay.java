package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Рисует усталость на экране: постоянную виньетку, а также потемнения и падение в сон («туннельное зрение»). */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FatigueOverlay {
    private FatigueOverlay() {}

    private static final ResourceLocation VIGNETTE =
            ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "textures/misc/fatigue_vignette.png");
    /** Прозрачный центр, сплошная чернота по краям. Масштабируется: чем меньше, тем уже «щель». */
    private static final ResourceLocation TUNNEL =
            ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "textures/misc/fatigue_tunnel.png");

    /** Во сколько раз текстура «туннеля» больше экрана при нулевом потемнении (за пределами видимости). */
    private static final float TUNNEL_OPEN_SCALE = 2.6f;
    /** Щель уже по вертикали, чем по горизонтали: сильнее похоже на веки. */
    private static final float TUNNEL_VERTICAL_SQUEEZE = 0.8f;

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        // Виньетка лежит под интерфейсом (хотбар и т.п. остаются чёткими)
        event.registerAbove(
                VanillaGuiLayers.CAMERA_OVERLAYS,
                ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "fatigue_vignette"),
                FatigueOverlay::renderVignette
        );
        // Потемнения и падение закрывают всё, включая интерфейс
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "fatigue_overlay"),
                FatigueOverlay::render
        );
    }

    private static void render(GuiGraphics g, DeltaTracker deltaTracker) {
        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        float amount = Math.max(
                ClientFatigueState.blackoutIntensity(partial),
                ClientFatigueState.collapseIntensity(partial)
        );
        if (amount <= 0.001f) return;

        int w = g.guiWidth();
        int h = g.guiHeight();

        // Лёгкая тёмная вуаль: мир тускнеет ещё до того, как края сомкнутся
        g.fill(0, 0, w, h, ((int) (amount * 70f)) << 24);

        // Туннельное зрение: прозрачная «щель» плавно сужается к центру. Ease-out: сначала быстро, к концу медленно.
        float k = Mth.lerp(1f - (1f - amount) * (1f - amount), TUNNEL_OPEN_SCALE, 0f);
        float hw = w * 0.5f * k;
        float hh = h * 0.5f * k * TUNNEL_VERTICAL_SQUEEZE;
        float cx = w * 0.5f;
        float cy = h * 0.5f;

        int x0 = Math.round(cx - hw);
        int x1 = Math.round(cx + hw);
        int y0 = Math.round(cy - hh);
        int y1 = Math.round(cy + hh);

        if (x1 - x0 >= 2 && y1 - y0 >= 2) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.blit(TUNNEL, x0, y0, 0f, 0f, x1 - x0, y1 - y0, x1 - x0, y1 - y0);
            RenderSystem.disableBlend();
        }

        // Всё, что вне текстуры, закрашиваем чёрным (когда щель меньше экрана)
        int cy0 = Mth.clamp(y0, 0, h);
        int cy1 = Mth.clamp(y1, 0, h);
        int cx0 = Mth.clamp(x0, 0, w);
        int cx1 = Mth.clamp(x1, 0, w);
        if (x1 - x0 < 2 || y1 - y0 < 2) {
            g.fill(0, 0, w, h, 0xFF000000);
            return;
        }
        if (cy0 > 0) g.fill(0, 0, w, cy0, 0xFF000000);
        if (cy1 < h) g.fill(0, cy1, w, h, 0xFF000000);
        if (cx0 > 0 && cy1 > cy0) g.fill(0, cy0, cx0, cy1, 0xFF000000);
        if (cx1 < w && cy1 > cy0) g.fill(cx1, cy0, w, cy1, 0xFF000000);
    }

    private static void renderVignette(GuiGraphics g, DeltaTracker deltaTracker) {
        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        float amount = ClientFatigueState.vignetteIntensity(partial);
        if (amount <= 0.005f) return;

        int w = g.guiWidth();
        int h = g.guiHeight();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1f, 1f, 1f, amount);
        g.blit(VIGNETTE, 0, 0, -90, 0f, 0f, w, h, w, h);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
