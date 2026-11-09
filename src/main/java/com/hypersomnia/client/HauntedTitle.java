package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
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

/**
 * Главное меню после стадии 3+ [новое].
 *
 * Игрок дошёл до стадии 3 хотя бы в одном мире — и главное меню перестаёт быть обычным. Панорама уходит в чёрный,
 * кнопки становятся пустыми рамками, а рядом с выбранной кнопкой появляется глаз осколка: он смотрит на неё, моргает
 * и ждёт, пока игрок нажмёт. Это не толпа глаз и не глаз в интерфейсе в мире: осколок смотрит на игрока снаружи,
 * когда тот ещё не начал сеанс. Полученная стадия хранится в config/hypersomnia_title.txt и переживает перезапуск,
 * поэтому эффект включается один раз и остаётся.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class HauntedTitle {
    private HauntedTitle() {}

    /** С этой стадии главное меню считается «испорченным». */
    private static final int MIN_STAGE = 3;
    private static final String FILE_NAME = "hypersomnia_title.txt";
    private static final ResourceLocation VIGNETTE =
            ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "textures/misc/fatigue_vignette.png");

    /** 20 строк роя в lang; здесь используются как редкие обрывки в углах меню. */
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
    /** Плавное перемещение глаза между кнопками, чтобы он не телепортировался. */
    private static float slideX = Float.NaN;
    private static float slideY = Float.NaN;
    private static long slideAt;

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

    /**
     * Рисуем поверх ванильного заголовка: гасим панораму, обводим кнопки, ставим глаз у выбранной.
     * Кнопки остаются рабочими — меняется только их вид.
     */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof TitleScreen screen) || !haunted()) return;
        int w = screen.width;
        int h = screen.height;
        if (w <= 0 || h <= 0) return;
        long now = System.currentTimeMillis();
        float level = Mth.clamp((maxStage - (MIN_STAGE - 1)) / 3f, 0f, 1f);
        GuiGraphics g = event.getGuiGraphics();

        // 1) Панорама почти гаснет: остаются только силуэты, как за выключенным монитором.
        int veil = (int) (Mth.lerp(level, 234f, 248f));
        g.fill(0, 0, w, h, (veil << 24) | 0x03050A);

        // 2) Тоннель по краям: меню сжимается, как кадр записи.
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1f, 1f, 1f, 0.62f + 0.18f * level);
        g.blit(VIGNETTE, 0, 0, -90, 0f, 0f, w, h, w, h);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        // 3) Пиксельная эрозия по краям. Центр не трогаем: там кнопки и глаз.
        AbstractWidget selected = selectedButton(screen, event.getMouseX(), event.getMouseY());
        float eyeW = Mth.clamp(h * 0.15f, 58f, 124f);
        float eyeTargetX = eyeX(w, selected, eyeW);
        float eyeTargetY = selected == null ? h * 0.5f : selected.getY() + selected.getHeight() / 2f;
        if (Float.isNaN(slideX) || now - slideAt > 500L) {
            slideX = eyeTargetX;
            slideY = eyeTargetY;
        } else {
            slideX += (eyeTargetX - slideX) * 0.22f;
            slideY += (eyeTargetY - slideY) * 0.22f;
        }
        slideAt = now;
        float eyeX = slideX;
        float eyeY = slideY;
        float[] avoid = avoid(screen, eyeX, eyeY, eyeW);
        PixelDecay.draw(g, w, h, now, 0x7A11C0DEL, 0.20f + 0.16f * level, 0.10f + 0.10f * level,
                0.02f + 0.03f * level, avoid);

        // 4) Обрывки фраз в углах: тонкий шрифт, медленно перебирается и дрожит.
        drawCorners(g, w, h, now, level);

        // 5) Кнопки: чёрные коробки с бледной рамкой, подписи остаются читаемыми.
        Font font = Minecraft.getInstance().font;
        for (GuiEventListener listener : screen.children()) {
            if (!(listener instanceof AbstractWidget widget) || !widget.visible) continue;
            drawButton(g, font, widget, widget == selected, event.getMouseX(), event.getMouseY(),
                    event.getPartialTick(), now);
        }

        // 6) Глаз осколка у выбранной кнопки. Зрачок направлен точно на неё.
        if (selected != null) {
            drawEye(g, eyeX, eyeY, eyeW, selected, now, level);
        }
    }

    /** Кнопка под мышью, иначе кнопка с клавиатурным фокусом, иначе первая видимая и активная. */
    private static AbstractWidget selectedButton(TitleScreen screen, double mx, double my) {
        AbstractWidget focused = null;
        AbstractWidget first = null;
        AbstractWidget fallback = null;
        for (GuiEventListener listener : screen.children()) {
            if (!(listener instanceof AbstractWidget widget) || !widget.visible) continue;
            if (fallback == null) fallback = widget;
            if (!widget.active) continue;
            if (first == null) first = widget;
            if (widget.isMouseOver(mx, my)) return widget;
            if (focused == null && widget.isFocused()) focused = widget;
        }
        return focused != null ? focused : first != null ? first : fallback;
    }

    /** Справа от кнопки; если места нет — слева. */
    private static float eyeX(int w, AbstractWidget selected, float eyeW) {
        float half = eyeW / 2f;
        if (selected == null) return w / 2f;
        float margin = eyeW * 0.72f + 14f;
        float right = selected.getX() + selected.getWidth() + margin;
        if (right + half <= w - 6f) return right;
        float left = selected.getX() - margin;
        if (left - half >= 6f) return left;
        return Mth.clamp(w / 2f, half + 4f, w - half - 4f);
    }

    /** Прямоугольники кнопок и глаза, куда эрозия не заходит. */
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

    private static void drawButton(GuiGraphics g, Font font, AbstractWidget widget, boolean selected,
                                   int mouseX, int mouseY, float partialTick, long now) {
        int x = widget.getX();
        int y = widget.getY();
        int w = widget.getWidth();
        int h = widget.getHeight();
        String label = widget.getMessage().getString();
        boolean hasText = !label.isBlank();

        // Иконки (язык, доступность) перерисовываем целиком поверх затемнения: иначе от них останется пустая коробка.
        if (!hasText || widget instanceof SpriteIconButton) {
            widget.render(g, mouseX, mouseY, partialTick);
        } else {
            g.fill(x, y, x + w, y + h, 0xF4000000);
        }

        boolean lit = selected && widget.active;
        int edge = !widget.active ? 0x70 : lit ? 0xF2 : 0xA0;
        int rgb = !widget.active ? 0x6E7B82 : lit ? 0xDCE7EB : 0x9DAEB6;
        int t = lit ? 2 : 1;
        g.fill(x, y, x + w, y + t, (edge << 24) | rgb);
        g.fill(x, y + h - t, x + w, y + h, (edge << 24) | rgb);
        g.fill(x, y, x + t, y + h, (edge << 24) | rgb);
        g.fill(x + w - t, y, x + w, y + h, (edge << 24) | rgb);

        if (!hasText) return;
        int color = !widget.active ? 0xFF7E8A90 : lit ? 0xFFE9F0F3 : 0xFFB9C6CC;
        // На выбранной кнопке подпись чуть дрожит: как будто её держит не совсем твёрдая рука.
        int jitter = selected && ((now / 130) & 7) == 0 ? ((now / 130) & 1) == 0 ? 1 : -1 : 0;
        g.drawCenteredString(font, widget.getMessage(), x + w / 2 + jitter, y + h / 2 - 4, color);
    }

    private static void drawEye(GuiGraphics g, float ex, float ey, float eyeW, AbstractWidget selected,
                                long now, float level) {
        float tx = selected.getX() + selected.getWidth() / 2f;
        float ty = selected.getY() + selected.getHeight() / 2f;
        float dx = Mth.clamp((tx - ex) / (eyeW * 0.58f), -0.9f, 0.9f);
        float dy = Mth.clamp((ty - ey) / (eyeW * 0.40f), -0.8f, 0.8f);
        // Живая мелкая поправка: зрачок не стоит идеально ровно.
        dx += (float) Math.sin(now / 890.0) * 0.025f;
        dy += (float) Math.sin(now / 1130.0) * 0.018f;
        float blink = blink(now, 5600, 0);
        float open = Mth.clamp((0.96f + 0.04f * level) * blink, 0.08f, 1f);
        // Глаз не должен вспыхивать на чёрном: бледная склера и полупрозрачность.
        EyeGlyphs.draw(g, ex, ey, eyeW, open, dx, dy, 0.88f, -2f + 2f * level);
    }

    /** 1 = глаз открыт, ~0.08 = закрыт. Моргание 170 мс раз в period мс. */
    private static float blink(long now, int periodMs, int phaseMs) {
        long ph = Math.floorMod(now + phaseMs, periodMs);
        if (ph > 170) return 1f;
        return 1f - 0.92f * (float) Math.sin(Math.PI * ph / 170.0);
    }

    private static void drawCorners(GuiGraphics g, int w, int h, long now, float level) {
        // Три места как в наброске автора: верх, низ слева, низ справа.
        drawCorner(g, w, h, now, level, 0, w * 0.16f, h * 0.11f, -4.5f);
        drawCorner(g, w, h, now, level, 1, w * 0.05f, h * 0.88f, 2.0f);
        drawCorner(g, w, h, now, level, 2, w * 0.65f, h * 0.93f, -2.5f);
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
        float alpha = Mth.clamp((0.26f + 0.18f * level) * env, 0f, 0.55f);
        if (alpha <= 0.01f) return;
        float px = Mth.clamp(h * 0.047f, 15f, 38f);
        boolean smooth = FragmentFont.ready();
        Font mcFont = Minecraft.getInstance().font;
        long q = now / JITTER_MS;

        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        g.setColor(0.86f, 0.90f, 0.92f, alpha);
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
                FragmentFont.glyph(g, cp, pen + jx, jy, px);
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
