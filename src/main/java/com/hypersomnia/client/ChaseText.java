package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.chase.ChaseTuning;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Рой мечущегося текста в погоне [новое, вместо глаз по углам]: вопросы, обвинения и знаки вопроса (TextSwarm).
 * Со временем погони строк становится больше, паника немного опережает рост; открытые строки не пропадают
 * до конца погони. Центр кадра свободен. Погоня кончилась — рой гаснет за SWARM_END_FADE_TICKS.
 * Слой chase_text (ChaseOverlay): над виньеткой, под чёрным кадром поимки. Числа — ChaseTuning.SWARM_*.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class ChaseText {
    private ChaseText() {}

    private static final RandomSource RANDOM = RandomSource.create();
    private static boolean active;
    private static boolean shown;
    private static long seed;
    private static int chaseTicks;
    private static int peakWanted;
    /** Общая яркость 0..1: растёт при начале погони, спадает после конца. */
    private static float fade;

    /** Новая погоня всегда начинает новую раскладку и отсчёт с нуля. */
    public static void begin() {
        seed = RANDOM.nextLong();
        active = true;
        shown = true;
        chaseTicks = 0;
        peakWanted = 0;
        fade = 0f;
    }

    /** Погоня кончилась: рой гаснет. */
    public static void end() {
        active = false;
    }

    public static void reset() {
        active = false;
        shown = false;
        chaseTicks = 0;
        peakWanted = 0;
        fade = 0f;
    }

    /** Цель растёт по времени; паника добавляет небольшой опережающий прирост, не заменяя течение времени. */
    private static int wanted(float p) {
        float time = Mth.clamp(chaseTicks / (float) ChaseTuning.SWARM_FILL_TICKS, 0f, 1f);
        float panic = Mth.clamp((p - ChaseTuning.PANIC_WARN) / (1f - ChaseTuning.PANIC_WARN), 0f, 1f);
        float progress = time + ChaseTuning.SWARM_PANIC_BOOST * panic * (1f - time);
        int start = Math.min(ChaseTuning.SWARM_START, ChaseTuning.SWARM_MAX);
        return Math.min(ChaseTuning.SWARM_MAX, start + Math.round(progress * (ChaseTuning.SWARM_MAX - start)));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!shown) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }
        if (mc.isPaused()) return;
        if (active && !ChaseClient.active()) end();
        if (active) {
            if (chaseTicks < ChaseTuning.SWARM_FILL_TICKS) chaseTicks++;
            peakWanted = Math.max(peakWanted, wanted(ChaseClient.panicNow()));
            fade = Math.min(1f, fade + 1f / ChaseTuning.SWARM_FADE_IN_TICKS);
        } else {
            fade = Math.max(0f, fade - 1f / ChaseTuning.SWARM_END_FADE_TICKS);
            if (fade <= 0f) shown = false;
        }
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        if (!shown || fade <= 0f) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth();
        int h = g.guiHeight();
        float[] avoid = {w / 2f, h / 2f, w * ChaseTuning.SWARM_AVOID_X, h * ChaseTuning.SWARM_AVOID_Y};
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        TextSwarm.draw(g, w, h, System.currentTimeMillis(), seed, peakWanted, ChaseTuning.SWARM_ALPHA * fade,
                ChaseClient.panicNow(), ChaseClient.pulse(partial), avoid);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
