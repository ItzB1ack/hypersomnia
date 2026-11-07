package com.hypersomnia.lab;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.dream.DreamDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Лифт-«телепорт»: на каждой остановке своя кабина 3x3 с общей осью (x, z). Игрок выбирает этаж на панели,
 * двери закрываются, всех в кабине переносит в такую же кабину на целевой остановке.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class LabLift {
    private LabLift() {}

    private static final class Trip {
        final ServerLevel level;
        final int from;
        final int to;
        int ticks, total, elapsed;
        com.hypersomnia.server.LiftRide ride;
        List<ServerPlayer> riders = new ArrayList<>();

        Trip(ServerLevel level, int from, int to, int ticks) {
            this.level = level;
            this.from = from;
            this.to = to;
            this.ticks = ticks;
            this.total = ticks;
        }
    }

    private static final List<Trip> TRIPS = new ArrayList<>();

    /** Кабина остановки n: клетки x-1..x+1, z-1..z+1, три блока вверх (низ включает плиту пола — для восстановления). */
    public static AABB cabin(LabSavedData d, int n) {
        return new AABB(d.originX - 1, d.stopY[n] - 1, d.originZ - 1, d.originX + 2, d.stopY[n] + 3, d.originZ + 2);
    }

    /** Остановка, в чьей кабине стоит игрок, или -1. */
    public static int stopOf(ServerPlayer p, LabSavedData d) {
        for (int n = 0; n < LabTuning.STOPS; n++)
            if (d.isBuilt(n) && cabin(d, n).contains(p.position())) return n;
        return -1;
    }

    public static boolean busy(ServerLevel level) {
        for (Trip t : TRIPS) if (t.level == level) return true;
        return false;
    }

    /**
     * Старший открытый этаж для лифта. Во сне при открытом входе — не ниже реального (LabTuning.DREAM_FOLLOWS_REAL):
     * во сне нет щита MAIN FEED, этажи открываются по реальной лаборатории.
     */
    public static int reached(ServerLevel level, LabSavedData d) {
        int r = d.reached;
        if (LabTuning.DREAM_FOLLOWS_REAL && d.dream && r >= 1) {
            ServerLevel real = level.getServer().overworld();
            if (real != level) r = Math.max(r, LabSavedData.get(real).reached);
        }
        return r;
    }

    /** Маска открытых для поездки остановок (построены и не выше reached(level, d)). */
    public static int enabledMask(ServerLevel level, LabSavedData d) {
        int top = reached(level, d);
        int m = 0;
        for (int n = 0; n < LabTuning.STOPS; n++) if (d.isBuilt(n) && n <= top) m |= 1 << n;
        return m;
    }

    private static void msg(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable(key), true);
    }

    public static void request(ServerPlayer player, int target) {
        ServerLevel level = player.serverLevel();
        LabSavedData d = LabSavedData.get(level);
        if (!d.built) return;
        int from = stopOf(player, d);
        if (from < 0) return;
        if (busy(level)) {
            msg(player, "hypersomnia.lab.lift.busy");
            return;
        }
        if (LabOutage.active(level)) { // «обесточено»: лифт стоит, пока не вернётся свет
            msg(player, "hypersomnia.lab.lock.nopower");
            return;
        }
        if (target == from) {
            msg(player, "hypersomnia.lab.lift.same");
            return;
        }
        if (!d.isBuilt(target)) {
            msg(player, "hypersomnia.lab.lift.none");
            return;
        }
        if (target > reached(level, d)) {
            msg(player, "hypersomnia.lab.lift.locked");
            return;
        }
        // [новое] Время поездки — по реальной высоте: G↔B1 выше одного этажа, иначе кабина летела бы слишком быстро.
        int ticks = LabTuning.LIFT_BASE_TICKS + Math.abs(d.stopY[target] - d.stopY[from]) * LabTuning.LIFT_PER_BLOCK;
        TRIPS.add(new Trip(level, from, target, ticks));
        setDoor(level, d, from, true);
        d.closedDoor = from;
        d.setDirty();
        BlockPos c = new BlockPos(d.originX, d.stopY[from], d.originZ);
        level.playSound(null, c, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 0.6f);
    }

    /** [новое] Плавный рейс: на старте копия оболочки кабины и сиденья игроков едут к целевой кабине; блоки лаборатории не трогаются. */
    private static void startRide(Trip t) {
        LabSavedData d = LabSavedData.get(t.level);
        int y = d.stopY[t.from];
        List<ServerPlayer> inside = new ArrayList<>();
        for (ServerPlayer p : t.level.players()) {
            if (!cabin(d, t.from).contains(p.position())) continue;
            // [важно] Если игрок уже провалился в пол (старый баг), поднимаем его на пол кабины до старта.
            if (p.getY() < y - 0.01) p.teleportTo(t.level, p.getX(), y, p.getZ(), p.getYRot(), p.getXRot());
            inside.add(p);
        }
        t.riders = inside;
        // [новое] Оболочка включает настоящий потолок кабины (y+5), а не обрывается на y+3:
        // иначе при подъёме камера проходила потолок сквозь открытый верх копии.
        // Шахта G↔B1 вырыта (LabShaft), поэтому объёмный режим теперь безопасен на всех маршрутах:
        // он прячет только плиты пола/потолка на концах, а между этажами кабина едет в готовом проёме.
        boolean volume = true;
        t.ride = com.hypersomnia.server.LiftRide.start(t.level, new BlockPos(d.originX - 2, y - 1, d.originZ - 2),
                new BlockPos(d.originX + 2, y + 5, d.originZ + 2), inside, 0, d.stopY[t.to] - y, 0, 0,
                Math.max(10, t.total - 10 - com.hypersomnia.server.LiftRide.PAD_TICKS), volume);
    }

    private static void finish(Trip t) {
        ServerLevel level = t.level;
        LabSavedData d = LabSavedData.get(level);
        double dy = d.stopY[t.to] - d.stopY[t.from];
        if (t.ride != null) t.ride.end();
        for (ServerPlayer p : t.riders) {
            if (!p.isAlive() || p.serverLevel() != level) continue;
            if (t.ride != null) {
                // После settle клиент уже в конечной точке: телепорт только при заметном расхождении,
                // иначе в конце поездки камера дёргается отдельным пакетом позиции.
                double[] end = t.ride.finalPos(p);
                // [важно] Никогда не оставляем игрока ниже пола целевой кабины: иначе он «проваливается»
                // в пол на блок и следующая поездка начинается из-под пола.
                double endY = Math.max(end[1], d.stopY[t.to]);
                // Любое утопание в пол исправляем, даже меньше порога 0.5: иначе игрок остаётся в плите.
                boolean sunk = p.getY() < endY - 0.01;
                if (sunk || p.position().distanceToSqr(end[0], endY, end[2]) > 0.25)
                    p.teleportTo(level, end[0], endY, end[2], p.getYRot(), p.getXRot());
            } else {
                p.teleportTo(level, p.getX(), p.getY() + dy, p.getZ(), p.getYRot(), p.getXRot());
            }
            p.resetFallDistance();
        }
        d.closedDoor = -1;
        refreshDoors(level, d);
        d.setDirty();
        BlockPos c = new BlockPos(d.originX, d.stopY[t.to], d.originZ);
        level.playSound(null, c, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0f, 0.6f);
    }

    /** Во время рейса дверь отправления остаётся закрытой; поверхность закрыта до открытия люка. */
    public static void refreshDoors(ServerLevel level, LabSavedData d) {
        for (int n = 0; n < LabTuning.STOPS; n++)
            if (d.isBuilt(n)) setDoor(level, d, n, n == d.closedDoor || (n == 0 && d.reached < 1));
    }

    public static void setDoor(ServerLevel level, LabSavedData d, int stop, boolean closed) {
        if (!d.isBuilt(stop)) return;
        BlockPos p = BlockPos.of(d.door[stop]);
        for (int y = 0; y < 3; y++) {
            BlockPos q = p.above(y);
            level.setBlock(q, closed ? doorBars(level, q) : Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /**
     * Прутья закрытой двери кабины, сцепленные с рамкой проёма так же, как при установке рукой.
     * Голый defaultBlockState() — одинокий столбик посреди проёма: дверь не читалась закрытой.
     */
    public static BlockState doorBars(ServerLevel level, BlockPos q) {
        return Block.updateFromNeighbourShapes(Blocks.IRON_BARS.defaultBlockState(), level, q);
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (TRIPS.isEmpty()) return;
        Iterator<Trip> it = TRIPS.iterator();
        while (it.hasNext()) {
            Trip t = it.next();
            t.elapsed++;
            if (t.ride == null && t.elapsed >= 10) startRide(t);
            if (t.ride != null) t.ride.tick();
            if (--t.ticks > 0 || (t.ride != null && !t.ride.motionDone())) continue;
            it.remove();
            finish(t);
        }
    }

    /** Если мир сохранили посреди рейса, двери остались закрытыми: открываем заново. */
    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        TRIPS.clear();
        List<ServerLevel> levels = new ArrayList<>();
        levels.add(event.getServer().getLevel(Level.OVERWORLD));
        levels.add(event.getServer().getLevel(DreamDimension.LEVEL));
        for (ServerLevel level : levels) {
            if (level == null) continue;
            LabSavedData d = LabSavedData.get(level);
            if (d.built && d.closedDoor >= 0) {
                d.closedDoor = -1;
                refreshDoors(level, d);
                d.setDirty();
            }
        }
    }

    /** Остановка сервера посреди рейса: доводим игроков до целевой кабины, чтобы они не сохранились в шахте. */
    @SubscribeEvent
    public static void onStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        for (Trip t : new ArrayList<>(TRIPS)) finish(t);
        TRIPS.clear();
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        TRIPS.clear();
    }
}
