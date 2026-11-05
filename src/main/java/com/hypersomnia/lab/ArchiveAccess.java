package com.hypersomnia.lab;

import com.hypersomnia.ModAttachments;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Серверная проверка доступа к записям рабочих станций. Клиент получает только готовый снимок. */
public final class ArchiveAccess {
    private ArchiveAccess() {}

    /** listed: id видимых записей станции; readable: id открываемых (подмножество listed). */
    public record Snapshot(List<String> listed, List<String> readable) {}

    public static Snapshot snapshot(ServerPlayer player, LabSavedData d, int station) {
        List<String> listed = new ArrayList<>();
        List<String> readable = new ArrayList<>();
        for (ArchiveDefs.Rec r : ArchiveDefs.all()) {
            if (r.station() != station || !test(r.listed(), player, d)) continue;
            listed.add(r.id());
            if (test(r.readable(), player, d)) readable.add(r.id());
        }
        return new Snapshot(listed, readable);
    }

    public static boolean canRead(String id, ServerPlayer player, LabSavedData d) {
        ArchiveDefs.Rec r = ArchiveDefs.get(id);
        return r != null && test(r.listed(), player, d) && test(r.readable(), player, d);
    }

    private static boolean test(ArchiveDefs.Cond c, ServerPlayer player, LabSavedData d) {
        return switch (c) {
            case ArchiveDefs.Always a -> true;
            case ArchiveDefs.LockOpen l -> d.isLockOpen(l.lock());
            case ArchiveDefs.LabFlag f -> d.hasFlag(f.flag());
            case ArchiveDefs.Memory m -> m.n() >= 1 && ((player.getData(ModAttachments.OBSERVER).getMemories() >> (m.n() - 1)) & 1) != 0;
            case ArchiveDefs.World w -> d.dream == w.dream();
            case ArchiveDefs.Not n -> !test(n.cond(), player, d);
        };
    }
}
