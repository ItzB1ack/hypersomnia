package com.hypersomnia.dream;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.portal.Portal;

import java.util.List;

/**
 * [новое] Петля в доме-зеркале (Immersive Portals).
 *
 * <p>Внешне дом остаётся ровно таким, каким его построил игрок. Внутри, на стадии 4, в конце
 * коридора ставится портал: он возвращает игрока на несколько блоков назад, и коридор
 * повторяется. На стадии 5 портал невидим — игрок просто снова оказывается в начале коридора.
 *
 * <p>Портал односторонний: назад через него пройти нельзя, поэтому из петли всегда есть выход.
 * Порталы ищутся по тегу и переиспользуются, поэтому петля не дублируется.
 *
 * <p>Коридор ищется по всему захваченному дому, на всех этажах в пределах LOOP_DEPTH_*:
 * это узкий проход (не шире LOOP_MAX_WIDTH) с полом, потолком 2..4 блока и стенами по бокам,
 * который упирается в тупик. Открытая земля, двор, широкая комната и спальня под это не
 * подходят, поэтому петля не появляется посреди комнаты.
 */
final class DreamMirrorLoop {
    private DreamMirrorLoop() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG = "hypersomnia:dream_mirror_loop";
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Direction.Axis[] AXES = {Direction.Axis.X, Direction.Axis.Z};
    private static final int[] SIGNS = {1, -1};
    /** Выше этого потолок не ищем: слишком высокое помещение — не коридор. */
    private static final int CEILING_SCAN = 6;

    /**
     * Найденный коридор: ось, направление к тупику, координата тупика вдоль оси, границы
     * поперёк, уровень пола, высота портала и длина петли назад.
     */
    private record Corridor(Direction.Axis axis, int sign, int a, int latStart, int latEnd,
                            int y, int height, int back) {
        int width() { return latEnd - latStart + 1; }
        double latCenter() { return (latStart + latEnd + 1) / 2.0; }
    }

    /**
     * Поставить или проверить петлю. false — чанки/сущности ещё не готовы, повторить в следующем тике.
     * true — петля стоит, переиспользована или невозможна (тогда её просто нет).
     */
    static boolean ensure(ServerLevel dream, BlockPos bed, int stage) {
        if (!chunksReady(dream, bed)) return false;

        AABB area = searchArea(bed);
        List<Portal> ours = dream.getEntitiesOfClass(Portal.class, area, p -> TAG.equals(p.portalTag));

        // Стадия ниже пороговой: петли быть не должно, в том числе оставшейся с прошлого сна.
        if (stage < DreamMirrorTuning.LOOP_MIN_STAGE) {
            for (Portal p : ours) p.discard();
            return true;
        }

        Corridor c = findCorridor(dream, bed);
        if (c == null) {
            LOGGER.info("DreamMirror loop: no suitable corridor near {}, loop skipped", bed);
            for (Portal p : ours) p.discard();
            return true;
        }

        Vec3 origin = centerOf(c.a, c.latCenter(), c.y, c.height, c.axis);
        Vec3 dest = centerOf(c.a - c.sign * c.back, c.latCenter(), c.y, c.height, c.axis);
        boolean visible = stage < DreamMirrorTuning.LOOP_INVISIBLE_STAGE;

        Portal keep = null;
        for (Portal p : ours) {
            if (keep == null && matches(p, dream, origin, dest, c.width(), c.height)) keep = p;
            else p.discard();
        }
        if (keep != null) {
            if (keep.isVisible() != visible) {
                keep.setIsVisible(visible);
                keep.reloadAndSyncToClient();
            }
            return true;
        }

        Portal portal = Portal.ENTITY_TYPE.create(dream);
        if (portal == null) return true;

        Vec3 dir = c.axis == Direction.Axis.X ? new Vec3(c.sign, 0, 0) : new Vec3(0, 0, c.sign);
        portal.setOriginPos(origin);
        portal.setOrientationAndSize(new Vec3(-dir.z, 0, dir.x), UP, c.width(), c.height);
        PortalAPI.setPortalTransformation(portal, dream.dimension(), dest, null, 1.0);
        portal.portalTag = TAG;
        portal.setDoRenderPlayer(false);
        portal.setInteractable(false);
        portal.setTeleportable(true);
        portal.setIsVisible(visible);
        portal.setCommandsOnTeleported(null);
        if (!portal.isPortalValid()) {
            portal.discard();
            return true;
        }
        dream.addFreshEntity(portal);
        LOGGER.info("DreamMirror loop: {} portal at {} -> {} ({}x{}, loop {} blocks, stage {})",
                visible ? "visible" : "invisible", BlockPos.containing(origin),
                BlockPos.containing(dest), c.width(), c.height(), c.back, stage);
        return true;
    }

    static String status(ServerLevel dream, BlockPos bed) {
        List<Portal> ours = dream.getEntitiesOfClass(Portal.class, searchArea(bed),
                p -> TAG.equals(p.portalTag));
        if (ours.isEmpty()) return "none";
        Portal p = ours.get(0);
        return (p.isVisible() ? "visible" : "invisible")
                + " " + BlockPos.containing(p.getOriginPos())
                + " -> " + BlockPos.containing(p.getDestPos())
                + ", " + p.getWidth() + "x" + p.getHeight();
    }

    // ------------------------------------------------------------------ поиск коридора

    /** Ищет самый длинный замкнутый коридор с тупиком в захваченной части дома. */
    private static Corridor findCorridor(ServerLevel level, BlockPos bed) {
        int r = DreamMirrorTuning.CHUNK_RADIUS;
        ChunkPos cp = new ChunkPos(bed);
        int minX = (cp.x - r) << 4;
        int maxX = ((cp.x + r) << 4) + 15;
        int minZ = (cp.z - r) << 4;
        int maxZ = ((cp.z + r) << 4) + 15;
        int minY = Math.max(level.getMinBuildHeight(), bed.getY() - DreamMirrorTuning.LOOP_DEPTH_BELOW);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, bed.getY() + DreamMirrorTuning.LOOP_DEPTH_ABOVE);

        // Сначала этаж кровати: там же обычно и коридор дома. Пещеры глубоко под домом
        // получают шанс только если рядом с кроватью ничего не нашлось.
        int near = DreamMirrorTuning.LOOP_NEAR_DEPTH;
        Corridor best = scanCorridors(level, minX, maxX, minZ, maxZ,
                Math.max(minY, bed.getY() - near), Math.min(maxY, bed.getY() + near));
        if (best != null) return best;
        return scanCorridors(level, minX, maxX, minZ, maxZ, minY, maxY);
    }

    private static Corridor scanCorridors(ServerLevel level, int minX, int maxX, int minZ, int maxZ,
                                          int minY, int maxY) {
        Corridor best = null;
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    for (Direction.Axis axis : AXES) {
                        for (int sign : SIGNS) {
                            Corridor c = corridorAt(level, x, y, z, axis, sign,
                                    minX, maxX, minZ, maxZ);
                            if (c != null && (best == null || c.back > best.back)) best = c;
                        }
                    }
                }
            }
        }
        return best;
    }

    /**
     * Проверяет, что клетка — тупик коридора в направлении {@code sign}: впереди стена,
     * по бокам стены, сверху потолок, а позади не меньше LOOP_MIN_LENGTH блоков такого же прохода.
     */
    private static Corridor corridorAt(ServerLevel level, int x, int y, int z,
                                       Direction.Axis axis, int sign,
                                       int minX, int maxX, int minZ, int maxZ) {
        int a = axis == Direction.Axis.X ? x : z;
        int lat = axis == Direction.Axis.X ? z : x;
        int aMin = axis == Direction.Axis.X ? minX : minZ;
        int aMax = axis == Direction.Axis.X ? maxX : maxZ;
        int latMin = axis == Direction.Axis.X ? minZ : minX;
        int latMax = axis == Direction.Axis.X ? maxZ : maxX;

        if (a <= aMin || a >= aMax) return null; // нужен запас на клетку впереди и позади
        if (!columnOpen(level, a, lat, axis, y)) return null;
        // Тупик: впереди стена на уровне ног и головы.
        if (passable(level, at(a + sign, y, lat, axis))) return null;
        if (passable(level, at(a + sign, y + 1, lat, axis))) return null;

        int[] run = lateralRun(level, a, lat, axis, y, latMin, latMax);
        if (run == null) return null;
        int lo = run[0], hi = run[1];
        int height = corridorHeight(level, a, lo, hi, axis, y);
        if (height < 2) return null;

        int back = 0;
        for (int i = 1; i <= DreamMirrorTuning.LOOP_BACK_MAX; i++) {
            int p = a - sign * i;
            if (p <= aMin || p >= aMax) break;
            if (!profileOpen(level, p, lo, hi, height, axis, y)) break;
            back = i;
        }
        if (back < DreamMirrorTuning.LOOP_MIN_LENGTH) return null;
        return new Corridor(axis, sign, a, lo, hi, y, height, back);
    }

    /**
     * Полоса свободного пола вокруг {@code lat}: расширяется в обе стороны, пока клетки
     * проходимы. null — проход шире LOOP_MAX_WIDTH (комната или улица) или упирается
     * в границу захвата (нельзя гарантировать, что там стена).
     */
    private static int[] lateralRun(ServerLevel level, int a, int lat, Direction.Axis axis, int y,
                                    int latMin, int latMax) {
        int lo = lat, hi = lat;
        while (lo > latMin && hi - lo + 1 < DreamMirrorTuning.LOOP_MAX_WIDTH
                && columnOpen(level, a, lo - 1, axis, y)) lo--;
        while (hi < latMax && hi - lo + 1 < DreamMirrorTuning.LOOP_MAX_WIDTH
                && columnOpen(level, a, hi + 1, axis, y)) hi++;
        if (lo > latMin && columnOpen(level, a, lo - 1, axis, y)) return null;
        if (hi < latMax && columnOpen(level, a, hi + 1, axis, y)) return null;
        return new int[]{lo, hi};
    }

    /**
     * Высота коридора: минимум из высоты потолка и высоты боковых стен, но не больше
     * LOOP_MAX_HEIGHT. Так потолок в 3 блока даёт портал в 3 блока, а не щель над ним.
     */
    private static int corridorHeight(ServerLevel level, int a, int latStart, int latEnd,
                                      Direction.Axis axis, int y) {
        int ceiling = ceilingHeight(level, a, latStart, latEnd, axis, y);
        // Потолок обязателен: открытая площадка или двор коридором не считаются.
        if (ceiling < 2 || ceiling > DreamMirrorTuning.LOOP_MAX_HEIGHT) return 0;
        int walls = Math.min(wallHeight(level, a, latStart - 1, axis, y),
                wallHeight(level, a, latEnd + 1, axis, y));
        return Math.min(walls, ceiling);
    }

    /** Сколько клеток подряд над полом сторона среза непроходима. */
    private static int wallHeight(ServerLevel level, int a, int lat, Direction.Axis axis, int y) {
        int h = 0;
        for (int i = 0; i < CEILING_SCAN; i++) {
            if (passable(level, at(a, y + i, lat, axis))) break;
            h = i + 1;
        }
        // Забор, стена или стекло занимают 1.5 блока: одной клетки мало, но игрок сквозь них не пройдёт.
        if (h == 1) h = 2;
        return h;
    }

    /** Сколько блоков воздуха над полом до потолка. */
    private static int ceilingHeight(ServerLevel level, int a, int latStart, int latEnd,
                                     Direction.Axis axis, int y) {
        for (int h = 0; h < CEILING_SCAN; h++) {
            for (int lat = latStart; lat <= latEnd; lat++) {
                if (!passable(level, at(a, y + h, lat, axis))) return h;
            }
        }
        return CEILING_SCAN;
    }

    /** Проход на координате a совпадает с найденным коридором: те же клетки свободны, стены на месте. */
    private static boolean profileOpen(ServerLevel level, int a, int latStart, int latEnd,
                                       int height, Direction.Axis axis, int y) {
        if (Math.min(wallHeight(level, a, latStart - 1, axis, y),
                wallHeight(level, a, latEnd + 1, axis, y)) < height) return false;
        for (int h = 0; h < height; h++) {
            for (int lat = latStart; lat <= latEnd; lat++) {
                if (!passable(level, at(a, y + h, lat, axis))) return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ блоки

    private static boolean columnOpen(ServerLevel level, int a, int lat, Direction.Axis axis, int y) {
        BlockPos feet = at(a, y, lat, axis);
        BlockPos floor = feet.below();
        return passable(level, feet) && passable(level, feet.above())
                && !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()
                && level.getFluidState(floor).isEmpty();
    }

    private static boolean passable(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                && level.getFluidState(p).isEmpty();
    }

    private static BlockPos at(int a, int y, int lat, Direction.Axis axis) {
        return axis == Direction.Axis.X ? new BlockPos(a, y, lat) : new BlockPos(lat, y, a);
    }

    private static Vec3 centerOf(int a, double latCenter, int y, int height, Direction.Axis axis) {
        double cy = y + height / 2.0;
        return axis == Direction.Axis.X
                ? new Vec3(a + 0.5, cy, latCenter)
                : new Vec3(latCenter, cy, a + 0.5);
    }

    // ------------------------------------------------------------------ портал

    private static boolean matches(Portal p, ServerLevel dream, Vec3 origin, Vec3 dest,
                                   double width, double height) {
        return p.isPortalValid()
                && dream.dimension().equals(p.getDestDim())
                && TAG.equals(p.portalTag)
                && p.getOriginPos().distanceToSqr(origin) < 1.0e-6
                && p.getDestPos().distanceToSqr(dest) < 1.0e-6
                && Math.abs(p.getWidth() - width) < 1.0e-6
                && Math.abs(p.getHeight() - height) < 1.0e-6;
    }

    // ------------------------------------------------------------------ чанки

    /** Портал ищем только в загруженных чанках; ждём, пока прогрузятся их сущности. */
    private static boolean chunksReady(ServerLevel dream, BlockPos bed) {
        int r = DreamMirrorTuning.CHUNK_RADIUS;
        ChunkPos cp = new ChunkPos(bed);
        for (int cx = cp.x - r; cx <= cp.x + r; cx++) {
            for (int cz = cp.z - r; cz <= cp.z + r; cz++) {
                dream.getChunk(cx, cz);
                if (!dream.areEntitiesLoaded(new ChunkPos(cx, cz).toLong())) return false;
            }
        }
        return true;
    }

    private static AABB searchArea(BlockPos bed) {
        int r = DreamMirrorTuning.CHUNK_RADIUS;
        ChunkPos cp = new ChunkPos(bed);
        int minX = (cp.x - r) << 4;
        int maxX = ((cp.x + r) << 4) + 16;
        int minZ = (cp.z - r) << 4;
        int maxZ = ((cp.z + r) << 4) + 16;
        int minY = bed.getY() - DreamMirrorTuning.VERTICAL_BELOW;
        int maxY = bed.getY() + DreamMirrorTuning.VERTICAL_ABOVE;
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
