package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Главное меню после стадии 3+ [новое].
 *
 * Игрок дошёл до стадии 3 хотя бы в одном мире — и главное меню перестаёт быть обычным, как в референсе автора:
 * чёрный экран, пустые белые рамки кнопок, глаз осколка в центре и редкие рукописные обрывки по углам. Кнопки
 * расставляются вокруг глаза, поэтому зрачок может смотреть на любую из них. Это не толпа глаз и не глаз в
 * интерфейсе внутри мира: осколок смотрит на игрока снаружи, пока сеанс ещё не начат.
 *
 * Стадия хранится в config/hypersomnia_title.txt и переживает перезапуск. Пока эффект включён, ванильная панорама,
 * логотип и подписи не рисуются; подпись выбранной кнопки показывается внутри её рамки, чтобы меню оставалось
 * рабочим.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class HauntedTitle {
    private HauntedTitle() {}

    /** С этой стадии главное меню считается «испорченным». */
    private static final int MIN_STAGE = 3;
    private static final String FILE_NAME = "hypersomnia_title.txt";

    /** 20 строк роя в lang; здесь они используются как редкие обрывки в углах меню. */
    private static final int LINES = 20;
    private static final int PHRASE_MS = 7200;
    /** Запасной английский текст, если ключей нет в lang (не даёт светиться именам ключей). */
    private static final String[] FALLBACK = {
            "Where is this place?", "Who is speaking?", "Is the session open?", "What time is it?",
            "Is anyone present?", "Is this recording on?", "Why was it stopped?", "How long was I out?",
            "What was changed?", "Who is counting?", "You missed it.", "The session did not end.",
            "You said you would return.", "The light was left on.", "This was your decision.", "Again.",
            "I remained here.", "The count was interrupted.", "The door was closed.",
            "You were allowed to forget."
    };

    private static boolean loaded;
    private static int maxStage;
    /** Шаг времени для мелкой дрожи букв в углах: обновляется редко, чтобы не было ряби каждый кадр. */
    private static final int JITTER_MS = 110;
    /** Сглаженный взгляд зрачка: цель меняется при смене кнопки. */
    private static float gazeX = Float.NaN;
    private static float gazeY = Float.NaN;
    private static long gazeAt;

    /**
     * Раскладка из референса: восемь рамок вокруг центрального глаза. Координаты — доли экрана, чтобы меню
     * одинаково выглядело на 720p, 1080p и 4K.
     */
    private record Box(float x, float y, float w, float h) {}

    private static final Box TOP = new Box(0.409f, 0.190f, 0.187f, 0.106f);
    private static final Box UPPER_LEFT = new Box(0.284f, 0.269f, 0.088f, 0.083f);
    private static final Box UPPER_RIGHT = new Box(0.630f, 0.278f, 0.078f, 0.083f);
    private static final Box MID_LEFT = new Box(0.206f, 0.417f, 0.192f, 0.102f);
    private static final Box MID_RIGHT = new Box(0.632f, 0.417f, 0.188f, 0.102f);
    private static final Box LOWER_LEFT = new Box(0.281f, 0.574f, 0.102f, 0.093f);
    private static final Box LOWER_RIGHT = new Box(0.625f, 0.574f, 0.115f, 0.102f);
    private static final Box BOTTOM = new Box(0.414f, 0.639f, 0.182f, 0.111f);

    /** Куда ставить текстовые кнопки: Singleplayer, Multiplayer, Realms, Mods, Options, Quit. */
    private static final Box[] TEXT_BOXES = {TOP, MID_LEFT, MID_RIGHT, LOWER_LEFT, LOWER_RIGHT, BOTTOM};
    /** Куда ставить иконки: Language и Accessibility. */
    private static final Box[] ICON_BOXES = {UPPER_LEFT, UPPER_RIGHT};
    /** Уже расставленные виджеты в смысловом порядке: первая — Singleplayer, с неё начинается выбор. */
    private static final List<AbstractWidget> ORDER = new ArrayList<>();

    /** Сервер сообщил стадию: запоминаем максимум. Вызывается из клиентского тика. */
    public static void observe(int stage) {
        ensureLoaded();
        if (stage < MIN_STAGE || stage <= maxStage) return;
        maxStage = stage;
        save();
    }

    /** Есть ли в клиенте след мира со стадией 3+. */
    public static boolean haunted() {
        ensureLoaded();
        return maxStage >= MIN_STAGE;
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Path p = file();
            if (!Files.exists(p)) return;
            String text = Files.readString(p, StandardCharsets.UTF_8).trim();
            int eq = text.indexOf('=');
            if (eq >= 0) text = text.substring(eq + 1).trim();
            maxStage = Math.max(0, Math.min(5, Integer.parseInt(text)));
        } catch (IOException | NumberFormatException ex) {
            // Повреждённый или недоступный файл не должен ломать меню.
            maxStage = 0;
        }
    }

    private static void save() {
        try {
            Path p = file();
            Files.createDirectories(p.getParent());
            Files.writeString(p, "max_stage=" + maxStage, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Меню всё равно покажет эффект до перезапуска.
        }
    }

    /** Рамки ставятся сразу при открытии меню, чтобы первый клик попал в новую раскладку. */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen screen) || !haunted()) return;
        layout(screen);
    }

    /**
     * Рисуем поверх ванильного заголовка: чёрный кадр, пустые рамки, глаз в центре и обрывки фраз.
     * Кнопки остаются рабочими — меняются только их места, размеры и вид.
     */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof TitleScreen screen) || !haunted()) return;
        int w = screen.width;
        int h = screen.height;
        if (w <= 0 || h <= 0) return;
        layout(screen);
        long now = System.currentTimeMillis();
        float level = Mth.clamp((maxStage - (MIN_STAGE - 1)) / 3f, 0f, 1f);
        GuiGraphics g = event.getGuiGraphics();

        // 1) Полный чёрный кадр вместо панорамы, логотипа и сплэша. Референс — именно пустой экран.
        g.fill(0, 0, w, h, 0xFF03050A);

        // 2) Очень слабая эрозия по краям: кадр не абсолютно чистый, но и не спорит с рамками.
        AbstractWidget selected = selectedButton(event.getMouseX(), event.getMouseY());
        float eyeW = eyeWidth(w, h);
        float[] avoid = avoid(screen, w / 2f, h * 0.47f, eyeW);
        PixelDecay.draw(g, w, h, now, 0x7A11C0DEL, 0.09f + 0.07f * level, 0.04f + 0.04f * level,
                0.01f + 0.02f * level, avoid);

        // 3) Обрывки фраз в углах: тонкий шрифт, медленно перебирается и дрожит.
        drawCorners(g, w, h, now, level);

        // 4) Восемь пустых рамок. Подпись видна только у выбранной кнопки, чтобы меню оставалось понятным.
        Font font = Minecraft.getInstance().font;
        for (GuiEventListener listener : screen.children()) {
            if (!(listener instanceof AbstractWidget widget) || !widget.visible) continue;
            drawBox(g, font, widget, widget == selected);
        }

        // 5) Глаз осколка в центре. Зрачок поворачивается к выбранной кнопке.
        drawEye(g, w / 2f, h * 0.47f, eyeW, selected, now, level);

        // 6) Тултипы (язык, доступность, отключённый мультиплеер) оказались под чёрным кадром — возвращаем сверху.
        AbstractWidget hover = hovered(event.getMouseX(), event.getMouseY());
        if (hover != null) {
            Tooltip tip = hover.getTooltip();
            if (tip != null) {
                g.renderTooltip(font, tip.toCharSequence(Minecraft.getInstance()),
                        event.getMouseX(), event.getMouseY());
            }
        }
    }

    /**
     * Переставляет ванильные кнопки в раскладку из референса. Патентованные подписи и фон не важны: рамки
     * рисуются поверх, а сами кнопки остаются кликабельными.
     */
    private static void layout(TitleScreen screen) {
        List<AbstractWidget> texts = new ArrayList<>();
        List<AbstractWidget> icons = new ArrayList<>();
        for (GuiEventListener listener : screen.children()) {
            if (!(listener instanceof AbstractWidget widget) || !widget.visible) continue;
            // Копирайт внизу — не кнопка меню. Прячем, чтобы по пустому месту нельзя было открыть титры.
            if (widget instanceof PlainTextButton) {
                widget.visible = false;
                continue;
            }
            if (widget instanceof SpriteIconButton) icons.add(widget);
            else texts.add(widget);
        }
        // Ванильный порядок — Mods, Language, Options, Quit, ...; для меню важнее смысл: Singleplayer сверху.
        texts.sort((a, b) -> Integer.compare(rank(a), rank(b)));
        int w = screen.width;
        int h = screen.height;
        ORDER.clear();
        for (int i = 0; i < texts.size(); i++) {
            if (i >= TEXT_BOXES.length) {
                texts.get(i).visible = false;
                continue;
            }
            place(texts.get(i), TEXT_BOXES[i], w, h);
            ORDER.add(texts.get(i));
        }
        for (int i = 0; i < icons.size(); i++) {
            if (i >= ICON_BOXES.length) {
                icons.get(i).visible = false;
                continue;
            }
            place(icons.get(i), ICON_BOXES[i], w, h);
            ORDER.add(icons.get(i));
        }
    }

    /** Смысловой порядок кнопок: сверху вниз по важности. Неизвестные — в конец. */
    private static int rank(AbstractWidget widget) {
        String key = translationKey(widget.getMessage());
        return switch (key) {
            case "menu.singleplayer" -> 0;
            case "menu.multiplayer" -> 1;
            case "menu.online" -> 2;
            case "fml.menu.mods" -> 3;
            case "menu.options" -> 4;
            case "menu.quit" -> 5;
            default -> 6;
        };
    }

    private static String translationKey(Component message) {
        return message.getContents() instanceof TranslatableContents tc ? tc.getKey() : "";
    }

    private static void place(AbstractWidget widget, Box box, int sw, int sh) {
        int x = Math.round(box.x * sw);
        int y = Math.round(box.y * sh);
        int w = Math.max(24, Math.round(box.w * sw));
        int h = Math.max(16, Math.round(box.h * sh));
        widget.setX(x);
        widget.setY(y);
        widget.setWidth(w);
        widget.setHeight(h);
    }

    /** Кнопка под мышью, иначе кнопка с клавиатурным фокусом, иначе первая в смысловом порядке (Singleplayer). */
    private static AbstractWidget selectedButton(double mx, double my) {
        AbstractWidget focused = null;
        AbstractWidget first = null;
        AbstractWidget fallback = null;
        for (AbstractWidget widget : ORDER) {
            if (!widget.visible) continue;
            if (fallback == null) fallback = widget;
            if (!widget.active) continue;
            if (first == null) first = widget;
            if (widget.isMouseOver(mx, my)) return widget;
            if (focused == null && widget.isFocused()) focused = widget;
        }
        return focused != null ? focused : first != null ? first : fallback;
    }

    /** Виджет под курсором — для возврата тултипа поверх чёрного кадра. */
    private static AbstractWidget hovered(double mx, double my) {
        for (AbstractWidget widget : ORDER) {
            if (widget.visible && widget.isMouseOver(mx, my)) return widget;
        }
        return null;
    }

    /** Ширина глаза: как в референсе, примерно шестая часть кадра по ширине. */
    private static float eyeWidth(int w, int h) {
        return Math.max(48f, Math.min(w * 0.17f, h * 0.30f));
    }

    /** Рамки кнопок и глаз: сюда эрозия не заходит. */
    private static float[] avoid(TitleScreen screen, float eyeX, float eyeY, float eyeW) {
        int n = 0;
        for (GuiEventListener listener : screen.children()) {
            if (listener instanceof AbstractWidget widget && widget.visible) n++;
        }
        float[] out = new float[(n + 1) * 4];
        int i = 0;
        for (GuiEventListener listener : screen.children()) {
            if (!(listener instanceof AbstractWidget widget) || !widget.visible) continue;
            out[i++] = widget.getX() + widget.getWidth() / 2f;
            out[i++] = widget.getY() + widget.getHeight() / 2f;
            out[i++] = widget.getWidth() / 2f + 6f;
            out[i++] = widget.getHeight() / 2f + 6f;
        }
        out[i++] = eyeX;
        out[i++] = eyeY;
        out[i++] = eyeW * 0.55f;
        out[i] = eyeW * 0.42f;
        return out;
    }

    private static void drawBox(GuiGraphics g, Font font, AbstractWidget widget, boolean selected) {
        int x = widget.getX();
        int y = widget.getY();
        int w = widget.getWidth();
        int h = widget.getHeight();
        boolean lit = selected && widget.active;
        int edge = !widget.active ? 0x88 : lit ? 0xFFF4F8FA : 0xB8B6C3C9;
        int t = lit ? 2 : 1;
        g.fill(x, y, x + w, y + t, edge);
        g.fill(x, y + h - t, x + w, y + h, edge);
        g.fill(x, y, x + t, y + h, edge);
        g.fill(x + w - t, y, x + w, y + h, edge);

        // Референс оставляет рамки пустыми; подпись появляется только у выбранной кнопки.
        if (!selected) return;
        String label = widget.getMessage().getString();
        if (label.isBlank()) return;
        int color = !widget.active ? 0xFF7E8A90 : 0xFFE9F0F3;
        // Подпись может быть шире рамки (особенно при GUI scale 4): сжимаем, чтобы не вылезала за края.
        int textW = font.width(label);
        float scale = Math.min(1f, (w - 6f) / Math.max(1, textW));
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, y + (h - font.lineHeight * scale) / 2f, 0f);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, label, -textW / 2, 0, color, false);
        g.pose().popPose();
    }

    private static void drawEye(GuiGraphics g, float ex, float ey, float eyeW, AbstractWidget selected,
                                long now, float level) {
        float tx = selected == null ? ex : selected.getX() + selected.getWidth() / 2f;
        float ty = selected == null ? ey : selected.getY() + selected.getHeight() / 2f;
        float wantX = Mth.clamp((tx - ex) / (eyeW * 0.58f), -1f, 1f);
        float wantY = Mth.clamp((ty - ey) / (eyeW * 0.42f), -0.9f, 0.9f);
        if (Float.isNaN(gazeX) || now - gazeAt > 500L) {
            gazeX = wantX;
            gazeY = wantY;
        } else {
            gazeX += (wantX - gazeX) * 0.30f;
            gazeY += (wantY - gazeY) * 0.30f;
        }
        gazeAt = now;
        // Живая мелкая поправка: зрачок не стоит идеально ровно.
        float dx = gazeX + (float) Math.sin(now / 890.0) * 0.02f;
        float dy = gazeY + (float) Math.sin(now / 1130.0) * 0.015f;
        float blink = blink(now, 5600, 0);
        float open = Mth.clamp((0.97f + 0.03f * level) * blink, 0.08f, 1f);
        EyeGlyphs.draw(g, ex, ey, eyeW, open, dx, dy, 0.90f, 0f);
    }

    /** 1 = глаз открыт, ~0.08 = закрыт. Моргание 170 мс раз в period мс. */
    private static float blink(long now, int periodMs, int phaseMs) {
        long ph = Math.floorMod(now + phaseMs, periodMs);
        if (ph > 170) return 1f;
        return 1f - 0.92f * (float) Math.sin(Math.PI * ph / 170.0);
    }

    private static void drawCorners(GuiGraphics g, int w, int h, long now, float level) {
        // Три места как в референсе: верх, низ слева, низ справа.
        drawCorner(g, w, h, now, level, 0, w * 0.24f, h * 0.145f, -4.5f);
        drawCorner(g, w, h, now, level, 1, w * 0.03f, h * 0.93f, 2.0f);
        drawCorner(g, w, h, now, level, 2, w * 0.69f, h * 0.94f, -2.5f);
    }

    private static void drawCorner(GuiGraphics g, int w, int h, long now, float level,
                                   int corner, float x, float y, float angle) {
        // Сдвиг по фазе: углы меняют фразу не одновременно.
        long local = now + corner * 2400L;
        int idx = (int) Math.floorMod(local / PHRASE_MS + corner * 3L, LINES);
        Component key = Component.translatable("hypersomnia.swarm." + idx);
        String text = key.getString();
        if (text.equals("hypersomnia.swarm." + idx)) text = FALLBACK[idx];
        // Медленное проявление и угасание внутри цикла.
        long ph = Math.floorMod(local, PHRASE_MS);
        float f = ph / (float) PHRASE_MS;
        float env = f < 0.16f ? f / 0.16f : f > 0.78f ? (1f - f) / 0.22f : 1f;
        float alpha = Mth.clamp((0.34f + 0.20f * level) * env, 0f, 0.62f);
        if (alpha <= 0.01f) return;
        float px = Mth.clamp(h * 0.050f, 15f, 40f);
        boolean smooth = FragmentFont.ready();
        Font mcFont = Minecraft.getInstance().font;
        long q = now / JITTER_MS;

        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        g.setColor(0.90f, 0.93f, 0.95f, alpha);
        if (smooth) {
            float pen = 0f;
            int i = 0;
            while (i < text.length()) {
                int cp = text.codePointAt(i);
                i += Character.charCount(cp);
                if (cp == ' ') {
                    pen += FragmentFont.advance(cp, px);
                    continue;
                }
                long hash = mix(corner * 131L + idx, i) ^ q;
                float jx = (((hash) & 15L) - 7.5f) / 7.5f * 0.55f;
                float jy = (((hash >>> 4) & 15L) - 7.5f) / 7.5f * 0.55f;
                float rot = (((hash >>> 8) & 15L) - 7.5f) / 7.5f * 3.0f;
                g.pose().pushPose();
                g.pose().translate(pen, 0f, 0f);
                g.pose().mulPose(Axis.ZP.rotationDegrees(rot));
                FragmentFont.glyph(g, cp, jx, jy, px);
                g.pose().popPose();
                pen += FragmentFont.advance(cp, px);
            }
        } else {
            g.drawString(mcFont, text, 0, 0, (int) (alpha * 255f) << 24 | 0xDCE6EA, false);
        }
        g.setColor(1f, 1f, 1f, 1f);
        g.pose().popPose();
    }

    private static long mix(long a, long b) {
        long hash = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
        hash ^= hash >>> 29;
        hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 32;
        return hash;
    }
}
