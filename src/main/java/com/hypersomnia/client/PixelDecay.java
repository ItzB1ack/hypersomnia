package com.hypersomnia.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Пиксельная эрозия кадра [новое]: по краям экрана изображение теряет квадратные пиксели и медленно рассыпается
 * внутрь, как цифровая копия мира. Один и тот же рисовальщик в погоне (ChaseText) и в меню антипаузы
 * (SleepSceneOverlay.drawPauseEyes). Без состояния: всё выводится из (seed, номер блока, время), поэтому раскладка
 * воспроизводима. Блоки живут короткими циклами, часть возвращается — кадр «дышит». Центр и путь впереди не
 * задеваются (avoid). Почти всё — чёрные дыры палитры #03050A; редкие тусклые #1E2A34/#4A5E70 читаются как битые
 * пиксели, красный #8A2030 — только редкий акцент. Глаза не рисует: они остаются только на лице осколка.
 */
public final class PixelDecay {
    private PixelDecay() {}

    /** Верхняя граница числа блоков. */
    public static final int MAX_BLOCKS = 170;
    /** Шаг дрожания блоков: положение меняется раз в JITTER_MS. */
    private static final int JITTER_MS = 90;

    private static final int HOLE_RGB = 0x03050A;
    private static final int DIM_RGB = 0x1E2A34;
    private static final int PALE_RGB = 0x4A5E70;
    private static final int ACCENT_RGB = 0x8A2030;

    /**
     * @param intensity 0..1: сколько блоков и насколько ярко
     * @param panic     0..1: глубина проникновения, размер и дрожь
     * @param red       0..1: доля красных акцентов (0 — совсем нет)
     * @param avoid     прямоугольники, куда блоки не заходят, по четыре числа {cx, cy, halfW, halfH} в пикселях; может быть null
     */
    public static void draw(GuiGraphics g, int w, int h, long now, long seed, float intensity, float panic,
                            float red, float[] avoid) {
        float amount = Mth.clamp(intensity, 0f, 1f);
        if (amount <= 0.01f || w <= 0 || h <= 0) return;
        int count = Math.min(MAX_BLOCKS, Math.round(MAX_BLOCKS * amount));
        if (count <= 0) return;
        float p = Mth.clamp(panic, 0f, 1f);
        float reach = h * (0.07f + 0.22f * p);
        long q = now / JITTER_MS;
        for (int slot = 0; slot < count; slot++) {
            long base = mix(seed, slot);
            int lifeMs = 320 + (int) (unit(base, 1) * 1000f);
            long shifted = now + (long) (unit(base, 2) * lifeMs);
            long cycle = shifted / lifeMs;
            float f = (shifted % lifeMs) / (float) lifeMs;
            float env = f < 0.2f ? f / 0.2f : f > 0.7f ? (1f - f) / 0.3f : 1f;
            long ch = mix(base, cycle);
            float a = env * amount * (0.55f + 0.45f * unit(ch, 3));
            int alpha = (int) (Mth.clamp(a, 0f, 1f) * 255f);
            if (alpha < 14) continue;

            int edge = Math.min(3, (int) (unit(ch, 4) * 4f));
            float depth = (float) Math.pow(unit(ch, 5), 2.0) * reach;
            float along = unit(ch, 6);
            int size = Math.max(1, Math.round(2f + unit(ch, 7) * (4f + 5f * p)));
            float x;
            float y;
            switch (edge) {
                case 0 -> { x = along * w; y = depth; }
                case 1 -> { x = along * w; y = h - depth; }
                case 2 -> { x = depth; y = along * h; }
                default -> { x = w - depth; y = along * h; }
            }
            // Блоки мелко дрожат, как сбойные тайлы, а не плывут плавно.
            x += (unit(mix(base, q), 8) - 0.5f) * 3f;
            y += (unit(mix(base, q), 9) - 0.5f) * 3f;
            if (blocked(avoid, x, y, size)) continue;
            x = Mth.clamp(x, 0f, w - size);
            y = Mth.clamp(y, 0f, h - size);

            float roll = unit(ch, 10);
            int rgb;
            if (roll < Mth.clamp(red, 0f, 1f) * 0.10f) rgb = ACCENT_RGB;
            else if (roll < 0.18f) rgb = PALE_RGB;
            else if (roll < 0.34f) rgb = DIM_RGB;
            else rgb = HOLE_RGB;
            g.fill((int) x, (int) y, (int) x + size, (int) y + size, (alpha << 24) | rgb);
        }
    }

    private static boolean blocked(float[] avoid, float x, float y, int size) {
        if (avoid == null) return false;
        float half = size / 2f;
        float cx = x + half;
        float cy = y + half;
        for (int i = 0; i + 3 < avoid.length; i += 4) {
            if (Math.abs(cx - avoid[i]) < avoid[i + 2] + half && Math.abs(cy - avoid[i + 1]) < avoid[i + 3] + half) {
                return true;
            }
        }
        return false;
    }

    private static long mix(long a, long b) {
        long hash = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
        hash ^= hash >>> 29;
        hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 32;
        return hash;
    }

    /** Детерминированное 0..1 для (хэш, соль). */
    private static float unit(long h, int salt) {
        return (mix(h, salt) & 0xFFFFL) / 65535f;
    }
}
