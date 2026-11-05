package com.hypersomnia.client;

import com.hypersomnia.document.SignAlphabet;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Знак алфавита черт (SignAlphabet) карандашом: 1-3 вертикальные черты, перечерк поперёк или наискось, подставка снизу,
 * точка сверху. Рисуется прямоугольниками по пикселям GUI, поэтому чёткий и в дневнике (11 px), и на листе (18 px).
 */
public final class SignGlyphs {
    private SignGlyphs() {}

    /** cx, cy — центр знака; size — размер знака (лист 18, дневник 11); argb — чернила. */
    public static void draw(GuiGraphics g, float cx, float cy, float size, SignAlphabet.Sym s, int argb) {
        if (s == null) return;
        int t = size >= 15f ? 2 : 1;
        int h = Math.max(6, Math.round(size * 0.62f));
        int gap = Math.max(2, Math.round(size * 0.17f));
        int n = s.strokes();
        int w = n * t + (n - 1) * gap;
        int x0 = Math.round(cx - w / 2f);
        int top = Math.round(cy - h / 2f);
        int bot = top + h;
        for (int i = 0; i < n; i++) {
            int x = x0 + i * (t + gap);
            g.fill(x, top, x + t, bot, argb);
        }
        int ext = Math.max(2, gap);
        int l = x0 - ext, r = x0 + w + ext;
        if (s.cross() == 1) {
            int y = top + (h - t) / 2;
            g.fill(l, y, r, y + t, argb);
        } else if (s.cross() == 2) {
            // снизу слева вверх направо, без разрывов на крутом наклоне
            int len = r - l;
            for (int k = 0; k < len; k++) {
                int y1 = yOn(k, len, top, bot), y2 = yOn(Math.min(len - 1, k + 1), len, top, bot);
                g.fill(l + k, Math.min(y1, y2), l + k + t, Math.max(y1, y2) + t, argb);
            }
        }
        if (s.base()) {
            int y = bot + t + 1;
            g.fill(l, y, r, y + t, argb);
        }
        if (s.mark()) {
            int d = t + 1;
            int mx = Math.round(cx - d / 2f);
            int my = top - gap - d;
            g.fill(mx, my, mx + d, my + d, argb);
        }
    }

    private static int yOn(int k, int len, int top, int bot) {
        return bot - 1 - Math.round((bot - 1 - top) * (k / (float) Math.max(1, len - 1)));
    }

    /** Ряд черт счёта: n черт, каждая пятая — наискось через четыре. Для элемента Letters (номер буквы). */
    public static int tally(GuiGraphics g, int x, int y, int n, int h, int argb) {
        for (int k = 0; k < n; k++) {
            int group = k / 5, pos = k % 5;
            int gx = x + group * 14;
            if (pos < 4) g.fill(gx + pos * 3, y, gx + pos * 3 + 1, y + h, argb);
            else for (int i = 0; i < 12; i++) {
                int yy = y + h - 1 - Math.round((h - 1) * i / 11f);
                g.fill(gx - 1 + i, yy, gx + i, yy + 1, argb);
            }
        }
        return x + ((n + 4) / 5) * 14;
    }
}
