package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModItems;
import com.hypersomnia.diary.DiaryTuning;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Клиентская анимация дневника в руке по обычному ПКМ (без удержания).
 * Раскрытие: пока идёт OPEN_MS, модель книги листает кадры hypersomnia:using;
 * после этого открывается GUI. Пока GUI открыт, книга в руке остаётся раскрытой.
 * После выхода из GUI она закрывается за CLOSE_MS. Если в момент финала открыт
 * другой экран или дневника уже нет в руках, GUI не открывается.
 *
 * Время идёт по System.nanoTime, а не по тикам, поэтому ход не ступенчатый
 * при 20 тиках; смягчение краёв даёт ease().
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class DiaryOpenAnim {
    private DiaryOpenAnim() {}

    private enum Phase { IDLE, OPENING, OPEN, CLOSING }

    private static final long OPEN_NS = DiaryTuning.OPEN_MS * 1_000_000L;
    private static final long CLOSE_NS = DiaryTuning.CLOSE_MS * 1_000_000L;

    private static Phase phase = Phase.IDLE;
    private static long phaseStart;
    private static InteractionHand hand = InteractionHand.MAIN_HAND;

    public static void start(InteractionHand usedHand) {
        // Повторный ПКМ во время раскрытия или с открытым GUI анимацию не перезапускает.
        if (phase == Phase.OPENING || phase == Phase.OPEN) return;
        long now = System.nanoTime();
        // Если книга ещё закрывается, раскрытие продолжается с её текущего положения, без скачка.
        float from = phase == Phase.CLOSING ? rawProgress() : 0.0F;
        hand = usedHand;
        phase = Phase.OPENING;
        phaseStart = now - (long) (from * (float) OPEN_NS);
    }

    /** Рука, в которой дневник: её рисует DiaryViewModel. */
    public static InteractionHand hand() {
        return hand;
    }

    /** Идёт ли сейчас какая-либо фаза (книга не в покое). */
    public static boolean active() {
        return phase != Phase.IDLE;
    }

    /** 0..1 — степень раскрытия книги с мягким разгоном и торможением; 0 в покое. */
    public static float progress() {
        return ease(rawProgress());
    }

    /** 0..1 — раскрытие по линейному времени (для сдвигов, которые сами смягчаются). */
    private static float rawProgress() {
        long elapsed = System.nanoTime() - phaseStart;
        return switch (phase) {
            case IDLE -> 0.0F;
            case OPENING -> Mth.clamp((float) elapsed / (float) OPEN_NS, 0.0F, 1.0F);
            case OPEN -> 1.0F;
            case CLOSING -> 1.0F - Mth.clamp((float) elapsed / (float) CLOSE_NS, 0.0F, 1.0F);
        };
    }

    /** Ease-in-out cubic: нулевая скорость на краях. */
    private static float ease(float t) {
        if (t <= 0.0F) return 0.0F;
        if (t >= 1.0F) return 1.0F;
        return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3.0) / 2.0F;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (phase == Phase.IDLE) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();

        switch (phase) {
            case OPENING -> {
                if (now - phaseStart < OPEN_NS) return;
                if (mc.player == null || !holdsDiary(mc.player)) {
                    phase = Phase.IDLE;
                } else if (mc.screen != null) {
                    // Открыт другой экран: книгу закрываем, GUI дневника не открываем.
                    phase = Phase.CLOSING;
                    phaseStart = now;
                } else {
                    DiaryScreen.open();
                    phase = Phase.OPEN;
                    phaseStart = now;
                }
            }
            case OPEN -> {
                if (mc.player == null || !holdsDiary(mc.player)) {
                    phase = Phase.IDLE;
                } else if (!(mc.screen instanceof DiaryScreen)) {
                    phase = Phase.CLOSING;
                    phaseStart = now;
                }
            }
            case CLOSING -> {
                if (mc.player == null || !holdsDiary(mc.player) || now - phaseStart >= CLOSE_NS) {
                    phase = Phase.IDLE;
                }
            }
            default -> {
            }
        }
    }

    private static boolean holdsDiary(LocalPlayer player) {
        return isDiary(player.getItemInHand(InteractionHand.MAIN_HAND))
                || isDiary(player.getItemInHand(InteractionHand.OFF_HAND));
    }

    private static boolean isDiary(ItemStack stack) {
        return stack.is(ModItems.DIARY.get());
    }
}
