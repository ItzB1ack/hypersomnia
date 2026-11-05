package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.lab.MonitorBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Финал на клиенте: тик записи, выход из детской, ПКМ по кровати, двери и монитору без предсказания клиента. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class FinaleClient {
    private FinaleClient() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            FinaleClientState.reset();
            return;
        }
        if (FinaleClientState.inside && !DreamClientState.isDream(mc)) FinaleClientState.reset();
        if (!mc.isPaused()) FinaleClientState.tick();
    }

    /**
     * В детской кровать, дверь и монитор — выбор, его решает сервер (LabHandler -> FinaleHandler): клиент не открывает
     * дверь сам и не открывает документ из руки (иначе экран страницы вместо записи на мониторе).
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() || !FinaleClientState.inside) return;
        Block b = event.getLevel().getBlockState(event.getPos()).getBlock();
        if (b instanceof BedBlock || b instanceof DoorBlock || b instanceof MonitorBlock) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        FinaleClientState.reset();
    }
}
