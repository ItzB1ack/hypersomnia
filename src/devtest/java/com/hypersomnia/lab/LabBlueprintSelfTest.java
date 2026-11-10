package com.hypersomnia.lab;

import com.hypersomnia.document.DocumentDefs;
import com.hypersomnia.document.MemoryTuning;
import com.hypersomnia.finale.FinaleTuning;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Проверка связности чертежей и сюжетных определений без запуска игрового мира. */
public final class LabBlueprintSelfTest {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    private static int occurrences(LabBlueprint bp, char symbol) {
        int count = 0;
        for (int z = 0; z < bp.d; z++) for (int x = 0; x < bp.w; x++) if (bp.at(x, z) == symbol) count++;
        return count;
    }
    private static void used(LabBlueprint bp, Map<Character, ?> entries, String kind) {
        for (char c : entries.keySet()) check(occurrences(bp, c) > 0, bp.name + ": unused " + kind + " " + c);
    }

    private static final int[][] ORDER = {{0, -1}, {0, 1}, {1, 0}, {-1, 0}};
    private static final String FLOOR = ".*vru123456789";
    private static final String STRUCTURAL = "#=lL@YEw";
    private static final String[] MAP_KINDS = {"station", "finale", "bed", "capsule", "panel",
            "knock", "rack", "equip", "stack", "cabinet", "lock", "sign"};

    private static boolean passage(LabBlueprint bp, char c) {
        return c == '=' || bp.gates.containsKey(c);
    }

    private static boolean structural(char c) {
        return STRUCTURAL.indexOf(c) >= 0;
    }

    private static boolean floor(char c) {
        return FLOOR.indexOf(c) >= 0 || "LY@".indexOf(c) >= 0;
    }

    private static int[] firstWall(LabBlueprint bp, int x, int z) {
        for (int[] d : ORDER) {
            char n = bp.at(x + d[0], z + d[1]);
            if (n == '#' || bp.knocks.containsKey(n)) return d;
        }
        return null;
    }

    private static List<int[]> cells(LabBlueprint bp, char c) {
        List<int[]> out = new ArrayList<>();
        for (int z = 0; z < bp.d; z++) for (int x = 0; x < bp.w; x++)
            if (bp.at(x, z) == c) out.add(new int[]{x, z});
        return out;
    }

    private static List<int[]> passages(LabBlueprint bp) {
        List<int[]> out = new ArrayList<>();
        for (int z = 0; z < bp.d; z++) for (int x = 0; x < bp.w; x++)
            if (passage(bp, bp.at(x, z))) out.add(new int[]{x, z});
        return out;
    }

    private static Map<String, Set<Character>> mapKinds(LabBlueprint bp) {
        Map<String, Set<Character>> kinds = new HashMap<>();
        kinds.put("sign", bp.signs.keySet());
        kinds.put("lock", bp.locks.keySet());
        kinds.put("panel", bp.panels.keySet());
        kinds.put("station", bp.stations.keySet());
        kinds.put("cabinet", bp.cabinets.keySet());
        kinds.put("finale", bp.finale.keySet());
        kinds.put("capsule", bp.capsules.keySet());
        kinds.put("bed", bp.beds.keySet());
        kinds.put("rack", bp.racks.keySet());
        kinds.put("stack", bp.stacks.keySet());
        kinds.put("equip", bp.equips.keySet());
        kinds.put("knock", bp.knocks.keySet());
        kinds.put("gate", bp.gates.keySet());
        return kinds;
    }

    private static boolean mapChar(LabBlueprint bp, char c) {
        return bp.stations.containsKey(c) || bp.finale.containsKey(c) || bp.beds.containsKey(c)
                || bp.capsules.containsKey(c) || bp.panels.containsKey(c) || bp.cabinets.containsKey(c)
                || bp.knocks.containsKey(c) || bp.racks.containsKey(c) || bp.equips.containsKey(c)
                || bp.stacks.containsKey(c) || bp.locks.containsKey(c);
    }

    /** Проём — ровно одна клетка-маркер, стена по ±1/±2 и свободный пол по обе стороны. */
    private static void checkPassages(LabBlueprint bp) {
        boolean under = !bp.name.endsWith("_g");
        List<int[]> doors = passages(bp);
        for (int[] p : doors) {
            int x = p[0], z = p[1];
            for (int[] d : ORDER) check(!passage(bp, bp.at(x + d[0], z + d[1])),
                    bp.name + ": passage (" + x + "," + z + ") touches another marker");
            boolean along = bp.at(x - 1, z) == '#' && bp.at(x + 1, z) == '#';
            int dx = along ? 1 : 0, dz = along ? 0 : 1;
            for (int k : new int[]{-2, -1, 1, 2}) check(bp.at(x + dx * k, z + dz * k) == '#',
                    bp.name + ": passage (" + x + "," + z + ") wall cell " + k);
            for (int k = -1; k <= 1; k++) for (int s = -1; s <= 1; s += 2) {
                int px = x + dx * k + dz * s, pz = z + dz * k + dx * s;
                char c = bp.at(px, pz);
                if (c == ' ' && !under) continue;
                check(floor(c), bp.name + ": passage (" + x + "," + z + ") blocked by '" + c + "'");
            }
        }
    }

    /** Табличка не ближе одной клетки к проёму: её читают с порога, а не из проёма. */
    private static void signsNearPassages(LabBlueprint bp) {
        List<int[]> doors = passages(bp);
        for (char c : bp.signs.keySet()) for (int[] p : cells(bp, c)) {
            boolean close = false;
            for (int[] d : doors)
                if (Math.max(Math.abs(p[0] - d[0]), Math.abs(p[1] - d[1])) < 2) close = true;
            check(!close, bp.name + ": sign " + c + " at (" + p[0] + "," + p[1] + ") is within 1 cell of passage");
        }
    }

    /** Карты важнее мебели: символ — одна карта, карта не на стене, изножье не затирает карту. */
    private static void mapsAndFurniture(LabBlueprint bp) {
        Map<String, Set<Character>> kinds = mapKinds(bp);
        Map<Character, String> owner = new HashMap<>();
        for (var entry : kinds.entrySet()) for (char c : entry.getValue()) {
            String before = owner.putIfAbsent(c, entry.getKey());
            check(before == null || before.equals(entry.getKey()),
                    bp.name + ": symbol '" + c + "' is both " + before + " and " + entry.getKey());
        }
        for (String kind : MAP_KINDS) for (char c : kinds.get(kind))
            check(!structural(c), bp.name + ": " + kind + " '" + c + "' is a structural cell");
        for (char c : bp.beds.keySet()) for (int[] p : cells(bp, c)) {
            int[] w = firstWall(bp, p[0], p[1]);
            int fx = p[0] - (w == null ? -1 : w[0]), fz = p[1] - (w == null ? 0 : w[1]);
            check(!mapChar(bp, bp.at(fx, fz)), bp.name + ": bed " + c + " at (" + p[0] + "," + p[1] + ") foot overwrites a map");
        }
        for (var entry : bp.finale.entrySet()) if (entry.getValue() == FinaleTuning.PART_BED)
            for (int[] p : cells(bp, entry.getKey())) {
                int[] w = firstWall(bp, p[0], p[1]);
                int fx = p[0] - (w == null ? -1 : w[0]), fz = p[1] - (w == null ? 0 : w[1]);
                check(!mapChar(bp, bp.at(fx, fz)), bp.name + ": finale bed " + entry.getKey() + " at (" + p[0] + "," + p[1] + ") foot overwrites a map");
            }
        for (char c : bp.locks.keySet())
            check(!LabBlueprint.furnitureSymbol(c), bp.name + ": lock " + c + " uses furniture symbol");
    }

    private static long key(LabBlueprint bp, int x, int z) {
        return (long) x * 10000 + z;
    }

    private static boolean walkable(LabBlueprint bp, char c) {
        if (floor(c)) return true;
        if ("lE=".indexOf(c) >= 0) return true;
        if (bp.gates.containsKey(c) || bp.locks.containsKey(c)) return true;
        Integer part = bp.finale.get(c);
        return part != null && (part == FinaleTuning.PART_TOY || part == FinaleTuning.PART_FRAGMENT
                || part == FinaleTuning.PART_DOOR);
    }

    private static boolean near(LabBlueprint bp, Set<Long> reach, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if ((dx != 0 || dz != 0) && reach.contains(key(bp, x + dx, z + dz))) return true;
        for (int[] d : ORDER) {
            char c = bp.at(x + d[0], z + d[1]);
            boolean low = c == 'h' || c == 'H' || c == 'T' || c == 'D' || c == 'd'
                    || c == 'r' || c == 'u' || c == '+' || bp.stations.containsKey(c);
            if (low && reach.contains(key(bp, x + 2 * d[0], z + 2 * d[1]))) return true;
        }
        return false;
    }

    /** Замок стоит на достижимой клетке, к шкафу можно дотянуться с достижимой клетки. */
    private static void reachability(LabBlueprint bp) {
        Set<Long> reach = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        reach.add(key(bp, bp.ax, bp.az));
        queue.add(new int[]{bp.ax, bp.az});
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            for (int[] d : ORDER) {
                int x = p[0] + d[0], z = p[1] + d[1];
                long k = key(bp, x, z);
                if (reach.contains(k) || !walkable(bp, bp.at(x, z))) continue;
                reach.add(k);
                queue.add(new int[]{x, z});
            }
        }
        for (char c : bp.locks.keySet()) for (int[] p : cells(bp, c))
            check(reach.contains(key(bp, p[0], p[1])), bp.name + ": lock " + c + " at (" + p[0] + "," + p[1] + ") unreachable");
        for (char c : bp.cabinets.keySet()) for (int[] p : cells(bp, c))
            check(near(bp, reach, p[0], p[1]), bp.name + ": cabinet " + c + " at (" + p[0] + "," + p[1] + ") unreachable");
    }

    public static void main(String[] args) {
        Map<String, Integer> memories = new HashMap<>();
        Set<Integer> finaleParts = new HashSet<>();
        for (boolean dream : new boolean[]{false, true}) for (int floor = 0; floor < LabTuning.STOPS; floor++) {
            String name = LabTuning.blueprintName(dream, floor);
            check(LabBlueprint.exists(name), "missing " + name);
            LabBlueprint bp = LabBlueprint.get(name);
            check(bp.dream == dream, name + ": wrong style");
            check(bp.w > 0 && bp.d > 0 && bp.height > 0, name + ": invalid dimensions");
            check(occurrences(bp, '@') == 1, name + ": lift needs exactly one anchor");
            check(bp.at(bp.ax, bp.az) == '@', name + ": anchor coordinates");
            used(bp, bp.signs, "sign"); used(bp, bp.locks, "lock"); used(bp, bp.panels, "panel");
            used(bp, bp.stations, "station"); used(bp, bp.cabinets, "cabinet"); used(bp, bp.syringes, "syringe");
            used(bp, bp.seals, "seal");
            used(bp, bp.gates, "gate"); used(bp, bp.finale, "finale"); used(bp, bp.capsules, "capsule");
            used(bp, bp.beds, "bed"); used(bp, bp.racks, "rack"); used(bp, bp.stacks, "stack");
            used(bp, bp.knocks, "knock"); used(bp, bp.equips, "equip");
            checkPassages(bp);
            signsNearPassages(bp);
            mapsAndFurniture(bp);
            reachability(bp);
            for (var entry : bp.cabinets.entrySet()) {
                String id = entry.getValue();
                check(DocumentDefs.get(id) != null, name + ": unknown cabinet document " + id);
                if (MemoryTuning.indexOf(id) >= 0) memories.merge(id, occurrences(bp, entry.getKey()), Integer::sum);
            }
            for (var entry : bp.syringes.entrySet()) {
                char c = entry.getKey();
                check(entry.getValue() > 0, name + ": bad syringe count " + c);
                check(bp.cabinets.containsKey(c) || c == 'F' || c == 'p', name + ": syringe on non-cabinet " + c);
            }
            for (var entry : bp.stations.entrySet()) check(ArchiveDefs.station(entry.getValue()[0]) != null,
                    name + ": unknown station " + entry.getValue()[0]);
            for (char c : bp.seals.keySet()) check(bp.cabinets.containsKey(c), name + ": seal without cabinet " + c);
            if (dream) finaleParts.addAll(bp.finale.values());
        }
        for (String id : MemoryTuning.IDS) check(memories.getOrDefault(id, 0) == 1, "memory source count: " + id);
        for (int part = 0; part < com.hypersomnia.finale.FinaleTuning.PARTS.length; part++)
            check(finaleParts.contains(part), "dream finale part missing: " + part);
        for (boolean dream : new boolean[]{false, true}) {
            LabBlueprint b4 = LabBlueprint.get(LabTuning.blueprintName(dream, 4)); // stop 4 = B4
            int total = 0;
            for (int count : b4.syringes.values()) total += count;
            check(total > 0, (dream ? "dream" : "real") + " B4 has no syringe cabinets");
        }
        System.out.println("LabBlueprintSelfTest: " + checks + " checks passed.");
    }
}
