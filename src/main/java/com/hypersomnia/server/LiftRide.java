package com.hypersomnia.server;

import com.hypersomnia.Hypersomnia;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * [новое] Плавный реальный подъём/спуск кабины без перестановки блоков: копия оболочки кабины из блочных
 * дисплеев и невидимое «сиденье» игрока плавно едут вместе; настоящие блоки мира не трогаются. Игрок сидит на
 * дисплее-носителе, поэтому клиент интерполирует движение, а не получает телепорт каждый тик.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class LiftRide {
    private static final String TAG = "hypersomnia_lift_ride";
    private static final Set<LiftRide> ALL = new HashSet<>();
    private static final Set<UUID> RIDING = new HashSet<>();

    private static final class Rider {
        final ServerPlayer player; final Entity seat; final double x, y, z;
        double ox, oy, oz;
        Rider(ServerPlayer player, Entity seat) { this.player = player; this.seat = seat; x = player.getX(); y = player.getY(); z = player.getZ(); }
    }
    private static final class Part {
        final Entity entity; final double x, y, z;
        Part(Entity entity) { this.entity = entity; x = entity.getX(); y = entity.getY(); z = entity.getZ(); }
    }

    private final double dy;
    private final int moveTicks;
    private int t, calib;
    private final List<Part> shell = new ArrayList<>();
    private final List<Rider> riders = new ArrayList<>();

    private LiftRide(double dy, int moveTicks) { this.dy = dy; this.moveTicks = Math.max(1, moveTicks); }

    /** Копирует непустые блоки коробки min..max (включительно) в дисплеи и сажает игроков; dy — полный сдвиг по вертикали. */
    public static LiftRide start(ServerLevel level, BlockPos min, BlockPos max, List<ServerPlayer> players, double dy, int moveTicks) {
        LiftRide ride = new LiftRide(dy, moveTicks);
        for (int x = min.getX(); x <= max.getX(); x++) for (int y = min.getY(); y <= max.getY(); y++)
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                BlockState state = level.getBlockState(new BlockPos(x, y, z));
                if (state.isAir()) continue;
                Entity e = display(level, state, x, y, z, 10);
                if (e != null) ride.shell.add(new Part(e));
            }
        for (ServerPlayer p : players) {
            Entity seat = display(level, Blocks.AIR.defaultBlockState(), p.getX(), p.getY(), p.getZ(), 0);
            if (seat == null) continue;
            p.startRiding(seat, true);
            ride.riders.add(new Rider(p, seat));
            RIDING.add(p.getUUID());
        }
        ALL.add(ride);
        return ride;
    }

    private static Entity display(ServerLevel level, BlockState state, double x, double y, double z, int light) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:block_display");
        tag.put("block_state", NbtUtils.writeBlockState(state));
        CompoundTag brightness = new CompoundTag();
        brightness.putInt("block", light); brightness.putInt("sky", 0);
        tag.put("brightness", brightness);
        ListTag tags = new ListTag(); tags.add(StringTag.valueOf(TAG)); tag.put("Tags", tags);
        Entity e = EntityType.create(tag, level).orElse(null);
        if (e == null) return null;
        e.moveTo(x, y, z);
        level.addFreshEntity(e);
        return e;
    }

    /** Вызывать каждый серверный тик. */
    public void tick() {
        if (calib < 2) {
            // Два тика на то, чтобы клиент и сервер выставили игрока на сиденье; снимаем смещение сиденья.
            if (++calib == 2) for (Rider r : riders) {
                r.ox = r.player.getX() - r.seat.getX(); r.oy = r.player.getY() - r.seat.getY(); r.oz = r.player.getZ() - r.seat.getZ();
            }
            return;
        }
        if (t < moveTicks) t++;
        double k = (double) t / moveTicks, s = k * k * (3 - 2 * k); // плавный разгон и торможение
        for (Rider r : riders) {
            if (!r.player.isPassenger()) r.player.startRiding(r.seat, true);
            r.seat.moveTo(r.x - r.ox, r.y + dy * s - r.oy, r.z - r.oz);
        }
        for (Part p : shell) p.entity.moveTo(p.x, p.y + dy * s, p.z);
    }

    public boolean motionDone() { return calib >= 2 && t >= moveTicks; }

    /** Конечная точка игрока (старт + dy). */
    public double[] finalPos(ServerPlayer player) {
        for (Rider r : riders) if (r.player == player) return new double[]{r.x, r.y + dy, r.z};
        return new double[]{player.getX(), player.getY(), player.getZ()};
    }

    /** Снимает игроков с сидений и удаляет все дисплеи; безопасно вызывать повторно. */
    public void end() {
        if (!ALL.remove(this)) return;
        for (Rider r : riders) {
            r.player.stopRiding();
            RIDING.remove(r.player.getUUID());
            r.seat.discard();
        }
        for (Part p : shell) p.entity.discard();
        shell.clear();
    }

    @SubscribeEvent public static void inWall(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && RIDING.contains(p.getUUID())
                && event.getSource().is(DamageTypes.IN_WALL)) event.setCanceled(true);
    }
    /** Осиротевшие дисплеи (сбой посреди поездки) не возвращаются из сохранения. */
    @SubscribeEvent public static void orphan(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getEntity().getTags().contains(TAG)) event.setCanceled(true);
    }
}
