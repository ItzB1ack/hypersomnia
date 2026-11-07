package com.hypersomnia.server;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.ObserverProgressData;
import com.hypersomnia.ObserverTuning;
import com.hypersomnia.network.ObserverSeenPayload;
import com.hypersomnia.network.TraceSyncPayload;
import com.hypersomnia.trace.TraceAttachments;
import com.hypersomnia.trace.TraceData;
import com.hypersomnia.trace.TraceTuning;
import com.hypersomnia.trace.TraceType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Расставляет следы осколка после пробуждения (только Верхний мир, только для этого игрока) и синхронизирует их. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class TraceHandler {
    private TraceHandler() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player, player.getData(TraceAttachments.TRACES));
    }

    /** Отдельное от следов событие: надпись появляется с задержкой, а не в момент сна. */
    public static void scheduleWriting(ServerPlayer player, ServerLevel level, BlockPos origin) {
        if (level.dimension() != Level.OVERWORLD || player.getData(ModAttachments.OBSERVER).stage() < 3
                || player.getRandom().nextFloat() >= TraceTuning.WRITING_CHANCE) return;
        player.getData(TraceAttachments.TRACES).scheduleWriting(origin,
                TraceTuning.WRITING_DELAY_MIN + player.getRandom().nextInt(TraceTuning.WRITING_DELAY_RANGE + 1));
    }

    @SubscribeEvent public static void tickWriting(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive() || player.isSleeping()
                || player.serverLevel().dimension() != Level.OVERWORLD) return;
        TraceData data = player.getData(TraceAttachments.TRACES);
        BlockPos origin = data.pendingWriting();
        if (origin == null || !player.serverLevel().isLoaded(origin)
                || player.blockPosition().distSqr(origin) > TraceTuning.WRITING_NEAR_RADIUS * TraceTuning.WRITING_NEAR_RADIUS) return;
        if (data.writingDelay() > 0) { data.tickWriting(); return; }
        int stage = player.getData(ModAttachments.OBSERVER).stage();
        if (stage < 3) { data.deferWriting(TraceTuning.WRITING_RETRY_TICKS); return; }
        TraceData.Entry found = tryPlace(player.serverLevel(), data, origin, TraceTuning.RADIUS, origin, TraceType.WRITING, player.getRandom());
        if (found == null) { data.deferWriting(TraceTuning.WRITING_RETRY_TICKS); return; }
        int first = stage >= 5 ? 5 : stage >= 4 ? 3 : 0;
        int count = stage >= 5 ? 3 : stage >= 4 ? 2 : 3;
        data.add(new TraceData.Entry(TraceType.WRITING.ordinal(), found.pos(), found.face(), first + player.getRandom().nextInt(count), 0, origin));
        data.clearWriting();
        send(player, data);
    }

    /** Вызывать из DreamTransition.exit() после возврата игрока. origin = где он уснул. */
    public static void onWake(ServerPlayer player, ServerLevel level, BlockPos origin) {
        place(player, level, origin, false);
    }

    /**
     * force = отладка: без стадии и шанса, любые виды, DEBUG_MIN..MAX следов. Возвращает, сколько следов добавлено.
     * Обычно: один след, чаще у знакомой точки маршрута рядом с кроватью, вид не повторяет прошлый.
     */
    public static int place(ServerPlayer player, ServerLevel level, BlockPos origin, boolean force) {
        if (level.dimension() != Level.OVERWORLD) return 0;
        ObserverProgressData prog = player.getData(ModAttachments.OBSERVER);
        int stage = prog.stage();
        RandomSource r = player.getRandom();
        if (!force) {
            if (stage < TraceTuning.MIN_STAGE || r.nextFloat() >= TraceTuning.WAKE_CHANCE) return 0;
        }
        TraceData data = player.getData(TraceAttachments.TRACES);
        // Где игрок недавно прогнал фигуру взглядом, там остаются её шаги (носками к кровати). Это и есть след этого сна.
        int[] seen = stage >= TraceTuning.SEEN_MIN_STAGE || force ? prog.peekSeen() : null;
        if (seen != null && (force || r.nextFloat() < TraceTuning.SEEN_SHARE)) {
            TraceData.Entry e = trySeen(level, data, seen, origin);
            if (e != null) {
                prog.removeSeen(seen[0], seen[1], seen[2]);
                data.add(e);
                send(player, data);
                return 1;
            }
        }
        int want = force ? TraceTuning.DEBUG_MIN + r.nextInt(TraceTuning.DEBUG_MAX - TraceTuning.DEBUG_MIN + 1)
                : TraceTuning.PER_WAKE_MIN + r.nextInt(TraceTuning.PER_WAKE_MAX - TraceTuning.PER_WAKE_MIN + 1);
        int lastType = data.all().isEmpty() ? -1 : data.all().get(data.all().size() - 1).type();
        int added = 0;
        for (int n = 0; n < want; n++) {
            TraceType type = pick(r, force ? Integer.MAX_VALUE : stage, lastType);
            if (type == null) break;
            TraceData.Entry e = null;
            BlockPos anchor = r.nextFloat() < TraceTuning.ROUTE_SHARE ? routeAnchor(prog, origin, r) : null;
            if (anchor != null) e = tryPlace(level, data, anchor, TraceTuning.ANCHOR_RADIUS, origin, type, r);
            if (e == null) e = tryPlace(level, data, origin, TraceTuning.RADIUS, origin, type, r);
            if (e != null) {
                data.add(e);
                lastType = e.type();
                added++;
            }
        }
        if (added > 0) send(player, data);
        return added;
    }

    // ------------------------------------------------------------------
    // «Где стоял — там след»
    // ------------------------------------------------------------------

    public static void handleSeen(ObserverSeenPayload pl, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) seen(player, pl.pos());
        });
    }

    /**
     * Клиент сообщил, где стояла фигура, которую игрок прогнал взглядом. Сервер только запоминает место:
     * Верхний мир, стадия SEEN_MIN_STAGE+, не дальше SEEN_MAX_DIST от игрока, не чаще SEEN_MIN_GAP_TICKS, чанк загружен.
     */
    public static boolean seen(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD) return false;
        ObserverProgressData prog = player.getData(ModAttachments.OBSERVER);
        if (prog.stage() < TraceTuning.SEEN_MIN_STAGE) return false;
        if (player.blockPosition().distSqr(pos) > (double) ObserverTuning.SEEN_MAX_DIST * ObserverTuning.SEEN_MAX_DIST) return false;
        long now = level.getGameTime();
        long last = prog.seenAcceptedAt();
        if (last != Long.MIN_VALUE && now >= last && now - last < ObserverTuning.SEEN_MIN_GAP_TICKS) return false;
        if (!level.isLoaded(pos)) return false;
        prog.noteSeen(pos.getX(), pos.getY(), pos.getZ(), now);
        return true;
    }

    /** Шаги на полу под местом (или в соседней клетке), носками к кровати bed. Чанк не загружен или места нет — null. */
    private static TraceData.Entry trySeen(ServerLevel level, TraceData data, int[] s, BlockPos bed) {
        BlockPos at = new BlockPos(s[0], s[1], s[2]);
        if (!level.isLoaded(at)) return null;
        int[][] around = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        for (int[] d : around) {
            for (int y = at.getY() + 1; y >= at.getY() - 3; y--) {
                BlockPos p = new BlockPos(at.getX() + d[0], y, at.getZ() + d[1]);
                BlockPos air = p.above();
                // Соседняя клетка может оказаться в незагруженном чанке или за высотой мира.
                if (level.isOutsideBuildHeight(p) || level.isOutsideBuildHeight(air) || !level.isLoaded(p)) continue;
                if (!level.getBlockState(p).isFaceSturdy(level, p, Direction.UP)) continue;
                if (!level.isEmptyBlock(air) || !level.getFluidState(air).isEmpty()) break;
                if (data.has(p, Direction.UP.ordinal())) break;
                return new TraceData.Entry(TraceType.STEPS.ordinal(), p, Direction.UP.ordinal(), 0, rotToward(p, bed));
            }
        }
        return null;
    }

    /**
     * Поворот наклейки на полу (шаг 45°), чтобы носки смотрели от from к to. Как в TraceRenderer: rot 0 — на север (-Z),
     * 2 — на запад, 4 — на юг, 6 — на восток.
     */
    public static int rotToward(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        if (dx == 0 && dz == 0) return 0;
        double phi = Math.atan2(-dx, -dz);
        return (int) Math.floorMod(Math.round(phi / (Math.PI / 4)), 8L);
    }

    /** Знакомая точка маршрута не дальше ROUTE_RADIUS от кровати (по весу привычки) или null. */
    private static BlockPos routeAnchor(ObserverProgressData prog, BlockPos origin, RandomSource r) {
        java.util.List<Integer> route = prog.routeList();
        int total = 0;
        BlockPos best = null;
        for (int i = 0; i + 3 < route.size(); i += 4) {
            long dx = (long) route.get(i) - origin.getX();
            long dz = (long) route.get(i + 2) - origin.getZ();
            if (Math.abs(dx) > TraceTuning.ROUTE_RADIUS || Math.abs(dz) > TraceTuning.ROUTE_RADIUS
                    || dx * dx + dz * dz > (long) TraceTuning.ROUTE_RADIUS * TraceTuning.ROUTE_RADIUS) continue;
            int w = Math.max(1, route.get(i + 3));
            total += w;
            if (r.nextInt(total) < w) best = new BlockPos(route.get(i), route.get(i + 1), route.get(i + 2));
        }
        return best;
    }

    public static void clear(ServerPlayer player) {
        TraceData data = player.getData(TraceAttachments.TRACES);
        data.clear();
        send(player, data);
    }

    public static void send(ServerPlayer player, TraceData data) {
        data.consumeDirty();
        PacketDistributor.sendToPlayer(player, new TraceSyncPayload(data.flat()));
    }

    /** Вид следа по весам стадии; прошлый вид (avoid) исключается, если есть другой. */
    private static TraceType pick(RandomSource r, int stage, int avoid) {
        int total = 0;
        int others = 0;
        for (TraceType t : TraceType.values()) {
            if (stage < t.minStage) continue;
            total += t.weight;
            if (t.ordinal() != avoid) others += t.weight;
        }
        if (total <= 0) return null;
        boolean skip = others > 0;
        int roll = r.nextInt(skip ? others : total);
        for (TraceType t : TraceType.values()) {
            if (stage < t.minStage || (skip && t.ordinal() == avoid)) continue;
            roll -= t.weight;
            if (roll < 0) return t;
        }
        return null;
    }

    /** Место вокруг center в радиусе radius, не ближе MIN_FROM_BED к кровати bed. */
    private static TraceData.Entry tryPlace(ServerLevel level, TraceData data, BlockPos origin, int radius, BlockPos bed,
                                            TraceType type, RandomSource r) {
        for (int i = 0; i < TraceTuning.ATTEMPTS; i++) {
            boolean indoorOnly = i < TraceTuning.ATTEMPTS / 2;
            int x = origin.getX() + r.nextInt(radius * 2 + 1) - radius;
            int z = origin.getZ() + r.nextInt(radius * 2 + 1) - radius;
            int bx = x - bed.getX();
            int bz = z - bed.getZ();
            if (bx * bx + bz * bz < TraceTuning.MIN_FROM_BED * TraceTuning.MIN_FROM_BED) continue;
            Direction wallDir = Direction.Plane.HORIZONTAL.getRandomDirection(r);
            for (int y = origin.getY() + 3; y >= origin.getY() - 4; y--) {
                BlockPos p = new BlockPos(x, y, z);
                Direction face = (type.kind == TraceType.Kind.WALL || type.kind == TraceType.Kind.TEXT) ? wallDir : Direction.UP;
                BlockPos air = p.relative(face);
                // Поиск не должен загружать соседние чанки: стена и воздух могут быть по разные стороны границы.
                if (level.isOutsideBuildHeight(p) || level.isOutsideBuildHeight(air)
                        || !level.isLoaded(p) || !level.isLoaded(air)) continue;
                if (type.kind == TraceType.Kind.WALL || type.kind == TraceType.Kind.TEXT) {
                    if (!level.getBlockState(p).isFaceSturdy(level, p, face) || !level.isEmptyBlock(air)) continue;
                    // стена комнаты: пол в пределах двух блоков под местом
                    if (!level.getBlockState(air.below()).isFaceSturdy(level, air.below(), Direction.UP)
                            && !level.getBlockState(air.below(2)).isFaceSturdy(level, air.below(2), Direction.UP)) continue;
                } else {
                    if (!level.getBlockState(p).isFaceSturdy(level, p, Direction.UP)) continue;
                }
                if (!level.isEmptyBlock(air) || !level.getFluidState(air).isEmpty()) continue;
                if (indoorOnly && level.canSeeSky(air)) continue;
                if (data.has(p, face.ordinal())) continue;
                return new TraceData.Entry(type.ordinal(), p, face.ordinal(), r.nextInt(type.variants), r.nextInt(8));
            }
        }
        return null;
    }
}