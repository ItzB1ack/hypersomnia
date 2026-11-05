package com.hypersomnia.client;

import com.hypersomnia.FatigueTuning;
import com.hypersomnia.Hypersomnia;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class FatigueClientEvents {
    private FatigueClientEvents() {}

    private static final RandomSource RANDOM = RandomSource.create();

    // Для «первый раз не получилось, второй получилось»
    private static BlockPos lastFailPos;
    private static long lastFailTime;

    // Меняется при каждом открытии инвентаря: определяет, какие предметы «забыты»
    private static long blurSeed = RANDOM.nextLong();

    // ------------------------------------------------------------------
    // Тик: потемнения, бег, цифровые клавиши
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused()) return;

        ClientFatigueState.tick();
        float r = ClientFatigueState.ratio();

        // Бег запрещён
        if (r >= FatigueTuning.NO_SPRINT_START || ClientFatigueState.collapsing) {
            mc.options.keySprint.setDown(false);
            mc.player.setSprinting(false);
        }

        // Цифровые клавиши иногда «не нажимаются»
        if (r >= FatigueTuning.CLUMSY_START && mc.screen == null) {
            float p = FatigueTuning.progress(r, FatigueTuning.CLUMSY_START);
            float failChance = Mth.lerp(p, 0.25f, 0.70f);
            for (KeyMapping key : mc.options.keyHotbarSlots) {
                if (RANDOM.nextFloat() < failChance) {
                    while (key.consumeClick()) {
                        // съедаем нажатие, слот не переключится
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Покачивание камеры: «клонит в сон»
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSleeping()) return;

        float partial = (float) event.getPartialTick();
        event.setPitch(event.getPitch() + ClientFatigueState.swayPitch(partial));
        event.setYaw(event.getYaw() + ClientFatigueState.swayYaw(partial));
        event.setRoll(event.getRoll() + ClientFatigueState.swayRoll(partial));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientFatigueState.reset();
    }

    // ------------------------------------------------------------------
    // Колесо мыши отключено при сильной усталости
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return; // в меню прокрутка должна работать
        if (ClientFatigueState.ratio() >= FatigueTuning.NO_SCROLL_START || ClientFatigueState.collapsing) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------
    // Мелкая моторика: блоки не открываются с первого раза
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (!level.isClientSide()) return;

        float r = ClientFatigueState.ratio();
        if (r < FatigueTuning.CLUMSY_START) return;

        BlockPos pos = event.getPos();
        if (!isFiddly(level.getBlockState(pos), level, pos)) return;

        long now = level.getGameTime();

        // Второй рукой в тот же тик повторяем исход главной руки
        if (event.getHand() == InteractionHand.OFF_HAND) {
            if (pos.equals(lastFailPos) && now == lastFailTime) cancel(event);
            return;
        }

        // Повторная попытка по тому же блоку за ~2 секунды всегда удаётся
        if (pos.equals(lastFailPos) && now - lastFailTime <= 40) {
            lastFailPos = null;
            return;
        }

        float p = FatigueTuning.progress(r, FatigueTuning.CLUMSY_START);
        if (RANDOM.nextFloat() < Mth.lerp(p, 0.40f, 0.90f)) {
            lastFailPos = pos.immutable();
            lastFailTime = now;
            cancel(event);
        }
    }

    private static void cancel(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    private static boolean isFiddly(BlockState state, Level level, BlockPos pos) {
        Block b = state.getBlock();
        return state.getMenuProvider(level, pos) != null
                || b instanceof DoorBlock
                || b instanceof TrapDoorBlock
                || b instanceof FenceGateBlock
                || b instanceof ButtonBlock
                || b instanceof LeverBlock;
    }

    // ------------------------------------------------------------------
    // «Забытые» предметы: туман на слотах + нечитаемое название
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        boolean opensContainer = event.getNewScreen() instanceof AbstractContainerScreen<?>;
        boolean wasContainer = event.getCurrentScreen() instanceof AbstractContainerScreen<?>;
        if (opensContainer && !wasContainer) {
            blurSeed = RANDOM.nextLong(); // каждое открытие -> другой набор
        }
    }

    private static boolean isForgotten(int slotIndex) {
        float r = ClientFatigueState.ratio();
        if (r < FatigueTuning.BLUR_START) return false;
        float chance = Mth.lerp(FatigueTuning.progress(r, FatigueTuning.BLUR_START), 0.08f, 0.50f);
        return hash01(blurSeed, slotIndex) < chance;
    }

    /** Детерминированное «случайное» число 0..1 для пары (seed, salt). */
    private static float hash01(long seed, long salt) {
        long h = seed ^ (salt * 0x9E3779B97F4A7C15L);
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        return (h >>> 40) / (float) (1L << 24);
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (ClientFatigueState.ratio() < FatigueTuning.BLUR_START) return;

        GuiGraphics g = event.getGuiGraphics();
        long time = System.currentTimeMillis();

        for (Slot slot : screen.getMenu().slots) {
            if (!slot.hasItem() || !isForgotten(slot.index)) continue;
            drawFog(g, screen.getGuiLeft() + slot.x, screen.getGuiTop() + slot.y, slot.index, time);
        }
    }

    /**
     * Имитация размытия: четыре квадранта 8x8 разной прозрачности, слабо «дышащие» во времени.
     * z=300: выше предметов (150-232), но ниже подсказок (400).
     */
    private static void drawFog(GuiGraphics g, int x, int y, int salt, long time) {
        for (int i = 0; i < 4; i++) {
            int qx = x + (i % 2) * 8;
            int qy = y + (i / 2) * 8;
            float base = 120f + hash01(blurSeed + 17, salt * 4L + i) * 80f;
            float pulse = Mth.sin(time * 0.002f + salt + i) * 18f;
            int alpha = Mth.clamp((int) (base + pulse), 60, 235);
            g.fill(qx, qy, qx + 8, qy + 8, 300, (alpha << 24) | 0x8B8B8B);
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;

        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || slot.getItem() != event.getItemStack()) return;
        if (!isForgotten(slot.index)) return;

        List<Component> tooltip = event.getToolTip();
        if (tooltip.isEmpty()) return;

        int len = Mth.clamp(tooltip.get(0).getString().length(), 4, 20);
        tooltip.set(0, Component.literal("?".repeat(len))
                .withStyle(ChatFormatting.OBFUSCATED, ChatFormatting.GRAY));
    }
}
