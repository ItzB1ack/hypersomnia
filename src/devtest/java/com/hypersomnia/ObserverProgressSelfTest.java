package com.hypersomnia;

/** Проверяет память маршрутов, включая удалённые и предельные координаты. */
public final class ObserverProgressSelfTest {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }

    public static void main(String[] args) {
        ObserverProgressData data = new ObserverProgressData();
        data.noteRoute(0, 64, 0);
        data.noteRoute(3, 64, 4);
        check(data.routeCount() == 1, "nearby route merges");
        check(data.routeList().get(3) == 2, "merged weight");
        data.noteRoute(65536, 64, 0);
        check(data.routeCount() == 2, "distant route must not overflow");
        data.noteRoute(Integer.MIN_VALUE, 64, Integer.MIN_VALUE);
        data.noteRoute(Integer.MAX_VALUE, 64, Integer.MAX_VALUE);
        check(data.routeCount() == 4, "integer limits must not merge");
        data.noteSleep(0, 64, 0);
        data.noteSleep(65536, 64, 0);
        check(data.sleepCount() == 2, "distant beds do not merge");
        data.noteSleep(0, Integer.MIN_VALUE, 0);
        check(data.sleepCount() == 3, "height subtraction must not overflow");
        ObserverProgressData pending = new ObserverProgressData();
        pending.setPending(1, 2);
        pending.setNoteThisSleep(true);
        var tag = pending.serializeNBT(null);
        ObserverProgressData loaded = new ObserverProgressData();
        loaded.deserializeNBT(null, tag);
        check(loaded.getPendingNote() == 1 && loaded.getPendingWay() == 2, "pending note survives reload");
        check(loaded.noteThisSleep(), "entry delivery survives reload");
        loaded.deserializeNBT(null, new net.minecraft.nbt.CompoundTag());
        check(loaded.getPendingNote() == -1 && loaded.getPendingWay() == -1 && !loaded.noteThisSleep(), "old saves reset transient state");
        tag.putInt("pending_way", 99);
        loaded.deserializeNBT(null, tag);
        check(loaded.getPendingNote() == -1, "invalid delivery way rejected");
        tag.putInt("pending_way", 0);
        tag.putInt("pending_note", Integer.MAX_VALUE);
        loaded.deserializeNBT(null, tag);
        check(loaded.getPendingNote() == -1, "invalid note rejected");
        loaded.setPending(0, 0);
        loaded.setNoteThisSleep(true);
        loaded.resetNotes();
        check(loaded.getPendingNote() == -1 && !loaded.noteThisSleep(), "reset clears sleep flags");
        System.out.println("ObserverProgressSelfTest: " + checks + " checks passed.");
    }
}
