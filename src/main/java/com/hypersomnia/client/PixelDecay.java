package com.hypersomnia.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Пиксельная эрозия кадра [новое]: по краям экрана изображение теряет крупные квадратные куски и медленно рассыпается
 * внутрь, как цифровая копия мира. Один и тот же рисовальщик в погоне (ChaseText) и в меню антипаузы
 * (SleepSceneOverlay.drawPauseEyes). Без состояния: всё выводится из (seed, номер блока, время), поэтому раскладка
 * воспроизводима. Куски собраны в кластеры по 1..3 клетки, живут короткими циклами и сносятся внутрь кадра; часть
 * возвращается — кадр «дышит». В дополнение идут редкие горизонтальные полосы-выпадения, как сбой записи. Центр и
 * путь впереди не задеваются (avoid). Почти всё — чёрные дыры палитры #03050A; редкие тусклые #1E2A34/#4A5E70
 * читаются как битые пиксели, красный #8A2030 — только редкий акцент. Глаза не рисует: они остаются только на лице
 * осколка.
 */
public final class PixelDecay {
    private PixelDecay() {}

    /** Верхняя граница числа кусков. */
    public static final int MAX_BLOCKS = 240;
    /** Шаг дрожания блоков: положение меняется раз в JITTER_MS. */
    private static final int JITTER_MS = 90;

    private static final int HOLE_RGB = 0x03050A;
    private static final int DIM_RGB = 0x1E2A34;
    private static final int PALE_RGB = 0x4A5E70;
    private static final int ACCENT_RGB = 0x8A2030;

    /**
     * @param intensity 0..1: сколько кусков и насколько ярко
     * @param panic     0..1: глубина проникновения, размер, дрейф и полосы
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
        // Размер клетки привязан к высоте экрана: на 1080p куски заметно крупнее, чем на 720p.
        float unitPx = Math.max(1f, h / 360f);
        float reach = h * (0.09f + 0.30f * p);
        long q = now / JITTER_MS;
        for (int slot = 0; slot < count; slot++) {
            long base = mix(seed, slot);
            int lifeMs = 300 + (int) (unit(base, 1) * 1100f);
            long shifted = now + (long) (unit(base, 2) * lifeMs);
            long cycle = shifted / lifeMs;
            float f = (shifted % lifeMs) / (float) lifeMs;
            float env = f < 0.16f ? f / 0.16f : f > 0.72f ? (1f - f) / 0.28f : 1f;
            long ch = mix(base, cycle);
            float a = env * amount * (0.60f + 0.40f * unit(ch, 3));
            int alpha = (int) (Mth.clamp(a, 0f, 1f) * 255f);
            if (alpha < 14) continue;

            int edge = Math.min(3, (int) (unit(ch, 4) * 4f));
            float depth = (float) Math.pow(unit(ch, 5), 2.4) * reach;
            float along = unit(ch, 6);
            int size = Math.max(2, Math.round((2.5f + unit(ch, 7) * 5.5f) * unitPx * (1f + 0.35f * p)));
            // Куски не стоят на месте: рождаются у края и медленно уносятся внутрь кадра.
            float drift = f * (6f + 34f * p) * unitPx;
            float x;
            float y;
            switch (edge) {
                case 0 -> { x = along * w; y = depth + drift; }
                case 1 -> { x = along * w; y = h - depth - drift; }
                case 2 -> { x = depth + drift; y = along * h; }
                default -> { x = w - depth - drift; y = along * h; }
            }
            // Мелкая дрожь, как у сбойного тайла, а не плавное движение.
            x += (unit(mix(base, q), 8) - 0.5f) * 4f;
            y += (unit(mix(base, q), 9) - 0.5f) * 4f;

            int cells = 1 + (int) (unit(ch, 11) * 3f);
            for (int cell = 0; cell < cells; cell++) {
                long cb = mix(ch, 20 + cell);
                float cx = x + (unit(cb, 1) < 0.5f ? 0f : (unit(cb, 2) < 0.5f ? -size : size));
                float cy = y + (unit(cb, 3) < 0.5f ? 0f : (unit(cb, 4) < 0.5f ? -size : size));
                if (blocked(avoid, cx, cy, size)) continue;
                cx = Mth.clamp(cx, 0f, w - size);
                cy = Mth.clamp(cy, 0f, h - size);

                float roll = unit(cb, 5);
                int rgb;
                if (roll < Mth.clamp(red, 0f, 1f) * 0.10f) rgb = ACCENT_RGB;
                else if (roll < 0.14f) rgb = PALE_RGB;
                else if (roll < 0.30f) rgb = DIM_RGB;
                else rgb = HOLE_RGB;
                int x0 = (int) cx;
                int y0 = (int) cy;
                g.fill(x0, y0, x0 + size, y0 + size, (alpha << 24) | rgb);
                // Светлая кромка у части дыр: эрозия видна и на тёмной земле, и на светлом небе.
                if (unit(cb, 6) < 0.30f) {
                    int edgeAlpha = Math.round(alpha * (0.25f + 0.30f * unit(cb, 7)));
                    g.fill(x0, y0, x0 + size, y0 + 1, (edgeAlpha << 24) | PALE_RGB);
                    g.fill(x0, y0, x0 + 1, y0 + size, (edgeAlpha << 24) | PALE_RGB);
                }
            }
        }
        // Полосы-выпадения: редкие тонкие срезы кадра, как потеря строк в старой записи.
        int bands = Math.round((1f + 5f * amount) * (0.35f + 0.65f * p));
        for (int band = 0; band < bands; band++) {
            long bb = mix(seed ^ 0x5DEECE66DL, band);
            int bandLife = 240 + (int) (unit(bb, 1) * 520f);
            long shifted = now + (long) (unit(bb, 2) * bandLife);
            long cycle = shifted / bandLife;
            float f = (shifted % bandLife) / (float) bandLife;
            float env = f < 0.25f ? f / 0.25f : f > 0.65f ? (1f - f) / 0.35f : 1f;
            long ch = mix(bb, cycle);
            int alpha = (int) (env * amount * (0.18f + 0.30f * unit(ch, 3)) * 255f);
            if (alpha < 10) continue;
            int y = (int) (unit(ch, 4) * h);
            int height = Math.max(1, Math.round((1f + unit(ch, 5) * 2f) * unitPx));
            int rgb = unit(ch, 6) < 0.25f ? PALE_RGB : HOLE_RGB;
            g.fill(0, y, w, Math.min(h, y + height), (alpha << 24) | rgb);
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
