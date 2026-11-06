package com.hypersomnia.forest;

public final class ForestReplyRhythmSelfTest {
    private static int checks;
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("check " + checks);
    }
    public static void main(String[] args) {
        int[] expected = {0, 4, 8, 12, 16, 20, 24, 28};
        for (int i = 0; i < 8; i++) {
            check(ForestReplyRhythm.tick(i, 12) == expected[i]);
            check(ForestReplyRhythm.isBreak(i) == (i == 3 || i == 7));
        }
        for (int gap : new int[] {-100, 6, 12, 16, 17, 32, 60, 1000}) {
            int last = -1;
            for (int i = 0; i < 8; i++) {
                int now = ForestReplyRhythm.tick(i, gap);
                check(now > last);
                last = now;
            }
            int actualGap = ForestReplyRhythm.tick(7, gap) - ForestReplyRhythm.tick(3, gap);
            check(actualGap == Math.min(60, Math.max(16, gap)));
        }
        try { ForestReplyRhythm.tick(-1, 12); throw new AssertionError(); }
        catch (IllegalArgumentException expectedException) { checks++; }
        try { ForestReplyRhythm.isBreak(8); throw new AssertionError(); }
        catch (IllegalArgumentException expectedException) { checks++; }
        System.out.println("ForestReplyRhythm: " + checks + " checks passed");
    }
}
