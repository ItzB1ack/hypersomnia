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
    /** Фраза собирается из пиксельной пыли за ASSEMBLE_MS мс, а не появляется готовой строкой [новое]. */
    private static final int ASSEMBLE_MS = 620;
    /** Максимум пиксельных точек на одну сборку. */
    private static final int ASSEMBLE_DOTS = 30;
    /** Полос, из которых по одной проявляются строки: буквы собираются рядами пикселей. */
    private static final int REVEAL_BANDS = 7;
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
            // Сборка из пикселей: в начале жизни строка ещё не готова, точки слетаются к её месту.
            // На высокой панике жизнь короче, поэтому сборка не должна съесть всю вспышку.
            float asmMs = Math.min(ASSEMBLE_MS, lifeMs * 0.5f);
            float asm = Mth.clamp(f * lifeMs / asmMs, 0f, 1f);

            Component text;
            float scale;
            int rgb;
            if (unit(ch, 3) < MARK_SHARE) {
                text = Component.literal(MARKS[Math.min(MARKS.length - 1, (int) (unit(ch, 4) * MARKS.length))]);
                scale = 2f + (float) Math.pow(unit(ch, 11), 1.8) * 6f;
                rgb = QUESTION_RGB;
            } else {
                int line = Math.min(LINES - 1, (int) (unit(ch, 5) * LINES));
                text = Component.translatable("hypersomnia.swarm." + line);
                scale = 1.3f + (float) Math.pow(unit(ch, 11), 1.6) * 2.7f;
                rgb = line >= LINES_QUESTIONS ? ACCUSE_RGB : QUESTION_RGB;
            }
            scale *= 1f + 0.12f * Mth.clamp(pulse, 0f, 1f);
            float textW = font.width(text);
            scale = Math.min(scale, Math.max(1f, (w - 16f) / Math.max(1f, textW)));
            float tw = textW * scale;
            float th = 9f * scale;
            float x = unit(ch, 7) * w + (unit(ch, 9) - 0.5f) * 60f * f
                    + (unit(mix(base, q), 14) - 0.5f) * 2f * jitterAmp * scale * 0.5f;
            float y = unit(ch, 8) * h + (unit(mix(base, q), 15) - 0.5f) * 2f * jitterAmp * scale * 0.5f;
            x = tw + 8f < w ? Mth.clamp(x, tw / 2f + 4f, w - tw / 2f - 4f) : w / 2f;
            y = Mth.clamp(y, th / 2f + 2f, h - th / 2f - 2f);
            if (blocked(avoid, x, y, tw / 2f, th / 2f)) continue;
            float rot = (unit(ch, 10) - 0.5f) * 28f + (unit(mix(base, q), 16) - 0.5f) * 10f * panic;

            // Сборка: густая пиксельная пыль слетается к прямоугольнику строки, пока буквы не проявятся.
            if (asm < 1f) {
                int dots = Math.min(ASSEMBLE_DOTS, 8 + (int) (tw / 8f));
                for (int dot = 0; dot < dots; dot++) {
                    long db = mix(ch, 30 + dot);
                    float angle = unit(db, 1) * 6.2832f;
                    float dist = (10f + unit(db, 2) * 62f) * scale * (1f - asm);
                    float dotX = x + (unit(db, 3) - 0.5f) * tw + (float) Math.cos(angle) * dist;
                    float dotY = y + (unit(db, 4) - 0.5f) * th + (float) Math.sin(angle) * dist;
                    int dotAlpha = (int) (alpha * (0.95f - 0.35f * asm));
                    if (dotAlpha < 12) continue;
                    int dotSize = Math.max(1, Math.round((1f + unit(db, 5) * 2.4f) * Math.max(1f, scale * 0.55f)));
                    g.fill((int) dotX, (int) dotY, (int) dotX + dotSize, (int) dotY + dotSize,
                            (dotAlpha << 24) | rgb);
                }
            }
            int textAlpha = Math.round(alpha * (0.4f + 0.6f * asm));

            if (asm >= 1f) {
                g.pose().pushPose();
                g.pose().translate(x, y, 0f);
                g.pose().mulPose(Axis.ZP.rotationDegrees(rot));
                g.pose().scale(scale, scale, 1f);
                g.drawString(font, text, -Math.round(textW / 2f), -4, (textAlpha << 24) | rgb, false);
                g.pose().popPose();
            } else {
                // Буквы проявляются не целиком, а случайными рядами пикселей: строка «собирается» из шума.
                float left = x - tw / 2f - 8f;
                float right = x + tw / 2f + 8f;
                float top = y - th / 2f - 3f;
                float bottom = y + th / 2f + 3f;
                for (int band = 0; band < REVEAL_BANDS; band++) {
                    if (asm <= unit(ch, 40 + band) * 0.9f) continue;
                    int bandTop = Math.round(top + (bottom - top) * band / REVEAL_BANDS);
                    int bandBottom = Math.round(top + (bottom - top) * (band + 1) / REVEAL_BANDS);
                    g.enableScissor((int) left, bandTop, (int) right, bandBottom);
                    g.pose().pushPose();
                    g.pose().translate(x, y, 0f);
                    g.pose().mulPose(Axis.ZP.rotationDegrees(rot));
                    g.pose().scale(scale, scale, 1f);
                    g.drawString(font, text, -Math.round(textW / 2f), -4, (textAlpha << 24) | rgb, false);
                    g.pose().popPose();
                    g.disableScissor();
                }
            }
            // После сборки на буквах ещё держатся одиночные пиксели — строка не выглядит «чистой» наклейкой.
            if (asm >= 1f) {
                int motes = 2 + (int) (unit(ch, 61) * 4f);
                for (int mote = 0; mote < motes; mote++) {
                    long mb = mix(ch, 70 + mote);
                    float moteX = x + (unit(mb, 1) - 0.5f) * tw;
                    float moteY = y + (unit(mb, 2) - 0.5f) * th;
                    int moteAlpha = (int) (alpha * (0.25f + 0.35f * unit(mb, 3)));
                    if (moteAlpha < 12) continue;
                    int moteSize = 1 + (int) (unit(mb, 4) * 2f);
                    g.fill((int) moteX, (int) moteY, (int) moteX + moteSize, (int) moteY + moteSize,
                            (moteAlpha << 24) | rgb);
                }
            }
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
