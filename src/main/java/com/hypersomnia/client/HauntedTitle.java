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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Главное меню после стадии 3+ [новое].
 *
 * Игрок дошёл до стадии 3 хотя бы в одном мире — и главное меню перестаёт быть обычным, как в референсе автора:
 * чёрный экран, плавающие подписи без рамок, глаз осколка в центре и редкие рукописные обрывки по углам. Кнопки
 * расставляются вокруг глаза, поэтому зрачок может смотреть на любую из них. Это не толпа глаз и не глаз в
 * интерфейсе внутри мира: осколок смотрит на игрока снаружи, пока сеанс ещё не начат.
 *
 * Стадия хранится в config/hypersomnia_title.txt и переживает перезапуск. Пока эффект включён, ванильная отрисовка
 * титульного экрана отменяется целиком: панорама, логотип, сплэш, версия, копирайт и подписи не рисуются. Вместо них
 * мы рисуем чёрный кадр, глаз и подписи всех кнопок одним размером. Все надписи — ванильным шрифтом Minecraft.
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
    /** Шаг времени для мелкой дрожи букв: обновляется редко, чтобы не было ряби каждый кадр. */
    private static final int JITTER_MS = 110;
    /** Общая шкала подписей: зависит только от высоты экрана, поэтому одинакова у всех кнопок. */
    private static final float LABEL_BASE = 1.15f;
    private static final float LABEL_MIN = 0.90f;
    private static final float LABEL_MAX = 2.40f;
    private static final int LABEL_PAD_X = 8;
    private static final int LABEL_PAD_Y = 5;
    /** Сглаженный взгляд зрачка: цель меняется при смене кнопки. */
    private static float gazeX = Float.NaN;
    private static float gazeY = Float.NaN;
    private static long gazeAt;
    /** Рябь включается заново, когда курсор переходит на другую кнопку. */
    private static AbstractWidget hoveredLast;
    private static long hoverAt;

    /**
     * Раскладка: две колонки текста по сторонам глаза, важное — над ним и под ним. Рамок нет, поэтому кнопка —
     * это сам текст и небольшая невидимая область вокруг него. Координаты — доли экрана, чтобы меню одинаково
     * выглядело на 720p, 1080p и 4K.
     */
    private record Spot(float cx, float cy, float maxW) {}

    private static final Spot SINGLE = new Spot(0.500f, 0.155f, 0.260f);
    private static final Spot MULTI = new Spot(0.285f, 0.295f, 0.220f);
    private static final Spot REALMS = new Spot(0.715f, 0.295f, 0.250f);
    private static final Spot MODS = new Spot(0.285f, 0.700f, 0.170f);
    private static final Spot OPTIONS = new Spot(0.715f, 0.700f, 0.200f);
    private static final Spot QUIT = new Spot(0.500f, 0.830f, 0.240f);
    private static final Spot LANGUAGE = new Spot(0.100f, 0.930f, 0.150f);
    private static final Spot ACCESS = new Spot(0.870f, 0.930f, 0.240f);

    /** Куда ставить текстовые кнопки: Singleplayer, Multiplayer, Realms, Mods, Options, Quit. */
    private static final Spot[] TEXT_SPOTS = {SINGLE, MULTI, REALMS, MODS, OPTIONS, QUIT};
    /** Куда ставить иконки: Language и Accessibility. */
    private static final Spot[] ICON_SPOTS = {LANGUAGE, ACCESS};
    /** Уже расставленные виджеты в смысловом порядке: первая — Singleplayer, с неё начинается выбор. */
    private static final List<AbstractWidget> ORDER = new ArrayList<>();
    /** Готовые строки подписи (с переносами) для каждого виджета: кнопка подгоняется под них, а не наоборот. */
    private static final Map<AbstractWidget, List<String>> LABEL_LINES = new IdentityHashMap<>();

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

    /** Кнопки ставятся сразу при открытии меню, чтобы первый клик попал в новую раскладку. */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen screen) || !haunted()) return;
        layout(screen);
    }

    /**
     * Отменяем ванильную отрисовку титульного экрана целиком. Иначе панорама, логотип, сплэш, версия
     * Minecraft, брендинг NeoForge и подписи кнопок приходится закрывать чёрным кадром — а часть текста
     * всё равно оказывается поверх него. В Render.Post при этом событие всё равно приходит, и мы рисуем
     * меню сами: чёрный кадр, глаз, подписи и тултипы.
     */
    @SubscribeEvent
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof TitleScreen && haunted()) {
            event.setCanceled(true);
        }
    }

    /**
     * Панорама есть только вне мира, но её рисуют не только титульный экран, а все меню: выбор мира,
     * создание мира, настройки, язык, доступность и т.д. После стадии 3+ осколок уже смотрит с титульного
     * экрана, поэтому фон остальных меню тоже становится чёрным. Событие приходит после ванильного фона,
     * но до виджетов, так что кнопки, списки и поля ввода остаются поверх и продолжают работать.
     *
     * Event помечен deprecated (NeoForge считает его хрупким), но в 1.21.1 он единственный, кто даёт точку
     * между фоном и виджетами без миксинов; для ванильных экранов он срабатывает стабильно.
     */
    @SuppressWarnings("removal")
    @SubscribeEvent
    public static void onBackgroundRendered(ScreenEvent.BackgroundRendered event) {
        if (!haunted() || event.getScreen() instanceof TitleScreen) return;
        // Внутри мира панорамы нет: там свой фон и своя сцена, их не трогаем.
        if (Minecraft.getInstance().level != null) return;
        int w = event.getScreen().width;
        int h = event.getScreen().height;
        if (w <= 0 || h <= 0) return;
        GuiGraphics g = event.getGuiGraphics();
        g.flush();
        g.fill(0, 0, w, h, 0xFF03050A);
    }

    /**
     * Ванильный рендер отменён, поэтому рисуем титульный экран сами: чёрный кадр, глаз, подписи и тултипы.
     * Кнопки остаются рабочими — их прямоугольники задаёт layout().
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

        // 1) На всякий случай сбрасываем чужие батчи (GUI-слои рисуются раньше меню), затем закрываем экран чёрным.
        g.flush();
        g.blit(TITLE_BLACK, 0, 0, 0, 0f, 0f, w, h, w, h);

        // 2) Мягкое свечение вокруг глаза: чёрный экран остаётся чёрным, но центр получает глубину.
        drawGlow(g, eyeX, eyeY, eyeW, now, level);

        // 3) Глаз осколка в центре. Зрачок поворачивается к выбранной кнопке и не выходит за белок.
        drawEye(g, eyeX, eyeY, eyeW, selected, now, level);

        // 4) Фиксируем всю графику, чтобы буквы гарантированно легли сверху.
        g.flush();

        // 5) Обрывки фраз по углам — ванильный шрифт с очень мелкой дрожью.
        drawCorners(g, font, w, h, now, level);

        // 6) Подписи всех кнопок одним размером: выбранная ярче, под курсором — лёгкая рябь и дрожь букв.
        drawLabels(g, font, selected, hover, now, hoverAt);

        // 7) Тултипы (язык, доступность, отключённый мультиплеер) рисуем сами — ванильные отключены.
        if (hover != null) {
            Tooltip tip = hover.getTooltip();
            if (tip != null) {
                g.renderTooltip(font, tip.toCharSequence(Minecraft.getInstance()),
                        event.getMouseX(), event.getMouseY());
            }
        }
    }

    /**
     * Переставляет ванильные кнопки в текстовую раскладку. Сами кнопки не рисуются (ванильный рендер отменён),
     * но остаются кликабельными: невидимая область вокруг подписи совпадает с тем, что видит игрок.
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
        float scale = labelScale(screen.height);
        Font font = Minecraft.getInstance().font;
        ORDER.clear();
        LABEL_LINES.clear();
        for (int i = 0; i < texts.size(); i++) {
            if (i >= TEXT_SPOTS.length) {
                texts.get(i).visible = false;
                continue;
            }
            place(texts.get(i), TEXT_SPOTS[i], screen.width, screen.height, scale, font);
            ORDER.add(texts.get(i));
        }
        for (int i = 0; i < icons.size(); i++) {
            if (i >= ICON_SPOTS.length) {
                icons.get(i).visible = false;
                continue;
            }
            place(icons.get(i), ICON_SPOTS[i], screen.width, screen.height, scale, font);
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

    /** Ставит невидимую кнопку так, чтобы её центр совпал с точкой раскладки, а размер — с текстом и отступами. */
    private static void place(AbstractWidget widget, Spot spot, int sw, int sh, float scale, Font font) {
        String text = labelText(widget);
        int maxTextW = Math.max(40, Math.round(spot.maxW() * sw));
        List<String> lines = wrap(font, text, Math.max(1, (int) Math.floor(maxTextW / scale)));
        if (lines == null) lines = List.of(text);
        int contentW = 0;
        for (String line : lines) contentW = Math.max(contentW, font.width(line));
        int contentH = lines.size() * font.lineHeight;
        int widgetW = Math.max(32, Math.round(contentW * scale) + LABEL_PAD_X * 2);
        int widgetH = Math.max(18, Math.round(contentH * scale) + LABEL_PAD_Y * 2);
        int cx = Math.round(spot.cx() * sw);
        int cy = Math.round(spot.cy() * sh);
        widget.setX(Mth.clamp(cx - widgetW / 2, 4, Math.max(4, sw - widgetW - 4)));
        widget.setY(Mth.clamp(cy - widgetH / 2, 4, Math.max(4, sh - widgetH - 4)));
        widget.setWidth(widgetW);
        widget.setHeight(widgetH);
        LABEL_LINES.put(widget, lines);
    }

    /** Шкала подписей зависит только от высоты экрана, поэтому у всех кнопок она одинаковая. */
    private static float labelScale(int screenHeight) {
        return Mth.clamp(screenHeight / 270f * LABEL_BASE, LABEL_MIN, LABEL_MAX);
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
        float size = eyeW * (2.95f + pulse + 0.10f * level);
        float alpha = Mth.clamp(0.38f + 0.14f * level, 0f, 0.60f);
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
     * Вместо рамки — тонкая черта под выбранной подписью и очень слабое свечение. Рамки убраны: текст висит
     * в темноте, а глаз остаётся единственным крупным объектом. Черта пульсирует, у наведённой — ярче.
     */
    private static void drawSelection(GuiGraphics g, int cx, int cy, float textW, float textH,
                                      long now, boolean hovered) {
        int half = Math.round(textW / 2f) + 6;
        int y = Math.round(cy + textH / 2f + 5);
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 420.0);
        int alpha = hovered ? 150 + (int) (75 * pulse) : 70 + (int) (45 * pulse);
        g.fill(cx - half - 4, y + 1, cx + half + 4, y + 2, (Math.max(16, alpha / 4) << 24) | 0xDCE8EC);
        g.fill(cx - half, y, cx + half, y + 1, (alpha << 24) | 0xF3F8FA);
    }

    /**
     * Рябь от наведения: две пары коротких линий уходят вверх и вниз от подписи, будто текст отражается
     * в чёрной воде. Полных прямоугольников нет, поэтому эффект не читается как рамка.
     */
    private static void drawHoverRipple(GuiGraphics g, int cx, int cy, float textW, float textH, long age) {
        float maxD = 10f + Math.min(16f, textH * 0.45f);
        for (int k = 0; k < 2; k++) {
            float f = ((age + k * 650f) % 1300f) / 1300f;
            int alpha = (int) ((1f - f) * (1f - f) * 70f);
            if (alpha < 4) continue;
            float d = f * maxD;
            int half = Math.round(textW * 0.5f + 8f + d * 0.8f);
            int yTop = Math.round(cy - textH / 2f - 6f - d);
            int yBot = Math.round(cy + textH / 2f + 6f + d);
            g.fill(cx - half, yTop, cx + half, yTop + 1, (alpha << 24) | 0xE8F2F4);
            g.fill(cx - half, yBot, cx + half, yBot + 1, (alpha << 24) | 0xE8F2F4);
        }
    }

    /** Ширина самой длинной строки подписи в пикселях шрифта (до масштабирования). */
    private static int blockWidth(Font font, List<String> lines) {
        int w = 0;
        for (String line : lines) w = Math.max(w, font.width(line));
        return w;
    }

    /**
     * Рисует блок подписи по центру. Под курсором буквы мелко дрожат и поворачиваются, как будто текст
     * не хочет стоять на месте; в остальное время строка ровная, чтобы меню оставалось удобным.
     */
    private static void drawBlock(GuiGraphics g, Font font, List<String> lines, float cx, float cy,
                                  float scale, int color, boolean jitter, long now) {
        int lineH = font.lineHeight;
        float blockH = lines.size() * lineH * scale;
        g.pose().pushPose();
        g.pose().translate(cx, cy - blockH / 2f, 0f);
        g.pose().scale(scale, scale, 1f);
        long q = now / JITTER_MS;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int y = i * lineH;
            if (!jitter) {
                g.drawString(font, line, -font.width(line) / 2, y, color, false);
                continue;
            }
            float pen = -font.width(line) / 2f;
            for (int j = 0; j < line.length(); ) {
                int cp = line.codePointAt(j);
                j += Character.charCount(cp);
                String glyph = new String(Character.toChars(cp));
                long hash = mix(i * 97L + j, q);
                float jx = ((hash & 7L) - 3.5f) / 3.5f * 0.5f;
                float jy = (((hash >>> 3) & 7L) - 3.5f) / 3.5f * 0.5f;
                float rot = (((hash >>> 6) & 7L) - 3.5f) / 3.5f * 1.4f;
                g.pose().pushPose();
                g.pose().translate(pen + jx, y + jy, 0f);
                g.pose().mulPose(Axis.ZP.rotationDegrees(rot));
                g.drawString(font, glyph, 0, 0, color, false);
                g.pose().popPose();
                pen += font.width(glyph);
            }
        }
        g.pose().popPose();
    }

    /** Все подписи одним размером; под выбранной — черта, под курсором — рябь и дрожь букв. */
    private static void drawLabels(GuiGraphics g, Font font, AbstractWidget selected, AbstractWidget hover,
                                   long now, long hoverAt) {
        float scale = labelScale(Minecraft.getInstance().getWindow().getGuiScaledHeight());
        for (AbstractWidget widget : ORDER) {
            if (!widget.visible) continue;
            List<String> lines = LABEL_LINES.get(widget);
            if (lines == null || lines.isEmpty()) continue;
            int cx = widget.getX() + widget.getWidth() / 2;
            int cy = widget.getY() + widget.getHeight() / 2;
            boolean lit = widget == selected;
            boolean glitch = widget == hover && widget.active;
            int color = !widget.active ? 0xFF59636A : lit ? 0xFFF3F8FA : 0xFFA9B4B9;
            float textW = blockWidth(font, lines) * scale;
            float textH = lines.size() * font.lineHeight * scale;

            if (glitch) {
                drawHoverRipple(g, cx, cy, textW, textH, now - hoverAt);
                // Бледный «двойник» на пару пикселей в сторону — короткий сбой изображения.
                drawBlock(g, font, lines, cx + 2f, cy + 1f, scale, 0x22FFFFFF, false, now);
            }
            if (lit && widget.active) {
                drawSelection(g, cx, cy, textW, textH, now, glitch);
            }
            drawBlock(g, font, lines, cx, cy, scale, color, glitch, now);
        }
    }

    /** Подпись кнопки; у иконочных кнопок сообщение тоже заполнено (options.language / options.accessibility). */
    private static String labelText(AbstractWidget widget) {
        return widget.getMessage().getString();
    }

    /** Перенос по словам; null, если даже одно слово не влезает в заданную ширину. */
    private static List<String> wrap(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            if (font.width(word) > maxWidth) return null;
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (font.width(candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines.isEmpty() ? null : lines;
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
        // Три места: два верхних угла и низ по центру. Нижние углы отданы «Языку» и спец. возможностям.
        drawCorner(g, font, now, level, 0, w * 0.025f, h * 0.045f, 1.2f, -1);
        drawCorner(g, font, now, level, 1, w * 0.975f, h * 0.050f, -1.2f, 1);
        drawCorner(g, font, now, level, 2, w * 0.50f, h * 0.952f, -0.8f, 0);
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
