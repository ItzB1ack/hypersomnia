package com.hypersomnia.server;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.ObserverProgressData;
import com.hypersomnia.ObserverTuning;
import com.hypersomnia.document.DocumentItem;
import com.hypersomnia.document.MemoryTuning;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Отмечает найденные воспоминания: раз в секунду смотрит инвентарь. Найденное не «теряется».
 * Записки Фрагмента (ObserverTuning.NOTE_PAGES), найденные самим игроком (половинки и note_l3 лежат и в шкафах лаборатории),
 * тоже отмечаются выданными: Фрагмент их уже не принесёт и переходит к следующей.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class MemoryHandler {
    private MemoryHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % MemoryTuning.SCAN_INTERVAL_TICKS != 0) return;
        ObserverProgressData data = player.getData(ModAttachments.OBSERVER);
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!(stack.getItem() instanceof DocumentItem)) continue;
            String id = DocumentItem.idOf(stack);
            int idx = MemoryTuning.indexOf(id);
            if (idx >= 0 && (data.getMemories() & (1 << idx)) == 0) data.addMemory(idx);
            for (int n = 0; n < ObserverTuning.NOTE_PAGES.length; n++)
                if (ObserverTuning.NOTE_PAGES[n].equals(id) && !data.hasNote(n)) data.addNote(n);
        }
    }
}