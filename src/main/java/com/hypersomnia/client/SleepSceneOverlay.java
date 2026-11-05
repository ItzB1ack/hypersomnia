package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.scene.SleepSceneTuning;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Сцена «This isn't a dream» в полной тишине.
 *
 * Тайминг: вступление (тишина, кадр медленно темнеет, в проёме зажигается свет) -> тело сцены
 * (фигура подходит скачками между «морганиями», строки текста, пульс рисует виньетка) ->
 * замирание кадра -> обрыв в чёрное -> чёрная пауза.
 * Звуков у сцены нет: всё, что раньше делал звук, теперь делает картинка.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SleepSceneOverlay {
    private SleepSceneOverlay() {}

    private static final ResourceLocation VIGNETTE =
            ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "textures/misc/fatigue_vignette.png");

    // ---- Фигура ----
    /** Стадии: тик начала (от начала ТЕЛА сцены), масштаб, позиция X, позиция ног Y, наклон головы. */
    static final int[] STAGE_AT = {14, 78, 140, 200};
    private static final float[] STAGE_SCALE = {0.50f, 0.72f, 1.02f, 1.90f};
    private static final float[] STAGE_X = {0.80f, 0.66f, 0.54f, 0.50f};
    private static final float[] STAGE_FEET = {0.80f, 0.90f, 1.02f, 2.10f};
    // ---- Силуэт «потерянного себя» ----
    // Пропорции самого игрока, вытянутые вверх, без деталей и с плывущими краями.
    // Текстуры делает tools/make_silhouette.py. Пикселей на юнит должно совпадать с PX в скрипте.
    private static final int TEX_PX = 8;
    /** Рост фигуры в юнитах (1 юнит = 1/50 роста). */
    private static final float FIG_H = 50f;
    /** Глаза: расстояние от центра и высота центра глаз от макушки (юниты). */
    private static final float EYE_DX = 2.3f;
    /** Общий размер глаз (1 = как нарисованы в текстуре). Больше 1.25 уже не помещаются в голову. */
    private static final float EYE_SCALE = 1.2f;
    private static final float EYE_Y = 5.4f;

    /** Пикселей на юнит у текстур глаз (выше, чем у тела: нужны блики и кромка). Должно совпадать с EYE_PX в скрипте. */
    private static final int EYE_TEX_PX = 24;

    /** Текстура: размер холста и точка привязки (всё в юнитах), px — пикселей на юнит. Для тела (0,0) = середина макушки. */
    private record Part(ResourceLocation tex, float wU, float hU, float ox, float oy, int px) {
        Part(ResourceLocation tex, float wU, float hU, float ox, float oy) {
            this(tex, wU, hU, ox, oy, TEX_PX);
        }
        int w() { return Math.round(wU * px); }
        int h() { return Math.round(hU * px); }
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "textures/misc/" + name + ".png");
    }

    /** Три варианта границы тела: их веса плавно меняются, края «плывут», форма не держится. */
    private static final Part[] SELF = {
            new Part(tex("silhouette_self_a"), 26f, 52f, 13f, 1f),
            new Part(tex("silhouette_self_b"), 26f, 52f, 13f, 1f),
            new Part(tex("silhouette_self_c"), 26f, 52f, 13f, 1f)
    };

    /**
     * Глаза: реальный человеческий глаз «как в инфракрасной съёмке», три слоя:
     * склера (низ), радужка со зрачком (двигается), веки (верх, обрезают радужку).
     * Левый глаз берёт зеркальные текстуры (_r), чтобы светлая сторона склеры была у переносицы.
     */
    private static final Part EYE_SCLERA_L = eye("sclera", 5f, 3.4f, true);
    private static final Part EYE_SCLERA_R = eye("sclera", 5f, 3.4f, false);
    private static final Part EYE_IRIS_L = eye("iris", 2.2f, 2.2f, true);
    private static final Part EYE_IRIS_R = eye("iris", 2.2f, 2.2f, false);
    private static final Part EYE_LIDS_L = eye("lids", 5f, 3.4f, true);
    private static final Part EYE_LIDS_R = eye("lids", 5f, 3.4f, false);

    private static Part eye(String layer, float wU, float hU, boolean mirrored) {
        return new Part(tex("silhouette_eye_" + layer + (mirrored ? "_r" : "")), wU, hU, wU / 2f, hU / 2f, EYE_TEX_PX);
    }
    /** Выпадение кадра при смене стадии (тики). */
    private static final int BLINK_TICKS = 5;

    // Замороженный момент времени для последних тиков
    private static boolean frozenCaptured;
    private static long frozenNow;

    /** Запасной английский текст, если lang-файл мода не содержит ключ (чтобы не светились ключи). */
    private static final Map<String, String> FALLBACK = new HashMap<>();
    static {
        FALLBACK.put("hypersomnia.scene.t1", "This isn't a dream.");
        String[] t2 = {"You can't sleep.", "You are not asleep.", "Close your eyes. Nothing will change.",
                "Sleep did not resume.", "That bed isn't yours.", "Not here. Not yet."};
        String[] t3 = {"Is it my fault?", "I didn't make it in time.", "I should have woken up.", "Tell me it wasn't.",
                "Someone was waiting.", "I tried. Did I try?", "Don't look at me like that."};
        String[] t4 = {"47.", "Subject unresponsive.", "Stay awake."};
        for (int i = 0; i < t2.length; i++) FALLBACK.put("hypersomnia.scene.t2." + i, t2[i]);
        for (int i = 0; i < t3.length; i++) FALLBACK.put("hypersomnia.scene.t3." + i, t3[i]);
        for (int i = 0; i < t4.length; i++) FALLBACK.put("hypersomnia.scene.t4." + i, t4[i]);
    }

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "sleep_scene"),
                SleepSceneOverlay::render
        );
    }

    /** Все фразы сцены: t1, затем t2, t3, t4. Место каждой в кадре определяет seed. */
    private static final String[] KEYS = buildKeys();

    private static String[] buildKeys() {
        String[] keys = new String[1 + SleepSceneTuning.T2_COUNT + SleepSceneTuning.T3_COUNT + SleepSceneTuning.T4_COUNT];
        int n = 0;
        keys[n++] = "hypersomnia.scene.t1";
        for (int i = 0; i < SleepSceneTuning.T2_COUNT; i++) keys[n++] = "hypersomnia.scene.t2." + i;
        for (int i = 0; i < SleepSceneTuning.T3_COUNT; i++) keys[n++] = "hypersomnia.scene.t3." + i;
        for (int i = 0; i < SleepSceneTuning.T4_COUNT; i++) keys[n++] = "hypersomnia.scene.t4." + i;
        return keys;
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        if (!SleepSceneState.active()) {
            frozenCaptured = false;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth();
        int h = g.guiHeight();

        final int lead = SleepSceneTuning.LEAD_IN_TICKS;
        final int total = SleepSceneTuning.BODY_TICKS;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        float raw = SleepSceneState.ticks + partial;   // с самого начала, вместе со вступлением
        float t = raw - lead;                          // время тела сцены; < 0 во вступлении
        float p = Mth.clamp(t / total, 0f, 1f);

        // Замирание: в последние тики всё останавливается (зерно, дрожь, пульс, ползущая фигура)
        boolean frozen = t >= total - SleepSceneTuning.FREEZE_TICKS;
        long realNow = System.currentTimeMillis();
        if (frozen && !frozenCaptured) {
            frozenNow = realNow;
            frozenCaptured = true;
        }
        if (!frozen) frozenCaptured = false;
        long now = frozen ? frozenNow : realNow;
        RandomSource rnd = RandomSource.create(frozen ? 7919L : now / 60L);

        float fadeIn = Mth.clamp(raw / (lead + 12f), 0f, 1f);

        // 1) Вуаль: глаза медленно привыкают к темноте, дальше она только сгущается
        float veil = Math.min(0.97f, fadeIn * (0.90f + 0.07f * p));
        g.fill(0, 0, w, h, ((int) (veil * 255f) << 24) | 0x03050A);

        // 2) Стадия фигуры
        int stage = -1;
        for (int i = 0; i < STAGE_AT.length; i++) if (t >= STAGE_AT[i]) stage = i;
        boolean blink = false;
        for (int i = 1; i < STAGE_AT.length; i++) {
            if (t >= STAGE_AT[i] && t < STAGE_AT[i] + BLINK_TICKS) blink = true; // кадр «пропал» при подходе
        }

        // 2a) Свет в дверном проёме загорается ещё до появления фигуры: слабая вертикальная полоса
        if (stage < 0) {
            float door = Mth.clamp((raw - (lead - 22f)) / 22f, 0f, 1f);
            if (door > 0f) {
                drawGlow(g, w, h, w * STAGE_X[0], h / 50f * STAGE_SCALE[0], door * 0.8f);
            }
        }

        if (stage >= 0 && !blink) {
            // Первые тики стадии 0: нестабильное мерцание
            boolean unstable = stage == 0 && t < STAGE_AT[0] + 22 && rnd.nextInt(3) != 0;
            if (!unstable) {
                float tFig = Math.min(t, total - SleepSceneTuning.FREEZE_TICKS);
                drawFigure(g, w, h, stage, tFig, now, rnd, frozen);
            }
        }
        // Выпадение кадра: на миг всё гаснет, а фигура уже ближе
        if (blink) g.fill(0, 0, w, h, 0xC8000000);

        // 2b) Пульс: тусклый кровяной оттенок на весь кадр в такт «сердцу» (звука нет, бьётся картинка)
        float pulse = SleepSceneState.pulse(partial);
        if (pulse > 0.02f) {
            g.fill(0, 0, w, h, ((int) (pulse * 26f) << 24) | 0x40000A);
        }

        // 2c) Тоннель: края сжимают кадр и прячут границы фигуры; со временем стягивается, с пульсом дышит
        float vig = Mth.clamp((0.78f + 0.22f * p) * fadeIn + 0.10f * pulse, 0f, 1f);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1f, 1f, 1f, vig);
        g.blit(VIGNETTE, 0, 0, -90, 0f, 0f, w, h, w, h);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        // 3) Строки: одно облачко, фразы живут по кругу. Каждая появляется, висит, гаснет и возвращается
        //    в другом месте (новая раскладка на каждый «виток»), пока висит, медленно дрейфует. t1 остаётся в центре.
        final int n = KEYS.length;
        float tt = Math.min(t, total - SleepSceneTuning.FREEZE_TICKS); // в замирании всё стоит
        float step = 10f * SleepSceneTuning.CLOUD_LINE_STEP;
        float halfRows = (n - 1) / 2f;

        // Рисуем с конца: t1 (i = 0) ложится поверх остальных
        for (int i = n - 1; i >= 0; i--) {
            boolean first = i == 0;
            long base = SleepSceneState.seed * 31L + i * 7919L;

            // Свойства фразы, не меняющиеся между витками
            RandomSource ir = RandomSource.create(base + 12345L);
            float start = first ? SleepSceneTuning.TEXT_APPEAR_MIN
                    : Mth.lerp(ir.nextFloat(), SleepSceneTuning.TEXT_APPEAR_MIN, SleepSceneTuning.TEXT_APPEAR_MAX);
            float life = Mth.lerp(ir.nextFloat(), SleepSceneTuning.TEXT_LIFE_MIN, SleepSceneTuning.TEXT_LIFE_MAX);
            float bright = first ? 1f : Mth.lerp(ir.nextFloat(), SleepSceneTuning.TEXT_MIN_BRIGHTNESS, 1f);
            float ph1 = ir.nextFloat() * 6.28f;
            float ph2 = ir.nextFloat() * 6.28f;

            float local = tt - start;
            if (local < 0f) continue;
            int epoch = first ? 0 : (int) (local / life);
            float age = first ? local : local - epoch * life;

            // Раскладка этого витка
            RandomSource er = RandomSource.create(base + epoch * 104729L + 555L);
            float rScale = er.nextFloat();
            float rX = er.nextFloat() - 0.5f;
            int row = first ? n / 2 : er.nextInt(n);
            float rY = er.nextFloat() - 0.5f;
            float rTilt = er.nextFloat() - 0.5f;

            float fade = Mth.clamp(age / SleepSceneTuning.TEXT_FADE_IN, 0f, 1f);
            if (!first) fade *= Mth.clamp((life - age) / SleepSceneTuning.TEXT_FADE_OUT, 0f, 1f);
            int a = (int) (fade * 255f * bright);
            if (a < 8) continue;

            boolean guilt = KEYS[i].startsWith("hypersomnia.scene.t3.");
            int gray = guilt ? 0xC8B8BC : 0xDDE6EC;
            Component text = Component.translatableWithFallback(KEYS[i], FALLBACK.getOrDefault(KEYS[i], KEYS[i]));
            int tw = Math.max(1, mc.font.width(text));

            float want = first ? SleepSceneTuning.TEXT_T1_SCALE
                    : Mth.lerp(rScale, SleepSceneTuning.TEXT_SCALE_MIN, SleepSceneTuning.TEXT_SCALE_MAX);
            float scale = Math.min(want, w * SleepSceneTuning.CLOUD_MAX_W / tw);

            float rowF = row - halfRows;
            float yn = rowF / halfRows;
            float halfW = w * SleepSceneTuning.CLOUD_HALF_W * (float) Math.sqrt(Math.max(0.15f, 1f - yn * yn));
            float drift = SleepSceneTuning.TEXT_DRIFT;
            float cx = w * SleepSceneTuning.CLOUD_CX + rX * 2f * halfW + (float) Math.sin(tt * 0.050f + ph1) * drift;
            float cy = h * SleepSceneTuning.CLOUD_CY + rowF * step + rY * step * 0.6f
                    + (float) Math.sin(tt * 0.037f + ph2) * drift * 0.6f;
            float tilt = rTilt * 2f * SleepSceneTuning.TEXT_MAX_TILT * (first ? 0.4f : 1f);

            int jx = 0;
            int jy = 0;
            if (!frozen && rnd.nextInt(guilt ? 40 : 80) == 0) {
                jx = rnd.nextInt(7) - 3;
                jy = rnd.nextInt(3) - 1;
            }

            g.pose().pushPose();
            g.pose().translate(cx, cy, 0f);
            g.pose().mulPose(Axis.ZP.rotationDegrees(tilt));
            g.pose().scale(scale, scale, 1f);
            int x = jx;
            int y = jy - 4;
            // лёгкая подложка, чтобы строка читалась поверх фигуры и соседних строк
            g.fill(x - tw / 2 - 3, y - 2, x + tw / 2 + 3, y + 10, (a / 3) << 24);
            if (jx != 0) {
                g.drawCenteredString(mc.font, text, x + 2, y, (Math.max(8, a / 3) << 24) | 0xFF3050);
            }
            g.drawCenteredString(mc.font, text, x, y, (a << 24) | gray);
            g.pose().popPose();
        }

        // 3b) Укор осколка после закрытого меню; в первые тики ещё и красная вспышка ярости
        if (SleepSceneState.scoldTicks > 0) {
            int sc = SleepSceneState.scoldTicks;
            drawScold(g, w, h, Mth.clamp(sc / 12f, 0f, 1f));
            int since = SleepSceneTuning.SCOLD_SHOW_TICKS - sc;
            if (since < 14) g.fill(0, 0, w, h, ((int) ((1f - since / 14f) * 70f) << 24) | 0x40000A);
        }

        // 4) Строки развёртки и зерно (в замирании зерно не меняется: стоп-кадр)
        for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, 0x22000000);
        int grains = Math.max(100, w * h / 1200);
        for (int i = 0; i < grains; i++) {
            int x = rnd.nextInt(w);
            int y = rnd.nextInt(h);
            g.fill(x, y, x + 1, y + 1, ((8 + rnd.nextInt(20)) << 24) | 0xFFFFFF);
        }

        // 4b) Сбои: цветные полосы, чаще с приближением; в замирании нет
        if (!frozen && stage >= 1 && rnd.nextInt(Math.max(2, 9 - stage * 2)) == 0) {
            int gy = rnd.nextInt(h);
            g.fill(0, gy, w, gy + 1 + rnd.nextInt(4), 0x30FFFFFF);
            g.fill(0, gy + 3, w, gy + 5, 0x22FF2040);
        }

        // 5) Обрыв: чёрный кадр без вспышек и без звука; держится до конца чёрной паузы
        if (t > total - 2f) g.fill(0, 0, w, h, 0xFF000000);
    }

    /** Прозрачность глаз осколка (1 в сцене; в меню паузы растёт от 0). */
    private static float eyesMul = 1f;

    /** Глаза осколка поверх меню паузы: экран темнеет, глаза проступают, к концу зрачки дрожат. a = 0..1. */
    static void drawPauseEyes(GuiGraphics g, int w, int h, float a) {
        a = Mth.clamp(a, 0f, 1f);
        if (a <= 0f) return;
        g.fill(0, 0, w, h, ((int) (a * 120f) << 24) | 0x03050A);
        float k = h / 18f;
        eyesMul = a;
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.pose().pushPose();
        g.pose().translate(w / 2f, h * 0.42f - EYE_Y * k, 0f);
        drawEyes(g, 3, 0f, System.currentTimeMillis(), k, false, RandomSource.create(System.nanoTime()));
        g.pose().popPose();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        eyesMul = 1f;
    }

    /** Укор осколка по центру кадра; a = 0..1. Вызывается и из оверлея, и поверх меню паузы. */
    static void drawScold(GuiGraphics g, int w, int h, float a) {
        Minecraft mc = Minecraft.getInstance();
        int i = SleepSceneState.scoldCount % 2;
        Component text = Component.translatableWithFallback("hypersomnia.scene.scold." + i,
                i == 0 ? "Why won't you listen?" : "Stay. With. Me.");
        int tw = Math.max(1, mc.font.width(text));
        int alpha = (int) (Mth.clamp(a, 0f, 1f) * 255f);
        if (alpha < 8) return;
        g.pose().pushPose();
        g.pose().translate(w / 2f, h * 0.28f, 0f);
        g.pose().scale(1.7f, 1.7f, 1f);
        g.fill(-tw / 2 - 4, -3, tw / 2 + 4, 11, (alpha / 2) << 24);
        g.drawCenteredString(mc.font, text, 0, 0, (alpha << 24) | 0xC8B8BC);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------
    // Свет из проёма: вертикальная мягкая полоса позади фигуры (scale 0..1 управляет яркостью)
    // ------------------------------------------------------------------
    private static void drawGlow(GuiGraphics g, int w, int h, float cx, float k, float scale) {
        float glowW = 15f * k;
        for (int i = 0; i < 6; i++) {
            float e = (5 - i) * k * 0.9f;
            int a = (int) ((0x14 + i * 3) * scale);
            int a2 = (int) ((0x14 + i * 3 + 6) * scale);
            g.fillGradient((int) (cx - glowW / 2 - e), 0, (int) (cx + glowW / 2 + e), h,
                    (a << 24) | 0x4A5E70, (a2 << 24) | 0x1E2A34);
        }
    }

    // ------------------------------------------------------------------
    // Фигура: это игрок, каким он себя не помнит. Те же пропорции, только выше, темнее и без черт.
    // Края плывут (три варианта границы перетекают друг в друга), ноги растворяются,
    // за спиной слабое «эхо» контура. Глаза: бледные, размытые, неодинаковые.
    // frozen: в замирании всё стоит на месте, глаза всегда видны.
    // ------------------------------------------------------------------
    private static void drawFigure(GuiGraphics g, int w, int h, int stage, float t, long now,
                                   RandomSource rnd, boolean frozen) {
        float creep = 1f + 0.12f * Mth.clamp((t - STAGE_AT[stage]) / 60f, 0f, 1f); // оно ползёт и между скачками
        float k = h / 50f * STAGE_SCALE[stage] * creep; // единица длины: рост фигуры = FIG_H * k
        float feetY = h * STAGE_FEET[stage];
        float cx = w * STAGE_X[stage] + (float) Math.sin(now / 1300.0) * k * 0.35f;
        // Редкая дрожь: рывок на пару пикселей
        if (!frozen && rnd.nextInt(40) == 0) cx += rnd.nextInt(5) - 2;

        drawGlow(g, w, h, cx, k, 1f);

        // Медленное качание вокруг ног и «дыхание» по высоте
        float lean = (float) Math.sin(now / 2300.0) * 1.2f;
        float breathe = 1f + 0.012f * (float) Math.sin(now / 1900.0);

        // Веса вариантов границы: в сумме у ядра почти полная непрозрачность, а края шевелятся
        double tm = now / 1000.0;
        float[] weight = new float[3];
        for (int i = 0; i < 3; i++) weight[i] = 0.55f + 0.30f * (float) Math.sin(tm * 0.9 + i * 2.1);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        g.pose().pushPose();
        g.pose().translate(cx, feetY, 0f);
        g.pose().mulPose(Axis.ZP.rotationDegrees(lean));
        g.pose().scale(1f, breathe, 1f);
        g.pose().translate(0f, -FIG_H * k, 0f); // начало координат: макушка по центру

        // Эхо контура: слабая смещённая копия
        layer(g, SELF[0], 1.4f, -0.7f, k, 1.02f, 0.16f);
        for (int i = 0; i < 3; i++) layer(g, SELF[i], 0f, 0f, k, 1f, weight[i]);

        drawEyes(g, stage, t, now, k, frozen, rnd);

        g.pose().popPose();

        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /**
     * Глаза. Со стадии 2: приоткрыты и тусклые, к стадии 3 раскрываются полностью и смотрят прямо в игрока.
     * Моргают (правый позже левого), взгляд большую часть времени упирается в зрителя, иногда уходит в сторону.
     * Правый глаз чуть меньше, ниже и слабее: неодинаковость делает их живыми.
     */
    private static void drawEyes(GuiGraphics g, int stage, float t, long now, float k, boolean frozen, RandomSource rnd) {
        boolean rage = SleepSceneState.scoldTicks > 0 || SleepSceneState.pauseTicks > 40;
        if (stage < 2 && !rage) return; // в ярости глаза видны на любой стадии
        if (!frozen && rnd.nextInt(30) == 0) return; // иногда гаснут совсем (сбой кадра)

        boolean close = stage >= 3;
        float open = close ? 1f : 0.42f + 0.28f * Mth.clamp((t - STAGE_AT[2]) / 60f, 0f, 1f);
        float base = close ? 1f : 0.62f;

        // Взгляд: раз в ~1,3 с новая точка; в 70% случаев строго в зрителя. В замирании — всегда в зрителя.
        float gx = 0f;
        float gy = 0f;
        if (!frozen) {
            RandomSource gr = RandomSource.create(now / 1300L * 31L + 7L);
            if (gr.nextInt(10) >= 7) {
                gx = (gr.nextFloat() - 0.5f) * 0.34f;
                gy = (gr.nextFloat() - 0.5f) * 0.12f;
            }
            gx += (float) Math.sin(now / 410.0) * 0.02f;
        }

        for (int side = -1; side <= 1; side += 2) {
            boolean right = side > 0;
            float blink = frozen ? 1f : blinkFactor(now, right ? 4300 : 3700, right ? 900 : 0);
            float sy = open * blink;
            float size = EYE_SCALE * (right ? 0.94f : 1f);
            float alpha = base * (right ? 0.82f : 1f) * (frozen ? 1f : 0.90f + 0.10f * rnd.nextFloat()) * eyesMul;
            float cxU = side * EYE_DX;
            float cyU = EYE_Y + (right ? 0.35f : 0f);

            layer(g, right ? EYE_SCLERA_R : EYE_SCLERA_L, cxU, cyU, k, size, size * sy, alpha);
            // Ярость: зрачок мелко и быстро дрожит (новый сдвиг каждый кадр)
            float tx = rage ? (rnd.nextFloat() - 0.5f) * 0.55f : 0f;
            float ty = rage ? (rnd.nextFloat() - 0.5f) * 0.22f : 0f;
            layer(g, right ? EYE_IRIS_R : EYE_IRIS_L, cxU + (gx + tx) * size, cyU + (gy + ty) * size * sy, k, size, size * sy, alpha);
            layer(g, right ? EYE_LIDS_R : EYE_LIDS_L, cxU, cyU, k, size, size * sy, eyesMul);
        }
    }

    /** 1 = глаз открыт, ~0.08 = закрыт. Моргание длится 160 мс раз в period мс. */
    private static float blinkFactor(long now, int periodMs, int phaseMs) {
        long ph = (now + phaseMs) % periodMs;
        if (ph > 160) return 1f;
        return 1f - 0.92f * (float) Math.sin(Math.PI * ph / 160.0);
    }

    /**
     * Рисует текстуру со смещением (dxU, dyU) юнитов от текущего начала координат.
     * sx/sy масштабируют вокруг точки привязки, alpha — прозрачность слоя.
     */
    private static void layer(GuiGraphics g, Part p, float dxU, float dyU, float k, float sx, float sy, float alpha) {
        g.pose().pushPose();
        g.pose().translate(dxU * k, dyU * k, 0f);
        float sc = k / p.px();
        g.pose().scale(sc * sx, sc * sy, 1f);
        g.setColor(1f, 1f, 1f, Mth.clamp(alpha, 0f, 1f));
        int tw = p.w();
        int th = p.h();
        g.blit(p.tex(), -Math.round(p.ox() * p.px()), -Math.round(p.oy() * p.px()), 0, 0f, 0f, tw, th, tw, th);
        g.setColor(1f, 1f, 1f, 1f);
        g.pose().popPose();
    }

    private static void layer(GuiGraphics g, Part p, float dxU, float dyU, float k, float scaleMul, float alpha) {
        layer(g, p, dxU, dyU, k, scaleMul, scaleMul, alpha);
    }
}
