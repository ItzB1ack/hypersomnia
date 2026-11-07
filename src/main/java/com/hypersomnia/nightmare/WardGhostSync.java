package com.hypersomnia.nightmare;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.dream.DreamAttachments;
import com.hypersomnia.dream.DreamData;
import com.hypersomnia.dream.DreamDimension;
import com.hypersomnia.network.WardGhostPayload;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Персональное состояние: на сервере и в мире осколочные сущности не создаются. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class WardGhostSync {
    private WardGhostSync() {}
    private static final Set<UUID> ACTIVE = new HashSet<>();
    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        DreamData data = player.getData(DreamAttachments.DREAM);
        boolean active = player.level().dimension().equals(DreamDimension.LEVEL)
                && data.isInDream() && NightmareDefs.WARD_CORRIDOR.id().equals(data.getKind());
        UUID id = player.getUUID();
        if (active) {
            boolean started = ACTIVE.add(id);
            if (started || player.tickCount % NightmareTuning.WARD_GHOST_SYNC_TICKS == 0)
                PacketDistributor.sendToPlayer(player, new WardGhostPayload(true));
        } else if (ACTIVE.remove(id)) {
            PacketDistributor.sendToPlayer(player, new WardGhostPayload(false));
        }
    }
    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { ACTIVE.remove(event.getEntity().getUUID()); }
}
