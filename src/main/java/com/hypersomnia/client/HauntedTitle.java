package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraft.resources.ResourceLocation;
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
 * логотип, сплэш, версия, копирайт и подписи не рисуются; подпись выбранной кнопки показывается внутри её рамки,
 * чтобы меню оставалось рабочим. Все надписи — ванильным шрифтом Minecraft.
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

    private static final ResourceLocation TITLE_BLACK = tex("title_black");
    private static final ResourceLocation TITLE_GLOW = tex("title_glow");

    private static boolean loaded;
    private static int maxStage;
    /** Шаг времени для мелкой дрожи букв в углах: обновляется редко, чтобы не было ряби каждый кадр. */
    private static final int JITTER_MS = 110;
    /** Ванильная опция «Скрывать сплэши» временно включается только на кадр титульного экрана. */
    private static boolean splashForced;
    private static boolean splashWasHidden;
    /** Сглаженный взгляд зрачка: цель меняется при смене кнопки. */
    private static float gazeX = Float.NaN;
    private static float gazeY = Float.NaN;
    private static long gazeAt;
    /** Рябь включается заново, когда курсор переходит на другую рамку. */
    private static AbstractWidget hoveredLast;
    private static long hoverAt;

    /**
     * Раскладка из референса: восемь рамок вокруг центрального глаза. Координаты — доли экрана, чтобы меню
     * одинаково выглядело на 720p, 1080p и 4K.
     */
    private record Box(float x, float y, float w, float h) {}

    private static final Box TOP = new Box(0.402f, 0.184f, 0.196f, 0.108f);
    private static final Box UPPER_LEFT = new Box(0.272f, 0.266f, 0.100f, 0.090f);
    private static final Box UPPER_RIGHT = new Box(0.628f, 0.266f, 0.100f, 0.090f);
    private static final Box MID_LEFT = new Box(0.202f, 0.415f, 0.196f, 0.104f);
    private static final Box MID_RIGHT = new Box(0.602f, 0.415f, 0.196f, 0.104f);
    private static final Box LOWER_LEFT = new Box(0.274f, 0.574f, 0.106f, 0.098f);
    private static final Box LOWER_RIGHT = new Box(0.620f, 0.574f, 0.106f, 0.098f);
    private static final Box BOTTOM = new Box(0.405f, 0.632f, 0.190f, 0.112f);

    /** Куда ставить текстовые кнопки: Singleplayer, Multiplayer, Realms, Mods, Options, Quit. */
    private static final Box[] TEXT_BOXES = {TOP, MID_LEFT, MID_RIGHT, LOWER_LEFT, LOWER_RIGHT, BOTTOM};
    /** Куда ставить иконки: Language и Accessibility. */
    private static final Box[] ICON_BOXES = {UPPER_LEFT, UPPER_RIGHT};
    /** Уже расставленные виджеты в смысловом порядке: первая — Singleplayer, с неё начинается выбор. */
    private static final List<AbstractWidget> ORDER = new ArrayList<>();

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "textures/misc/" + name + ".png");
    }

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
        restoreSplash();
        if (!(event.getScreen() instanceof TitleScreen screen) || !haunted()) return;
        layout(screen);
    }

    /**
     * Сплэш — ванильная шутка, которая не вписывается в пустой экран. Вместо рефлексии используем готовую
     * опцию «Скрывать сплэши»: включаем её на время кадра и возвращаем прежнее значение после отрисовки,
     * чтобы не менять настройки игрока.
     */
    @SubscribeEvent
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof TitleScreen) || !haunted()) {
            restoreSplash();
            return;
        }
        if (splashForced) return;
        Minecraft mc = Minecraft.getInstance();
        splashWasHidden = mc.options.hideSplashTexts().get();
        splashForced = true;
        if (!splashWasHidden) mc.options.hideSplashTexts().set(true);
    }

    private static void restoreSplash() {
        if (!splashForced) return;
        splashForced = false;
        if (!splashWasHidden) Minecraft.getInstance().options.hideSplashTexts().set(false);
    }

    /**
     * Рисуем поверх ванильного заголовка: чёрный кадр, пустые рамки, глаз в центре и обрывки фраз.
     * Кнопки остаются рабочими — меняются только их места, размеры и вид.
     */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof TitleScreen screen) || !haunted()) {
            restoreSplash();
            return;
        }
        int w = screen.width;
        int h = screen.height;
        if (w <= 0 || h <= 0) {
            restoreSplash();
            return;
        }
        layout(screen);
        long now = System.currentTimeMillis();
        float level = Mth.clamp((maxStage - (MIN_STAGE - 1)) / 3f, 0f, 1f);
        GuiGraphics g = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;

        AbstractWidget selected = selectedButton(event.getMouseX(), event.getMouseY());
        AbstractWidget hover = hovered(event.getMouseX(), event.getMouseY());
        if (hover != hoveredLast) {
            hoveredLast = hover;
            hoverAt = now;
        }
        float eyeW = eyeWidth(w, h);
        float eyeX = w / 2f;
        float eyeY = h * 0.47f;

        // 1) Сбрасываем накопленные ванильные батчи (версия, брендинг, сплэш), затем закрываем экран чёрным.
        //    Без flush текст может оказаться в буфере, который отрисуется уже после заливки.
        g.flush();
        g.blit(TITLE_BLACK, 0, 0, 0, 0f, 0f, w, h, w, h);

        // 2) Мягкое свечение вокруг глаза: чёрный экран остаётся чёрным, но центр получает глубину.
        drawGlow(g, eyeX, eyeY, eyeW, now, level);

        // 3) Восемь пустых рамок: неровные штрихи, лёгкое дыхание яркости, подсветка выбранной и рябь под курсором.
        for (GuiEventListener listener : screen.children()) {
            if (!(listener instanceof AbstractWidget widget) || !widget.visible) continue;
            drawFrame(g, widget, widget == selected, widget == hover, now - hoverAt, now);
        }

        // 4) Глаз осколка в центре. Зрачок поворачивается к выбранной кнопке и не выходит за белок.
        drawEye(g, eyeX, eyeY, eyeW, selected, now, level);

        // 5) Фиксируем всю графику, чтобы буквы гарантированно легли сверху, а не под рамками.
        g.flush();

        // 6) Обрывки фраз в углах — ванильный шрифт с очень мелкой дрожью.
        drawCorners(g, font, w, h, now, level);

        // 7) Подпись только у выбранной кнопки: рамки остаются пустыми, как в референсе.
        if (selected != null) drawLabel(g, font, selected);

        // 8) Тултипы (язык, доступность, отключённый мультиплеер) оказались под чёрным кадром — возвращаем сверху.
        if (hover != null) {
            Tooltip tip = hover.getTooltip();
            if (tip != null) {
                g.renderTooltip(font, tip.toCharSequence(Minecraft.getInstance()),
                        event.getMouseX(), event.getMouseY());
            }
        }

        restoreSplash();
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
        int w = Math.max(56, Math.round(box.w * sw));
        int h = Math.max(22, Math.round(box.h * sh));
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

    private static void drawGlow(GuiGraphics g, float cx, float cy, float eyeW, long now, float level) {
        float pulse = (float) Math.sin(now / 1700.0) * 0.03f;
        float size = eyeW * (3.15f + pulse + 0.10f * level);
        float alpha = Mth.clamp(0.42f + 0.14f * level, 0f, 0.68f);
        int s = Math.max(8, Math.round(size));
        int x = Math.round(cx - s / 2f);
        int y = Math.round(cy - s / 2f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1f, 1f, 1f, alpha);
        g.blit(TITLE_GLOW, x, y, 0, 0f, 0f, s, s, s, s);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    /**
     * Рамка нарисована «от руки»: тонкая неровная линия с утолщениями и чуть выходящими углами. Выбранная
     * кнопка получает мягкое свечение и очень слабую заливку, а под курсором по рамке бежит рябь.
     */
    private static void drawFrame(GuiGraphics g, AbstractWidget widget, boolean selected, boolean hovered,
                                  long hoverAge, long now) {
        int x = widget.getX();
        int y = widget.getY();
        int w = widget.getWidth();
        int h = widget.getHeight();
        boolean lit = selected && widget.active;
        boolean ripple = hovered && widget.active;
        int edge = !widget.active ? 0xB85C666B : lit ? 0xFFF3F8FA : 0xC0AEB9BE;

        // Рябь расходится от рамки наружу, поэтому рисуется первой — под линиями.
        if (ripple) drawRipple(g, x, y, w, h, hoverAge, now);

        if (lit) {
            int pulse = (int) Math.round(2.0 + 2.0 * Math.sin(now / 520.0));
            int boost = hovered ? 6 : 0;
            for (int i = 5; i >= 1; i--) {
                int a = Math.min(44, 8 + pulse + boost + (5 - i) * 4);
                outline(g, x - i, y - i, w + i * 2, h + i * 2, (a << 24) | 0xC9E2EA);
            }
            // Едва заметная холодная дымка внутри — пустая рамка не выглядит мёртвой.
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hovered ? 0x0CE6F0F2 : 0x07E6F0F2);
        }

        // Тонкая внутренняя линия, будто рамка выдавлена в темноте.
        if (lit || ripple) {
            outline(g, x + 3, y + 3, w - 6, h - 6, 0x28D5E4EA);
        }

        // Базовая линия и мелкие «перекосы» пера.
        g.fill(x, y, x + w, y + 1, edge);
        g.fill(x, y + h - 1, x + w, y + h, edge);
        g.fill(x, y, x + 1, y + h, edge);
        g.fill(x + w - 1, y, x + w, y + h, edge);

        int tx = Math.max(6, w / 6);
        int ty = Math.max(4, h / 5);
        g.fill(x + w / 5, y, x + w / 5 + tx, y + 2, edge);
        g.fill(x + w - w / 4, y + h - 2, x + w - w / 4 + tx, y + h, edge);
        g.fill(x, y + h / 2, x + 2, y + h / 2 + ty, edge);
        g.fill(x + w - 2, y + h / 6, x + w, y + h / 6 + ty, edge);

        // Углы чуть выходят за прямоугольник, как у настоящего наброска; у выбранной — чуть заметнее.
        int over = lit ? 2 : 1;
        int corner = lit ? Math.max(5, Math.min(12, w / 9)) : 4;
        g.fill(x - over, y - over, x + corner, y, edge);
        g.fill(x - over, y - over, x, y + corner, edge);
        g.fill(x + w - corner, y - over, x + w + over, y, edge);
        g.fill(x + w, y - over, x + w + over, y + corner, edge);
        g.fill(x - over, y + h, x + corner, y + h + over, edge);
        g.fill(x - over, y + h - corner, x, y + h, edge);
        g.fill(x + w - corner, y + h, x + w + over, y + h + over, edge);
        g.fill(x + w, y + h - corner, x + w + over, y + h, edge);

        // По рамке под курсором бегут световые штрихи — как рябь по воде.
        if (ripple) {
            float p = (hoverAge % 1700L) / 1700f;
            int dash = Math.max(10, (w + h) / 10);
            perimeterDash(g, x, y, w, h, p, dash, 0x78EAF4F7);
            perimeterDash(g, x, y, w, h, (p + 0.5f) % 1f, Math.max(6, dash / 2), 0x30DAE8EC);
        }
    }

    /** Две расходящиеся рамки-волны и медленные горизонтальные полосы помех внутри. */
    private static void drawRipple(GuiGraphics g, int x, int y, int w, int h, long age, long now) {
        float maxD = Math.max(10f, Math.min(28f, h * 0.30f));
        for (int k = 0; k < 2; k++) {
            float f = ((age + k * 760f) % 1520f) / 1520f;
            int a = (int) ((1f - f) * (1f - f) * 78f);
            if (a < 4) continue;
            int d = Math.round(f * maxD);
            outline(g, x - d, y - d, w + d * 2, h + d * 2, (a << 24) | 0xD6EAF0);
        }

        long q = now / 90L;
        for (int i = 0; i < 3; i++) {
            int yy = y + 2 + (int) Math.floorMod(q + i * (long) Math.max(1, h / 3), Math.max(1, h - 4));
            int a = 8 + (int) (6 * Math.sin(now / 300.0 + i * 2.1));
            g.fill(x + 3, yy, x + w - 3, yy + 1, (Math.max(4, a) << 24) | 0xCFE0E6);
        }
    }

    /** Короткий штрих, бегущий по периметру рамки; phase 0..1, len в пикселях. */
    private static void perimeterDash(GuiGraphics g, int x, int y, int w, int h,
                                      float phase, int len, int color) {
        int total = 2 * (w + h);
        if (total <= 0) return;
        int count = Math.min(len, total);
        int start = Math.round(Math.floorMod((long) (phase * total), (long) total));
        for (int i = 0; i < count; i++) {
            int t = Math.floorMod(start + i, total);
            if (t < w) {
                g.fill(x + t, y, x + t + 1, y + 2, color);
            } else if (t < w + h) {
                g.fill(x + w - 2, y + t - w, x + w, y + t - w + 1, color);
            } else if (t < 2 * w + h) {
                int u = t - w - h;
                g.fill(x + w - 1 - u, y + h - 2, x + w - u, y + h, color);
            } else {
                int u = t - 2 * w - h;
                g.fill(x, y + h - 1 - u, x + 2, y + h - u, color);
            }
        }
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void drawLabel(GuiGraphics g, Font font, AbstractWidget widget) {
        String label = widget.getMessage().getString();
        if (label.isBlank()) return;
        int x = widget.getX();
        int y = widget.getY();
        int w = widget.getWidth();
        int h = widget.getHeight();
        int color = !widget.active ? 0xFF7C878C : 0xFFEDF4F6;
        // Подпись может быть шире рамки (GUI scale 4) или теряться в большой рамке: подгоняем в обе стороны.
        int textW = font.width(label);
        float scale = Math.min(2f, Math.min((w - 14f) / Math.max(1, textW),
                (h - 12f) / Math.max(1, font.lineHeight)));
        scale = Math.max(0.55f, scale);
        float baseY = y + (h - font.lineHeight * scale) / 2f;
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, baseY, 0f);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, label, -textW / 2, 0, color, false);
        g.pose().popPose();

        int underline = Math.min(w - 10, Math.round(textW * scale) + 10);
        if (underline > 0) {
            int ux = x + (w - underline) / 2;
            int uy = y + h - 5;
            g.fill(ux, uy, ux + underline, uy + 1, (color & 0x00FFFFFF) | 0x50000000);
        }
    }

    private static void drawEye(GuiGraphics g, float ex, float ey, float eyeW, AbstractWidget selected,
                                long now, float level) {
        float tx = selected == null ? ex : selected.getX() + selected.getWidth() / 2f;
        float ty = selected == null ? ey : selected.getY() + selected.getHeight() / 2f;
        float wantX = Mth.clamp((tx - ex) / (eyeW * 0.72f), -0.75f, 0.75f);
        float wantY = Mth.clamp((ty - ey) / (eyeW * 0.62f), -0.42f, 0.42f);
        // Белок миндалевидный: при одновременном смещении по обеим осям зрачок вылезал бы за его край.
        // Поэтому держим суммарное смещение внутри единичного радиуса.
        float r = Mth.sqrt((wantX / 0.75f) * (wantX / 0.75f) + (wantY / 0.42f) * (wantY / 0.42f));
        if (r > 1f) {
            wantX /= r;
            wantY /= r;
        }
        if (Float.isNaN(gazeX) || now - gazeAt > 500L) {
            gazeX = wantX;
            gazeY = wantY;
        } else {
            gazeX += (wantX - gazeX) * 0.30f;
            gazeY += (wantY - gazeY) * 0.30f;
        }
        gazeAt = now;
        // Живая мелкая поправка: зрачок не стоит идеально ровно.
        float dx = gazeX + (float) Math.sin(now / 890.0) * 0.012f;
        float dy = gazeY + (float) Math.sin(now / 1130.0) * 0.010f;
        float blink = blink(now, 5600, 0);
        float open = Mth.clamp((0.97f + 0.03f * level) * blink, 0.08f, 1f);
        // Радужка чуть меньше проёма: белок миндалевидный, и при повороте зрачка полный круг вылезал бы за него.
        EyeGlyphs.draw(g, ex, ey, eyeW, open, dx, dy, 0.90f, 0f, 0.84f);
    }

    /** 1 = глаз открыт, ~0.08 = закрыт. Моргание 170 мс раз в period мс. */
    private static float blink(long now, int periodMs, int phaseMs) {
        long ph = Math.floorMod(now + phaseMs, periodMs);
        if (ph > 170) return 1f;
        return 1f - 0.92f * (float) Math.sin(Math.PI * ph / 170.0);
    }

    private static void drawCorners(GuiGraphics g, Font font, int w, int h, long now, float level) {
        // Три места как в референсе: верх по центру, низ слева, низ справа.
        drawCorner(g, font, now, level, 0, w * 0.50f, h * 0.135f, -4.5f, 0);
        drawCorner(g, font, now, level, 1, w * 0.03f, h * 0.93f, 2.0f, -1);
        drawCorner(g, font, now, level, 2, w * 0.97f, h * 0.94f, -2.5f, 1);
    }

    /** align: -1 слева, 0 по центру, 1 справа. Шрифт — ванильный Minecraft, с лёгкой дрожью букв. */
    private static void drawCorner(GuiGraphics g, Font font, long now, float level,
                                   int corner, float x, float y, float angle, int align) {
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
        float alpha = Mth.clamp((0.40f + 0.20f * level) * env, 0f, 0.66f);
        if (alpha <= 0.01f) return;

        float tracking = 0.45f;
        float width = 0f;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            width += font.width(new String(Character.toChars(cp))) + tracking;
        }
        float startX = align < 0 ? 0f : align > 0 ? -width : -width / 2f;

        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().mulPose(Axis.ZP.rotationDegrees(angle));

        int color = ((int) (alpha * 255f) << 24) | 0xE4EDF0;
        long q = now / JITTER_MS;
        float pen = startX;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            String glyph = new String(Character.toChars(cp));
            long hash = mix(corner * 131L + idx, i) ^ q;
            float jx = ((hash & 15L) - 7.5f) / 7.5f * 0.45f;
            float jy = (((hash >>> 4) & 15L) - 7.5f) / 7.5f * 0.55f;
            float rot = (((hash >>> 8) & 15L) - 7.5f) / 7.5f * 1.8f;
            g.pose().pushPose();
            g.pose().translate(pen + jx, jy, 0f);
            g.pose().mulPose(Axis.ZP.rotationDegrees(rot));
            g.drawString(font, glyph, 0, 0, color, false);
            g.pose().popPose();
            pen += font.width(glyph) + tracking;
        }
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
