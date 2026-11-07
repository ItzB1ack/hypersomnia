package com.hypersomnia.finale;

import com.hypersomnia.document.DrawingTuning;
import com.hypersomnia.document.MemoryTuning;

/** Проверяет выбор концовки по сохранённому прогрессу, не запускает Minecraft. */
public final class FinaleLogicSelfTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        for (int memories = 0; memories <= MemoryTuning.COUNT + 1; memories++) {
            for (boolean truth : new boolean[]{false, true}) {
                for (int drawings = 0; drawings <= DrawingTuning.COUNT + 1; drawings++) {
                    for (boolean record : new boolean[]{false, true}) {
                        boolean complete = memories >= MemoryTuning.COUNT && truth;
                        int bed = complete ? FinaleTuning.UNITY : FinaleTuning.REPLACEMENT;
                        int door = !complete ? FinaleTuning.OBLIVION
                                : drawings >= DrawingTuning.COUNT && record ? FinaleTuning.NORA : FinaleTuning.FAREWELL;
                        String state = " memories=" + memories + " truth=" + truth + " drawings=" + drawings + " record=" + record;
                        check(FinaleLogic.choose(true, memories, truth, drawings, record) == bed, "bed" + state);
                        check(FinaleLogic.choose(false, memories, truth, drawings, record) == door, "door" + state);
                    }
                }
            }
        }
        check(FinaleTuning.KEYS.length == 7, "seven ending keys");
        check(FinaleTuning.ENDINGS == FinaleTuning.KEYS.length, "ending count");
        for (String key : FinaleTuning.KEYS) check(key != null && !key.isBlank(), "nonempty ending key");
        System.out.println("FinaleLogicSelfTest: " + checks + " checks passed.");
    }
}
