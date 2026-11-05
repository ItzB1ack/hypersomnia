package com.hypersomnia.lab;

import com.hypersomnia.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
 */
public final class SeatEntity extends Entity {
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

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && (!isVehicle() || !(level().getBlockState(blockPosition()).getBlock() instanceof ChairBlock))) discard();
    }

    /** Точка посадки — сама сущность: высоту задаёт CHAIR_SEAT_Y. */
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
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
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
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
