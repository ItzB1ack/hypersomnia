package com.hypersomnia.server;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.ObserverProgressData;
import com.hypersomnia.ObserverTuning;
import com.hypersomnia.chase.ChaseTuning;
import com.hypersomnia.finale.FinaleLogic;
import com.hypersomnia.network.NoraRoomAck;
import com.hypersomnia.network.NoraRoomPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.BedBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Персональная сцена у известной кровати: только визуальные объекты, блоки мира не меняются. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class NoraRoomHandler {
    private NoraRoomHandler() {}
    private record Session(int token, BlockPos bed, BlockPos a, BlockPos b, BlockPos c, int layout, long started) {}
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> RETRY = new HashMap<>();

    public static void ack(NoraRoomAck payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer p)) return;
            Session s = ACTIVE.get(p.getUUID());
            if (s == null || s.token() != payload.token() || !valid(p, s.bed())
                    || p.serverLevel().getGameTime() - s.started() < ObserverTuning.NORA_ROOM_ACK_TICKS
                    || !lookingAtRoom(p, s)) return;
            p.getData(ModAttachments.OBSERVER).markNoraRoomSeen();
        });
    }
    private static boolean lookingAtRoom(ServerPlayer p, Session session) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 view = p.getViewVector(1f);
        BlockPos[] positions = {session.a(), session.b(), session.c()};
        int[] bits = {ObserverTuning.NORA_ROOM_LAYOUT_BED, ObserverTuning.NORA_ROOM_LAYOUT_CARPET, ObserverTuning.NORA_ROOM_LAYOUT_TOY};
        for (int i = 0; i < positions.length; i++) {
            if ((session.layout() & bits[i]) == 0) continue;
            BlockPos pos = positions[i];
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()) continue;
            if (bits[i] == ObserverTuning.NORA_ROOM_LAYOUT_BED
                    && (!level.hasChunkAt(pos.north()) || !level.getBlockState(pos.north()).isAir())) continue;
            Vec3 to = Vec3.atCenterOf(pos).subtract(eye);
            if (to.lengthSqr() > 0.01 && view.dot(to.normalize()) >= ObserverTuning.NORA_ROOM_VIEW_DOT
                    && level.clip(new ClipContext(eye, Vec3.atCenterOf(pos), ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }
    private static boolean valid(ServerPlayer p, BlockPos bed) {
        ServerLevel l = p.serverLevel();
        return l.dimension().equals(Level.OVERWORLD) && p.isAlive() && !p.isSleeping() && !p.isSpectator()
                && !p.isCreative() && !p.isPassenger() && !p.getAbilities().flying
                && !p.isInWater() && !p.isInLava() && !FinaleLogic.completed(p) && l.hasChunkAt(bed)
                && l.getBlockState(bed).getBlock() instanceof BedBlock
                && p.distanceToSqr(bed.getX() + .5, bed.getY() + .5, bed.getZ() + .5)
                    <= ObserverTuning.NORA_ROOM_TRIGGER_RADIUS * ObserverTuning.NORA_ROOM_TRIGGER_RADIUS;
    }
    private static boolean canPlaceFullBed(ServerLevel level, BlockPos pos) {
        BlockPos foot = pos.north();
        return level.hasChunkAt(foot) && level.hasChunkAt(foot.above()) && level.hasChunkAt(foot.below())
                && level.getBlockState(foot).isAir() && level.getBlockState(foot.above()).isAir()
                && level.getBlockState(foot.below()).isFaceSturdy(level, foot.below(), Direction.UP)
                && !level.canSeeSky(foot.above());
    }
    private static void finish(ServerPlayer p, Session s) {
        ACTIVE.remove(p.getUUID());
        RETRY.put(p.getUUID(), p.serverLevel().getGameTime() + ObserverTuning.NORA_ROOM_RETRY_TICKS);
        PacketDistributor.sendToPlayer(p, new NoraRoomPayload(s.token(), 0, s.bed(), s.a(), s.b(), s.c(), s.layout()));
    }
    public static void resetFor(ServerPlayer p) {
        Session session = ACTIVE.get(p.getUUID());
        if (session != null) finish(p, session);
        RETRY.remove(p.getUUID());
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        UUID id = p.getUUID();
        Session existing = ACTIVE.get(id);
        if (existing != null) {
            if (!valid(p, existing.bed()) || p.serverLevel().getGameTime() - existing.started() >= ObserverTuning.NORA_ROOM_DURATION)
                finish(p, existing);
            return;
        }
        ObserverProgressData progress = p.getData(ModAttachments.OBSERVER);
        if (p.tickCount % ObserverTuning.NORA_ROOM_SCAN_TICKS != 0 || progress.stage() < 5 || progress.noraRoomSeen()
                || LabPathHandler.isActive(p)) return;
        ServerLevel level = p.serverLevel();
        if (!level.dimension().equals(Level.OVERWORLD) || level.getGameTime() < RETRY.getOrDefault(id, 0L)) return;
        int[] home = progress.home();
        if (home == null) return;
        BlockPos bed = new BlockPos(home[0], home[1], home[2]);
        long day = level.getDayTime() % 24000L;
        if (!valid(p, bed) || day < ChaseTuning.NIGHT_FROM || day > ChaseTuning.NIGHT_TO
                || level.canSeeSky(bed.above())) return;
        ArrayList<BlockPos> free = new ArrayList<>();
        BlockPos fullBed = null;
        int radius = ObserverTuning.NORA_ROOM_SEARCH_RADIUS;
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            BlockPos pos = bed.offset(x, 0, z);
            if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.above()) || !level.hasChunkAt(pos.below())) continue;
            if (pos.distSqr(p.blockPosition()) < 4 || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) continue;
            if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP) || level.canSeeSky(pos.above())) continue;
            // Не ставим иллюзию за капитальной стеной или в другой пристройке.
            if (level.clip(new ClipContext(p.getEyePosition(), Vec3.atCenterOf(pos), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS) continue;
            free.add(pos);
            if (fullBed == null && canPlaceFullBed(level, pos)) fullBed = pos;
        }
        if (free.isEmpty()) { RETRY.put(id, level.getGameTime() + ObserverTuning.NORA_ROOM_RETRY_TICKS); return; }
        int layout;
        BlockPos a, b, c;
        if (fullBed != null) {
            a = fullBed;
            b = null;
            c = null;
            layout = ObserverTuning.NORA_ROOM_LAYOUT_BED;
            for (BlockPos pos : free) {
                if (pos.equals(a) || pos.equals(a.north())) continue;
                if (b == null) { b = pos; layout |= ObserverTuning.NORA_ROOM_LAYOUT_CARPET; }
                else if (c == null) { c = pos; layout |= ObserverTuning.NORA_ROOM_LAYOUT_TOY; break; }
            }
            if (b == null) b = a;
            if (c == null) c = a;
        } else {
            // Одна клетка: только ковёр и рисунок, без двухблочной кровати.
            a = b = c = free.get(0);
            layout = ObserverTuning.NORA_ROOM_LAYOUT_CARPET;
        }
        int token = p.getRandom().nextInt();
        Session session = new Session(token, bed, a, b, c, layout, level.getGameTime());
        ACTIVE.put(id, session);
        PacketDistributor.sendToPlayer(p, new NoraRoomPayload(token, ObserverTuning.NORA_ROOM_DURATION, bed, a, b, c, layout));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { ACTIVE.remove(e.getEntity().getUUID()); RETRY.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) { ACTIVE.clear(); RETRY.clear(); }
}
