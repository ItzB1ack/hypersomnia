package com.hypersomnia.server;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.diary.DiaryAttachments;
import com.hypersomnia.diary.DiaryData;
import com.hypersomnia.diary.DiaryTuning;
import com.hypersomnia.document.DocumentDefs;
import com.hypersomnia.document.DocumentItem;
import com.hypersomnia.document.SignAlphabet;
import com.hypersomnia.network.DiaryActionPayload;
import com.hypersomnia.network.DiaryKeyPayload;
import com.hypersomnia.network.DiarySyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Листы в дневнике: проверка действий клиента и синхронизация. Класть можно только то, что лежит в инвентаре. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class DiaryHandler {
    private DiaryHandler() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) send(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) send(p);
    }

    public static void handleAction(DiaryActionPayload pl, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            DiaryData data = player.getData(DiaryAttachments.DIARY);
            if (pl.remove()) {
                data.remove(pl.id());
            } else {
                // отказ тоже синхронизируем: клиент уже мог применить правку локально
                if (DocumentDefs.get(pl.id()) == null && !DiaryTuning.PHOTOS.containsKey(pl.id())) { send(player); return; }
                if (!data.has(pl.id()) && !owns(player, data, pl.id())) { send(player); return; }
                int page = Math.max(1, Math.min(DiaryTuning.PAGES, pl.page()));
                if (page == DiaryTuning.KEY_PAGE && !DiaryTuning.PHOTOS.containsKey(pl.id())) { send(player); return; }
                int x = Math.max(0, Math.min(DiaryTuning.PAPER_W - DiaryTuning.THUMB_MIN, pl.x()));
                int y = Math.max(0, Math.min(DiaryTuning.PAPER_H - DiaryTuning.THUMB_MIN, pl.y()));
                data.place(pl.id(), page, x, y);
            }
            send(player);
        });
    }

    public static void handleKey(DiaryKeyPayload pl, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (pl.letter().length() != 1) return;
            char ch = Character.toUpperCase(pl.letter().charAt(0));
            if (!SignAlphabet.isLetter(ch)) return;
            int sym = pl.sym() < 0 ? -1 : Math.min(pl.sym(), SignAlphabet.count() - 1);
            player.getData(DiaryAttachments.DIARY).setKey(ch, sym);
            send(player);
        });
    }

    /** Печать можно положить, пока цела её страница-источник: лист в инвентаре или уже вклеен в дневник. */
    private static boolean owns(ServerPlayer player, DiaryData data, String id) {
        DiaryTuning.Photo ph = DiaryTuning.PHOTOS.get(id);
        if (ph == null) return carries(player, id);
        return carries(player, ph.source()) || data.has(ph.source());
    }

    private static boolean carries(ServerPlayer player, String id) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (stack.getItem() instanceof DocumentItem && DocumentItem.idOf(stack).equals(id)) return true;
        }
        return false;
    }

    public static void send(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new DiarySyncPayload(
                player.getData(DiaryAttachments.DIARY).toStrings(),
                player.getData(DiaryAttachments.DIARY).keyStrings()));
    }
}