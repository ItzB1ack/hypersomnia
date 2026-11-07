package com.hypersomnia.nightmare;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.dream.DreamAttachments;
import com.hypersomnia.dream.DreamData;
import com.hypersomnia.dream.DreamDimension;
import com.hypersomnia.dream.DreamTransition;
import com.hypersomnia.network.ShaftOpenPayload;
import com.hypersomnia.network.ShaftSelectPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Сервер управляет поездкой; клиент может только запросить допустимый этаж. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class ShaftHandler {
    private ShaftHandler() {}
    private static final class Trip {
        final int lane, from, target, floor;
        int ticks;
        Trip(int lane, int from, int target, int floor, int ticks) { this.lane = lane; this.from = from; this.target = target; this.floor = floor; this.ticks = ticks; }
    }
    private static final Map<UUID, Trip> TRIPS = new HashMap<>();
    private static boolean active(ServerPlayer p) {
        DreamData data = p.getData(DreamAttachments.DREAM);
        return p.isAlive() && p.serverLevel().dimension().equals(DreamDimension.LEVEL)
                && data.isInDream() && NightmareDefs.SHAFT.id().equals(data.getKind());
    }
    @SubscribeEvent public static void panel(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer p) || !active(p)) return;
        int lane = ShaftStorage.get(p.serverLevel()).lane(p.getUUID());
        if (lane < 0) return;
        int stop = ShaftBuilder.stopOf(lane, p.position());
        if (stop < 0 || !ShaftBuilder.panel(lane, stop).equals(event.getPos())) return;
        event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
        if (!TRIPS.containsKey(p.getUUID())) PacketDistributor.sendToPlayer(p, new ShaftOpenPayload(ShaftBuilder.FIRST_FLOOR + ShaftStorage.get(p.serverLevel()).trips(p.getUUID())));
    }
    public static void handleSelect(ShaftSelectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof ServerPlayer p) request(p, payload.target()); });
    }
    /** [новое] Любая кнопка отправляет кабину на следующий этаж вниз; кнопка 0 работает после SHAFT_TRIPS_TO_ZERO поездок. */
    public static boolean request(ServerPlayer p, int target) {
        if (!active(p) || TRIPS.containsKey(p.getUUID())) return false;
        int lane = ShaftStorage.get(p.serverLevel()).lane(p.getUUID());
        if (lane < 0) return false;
        int from = ShaftBuilder.stopOf(lane, p.position());
        if (from < 0) return false;
        int done = ShaftStorage.get(p.serverLevel()).trips(p.getUUID());
        boolean leave = target == 0 && done >= NightmareTuning.SHAFT_TRIPS_TO_ZERO;
        int floor = leave ? 0 : ShaftBuilder.FIRST_FLOOR + done + 1;
        if (!leave && target != floor) p.displayClientMessage(Component.translatable("hypersomnia.nightmare.shaft.mismatch"), true);
        if (!ShaftBuilder.setDoor(p.serverLevel(), lane, from, true)) return false;
        ShaftBuilder.lamp(p.serverLevel(), lane, from, true);
        int ticks = NightmareTuning.SHAFT_TRIP_TICKS + Math.min(NightmareTuning.SHAFT_TRIP_EXTRA_MAX,
                done * NightmareTuning.SHAFT_TRIP_EXTRA_TICKS);
        TRIPS.put(p.getUUID(), new Trip(lane, from, leave ? 0 : ShaftBuilder.slotOf(floor), floor, ticks));
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks + 20, 0, false, false, false));
        return true;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        Iterator<Map.Entry<UUID, Trip>> iterator = TRIPS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Trip> entry = iterator.next(); Trip t = entry.getValue();
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (p == null || !active(p) || ShaftStorage.get(p.serverLevel()).lane(p.getUUID()) != t.lane
                    || ShaftBuilder.stopOf(t.lane, p.position()) != t.from) {
                iterator.remove();
                ServerLevel world = event.getServer().getLevel(DreamDimension.LEVEL);
                if (world != null) ShaftBuilder.setDoor(world, t.lane, t.from, false);
                if (p != null) p.removeEffect(MobEffects.BLINDNESS);
                continue;
            }
            if (--t.ticks > 0) continue;
            iterator.remove();
            ServerLevel world = p.serverLevel();
            BlockPos destination = ShaftBuilder.spawn(t.lane, t.target);
            p.teleportTo(world, destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5, p.getYRot(), p.getXRot());
            p.resetFallDistance(); p.removeEffect(MobEffects.BLINDNESS);
            ShaftStorage.get(world).addTrip(p.getUUID());
            if (t.floor != 0) p.displayClientMessage(Component.translatable("hypersomnia.nightmare.shaft.arrive", "B" + t.floor), true);
            ShaftBuilder.setDoor(world, t.lane, t.from, false);
            ShaftBuilder.setDoor(world, t.lane, t.target, false);
            if (t.floor == 0) DreamTransition.exit(p);
            else depthEvent(p, world, t);
        }
    }
    /** [новое] Глубже B8: по хешу игрока и этажа редкое личное событие. Баланс рабочий, не авторский. */
    private static void depthEvent(ServerPlayer p, ServerLevel world, Trip t) {
        if (t.floor < NightmareTuning.SHAFT_EVENTS_FROM_FLOOR) return;
        switch (Math.floorMod(java.util.Objects.hash(p.getUUID(), t.floor), 4)) {
            case 0 -> ShaftBuilder.lamp(world, t.lane, t.target, false);
            case 1 -> p.playNotifySound(net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE, net.minecraft.sounds.SoundSource.AMBIENT, 0.5f, 0.5f);
            case 2 -> p.playNotifySound(net.minecraft.sounds.SoundEvents.DEEPSLATE_STEP, net.minecraft.sounds.SoundSource.AMBIENT, 0.6f, 0.6f);
            default -> { }
        }
    }
    private static void relight(ServerLevel level, int lane) {
        for (int stop : new int[]{0, 6, 7, 8}) ShaftBuilder.lamp(level, lane, stop, true);
    }
    /** Удаляется только после успешного выхода или смерти, не при временном отключении. */
    public static void release(ServerPlayer p) {
        Trip t = TRIPS.remove(p.getUUID());
        ServerLevel level = p.server.getLevel(DreamDimension.LEVEL);
        if (level != null) {
            if (t != null) ShaftBuilder.setDoor(level, t.lane, t.from, false);
            int lane = ShaftStorage.get(level).lane(p.getUUID());
            if (lane >= 0) relight(level, lane);
            ShaftStorage.get(level).release(p.getUUID());
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Trip t = TRIPS.remove(p.getUUID());
        ServerLevel level = p.server.getLevel(DreamDimension.LEVEL);
        if (t != null && level != null) { ShaftBuilder.setDoor(level, t.lane, t.from, false); p.removeEffect(MobEffects.BLINDNESS); }
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (!active(p)) { release(p); return; }
        int lane = ShaftStorage.get(p.serverLevel()).lane(p.getUUID());
        if (lane < 0 || !ShaftBuilder.ensureBuilt(p.serverLevel(), lane)) {
            p.displayClientMessage(Component.translatable("hypersomnia.nightmare.shaft.restore_failed"), false); return;
        }
        for (int stop : new int[]{0, 6, 7, 8}) ShaftBuilder.setDoor(p.serverLevel(), lane, stop, false);
        relight(p.serverLevel(), lane);
        p.removeEffect(MobEffects.BLINDNESS);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !active(p)) release(p);
    }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(DreamDimension.LEVEL)
                && !event.getPlayer().isCreative() && ShaftBuilder.containsBuilt(level, event.getPos()))
            event.setCanceled(true);
    }
    @SubscribeEvent public static void placeBlock(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(DreamDimension.LEVEL)
                && event.getEntity() instanceof ServerPlayer p && !p.isCreative()
                && ShaftBuilder.containsBuilt(level, event.getPos())) event.setCanceled(true);
    }
    @SubscribeEvent public static void explode(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(DreamDimension.LEVEL))
            event.getAffectedBlocks().removeIf(pos -> ShaftBuilder.containsBuilt(level, pos));
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event) { TRIPS.clear(); }
}
