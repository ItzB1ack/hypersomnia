package com.hypersomnia.server;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.ObserverProgressData;
import com.hypersomnia.chase.AmbushTuning;
import com.hypersomnia.chase.ChaseData;
import com.hypersomnia.chase.ChaseHandler;
import com.hypersomnia.chase.ChaseTuning;
import com.hypersomnia.finale.FinaleLogic;
import com.hypersomnia.lab.CabinetBlock;
import com.hypersomnia.lab.LabOutage;
import com.hypersomnia.lab.LabProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * [новое] Засада у двери и контейнера — короткий экшен-момент.
 * <p>
 * Дверь: игрок открывает деревянную дверь ночью/в темноте (стадия 3+, расписание погонь позволяет). Следующий
 * тик сервер видит, что дверь открылась, и ставит фигуру с противоположной стороны проёма. Контейнер: игрок
 * открывает сундук/бочку/шкаф и закрывает экран — фигура стоит за спиной. Дальше ChaseHandler.startAmbush:
 * секунда вплотную, затем настоящая погоня; клиент даёт короткий кадр-испуг (AmbushClient/AmbushOverlay).
 * <p>
 * Не запускается во время погони, сцены, чтения, смерти, в креативе, после финала. В лаборатории — только если
 * на этаже можно начать «обесточено» (LabOutage). Шанс и тайминги — AmbushTuning.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class AmbushHandler {
    private AmbushHandler() {}

    private static final class Pending {
        final BlockPos pos;
        final boolean door;
        final long since;
        boolean menuSeen;

        Pending(BlockPos pos, boolean door, long since) {
            this.pos = pos;
            this.door = door;
            this.since = since;
        }
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level) || level.isClientSide()) return;
        UUID id = player.getUUID();
        if (PENDING.containsKey(id) || !eligible(player)) return;

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            pos = pos.below();
            state = level.getBlockState(pos);
        }

        boolean door = isWoodenDoor(state);
        boolean container = isContainer(state);
        if (!door && !container) return;
        if (door) {
            if (state.getValue(DoorBlock.OPEN) || state.getValue(DoorBlock.POWERED) || player.isShiftKeyDown()) return;
            if (player.getRandom().nextFloat() >= AmbushTuning.DOOR_CHANCE) return;
        } else if (player.getRandom().nextFloat() >= AmbushTuning.CONTAINER_CHANCE) {
            return;
        }
        // В лаборатории засада возможна только там, где есть что обесточить.
        if (LabProtection.inside(level, player.blockPosition()) && LabOutage.stopFor(player) < 0) return;
        PENDING.put(id, new Pending(pos.immutable(), door, level.getGameTime()));
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        Pending pending = PENDING.get(id);
        if (pending == null) return;
        ServerLevel level = player.serverLevel();
        if (!player.isAlive() || player.isSpectator() || player.isSleeping() || player.isCreative()
                || player.isDeadOrDying() || ChaseHandler.activeFor(player) || FinaleLogic.completed(player)
                || !level.dimension().equals(Level.OVERWORLD) || !level.hasChunkAt(pending.pos)) {
            PENDING.remove(id);
            return;
        }
        long age = level.getGameTime() - pending.since;
        if (age > AmbushTuning.PENDING_TIMEOUT) {
            PENDING.remove(id);
            return;
        }
        if (pending.door) {
            tickDoor(player, level, id, pending, age);
        } else {
            tickContainer(player, level, id, pending);
        }
    }

    private static void tickDoor(ServerPlayer player, ServerLevel level, UUID id, Pending pending, long age) {
        if (age < 1) return;
        BlockState state = level.getBlockState(pending.pos);
        if (!isWoodenDoor(state)) {
            PENDING.remove(id);
            return;
        }
        if (!state.getValue(DoorBlock.OPEN)) {
            // Игрок мог просто поставить блок/использовать предмет на двери: ждём пару тиков и снимаем заявку.
            if (age > 2) PENDING.remove(id);
            return;
        }
        if (player.distanceToSqr(pending.pos.getX() + 0.5, pending.pos.getY() + 0.5,
                pending.pos.getZ() + 0.5) > AmbushTuning.CONTAINER_RADIUS * AmbushTuning.CONTAINER_RADIUS) {
            PENDING.remove(id);
            return;
        }
        PENDING.remove(id);
        ChaseHandler.startAmbush(player, doorSpot(player, pending.pos));
    }

    private static void tickContainer(ServerPlayer player, ServerLevel level, UUID id, Pending pending) {
        if (player.distanceToSqr(pending.pos.getX() + 0.5, pending.pos.getY() + 0.5,
                pending.pos.getZ() + 0.5) > AmbushTuning.CONTAINER_RADIUS * AmbushTuning.CONTAINER_RADIUS) {
            PENDING.remove(id);
            return;
        }
        if (player.containerMenu != player.inventoryMenu) {
            pending.menuSeen = true;
            return;
        }
        if (!pending.menuSeen) return; // экран ещё не успел открыться; если не откроется — сработает таймаут
        PENDING.remove(id);
        ChaseHandler.startAmbush(player, behind(player));
    }

    private static boolean eligible(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || player.isSleeping() || player.isCreative()
                || player.isDeadOrDying() || ChaseHandler.activeFor(player) || FinaleLogic.completed(player)) {
            return false;
        }
        ObserverProgressData progress = player.getData(ModAttachments.OBSERVER);
        if (progress.stage() < AmbushTuning.MIN_STAGE) return false;
        ChaseData chase = player.getData(ModAttachments.CHASE);
        if (progress.getDays() < chase.nextDay()) return false;
        return nightOrDark(player);
    }

    private static boolean nightOrDark(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long tod = level.getDayTime() % 24000L;
        if (tod >= ChaseTuning.NIGHT_FROM && tod <= ChaseTuning.NIGHT_TO) return true;
        return level.getMaxLocalRawBrightness(player.blockPosition()) <= ChaseTuning.DARK_LIGHT;
    }

    private static boolean isWoodenDoor(BlockState state) {
        return state.is(BlockTags.WOODEN_DOORS) && state.getBlock() instanceof DoorBlock
                && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    private static boolean isContainer(BlockState state) {
        return state.getBlock() instanceof ChestBlock
                || state.getBlock() instanceof BarrelBlock
                || state.getBlock() instanceof CabinetBlock
                || state.getBlock() instanceof ShulkerBoxBlock;
    }

    /** Точка с противоположной от игрока стороны двери: её видно сразу, как только дверь открылась. */
    private static Vec3 doorSpot(ServerPlayer player, BlockPos door) {
        Vec3 center = new Vec3(door.getX() + 0.5, door.getY(), door.getZ() + 0.5);
        Vec3 flat = player.position().subtract(center);
        flat = new Vec3(flat.x, 0.0, flat.z);
        Vec3 dir = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
        return center.subtract(dir.scale(AmbushTuning.DOOR_BEHIND));
    }

    /** За спиной игрока: он смотрит в открытый сундук/шкаф, когда экран закрывается. */
    private static Vec3 behind(ServerPlayer player) {
        Vec3 look = player.getViewVector(1.0f);
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        Vec3 dir = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
        return player.position().subtract(dir.scale(AmbushTuning.CONTAINER_BEHIND));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }
}
