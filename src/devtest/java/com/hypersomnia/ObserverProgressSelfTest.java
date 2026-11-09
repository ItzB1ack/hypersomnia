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

        // Личные надписи: шаг переживает перезагрузку, порядок покрывает все 12 вариантов ровно по разу,
        // а стадия не даёт выбрать строку, которая ещё не положена.
        for (int i = 0; i < 5; i++) loaded.advanceWriting();
        check(loaded.writingStep() == 5, "writing step advances");
        tag = loaded.serializeNBT(null);
        ObserverProgressData writing = new ObserverProgressData();
        writing.deserializeNBT(null, tag);
        check(writing.writingStep() == 5, "writing step survives reload");
        tag.putInt("writing_step", -3);
        writing.deserializeNBT(null, tag);
        check(writing.writingStep() == 0, "negative writing step clamped");
        boolean[] seenVariant = new boolean[12];
        for (int[] e : com.hypersomnia.trace.TraceTuning.WRITING_ORDER) {
            check(e[0] >= 0 && e[0] < seenVariant.length, "writing variant in range");
            check(!seenVariant[e[0]], "writing variant listed once: " + e[0]);
            seenVariant[e[0]] = true;
            check(e[1] >= 3 && e[1] <= 5, "writing min stage 3..5");
        }
        for (boolean seen : seenVariant) check(seen, "writing order covers all variants");
        boolean[] atStage3 = new boolean[12], atStage5 = new boolean[12];
        for (int step = 0; step < 12; step++) {
            atStage3[com.hypersomnia.trace.TraceTuning.writingVariant(step, 3)] = true;
            atStage5[com.hypersomnia.trace.TraceTuning.writingVariant(step, 5)] = true;
        }
        for (int v = 0; v < 12; v++) {
            if (v >= 3) check(!atStage3[v], "stage 3 must not show later variant " + v);
            check(atStage5[v], "stage 5 cycle must show variant " + v);
        }
        check(com.hypersomnia.trace.TraceTuning.writingVariant(12, 5)
                == com.hypersomnia.trace.TraceTuning.writingVariant(0, 5), "writing order wraps");

        // Влияние бодрствования: копится по тикам, ограничено, снимается сном и переживает NBT.
        ObserverProgressData exposure = new ObserverProgressData();
        for (int i = 0; i < ObserverTuning.EXPOSURE_AWAKE_TICKS_PER_POINT; i++) exposure.addExposureTick();
        check(exposure.exposurePoints() == 1, "awake ticks give one exposure point");
        exposure.registerSyringeExposure();
        check(exposure.exposurePoints() == 2, "syringe adds one exposure point");
        for (int i = 0; i < 20; i++) exposure.registerSyringeExposure();
        check(exposure.exposurePoints() == ObserverTuning.EXPOSURE_MAX_POINTS, "exposure is capped");
        exposure.restExposure();
        check(exposure.exposurePoints() == ObserverTuning.EXPOSURE_MAX_POINTS - 1, "sleep removes one exposure point");
        exposure.setExposure(3);
        check(exposure.exposurePoints() == 3, "debug exposure set");
        var exposureTag = exposure.serializeNBT(null);
        ObserverProgressData exposureLoaded = new ObserverProgressData();
        exposureLoaded.deserializeNBT(null, exposureTag);
        check(exposureLoaded.exposurePoints() == 3, "exposure survives reload");
        check(ObserverTuning.stage(0, 0, ObserverTuning.EXPOSURE_MAX_POINTS) == 1, "exposure counts toward stage");
        check(ObserverTuning.stage(0, 0, 99) == 1, "exposure stage clamps");
        check(ObserverTuning.stage(28, 3, 0) == 4, "stage 5 needs four memories");
        check(ObserverTuning.stage(28, 4, 0) == 5, "stage 5 with four memories");
        int sleepAt = Math.round(FatigueTuning.MAX_FATIGUE * FatigueTuning.SLEEP_ANYTIME_FATIGUE);
        check(!FatigueTuning.canSleepAtAnyTime(sleepAt - 1), "day sleep locked below fatigue threshold");
        check(FatigueTuning.canSleepAtAnyTime(sleepAt), "day sleep allowed at fatigue threshold");
        check(FatigueTuning.canSleepAtAnyTime(FatigueTuning.MAX_FATIGUE), "day sleep allowed at max fatigue");
        FatigueData daySleep = new FatigueData();
        daySleep.setAnyTimeSleep(true);
        check(daySleep.isAnyTimeSleep(), "day sleep permission set");
        daySleep.deserializeNBT(null, daySleep.serializeNBT(null));
        check(!daySleep.isAnyTimeSleep(), "day sleep permission is session-only");
        System.out.println("ObserverProgressSelfTest: " + checks + " checks passed.");
    }
}
