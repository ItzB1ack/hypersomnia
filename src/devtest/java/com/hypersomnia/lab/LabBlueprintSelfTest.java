package com.hypersomnia.lab;

import com.hypersomnia.document.DocumentDefs;
import com.hypersomnia.document.MemoryTuning;
import java.util.HashMap;
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
            used(bp, bp.stations, "station"); used(bp, bp.cabinets, "cabinet"); used(bp, bp.seals, "seal");
            used(bp, bp.gates, "gate"); used(bp, bp.finale, "finale"); used(bp, bp.capsules, "capsule");
            used(bp, bp.beds, "bed"); used(bp, bp.racks, "rack"); used(bp, bp.stacks, "stack");
            used(bp, bp.knocks, "knock"); used(bp, bp.equips, "equip");
            for (var entry : bp.cabinets.entrySet()) {
                String id = entry.getValue();
                check(DocumentDefs.get(id) != null, name + ": unknown cabinet document " + id);
                if (MemoryTuning.indexOf(id) >= 0) memories.merge(id, occurrences(bp, entry.getKey()), Integer::sum);
            }
            for (var entry : bp.stations.entrySet()) check(ArchiveDefs.station(entry.getValue()[0]) != null,
                    name + ": unknown station " + entry.getValue()[0]);
            for (char c : bp.seals.keySet()) check(bp.cabinets.containsKey(c), name + ": seal without cabinet " + c);
            if (dream) finaleParts.addAll(bp.finale.values());
        }
        for (String id : MemoryTuning.IDS) check(memories.getOrDefault(id, 0) == 1, "memory source count: " + id);
        for (int part = 0; part < com.hypersomnia.finale.FinaleTuning.PARTS.length; part++)
            check(finaleParts.contains(part), "dream finale part missing: " + part);
        System.out.println("LabBlueprintSelfTest: " + checks + " checks passed.");
    }
}
