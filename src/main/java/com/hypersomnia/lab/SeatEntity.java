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
 * Тем же сиденьем пользуется открытая капсула B5 (lie): якорь — изножье тройки CCC, сиденье стоит в центре
 * клетки изголовья на высоте ложа, игрок лежит лицом к изголовью в позе кровати (Pose.SLEEPING, как в
 * ванильной кровати; клиентский рендер — LabCapsuleRender). Сон при этом не начинается: startSleeping не зовётся.
 * Закрылась/исчезла капсула — сиденье исчезает и выпускает игрока.
 */
public final class SeatEntity extends Entity {
    /** Лежит в капсуле (синхронизируется: клиенту нужен вид и поза). */
    private static final EntityDataAccessor<Boolean> DATA_LYING =
            SynchedEntityData.defineId(SeatEntity.class, EntityDataSerializers.BOOLEAN);
    /** Направление изножье -> изголовье (2D data value), нужно клиентскому рендеру. */
    private static final EntityDataAccessor<Byte> DATA_LIE_FACING =
            SynchedEntityData.defineId(SeatEntity.class, EntityDataSerializers.BYTE);

    /** Изножье капсулы (только на сервере). */
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
     * ПКМ по открытой капсуле на сервере: лечь вдоль тройки (изножье -> середина -> изголовье).
     * false — не лёг (капсула закрыта, занята или тройка разбита).
     */
    public static boolean lie(ServerLevel level, BlockPos foot, Player player, Direction facing) {
        if (player.isPassenger() || player.isSleeping()) return false;
        if (!CapsuleBlock.canLie(level, foot, facing)) return false;
        BlockPos head = foot.relative(facing, 2);
        AABB triple = new AABB(foot).minmax(new AABB(head));
        if (!level.getEntitiesOfClass(SeatEntity.class, triple, Entity::isVehicle).isEmpty()) return false; // занята
        SeatEntity seat = new SeatEntity(ModEntities.SEAT.get(), level);
        seat.anchor = foot.immutable();
        seat.lieFacing = facing;
        seat.entityData.set(DATA_LYING, true);
        seat.entityData.set(DATA_LIE_FACING, (byte) facing.get2DDataValue());
        // Тело лежит от изножья к изголовью; сиденье — в центре клетки изголовья, на верх ложа.
        seat.moveTo(head.getX() + 0.5, foot.getY() + LabTuning.CAPSULE_LIE_Y, head.getZ() + 0.5,
                facing.toYRot(), 0.0f);
        if (!level.addFreshEntity(seat)) return false;
        if (player.startRiding(seat, true)) return true;
        seat.discard();
        return false;
    }

    public boolean isLying() {
        return entityData.get(DATA_LYING);
    }

    /** Направление тела лежащего (доступно на клиенте). */
    public Direction getLieFacing() {
        return Direction.from2DDataValue(entityData.get(DATA_LIE_FACING));
    }

    /** Высота сиденья: стул — CHAIR_SEAT_Y, капсула — CAPSULE_LIE_Y (центр клетки изголовья). */
    public double seatY() {
        return isLying() ? LabTuning.CAPSULE_LIE_Y : LabTuning.CHAIR_SEAT_Y;
    }

    @Override
    public void tick() {
        super.tick();
        // Поза задаётся и на клиенте: у LocalPlayer updatePlayerPose перезаписывает позу каждый тик.
        if (isLying() && getFirstPassenger() instanceof Player player) {
            player.setForcedPose(Pose.SLEEPING);
            player.setPose(Pose.SLEEPING);
        }
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

    /** Лежащий не «сидит»: иначе модель получает позу езды вместо позы кровати. */
    @Override
    public boolean shouldRiderSit() {
        return !isLying();
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (isLying()) {
            // Лежащего не разворачиваем: его yRot — это взгляд (клиентский рендер ограничивает поворот головы).
            if (passenger instanceof Player player) {
                player.setForcedPose(Pose.SLEEPING);
                player.setPose(Pose.SLEEPING);
            }
            return;
        }
        float yaw = getYRot(); // на клиенте поворачивает камеру садящегося (как лодка)
        passenger.setYRot(yaw);
        passenger.yRotO = yaw;
        passenger.setYHeadRot(yaw);
        if (passenger instanceof LivingEntity living) {
            living.yBodyRot = yaw;
            living.yBodyRotO = yaw;
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (isLying() && passenger instanceof Player player) player.setForcedPose(null);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (isLying() && anchor != null) {
            // Сначала сбоку от тройки, иначе перед изножьем/за изголовьем.
            Direction[] sides = {lieFacing.getClockWise(), lieFacing.getCounterClockWise()};
            for (Direction side : sides) {
                for (int i = 0; i < 3; i++) {
                    Vec3 v = DismountHelper.findSafeDismountLocation(passenger.getType(), level(),
                            anchor.relative(lieFacing, i).relative(side), true);
                    if (v != null) return v;
                }
            }
            for (int i = 0; i < 3; i++) {
                Vec3 v = DismountHelper.findSafeDismountLocation(passenger.getType(), level(),
                        anchor.relative(lieFacing, i), true);
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
        builder.define(DATA_LIE_FACING, (byte) Direction.EAST.get2DDataValue());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
