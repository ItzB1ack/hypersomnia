package com.hypersomnia.forest;

import com.hypersomnia.ForestReplyTuning;

/** Две короткие рубки: три удара с шагом в 4 тика, затем разрушение. */
public final class ForestReplyRhythm {
    private ForestReplyRhythm() {}

    public static final int CUE_COUNT = 8;
    private static final int STRIKE_INTERVAL = 4;
    private static final int MIN_BREAK_GAP = 16;

    public static int tick(int index, int requestedGap) {
        if (index < 0 || index >= CUE_COUNT) throw new IllegalArgumentException("cue index");
        int gap = Math.max(MIN_BREAK_GAP, ForestReplyTuning.clampGap(requestedGap));
        return (index / 4) * gap + (index % 4) * STRIKE_INTERVAL;
    }

    public static boolean isBreak(int index) {
        if (index < 0 || index >= CUE_COUNT) throw new IllegalArgumentException("cue index");
        return index % 4 == 3;
    }
}
