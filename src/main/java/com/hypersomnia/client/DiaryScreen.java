package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.diary.DiaryData;
import com.hypersomnia.diary.DiaryTuning;
import com.hypersomnia.document.DocumentDefs;
import com.hypersomnia.document.DocumentItem;
import com.hypersomnia.document.MemoryTuning;
import com.hypersomnia.network.DiaryActionPayload;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Дневник-книга: разворот из двух страниц (2k и 2k+1), страница перелистывается с анимацией.
 * Стр. 0 = хронология (только положенные воспоминания), 1..PAGES = свободные. Листы из «кармана» кладутся на страницу.
 * Листание: тянуть мышью по пустому месту страницы к корешку и дальше (или стрелки).
 */
public final class DiaryScreen extends Screen {
    private static final int PW = DiaryTuning.PAPER_W;
    private static final int PH = DiaryTuning.PAPER_H;
    private static final int TW = DiaryTuning.THUMB_W;
    private static final int TH = DiaryTuning.THUMB_H;
    private static final int LAST_PAGE = DiaryTuning.PAGES;
    private static final int LAST_SPREAD = LAST_PAGE / 2;
    private static final int PAD = 12;
    private static final int TOP = 40;
    private static final int ROW = 16;
    private static final int TRAY_W = 84;
    private static final int TRAY_ROW = 34;
    private static final int TRAY_BOX_W = 28;
    private static final int TRAY_BOX_H = 30;
    private static final float TURN_SPEED = 1f / DiaryTuning.TURN_SECONDS;
    private static final int INK = 0xFF1E2A34;
    private static final int FADED = 0xFF8A949A;
    /** Номера страниц: темнее FADED, читаются на светлой бумаге. */
    private static final int NUM = 0xFF4A565E;
    private static final int ACCENT = 0xFF8A2030;

    private int spread = 0;
    /** Разворот, на котором дневник закрыли: следующий открывается там же. */
    private static int lastSpread = 0;
    /** Перелистывание: 0 нет, +1 вперёд, -1 назад; turnT 0..1; turnTarget 1 = дойти до конца, 0 = вернуть. */
    private int turnDir = 0;
    private float turnT = 0f;
    private int turnTarget = 1;
    private boolean turnDragging;
    private long lastNanos;

    private boolean flipping;
    private float flipDx;
    private int flipSide;
    private int trayScroll;
    /** Стрелка, удерживаемая сейчас: её повторы не листают дальше. */
    private int keyDown = -1;
    /** Номер строки хронологии, на которой нажата мышь (-1 нет): документ открывается на отпускание. */
    private int chronoPress = -1;
    private long lastTurnSoundMs;
    /** Скорость жеста листания (доля страницы в секунду, со знаком «к концу») и момент последнего шага. */
    private float flipVel;
    private long lastDragNanos;
    /** Угол, на котором нажата мышь (+1 / -1, 0 нет): лист переворачивается на отпускание без сдвига. */
    private int cornerPress;
    /** Страница ключа: общая с DocumentScreen, хранит только открытую палитру. */
    private final KeyPageView keyView = new KeyPageView();
    private final Map<String, DocumentScreen> papers = new HashMap<>();

    private String heldId;
    private boolean heldFromTray;
    private int heldGrabX, heldGrabY;
    private double pressX, pressY;
    private boolean heldMoved;

    public static void open() {
        Minecraft.getInstance().setScreen(new DiaryScreen());
    }

    private DiaryScreen() {
        super(Component.empty());
        spread = Math.max(0, Math.min(LAST_SPREAD, lastSpread));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- геометрия (координаты разворота: левая страница 0..PW, правая PW..2*PW) ----

    private float scale() {
        return Math.min(1.6f, Math.min((height - 16f) / (PH + 12f), (width - 16f - TRAY_W) / (PW * 2f + 12f)));
    }

    private float originX() { return (width - TRAY_W - PW * 2f * scale()) / 2f; }
    private float originY() { return (height - PH * scale()) / 2f; }
    private double localX(double mx) { return (mx - originX()) / scale(); }
    private double localY(double my) { return (my - originY()) / scale(); }
    private int trayX() { return width - TRAY_W - 4; }

    private static float rotFor(String id) {
        return (Math.floorMod(id.hashCode(), 9) - 4) * 1.2f;
    }

    private static String title(String id) {
        DiaryTuning.Photo ph = DiaryTuning.PHOTOS.get(id);
        String src = ph == null ? id : ph.source();
        DocumentDefs.Doc d = DocumentDefs.get(src);
        return Component.translatableWithFallback("hypersomnia.doc." + src + ".title", d == null ? id : d.title()).getString();
    }

    /** Заголовок по ширине: обрезается по словам, в конце «…». */
    private String fitTitle(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        String ell = "…";
        String[] words = s.split(" ");
        StringBuilder out = new StringBuilder();
        for (String w : words) {
            String next = out.length() == 0 ? w : out + " " + w;
            if (font.width(next + ell) > maxW) break;
            out.setLength(0);
            out.append(next);
        }
        if (out.length() == 0) {
            return font.plainSubstrByWidth(s, Math.max(0, maxW - font.width(ell))) + ell;
        }
        return out + ell;
    }

    // ---- данные ----

    /** Хронология: вклеенные воспоминания и все найденные (бит сервера ObserverState.memories), даже если лист не вклеен или утерян. */
    private List<String> chronoIds() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < MemoryTuning.IDS.length; i++) {
            String id = MemoryTuning.IDS[i];
            if (DiaryClientState.has(id) || (ObserverState.memories & (1 << i)) != 0) out.add(id);
        }
        return out;
    }

    private List<String> trayIds() {
        List<String> out = new ArrayList<>();
        var player = Minecraft.getInstance().player;
        if (player == null) return out;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (!(stack.getItem() instanceof DocumentItem)) continue;
            String id = DocumentItem.idOf(stack);
            if (DocumentDefs.get(id) != null && !DiaryClientState.has(id) && !out.contains(id)) out.add(id);
        }
        // печати: доступны, пока цела страница-источник (в инвентаре или уже вклеена)
        for (var ph : DiaryTuning.PHOTOS.entrySet()) {
            if (DiaryClientState.has(ph.getKey()) || out.contains(ph.getKey())) continue;
            if (DiaryClientState.has(ph.getValue().source()) || carries(ph.getValue().source())) out.add(ph.getKey());
        }
        return out;
    }

    private static boolean carries(String id) {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (stack.getItem() instanceof DocumentItem && id.equals(DocumentItem.idOf(stack))) return true;
        }
        return false;
    }

    private int trayVisible() {
        return Math.max(1, (height - 32) / TRAY_ROW);
    }

    private String trayAt(double mx, double my) {
        if (mx < trayX() || mx > trayX() + TRAY_W || my < 24) return null;
        int row = (int) ((my - 24) / TRAY_ROW);
        if (row < 0 || row >= trayVisible()) return null;
        List<String> ids = trayIds();
        int i = trayScroll + row;
        return i < ids.size() ? ids.get(i) : null;
    }

    /** Строка хронологии по координатам внутри страницы 0 или -1. */
    private int chronoRowAt(double px, double py) {
        if (px < PAD || px > PW - PAD || py < TOP) return -1;
        int i = (int) ((py - TOP) / ROW);
        return i >= 0 && i < chronoIds().size() ? i : -1;
    }

    /** Лист под курсором на текущем развороте (координаты разворота). */
    private DiaryData.Entry entryAt(double lx, double ly) {
        int side = lx < PW ? 0 : 1;
        int p = spread * 2 + side;
        double px = lx - side * PW;
        List<DiaryData.Entry> es = DiaryClientState.entries;
        for (int i = es.size() - 1; i >= 0; i--) {
            DiaryData.Entry e = es.get(i);
            if (e.page() != p) continue;
            int[] sz = entrySize(e.id(), null);
            int m = edgeMargin(e.id());
            int ex = Math.min(e.x(), PW - sz[0] - m);
            int ey = Math.min(e.y(), PH - sz[1] - m);
            if (px >= ex && px <= ex + sz[0] && ly >= ey && ly <= ey + sz[1]) return e;
        }
        return null;
    }

    // ---- рисование ----

    private static ResourceLocation picture(String tex) {
        return ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "textures/misc/" + tex + ".png");
    }

    /** Страница документа для миниатюры (создаётся один раз, шрифт берётся из init). */
    private DocumentScreen paper(String id) {
        DocumentScreen s = papers.get(id);
        if (s == null && DocumentDefs.get(id) != null) {
            s = new DocumentScreen(id);
            s.init(Minecraft.getInstance(), width, height);
            papers.put(id, s);
        }
        return s;
    }

    /** Размер миниатюры: пропорция настоящей страницы, вписанная в рамку bw x bh. g = null: по уже посчитанной высоте. */
    private int[] fit(String id, GuiGraphics g, int bw, int bh) {
        DocumentScreen p = paper(id);
        float aspect = p == null ? -1f : p.aspect(g);
        if (aspect <= 0f) aspect = 0.75f;
        int w = bw;
        int h = Math.round(bw / aspect);
        if (h > bh) {
            h = bh;
            w = Math.round(bh * aspect);
        }
        return new int[]{Math.max(DiaryTuning.THUMB_MIN, w), Math.max(DiaryTuning.THUMB_MIN, h)};
    }

    /** Размер элемента на листе: печать крупнее страницы документа. */
    private int[] entrySize(String id, GuiGraphics g) {
        DiaryTuning.Photo ph = DiaryTuning.PHOTOS.get(id);
        if (ph == null) return fit(id, g, TW, TH);
        int w = DiaryTuning.PHOTO_W;
        return new int[]{w, Math.round(w * ph.texH() / (float) ph.texW())};
    }

    /** Поля, чтобы повёрнутый лист с тенью не вылезал за бумагу. */
    private static int edgeMargin(String id) {
        return DiaryTuning.PHOTOS.containsKey(id) ? 8 : 3;
    }

    /**
     * Подсказка под книгой: по центру книги, но не за краем экрана (в переводе строка длиннее).
     * Не влезает в ширину экрана — пункты (разделены тремя пробелами) переносятся строками снизу вверх.
     */
    private void drawHint(GuiGraphics g, String hint, float centerX) {
        int avail = width - 8;
        List<String> lines = new ArrayList<>();
        if (font.width(hint) <= avail) {
            lines.add(hint);
        } else {
            String cur = "";
            for (String part : hint.split(" {3}")) {
                String next = cur.isEmpty() ? part : cur + "   " + part;
                if (!cur.isEmpty() && font.width(next) > avail) {
                    lines.add(cur);
                    cur = part;
                } else {
                    cur = next;
                }
            }
            if (!cur.isEmpty()) lines.add(cur);
        }
        int y = height - 9 - (lines.size() - 1) * 10;
        for (String line : lines) {
            int hw = font.width(line);
            int x = Math.round(centerX - hw / 2f);
            x = Math.max(4, Math.min(x, width - 4 - hw));
            g.drawString(font, line, x, y, 0xFF6E7A82, false);
            y += 10;
        }
    }

    private void drawThumb(GuiGraphics g, String id, float x, float y, float rot, int bw, int bh) {
        DiaryTuning.Photo ph = DiaryTuning.PHOTOS.get(id);
        if (ph != null) {
            drawPhoto(g, ph, x, y, rot, bw, bh);
            return;
        }
        DocumentScreen p = paper(id);
        if (p == null) return;
        int[] sz = fit(id, g, bw, bh);
        int w = sz[0], h = sz[1];
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, y + h / 2f, 0f);
        g.pose().mulPose(Axis.ZP.rotationDegrees(rot));
        g.pose().translate(-w / 2f, -h / 2f, 0f);
        g.fill(2, 3, w + 4, h + 4, 0x18000000);
        g.fill(1, 2, w + 3, h + 3, 0x2A000000);
        g.pose().scale(w / (float) p.paperWidth(), h / (float) p.paperHeight(g), 1f);
        p.drawPaper(g);
        g.pose().popPose();
    }

    /** Печать на листе: у текстуры уже есть белое поле, поэтому только мягкая тень. Пропорция из размера текстуры. */
    private void drawPhoto(GuiGraphics g, DiaryTuning.Photo ph, float x, float y, float rot, int bw, int bh) {
        int w = bw;
        int h = Math.round(bw * ph.texH() / (float) ph.texW());
        if (h > bh) {
            h = bh;
            w = Math.round(bh * ph.texW() / (float) ph.texH());
        }
        if (w < DiaryTuning.THUMB_MIN || h < DiaryTuning.THUMB_MIN) return;
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, y + h / 2f, 0f);
        g.pose().mulPose(Axis.ZP.rotationDegrees(rot));
        g.pose().translate(-w / 2f, -h / 2f, 0f);
        g.fill(2, h - 1, w + 2, h + 3, 0x33000000);
        g.fill(w - 1, 2, w + 3, h + 2, 0x22000000);
        // blit не масштабирует: рисуем текстуру целиком в её же размере, а размер задаём позой,
        // иначе UV считается как w/texW и правая часть кадра срезается.
        g.pose().scale(w / (float) ph.texW(), h / (float) ph.texH(), 1f);
        g.blit(picture(ph.tex()), 0, 0, 0, 0f, 0f, ph.texW(), ph.texH(), ph.texW(), ph.texH());
        g.pose().popPose();
    }

    // ---- страница ключа ----

    /** Клик по таблице ключа на текущем развороте; палитра и отрисовка — в KeyPageView. */
    private boolean keyPageClick(double lx, double ly, int button) {
        if (spread * 2 != DiaryTuning.KEY_PAGE) return false;
        return keyView.clickPage(lx, ly, button);
    }

    /** Тон листа: у каждой страницы свой, на единицы градаций теплее или холоднее. Зависит только от номера страницы. */
    private static int paperTone(int p) {
        int d = Math.floorMod(p * 5 + 3, 7) - 3;
        return 0xFF000000 | ((0xC9 + d * 2) << 16) | ((0xCF + d) << 8) | (0xD2 - d);
    }

    /** Износ: крапинки, волокна и потёртости на полях. Seed от номера страницы, не от времени: ничего не мерцает. */
    private static void drawWear(GuiGraphics g, int p) {
        RandomSource rnd = RandomSource.create(47_000L + p * 7919L);
        for (int i = 0; i < 40; i++) {
            int x = rnd.nextInt(PW);
            int y = rnd.nextInt(PH);
            g.fill(x, y, x + 1, y + 1, ((10 + rnd.nextInt(24)) << 24) | 0xFFFFFF);
        }
        for (int i = 0; i < 26; i++) {
            int x = rnd.nextInt(PW - 8);
            int y = rnd.nextInt(PH);
            int len = 3 + rnd.nextInt(6);
            g.fill(x, y, x + len, y + 1, rnd.nextBoolean() ? 0x0EFFFFFF : 0x0C000000);
        }
        for (int i = 0; i < 7; i++) {
            int x = rnd.nextBoolean() ? rnd.nextInt(7) : PW - 9 + rnd.nextInt(7);
            int y = 6 + rnd.nextInt(PH - 12);
            int len = 5 + rnd.nextInt(10);
            g.fill(x, y, x + 2, Math.min(PH - 2, y + len), rnd.nextBoolean() ? 0x16FFFFFF : 0x14000000);
        }
    }
    /** Одна страница в координатах (0..PW, 0..PH); lmx/lmy = курсор внутри страницы для подсветки. */
    private void drawPage(GuiGraphics g, int p, double lmx, double lmy, boolean interactive) {
        g.fill(0, 0, PW, PH, paperTone(p));
        if (p < 0 || p > LAST_PAGE) return;
        if (p == 0) {
            g.drawString(font, Component.translatableWithFallback("hypersomnia.diary.title", "DIARY")
                    .copy().withStyle(ChatFormatting.BOLD), PAD, PAD, INK, false);
            g.fill(PAD, PAD + 10, PW - PAD, PAD + 11, INK);
            g.drawString(font, Component.translatableWithFallback("hypersomnia.diary.chronology", "CHRONOLOGY"),
                    PAD, TOP - 14, FADED, false);
            int hover = interactive ? chronoRowAt(lmx, lmy) : -1;
            List<String> ids = chronoIds();
            for (int i = 0; i < ids.size(); i++) {
                int y = TOP + i * ROW;
                if (i == hover) g.fill(PAD - 3, y - 3, PW - PAD + 3, y + 11, 0x22000000);
                g.drawString(font, "-", PAD, y, INK, false);
                g.drawString(font, fitTitle(title(ids.get(i)), PW - PAD * 2 - 14),
                        PAD + 12, y, i == hover ? ACCENT : INK, false);
            }
        } else if (p == DiaryTuning.KEY_PAGE) {
            keyView.draw(g, font, lmx, lmy);
        } else {
            for (int y = 24; y < PH - 8; y += 12) g.fill(PAD, y, PW - PAD, y + 1, 0x14000000);
            String num = String.valueOf(p);
            g.drawString(font, num, (PW - font.width(num)) / 2, PH - 12, NUM, false);
        }
        for (int y = 0; y < PH; y += 3) g.fill(0, y, PW, y + 1, 0x14000000);
        drawWear(g, p);
        for (DiaryData.Entry e : DiaryClientState.entries) {
            if (e.page() != p || e.id().equals(heldId)) continue;
            int[] sz = entrySize(e.id(), g);
            int m = edgeMargin(e.id());
            drawThumb(g, e.id(), Math.min(e.x(), PW - sz[0] - m), Math.min(e.y(), PH - sz[1] - m), rotFor(e.id()), sz[0], sz[1]);
        }
    }

    /** Переворачиваемый лист: ширина сжимается к корешку (cos), на середине меняется сторона; лист приподнимается, изгибается тенью и бликом. */
    private void drawLeaf(GuiGraphics g) {
        float e = turnT * turnT * (3f - 2f * turnT);
        boolean front = e < 0.5f;
        float f = Math.max(0.02f, (float) Math.abs(Math.cos(e * Math.PI)));
        float lift = (float) Math.sin(e * Math.PI);
        int k = spread;
        int page = turnDir > 0 ? (front ? 2 * k + 1 : 2 * k + 2) : (front ? 2 * k : 2 * k - 1);
        boolean onRight = (turnDir > 0) == front;
        int x0 = onRight ? 0 : -PW;

        g.pose().pushPose();
        g.pose().translate(PW, PH / 2f, 5f);
        g.pose().scale(f, 1f + 0.035f * lift, 1f);
        g.pose().translate(0f, -PH / 2f, 0f);
        g.pose().pushPose();
        g.pose().translate(x0, 0f, 0f);
        drawPage(g, page, -1000, -1000, false);
        g.pose().popPose();
        // изгиб: темнее у корешка, блик у свободного края, общее затемнение при развороте ребром
        final int steps = 17, sw = PW / steps;
        float base = (1f - f) * 90f;
        for (int i = 0; i < steps; i++) {
            float t = i / (float) (steps - 1);
            int xs = onRight ? i * sw : -(i + 1) * sw;
            int a = Math.min(200, (int) (base + (1f - t) * lift * 45f));
            if (a > 0) g.fill(xs, 0, xs + sw, PH, a << 24);
            if (t > 0.78f) {
                int h = (int) (lift * 38f * (t - 0.78f) / 0.22f);
                if (h > 0) g.fill(xs, 0, xs + sw, PH, (h << 24) | 0xFFFFFF);
            }
        }
        g.pose().popPose();

        // тень на страницу под листом: мягкий спад от свободного края
        int edge = Math.round(f * PW);
        int len = 10 + Math.round(14f * lift);
        for (int j = 0; j < len; j++) {
            int a = (int) (0x3C * (1f - j / (float) len) * (0.4f + 0.6f * lift));
            if (a <= 0) continue;
            int x = onRight ? PW + edge + j : PW - edge - j - 1;
            if (x < 0 || x >= PW * 2) continue;
            g.fill(x, 0, x + 1, PH, a << 24);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        if (turnDir != 0 && !turnDragging) {
            turnT += (turnTarget == 1 ? 1f : -1f) * TURN_SPEED * dt;
            if (turnT >= 1f) {
                spread += turnDir;
                lastSpread = spread;
                playTurnSound();
                turnDir = 0;
                turnT = 0f;
            } else if (turnT <= 0f) {
                turnDir = 0;
                turnT = 0f;
            }
        }

        g.fill(0, 0, width, height, 0xE0030508);
        float s = scale();
        double lx = localX(mouseX), ly = localY(mouseY);

        g.pose().pushPose();
        g.pose().translate(originX(), originY(), 0f);
        g.pose().scale(s, s, 1f);

        // обложка и срез страниц
        g.fill(-6, -6, PW * 2 + 6, PH + 6, 0xFF1C1412);
        g.fill(-3, -2, PW * 2 + 3, PH + 2, 0xFFA8A396);

        int leftP, rightP;
        if (turnDir == 0) {
            leftP = spread * 2;
            rightP = leftP + 1;
        } else if (turnDir > 0) {
            leftP = spread * 2;
            rightP = spread * 2 + 3;
        } else {
            leftP = spread * 2 - 2;
            rightP = spread * 2 + 1;
        }
        boolean still = turnDir == 0;
        drawPage(g, leftP, lx, ly, still);
        g.pose().pushPose();
        g.pose().translate(PW, 0f, 0f);
        drawPage(g, rightP, lx - PW, ly, still);
        g.pose().popPose();

        // лёгкое затемнение краёв книги: стабильное, зависит только от геометрии
        for (int i = 0; i < 7; i++) {
            int c = ((7 - i) * 3) << 24;
            g.fill(i, 0, i + 1, PH, c);
            g.fill(PW * 2 - 1 - i, 0, PW * 2 - i, PH, c);
            g.fill(0, i, PW * 2, i + 1, c);
            g.fill(0, PH - 1 - i, PW * 2, PH - i, c);
        }
        if (turnDir != 0) drawLeaf(g);

        // желоб у корешка
        for (int i = 0; i < 18; i++) {
            int a = (18 - i) * 5;
            g.fill(PW - 1 - i, 0, PW - i, PH, a << 24);
            g.fill(PW + i, 0, PW + i + 1, PH, a << 24);
        }
        g.fill(PW - 1, 0, PW + 1, PH, 0x66000000);

        // место размещения: рамка там, где ляжет лист; красный контур страницы, где класть нельзя
        if (heldId != null && heldMoved && mouseX < trayX() - 2 && lx >= 0 && lx <= PW * 2 && ly >= 0 && ly <= PH) {
            int[] t = dropTarget(heldId, mouseX, mouseY);
            if (t != null) {
                int[] sz = entrySize(heldId, g);
                int fx = t[3] * PW + t[1];
                frame(g, fx - 1, t[2] - 1, fx + sz[0] + 1, t[2] + sz[1] + 1, 0xAA1E2A34);
            } else {
                int fx = lx < PW ? 0 : PW;
                frame(g, fx, 0, fx + PW, PH, 0xAA8A2030);
            }
        }
        // уголок: спокойный загиб при наведении
        if (turnDir == 0 && heldId == null && !flipping && entryAt(lx, ly) == null) {
            int c = cornerAt(lx, ly);
            if (c != 0) {
                int cs = DiaryTuning.CORNER_SIZE;
                for (int j = 0; j < cs; j++) {
                    int w = cs - j;
                    int y = PH - 1 - j;
                    if (c > 0) g.fill(PW * 2 - w, y, PW * 2, y + 1, 0x38000000);
                    else g.fill(0, y, w, y + 1, 0x38000000);
                }
            }
        }

        if (keyView.edit >= 0) keyView.drawPalette(g, font);

        String hoverId = null;
        if (turnDir == 0 && heldId == null && keyView.edit < 0) {
            DiaryData.Entry h = entryAt(lx, ly);
            if (h != null) hoverId = h.id();
        }
        g.pose().popPose();

        // карман
        int tx = trayX();
        g.fill(tx - 2, 4, tx + TRAY_W + 2, height - 4, 0x90000000);
        g.drawString(font, Component.translatableWithFallback("hypersomnia.diary.pocket", "POCKET"), tx + 2, 10, FADED, false);
        List<String> tray = trayIds();
        int vis = trayVisible();
        trayScroll = Math.max(0, Math.min(trayScroll, Math.max(0, tray.size() - vis)));
        for (int r = 0; r < vis && trayScroll + r < tray.size(); r++) {
            String id = tray.get(trayScroll + r);
            if (id.equals(heldId) && heldFromTray) continue;
            int y = 24 + r * TRAY_ROW;
            drawThumb(g, id, tx + 3, y, 0f, TRAY_BOX_W, TRAY_BOX_H);
            g.drawString(font, fitTitle(title(id), TRAY_W - 38), tx + 35, y + 10, 0xFFDDE6EC, false);
        }
        if (heldId != null && heldMoved && !heldFromTray && mouseX >= tx - 2) {
            frame(g, tx - 2, 4, tx + TRAY_W + 2, height - 4, 0xAADDE6EC);
        }
        if (heldId == null && turnDir == 0) {
            String hint = Component.translatableWithFallback("hypersomnia.diary.hint",
                    "CLICK: READ   DRAG: PLACE   CORNER / ARROWS: TURN").getString();
            drawHint(g, hint, originX() + PW * s);
        }

        if (heldId != null) {
            int[] hsz = entrySize(heldId, null);
            float lift = heldMoved ? DiaryTuning.HELD_LIFT_SCALE : 1f;
            g.pose().pushPose();
            g.pose().translate(mouseX, mouseY, 50f);
            g.pose().scale(s * lift, s * lift, 1f);
            g.pose().translate(-heldGrabX, -heldGrabY, 0f);
            if (heldMoved) g.fill(4, 6, hsz[0] + 4, hsz[1] + 6, 0x30000000);
            drawThumb(g, heldId, 0, 0, rotFor(heldId), hsz[0], hsz[1]);
            g.pose().popPose();
        } else if (hoverId != null && !DiaryTuning.PHOTOS.containsKey(hoverId)) {
            // Над фото название не всплывает: только у документов.
            String name = title(hoverId);
            int w = font.width(name);
            g.pose().pushPose();
            g.pose().translate(0f, 0f, 60f);
            g.fill(mouseX + 8, mouseY - 14, mouseX + 12 + w, mouseY - 2, 0xC0000000);
            g.drawString(font, name, mouseX + 10, mouseY - 12, 0xFFDDE6EC, false);
            g.pose().popPose();
        }
    }

    // ---- ввод ----

    private void open(String id) {
        if (DocumentDefs.get(id) != null) Minecraft.getInstance().setScreen(new DocumentScreen(id, this));
    }
    /** Единственная точка звука: вызывается только когда переворот дошёл до конца. Локально, по настройке «Игроки». */
    private void playTurnSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || SleepSceneState.silent()) return;
        long now = System.currentTimeMillis();
        if (now - lastTurnSoundMs < DiaryTuning.TURN_SOUND_MIN_INTERVAL_MS) return;
        lastTurnSoundMs = now;
        playDesk(SoundEvents.BOOK_PAGE_TURN, DiaryTuning.TURN_SOUND_VOLUME, DiaryTuning.TURN_SOUND_PITCH);
    }
    private void beginTurn(int dir, boolean drag) {
        keyView.edit = -1;
        turnDir = dir;
        turnT = 0f;
        turnTarget = 1;
        turnDragging = drag;
        flipVel = 0f;
        lastDragNanos = System.nanoTime();
    }

    /** Тихий звук стола (взять / положить лист). Локальный, по настройке «Игроки», в тишине сцены не играет. */
    private void playDesk(SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || SleepSceneState.silent()) return;
        mc.level.playLocalSound(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(),
                sound, SoundSource.PLAYERS, volume, pitch, false);
    }

    /** Угол страницы под курсором: +1 правый нижний (вперёд), -1 левый нижний (назад), 0 нет или листать некуда. */
    private int cornerAt(double lx, double ly) {
        int c = DiaryTuning.CORNER_SIZE;
        if (ly < PH - c || ly > PH) return 0;
        if (lx >= PW * 2 - c && lx <= PW * 2 && spread < LAST_SPREAD) return 1;
        if (lx >= 0 && lx <= c && spread > 0) return -1;
        return 0;
    }

    /** Куда ляжет удерживаемый лист: {страница, x, y, сторона} или null, если сюда класть нельзя. Одна логика для рамки и для броска. */
    private int[] dropTarget(String id, double mouseX, double mouseY) {
        if (id == null || mouseX >= trayX() - 2) return null;
        double lx = localX(mouseX), ly = localY(mouseY);
        if (lx < 0 || lx > PW * 2 || ly < 0 || ly > PH) return null;
        int side = lx < PW ? 0 : 1;
        int p = spread * 2 + side;
        boolean photo = DiaryTuning.PHOTOS.containsKey(id);
        if (p < 1 || p > LAST_PAGE || (p == DiaryTuning.KEY_PAGE && !photo)) return null;
        int[] sz = entrySize(id, null);
        int m = edgeMargin(id);
        int px = (int) Math.max(0, Math.min(PW - sz[0] - m, lx - side * PW - heldGrabX));
        int py = (int) Math.max(0, Math.min(PH - sz[1] - m, ly - heldGrabY));
        return new int[]{p, px, py, side};
    }

    private static void frame(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        g.fill(x0, y0, x1, y0 + 1, c);
        g.fill(x0, y1 - 1, x1, y1, c);
        g.fill(x0, y0, x0 + 1, y1, c);
        g.fill(x1 - 1, y0, x1, y1, c);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (keyView.clickPalette(localX(mouseX), localY(mouseY), button)) return true;
        if (button == 1) {
            return turnDir == 0 && heldId == null && keyPageClick(localX(mouseX), localY(mouseY), 1);
        }
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (turnDir != 0) return true;
        pressX = mouseX;
        pressY = mouseY;
        heldMoved = false;
        String t = trayAt(mouseX, mouseY);
        if (t != null) {
            heldId = t;
            heldFromTray = true;
            int[] tsz = entrySize(t, null);
            heldGrabX = tsz[0] / 2;
            heldGrabY = tsz[1] / 2;
            return true;
        }
        double lx = localX(mouseX), ly = localY(mouseY);
        DiaryData.Entry e = entryAt(lx, ly);
        if (e != null) {
            heldId = e.id();
            heldFromTray = false;
            int[] sz = entrySize(e.id(), null);
            int gm = edgeMargin(e.id());
            heldGrabX = (int) (lx - (lx < PW ? 0 : PW) - Math.min(e.x(), PW - sz[0] - gm));
            heldGrabY = (int) (ly - Math.min(e.y(), PH - sz[1] - gm));
            return true;
        }
        chronoPress = spread == 0 && lx >= 0 && lx < PW ? chronoRowAt(lx, ly) : -1;
        if (keyPageClick(lx, ly, 0)) return true;
        // жест листания начинается только над бумагой: не с лотка, не с тёмного фона
        if (mouseX >= trayX() || lx < 0 || lx >= PW * 2 || ly < 0 || ly >= PH) return true;
        flipping = true;
        flipDx = 0f;
        flipSide = lx < PW ? 0 : 1;
        cornerPress = cornerAt(lx, ly);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button == 0) {
            if (heldId != null) {
                if (!heldMoved && Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > 4) {
                    heldMoved = true;
                    playDesk(SoundEvents.BOOK_PAGE_TURN, DiaryTuning.PICK_SOUND_VOLUME, DiaryTuning.PICK_SOUND_PITCH);
                }
                return true;
            }
            if (flipping) {
                flipDx += (float) dx;
                if (turnDir == 0 && Math.abs(flipDx) > DiaryTuning.DRAG_START_PX) {
                    if (flipSide == 1 && flipDx < 0 && spread < LAST_SPREAD) beginTurn(1, true);
                    else if (flipSide == 0 && flipDx > 0 && spread > 0) beginTurn(-1, true);
                }
                if (turnDir != 0 && turnDragging) {
                    float span = PW * scale() * 1.1f;
                    float v = turnDir > 0 ? -flipDx : flipDx;
                    turnT = Math.max(0f, Math.min(1f, v / span));
                    long nn = System.nanoTime();
                    float dtv = Math.max(0.001f, (nn - lastDragNanos) / 1_000_000_000f);
                    lastDragNanos = nn;
                    float step = (turnDir > 0 ? -(float) dx : (float) dx) / span;
                    flipVel = flipVel * 0.6f + (step / dtv) * 0.4f;
                }
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (heldId != null) {
                String id = heldId;
                boolean fromTray = heldFromTray;
                heldId = null;
                if (!heldMoved) {
                    open(id);
                    return true;
                }
                int[] t = dropTarget(id, mouseX, mouseY);
                if (t != null) {
                    DiaryClientState.applyPlace(id, t[0], t[1], t[2]);
                    PacketDistributor.sendToServer(new DiaryActionPayload(id, t[0], t[1], t[2], false));
                    playDesk(SoundEvents.BOOK_PUT, DiaryTuning.DROP_SOUND_VOLUME, DiaryTuning.DROP_SOUND_PITCH);
                } else if (!fromTray && mouseX >= trayX() - 2) {
                    // из книги лист снимается только броском на лоток; бросок мимо оставляет его на месте
                    DiaryClientState.applyRemove(id);
                    PacketDistributor.sendToServer(new DiaryActionPayload(id, 0, 0, 0, true));
                    playDesk(SoundEvents.BOOK_PUT, DiaryTuning.DROP_SOUND_VOLUME, DiaryTuning.DROP_SOUND_PITCH);
                }
                return true;
            }
            if (flipping) {
                flipping = false;
                int cr = chronoPress;
                int cp = cornerPress;
                chronoPress = -1;
                cornerPress = 0;
                boolean still = Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) <= 4;
                if (turnDragging) {
                    turnDragging = false;
                    // флик: быстрый жест решает сам, медленный решает пройденный путь
                    float vel = System.nanoTime() - lastDragNanos < 120_000_000L ? flipVel : 0f;
                    turnTarget = vel > DiaryTuning.FLICK_SPEED ? 1
                            : vel < -DiaryTuning.FLICK_SPEED ? 0
                            : turnT > DiaryTuning.COMMIT_FRACTION ? 1 : 0;
                } else if (turnDir == 0 && still && cr >= 0) {
                    List<String> ids = chronoIds();
                    if (cr < ids.size()) open(ids.get(cr));
                } else if (turnDir == 0 && still && cp != 0) {
                    beginTurn(cp, false);
                }
                flipDx = 0f;
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= trayX()) {
            trayScroll = Math.max(0, trayScroll - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (keyView.closePalette()) return true;
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_LEFT) {
            // повтор зажатой клавиши игнорируем: один лист на одно нажатие
            if (keyDown == key) return true;
            keyDown = key;
            if (turnDir == 0 && heldId == null && !flipping) {
                if (key == GLFW.GLFW_KEY_RIGHT && spread < LAST_SPREAD) beginTurn(1, false);
                else if (key == GLFW.GLFW_KEY_LEFT && spread > 0) beginTurn(-1, false);
            }
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        if (key == keyDown) keyDown = -1;
        return super.keyReleased(key, scanCode, modifiers);
    }
}