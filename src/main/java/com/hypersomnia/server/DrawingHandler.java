package com.hypersomnia.server;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.ObserverProgressData;
import com.hypersomnia.document.DocumentItem;
import com.hypersomnia.document.DrawingTuning;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Отмечает найденные рисунки Nora: раз в секунду смотрит инвентарь (подбор из любого источника). Найденное не «теряется». */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class DrawingHandler {
    private DrawingHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % DrawingTuning.SCAN_INTERVAL_TICKS != 0) return;
        ObserverProgressData data = player.getData(ModAttachments.OBSERVER);
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!(stack.getItem() instanceof DocumentItem)) continue;
            int idx = DrawingTuning.indexOf(DocumentItem.idOf(stack));
            if (idx >= 0 && !data.hasDrawing(idx)) data.addDrawing(idx);
        }
    }
}