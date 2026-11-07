package com.hypersomnia.client;

import com.hypersomnia.finale.FinaleTexts;
import com.hypersomnia.finale.FinaleTuning;
import com.hypersomnia.Hypersomnia;
import com.hypersomnia.network.EndingDonePayload;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Заключительный экран концовки в аналоговом стиле (канон, пункт 9): тёмный кадр записи — строки развёртки, зерно,
 * бегущая полоса, таймкод; строки появляются по очереди; для «Вечного сна» и «Subject 48» — кадр «капсула 47, мёртвые
 * мониторы»; затем карточка: название, служебные строки и штамп. Esc и клавиши — только на карточке
 * (FinaleTuning.CARD_MIN_TICKS), сама закрывается через CARD_AUTO_TICKS. Закрытие — EndingDonePayload: сервер будит.
 */
public final class EndingScreen extends Screen {
    private static final int BG = 0xFF03050A;
    private static final int DEEP = 0x1E2A34;
    private static final int COLD = 0x4A5E70;
    private static final int LIGHT = 0xDDE6EC;
    private static final int WINE = 0xC8B8BC;
    private static final int RED = 0x8A2030;

    private final int ending;
    private final List<String> texts = new ArrayList<>();
    private final List<Character> styles = new ArrayList<>();
    private final List<Integer> sourceIndices = new ArrayList<>();
    private final List<ResourceLocation[]> shots = new ArrayList<>();
    private final ResourceLocation specialFrame;
    private final int[] at;
    private final int linesEnd;
    private final int frameStart;
    private final int cardStart;
    private final String[] card;
    private final String stamp;
    private int t;
    private boolean done;

    public static void open(int ending, int flags) {
        if (ending < 0 || ending >= FinaleTuning.ENDINGS) return;
        Minecraft.getInstance().setScreen(new EndingScreen(ending, (flags & FinaleTuning.FLAG_BLANK) != 0));
    }

    /** Отладка и снимки (devtest): экран сразу на тике tick. */
    public static EndingScreen openAt(int ending, int flags, int tick) {
        EndingScreen s = new EndingScreen(ending, (flags & FinaleTuning.FLAG_BLANK) != 0);
        s.t = tick;
        Minecraft.getInstance().setScreen(s);
        return s;
    }

    private EndingScreen(int ending, boolean blank) {
        super(Component.translatableWithFallback(FinaleTexts.key(ending) + ".title", FinaleTexts.TITLES[ending]));
        this.ending = ending;
        FinaleTexts.Line[] src = FinaleTexts.LINES[ending];
        for (int i = 0; i < src.length; i++) {
            FinaleTexts.Line l = src[i];
            if (l.style() == 'b' && !blank) continue;
            String key = l.style() == 'b' ? "hypersomnia.finale.blank" : FinaleTexts.key(ending) + ".line." + i;
            texts.add(Component.translatableWithFallback(key, l.text()).getString());
            styles.add(l.style());
            sourceIndices.add(i);
        }
        for (int index : sourceIndices) shots.add(loadVariants(ending, index));
        specialFrame = existing("textures/gui/ending/" + FinaleTuning.KEYS[ending] + "_frame.png");
        at = new int[texts.size()];
        int tick = FinaleTuning.BLACK_TICKS;
        for (int i = 0; i < texts.size(); i++) {
            at[i] = tick;
            tick += FinaleTuning.LINE_FADE_TICKS + FinaleTuning.LINE_GAP_TICKS
                    + (int) (FinaleTuning.LINE_TICKS_PER_CHAR * texts.get(i).length());
        }
        linesEnd = tick + FinaleTuning.LINES_HOLD_TICKS;
        frameStart = linesEnd + FinaleTuning.BLACK_TICKS;
        cardStart = frameStart + (FinaleTexts.hasFrame(ending) ? FinaleTuning.FRAME_TICKS + FinaleTuning.BLACK_TICKS : 0);
        String[] c = FinaleTexts.CARDS[ending];
        card = new String[c.length];
        for (int i = 0; i < c.length; i++)
            card[i] = Component.translatableWithFallback(FinaleTexts.key(ending) + ".card." + i, c[i]).getString();
        stamp = Component.translatableWithFallback(FinaleTexts.key(ending) + ".stamp", FinaleTexts.STAMPS[ending]).getString();
    }

    public int linesEnd() { return linesEnd; }
    public int frameStart() { return frameStart; }
    public int cardStart() { return cardStart; }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return canClose();
    }

    private boolean canClose() {
        return t >= cardStart + FinaleTuning.CARD_MIN_TICKS;
    }

    @Override
    public void tick() {
        t++;
        if (t >= cardStart + FinaleTuning.CARD_AUTO_TICKS) onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (canClose()) onClose();
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (canClose()) onClose();
        return true;
    }

    @Override
    public void onClose() {
        finish();
        super.onClose();
    }

    @Override
    public void removed() {
        finish();
    }

    private void finish() {
        if (done) return;
        done = true;
        if (Minecraft.getInstance().getConnection() != null) PacketDistributor.sendToServer(new EndingDonePayload());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        // фон рисует render
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        float time = t + pt;
        g.fill(0, 0, width, height, BG);
        if (time < frameStart) {
            float fadeOut = time > linesEnd ? 1f - Mth.clamp((time - linesEnd) / FinaleTuning.BLACK_TICKS, 0f, 1f) : 1f;
            int index = currentLine(time);
            if (index >= 0 && shots.get(index).length > 0) drawShotLine(g, time, index, fadeOut);
            else drawLines(g, time, fadeOut);
        } else if (time < cardStart - (FinaleTexts.hasFrame(ending) ? FinaleTuning.BLACK_TICKS : 0)) {
            if (specialFrame != null) drawImage(g, specialFrame, 1f, 0f);
            else drawFrame(g, time - frameStart);
        } else if (time >= cardStart) {
            drawCard(g, time - cardStart);
        }
        drawTape(g, time);
    }

    private static ResourceLocation existing(String path) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, path);
        return Minecraft.getInstance().getResourceManager().getResource(location).isPresent() ? location : null;
    }

    /** Отсутствующие файлы не превращаются в розово-чёрные текстуры. */
    private static ResourceLocation[] loadVariants(int ending, int index) {
        String prefix = "textures/gui/ending/" + FinaleTuning.KEYS[ending] + "_"
                + String.format(java.util.Locale.ROOT, "%02d", index);
        List<ResourceLocation> found = new ArrayList<>();
        ResourceLocation base = existing(prefix + ".png");
        if (base != null) found.add(base);
        for (int i = 1; i <= 3; i++) {
            ResourceLocation variant = existing(prefix + "_" + i + ".png");
            if (variant != null) found.add(variant);
        }
        return found.toArray(ResourceLocation[]::new);
    }

    private int currentLine(float time) {
        int result = -1;
        for (int i = 0; i < at.length && time >= at[i]; i++) result = i;
        return result;
    }

    private ResourceLocation shotAt(int index, float elapsed) {
        ResourceLocation[] options = shots.get(index);
        return options[(Math.max(0, (int) elapsed) / FinaleTuning.SHOT_VARIANT_TICKS) % options.length];
    }

    private void drawShotLine(GuiGraphics g, float time, int index, float fadeOut) {
        float age = time - at[index];
        float blend = Mth.clamp(age / FinaleTuning.SHOT_FADE_TICKS, 0f, 1f);
        if (index > 0 && shots.get(index - 1).length > 0 && blend < 1f)
            drawImage(g, shotAt(index - 1, at[index] - at[index - 1]), fadeOut, 1f);
        float duration = Math.max(1f, (index + 1 < at.length ? at[index + 1] : linesEnd) - at[index]);
        drawImage(g, shotAt(index, age), blend * fadeOut, Mth.clamp(age / duration, 0f, 1f));
        List<FormattedCharSequence> lines = font.split(Component.literal(texts.get(index)), Math.min(300, width - 40));
        int y = (int) (height * 0.76f) - lines.size() * 5;
        g.fill(0, y - 5, width, y + lines.size() * 11 + 5, argb(0.45f * fadeOut, 0));
        int color = argb(Mth.clamp(age / FinaleTuning.LINE_FADE_TICKS, 0f, 1f) * fadeOut, styleColor(styles.get(index)));
        for (FormattedCharSequence line : lines) {
            if (color != 0) g.drawCenteredString(font, line, width / 2, y, color);
            y += 11;
        }
    }

    private void drawImage(GuiGraphics g, ResourceLocation image, float alpha, float progress) {
        float scale = Math.max((float) width / FinaleTuning.SHOT_WIDTH, (float) height / FinaleTuning.SHOT_HEIGHT)
                * Mth.lerp(progress, 1f, FinaleTuning.SHOT_ZOOM);
        float x = (width - FinaleTuning.SHOT_WIDTH * scale) / 2f;
        float y = (height - FinaleTuning.SHOT_HEIGHT * scale) / 2f;
        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, Mth.clamp(alpha, 0f, 1f));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.blit(image, 0, 0, 0, 0f, 0f, FinaleTuning.SHOT_WIDTH, FinaleTuning.SHOT_HEIGHT,
                FinaleTuning.SHOT_WIDTH, FinaleTuning.SHOT_HEIGHT);
        g.flush();
        g.pose().popPose();
        g.setColor(1f, 1f, 1f, 1f);
    }

    private static int argb(float a, int rgb) {
        int ai = (int) (Mth.clamp(a, 0f, 1f) * 255);
        return ai < 5 ? 0 : (ai << 24) | rgb;
    }

    private int styleColor(char s) {
        return switch (s) {
            case 'f', 'b' -> WINE;
            case 's' -> COLD;
            default -> LIGHT;
        };
    }

    /** Строки по очереди: новая проявляется, прежние приглушены; не влезают — старые уходят вверх. */
    private void drawLines(GuiGraphics g, float time, float fadeOut) {
        int maxW = Math.min(300, width - 40);
        int rowH = 11, gap = 9;
        List<List<FormattedCharSequence>> rows = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        int newest = -1;
        for (int i = 0; i < texts.size(); i++) {
            if (time < at[i]) break;
            newest = i;
            rows.add(font.split(Component.literal(texts.get(i)), maxW));
            idx.add(i);
        }
        if (newest < 0) return;
        int total = 0;
        for (List<FormattedCharSequence> r : rows) total += r.size() * rowH + gap;
        int first = 0;
        int maxH = (int) (height * 0.72f);
        while (total > maxH && first < rows.size() - 1) {
            total -= rows.get(first).size() * rowH + gap;
            first++;
        }
        int y = (height - total) / 2;
        for (int k = first; k < rows.size(); k++) {
            int i = idx.get(k);
            float a = Mth.clamp((time - at[i]) / FinaleTuning.LINE_FADE_TICKS, 0f, 1f) * fadeOut;
            if (i != newest) a *= 0.55f;
            int color = argb(a, styleColor(styles.get(i)));
            for (FormattedCharSequence r : rows.get(k)) {
                if (color != 0) g.drawCenteredString(font, r, width / 2, y, color);
                y += rowH;
            }
            y += gap;
        }
    }

    /** Кадр «капсула 47, мёртвые мониторы»: средний монитор гаснет, как кинескоп (полоса, точка, темно). */
    private void drawFrame(GuiGraphics g, float ft) {
        float a = Mth.clamp(ft / 20f, 0f, 1f) * Mth.clamp((FinaleTuning.FRAME_TICKS - ft) / 20f, 0f, 1f);
        if (a <= 0.02f) return;
        int cx = width / 2;
        int floor = (int) (height * 0.70f);
        g.fill(0, floor, width, floor + 1, argb(a * 0.8f, DEEP));
        // мониторы на стене
        int mw = Math.max(36, width / 9), mh = mw * 3 / 4, my = (int) (height * 0.22f);
        for (int k = -1; k <= 1; k++) {
            int x = cx + k * (mw + 14) - mw / 2;
            g.fill(x - 2, my - 2, x + mw + 2, my + mh + 2, argb(a, DEEP));
            g.fill(x, my, x + mw, my + mh, argb(a, 0x03050A));
            g.fill(x + mw / 2 - 1, my + mh + 2, x + mw / 2 + 1, floor - (int) (height * 0.18f), argb(a * 0.5f, DEEP)); // кабель
            if (k == 0 && ft < 46) {
                float p = ft / 46f;
                int lineH = Math.max(1, (int) (mh * (1f - p) * 0.5f));
                int lineW = (int) (mw * (p < 0.7f ? 1f : 1f - (p - 0.7f) / 0.3f));
                g.fill(x + (mw - lineW) / 2, my + mh / 2 - lineH, x + (mw + lineW) / 2, my + mh / 2 + lineH,
                        argb(a * (1f - p * 0.6f), LIGHT));
            }
        }
        // капсула: ложе и тёмная крышка
        int capW = Math.max(120, width / 3), capH = Math.max(26, height / 9);
        int x0 = cx - capW / 2, y0 = floor - capH;
        g.fill(x0 + 6, y0, x0 + capW - 6, y0 + capH / 2, argb(a, DEEP));
        g.fill(x0 + 3, y0 + 3, x0 + capW - 3, y0 + capH / 2, argb(a, DEEP));
        g.fill(x0 + 8, y0 + 3, x0 + capW - 8, y0 + 4, argb(a * 0.6f, COLD)); // блик стекла
        g.fill(x0, y0 + capH / 2, x0 + capW, y0 + capH, argb(a, COLD));
        g.fill(x0, y0 + capH / 2, x0 + capW, y0 + capH / 2 + 1, argb(a, LIGHT));
        g.fill(x0 + capW / 2 - 10, floor, x0 + capW / 2 + 10, floor + 4, argb(a, DEEP)); // опора
        String cap = Component.translatableWithFallback("hypersomnia.finale.screen.capsule",
                FinaleTexts.ui("hypersomnia.finale.screen.capsule")).getString();
        int c = argb(a, COLD);
        if (c != 0) g.drawCenteredString(font, cap, cx, floor + 14, c);
    }

    /** Карточка: «КОНЦОВКА», название, служебные строки, красный штамп; подсказка, когда можно закрыть. */
    private void drawCard(GuiGraphics g, float ct) {
        float a = Mth.clamp(ct / 30f, 0f, 1f);
        int cx = width / 2;
        int y = (int) (height * 0.30f);
        String label = Component.translatableWithFallback("hypersomnia.finale.screen.ending",
                FinaleTexts.ui("hypersomnia.finale.screen.ending")).getString();
        int lc = argb(a, COLD);
        if (lc != 0) g.drawCenteredString(font, label, cx, y - 14, lc);
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(2f, 2f, 1f);
        int tc = argb(a, LIGHT);
        if (tc != 0) g.drawCenteredString(font, getTitle(), 0, 0, tc);
        g.pose().popPose();
        int ly = y + 30;
        g.fill(cx - 60, ly - 6, cx + 60, ly - 5, argb(a * 0.7f, DEEP));
        for (String s : card) {
            int c = argb(a * 0.9f, LIGHT);
            if (c != 0) g.drawCenteredString(font, s, cx, ly, c);
            ly += 12;
        }
        // штамп: красная рамка с текстом, чуть повёрнут
        float sa = Mth.clamp((ct - 24) / 10f, 0f, 1f);
        if (sa > 0.02f) {
            int sw = font.width(stamp) + 12;
            g.pose().pushPose();
            g.pose().translate(cx, ly + 22, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(-6f));
            g.pose().scale(1.4f, 1.4f, 1f);
            int rc = argb(sa, RED);
            g.fill(-sw / 2, -8, sw / 2, -7, rc);
            g.fill(-sw / 2, 7, sw / 2, 8, rc);
            g.fill(-sw / 2, -8, -sw / 2 + 1, 8, rc);
            g.fill(sw / 2 - 1, -8, sw / 2, 8, rc);
            g.drawCenteredString(font, stamp, 0, -4, rc);
            g.pose().popPose();
        }
        if (ct >= FinaleTuning.CARD_MIN_TICKS && ((int) (ct / 20)) % 2 == 0) {
            String hint = Component.translatableWithFallback("hypersomnia.finale.screen.continue",
                    FinaleTexts.ui("hypersomnia.finale.screen.continue")).getString();
            g.drawCenteredString(font, hint, cx, height - 24, argb(0.8f, COLD));
        }
    }

    /** Плёнка: строки развёртки, зерно, бегущая полоса, таймкод и PLAY. */
    private void drawTape(GuiGraphics g, float time) {
        for (int y = 0; y < height; y += 3) g.fill(0, y, width, y + 1, 0x0C1E2A34);
        RandomSource r = RandomSource.create((long) (time / 2) * 7919L + 13L);
        int dots = width * height / 700;
        for (int i = 0; i < dots; i++) {
            int x = r.nextInt(Math.max(1, width)), y = r.nextInt(Math.max(1, height));
            g.fill(x, y, x + 1, y + 1, (r.nextInt(40) + 16) << 24 | COLD);
        }
        int band = (int) ((time * 1.3f) % (height + 40)) - 20;
        g.fill(0, band, width, band + 14, 0x08DDE6EC);
        int secs = FinaleTuning.timecodeStart(ending) + (int) (time / 20f);
        int frames = (int) (time % 20f * 1.25f);
        String tc = String.format("%02d:%02d:%02d:%02d", secs / 3600 % 24, secs / 60 % 60, secs % 60, frames);
        g.drawString(font, tc, 8, 8, 0xB04A5E70, false);
        String play = Component.translatableWithFallback("hypersomnia.finale.screen.play",
                FinaleTexts.ui("hypersomnia.finale.screen.play")).getString();
        g.drawString(font, play, width - 8 - font.width(play), 8, 0xB04A5E70, false);
    }
}
