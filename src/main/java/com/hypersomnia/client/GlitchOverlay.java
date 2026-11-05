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

/** Оверлей сбоя: вуаль 0x03050A, полосы развёртки, светлая бегущая полоса, служебная строка. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GlitchOverlay {
    private GlitchOverlay() {}

    private static final String[] LINES = {"SIGNAL LOST", "REC", "03:12"};

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "glitch"),
                GlitchOverlay::render
        );
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        if (!GlitchState.active() || SleepSceneState.active()) return;
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth();
        int h = g.guiHeight();
        int frame = (int) (System.nanoTime() / 50_000_000L); // 20 fps
        RandomSource rnd = RandomSource.create(frame);

        g.fill(0, 0, w, h, 0xD003050A);
        for (int y = 0; y < h; y += 2) g.fill(0, y, w, y + 1, 0x22000000);

        int band = ((frame * 13) % (h + 40)) - 20;
        int bandH = 6 + rnd.nextInt(6);
        g.fill(0, band, w, band + bandH, ((50 + rnd.nextInt(40)) << 24) | 0x4A5E70);

        int strips = 2 + rnd.nextInt(3);
        for (int i = 0; i < strips; i++) {
            int y = rnd.nextInt(h);
            int sh = 1 + rnd.nextInt(3);
            g.fill(0, y, w, y + sh, ((30 + rnd.nextInt(40)) << 24) | 0x8A2030);
        }

        Component text = Component.translatableWithFallback(
                "hypersomnia.glitch." + GlitchState.lineIndex, LINES[GlitchState.lineIndex]);
        int tw = Math.max(1, mc.font.width(text));
        g.pose().pushPose();
        g.pose().translate(w / 2f, h * 0.86f, 0f);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.fill(-tw / 2 - 3, -2, tw / 2 + 3, 9, 0x99000000);
        g.drawCenteredString(mc.font, text, 0, 0, 0xFFDDE6EC);
        g.pose().popPose();
    }
}