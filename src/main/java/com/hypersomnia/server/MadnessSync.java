package com.hypersomnia.server;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.network.MadnessSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Отправляет клиенту счётчик безумия при входе и при каждом изменении. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class MadnessSync {
    private MadnessSync() {}

    private static final Map<UUID, Integer> LAST_SENT = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        int madness = player.getData(ModAttachments.FATIGUE).getMadness();
        Integer last = LAST_SENT.get(player.getUUID());
        if (last == null || last != madness) {
            PacketDistributor.sendToPlayer(player, new MadnessSyncPayload(madness));
            LAST_SENT.put(player.getUUID(), madness);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }
}
