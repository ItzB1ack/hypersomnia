package com.hypersomnia.lab;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Проверяет, что варианты загадок согласованы с их ответами. Не заменяет прохождение. */
public final class LabVariantsSelfTest {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    private static String digits(int[] values) {
        StringBuilder s = new StringBuilder(); for (int n : values) s.append(n); return s.toString();
    }
    public static void main(String[] args) {
        long[] special = {0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE};
        for (long seed : special) checkSeed(seed);
        for (long seed = 2; seed < 102; seed++) checkSeed(seed * 0x9E3779B97F4A7C15L);
        System.out.println("LabVariantsSelfTest: " + checks + " checks passed across 105 seeds.");
    }
    private static void checkSeed(long seed) {
        String c = digits(LabVariants.feedRevision(seed, 'C'));
        String a = digits(LabVariants.feedRevision(seed, 'A'));
        String b = digits(LabVariants.feedRevision(seed, 'B'));
        check(!c.equals(a) && !b.equals(c) && !b.equals(a), "feed revisions " + seed);
        check(c.equals(Integer.toString(LabVariants.feedCode(seed))), "feed code " + seed);
        Set<Character> feed = new HashSet<>(); for (char d : c.toCharArray()) feed.add(d);
        check(feed.size() == LabTuning.FEEDERS && c.length() == LabTuning.FEEDERS, "feed permutation " + seed);
        check(LabVariants.hatchCode(seed, false) == LabTuning.HATCH_CODES[LabVariants.hatchRule(seed)], "hatch " + seed);
        check(LabVariants.hatchCode(seed, true) == LabTuning.HATCH_DREAM_CODE, "dream hatch " + seed);
        String bay = LabVariants.bayTime(seed).replace(":", "");
        check(!bay.equals(new StringBuilder(bay).reverse().toString()), "palindrome bay " + seed);
        check(LabVariants.bayCode(seed).equals(new StringBuilder(bay).reverse().toString()), "bay code " + seed);
        var plan = LabVariants.posts(seed);
        check(plan.cameras() == LabTuning.POSTS_CAMERAS, "camera count " + seed);
        for (int i = 0; i < plan.cameras(); i++) {
            int[] f = LabVariants.wallHit(plan.camX()[i], plan.camZ()[i], plan.dirX()[i], plan.dirZ()[i]);
            int[] back = LabVariants.wallHit(plan.camX()[i], plan.camZ()[i], -plan.dirX()[i], -plan.dirZ()[i]);
            int fi = plan.front()[i], bi = plan.back()[i];
            check(f != null && f[0] == plan.postX()[fi] && f[1] == plan.postZ()[fi], "camera front " + seed);
            check(back != null && back[0] == plan.postX()[bi] && back[1] == plan.postZ()[bi], "camera back " + seed);
        }
        check(!LabVariants.postCode(seed).equals(LabVariants.planCode(seed)), "post direction matters " + seed);
        int[] gaps = LabVariants.stacksGaps(seed); int[][] sheet = LabVariants.stacksSheet(seed);
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < gaps.length; i++) expected.append(sheet[i][gaps[i]]);
        check(expected.toString().equals(LabVariants.stacksCode(seed)), "stacks code " + seed);
        int[] racks = LabVariants.rackHold(seed); Set<Integer> unique = new HashSet<>();
        for (int rack : racks) { unique.add(rack); check(LabVariants.rackHeld(seed, rack), "rack hold " + seed); }
        check(unique.size() == racks.length, "unique racks " + seed);
        check(LabVariants.antennaTarget(seed) == (LabVariants.antennaStart(seed) + 180) % 360, "antenna " + seed);
        String knock = LabVariants.knockCode(seed); Set<Character> knockDigits = new HashSet<>();
        for (char digit : knock.toCharArray()) knockDigits.add(digit);
        check(knock.length() == LabTuning.CODE_DIGITS && knockDigits.size() == LabTuning.CODE_DIGITS, "knock " + seed);
        check(Arrays.equals(LabVariants.keyCell(0), new int[]{4, 2}), "zero keypad cell");
    }
}
