package com.hypersomnia.client;

import com.hypersomnia.chase.CaughtTuning;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * [новое] Скример поимки: поверх чёрного кадра погони. Сначала белая вспышка с чёрным разрывом,
 * затем лицо осколка (пара глаз, SleepSceneOverlay.drawScareEyes в замороженном взгляде) влетает
 * в камеру: масштаб ZOOM_NEAR→ZOOM_FAR, наклон TILT_FROM→TILT_TO, тряска самого кадра, красная
 * кромка и полосы развёртки. К концу FADE_START кадр гаснет в чёрный, оставляя место голосу
 * осколка. Регистрируется в ChaseOverlay сразу над chase_black (под субтитрами).
 */
public final class CaughtScareOverlay {
    private CaughtScareOverlay() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        if (!CaughtScare.active()) return;
        int w = g.guiWidth();
        int h = g.guiHeight();
        int e = CaughtScare.elapsed();

        if (e < CaughtTuning.FLASH_TICKS) {
            drawFlash(g, w, h, e);
            return;
        }

        float t = Mth.clamp((e - CaughtTuning.FLASH_TICKS) / (float) Math.max(1, CaughtTuning.LUNGE_TICKS), 0f, 1f);
        float lunge = t * t * (3f - 2f * t);
        float zoom = CaughtTuning.ZOOM_NEAR + (CaughtTuning.ZOOM_FAR - CaughtTuning.ZOOM_NEAR) * lunge;
        float tilt = CaughtTuning.TILT_FROM + (CaughtTuning.TILT_TO - CaughtTuning.TILT_FROM) * lunge;

        RandomSource rnd = RandomSource.create(System.nanoTime());
        float jx = (rnd.nextFloat() - 0.5f) * 2f * w * CaughtTuning.JITTER_X;
        float jy = (rnd.nextFloat() - 0.5f) * 2f * h * CaughtTuning.JITTER_Y;

        g.fill(0, 0, w, h, 0xFF03050A);
        g.pose().pushPose();
        g.pose().translate(w / 2f + jx, h * 0.42f + jy, 0f);
        g.pose().mulPose(Axis.ZP.rotationDegrees(tilt));
        g.pose().scale(zoom, zoom, 1f);
        g.pose().translate(-w / 2f, -h * 0.42f, 0f);
        SleepSceneOverlay.drawScareEyes(g, w, h, true);
        g.pose().popPose();

        // Полосы развёртки и пыль поверх лица: кадр «сломан».
        for (int i = 0; i < 6; i++) {
            int y = rnd.nextInt(Math.max(1, h));
            int hh = Math.max(1, h / 120 + rnd.nextInt(Math.max(2, h / 60)));
            int c = rnd.nextInt(3) == 0 ? 0xAA1622 : (rnd.nextBoolean() ? 0xFFFFFF : 0x08080A);
            int a = 25 + rnd.nextInt(70);
            g.fill(0, y, w, Math.min(h, y + hh), (a << 24) | c);
        }
        int specks = Math.max(140, w * h / 900);
        for (int i = 0; i < specks; i++) {
            int x = rnd.nextInt(w);
            int y = rnd.nextInt(h);
            int a = 12 + rnd.nextInt(45);
            int c = rnd.nextBoolean() ? 0xFFFFFF : 0xAA1622;
            g.fill(x, y, x + 1 + rnd.nextInt(2), y + 1, (a << 24) | c);
        }
        // Красная кромка и тонкая линия на уровне глаз — «лицо в рамке».
        int rim = Math.max(3, h / 60);
        g.fill(0, 0, w, rim, 0x77AA1622);
        g.fill(0, h - rim, w, h, 0x77AA1622);
        g.fill(0, (int) (h * 0.42f), w, (int) (h * 0.42f) + 1, 0x55AA1622);

        if (e >= CaughtTuning.FADE_START) {
            float f = (e - CaughtTuning.FADE_START) / (float) Math.max(1, CaughtTuning.SCARE_TICKS - CaughtTuning.FADE_START);
            int a = Math.round(Mth.clamp(f, 0f, 1f) * 255f) & 0xFF;
            g.fill(0, 0, w, h, (a << 24) | 0x03050A);
        }
    }

    /** Белая вспышка с чёрным разрывом по диагонали и красной кромкой. */
    private static void drawFlash(GuiGraphics g, int w, int h, int e) {
        int alpha = Math.round(CaughtTuning.FLASH_ALPHA * 255f) & 0xFF;
        g.fill(0, 0, w, h, (alpha << 24) | 0xF6F1E9);
        g.pose().pushPose();
        g.pose().translate(w / 2f, h / 2f, 0f);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-16f + e * 5f));
        int thick = Math.max(2, h / 70);
        g.fill(-w, -thick / 2, w, thick / 2, 0xFF08080A);
        g.pose().popPose();
        int rim = Math.max(3, h / 55);
        g.fill(0, 0, w, rim, 0xBBAA1622);
        g.fill(0, h - rim, w, h, 0xBBAA1622);
    }
}
