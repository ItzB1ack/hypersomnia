package com.hypersomnia.client;

import com.hypersomnia.document.DocumentDefs;
import com.hypersomnia.document.DocumentDefs.*;
import com.hypersomnia.document.MorseCipher;
import com.hypersomnia.document.SignAlphabet;
import com.hypersomnia.document.TapeTuning;
import com.hypersomnia.document.WorldVariant;
import com.hypersomnia.lab.LabTuning;
import com.hypersomnia.lab.LabVariants;
import com.hypersomnia.ModSounds;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.RandomSource;
import net.minecraft.resources.ResourceLocation;
import com.hypersomnia.Hypersomnia;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Страница документа: бумага рисуется заливками, содержимое собирается из элементов DocumentDefs.
 * Style.TAPE: расшифровка аудиозаписи. Открытый экран проигрывает её как кассету: абзацы появляются в темпе речи,
 * под листом дека (состояние, катушки, счётчик), шипение ленты, провал записи на вымаранных словах. Миниатюры в дневнике
 * (экран не открыт) рисуют лист целиком и без звука.
 */
public final class DocumentScreen extends Screen {
    static final int PAPER_W = 200;
    private static final int PAD = 12;
    private static final float GLYPH_W = 18f;
    private static final float GLYPH_STEP = 24f;

    /** Знак алфавита черт (шифр 2) чернилами листа. */
    private static void glyph(GuiGraphics g, float cx, float cy, SignAlphabet.Sym s, int ink) {
        SignGlyphs.draw(g, cx, cy, GLYPH_W, s, ink);
    }

    /** Зерно мира для знаков: пока не пришло — 0 (одинаковая запасная таблица). */
    private static long signSeed() {
        return LabClientState.seeded ? LabClientState.cipherSeed : 0L;
    }

    private final Doc doc;

    public DocumentScreen(String id) {
        super(Component.empty());
        this.doc = DocumentDefs.get(id);
    }


    private Screen parent;

    /** Открыт из дневника: по закрытию возвращает в него. */
    public DocumentScreen(String id, Screen parent) {
        this(id);
        this.parent = parent;
    }

    @Override
    public void onClose() {
        if (parent != null && minecraft != null) minecraft.setScreen(parent);
        else super.onClose();
    }

    @Override
    public void removed() {
        if (tape != null) tape.halt();
        super.removed();
    }

    @Override
    public void tick() {
        ensureTape();
        if (tape != null) tape.tick();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tape != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            tape.toggle();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (tape != null && (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            tape.toggle();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xE0030508);
        if (doc == null) return;

        ensureTape();
        int h = paperHeight(g);
        int pw = paperWidth();
        boolean deck = tape != null;
        int full = deck ? h + DECK_GAP + DECK_H : h;
        int room = deck ? HINT_ROOM : 0;
        float s = Math.min(1.6f, Math.min((height - 16f - room) / full, (width - 16f) / pw));
        if (deck) drawTime = tape.time(minecraft.getTimer().getGameTimeDeltaPartialTick(true));

        g.pose().pushPose();
        g.pose().translate((width - pw * s) / 2f, (height - room - full * s) / 2f, 0f);
        g.pose().scale(s, s, 1f);
        drawPaper(g);
        if (deck) drawDeck(g, h + DECK_GAP);
        g.pose().popPose();
        if (deck) drawTapeHint(g);
    }

    private int cachedH = -1;

    /** Ширина листа: у разорванной половинки — половина обычной. */
    int paperWidth() {
        return doc != null && doc.edge() != DocumentDefs.EDGE_NONE ? PAPER_W / 2 : PAPER_W;
    }

    /** Полезная ширина текста: у половинки листа текст доходит до разрыва. */
    private int innerWidth() {
        return doc != null && doc.edge() != DocumentDefs.EDGE_NONE ? paperWidth() - PAD : paperWidth() - PAD * 2;
    }

    /** Левый край строки: у левой половины текст прижат к разрыву, у правой — начинается с него. */
    private int lineX(FormattedCharSequence l, int y) {
        int h = Math.max(1, cachedH);
        if (doc.edge() == DocumentDefs.EDGE_RIGHT) return Math.max(0, spanRight(y, h, paperWidth()) - 1 - font.width(l));
        if (doc.edge() == DocumentDefs.EDGE_LEFT) return spanLeft(y, h);
        return PAD;
    }

    /** Высота страницы (считается один раз). */
    int paperHeight(GuiGraphics g) {
        if (cachedH < 0) cachedH = run(g, false);
        return cachedH;
    }

    /** Ширина / высота страницы; -1, если высота ещё не посчитана (при g = null). */
    float aspect(GuiGraphics g) {
        int h = g != null ? paperHeight(g) : cachedH;
        return h > 0 ? paperWidth() / (float) h : -1f;
    }

    /** Профиль рваной кромки: один на обе половины. Левая берёт его «в минус», правая «в плюс», поэтому края сходятся как кусочки. */
    private static final int TEAR_MAX = 7;
    private static final int TEAR_STEP = 12;
    private static final int[] TEAR = new int[32];
    static {
        RandomSource r = RandomSource.create(0x7EA41L);
        for (int i = 0; i < TEAR.length; i++) TEAR[i] = r.nextInt(TEAR_MAX);
    }

    /** Глубина излома на высоте y (0..TEAR_MAX): плавный профиль плюс мелкая зубчатость. От высоты листа не зависит. */
    private int tearAt(int y, int h) {
        float t = Math.max(0, y) / (float) TEAR_STEP;
        int i = (int) t;
        float f = t - i;
        int a = TEAR[i % TEAR.length];
        int b = TEAR[(i + 1) % TEAR.length];
        int base = Math.round(a + (b - a) * f);
        int jag = (((y >> 1) * 0x9E3779B1) >>> 30) & 1;
        return Math.min(TEAR_MAX, base + jag);
    }

    /** Левый край правой половины. */
    private int spanLeft(int y, int h) {
        return doc.edge() == DocumentDefs.EDGE_LEFT ? tearAt(y, h) : 0;
    }

    /** Правый край левой половины: дополнение к правой, склеиваются при сдвиге правой на (ширина - TEAR_MAX). */
    private int spanRight(int y, int h, int pw) {
        return doc.edge() == DocumentDefs.EDGE_RIGHT ? pw - (TEAR_MAX - tearAt(y, h)) : pw;
    }

    /** Вся страница в координатах бумаги (0..paperWidth, 0..paperHeight); дневник масштабирует снаружи. */
    void drawPaper(GuiGraphics g) {
        int h = paperHeight(g);
        int pw = paperWidth();
        boolean note = doc.style() == Style.NOTE;
        boolean torn = doc.edge() != DocumentDefs.EDGE_NONE;
        int base = note ? 0xFFB9B3A5 : doc.style() == Style.DRAWING ? 0xFFDED6C4 : 0xFFC9CFD2;

        // Тень/кант по форме листа: у рваной кромки повторяет излом.
        if (!torn) {
            g.fill(-2, -2, pw + 2, h + 2, 0xFF000000);
            g.fill(0, 0, pw, h, base);
        } else {
            for (int y = 0; y < h; y += 2) {
                g.fill(spanLeft(y, h) - 2, y, spanRight(y, h, pw) + 2, y + 2, 0xFF000000);
            }
            for (int y = 0; y < h; y += 2) {
                int x0 = spanLeft(y, h);
                int x1 = spanRight(y, h, pw);
                g.fill(x0, y, x1, y + 2, base);
                // кромка излома: светлое волокно бумаги и тонкая тень за ним; ширина считается от y, у обеих половин одна
                int rim = 1 + ((((y >> 1) * 0x2545F491) >>> 31));
                if (doc.edge() == DocumentDefs.EDGE_RIGHT) {
                    g.fill(x1 - rim, y, x1, y + 2, 0x50FFFFFF);
                    g.fill(x1 - rim - 1, y, x1 - rim, y + 2, 0x28000000);
                } else {
                    g.fill(x0, y, x0 + rim, y + 2, 0x50FFFFFF);
                    g.fill(x0 + rim, y, x0 + rim + 1, y + 2, 0x28000000);
                }
            }
        }
        if (note) { // пятна на мятой бумаге
            RandomSource sr = RandomSource.create(doc.id().hashCode());
            for (int i = 0; i < 7; i++) {
                int x = sr.nextInt(Math.max(1, pw - 38));
                int y = sr.nextInt(Math.max(1, h - 20));
                g.fill(x, y, x + 8 + sr.nextInt(22), y + 3 + sr.nextInt(10), 0x22403020);
            }
        }
        run(g, true);

        if (torn) {
            for (int y = 0; y < h; y += 3) {
                g.fill(spanLeft(y, h), y, spanRight(y, h, pw), y + 1, 0x14000000);
            }
        } else {
            for (int y = 0; y < h; y += 3) g.fill(0, y, pw, y + 1, 0x14000000);
        }
        RandomSource rnd = RandomSource.create(System.nanoTime() / 60_000_000L);
        for (int i = 0; i < 70; i++) {
            int y = rnd.nextInt(h);
            int x0 = spanLeft(y, h);
            int x1 = spanRight(y, h, pw);
            if (x1 - x0 < 1) continue;
            int x = x0 + rnd.nextInt(x1 - x0);
            g.fill(x, y, x + 1, y + 1, ((10 + rnd.nextInt(24)) << 24) | 0xFFFFFF);
        }
    }

    /** Порядок группы из n строк Row на этой странице: вариант мира; пока зерно не пришло — как в описании. */
    private int[] rowOrder(int n) {
        if (LabClientState.seeded) return WorldVariant.order(LabClientState.cipherSeed, doc.id(), n);
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = i;
        return a;
    }

    private Component text(int index, String fallback) {
        Component c = Component.translatableWithFallback("hypersomnia.doc." + doc.id() + "." + index, fallback);
        return LabClientState.seeded ? worldText(doc.id(), c, LabClientState.cipherSeed) : c;
    }

    /**
     * Шифры по миру (LabVariants) в тексте страницы: время перевода в приказе bay_transfer, слово контрольной передачи
     * формы 12-B. Подстановка по готовому тексту, поэтому одинаково работает для EN из кода и для перевода из lang.
     */
    static Component worldText(String id, Component c, long seed) {
        switch (id) {
            case "bay_transfer" -> {
                String s = c.getString();
                return s.contains(BAY_CANON) ? Component.literal(s.replace(BAY_CANON, LabVariants.bayTime(seed))) : c;
            }
            case "morse_sample" -> {
                int v = LabVariants.morseWord(seed);
                if (v == 0) return c;
                String from = morseWord(0), to = morseWord(v), s = c.getString();
                return s.contains(from) ? Component.literal(s.replace(from, to)) : c;
            }
            default -> {
                return c;
            }
        }
    }

    /** Время в тексте приказа bay_transfer, которое заменяется вариантом мира. */
    private static final String BAY_CANON = "04:12";

    private static String morseWord(int n) {
        return Component.translatableWithFallback("hypersomnia.variant.morse." + n, LabTuning.MORSE_WORDS[n]).getString();
    }

    private static ResourceLocation picture(String tex) {
        return ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "textures/misc/" + tex + ".png");
    }

    /** Раскладывает элементы сверху вниз; draw=false только считает высоту страницы. */
    private int run(GuiGraphics g, boolean draw) {
        boolean note = doc.style() == Style.NOTE;
        int ink = note ? 0xFF3A2428 : doc.style() == Style.DRAWING ? 0xFF4A4A55 : 0xFF1E2A34;
        int y = PAD;
        List<El> els = doc.els();

        for (int i = 0; i < els.size(); i++) {
            El el = els.get(i);
            if (el instanceof Head e) {
                // Длинный заголовок (часто в переводе) переносится, а не уходит за край листа.
                for (FormattedCharSequence l : font.split(text(i, e.text()).copy().withStyle(ChatFormatting.BOLD), innerWidth())) {
                    if (draw) g.drawString(font, l, PAD, y, ink, false);
                    y += 10;
                }
                if (draw) g.fill(PAD, y, PAD + innerWidth(), y + 1, ink);
                y += 6;
            } else if (el instanceof Para e) {
                if (doc.style() == Style.TAPE) {
                    // Кассета: те же переносы строками; пока лента идёт, видна только уже «произнесённая» часть.
                    List<String> lines = tapeLines(i, e.text());
                    for (int k = 0; k < lines.size(); k++) {
                        if (draw) {
                            String l = tape != null ? tape.visible(i, k, lines.get(k), drawTime) : lines.get(k);
                            if (!l.isEmpty()) g.drawString(font, l, PAD, y, ink, false);
                        }
                        y += 10;
                    }
                } else {
                    for (FormattedCharSequence l : font.split(text(i, e.text()), innerWidth())) {
                        if (draw) g.drawString(font, l, lineX(l, y), y, ink, false);
                        y += 10;
                    }
                }
                y += 4;
            } else if (el instanceof Strike e) {
                for (FormattedCharSequence l : font.split(text(i, e.text()), innerWidth())) {
                    if (draw) {
                        int x = lineX(l, y);
                        g.drawString(font, l, x, y, ink, false);
                        g.fill(x, y + 4, x + font.width(l), y + 5, ink);
                    }
                    y += 10;
                }
                y += 4;
            } else if (el instanceof Stamp e) {
                Component c = text(i, e.text());
                int tw = font.width(c);
                if (draw) {
                    int x = PAD + innerWidth() - tw - 8;
                    int col = 0xFF8A2030;
                    g.fill(x - 4, y - 2, x + tw + 4, y - 1, col);
                    g.fill(x - 4, y + 10, x + tw + 4, y + 11, col);
                    g.fill(x - 4, y - 2, x - 3, y + 11, col);
                    g.fill(x + tw + 3, y - 2, x + tw + 4, y + 11, col);
                    g.drawString(font, c, x, y, col, false);
                }
                y += 18;
            } else if (el instanceof Letters e) {
                for (char ch : e.text().toUpperCase().toCharArray()) {
                    if (ch < 'A' || ch > 'Z') {
                        y += 6;
                        continue;
                    }
                    int n = ch - 'A' + 1;
                    if (draw) SignGlyphs.tally(g, PAD, y, n, 7, ink);
                    y += 10;
                }
                y += 6;
            } else if (el instanceof Morse e) {
                y = HeartMorse.draw(g, draw, PAD, y, innerWidth(), text(i, e.text()).getString(), e.printed());
            } else if (el instanceof Marked e) {
                // Шифр 1: слова между * отмечены чертой сверху, первые буквы отмеченных слов дают слово
                float x = PAD;
                float space = font.width(" ");
                for (String token : text(i, e.text()).getString().split(" ")) {
                    boolean marked = token.indexOf('*') >= 0;
                    String word = token.replace("*", "");
                    int w = font.width(word);
                    if (x > PAD && x + w > PAD + innerWidth()) {
                        x = PAD;
                        y += 16;
                    }
                    if (draw) {
                        g.drawString(font, word, (int) x, y + 7, ink, false);
                        // черта вплотную над словом (1 px зазора), до строки выше — 6 px: читается как отметка этого слова
                        if (marked) g.fill((int) x + 1, y + 5, (int) x + w - 1, y + 6, ink);
                    }
                    x += w + space;
                }
                y += 20;
            } else if (el instanceof Glyphs e) {
                // Шифр 2: алфавит черт (своя таблица в каждом мире), слово не рвётся между строками.
                // Знаки, которым игрок уже назначил букву в дневнике, показываются буквой.
                float x = PAD;
                long seed = signSeed();
                for (String word : text(i, e.text()).getString().toUpperCase(Locale.ROOT).split(" ")) {
                    List<Character> syms = new ArrayList<>();
                    for (char ch : word.toCharArray()) {
                        if (SignAlphabet.isLetter(ch)) syms.add(ch);
                    }
                    if (syms.isEmpty()) continue;
                    // Слово длиннее строки (в переводе бывает) не рвём, а сжимаем шаг, чтобы не вылезало за лист.
                    float step = Math.min(GLYPH_STEP, innerWidth() / (float) syms.size());
                    float w = syms.size() * step;
                    if (x > PAD && x + w > PAD + innerWidth()) {
                        x = PAD;
                        y += 28;
                    }
                    if (draw) {
                        for (int k = 0; k < syms.size(); k++) {
                            char ch = syms.get(k);
                            float cx = x + step * k + step / 2f;
                            Character known = DiaryClientState.letterFor(ch);
                            if (known != null) {
                                String s = String.valueOf(known);
                                g.drawString(font, s, (int) (cx - font.width(s) / 2f), y + 8, 0xFF8A2030, false);
                            } else {
                                glyph(g, cx, y + 12f, SignAlphabet.symbol(seed, ch), ink);
                            }
                        }
                    }
                    x += w + 10f;
                }
                y += 30;
            } else if (el instanceof GlyphKey e) {
                // Фрагмент ключа: знак и буква рядом, по 4 пары в строке
                int col = 0;
                for (char ch : text(i, e.letters()).getString().toUpperCase(Locale.ROOT).toCharArray()) {
                    SignAlphabet.Sym s = SignAlphabet.symbol(signSeed(), ch);
                    if (s == null) continue;
                    if (col == 4) {
                        col = 0;
                        y += 28;
                    }
                    if (draw) {
                        float x = PAD + col * 44f;
                        glyph(g, x + 12f, y + 12f, s, ink);
                        g.drawString(font, String.valueOf(ch), (int) (x + 28f), y + 8, ink, false);
                    }
                    col++;
                }
                y += 30;
            } else if (el instanceof Dirs e) {
                float x = PAD;
                for (char ch : e.dirs().toUpperCase().toCharArray()) {
                    String arrow = ch == 'L' ? "<" : ch == 'R' ? ">" : ch == 'U' ? "^" : ch == 'D' ? "v" : "";
                    if (draw && !arrow.isEmpty()) g.drawString(font, arrow, (int) x + 5, y + 2, ink, false);
                    x += 20f;
                }
                y += 16;
            } else if (el instanceof Row) {
                // Группа строк подряд — в порядке мира (зерно с сервера, ключ = id страницы), номера 1..n по месту на листе.
                int end = i;
                while (end < els.size() && els.get(end) instanceof Row) end++;
                int[] order = rowOrder(end - i);
                int indent = font.width("0.") + 4;
                for (int p = 0; p < order.length; p++) {
                    int k = i + order[p];
                    if (draw) g.drawString(font, (p + 1) + ".", PAD, y, ink, false);
                    for (FormattedCharSequence l : font.split(text(k, ((Row) els.get(k)).text()), innerWidth() - indent)) {
                        if (draw) g.drawString(font, l, PAD + indent, y, ink, false);
                        y += 10;
                    }
                    y += 3;
                }
                i = end - 1;
                y += 1;
            } else if (el instanceof Plan) {
                y = plan(g, draw, y, ink);
            } else if (el instanceof CheckGrid e) {
                y = checkGrid(g, draw, y, ink, text(i, e.labels()).getString());
            } else if (el instanceof Picture e) {
                if (draw) g.blit(picture(e.tex()), PAD, y, 0, 0f, 0f, innerWidth(), e.h(), innerWidth(), e.h());
                y += e.h() + 6;
            } else if (el instanceof Gap) {
                y += 8;
            }
        }
        return y + PAD - 4;
    }

    // ---- лист проверки копии B2-CHK (шифр 7, DocumentDefs.CheckGrid) ----

    /** Ширина колонки места, px бумаги (не больше, если лист уже). */
    private static final int CHECK_CELL = 16;
    private static final int CHECK_ROW_H = 14;

    /** Таблица цифр по зерну мира (LabVariants.stacksSheet): шапка «PLACE 1..8», слева «ROW n», тонкие линейки между рядами. */
    private int checkGrid(GuiGraphics g, boolean draw, int y, int ink, String labels) {
        String[] l = labels.split("\\|", -1);
        String row = l[0], place = l.length > 1 ? l[1] : "";
        int rows = LabTuning.STACK_ROWS, cols = LabTuning.STACK_PLACES;
        int head = font.width(row + " 0") + 6;
        int cell = Math.max(8, Math.min(CHECK_CELL, (innerWidth() - head) / cols));
        int x0 = PAD + head, x1 = x0 + cols * cell;
        int rule = (ink & 0x00FFFFFF) | 0x50000000;
        if (draw) g.drawString(font, place, x0 + 1, y, ink, false);
        y += 10;
        if (draw) {
            for (int c = 0; c < cols; c++) {
                String n = String.valueOf(c + 1);
                g.drawString(font, n, x0 + c * cell + (cell - font.width(n)) / 2 + 1, y, ink, false);
            }
            g.fill(PAD, y + 10, x1, y + 11, ink);
        }
        y += 11;
        int[][] sheet = LabVariants.stacksSheet(signSeed());
        for (int r = 0; r < rows; r++) {
            if (draw) {
                g.drawString(font, row + " " + (r + 1), PAD, y + 3, ink, false);
                for (int c = 0; c < cols; c++) {
                    String n = String.valueOf(sheet[r][c]);
                    g.drawString(font, n, x0 + c * cell + (cell - font.width(n)) / 2 + 1, y + 3, ink, false);
                    if (c > 0) g.fill(x0 + c * cell, y + 1, x0 + c * cell + 1, y + CHECK_ROW_H - 1, rule);
                }
                g.fill(PAD, y + CHECK_ROW_H, x1, y + CHECK_ROW_H + 1, rule);
            }
            y += CHECK_ROW_H + 1;
        }
        return y + 6;
    }

    // ---- план зала наблюдения (шифр 5, DocumentDefs.Plan) ----

    /** Клетка сетки плана, px бумаги (не больше, если лист уже). */
    private static final int PLAN_UNIT = 16;
    /** Поля вокруг стен плана: посты выступают за линию стены. */
    private static final int PLAN_MARGIN = 9;
    /** Половина стороны квадрата поста. */
    private static final int PLAN_POST = 5;
    private static final int PLAN_PAPER = 0xFFC9CFD2;

    /**
     * Чертёж зала по зерну мира (LabVariants.posts): стены с проёмом входа сверху, посты — квадраты с цифрой на стенах,
     * камеры — квадрат с объективом, номер 01..04 сбоку и пунктир взгляда до поста. Линий за спиной камер на бумаге нет.
     */
    private int plan(GuiGraphics g, boolean draw, int y, int ink) {
        int w = LabTuning.POSTS_W, h = LabTuning.POSTS_H;
        int u = Math.min(PLAN_UNIT, (innerWidth() - 2 * PLAN_MARGIN) / w);
        int x0 = PAD + (innerWidth() - w * u) / 2, y0 = y + PLAN_MARGIN;
        if (draw) {
            LabVariants.PostPlan p = LabVariants.posts(signSeed());
            int x1 = x0 + w * u, y1 = y0 + h * u;
            int gapA = x0 + (w / 2 - 1) * u, gapB = x0 + (w / 2 + 1) * u;
            g.fill(x0 - 1, y0 - 1, gapA, y0 + 1, ink);           // верхняя стена до проёма
            g.fill(gapB, y0 - 1, x1 + 1, y0 + 1, ink);           // и после
            g.fill(gapA - 1, y0 - 4, gapA, y0 + 1, ink);         // косяки проёма
            g.fill(gapB, y0 - 4, gapB + 1, y0 + 1, ink);
            g.fill(x0 - 1, y1 - 1, x1 + 1, y1 + 1, ink);         // нижняя
            g.fill(x0 - 1, y0 - 1, x0 + 1, y1 + 1, ink);         // левая
            g.fill(x1 - 1, y0 - 1, x1 + 1, y1 + 1, ink);         // правая
            for (int i = 0; i < p.cameras(); i++) {
                int cx = x0 + p.camX()[i] * u, cy = y0 + p.camZ()[i] * u;
                int dx = p.dirX()[i], dz = p.dirZ()[i];
                float len = (float) Math.hypot(dx, dz);
                float ux = dx / len, uz = dz / len;
                int post = p.front()[i];
                float reach = (float) Math.hypot(p.postX()[post] * u + x0 - cx, p.postZ()[post] * u + y0 - cy) - PLAN_POST - 2;
                for (int s = 7; s < reach; s++) { // пунктир взгляда: 3 px штрих, 3 px пропуск
                    if ((s / 3) % 2 != 0) continue;
                    int px = Math.round(cx + ux * s), pz = Math.round(cy + uz * s);
                    g.fill(px, pz, px + 1, pz + 1, ink);
                }
                g.fill(cx - 2, cy - 2, cx + 3, cy + 3, ink);     // корпус
                for (int s = 3; s <= 5; s++) {                    // объектив
                    int px = Math.round(cx + ux * s), pz = Math.round(cy + uz * s);
                    g.fill(px - 1, pz - 1, px + 1, pz + 1, ink);
                }
                String label = String.format(Locale.ROOT, "%02d", i + 1);
                float lx = cx - uz * 10f, lz = cy + ux * 10f;     // сбоку от линии, не на ней
                g.pose().pushPose();
                g.pose().translate(lx, lz, 0f);
                g.pose().scale(0.75f, 0.75f, 1f);
                g.drawString(font, label, -font.width(label) / 2, -4, ink, false);
                g.pose().popPose();
            }
            for (int k = 0; k < p.posts(); k++) {
                int cx = x0 + p.postX()[k] * u, cy = y0 + p.postZ()[k] * u;
                g.fill(cx - PLAN_POST - 1, cy - PLAN_POST - 1, cx + PLAN_POST + 2, cy + PLAN_POST + 2, ink);
                g.fill(cx - PLAN_POST, cy - PLAN_POST, cx + PLAN_POST + 1, cy + PLAN_POST + 1, PLAN_PAPER);
                String n = String.valueOf(p.postNum()[k]);
                g.drawString(font, n, cx - font.width(n) / 2 + 1, cy - 3, ink, false);
            }
        }
        return y0 + h * u + PLAN_MARGIN + 4;
    }

    // ---- кассета (Style.TAPE) ----

    private static final int DECK_GAP = 6;
    private static final int DECK_H = 24;
    /** Место под подсказку внизу экрана. */
    private static final int HINT_ROOM = 12;
    private static final int DECK_BG = 0xFF03050A;
    private static final int DECK_EDGE = 0xFF1E2A34;
    private static final int DECK_DIM = 0xFF4A5E70;
    private static final int DECK_LIGHT = 0xFFDDE6EC;
    private static final int HINT = 0xFF6E7A82;

    /** Записи, уже проигранные в этом запуске игры: при повторном открытии расшифровка видна сразу, лента в конце. */
    private static final Set<String> PLAYED = new HashSet<>();

    /** Строки абзацев кассеты (номер элемента → строки): переносы те же, что у font.split, но строками, чтобы печатать по знаку. */
    private final Map<Integer, List<String>> tapeLines = new HashMap<>();
    /** Проигрывание: есть только у открытого экрана, у миниатюры дневника null. */
    private Tape tape;
    /** Время ленты для текущего кадра (тики, с долей тика). */
    private float drawTime;

    private List<String> tapeLines(int index, String fallback) {
        List<String> lines = tapeLines.get(index);
        if (lines == null) {
            lines = new ArrayList<>();
            for (FormattedText t : font.getSplitter().splitLines(text(index, fallback).getString(), innerWidth(),
                    net.minecraft.network.chat.Style.EMPTY)) {
                lines.add(t.getString());
            }
            tapeLines.put(index, lines);
        }
        return lines;
    }

    /** Кассета заводится при первом тике или кадре открытого экрана: в первый раз за запуск игры сразу играет. */
    private void ensureTape() {
        if (tape != null || doc == null || doc.style() != Style.TAPE || font == null || minecraft == null) return;
        tape = new Tape();
        if (PLAYED.add(doc.id())) tape.start();
    }

    private static void tapeSound(SoundEvent sound, float volume, float pitch) {
        if (SleepSceneState.silent()) return;
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound.getLocation(), SoundSource.RECORDS,
                volume, pitch, SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
    }

    /** Проигрывание расшифровки: тик появления каждого знака, провалы записи, щелчки механизма, шипение. */
    private final class Tape {
        /** Номер элемента → [строка][знак] = тик ленты, когда знак появляется. */
        private final Map<Integer, float[][]> at = new HashMap<>();
        /** Начала провалов записи (тики). */
        private final List<Float> drops = new ArrayList<>();
        private final float end;
        private int now;
        private boolean playing;
        /** Остановлена щелчком до конца записи. */
        private boolean cut;
        private boolean inDrop;
        private TapeHiss hiss;

        Tape() {
            float t = TapeTuning.LEAD_IN_TICKS;
            float step = 1f / TapeTuning.CHARS_PER_TICK;
            float runStart = t;
            boolean any = false;
            List<El> els = doc.els();
            for (int i = 0; i < els.size(); i++) {
                if (!(els.get(i) instanceof Para p)) continue;
                List<String> lines = tapeLines(i, p.text());
                float[][] times = new float[lines.size()][];
                for (int k = 0; k < lines.size(); k++) {
                    String line = lines.get(k);
                    float[] ts = new float[line.length()];
                    for (int c = 0; c < line.length(); c++) {
                        char ch = line.charAt(c);
                        if (ch == '\u2588') {
                            // Вымаранное слово: на ленте провал, полоса появляется целиком, голос возвращается после паузы.
                            if (c == 0 || line.charAt(c - 1) != '\u2588') {
                                runStart = t;
                                drops.add(t);
                            }
                            ts[c] = runStart;
                            if (c + 1 == line.length() || line.charAt(c + 1) != '\u2588') t += TapeTuning.DROPOUT_TICKS;
                            continue;
                        }
                        ts[c] = t;
                        t += step;
                        if (c + 1 == line.length() || line.charAt(c + 1) == ' ') {
                            if (ch == '.' || ch == '?' || ch == '!') t += TapeTuning.SENTENCE_PAUSE_TICKS;
                            else if (ch == ',' || ch == ':' || ch == ';') t += TapeTuning.COMMA_PAUSE_TICKS;
                        }
                    }
                    times[k] = ts;
                    t += step; // пробел, съеденный переносом
                }
                at.put(i, times);
                t += TapeTuning.PARA_PAUSE_TICKS;
                any = true;
            }
            end = (any ? t - TapeTuning.PARA_PAUSE_TICKS : t) + TapeTuning.TAIL_TICKS;
            now = (int) Math.ceil(end);
        }

        /** Время ленты для кадра: пока играет, плавно между тиками. */
        float time(float partialTick) {
            return playing ? Math.min(end, now + partialTick) : now;
        }

        String visible(int index, int line, String full, float time) {
            float[][] times = at.get(index);
            if (times == null || line >= times.length) return full;
            float[] ts = times[line];
            int n = 0;
            while (n < ts.length && ts[n] <= time) n++;
            return full.substring(0, Math.min(n, full.length()));
        }

        boolean dropAt(float time) {
            for (float d : drops) if (time >= d && time < d + TapeTuning.DROPOUT_TICKS) return true;
            return false;
        }

        void start() {
            now = 0;
            playing = true;
            cut = false;
            inDrop = false;
            tapeSound(ModSounds.TAPE_CLICK.get(), TapeTuning.CLICK_VOLUME, TapeTuning.CLICK_PITCH_START);
            halt();
            if (!SleepSceneState.silent()) {
                hiss = new TapeHiss(DocumentScreen.this);
                Minecraft.getInstance().getSoundManager().play(hiss);
            }
        }

        void tick() {
            if (!playing) return;
            now++;
            boolean d = dropAt(now);
            if (d != inDrop) {
                inDrop = d;
                if (hiss != null) hiss.duck(d);
                if (d) tapeSound(ModSounds.TAPE_DROPOUT.get(), TapeTuning.DROPOUT_VOLUME, 1.0f);
            }
            if (now >= end) finish(false);
        }

        /** Конец записи или щелчок: вся расшифровка видна, лента на конце. */
        void finish(boolean byUser) {
            if (!playing) return;
            playing = false;
            cut = byUser;
            inDrop = false;
            now = (int) Math.ceil(end);
            halt();
            tapeSound(ModSounds.TAPE_CLICK.get(), TapeTuning.CLICK_VOLUME, TapeTuning.CLICK_PITCH_STOP);
        }

        void toggle() {
            if (playing) finish(true);
            else start();
        }

        void halt() {
            if (hiss != null) {
                hiss.halt();
                Minecraft.getInstance().getSoundManager().stop(hiss);
                hiss = null;
            }
        }
    }

    private static void frame(GuiGraphics g, int x0, int y0, int x1, int y1, int col) {
        g.fill(x0, y0, x1, y0 + 1, col);
        g.fill(x0, y1 - 1, x1, y1, col);
        g.fill(x0, y0, x0 + 1, y1, col);
        g.fill(x1 - 1, y0, x1, y1, col);
    }

    /** Катушка: три спицы (шесть зубцов) и тёмная втулка; поворот angle градусов. */
    private static void reel(GuiGraphics g, float cx, float cy, float angle) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0f);
        for (int k = 0; k < 3; k++) {
            g.pose().pushPose();
            g.pose().mulPose(Axis.ZP.rotationDegrees(angle + k * 60f));
            g.pose().translate(0f, -0.5f, 0f);
            g.fill(-4, 0, 4, 1, DECK_LIGHT);
            g.pose().popPose();
        }
        g.fill(-1, -1, 1, 1, DECK_BG);
        g.pose().popPose();
    }

    /**
     * Дека под листом (координаты листа): состояние, окно кассеты с катушками, счётчик ленты.
     * Помехи провала записи только в окне кассеты: текст расшифровки всегда читается.
     */
    private void drawDeck(GuiGraphics g, int y0) {
        int pw = paperWidth();
        int y1 = y0 + DECK_H;
        g.fill(-2, y0 - 2, pw + 2, y1 + 2, 0xFF000000);
        g.fill(0, y0, pw, y1, DECK_BG);
        frame(g, 0, y0, pw, y1, DECK_EDGE);

        // состояние: ▶ PLAY, ■ STOP / END
        int ix = 8;
        int iy = y0 + (DECK_H - 7) / 2;
        if (tape.playing) {
            for (int r = 0; r < 7; r++) g.fill(ix, iy + r, ix + Math.min(r, 6 - r) + 1, iy + r + 1, DECK_LIGHT);
        } else {
            g.fill(ix, iy + 1, ix + 5, iy + 6, DECK_DIM);
        }
        String state = tape.playing
                ? Component.translatableWithFallback("hypersomnia.tape.play", "PLAY").getString()
                : tape.cut
                ? Component.translatableWithFallback("hypersomnia.tape.stop", "STOP").getString()
                : Component.translatableWithFallback("hypersomnia.tape.end", "END").getString();
        g.drawString(font, state, ix + 9, y0 + (DECK_H - 8) / 2, tape.playing ? DECK_LIGHT : DECK_DIM, false);

        // окно кассеты: катушки и лента между ними
        int ww = 64;
        int wx0 = (pw - ww) / 2;
        int wx1 = wx0 + ww;
        int wy0 = y0 + 5;
        int wy1 = y1 - 5;
        g.fill(wx0, wy0, wx1, wy1, DECK_EDGE);
        frame(g, wx0, wy0, wx1, wy1, DECK_DIM);
        float cy = (wy0 + wy1) / 2f;
        float angle = drawTime * TapeTuning.REEL_DEG_PER_TICK;
        g.fill(wx0 + 12, wy1 - 3, wx1 - 12, wy1 - 2, DECK_DIM);
        reel(g, wx0 + 12.5f, cy, angle);
        reel(g, wx1 - 12.5f, cy, angle * 0.8f);
        if (tape.playing && tape.dropAt(drawTime)) {
            RandomSource r = RandomSource.create(System.nanoTime() / 50_000_000L);
            for (int k = 0; k < 3; k++) {
                int ly = wy0 + 1 + r.nextInt(Math.max(1, wy1 - wy0 - 2));
                g.fill(wx0 + 1, ly, wx1 - 1, ly + 1, 0x60DDE6EC);
            }
        }

        // счётчик ленты
        String count = String.format(Locale.ROOT, "%03d", (int) (drawTime / TapeTuning.COUNTER_STEP_TICKS) % 1000);
        int cw = font.width(count) + 8;
        int cx1 = pw - 8;
        int cx0 = cx1 - cw;
        g.fill(cx0, y0 + 6, cx1, y1 - 6, DECK_EDGE);
        g.drawString(font, count, cx0 + 4, y0 + (DECK_H - 8) / 2, DECK_LIGHT, false);
    }

    /** Подсказка внизу экрана, как у дневника. */
    private void drawTapeHint(GuiGraphics g) {
        String hint = tape.playing
                ? Component.translatableWithFallback("hypersomnia.tape.hint.skip", "CLICK / SPACE: SKIP").getString()
                : Component.translatableWithFallback("hypersomnia.tape.hint.replay", "CLICK / SPACE: PLAY AGAIN").getString();
        hint = font.plainSubstrByWidth(hint, width - 8);
        g.drawString(font, hint, (width - font.width(hint)) / 2, height - 10, HINT, false);
    }
}