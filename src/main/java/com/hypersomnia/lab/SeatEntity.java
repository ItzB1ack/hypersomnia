package com.hypersomnia.lab;

import com.hypersomnia.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Сиденье офисного стула: невидимая сущность без физики в блоке стула (высота LabTuning.CHAIR_SEAT_Y).
 * Живёт, пока на ней сидят и под ней стул; встал (Shift), стул снесли — сиденье исчезает само.
 * Садясь, игрок разворачивается лицом туда, куда смотрит сиденье (дальше крутится свободно, стул поворотный).
 * Встаёт перед стулом, если там свободно, иначе сбоку или сзади.
 *
 * Тем же сиденьем пользуется открытая капсула B5 (lie): якорь — изножье, сиденье стоит на стыке половин,
 * игрок лежит лицом к изголовью (Pose.SWIMMING). Сон при этом не начинается: startSleeping не зовётся.
 * Закрылась/исчезла капсула — сиденье исчезает и выпускает игрока.
 */
public final class SeatEntity extends Entity {
    /** Лежит в капсуле (синхронизируется: клиенту нужен вид и поза). */
    private static final EntityDataAccessor<Boolean> DATA_LYING =
            SynchedEntityData.defineId(SeatEntity.class, EntityDataSerializers.BOOLEAN);

    /** Изножье капсулы (только на сервере; клиенту достаточно флага). */
    private BlockPos anchor;
    /** Направление от изножья к изголовью (только на сервере). */
    private Direction lieFacing = Direction.EAST;

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** ПКМ по стулу на сервере: сесть, если стул свободен. false — не сел. */
    public static boolean sit(ServerLevel level, BlockPos pos, Player player, Direction front) {
        if (player.isPassenger() || player.isSleeping()) return false;
        if (!level.getEntitiesOfClass(SeatEntity.class, new AABB(pos), Entity::isVehicle).isEmpty()) return false; // занят
        SeatEntity seat = new SeatEntity(ModEntities.SEAT.get(), level);
        seat.moveTo(pos.getX() + 0.5, pos.getY() + LabTuning.CHAIR_SEAT_Y, pos.getZ() + 0.5, front.toYRot(), 0.0f);
        if (!level.addFreshEntity(seat)) return false;
        if (player.startRiding(seat, true)) return true;
        seat.discard();
        return false;
    }

    /**
     * ПКМ по открытой капсуле на сервере: лечь вдоль пары (изножье -> изголовье). false — не лёг
     * (капсула закрыта, занята или пара разбита).
     */
    public static boolean lie(ServerLevel level, BlockPos foot, Player player, Direction facing) {
        if (player.isPassenger() || player.isSleeping()) return false;
        if (!CapsuleBlock.canLie(level, foot, facing)) return false;
        BlockPos head = foot.relative(facing);
        AABB pair = new AABB(foot).minmax(new AABB(head));
        if (!level.getEntitiesOfClass(SeatEntity.class, pair, Entity::isVehicle).isEmpty()) return false; // занята
        SeatEntity seat = new SeatEntity(ModEntities.SEAT.get(), level);
        seat.anchor = foot.immutable();
        seat.lieFacing = facing;
        seat.entityData.set(DATA_LYING, true);
        // Лежим на стыке половин: тело вдоль оси капсулы, голова к изголовью.
        double x = foot.getX() + 0.5 + facing.getStepX() * 0.5;
        double z = foot.getZ() + 0.5 + facing.getStepZ() * 0.5;
        seat.moveTo(x, foot.getY() + LabTuning.CAPSULE_LIE_Y, z, facing.toYRot(), 0.0f);
        if (!level.addFreshEntity(seat)) return false;
        if (player.startRiding(seat, true)) return true;
        seat.discard();
        return false;
    }

    public boolean isLying() {
        return entityData.get(DATA_LYING);
    }

    @Override
    public void tick() {
        super.tick();
        // Поза задаётся и на клиенте: у LocalPlayer updatePlayerPose перезаписывает позу каждый тик.
        if (isLying() && getFirstPassenger() instanceof Player player) player.setForcedPose(Pose.SWIMMING);
        if (level().isClientSide) return;
        if (!isVehicle()) {
            discard();
            return;
        }
        boolean ok = isLying()
                ? CapsuleBlock.canLie(level(), anchor, lieFacing)
                : level().getBlockState(blockPosition()).getBlock() instanceof ChairBlock;
        if (!ok) discard();
    }

    /** Точка посадки — сама сущность: высоту задаёт CHAIR_SEAT_Y или CAPSULE_LIE_Y. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        return Vec3.ZERO;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        float yaw = getYRot(); // на клиенте поворачивает камеру садящегося (как лодка)
        passenger.setYRot(yaw);
        passenger.yRotO = yaw;
        passenger.setYHeadRot(yaw);
        if (passenger instanceof LivingEntity living) {
            living.yBodyRot = yaw;
            living.yBodyRotO = yaw;
        }
        if (isLying() && passenger instanceof Player player) player.setForcedPose(Pose.SWIMMING);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (isLying() && passenger instanceof Player player) player.setForcedPose(null);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (isLying() && anchor != null) {
            // Сначала сбоку от пары, иначе перед изножьем/за изголовьем.
            for (Direction side : new Direction[]{lieFacing.getClockWise(), lieFacing.getCounterClockWise()}) {
                for (BlockPos p : new BlockPos[]{anchor.relative(side), anchor.relative(lieFacing).relative(side)}) {
                    Vec3 v = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), p, true);
                    if (v != null) return v;
                }
            }
            for (BlockPos p : new BlockPos[]{anchor, anchor.relative(lieFacing)}) {
                Vec3 v = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), p, true);
                if (v != null) return v;
            }
        }
        BlockPos chair = blockPosition();
        BlockState st = level().getBlockState(chair);
        if (st.getBlock() instanceof ChairBlock) {
            Direction front = st.getValue(ChairBlock.FACING);
            for (Direction d : new Direction[]{front, front.getClockWise(), front.getCounterClockWise(), front.getOpposite()}) {
                Vec3 v = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), chair.relative(d), true);
                if (v != null) return v;
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_LYING, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
