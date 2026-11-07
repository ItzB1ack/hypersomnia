package com.hypersomnia.server;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.ObserverTuning;
import com.hypersomnia.chase.ChaseHandler;
import com.hypersomnia.dream.DreamDimension;
import com.hypersomnia.finale.FinaleLogic;
import com.hypersomnia.lab.LabProtection;
import com.hypersomnia.lab.LabLift;
import com.hypersomnia.lab.LabSavedData;
import com.hypersomnia.network.LabAmbientPayload;
import com.hypersomnia.scene.SceneAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Персональные тихие эпизоды на этажах, без изменения блоков и инвентарей. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class LabAmbientHandler {
    private LabAmbientHandler() {}
    private record Visit(ResourceKey<Level> dimension, int floor, long due) {}
    private static final Map<UUID, Visit> VISITS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % ObserverTuning.LAB_AMBIENT_SCAN_TICKS != 0) return;
        UUID id = p.getUUID();
        ServerLevel level = p.serverLevel();
        if (!p.isAlive() || p.isSleeping() || p.isSpectator() || p.isCreative()
                || p.getData(ModAttachments.OBSERVER).stage() < ObserverTuning.LAB_AMBIENT_MIN_STAGE
                || FinaleLogic.completed(p) || ChaseHandler.activeFor(p)
                || p.getData(SceneAttachments.SCENE).getHold() > 0
                || !(level.dimension().equals(Level.OVERWORLD) || level.dimension().equals(DreamDimension.LEVEL))) {
            VISITS.remove(id);
            return;
        }
        LabSavedData lab = LabSavedData.get(level);
        int floor = LabProtection.stopAt(lab, p.blockPosition());
        if (!lab.built || floor < 1 || floor > 5 || LabLift.stopOf(p, lab) >= 0) { VISITS.remove(id); return; }
        long now = p.getServer().overworld().getGameTime();
        Visit visit = VISITS.get(id);
        if (visit == null || !visit.dimension().equals(level.dimension()) || visit.floor() != floor) {
            int delay = ObserverTuning.LAB_AMBIENT_DWELL_MIN
                    + p.getRandom().nextInt(ObserverTuning.LAB_AMBIENT_DWELL_RANGE + 1);
            VISITS.put(id, new Visit(level.dimension(), floor, now + delay));
            return;
        }
        if (now < visit.due() || now < COOLDOWNS.getOrDefault(id, 0L)) return;
        var view = p.getViewVector(1f);
        BlockPos at = BlockPos.containing(p.getX() - view.x * ObserverTuning.LAB_AMBIENT_OFFSET,
                p.getY(), p.getZ() - view.z * ObserverTuning.LAB_AMBIENT_OFFSET);
        if (!level.hasChunkAt(at)) return;
        PacketDistributor.sendToPlayer(p, new LabAmbientPayload(at, floor - 1));
        COOLDOWNS.put(id, now + ObserverTuning.LAB_AMBIENT_COOLDOWN);
        VISITS.remove(id);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID(); VISITS.remove(id); COOLDOWNS.remove(id);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { VISITS.clear(); COOLDOWNS.clear(); }
}
