package com.hypersomnia.client;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Рой мечущегося текста вместо толпы глаз [новое]: обрывки вопросов, обвинения и просто знаки вопроса вспыхивают в
 * случайных местах экрана, дрожат, дрейфуют и гаснут. Один и тот же рисовальщик в погоне (ChaseText) и в меню
 * антипаузы (SleepSceneOverlay.drawPauseEyes). Без состояния: всё выводится из (seed, номер слота, время), поэтому
 * раскладка воспроизводима, а число видимых строк задаёт вызывающий. Тексты — hypersomnia.swarm.0..LINES-1
 * (0..9 вопросы, 10..19 обвинения). Без глаз, без слова «insomnia»; красный #8A2030 не используется — текст читаем.
 */
public final class TextSwarm {
    private TextSwarm() {}

    /** Верхняя граница числа слотов. */
    public static final int MAX = 64;
    /** Число строк в lang: hypersomnia.swarm.0 .. hypersomnia.swarm.19; с LINES_QUESTIONS начинаются обвинения. */
    public static final int LINES = 20;
    private static final int LINES_QUESTIONS = 10;
    private static final String[] MARKS = {"?", "??", "?!", "???"};
    /** Доля слотов, где вместо фразы просто знак вопроса. */
    private static final float MARK_SHARE = 0.34f;
    /** Шаг дрожи: положение каждого слота меняется раз в JITTER_MS. */
    private static final int JITTER_MS = 70;
    private static final int QUESTION_RGB = 0xDDE6EC;
    private static final int ACCUSE_RGB = 0xE0B7BA;

    /**
     * @param count     сколько слотов живо (0..MAX)
     * @param intensity общая яркость 0..1
     * @param panic     0..1: усиливает дрожь и скорость
     * @param pulse     0..1: толчок размера в такт сердцу (0 — без толчка)
     * @param avoid     прямоугольники, куда текст не попадает, по четыре числа {cx, cy, halfW, halfH} в пикселях; может быть null
     */
    public static void draw(GuiGraphics g, int w, int h, long now, long seed, int count, float intensity,
                            float panic, float pulse, float[] avoid) {
        count = Math.min(count, MAX);
        if (count <= 0 || intensity <= 0.01f) return;
        Font font = Minecraft.getInstance().font;
        float jitterAmp = 2f + 12f * Mth.clamp(panic, 0f, 1f);
        float speed = 1f + 0.9f * Mth.clamp(panic, 0f, 1f);
        long q = now / JITTER_MS;
        for (int slot = 0; slot < count; slot++) {
            long base = mix(seed, slot);
            float life = (900f + unit(base, 1) * 2200f) / speed;
            // Только long-арифметика: now — миллисекунды эпохи, во float точность потерялась бы.
            long lifeMs = Math.max(1L, (long) life);
            long shifted = now + (long) (unit(base, 2) * lifeMs);
            long cycle = shifted / lifeMs;
            float f = (shifted % lifeMs) / (float) lifeMs;
            long ch = mix(base, cycle);
            float env = f < 0.12f ? f / 0.12f : f > 0.72f ? (1f - f) / 0.28f : 1f;
            float a = env * intensity * (0.35f + 0.65f * unit(ch, 12));
            if (unit(mix(base, q), 13) < 0.06f) a *= 0.3f; // редкая вспышка-пропуск
            int alpha = (int) (Mth.clamp(a, 0f, 1f) * 255f);
            if (alpha < 12) continue;

            Component text;
            float scale;
            int rgb;
            if (unit(ch, 3) < MARK_SHARE) {
                text = Component.literal(MARKS[Math.min(MARKS.length - 1, (int) (unit(ch, 4) * MARKS.length))]);
                scale = 1.5f + (float) Math.pow(unit(ch, 11), 1.8) * 6f;
                rgb = QUESTION_RGB;
            } else {
                int line = Math.min(LINES - 1, (int) (unit(ch, 5) * LINES));
                text = Component.translatable("hypersomnia.swarm." + line);
                scale = 0.8f + (float) Math.pow(unit(ch, 11), 2.0) * 2.2f;
                rgb = line >= LINES_QUESTIONS ? ACCUSE_RGB : QUESTION_RGB;
            }
            scale *= 1f + 0.12f * Mth.clamp(pulse, 0f, 1f);
            float textW = font.width(text);
            float tw = textW * scale;
            float th = 9f * scale;
            float x = unit(ch, 7) * w + (unit(ch, 9) - 0.5f) * 60f * f
                    + (unit(mix(base, q), 14) - 0.5f) * 2f * jitterAmp * scale * 0.5f;
            float y = unit(ch, 8) * h + (unit(mix(base, q), 15) - 0.5f) * 2f * jitterAmp * scale * 0.5f;
            x = tw + 8f < w ? Mth.clamp(x, tw / 2f + 4f, w - tw / 2f - 4f) : w / 2f;
            y = Mth.clamp(y, th / 2f + 2f, h - th / 2f - 2f);
            if (blocked(avoid, x, y, tw / 2f, th / 2f)) continue;
            float rot = (unit(ch, 10) - 0.5f) * 28f + (unit(mix(base, q), 16) - 0.5f) * 10f * panic;

            g.pose().pushPose();
            g.pose().translate(x, y, 0f);
            g.pose().mulPose(Axis.ZP.rotationDegrees(rot));
            g.pose().scale(scale, scale, 1f);
            g.drawString(font, text, -Math.round(textW / 2f), -4, (alpha << 24) | rgb, false);
            g.pose().popPose();
        }
        // Текст буферизуется: сбрасываем сразу, чтобы он не оказался поверх более поздних слоёв (пара глаз, чёрный кадр).
        g.flush();
    }

    private static boolean blocked(float[] avoid, float x, float y, float halfW, float halfH) {
        if (avoid == null) return false;
        for (int i = 0; i + 3 < avoid.length; i += 4) {
            if (Math.abs(x - avoid[i]) < avoid[i + 2] + halfW && Math.abs(y - avoid[i + 1]) < avoid[i + 3] + halfH) return true;
        }
        return false;
    }

    private static long mix(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return h;
    }

    /** Детерминированное 0..1 для (хэш, соль). */
    private static float unit(long h, int salt) {
        return (mix(h, salt) & 0xFFFFL) / 65535f;
    }
}
