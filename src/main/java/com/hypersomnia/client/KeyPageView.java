package com.hypersomnia.client;

import com.hypersomnia.diary.DiaryTuning;
import com.hypersomnia.document.SignAlphabet;
import com.hypersomnia.network.DiaryKeyPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Страница ключа (таблица «буква -> знак») в координатах бумаги 0..PAPER_W, 0..PAPER_H.
 * Общая для дневника и для открытого документа: документ держит её развёрнутой рядом.
 * Буквы берутся из языка интерфейса (латиница или кириллица).
 */
public final class KeyPageView {
    private static final int PAD = 12;
    private static final int INK = 0xFF1E2A34;
    private static final int FADED = 0xFF8A949A;
    private static final int KEY_COLS = 3;
    private static final float KEY_CELL_H = 15f;
    private static final float KEY_TOP = 40f;
    private static final int PAL_CELL = 26;
    private static final int PAL_COLS = 6;
    private static final int PAL_PAD = 6;
    private static final int PAL_X = (DiaryTuning.PAPER_W - PAL_COLS * PAL_CELL) / 2;
    private static final int PAL_Y = (DiaryTuning.PAPER_H - PAL_COLS * PAL_CELL) / 2;

    /** Буква, для которой открыта палитра знаков, или -1. */
    public int edit = -1;

    public static int paperH() { return DiaryTuning.PAPER_H; }

    /** Буквы таблицы из языка интерфейса; мусор и повторы отбрасываются. */
    public static String letters() {
        String raw = Component.translatableWithFallback("hypersomnia.diary.key.letters", "ABCDEFGHIJKLMNOPQRSTUVWXYZ").getString();
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toCharArray()) {
            char u = Character.toUpperCase(c);
            if (SignAlphabet.isLetter(u) && sb.indexOf(String.valueOf(u)) < 0) sb.append(u);
        }
        return sb.length() > 0 ? sb.toString() : "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    }

    private static float cellW() { return (DiaryTuning.PAPER_W - 2f * PAD) / KEY_COLS; }

    /** Номер буквы под точкой страницы или -1. */
    public static int indexAt(double px, double py) {
        if (px < PAD || py < KEY_TOP) return -1;
        int col = (int) ((px - PAD) / cellW());
        int row = (int) ((py - KEY_TOP) / KEY_CELL_H);
        if (col < 0 || col >= KEY_COLS || row < 0) return -1;
        int i = row * KEY_COLS + col;
        return i < letters().length() ? i : -1;
    }

    /** Страница целиком: фон, зерно, таблица. mx/my — курсор в координатах бумаги. */
    public void draw(GuiGraphics g, Font font, double mx, double my) {
        int w = DiaryTuning.PAPER_W;
        int h = DiaryTuning.PAPER_H;
        g.fill(0, 0, w, h, 0xFFC9CFD2);
        g.drawString(font, Component.translatableWithFallback("hypersomnia.diary.key", "KEY / SIGNS")
                .copy().withStyle(net.minecraft.ChatFormatting.BOLD), PAD, PAD, INK, false);
        g.fill(PAD, PAD + 10, w - PAD, PAD + 11, INK);
        String hint = Component.translatableWithFallback("hypersomnia.diary.key.hint",
                "Letter: sign. R: clear.").getString();
        g.drawString(font, font.plainSubstrByWidth(hint, w - PAD * 2), PAD, PAD + 15, FADED, false);
        String letters = letters();
        int hover = indexAt(mx, my);
        float cw = cellW();
        for (int i = 0; i < letters.length(); i++) {
            float x = PAD + (i % KEY_COLS) * cw;
            float y = KEY_TOP + (i / KEY_COLS) * KEY_CELL_H;
            if (i == hover) g.fill((int) x - 2, (int) y - 2, (int) (x + cw) - 3, (int) (y + KEY_CELL_H) - 2, 0x22000000);
            char ch = letters.charAt(i);
            Integer sym = DiaryClientState.keySym(ch);
            if (sym == null) g.drawString(font, "-", (int) x + 5, (int) y + 3, FADED, false);
            else SignGlyphs.draw(g, x + 7f, y + 7f, 11f, SignAlphabet.byIndex(sym), INK);
            g.drawString(font, String.valueOf(ch), (int) (x + 20), (int) y + 3, sym == null ? FADED : INK, false);
        }
        String num = String.valueOf(DiaryTuning.KEY_PAGE);
        g.drawString(font, num, (w - font.width(num)) / 2, h - 12, FADED, false);
        for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, 0x14000000);
        RandomSource rnd = RandomSource.create(System.nanoTime() / 60_000_000L);
        for (int i = 0; i < 40; i++) {
            int x = rnd.nextInt(w);
            int y = rnd.nextInt(h);
            g.fill(x, y, x + 1, y + 1, ((10 + rnd.nextInt(24)) << 24) | 0xFFFFFF);
        }
    }

    /** Палитра всех знаков поверх страницы; координаты бумаги. */
    public void drawPalette(GuiGraphics g, Font font) {
        String letters = letters();
        if (edit < 0 || edit >= letters.length()) return;
        int side = PAL_COLS * PAL_CELL;
        int x0 = PAL_X, y0 = PAL_Y;
        g.fill(x0 - PAL_PAD, y0 - PAL_PAD - 12, x0 + side + PAL_PAD, y0 + side + PAL_PAD, 0xFFE8E4DA);
        g.fill(x0 - PAL_PAD, y0 - PAL_PAD - 12, x0 + side + PAL_PAD, y0 - PAL_PAD - 11, INK);
        g.fill(x0 - PAL_PAD, y0 + side + PAL_PAD - 1, x0 + side + PAL_PAD, y0 + side + PAL_PAD, INK);
        g.fill(x0 - PAL_PAD, y0 - PAL_PAD - 12, x0 - PAL_PAD + 1, y0 + side + PAL_PAD, INK);
        g.fill(x0 + side + PAL_PAD - 1, y0 - PAL_PAD - 12, x0 + side + PAL_PAD, y0 + side + PAL_PAD, INK);
        char ch = letters.charAt(edit);
        g.drawString(font, Component.translatableWithFallback("hypersomnia.diary.key.pick", "Sign for %s", String.valueOf(ch)).getString(),
                x0, y0 - PAL_PAD - 9, INK, false);
        Integer cur = DiaryClientState.keySym(ch);
        for (int i = 0; i < SignAlphabet.count(); i++) {
            int cx = x0 + (i % PAL_COLS) * PAL_CELL;
            int cy = y0 + (i / PAL_COLS) * PAL_CELL;
            if (cur != null && cur == i) g.fill(cx, cy, cx + PAL_CELL, cy + PAL_CELL, 0x308A2030);
            SignGlyphs.draw(g, cx + PAL_CELL / 2f, cy + PAL_CELL / 2f, 16f, SignAlphabet.byIndex(i), INK);
        }
    }

    /** Клик по самой таблице: левая кнопка открывает палитру, правая стирает знак. */
    public boolean clickPage(double px, double py, int button) {
        int i = indexAt(px, py);
        if (i < 0) return false;
        char ch = letters().charAt(i);
        if (button == 1) assign(ch, -1);
        else edit = i;
        return true;
    }

    /** Клик при открытой палитре: выбор знака, ПКМ стирает, клик мимо закрывает. Съедает всё. */
    public boolean clickPalette(double px, double py, int button) {
        if (edit < 0) return false;
        String letters = letters();
        if (edit >= letters.length()) {
            edit = -1;
            return true;
        }
        char ch = letters.charAt(edit);
        int col = (int) Math.floor((px - PAL_X) / PAL_CELL);
        int row = (int) Math.floor((py - PAL_Y) / PAL_CELL);
        if (col < 0 || row < 0 || col >= PAL_COLS || row >= PAL_COLS) {
            edit = -1;
            return true;
        }
        assign(ch, button == 1 ? -1 : row * PAL_COLS + col);
        edit = -1;
        return true;
    }

    public boolean closePalette() {
        if (edit < 0) return false;
        edit = -1;
        return true;
    }

    private static void assign(char ch, int sym) {
        DiaryClientState.applyKey(ch, sym);
        PacketDistributor.sendToServer(new DiaryKeyPayload(String.valueOf(ch), sym));
    }

}
