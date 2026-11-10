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
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * [новое] Плавный подъём/спуск кабины: копия оболочки кабины из блочных дисплеев и невидимое «сиденье»
 * игрока плавно едут вместе. Настоящие блоки не двигаются.
 *
 * <p>Два режима очистки пути:
 * <ul>
 *   <li>{@code volume = true} — блоки, в которые входит движущаяся оболочка, прячутся по мере движения и
 *       возвращаются, когда кабина ушла. Так камера никогда не проходит сквозь стены, полы и потолки
 *       промежуточных этажей; подходит для шахты и для лаборатории с настоящей шахтой.</li>
 *   <li>{@code volume = false} — старый режим: прячутся только внутренние плиты пола/потолка у входа и
 *       выхода. Оставлен для совместимости; лаборатория теперь всегда едет через готовую шахту.</li>
 * </ul>
 *
 * <p>Профиль движения — quintic smootherstep: скорость и ускорение равны нулю в начале и в конце, поэтому нет
 * рывка на старте и «щелчка» при остановке. После прибытия оболочка держится ещё несколько тиков, чтобы клиент
 * догнал последнюю позицию, и только потом убирается.
 *
 * <p>У display-сущностей выставлен {@code teleport_duration}: без него {@link Display#lerpTo} телепортирует
 * сущность каждый клиентский тик и кабина с камерой игрока двигаются рывками 20 Гц.
 *
 * <p>Игрок всегда стоит на полу копии кабины (верх нижнего блока коробки), поэтому камера во время рейса
 * находится на обычной высоте относительно кабины. Носитель изначально ставится на высоту крепления
 * пассажира, чтобы {@code positionRider} не опускал игрока в пол на время калибровки.
 *
 * <p>Все скрытые блоки пишутся в {@link LiftRideStorage}: если сервер упадёт посреди рейса, они вернутся при
 * следующем запуске.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class LiftRide {
    private static final String TAG = "hypersomnia_lift_ride";
    private static final Set<LiftRide> ALL = new HashSet<>();
    private static final Set<UUID> RIDING = new HashSet<>();

    /** Тиков на «догон» клиентской интерполяции после прибытия. */
    public static final int SETTLE_TICKS = 5;
    /** Тиков на выравнивание посадки; также запас, который надо вычесть из общего времени поездки. */
    public static final int CALIB_TICKS = 4;
    public static final int PAD_TICKS = CALIB_TICKS + SETTLE_TICKS;
    /** [новое] Сколько клиентских тиков display-сущности плавно догоняют серверную позицию. */
    private static final int DISPLAY_INTERP_TICKS = 3;
    /** Насколько блок должен отойти от кабины, прежде чем его вернут на место. */
    private static final double RESTORE_MARGIN = 1.5;

    private static final class Rider {
        final ServerPlayer player; final Entity seat; final double x, y, z;
        double ox, oy, oz;
        Rider(ServerPlayer player, Entity seat, double feetY) {
            this.player = player; this.seat = seat;
            x = player.getX(); y = feetY; z = player.getZ();
        }
    }
    private static final class Part {
        final Entity entity; final double x, y, z;
        Part(Entity entity) { this.entity = entity; x = entity.getX(); y = entity.getY(); z = entity.getZ(); }
    }

    private final ServerLevel level;
    private final BlockPos min, max;
    private final double dx, dy, dz, dip;
    private final int moveTicks;
    private final boolean volume;
    private int t, calib;
    private final List<Part> shell = new ArrayList<>();
    private final List<Rider> riders = new ArrayList<>();
    /** Блоки, скрытые этим рейсом: позиция -> точное состояние. */
    private final Map<Long, BlockState> hidden = new HashMap<>();
    /** Кандидаты объёмного режима: непустые блоки в развёртке пути кабины. */
    private final List<BlockPos> swept = new ArrayList<>();
    /** Старый режим: позиции реальных плит у входа/выхода. */
    private final List<BlockPos> srcCover = new ArrayList<>();
    private final List<BlockPos> tgtCover = new ArrayList<>();
    private AABB targetBox;
    private boolean srcHidden, tgtHidden;

    private LiftRide(ServerLevel level, BlockPos min, BlockPos max, double dx, double dy, double dz,
                     double dip, int moveTicks, boolean volume) {
        this.level = level;
        this.min = min; this.max = max;
        this.dx = dx; this.dy = dy; this.dz = dz; this.dip = dip;
        this.moveTicks = Math.max(1, moveTicks);
        this.volume = volume;
    }

    /** Копирует непустые блоки коробки min..max (включительно) в дисплеи и сажает игроков; dy — полный сдвиг по вертикали. */
    public static LiftRide start(ServerLevel level, BlockPos min, BlockPos max, List<ServerPlayer> players, double dy, int moveTicks) {
        return start(level, min, max, players, 0.0, dy, 0.0, 0.0, moveTicks, true);
    }

    public static LiftRide start(ServerLevel level, BlockPos min, BlockPos max, List<ServerPlayer> players,
                                 double dx, double dy, double dz, double dip, int moveTicks) {
        return start(level, min, max, players, dx, dy, dz, dip, moveTicks, true);
    }

    /**
     * Полный 3D-рейс. dx/dz — сдвиг по горизонтали, dy — итоговый сдвиг по вертикали, dip — дополнительная
     * вертикальная «дуга» вниз (для шахты: кабина уходит в шахту и возвращается на уровень целевой кабины).
     * dip задаётся положительным числом, внутри он вычитается.
     *
     * @param volume true — прятать весь объём, в который входит кабина (шахта); false — только плиты входа/выхода.
     */
    public static LiftRide start(ServerLevel level, BlockPos min, BlockPos max, List<ServerPlayer> players,
                                 double dx, double dy, double dz, double dip, int moveTicks, boolean volume) {
        LiftRide ride = new LiftRide(level, min, max, dx, dy, dz, dip, moveTicks, volume);
        for (int x = min.getX(); x <= max.getX(); x++) for (int y = min.getY(); y <= max.getY(); y++)
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                BlockState state = level.getBlockState(new BlockPos(x, y, z));
                if (state.isAir()) continue;
                Entity e = display(level, state, x, y, z, 10);
                if (e != null) ride.shell.add(new Part(e));
            }
        // [важно] Камера едет на обычной высоте стоящего игрока относительно пола копии.
        // Пол копии — нижний блок коробки, его верх = min.y + 1; игрок всегда ставится туда,
        // даже если в момент старта он прыгал или стоял не на полу.
        double floorTop = min.getY() + 1.0;
        for (ServerPlayer p : players) {
            Entity seat = display(level, Blocks.AIR.defaultBlockState(), p.getX(), p.getY(), p.getZ(), 0);
            if (seat == null) continue;
            // Носитель ставим сразу на высоту крепления игрока (0.6): иначе positionRider опускает
            // игрока на эту величину внутрь пола на время калибровки, и камера стартует ниже обычного.
            seat.setPos(seat.getX(), floorTop + p.getVehicleAttachmentPoint(seat).y, seat.getZ());
            p.startRiding(seat, true);
            ride.riders.add(new Rider(p, seat, floorTop));
            RIDING.add(p.getUUID());
        }
        if (volume) ride.prepareVolume(); else ride.prepareCovers();
        ALL.add(ride);
        return ride;
    }

    /**
     * [новое] Объёмный режим: собирает непустые блоки в развёртке оболочки. Это и полы/потолки промежуточных
     * этажей, и стены, мимо которых проходит кабина. Блоки с block entity не трогаем: у них есть данные.
     */
    private void prepareVolume() {
        int x0 = min.getX() + Math.min(0, (int) Math.floor(dx));
        int x1 = max.getX() + Math.max(0, (int) Math.ceil(dx));
        int z0 = min.getZ() + Math.min(0, (int) Math.floor(dz));
        int z1 = max.getZ() + Math.max(0, (int) Math.ceil(dz));
        int y0 = min.getY() + Math.min(0, (int) Math.floor(dy)) - (int) Math.ceil(dip);
        int y1 = max.getY() + Math.max(0, (int) Math.ceil(dy));
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = y0; y <= y1; y++) {
            BlockPos p = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(p);
            if (!state.isAir() && !state.hasBlockEntity()) swept.add(p);
        }
    }

    /**
     * Старый режим: выбирает реальные плиты, через которые пройдёт камера. На старте — пол (едем вниз) или
     * потолок (едем вверх), на финише — потолок (вниз) или пол (вверх).
     */
    private void prepareCovers() {
        boolean down = dy < 0 || (dy == 0 && dip > 0);
        boolean up = dy > 0 || (dy == 0 && dip > 0);
        if (!down && !up) return;
        int tx = min.getX() + (int) Math.round(dx);
        int ty = min.getY() + (int) Math.round(dy);
        int tz = min.getZ() + (int) Math.round(dz);
        int srcY = down ? min.getY() : max.getY();
        int tgtY = up ? ty : ty + (max.getY() - min.getY());
        for (int x = min.getX() + 1; x < max.getX(); x++) for (int z = min.getZ() + 1; z < max.getZ(); z++) {
            srcCover.add(new BlockPos(x, srcY, z));
            tgtCover.add(new BlockPos(tx + (x - min.getX()), tgtY, tz + (z - min.getZ())));
        }
        targetBox = new AABB(tx, ty, tz, tx + (max.getX() - min.getX()) + 1.0,
                ty + (max.getY() - min.getY()) + 1.0, tz + (max.getZ() - min.getZ()) + 1.0);
    }

    /** Убирает плиты «на выходе». Возвращаются в end() (или досрочно, когда камера уже далеко). */
    private void hideSourceCover() {
        if (srcHidden || srcCover.isEmpty()) return;
        srcHidden = true;
        for (BlockPos p : srcCover) hide(p);
    }

    private void hideTargetCover() {
        if (tgtHidden || tgtCover.isEmpty() || targetBox == null) return;
        // Чужой игрок в целевой кабине не должен провалиться в скрытый пол: подождём, пока он выйдет.
        for (ServerPlayer q : level.getEntitiesOfClass(ServerPlayer.class, targetBox))
            if (riders.stream().noneMatch(r -> r.player == q)) return;
        tgtHidden = true;
        for (BlockPos p : tgtCover) hide(p);
    }

    private void hide(BlockPos p) {
        BlockState state = level.getBlockState(p);
        if (state.isAir() || state.hasBlockEntity()) return;
        if (level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS)) {
            hidden.put(p.asLong(), state);
            LiftRideStorage.get(level).remember(p, state);
        }
    }

    private void restore(BlockPos p) {
        BlockState state = hidden.remove(p.asLong());
        if (state == null) return;
        level.setBlock(p, state, Block.UPDATE_CLIENTS);
        LiftRideStorage.get(level).forget(p);
    }

    private void restoreSourceCover() {
        if (!srcHidden) return;
        srcHidden = false;
        for (BlockPos p : srcCover) restore(p);
    }

    private void restoreTargetCover() {
        if (!tgtHidden) return;
        tgtHidden = false;
        for (BlockPos p : tgtCover) restore(p);
    }

    private void restoreAll() {
        for (Long key : new ArrayList<>(hidden.keySet())) restore(BlockPos.of(key));
    }

    /** [новое] Прячем блок, только если рядом нет чужого игрока: иначе он провалится в открытый пол. */
    private boolean canHide(BlockPos p) {
        AABB box = new AABB(p).inflate(RESTORE_MARGIN);
        for (ServerPlayer q : level.getEntitiesOfClass(ServerPlayer.class, box))
            if (!RIDING.contains(q.getUUID())) return false;
        return true;
    }

    /**
     * [новое] Объёмный режим: блоки внутри текущего объёма кабины прячутся, а те, что кабина уже прошла,
     * возвращаются. Так камера никогда не пересекает настоящие блоки, и не нужно вручную знать, где этажи.
     */
    private void updateVolume(double s, double arc) {
        double baseX = min.getX() + dx * s;
        double baseY = min.getY() + dy * s + arc;
        double baseZ = min.getZ() + dz * s;
        double w = max.getX() - min.getX() + 1.0;
        double h = max.getY() - min.getY() + 1.0;
        double d = max.getZ() - min.getZ() + 1.0;
        AABB car = new AABB(baseX, baseY, baseZ, baseX + w, baseY + h, baseZ + d);
        AABB hideBox = car.inflate(0.05);
        AABB keepBox = car.inflate(RESTORE_MARGIN);
        for (BlockPos p : swept) {
            double cx = p.getX() + 0.5, cy = p.getY() + 0.5, cz = p.getZ() + 0.5;
            if (hidden.containsKey(p.asLong())) {
                if (!keepBox.contains(cx, cy, cz)) restore(p);
            } else if (hideBox.contains(cx, cy, cz) && canHide(p)) {
                hide(p);
            }
        }
    }

    private static Entity display(ServerLevel level, BlockState state, double x, double y, double z, int light) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:block_display");
        tag.put("block_state", NbtUtils.writeBlockState(state));
        CompoundTag brightness = new CompoundTag();
        brightness.putInt("block", light); brightness.putInt("sky", 0);
        tag.put("brightness", brightness);
        // [важно] Без teleport_duration Display.lerpTo() не интерполирует позицию, а телепортирует
        // сущность каждый клиентский тик — кабина и камера игрока двигались рывками 20 Гц.
        tag.putInt(Display.TAG_POS_ROT_INTERPOLATION_DURATION, DISPLAY_INTERP_TICKS);
        ListTag tags = new ListTag(); tags.add(StringTag.valueOf(TAG)); tag.put("Tags", tags);
        Entity e = EntityType.create(tag, level).orElse(null);
        if (e == null) return null;
        e.moveTo(x, y, z);
        level.addFreshEntity(e);
        return e;
    }

    /** Вызывать каждый серверный тик. */
    public void tick() {
        if (calib < CALIB_TICKS) {
            // Несколько тиков на то, чтобы клиент и сервер выставили игрока на сиденье; снимаем смещение.
            if (++calib == CALIB_TICKS) for (Rider r : riders) {
                r.ox = r.player.getX() - r.seat.getX();
                r.oy = r.player.getY() - r.seat.getY();
                r.oz = r.player.getZ() - r.seat.getZ();
            }
            return;
        }
        // Старый режим: скрываем плиту только когда клиент уже посадил игрока на сиденье (после калибровки).
        if (!volume && !srcHidden) hideSourceCover();
        if (t < moveTicks + SETTLE_TICKS) t++;
        double s = LiftProfile.progress(t, moveTicks); // smootherstep: без рывка на концах
        double arc = LiftProfile.arc(dip, s);
        if (volume) {
            updateVolume(s, arc);
        } else {
            if (srcHidden && s > 0.5) restoreSourceCover();
            if (!tgtHidden && s > 0.55) hideTargetCover();
        }
        for (Rider r : riders) {
            if (!r.player.isPassenger()) r.player.startRiding(r.seat, true);
            r.player.resetFallDistance();
            r.seat.setPos(r.x + dx * s - r.ox, r.y + dy * s + arc - r.oy, r.z + dz * s - r.oz);
        }
        for (Part p : shell) p.entity.setPos(p.x + dx * s, p.y + dy * s + arc, p.z + dz * s);
    }

    public boolean motionDone() { return calib >= CALIB_TICKS && t >= moveTicks + SETTLE_TICKS; }

    /** Конечная точка игрока (старт + полный сдвиг). */
    public double[] finalPos(ServerPlayer player) {
        for (Rider r : riders) if (r.player == player) return new double[]{r.x + dx, r.y + dy, r.z + dz};
        return new double[]{player.getX(), player.getY(), player.getZ()};
    }

    /** Снимает игроков с сидений и удаляет все дисплеи; безопасно вызывать повторно. */
    public void end() {
        if (!ALL.remove(this)) return;
        restoreAll();
        for (Rider r : riders) {
            r.player.stopRiding();
            r.player.resetFallDistance();
            RIDING.remove(r.player.getUUID());
            r.seat.discard();
        }
        for (Part p : shell) p.entity.discard();
        shell.clear();
    }

    /** После падения сервера возвращаем всё, что не успели вернуть в end(). */
    @SubscribeEvent public static void onStarted(ServerStartedEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) LiftRideStorage.get(level).restoreAll(level);
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
