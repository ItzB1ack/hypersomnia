package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.document.MorseCipher;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Шифр 3 на странице: бьющееся сердце и кардиограмма. Низкий скачок = точка, высокий = тире.
 * Красный бегунок идёт по линии, сердце бьётся на каждом зубце (на тире сильнее).
 * printed = false: линии нет, только сердце (бьётся в том же ритме), читать можно только по ударам.
 */
public final class HeartMorse {
    private HeartMorse() {}

    private static final ResourceLocation HEART =
            ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "textures/misc/heart.png");
    private static final int TEX = 96;

    // Раскладка (пиксели бумаги)
    private static final float SYM = 12f;
    private static final float LETTER_GAP = 12f;
    private static final float WORD_GAP = 36f;
    private static final int ROW_H = 30;
    private static final int HEART_COL = 40;
    private static final float HEART_SIZE = 32f;
    private static final float HEART_SIZE_ALONE = 48f;
    private static final float DOT_H = 6f;
    private static final float DASH_H = 15f;

    // Время
    /** Скорость бегунка, пикселей в секунду (символ = 12 px, то есть 0,4 с). */
    private static final float SPEED = 30f;
    /** Пауза в конце цикла, пикселей. */
    private static final float PAUSE = 60f;
    private static final float PULSE_DOT = 0.12f;
    private static final float PULSE_DASH = 0.30f;
    private static final float PULSE_DECAY = 7f;
    private static final float TAIL = 26f;

    private static final int INK = 0xB01E2A34;
    private static final int RED = 0xFF8A2030;

    private record Spike(int row, float x, boolean dash, float d) {}

    private static List<Spike> layout(String raw, float rowLen, int[] rowsOut) {
        List<Spike> out = new ArrayList<>();
        int row = 0;
        float x = 0f;
        boolean firstWord = true;
        for (String word : raw.toUpperCase(Locale.ROOT).split(" ")) {
            if (word.isEmpty()) continue;
            if (!firstWord) x += WORD_GAP;
            firstWord = false;
            boolean firstLetter = true;
            for (char ch : word.toCharArray()) {
                String code = MorseCipher.code(ch);
                if (code.isEmpty()) continue;
                if (!firstLetter) x += LETTER_GAP;
                firstLetter = false;
                float w = code.length() * SYM;
                if (x > 0f && x + w > rowLen) {
                    row++;
                    x = 0f;
                }
                for (int k = 0; k < code.length(); k++) {
                    float sx = x + k * SYM;
                    out.add(new Spike(row, sx, code.charAt(k) == '-', row * rowLen + sx + SYM / 2f));
                }
                x += w;
            }
        }
        rowsOut[0] = row + 1;
        return out;
    }

    /** Рисует блок и возвращает новую y. draw = false только считает высоту. */
    public static int draw(GuiGraphics g, boolean draw, int left, int y, int width, String raw, boolean printed) {
        float rowLen = printed ? width - HEART_COL : width;
        int[] rows = new int[1];
        List<Spike> spikes = layout(raw, rowLen, rows);
        if (spikes.isEmpty()) return y;
        int height = printed ? rows[0] * ROW_H + 4 : 56;
        if (!draw) return y + height + 6;

        float cycle = spikes.get(spikes.size() - 1).d() + SYM + PAUSE;
        long periodMs = Math.max(1L, (long) (cycle / SPEED * 1000f));
        float c = (System.currentTimeMillis() % periodMs) / 1000f * SPEED;

        // Сила удара: самый свежий зубец, затухает за доли секунды
        float pulse = 0f;
        for (Spike s : spikes) {
            float dt = ((c - s.d()) % cycle + cycle) % cycle / SPEED;
            if (dt > 0.8f) continue;
            pulse = Math.max(pulse, (s.dash() ? PULSE_DASH : PULSE_DOT) * (float) Math.exp(-PULSE_DECAY * dt));
        }

        float traceX = left + (printed ? HEART_COL : 0);
        if (printed) {
            for (int r = 0; r < rows[0]; r++) trace(g, spikes, r, traceX, y + r * ROW_H + ROW_H / 2 + 2, rowLen, c);
            int cr = (int) (c / rowLen);
            if (cr < rows[0]) {
                int hx = Math.round(traceX + c - cr * rowLen);
                int hy = y + cr * ROW_H + ROW_H / 2 + 2;
                g.fill(hx - 1, hy - 1, hx + 2, hy + 2, RED);
            }
        }

        float cx = printed ? left + HEART_COL / 2f : left + width / 2f;
        float cy = y + (printed ? rows[0] * ROW_H / 2f : 28f) + 2f;
        float sc = (printed ? HEART_SIZE : HEART_SIZE_ALONE) / TEX * (1f + pulse);
        RenderSystem.enableBlend();
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0f);
        g.pose().scale(sc, sc, 1f);
        g.blit(HEART, -TEX / 2, -TEX / 2, 0, 0f, 0f, TEX, TEX, TEX, TEX);
        g.pose().popPose();
        return y + height + 6;
    }

    /** Линия одной строки: ровная с зубцами; участок за бегунком (хвост) красный. */
    private static void trace(GuiGraphics g, List<Spike> spikes, int row, float x0, float yb, float rowLen, float c) {
        float[] cur = {0f, yb};   // текущая точка: x от начала линии, y
        for (Spike s : spikes) {
            if (s.row() != row) continue;
            float h = s.dash() ? DASH_H : DOT_H;
            seg(g, cur, x0, s.x() + 2f, yb, row, rowLen, c);
            seg(g, cur, x0, s.x() + 4f, yb - h, row, rowLen, c);
            seg(g, cur, x0, s.x() + 6f, yb + h * 0.4f, row, rowLen, c);
            seg(g, cur, x0, s.x() + 7.5f, yb, row, rowLen, c);
        }
        seg(g, cur, x0, rowLen, yb, row, rowLen, c);
    }

    /** Отрезок от cur до (x1, y1) кусками по ~4 px, цвет по расстоянию до бегунка; cur сдвигается в конец. */
    private static void seg(GuiGraphics g, float[] cur, float x0, float x1, float y1, int row, float rowLen, float c) {
        float xa = cur[0], ya = cur[1];
        int n = Math.max(1, (int) Math.ceil(Math.max(Math.abs(x1 - xa), Math.abs(y1 - ya)) / 4f));
        for (int i = 0; i < n; i++) {
            float t0 = i / (float) n, t1 = (i + 1) / (float) n;
            float ax = xa + (x1 - xa) * t0, ay = ya + (y1 - ya) * t0;
            float bx = xa + (x1 - xa) * t1, by = ya + (y1 - ya) * t1;
            float dist = row * rowLen + (ax + bx) / 2f;
            line(g, x0 + ax, ay, x0 + bx, by, (c - dist >= 0f && c - dist <= TAIL) ? RED : INK);
        }
        cur[0] = x1;
        cur[1] = y1;
    }
    private static void line(GuiGraphics g, float x0, float y0, float x1, float y1, int col) {
        int n = Math.max(1, (int) Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))));
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            int px = Math.round(x0 + (x1 - x0) * t);
            int py = Math.round(y0 + (y1 - y0) * t);
            g.fill(px, py, px + 1, py + 1, col);
        }
    }
}