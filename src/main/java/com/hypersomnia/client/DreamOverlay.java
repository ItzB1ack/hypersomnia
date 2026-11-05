package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * «Аналоговая запись» поверх мира снов: холодный оттенок, строки развёртки, зерно,
 * бегущая полоса, редкие сбои и таймкод. Плюс затемнение при входе и выходе.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DreamOverlay {
    private DreamOverlay() {}

    private static final ResourceLocation VIGNETTE =
            ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "textures/misc/fatigue_vignette.png");

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.CAMERA_OVERLAYS,
                ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "dream_filter"),
                DreamOverlay::renderFilter
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "dream_fade"),
                DreamOverlay::renderFade
        );
    }

    private static void renderFilter(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!DreamClientState.isDream(mc)) return;

        int w = g.guiWidth();
        int h = g.guiHeight();
        long now = System.currentTimeMillis();
        // Зерно и сбои меняются ~16 раз в секунду, как кадры плёнки
        RandomSource rnd = RandomSource.create(now / 60L);

        // 1) Статичная виньетка по краям кадра
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1f, 1f, 1f, 0.55f);
        g.blit(VIGNETTE, 0, 0, -90, 0f, 0f, w, h, w, h);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        // 2) Холодный сдвиг цвета
        g.fill(0, 0, w, h, 0x30102A3C);

        // 3) Строки развёртки
        for (int y = 0; y < h; y += 3) {
            g.fill(0, y, w, y + 1, 0x26000000);
        }

        // 4) Зерно
        int grains = Math.max(120, w * h / 900);
        for (int i = 0; i < grains; i++) {
            int x = rnd.nextInt(w);
            int y = rnd.nextInt(h);
            int a = 10 + rnd.nextInt(26);
            int c = rnd.nextBoolean() ? 0xFFFFFF : 0x000000;
            g.fill(x, y, x + 1 + rnd.nextInt(2), y + 1, (a << 24) | c);
        }

        // 5) Бегущая светлая полоса (как на старой кассете)
        float roll = (now % 6500L) / 6500f;
        int bandY = (int) (roll * (h + 40)) - 20;
        g.fill(0, bandY, w, bandY + 8, 0x0DFFFFFF);
        g.fill(0, bandY + 8, w, bandY + 12, 0x06FFFFFF);

        // 6) Редкий сбой: тонкая яркая линия и смещённый кусок кадра
        if (rnd.nextInt(70) == 0) {
            int gy = rnd.nextInt(h);
            g.fill(0, gy, w, gy + 1 + rnd.nextInt(3), 0x40FFFFFF);
            g.fill(0, gy + 3, w, gy + 5, 0x18FF3050);
        }

        // 7) Таймкод и режим
        float ticks = DreamClientState.ticksInDream + deltaTracker.getGameTimeDeltaPartialTick(false);
        int total = (int) (ticks / 20f);
        int frames = (int) ((ticks % 20f) * 1.5f);
        String timecode = String.format("%02d:%02d:%02d", total / 60, total % 60, frames);
        boolean blink = (now / 600L) % 2 == 0;

        g.drawString(mc.font, "PLAY", 10, 8, 0xFFE8E8E8, true);
        if (blink) g.drawString(mc.font, "●", 10 + mc.font.width("PLAY ") + 2, 8, 0xFFE05050, true);
        g.drawString(mc.font, timecode, w - 10 - mc.font.width(timecode), h - 16, 0xFFE8E8E8, true);
    }

    /** Чёрная вуаль, которая медленно спадает после перехода между мирами. */
    private static void renderFade(GuiGraphics g, DeltaTracker deltaTracker) {
        if (DreamClientState.fadeTicks <= 0) return;

        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        float t = Mth.clamp((DreamClientState.fadeTicks - partial) / DreamClientState.FADE_TICKS, 0f, 1f);
        float alpha = t * t * (3f - 2f * t);
        int a = (int) (alpha * 255f);
        if (a <= 0) return;
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), a << 24);
    }
}
