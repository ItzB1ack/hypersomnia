package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.chase.ChaseTuning;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Глаза по углам экрана в погоне: скопления в темноте виньетки, разного размера (у самого угла крупнее).
 * Открываются по одному с начала погони: пока он стоит на следе — несколько, дальше чем сильнее паника
 * (чем он ближе), тем больше; отстал — лишние закрываются. Моргают, смотрят на игрока (в центр кадра),
 * радужки вздрагивают с ударами сердца. Погоня кончилась — закрываются по одному.
 * Слой chase_eyes (ChaseOverlay): над виньеткой, под чёрным кадром поимки. Числа — ChaseTuning.EYES_*.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class ChaseEyes {
    private ChaseEyes() {}

    private static final RandomSource RANDOM = RandomSource.create();

    private static final class Eye {
        /** Угол 0..3 (бит 0 — правый, бит 1 — нижний), сдвиг от угла и ширина — в долях высоты экрана. */
        int corner;
        float dx;
        float dy;
        float size;
        boolean mirrored;
        float tilt;
        float alpha;
        /** 0 закрыт, 1 открывается или открыт, 2 закрывается. */
        int state;
        float open;
        float openO;
        int blinkIn;
        int blink;
        int lookIn;
        float gx;
        float gy;
        float tgx;
        float tgy;
    }

    private static final List<Eye> EYES = new ArrayList<>();
    private static boolean active;
    private static int openIn;
    private static int closeIn;

    /** Погоня началась: раскладка новая (если прошлые глаза уже закрылись), первый откроется через FIRST_DELAY. */
    public static void begin() {
        if (EYES.isEmpty()) layout();
        active = true;
        openIn = ChaseTuning.EYES_FIRST_DELAY;
    }

    /** Погоня кончилась: закрываются по одному. */
    public static void end() {
        active = false;
        closeIn = 1;
    }

    public static void reset() {
        EYES.clear();
        active = false;
    }

    private static void layout() {
        EYES.clear();
        for (int c = 0; c < 4; c++) {
            int n = Mth.randomBetweenInclusive(RANDOM, ChaseTuning.EYES_PER_CORNER_MIN, ChaseTuning.EYES_PER_CORNER_MAX);
            List<Eye> placed = new ArrayList<>();
            for (int tries = 0; placed.size() < n && tries < 300; tries++) {
                float r = (float) Math.pow(RANDOM.nextFloat(), 0.85);
                float ang = RANDOM.nextFloat() * Mth.HALF_PI;
                Eye e = new Eye();
                e.corner = c;
                e.dx = ChaseTuning.EYES_EDGE + r * ChaseTuning.EYES_SPREAD_X * Mth.cos(ang);
                e.dy = ChaseTuning.EYES_EDGE + r * ChaseTuning.EYES_SPREAD_Y * Mth.sin(ang);
                e.size = Mth.lerp(r, ChaseTuning.EYES_SIZE_MAX, ChaseTuning.EYES_SIZE_MIN) * (0.8f + 0.4f * RANDOM.nextFloat());
                boolean free = true;
                for (Eye p : placed) {
                    float sx = (e.dx - p.dx) / ((e.size + p.size) * 0.56f);
                    float sy = (e.dy - p.dy) / ((e.size + p.size) * 0.42f);
                    if (sx * sx + sy * sy < 1f) {
                        free = false;
                        break;
                    }
                }
                if (!free) continue;
                e.mirrored = RANDOM.nextBoolean();
                e.tilt = (RANDOM.nextFloat() * 2f - 1f) * ChaseTuning.EYES_TILT;
                e.alpha = 0.78f + 0.22f * (1f - r);
                e.blinkIn = Mth.randomBetweenInclusive(RANDOM, ChaseTuning.EYES_BLINK_MIN, ChaseTuning.EYES_BLINK_MAX);
                placed.add(e);
            }
            EYES.addAll(placed);
        }
        // Порядок открытия: вперемешку по углам
        for (int i = EYES.size() - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            Eye t = EYES.get(i);
            EYES.set(i, EYES.get(j));
            EYES.set(j, t);
        }
    }

    /** Сколько глаз открыто при панике p: на следе EYES_WARN, дальше растёт до всех. */
    private static int wanted(float p) {
        float k = Mth.clamp((p - ChaseTuning.PANIC_WARN) / (1f - ChaseTuning.PANIC_WARN), 0f, 1f);
        return Math.min(EYES.size(), ChaseTuning.EYES_WARN + Math.round(k * (EYES.size() - ChaseTuning.EYES_WARN)));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (EYES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }
        if (mc.isPaused()) return;
        int opened = 0;
        for (Eye e : EYES) if (e.state == 1) opened++;
        if (active) {
            int want = wanted(ChaseClient.panicNow());
            if (opened < want && --openIn <= 0) {
                for (Eye e : EYES) {
                    if (e.state == 0) {
                        e.state = 1;
                        break;
                    }
                }
                openIn = Mth.randomBetweenInclusive(RANDOM, ChaseTuning.EYES_OPEN_EVERY_MIN, ChaseTuning.EYES_OPEN_EVERY_MAX);
            } else if (opened > want + ChaseTuning.EYES_HYSTERESIS && --closeIn <= 0) {
                for (int i = EYES.size() - 1; i >= 0; i--) {
                    if (EYES.get(i).state == 1) {
                        EYES.get(i).state = 2;
                        break;
                    }
                }
                closeIn = ChaseTuning.EYES_CLOSE_EVERY;
            }
        } else if (opened > 0 && --closeIn <= 0) {
            int k = RANDOM.nextInt(opened);
            for (Eye e : EYES) {
                if (e.state == 1 && k-- == 0) {
                    e.state = 2;
                    break;
                }
            }
            closeIn = Mth.randomBetweenInclusive(RANDOM, ChaseTuning.EYES_END_EVERY_MIN, ChaseTuning.EYES_END_EVERY_MAX);
        }
        boolean any = false;
        for (Eye e : EYES) {
            e.openO = e.open;
            if (e.state == 1) {
                e.open = Math.min(1f, e.open + 1f / ChaseTuning.EYES_OPEN_TICKS);
            } else if (e.state == 2) {
                e.open = Math.max(0f, e.open - 1f / ChaseTuning.EYES_CLOSE_TICKS);
                if (e.open <= 0f) e.state = 0;
            }
            if (e.state != 0 || e.open > 0f) any = true;
            if (e.blink > 0) {
                e.blink--;
            } else if (e.open >= 1f && --e.blinkIn <= 0) {
                e.blink = ChaseTuning.EYES_BLINK_TICKS;
                e.blinkIn = Mth.randomBetweenInclusive(RANDOM, ChaseTuning.EYES_BLINK_MIN, ChaseTuning.EYES_BLINK_MAX);
            }
            if (--e.lookIn <= 0) {
                e.lookIn = Mth.randomBetweenInclusive(RANDOM, ChaseTuning.EYES_LOOK_MIN, ChaseTuning.EYES_LOOK_MAX);
                e.tgx = (RANDOM.nextFloat() - 0.5f) * 0.5f;
                e.tgy = (RANDOM.nextFloat() - 0.5f) * 0.5f;
            }
            e.gx += (e.tgx - e.gx) * 0.35f;
            e.gy += (e.tgy - e.gy) * 0.35f;
        }
        if (!active && !any) EYES.clear();
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        if (EYES.isEmpty()) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth();
        int h = g.guiHeight();
        float iris = 1f + ChaseTuning.EYES_PULSE * ChaseClient.pulse(partial);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (Eye e : EYES) {
            float open = Mth.lerp(partial, e.openO, e.open);
            if (open <= 0.03f) continue;
            open = open * open * (3f - 2f * open); // веки идут мягко
            if (e.blink > 0) {
                float b = (ChaseTuning.EYES_BLINK_TICKS - e.blink + partial) / ChaseTuning.EYES_BLINK_TICKS;
                open *= 1f - 0.92f * Mth.sin(Mth.PI * Mth.clamp(b, 0f, 1f));
            }
            float x = (e.corner & 1) == 0 ? e.dx * h : w - e.dx * h;
            float y = (e.corner & 2) == 0 ? e.dy * h : h - e.dy * h;
            float vx = w / 2f - x;
            float vy = h / 2f - y;
            float len = Mth.sqrt(vx * vx + vy * vy) + 1e-3f;
            EyeSprite.draw(g, x, y, e.size * h, open, 0.8f * vx / len + e.gx, 0.8f * vy / len + e.gy,
                    e.alpha, e.mirrored, e.tilt, iris);
        }
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
