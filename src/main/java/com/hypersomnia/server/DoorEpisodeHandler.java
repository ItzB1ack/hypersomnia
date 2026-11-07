package com.hypersomnia.server;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.ObserverProgressData;
import com.hypersomnia.ObserverTuning;
import com.hypersomnia.chase.ChaseHandler;
import com.hypersomnia.network.DoorEpisodeRequest;
import com.hypersomnia.network.DoorEpisodeStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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

/** Дверь действительно открывается в мире; время и последствия решает сервер. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class DoorEpisodeHandler {
    private DoorEpisodeHandler() {}
    private static final class Episode {
        final int sequence;
        final BlockPos door;
        final net.minecraft.world.level.block.Block block;
        final int cycles;
        int opened, delay;
        boolean waiting;
        Episode(int sequence, BlockPos door, BlockState state, int cycles) {
            this.sequence = sequence; this.door = door.immutable(); this.block = state.getBlock(); this.cycles = cycles;
        }
    }
    private static final Map<UUID, Episode> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> NEXT = new HashMap<>();

    public static void handle(DoorEpisodeRequest payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer p)) return;
            UUID id = p.getUUID();
            if (payload.action() == DoorEpisodeRequest.CANCEL) {
                Episode e = ACTIVE.get(id);
                if (e != null && e.sequence == payload.sequence()) ACTIVE.remove(id);
                return;
            }
            if (payload.action() != DoorEpisodeRequest.START) return;
            ServerLevel level = p.serverLevel();
            BlockPos pos = payload.door();
            long now = level.getGameTime();
            if (ACTIVE.containsKey(id) || now < NEXT.getOrDefault(id, 0L)) { status(p, payload.sequence(), DoorEpisodeStatus.REJECTED); return; }
            NEXT.put(id, now + ObserverTuning.DOOR_EPISODE_REQUEST_COOLDOWN);
            ObserverProgressData data = p.getData(ModAttachments.OBSERVER);
            int[] home = data.home();
            if (home == null || !level.dimension().equals(Level.OVERWORLD)
                    || pos.distSqr(new BlockPos(home[0], home[1], home[2])) > ObserverTuning.DOOR_HOME_RADIUS * ObserverTuning.DOOR_HOME_RADIUS
                    || p.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > ObserverTuning.DOOR_RADIUS * ObserverTuning.DOOR_RADIUS
                    || !level.hasChunkAt(pos) || !level.hasChunkAt(pos.above()) || !ChaseHandler.canStartDoor(p)) {
                status(p, payload.sequence(), DoorEpisodeStatus.REJECTED); return;
            }
            BlockState state = level.getBlockState(pos);
            if (!suitable(state) || state.getValue(DoorBlock.OPEN) || state.getValue(DoorBlock.POWERED)) {
                status(p, payload.sequence(), DoorEpisodeStatus.REJECTED); return;
            }
            // Одна физическая дверь не должна одновременно участвовать в двух личных эпизодах.
            for (Episode other : ACTIVE.values()) if (other.door.equals(pos)) {
                status(p, payload.sequence(), DoorEpisodeStatus.REJECTED); return;
            }
            int n = ObserverTuning.DOOR_EPISODE_MIN_CYCLES + p.getRandom().nextInt(
                    ObserverTuning.DOOR_EPISODE_MAX_CYCLES - ObserverTuning.DOOR_EPISODE_MIN_CYCLES + 1);
            Episode episode = new Episode(payload.sequence(), pos, state, n);
            ACTIVE.put(id, episode);
            status(p, payload.sequence(), DoorEpisodeStatus.STARTED);
        });
    }

    private static boolean suitable(BlockState state) {
        return state.is(BlockTags.WOODEN_DOORS) && state.getBlock() instanceof DoorBlock
                && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
    }
    private static void status(ServerPlayer p, int sequence, int status) {
        PacketDistributor.sendToPlayer(p, new DoorEpisodeStatus(sequence, status));
    }
    private static void end(ServerPlayer p, Episode e) {
        ACTIVE.remove(p.getUUID());
        status(p, e.sequence, DoorEpisodeStatus.ENDED);
    }

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Episode e = ACTIVE.get(p.getUUID());
        if (e == null) return;
        ServerLevel level = p.serverLevel();
        if (!level.dimension().equals(Level.OVERWORLD) || !p.isAlive() || p.isSleeping() || p.isSpectator()
                || p.distanceToSqr(e.door.getX() + .5, e.door.getY() + .5, e.door.getZ() + .5)
                    > ObserverTuning.DOOR_EPISODE_MAX_DISTANCE * ObserverTuning.DOOR_EPISODE_MAX_DISTANCE
                || !level.hasChunkAt(e.door) || !level.hasChunkAt(e.door.above())) { end(p, e); return; }
        BlockState state = level.getBlockState(e.door);
        if (!suitable(state) || state.getBlock() != e.block || state.getValue(DoorBlock.POWERED)) { end(p, e); return; }
        if (e.waiting) {
            if (!state.getValue(DoorBlock.OPEN)) {
                e.waiting = false;
                if (e.opened >= e.cycles) { end(p, e); return; }
                e.delay = ObserverTuning.DOOR_EPISODE_GAP;
            } else if (--e.delay <= 0) {
                ACTIVE.remove(p.getUUID());
                if (!ChaseHandler.startFromDoor(p, e.door)) status(p, e.sequence, DoorEpisodeStatus.ENDED);
            }
            return;
        }
        if (e.delay > 0) { e.delay--; return; }
        if (state.getValue(DoorBlock.OPEN)) { end(p, e); return; }
        ((DoorBlock) state.getBlock()).setOpen(null, level, state, e.door, true);
        e.opened++;
        e.waiting = true;
        e.delay = Math.max(ObserverTuning.DOOR_EPISODE_MIN_REPLY,
                ObserverTuning.DOOR_EPISODE_INITIAL_REPLY - (e.opened - 1) * ObserverTuning.DOOR_EPISODE_REPLY_STEP);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        ACTIVE.remove(e.getEntity().getUUID()); NEXT.remove(e.getEntity().getUUID());
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) { ACTIVE.clear(); NEXT.clear(); }
}
