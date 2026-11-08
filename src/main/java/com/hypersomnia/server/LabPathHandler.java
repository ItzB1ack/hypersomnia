package com.hypersomnia.server;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.ObserverProgressData;
import com.hypersomnia.ObserverTuning;
import com.hypersomnia.finale.FinaleLogic;
import com.hypersomnia.lab.LabProtection;
import com.hypersomnia.lab.LabSavedData;
import com.hypersomnia.network.LabPathAck;
import com.hypersomnia.network.LabPathPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Личная временная преграда на подходе к лаборатории. Блоки и чужие игроки не затрагиваются. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class LabPathHandler {
    private LabPathHandler() {}
    private static final class Gate {
        final int token, dx, dz;
        final BlockPos base;
        final long started;
        Vec3 previous;
        boolean acknowledged;
        Gate(int token, BlockPos base, int dx, int dz, long started, Vec3 previous) {
            this.token = token; this.base = base; this.dx = dx; this.dz = dz; this.started = started; this.previous = previous;
        }
        double side(Vec3 p) { return (p.x - base.getX() - .5) * dx + (p.z - base.getZ() - .5) * dz; }
        double lateral(Vec3 p) { return (p.x - base.getX() - .5) * -dz + (p.z - base.getZ() - .5) * dx; }
    }
    private static final Map<UUID, Gate> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> RETRY = new HashMap<>();
    /** Идёт ли у игрока личная преграда: вторую сцену стадии 5 одновременно не запускаем. */
    public static boolean isActive(ServerPlayer p) { return ACTIVE.containsKey(p.getUUID()); }
    public static void ack(LabPathAck payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer p)) return;
            Gate g = ACTIVE.get(p.getUUID());
            if (g == null || g.token != payload.token() || !valid(p) || g.side(p.position()) >= 0
                    || p.serverLevel().getGameTime() - g.started > ObserverTuning.LAB_PATH_DURATION) return;
            g.acknowledged = true;
        });
    }
    private static boolean valid(ServerPlayer p) {
        return p.serverLevel().dimension().equals(Level.OVERWORLD) && p.isAlive() && !p.isSleeping() && !p.isSpectator()
                && !p.isCreative() && !p.isPassenger() && !p.getAbilities().flying && !p.isFallFlying()
                && !p.isInWater() && !p.isInLava() && !FinaleLogic.completed(p);
    }
    /** Те же клетки используются для личной картинки и серверной преграды. */
    private static boolean safeGateCells(ServerLevel level, BlockPos base, int dx, int dz) {
        for (int i = -1; i <= 1; i++) for (int y = 0; y < 3; y++) {
            BlockPos q = base.offset(-dz * i, y, dx * i);
            if (!level.hasChunkAt(q) || !level.hasChunkAt(q.below()) || !level.getBlockState(q).isAir()
                    || LabProtection.inside(level, q)) return false;
            if (y == 0 && !level.getBlockState(q.below()).isFaceSturdy(level, q.below(), Direction.UP)) return false;
        }
        return true;
    }
    private static void finish(ServerPlayer p, Gate g, boolean seen) {
        ACTIVE.remove(p.getUUID());
        RETRY.put(p.getUUID(), p.serverLevel().getGameTime() + ObserverTuning.LAB_PATH_RETRY_TICKS);
        if (seen && g.acknowledged) p.getData(ModAttachments.OBSERVER).markLabPathSeen();
        PacketDistributor.sendToPlayer(p, new LabPathPayload(g.token, 0, g.base, g.dx, g.dz));
    }
    public static void resetFor(ServerPlayer p) {
        Gate gate = ACTIVE.get(p.getUUID());
        if (gate != null) finish(p, gate, false);
        RETRY.remove(p.getUUID());
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Gate gate = ACTIVE.get(p.getUUID());
        if (gate != null) { update(p, gate); return; }
        ObserverProgressData progress = p.getData(ModAttachments.OBSERVER);
        if (p.tickCount % ObserverTuning.LAB_PATH_SCAN_TICKS != 0 || progress.stage() < 5
                || !progress.noraRoomSeen() || progress.labPathSeen() || !valid(p) || !p.onGround()) return;
        ServerLevel level = p.serverLevel();
        if (level.getGameTime() < RETRY.getOrDefault(p.getUUID(), 0L)) return;
        LabSavedData lab = LabSavedData.get(level);
        if (!lab.built || lab.buildFailed || !lab.isBuilt(0) || LabProtection.inside(level, p.blockPosition())) return;
        double x = lab.originX + .5 - p.getX(), z = lab.originZ + .5 - p.getZ();
        double distance = Math.hypot(x, z);
        if (distance < ObserverTuning.LAB_PATH_NEAR || distance > ObserverTuning.LAB_PATH_FAR) return;
        int dx = Math.abs(x) >= Math.abs(z) ? (x > 0 ? 1 : -1) : 0;
        int dz = dx == 0 ? (z > 0 ? 1 : -1) : 0;
        BlockPos base = p.blockPosition().offset(dx * ObserverTuning.LAB_PATH_AHEAD, 0, dz * ObserverTuning.LAB_PATH_AHEAD);
        if (!safeGateCells(level, base, dx, dz)) return;
        Gate g = new Gate(p.getRandom().nextInt(), base, dx, dz, level.getGameTime(), p.position());
        ACTIVE.put(p.getUUID(), g);
        PacketDistributor.sendToPlayer(p, new LabPathPayload(g.token, ObserverTuning.LAB_PATH_DURATION, base, dx, dz));
    }
    private static void update(ServerPlayer p, Gate g) {
        ServerLevel level = p.serverLevel();
        Vec3 now = p.position();
        if (!valid(p) || !level.hasChunkAt(g.base) || now.distanceToSqr(g.previous) > 16
                || level.getGameTime() - g.started >= ObserverTuning.LAB_PATH_DURATION
                || now.distanceToSqr(Vec3.atCenterOf(g.base)) > ObserverTuning.LAB_PATH_ESCAPE_DISTANCE * ObserverTuning.LAB_PATH_ESCAPE_DISTANCE
                || Math.abs(now.y - g.base.getY()) > 3) { finish(p, g, true); return; }
        // Если за время эпизода кто-то построил здесь настоящий блок, преграда немедленно снимается.
        if (!safeGateCells(level, g.base, g.dx, g.dz)) { finish(p, g, false); return; }
        if (g.acknowledged && p.onGround() && g.side(g.previous) < -.35 && g.side(now) >= -.35
                && Math.abs(g.lateral(now)) <= ObserverTuning.LAB_PATH_WIDTH) {
            Vec3 back = g.previous;
            if (level.noCollision(p, p.getBoundingBox().move(back.subtract(now)))) {
                p.teleportTo(level, back.x, back.y, back.z, p.getYRot(), p.getXRot());
                p.setDeltaMovement(0, p.getDeltaMovement().y, 0);
                p.resetFallDistance();
                now = back;
            } else { finish(p, g, false); return; }
        }
        g.previous = now;
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { ACTIVE.remove(e.getEntity().getUUID()); RETRY.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) { ACTIVE.clear(); RETRY.clear(); }
}
