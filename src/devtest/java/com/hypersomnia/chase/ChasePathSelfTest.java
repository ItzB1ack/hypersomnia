package com.hypersomnia.chase;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Проверки A* погони на искусственных сетках (N16): путь не идёт сквозь стены, опасные клетки и
 * закрытые двери без нужды; ямы перепрыгиваются, ступени и стены-уступы берутся; тупики дают
 * partial-путь или null, а не зависание; лимит клеток срабатывает. Плюс геометрия старта
 * (ChaseSpawn: ровно SPAWN_BEHIND_DIST за спиной, затем развороты) и таблицы ChaseTuning.
 * <p>
 * Сетка задаётся строками (z — строка, x — столбец): {@code #} стена 1×2, {@code .} пол,
 * {@code D} закрытая дверь, {@code P} яма, {@code ~} вода, {@code _} плита, {@code ^} лестница,
 * {@code s} ступень в один блок, {@code W} стена в три блока (лезет сверху), {@code L} лава.
 */
public final class ChasePathSelfTest {
    private static int checks;
    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    public static void main(String[] args) {
        maps();
        timeoutAndFallback();
        spawn();
        tuning();
        System.out.println("ChasePathSelfTest: " + checks + " checks passed.");
    }

    // ------------------------------------------------------------------
    // Карты
    // ------------------------------------------------------------------

    private static void maps() {
        MapGrid straight = new MapGrid(
                "#######",
                "#.....#",
                "#.....#",
                "#.....#",
                "#######");
        ChasePath.Route r = route(straight, 1, 1, 5, 3);
        validate(r, straight, "straight");
        check(r.size() >= 3, "straight: short path");
        check(!r.partial, "straight: must be complete");

        MapGrid gap = new MapGrid(
                "#########",
                "#...#...#",
                "#...#...#",
                "#.......#",
                "#########");
        r = route(gap, 1, 1, 7, 1);
        validate(r, gap, "gap");
        check(cell(r, 4, 3) != null, "gap: path must use the opening");
        check(cell(r, 4, 1) == null && cell(r, 4, 2) == null, "gap: path goes through wall");

        MapGrid maze = new MapGrid(
                "###########",
                "#.........#",
                "#.#######.#",
                "#.#.....#.#",
                "#.#.###.#.#",
                "#.#.#...#.#",
                "#.#.#####.#",
                "#.........#",
                "###########");
        r = route(maze, 1, 1, 5, 7);
        validate(r, maze, "maze");
        check(r.size() >= 8, "maze: path suspiciously short");
        check(!r.partial, "maze: must be complete");

        MapGrid sealed = new MapGrid(
                "#########",
                "#.......#",
                "#.#####.#",
                "#.#...#.#",
                "#.#####.#",
                "#.......#",
                "#########");
        ChasePath path = new ChasePath(sealed);
        check(path.find(at(1, 1), at(4, 3), ChaseTuning.PATH_MAX_NODES) == null,
                "sealed: complete path must not exist");
        ChasePath.Route partial = path.find(at(1, 1), at(4, 3), ChaseTuning.PATH_MAX_NODES, true);
        check(partial != null && partial.partial, "sealed: partial fallback expected");
        validate(partial, sealed, "sealed");
        check(cell(partial, 4, 3) == null, "sealed: partial path entered the closed room");

        MapGrid deadEnd = new MapGrid(
                "#########",
                "#.#.....#",
                "#.#.###.#",
                "#.......#",
                "#########");
        r = route(deadEnd, 1, 1, 7, 1);
        validate(r, deadEnd, "dead-end");
        check(!r.partial && r.size() > 6, "dead-end: must backtrack around the wall");

        MapGrid doorDetour = new MapGrid(
                "###########",
                "#....D....#",
                "#.#######.#",
                "#.........#",
                "###########");
        r = route(doorDetour, 1, 1, 9, 1);
        validate(r, doorDetour, "door-detour");
        check(cell(r, 5, 1) == null, "door-detour: must avoid a closed door when a detour is shorter");

        MapGrid doorOnly = new MapGrid(
                "#######",
                "#..D..#",
                "#######");
        r = route(doorOnly, 1, 1, 5, 1);
        validate(r, doorOnly, "door-only");
        check(cell(r, 3, 1) != null, "door-only: closed door is the only way");

        MapGrid pit = new MapGrid(
                "#########",
                "#...P...#",
                "#...P...#",
                "#.......#",
                "#########");
        r = route(pit, 1, 1, 7, 1);
        validate(r, pit, "pit");
        check(cell(r, 4, 1) == null && cell(r, 4, 2) == null, "pit: path stepped into the pit");

        MapGrid corner = new MapGrid(
                "#######",
                "#.....#",
                "#.#...#",
                "#..#..#",
                "#.....#",
                "#.....#",
                "#######");
        r = route(corner, 1, 1, 5, 5);
        validate(r, corner, "diagonal-corner");

        MapGrid stairs = new MapGrid(
                "#########",
                "#..s....#",
                "#.......#",
                "#.......#",
                "#########");
        r = route(stairs, 1, 1, 7, 1);
        validate(r, stairs, "stairs");
        check(anyY(r, 2), "stairs: path must step up onto the block");

        MapGrid tall = new MapGrid(
                "#########",
                "#..W....#",
                "#..W....#",
                "#..W....#",
                "#########");
        r = route(tall, 1, 1, 7, 1);
        validate(r, tall, "tall-wall");
        check(anyY(r, 4), "tall-wall: path must scramble over the wall");

        MapGrid water = new MapGrid(
                "#########",
                "#..~~~..#",
                "#..~~~..#",
                "#..~~~..#",
                "#########");
        r = route(water, 1, 1, 7, 1);
        validate(r, water, "water");
        check(anyKind(r, water, ChasePath.WATER), "water: path must cross the water");

        MapGrid slab = new MapGrid(
                "#########",
                "#.._....#",
                "#.._....#",
                "#.._....#",
                "#########");
        r = route(slab, 1, 1, 7, 1);
        validate(r, slab, "slab");
        check(anyKind(r, slab, ChasePath.LOW), "slab: path must step onto the low block");

        MapGrid lava = new MapGrid(
                "#########",
                "#..L....#",
                "#.......#",
                "#.......#",
                "#########");
        r = route(lava, 1, 1, 7, 1);
        validate(r, lava, "lava");
        check(!anyKind(r, lava, ChasePath.DANGER), "lava: path crossed danger");

        MapGrid noStart = new MapGrid(
                "#####",
                "#####",
                "#####");
        check(new ChasePath(noStart).find(at(1, 1), at(3, 1), ChaseTuning.PATH_MAX_NODES) == null,
                "no-start: wall cell must not produce a route");
    }

    // ------------------------------------------------------------------
    // Лимит поиска и запасной путь
    // ------------------------------------------------------------------

    private static void timeoutAndFallback() {
        MapGrid maze = new MapGrid(
                "###########",
                "#.........#",
                "#.#######.#",
                "#.#.....#.#",
                "#.#.###.#.#",
                "#.#.#...#.#",
                "#.#.#####.#",
                "#.........#",
                "###########");
        ChasePath path = new ChasePath(maze);
        ChasePath.Route limited = path.find(at(1, 1), at(5, 7), 1, true);
        check(limited != null && limited.partial, "timeout: partial route expected");
        check(path.lastVisited <= 2, "timeout: search exceeded its node budget: " + path.lastVisited);
        validate(limited, maze, "timeout");
        check(path.find(at(1, 1), at(5, 7), 0, true) != null, "timeout: zero budget must still return fallback");
        check(path.find(at(1, 1), at(5, 7), 0, false) == null, "timeout: zero budget must not invent a path");

        // Тупик: старт в нише, выхода нет — partial ведёт к ближайшей достижимой клетке, полный путь null.
        MapGrid niche = new MapGrid(
                "#########",
                "#.#.....#",
                "#.#.###.#",
                "###.#...#",
                "#.......#",
                "#########");
        path = new ChasePath(niche);
        check(path.find(at(1, 1), at(7, 1), ChaseTuning.PATH_MAX_NODES) == null,
                "dead end: complete path must not exist");
        ChasePath.Route fallback = path.find(at(1, 1), at(7, 1), ChaseTuning.PATH_MAX_NODES, true);
        check(fallback != null && fallback.partial, "dead end: fallback route expected");
        validate(fallback, niche, "dead-end-fallback");
    }

    // ------------------------------------------------------------------
    // Старт за спиной
    // ------------------------------------------------------------------

    private static void spawn() {
        check(ChaseTuning.SPAWN_BEHIND_DIST == 15.0, "spawn: SPAWN_BEHIND_DIST must stay 15");
        check(ChaseSpawn.MIN_BEHIND_DIST > 0 && ChaseSpawn.MIN_BEHIND_DIST < ChaseTuning.SPAWN_BEHIND_DIST,
                "spawn: minimum distance must be below the normal distance");

        Vec3 north = new Vec3(0, 0, -1);
        List<Vec3> behind = ChaseSpawn.behind(north, ChaseTuning.SPAWN_BEHIND_DIST);
        check(!behind.isEmpty(), "spawn: no behind candidates");
        Vec3 first = behind.get(0);
        check(Math.abs(first.length() - ChaseTuning.SPAWN_BEHIND_DIST) < 1.0E-6, "spawn: first point is not 15 blocks away");
        check(Math.abs(first.dot(north) + ChaseTuning.SPAWN_BEHIND_DIST) < 1.0E-6, "spawn: first point is not behind the player");
        check(Math.abs(first.y) < 1.0E-9, "spawn: behind offset must stay horizontal");

        int firstRing = ChaseSpawn.BEHIND_ANGLES.length;
        for (int i = 0; i < firstRing; i++) {
            Vec3 p = behind.get(i);
            check(Math.abs(p.length() - ChaseTuning.SPAWN_BEHIND_DIST) < 1.0E-6, "spawn: first ring distance changed");
            double angle = Math.toDegrees(Math.acos(clamp(p.dot(north) / -ChaseTuning.SPAWN_BEHIND_DIST)));
            check(angle <= 60.0 + 1.0E-6, "spawn: candidate outside the rear cone: " + angle);
        }
        check(behind.size() > firstRing, "spawn: no closer/farther fallback");
        check(ChaseSpawn.BEHIND_DISTANCE_STEPS[0] == 0.0, "spawn: first distance must be exact");
        for (double step : ChaseSpawn.BEHIND_DISTANCE_STEPS) {
            check(ChaseTuning.SPAWN_BEHIND_DIST + step >= ChaseSpawn.MIN_BEHIND_DIST,
                    "spawn: fallback distance is too small");
        }

        // Взгляд строго вверх/вниз не должен вырождать смещение в ноль.
        List<Vec3> vertical = ChaseSpawn.behind(new Vec3(0, 1, 0), ChaseTuning.SPAWN_BEHIND_DIST);
        check(!vertical.isEmpty() && vertical.get(0).length() > 1.0, "spawn: vertical look must still give a direction");

        List<Vec3> front = ChaseSpawn.around(north, true);
        List<Vec3> back = ChaseSpawn.around(north, false);
        check(Math.abs(front.get(0).dot(north) - ChaseSpawn.AROUND_DISTANCES[0]) < 1.0E-6,
                "spawn: front ring is not in front");
        check(Math.abs(back.get(0).dot(north) + ChaseSpawn.AROUND_DISTANCES[0]) < 1.0E-6,
                "spawn: back ring is not behind");
        check(front.size() == ChaseSpawn.AROUND_ANGLES.length * ChaseSpawn.AROUND_DISTANCES.length,
                "spawn: around ring size changed");
        for (Vec3 p : front) {
            check(p.length() >= 5.0 - 1.0E-6 && p.length() <= 9.0 + 1.0E-6, "spawn: around distance out of range");
        }
    }

    // ------------------------------------------------------------------
    // Таблицы
    // ------------------------------------------------------------------

    private static void tuning() {
        check(ChaseTuning.SPEED_BY_STAGE.length == 6, "tuning: speed table must cover stages 0..5");
        check(ChaseTuning.RUN_TICKS_BY_STAGE.length == 6, "tuning: run table must cover stages 0..5");
        for (int i = 0; i < 6; i++) {
            check(ChaseTuning.SPEED_BY_STAGE[i] > 0, "tuning: non-positive speed");
            check(ChaseTuning.RUN_TICKS_BY_STAGE[i] > 0, "tuning: non-positive run time");
            if (i > 0) {
                check(ChaseTuning.SPEED_BY_STAGE[i] >= ChaseTuning.SPEED_BY_STAGE[i - 1], "tuning: speed drops with stage");
                check(ChaseTuning.RUN_TICKS_BY_STAGE[i] >= ChaseTuning.RUN_TICKS_BY_STAGE[i - 1],
                        "tuning: run time drops with stage");
            }
        }
        check(ChaseTuning.CATCH_DIST < ChaseTuning.TEACH_VANISH_DIST, "tuning: teaching figure must vanish before catching");
        check(ChaseTuning.ABORT_DIST > ChaseTuning.SPAWN_BEHIND_DIST, "tuning: spawn would abort immediately");
        check(ChaseTuning.PATH_MAX_NODES > 0 && ChaseTuning.PATH_RANGE > 0, "tuning: path budget must be positive");
        check(ChaseTuning.TRAIL_JOIN_TRIES > 0 && ChaseTuning.TRAIL_JOIN_NODES > 0, "tuning: trail fallback must be enabled");
        check(ChaseTuning.REPATH_FAIL_TICKS > 0 && ChaseTuning.BLOCKED_TICKS > 0, "tuning: timeouts must be positive");
        check(ChaseTuning.PATH_SCRAMBLE_MAX >= 2, "tuning: scramble must reach a two-block ledge");
    }

    // ------------------------------------------------------------------
    // Инварианты пути
    // ------------------------------------------------------------------

    private static ChasePath.Route route(MapGrid grid, int sx, int sz, int gx, int gz) {
        ChasePath.Route r = new ChasePath(grid).find(at(sx, sz), at(gx, gz), ChaseTuning.PATH_MAX_NODES);
        check(r != null, "map: no route from " + sx + "," + sz + " to " + gx + "," + gz);
        return r;
    }

    private static void validate(ChasePath.Route r, MapGrid grid, String name) {
        check(r != null, name + ": no route");
        check(!r.points.isEmpty() && r.points.size() == r.cells.size(), name + ": broken route");
        for (int i = 0; i < r.size(); i++) {
            BlockPos c = r.cells.get(i);
            byte feet = grid.kind(c.getX(), c.getY(), c.getZ());
            byte head = grid.kind(c.getX(), c.getY() + 1, c.getZ());
            check(feet != ChasePath.SOLID && feet != ChasePath.DANGER,
                    name + ": path enters a wall at " + c);
            check(head != ChasePath.SOLID && head != ChasePath.DANGER && head != ChasePath.LOW,
                    name + ": head hits a block at " + c);
            check(feet == ChasePath.OPEN || feet == ChasePath.LOW || feet == ChasePath.DOOR
                            || feet == ChasePath.CLIMB || feet == ChasePath.WATER,
                    name + ": path enters an unknown cell at " + c);
            Vec3 p = r.points.get(i);
            check(Math.abs(p.x - (c.getX() + 0.5)) < 1.0E-6 && Math.abs(p.z - (c.getZ() + 0.5)) < 1.0E-6,
                    name + ": point is not the cell center at " + c);
        }
        for (int i = 1; i < r.size(); i++) {
            BlockPos a = r.cells.get(i - 1);
            BlockPos b = r.cells.get(i);
            int dx = b.getX() - a.getX();
            int dz = b.getZ() - a.getZ();
            if (Math.abs(dx) > 1 || Math.abs(dz) > 1) {
                // прыжок через яму: только по прямой, не дальше 3 клеток, промежуточные клетки без пола
                check(dx == 0 || dz == 0, name + ": diagonal jump between cells");
                int steps = Math.max(Math.abs(dx), Math.abs(dz));
                check(steps <= 3, name + ": path jumps too far");
                for (int k = 1; k < steps; k++) {
                    int x = a.getX() + Integer.signum(dx) * k;
                    int z = a.getZ() + Integer.signum(dz) * k;
                    check(Double.isNaN(grid.floor(x, a.getY(), z)), name + ": path jumps over a standable cell");
                    check(free(grid.kind(x, a.getY(), z)) && free(grid.kind(x, a.getY() + 1, z)),
                            name + ": path jumps through a block");
                }
                continue;
            }
            if (dx != 0 && dz != 0 && a.getY() == b.getY()) {
                check(free(grid.kind(a.getX() + dx, a.getY(), a.getZ())), name + ": path cuts a corner");
                check(free(grid.kind(a.getX(), a.getY(), a.getZ() + dz)), name + ": path cuts a corner");
            }
        }
    }

    private static boolean free(byte kind) {
        return kind == ChasePath.OPEN || kind == ChasePath.CLIMB || kind == ChasePath.WATER;
    }

    private static BlockPos cell(ChasePath.Route r, int x, int z) {
        for (BlockPos c : r.cells) if (c.getX() == x && c.getZ() == z) return c;
        return null;
    }

    private static boolean anyY(ChasePath.Route r, int y) {
        for (BlockPos c : r.cells) if (c.getY() == y) return true;
        return false;
    }

    private static boolean anyKind(ChasePath.Route r, MapGrid grid, byte kind) {
        for (BlockPos c : r.cells) if (grid.kind(c.getX(), c.getY(), c.getZ()) == kind) return true;
        return false;
    }

    private static Vec3 at(int x, int z) {
        return new Vec3(x + 0.5, 1.0, z + 0.5);
    }

    private static double clamp(double v) {
        return Math.max(-1.0, Math.min(1.0, v));
    }

    // ------------------------------------------------------------------
    // Искусственная сетка
    // ------------------------------------------------------------------

    private static final class MapGrid implements ChasePath.Grid {
        private final String[] rows;

        MapGrid(String... rows) {
            this.rows = rows;
            for (String row : rows) check(row.length() == rows[0].length(), "grid: rows have different lengths");
        }

        private char at(int x, int z) {
            if (z < 0 || z >= rows.length || x < 0 || x >= rows[z].length()) return '#';
            return rows[z].charAt(x);
        }

        @Override
        public byte kind(int x, int y, int z) {
            if (y < 0) return ChasePath.SOLID;
            char c = at(x, z);
            if (y == 0) return c == 'P' ? ChasePath.OPEN : ChasePath.SOLID;
            if (y == 1) {
                return switch (c) {
                    case '#', 's', 'W' -> ChasePath.SOLID;
                    case 'P' -> ChasePath.OPEN;
                    case '_' -> ChasePath.LOW;
                    case '~' -> ChasePath.WATER;
                    case '^' -> ChasePath.CLIMB;
                    case 'D' -> ChasePath.DOOR;
                    case 'L' -> ChasePath.DANGER;
                    default -> ChasePath.OPEN;
                };
            }
            if (y == 2) {
                return switch (c) {
                    case '#', 'W' -> ChasePath.SOLID;
                    case 's', 'P', '_', '~', '^' -> ChasePath.OPEN;
                    case 'D' -> ChasePath.DOOR;
                    case 'L' -> ChasePath.DANGER;
                    default -> ChasePath.OPEN;
                };
            }
            if (y == 3) return c == 'W' ? ChasePath.SOLID : ChasePath.OPEN;
            return ChasePath.OPEN;
        }

        @Override
        public double floor(int x, int y, int z) {
            if (y < 0) return Double.NaN;
            char c = at(x, z);
            if (y == 1) {
                return switch (c) {
                    case '#', 's', 'W', 'P', 'L' -> Double.NaN;
                    case '_' -> 1.5;
                    default -> 1.0;
                };
            }
            if (y == 2 && c == 's') return 2.0;
            if (y == 4 && c == 'W') return 4.0;
            return Double.NaN;
        }
    }
}
