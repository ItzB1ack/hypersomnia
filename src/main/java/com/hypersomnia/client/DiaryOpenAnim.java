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
 * Клиентская анимация раскрытия дневника по обычному ПКМ (без удержания).
 * Пока идёт OPEN_MS, модель книги листает кадры hypersomnia:using; после
 * этого открывается GUI. Если в момент финала открыт другой экран или
 * дневника уже нет в руках, GUI не открывается.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class DiaryOpenAnim {
    private DiaryOpenAnim() {}

    private static final long OPEN_NS = DiaryTuning.OPEN_MS * 1_000_000L;
    private static long startedAt = -1L;

    public static void start() {
        startedAt = System.nanoTime();
    }

    /** 0..1 — прогресс раскрытия; 0, если анимация не запущена. */
    public static float progress() {
        if (startedAt < 0L) return 0.0F;
        float t = (float) (System.nanoTime() - startedAt) / (float) OPEN_NS;
        return Mth.clamp(t, 0.0F, 1.0F);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (startedAt < 0L) return;
        if (System.nanoTime() - startedAt < OPEN_NS) return;

        startedAt = -1L;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;
        if (!holdsDiary(mc.player)) return;
        DiaryScreen.open();
    }

    private static boolean holdsDiary(LocalPlayer player) {
        return isDiary(player.getItemInHand(InteractionHand.MAIN_HAND))
                || isDiary(player.getItemInHand(InteractionHand.OFF_HAND));
    }

    private static boolean isDiary(ItemStack stack) {
        return stack.is(ModItems.DIARY.get());
    }
}
