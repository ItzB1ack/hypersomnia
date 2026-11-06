package com.hypersomnia.chase;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Поиск пути фигуры погони по блокам (A*, только сервер), как у ботов-навигаторов: к игроку по земле кратчайшей
 * дорогой, а не по его следу, поэтому бег по кругу и петли не спасают.
 * <p>
 * Тело — 1×2 блока, как у игрока. Ходы: 8 направлений (по диагонали без срезания углов), шаг на блок вверх, спрыгнуть
 * до DROP_MAX блоков, перепрыгнуть яму в 1-2 блока, лестницы и лианы вверх-вниз, вода (вплавь, дороже). Закрытая
 * дверь проходима, но дорого (DOOR_COST): если обход не намного длиннее, пойдёт в обход, иначе упрётся в дверь и будет
 * стучать (ChaseHandler, BLOCKED). Лава, огонь, магма, кактус, ягоды, паутина, рыхлый снег — нельзя; заборы и стены —
 * нельзя ни пройти, ни встать на них. Незагруженный чанк — стена (чанки не грузит).
 * <p>
 * Один объект — один тик сервера: кэширует блоки, поэтому между тиками его не хранят (мир меняется).
 * Работа ограничена: не больше ChaseTuning.PATH_MAX_NODES клеток за поиск и не дальше PATH_RANGE от старта.
 */
final class ChasePath {
    /** Найденный путь: точки (центр клетки на высоте пола) и клетки ног (по ним проверяют двери и стены на ходу). */
    static final class Route {
        final List<Vec3> points;
        final List<BlockPos> cells;
        /** Путь к точке следа (а не к игроку): индекс этой точки в следе погони, иначе -1. */
        int trailIndex = -1;
        /** Цель недостижима: путь к ближайшей достижимой клетке. */
        boolean partial;

        Route(List<Vec3> points, List<BlockPos> cells) {
            this.points = points;
            this.cells = cells;
        }

        int size() {
            return points.size();
        }
    }

    // Что в клетке
    private static final byte UNKNOWN = 0;
    private static final byte OPEN = 1;    // пусто (трава, цветы, факел, открытая дверь)
    private static final byte LOW = 2;     // низкий блок внизу клетки (плита, ковёр, снег): стоят на нём
    private static final byte SOLID = 3;   // твёрдое
    private static final byte DOOR = 4;    // закрытая дверь
    private static final byte CLIMB = 5;   // лестница, лиана, леса
    private static final byte WATER = 6;   // вода без твёрдого блока
    private static final byte DANGER = 7;  // лава, огонь, магма, кактус, паутина...

    /** Ниже этого верх блока в клетке — «низкий» (на нём стоят в той же клетке). */
    private static final double LOW_MAX = 0.6;
    /** Пол выше этого над низом клетки — голове нужна ещё одна свободная клетка. */
    private static final double LOW_HEAD = 0.2;
    /** На ровном ходу перепад пола не больше этого (плиты, тропинки), прыжок на блок — не больше JUMP_MAX. */
    private static final double STEP_FLAT = 0.6;
    private static final double JUMP_MAX = 1.25;

    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DZ = {0, 0, 1, -1, 1, -1, 1, -1};

    private final ServerLevel level;
    private final Long2ByteOpenHashMap kinds = new Long2ByteOpenHashMap();
    private final Long2DoubleOpenHashMap tops = new Long2DoubleOpenHashMap();
    private final Long2DoubleOpenHashMap floors = new Long2DoubleOpenHashMap();
    private final BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
    private LevelChunk chunk;
    private int chunkX = Integer.MIN_VALUE;
    private int chunkZ = Integer.MIN_VALUE;
    /** Сколько клеток открыл последний поиск (для отладки и тестов). */
    int lastVisited;

    ChasePath(ServerLevel level) {
        this.level = level;
        floors.defaultReturnValue(Double.NEGATIVE_INFINITY);
    }

    // ------------------------------------------------------------------
    // Клетки
    // ------------------------------------------------------------------

    private byte kind(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        byte k = kinds.get(key);
        if (k != UNKNOWN) return k;
        k = compute(x, y, z, key);
        kinds.put(key, k);
        return k;
    }

    private byte compute(int x, int y, int z, long key) {
        if (level.isOutsideBuildHeight(y)) return SOLID;
        int cx = x >> 4;
        int cz = z >> 4;
        if (cx != chunkX || cz != chunkZ) {
            chunk = level.getChunkSource().getChunkNow(cx, cz);
            chunkX = cx;
            chunkZ = cz;
        }
        if (chunk == null) return SOLID;
        mpos.set(x, y, z);
        BlockState s = chunk.getBlockState(mpos);
        if (s.isAir()) return OPEN;
        if (s.getFluidState().is(FluidTags.LAVA)) return DANGER;
        Block b = s.getBlock();
        if (s.is(BlockTags.FIRE) || s.is(BlockTags.CAMPFIRES) || b == Blocks.MAGMA_BLOCK || b == Blocks.CACTUS
                || b == Blocks.SWEET_BERRY_BUSH || b == Blocks.COBWEB || b == Blocks.POWDER_SNOW
                || b == Blocks.WITHER_ROSE) return DANGER;
        if (b instanceof DoorBlock) return s.getValue(DoorBlock.OPEN) ? OPEN : DOOR;
        if (b instanceof TrapDoorBlock && s.getValue(TrapDoorBlock.OPEN)) return OPEN;
        if (s.is(BlockTags.CLIMBABLE)) return CLIMB;
        VoxelShape shape = s.getCollisionShape(level, mpos);
        if (shape.isEmpty()) return s.getFluidState().is(FluidTags.WATER) ? WATER : OPEN;
        double top = shape.max(Direction.Axis.Y);
        tops.put(key, top);
        return top <= LOW_MAX && shape.min(Direction.Axis.Y) < 0.01 ? LOW : SOLID;
    }

    /** Через клетку можно пройти телом (ноги или голова). Закрытая дверь — тоже, но дорого. */
    private static boolean passable(byte k) {
        return k == OPEN || k == CLIMB || k == WATER || k == DOOR;
    }

    /** Свободно по-настоящему (для углов, прыжков и падения): без дверей и низких блоков. */
    private static boolean free(byte k) {
        return k == OPEN || k == CLIMB || k == WATER;
    }

    /** Высота пола, если в клетке (ноги в ней, голова выше) можно стоять, иначе NaN. */
    double floor(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        double v = floors.get(key);
        if (v != Double.NEGATIVE_INFINITY) return v;
        v = computeFloor(x, y, z);
        floors.put(key, v);
        return v;
    }

    private double computeFloor(int x, int y, int z) {
        byte feet = kind(x, y, z);
        if (feet == SOLID || feet == DANGER || feet == UNKNOWN) return Double.NaN;
        byte head = kind(x, y + 1, z);
        if (!passable(head)) return Double.NaN;
        if (feet == LOW) {
            double top = tops.get(BlockPos.asLong(x, y, z));
            if (top > LOW_HEAD && !free(kind(x, y + 2, z))) return Double.NaN;
            return y + top;
        }
        if (feet == CLIMB || feet == WATER) return y;
        byte below = kind(x, y - 1, z);
        if (below == SOLID) {
            double top = tops.get(BlockPos.asLong(x, y - 1, z));
            // заборы и стены (1.5) — не пол; низкие сплошные (снег 6-7 слоёв) — пол
            return top > LOW_MAX && top <= 1.0 ? y - 1 + top : Double.NaN;
        }
        if (below == CLIMB) return y;
        return Double.NaN;
    }

    private boolean door(int x, int y, int z) {
        return kind(x, y, z) == DOOR || kind(x, y + 1, z) == DOOR;
    }

    /** Клетка ног и головы свободна сейчас (по ней можно пройти, пусть и без пола). */
    private boolean clearColumn(int x, int y, int z) {
        return free(kind(x, y, z)) && free(kind(x, y + 1, z));
    }

    /** Ближайшая клетка, где можно стоять, у точки p: сначала на её уровне, потом ниже (прыжок), потом выше. */
    BlockPos locate(Vec3 p, int down) {
        int x = Mth.floor(p.x);
        int z = Mth.floor(p.z);
        int y = Mth.floor(p.y + 0.2);
        if (!Double.isNaN(floor(x, y, z))) return new BlockPos(x, y, z);
        for (int d = 1; d <= down; d++) if (!Double.isNaN(floor(x, y - d, z))) return new BlockPos(x, y - d, z);
        if (!Double.isNaN(floor(x, y + 1, z))) return new BlockPos(x, y + 1, z);
        return null;
    }

    /** Под точкой есть пол на её уровне или на блок выше-ниже (для прямого рывка: не по воздуху над ямой). */
    boolean floorNear(Vec3 p) {
        int x = Mth.floor(p.x);
        int z = Mth.floor(p.z);
        int y = Mth.floor(p.y + 0.2);
        return !Double.isNaN(floor(x, y, z)) || !Double.isNaN(floor(x, y - 1, z)) || !Double.isNaN(floor(x, y + 1, z));
    }

    /** По прямой от a до b всё время есть пол (шаг 0.5 блока). */
    boolean floorAlong(Vec3 a, Vec3 b) {
        double len = Math.hypot(b.x - a.x, b.z - a.z);
        int n = Math.max(1, (int) Math.ceil(len / 0.5));
        for (int i = 1; i <= n; i++) {
            if (!floorNear(a.lerp(b, (double) i / n))) return false;
        }
        return true;
    }

    /** Клетка сейчас — закрытая дверь (ноги или голова). */
    boolean closedDoor(BlockPos cell) {
        return door(cell.getX(), cell.getY(), cell.getZ());
    }

    /** Клетку перегородили (поставили блок, закрыли калитку): идти по старому пути дальше нельзя. */
    boolean walledNow(BlockPos cell) {
        byte feet = kind(cell.getX(), cell.getY(), cell.getZ());
        byte head = kind(cell.getX(), cell.getY() + 1, cell.getZ());
        return feet == SOLID || feet == DANGER || head == SOLID || head == LOW || head == DANGER;
    }

    // ------------------------------------------------------------------
    // A*
    // ------------------------------------------------------------------

    private static final class Node {
        final int x, y, z;
        final double floor;
        double g;
        Node parent;
        boolean closed;
        /** Пришли сюда подъёмом по стене от parent (build добавляет вертикальную точку). */
        boolean scramble;

        Node(int x, int y, int z, double floor) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.floor = floor;
        }
    }

    private record Entry(Node node, double f, double g) {}

    /**
     * Путь из from к цели to. Цель — клетка, где можно стоять, под to (игрок в прыжке — его пол) или рядом с ней.
     * null — нет пути в пределах работы, или старт/цель не на земле.
     */
    Route find(Vec3 from, Vec3 to, int maxNodes) {
        return find(from, to, maxNodes, false);
    }

    /** partial = true: цель недостижима — вернёт путь к ближайшей к ней достигнутой клетке (Route.partial). */
    Route find(Vec3 from, Vec3 to, int maxNodes, boolean partial) {
        lastVisited = 0;
        BlockPos start = locate(from, 2);
        BlockPos goal = locate(to, 3);
        if (start == null || goal == null) return null;
        int gx = goal.getX(), gy = goal.getY(), gz = goal.getZ();
        int sx = start.getX(), sy = start.getY(), sz = start.getZ();
        Long2ObjectOpenHashMap<Node> nodes = new Long2ObjectOpenHashMap<>();
        PriorityQueue<Entry> open = new PriorityQueue<>((a, b) -> a.f != b.f ? Double.compare(a.f, b.f)
                : Double.compare(b.g, a.g));
        Node s0 = new Node(sx, sy, sz, floor(sx, sy, sz));
        nodes.put(BlockPos.asLong(sx, sy, sz), s0);
        open.add(new Entry(s0, h(sx, sy, sz, gx, gy, gz), 0));
        Node best = s0;
        double bestH = h(sx, sy, sz, gx, gy, gz);
        int range = ChaseTuning.PATH_RANGE;
        while (!open.isEmpty()) {
            Entry e = open.poll();
            Node n = e.node;
            if (n.closed || e.g != n.g) continue;
            n.closed = true;
            if (++lastVisited > maxNodes) return partial ? build(best, true) : null;
            if (Math.abs(n.x - gx) <= 1 && Math.abs(n.z - gz) <= 1 && Math.abs(n.y - gy) <= 1) return build(n, false);
            if (partial) {
                double hn = h(n.x, n.y, n.z, gx, gy, gz);
                if (hn < bestH) {
                    best = n;
                    bestH = hn;
                }
            }
            for (int d = 0; d < 8; d++) {
                int dx = DX[d], dz = DZ[d];
                int nx = n.x + dx, nz = n.z + dz;
                if (Math.abs(nx - sx) > range || Math.abs(nz - sz) > range) continue;
                if (dx != 0 && dz != 0) {
                    // по диагонали: только по ровному и без срезания углов
                    if (!clearColumn(n.x + dx, n.y, n.z) || !clearColumn(n.x, n.y, n.z + dz)) continue;
                    double f = floor(nx, n.y, nz);
                    if (!Double.isNaN(f) && Math.abs(f - n.floor) <= STEP_FLAT && !door(nx, n.y, nz)) {
                        relax(nodes, open, n, nx, n.y, nz, f, ChaseTuning.PATH_DIAG_COST + extra(nx, n.y, nz),
                                gx, gy, gz);
                    }
                    continue;
                }
                double f = floor(nx, n.y, nz);
                if (!Double.isNaN(f) && f - n.floor <= STEP_FLAT) {
                    relax(nodes, open, n, nx, n.y, nz, f, 1.0 + extra(nx, n.y, nz), gx, gy, gz);
                    continue;
                }
                // на блок вверх (прыжок): над головой свободно
                double up = floor(nx, n.y + 1, nz);
                if (!Double.isNaN(up) && up - n.floor <= JUMP_MAX && free(kind(n.x, n.y + 2, n.z))) {
                    relax(nodes, open, n, nx, n.y + 1, nz, up, 1.0 + ChaseTuning.PATH_UP_COST + extra(nx, n.y + 1, nz),
                            gx, gy, gz);
                    continue;
                }
                // стена в 2-3 блока: лезет вдоль неё в своей клетке и встаёт наверху
                int sk = scrambleHeight(n, nx, nz);
                if (sk > 0) {
                    int sy2 = n.y + sk;
                    if (relax(nodes, open, n, nx, sy2, nz, floor(nx, sy2, nz),
                            1.0 + sk * ChaseTuning.PATH_SCRAMBLE_COST + extra(nx, sy2, nz), gx, gy, gz)) {
                        nodes.get(BlockPos.asLong(nx, sy2, nz)).scramble = true;
                    }
                    continue;
                }
                if (!clearColumn(nx, n.y, nz)) continue;
                // спрыгнуть
                boolean landed = false;
                for (int k = 1; k <= ChaseTuning.PATH_DROP_MAX; k++) {
                    int ny = n.y - k;
                    double df = floor(nx, ny, nz);
                    if (!Double.isNaN(df)) {
                        relax(nodes, open, n, nx, ny, nz, df, 1.0 + ChaseTuning.PATH_DROP_COST * k + extra(nx, ny, nz),
                                gx, gy, gz);
                        landed = true;
                        break;
                    }
                    if (!free(kind(nx, ny, nz))) break;
                }
                if (landed) continue;
                // перепрыгнуть яму в 1-2 блока (по прямой; над ямой и над головой свободно)
                if (!free(kind(n.x, n.y + 2, n.z)) || !free(kind(nx, n.y + 2, nz))) continue;
                for (int gap = 2; gap <= 3; gap++) {
                    int lx = n.x + dx * gap, lz = n.z + dz * gap;
                    if (Math.abs(lx - sx) > range || Math.abs(lz - sz) > range) break;
                    double lf = floor(lx, n.y, lz);
                    if (!Double.isNaN(lf) && Math.abs(lf - n.floor) <= STEP_FLAT && free(kind(lx, n.y + 2, lz))) {
                        relax(nodes, open, n, lx, n.y, lz, lf, gap + ChaseTuning.PATH_JUMP_COST, gx, gy, gz);
                        break;
                    }
                    if (!clearColumn(lx, n.y, lz) || !free(kind(lx, n.y + 2, lz))) break;
                }
            }
            // по лестнице или в воде — вверх и вниз
            byte here = kind(n.x, n.y, n.z);
            if (here == CLIMB || here == WATER) {
                double up = floor(n.x, n.y + 1, n.z);
                if (!Double.isNaN(up) && free(kind(n.x, n.y + 2, n.z))) {
                    relax(nodes, open, n, n.x, n.y + 1, n.z, up, ChaseTuning.PATH_CLIMB_COST, gx, gy, gz);
                }
            }
            byte below = kind(n.x, n.y - 1, n.z);
            if (below == CLIMB || below == WATER) {
                double dn = floor(n.x, n.y - 1, n.z);
                if (!Double.isNaN(dn)) relax(nodes, open, n, n.x, n.y - 1, n.z, dn, ChaseTuning.PATH_CLIMB_COST, gx, gy, gz);
            }
        }
        return partial ? build(best, true) : null;
    }

    private double extra(int x, int y, int z) {
        double c = 0;
        if (kind(x, y, z) == WATER) c += ChaseTuning.PATH_WATER_COST;
        if (door(x, y, z)) c += ChaseTuning.PATH_DOOR_COST;
        return c;
    }

    /** Высота стены (2..PATH_SCRAMBLE_MAX) у соседней колонки, на которую можно вылезти из клетки n; иначе 0. */
    private int scrambleHeight(Node n, int nx, int nz) {
        if (ChaseTuning.PATH_SCRAMBLE_MAX < 2 || kind(nx, n.y + 1, nz) != SOLID
                || !free(kind(n.x, n.y + 2, n.z))) return 0;
        for (int k = 2; k <= ChaseTuning.PATH_SCRAMBLE_MAX; k++) {
            if (!free(kind(n.x, n.y + k + 1, n.z))) return 0;
            if (!Double.isNaN(floor(nx, n.y + k, nz))) return k;
        }
        return 0;
    }

    private static double h(int x, int y, int z, int gx, int gy, int gz) {
        int dx = Math.abs(x - gx), dz = Math.abs(z - gz);
        int lo = Math.min(dx, dz), hi = Math.max(dx, dz);
        double flat = hi + (ChaseTuning.PATH_DIAG_COST - 1.0) * lo;
        return ChaseTuning.PATH_HEURISTIC * (flat + ChaseTuning.PATH_DROP_COST * Math.abs(y - gy));
    }

    private static boolean relax(Long2ObjectOpenHashMap<Node> nodes, PriorityQueue<Entry> open, Node from,
                                 int x, int y, int z, double floor, double cost, int gx, int gy, int gz) {
        long key = BlockPos.asLong(x, y, z);
        Node m = nodes.get(key);
        double g = from.g + cost;
        if (m == null) {
            m = new Node(x, y, z, floor);
            nodes.put(key, m);
        } else if (m.closed || g >= m.g) {
            return false;
        }
        m.g = g;
        m.parent = from;
        m.scramble = false;
        open.add(new Entry(m, g + h(x, y, z, gx, gy, gz), g));
        return true;
    }

    private static Route build(Node end, boolean partial) {
        ArrayList<Vec3> pts = new ArrayList<>();
        ArrayList<BlockPos> cells = new ArrayList<>();
        for (Node n = end; n != null; n = n.parent) {
            pts.add(new Vec3(n.x + 0.5, n.floor, n.z + 0.5));
            cells.add(new BlockPos(n.x, n.y, n.z));
            if (n.scramble && n.parent != null) {
                // подъём по стене: сначала вверх в клетке снизу, потом шаг на верх (не сквозь угол блока)
                pts.add(new Vec3(n.parent.x + 0.5, n.floor, n.parent.z + 0.5));
                cells.add(new BlockPos(n.parent.x, n.y, n.parent.z));
            }
        }
        Collections.reverse(pts);
        Collections.reverse(cells);
        // первая точка — клетка старта: он уже в ней
        if (pts.size() > 1) {
            pts.remove(0);
            cells.remove(0);
        }
        Route r = new Route(pts, cells);
        r.partial = partial;
        return r;
    }
}
