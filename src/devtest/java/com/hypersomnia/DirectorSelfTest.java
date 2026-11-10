package com.hypersomnia;

import com.hypersomnia.client.ChaseState;
import com.hypersomnia.client.DirectorRules;
import com.hypersomnia.client.ObserverDirector;
import com.hypersomnia.client.ObserverState;

/** Симуляция режиссёра страха: бюджет, тишина, кулдауны, сильные эпизоды (N23). */
public final class DirectorSelfTest {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }

    public static void main(String[] args) {
        stageTable();
        simulation();
        System.out.println("DirectorSelfTest: " + checks + " checks passed.");
    }

    private static void stageTable() {
        int previous = ObserverTuning.stage(0, 0, 0);
        for (int days = 1; days <= 40; days++) {
            int stage = ObserverTuning.stage(days, 0, 0);
            check(stage >= previous && stage - previous <= 1, "day step jumps stage: " + previous + " -> " + stage);
            previous = stage;
        }
        previous = ObserverTuning.stage(0, 0, 0);
        for (int memories = 1; memories <= 14; memories++) {
            int stage = ObserverTuning.stage(0, memories, 0);
            check(stage >= previous && stage - previous <= 1,
                    "memory step jumps stage: " + previous + " -> " + stage);
            previous = stage;
        }
        previous = ObserverTuning.stage(0, 0, 0);
        for (int exposure = 1; exposure <= ObserverTuning.EXPOSURE_MAX_POINTS; exposure++) {
            int stage = ObserverTuning.stage(0, 0, exposure);
            check(stage >= previous && stage - previous <= 1,
                    "exposure step jumps stage: " + previous + " -> " + stage);
            previous = stage;
        }
        check(ObserverTuning.stage(28, 3, 0) == 4, "stage 5 needs four memories");
        check(ObserverTuning.stage(28, 4, 0) == 5, "stage 5 with four memories");
        check(ObserverTuning.stage(100, 14, 8) == 5, "stage is capped at five");
        check(ObserverTuning.stage(-100, 0, 0) == 0, "negative days cannot make a stage");

        int n = ObserverDirector.Kind.values().length;
        check(ObserverTuning.EP_MIN_STAGE.length == n, "min stage table covers kinds");
        check(ObserverTuning.EP_COST.length == n, "cost table covers kinds");
        check(ObserverTuning.EP_COOLDOWN.length == n, "cooldown table covers kinds");
        check(ObserverTuning.EP_WEIGHT.length == n, "weight table covers kinds");
        check(ObserverTuning.EP_STRONG.length == n, "strong table covers kinds");
        check(ObserverTuning.EP_MAX_TICKS.length == n, "max ticks table covers kinds");
        for (int i = 0; i < n; i++) {
            check(ObserverTuning.EP_MIN_STAGE[i] >= 0 && ObserverTuning.EP_MIN_STAGE[i] <= 5,
                    "min stage in range: " + i);
            check(ObserverTuning.EP_COST[i] >= 0f, "cost is not negative: " + i);
            check(ObserverTuning.EP_COOLDOWN[i] >= 0, "cooldown is not negative: " + i);
            check(ObserverTuning.EP_MAX_TICKS[i] > 0, "max ticks is positive: " + i);
        }
        check(ObserverTuning.EP_WEIGHT[ObserverDirector.Kind.BED.ordinal()] == 0, "BED is not offered by pick");
        check(ObserverTuning.EP_WEIGHT[ObserverDirector.Kind.STILL.ordinal()] == 0, "STILL is not offered by pick");
        check(ObserverTuning.EP_COST[ObserverDirector.Kind.SCARE.ordinal()] == 0f, "SCARE is free inside GHOST");
        for (int stage = 1; stage <= 5; stage++) {
            check(ObserverTuning.STRONG_PER_WINDOW_BY_STAGE[stage] >= 0, "strong limit is not negative");
        }

        check(DirectorRules.regen(0f, 0f, 1) == ObserverTuning.DIRECTOR_REGEN_BASE
                + ObserverTuning.DIRECTOR_REGEN_PER_STAGE, "budget regen at stage one");
        check(DirectorRules.regen(0f, 1f, 5) == ObserverTuning.DIRECTOR_REGEN_BASE
                        + ObserverTuning.DIRECTOR_REGEN_PER_PRESSURE
                        + 5f * ObserverTuning.DIRECTOR_REGEN_PER_STAGE,
                "budget regen with pressure and stage");
        check(DirectorRules.regen(100f, 1f, 5) == ObserverTuning.DIRECTOR_BUDGET_CAP,
                "budget regen caps");
        check(DirectorRules.spend(100f, ObserverDirector.Kind.GHOST.ordinal()) == 65f, "ghost cost");
        check(DirectorRules.spend(10f, ObserverDirector.Kind.CHASE.ordinal()) == 0f, "budget floors at zero");
        check(DirectorRules.spend(0f, ObserverDirector.Kind.SCARE.ordinal()) == 0f, "scare is free");
        check(DirectorRules.strongLimit(0) == 0 && DirectorRules.strongLimit(2) == 1
                && DirectorRules.strongLimit(5) == 2, "strong limits by stage");
        check(!DirectorRules.strongRoom(0, 1), "stage one allows no strong episode");
        check(DirectorRules.strongRoom(0, 2), "stage two allows one strong episode");
        check(DirectorRules.strongRoom(1, 5), "stage five allows a second strong episode");
        check(!DirectorRules.strongRoom(2, 5), "stage five stops after two strong episodes");
        check(!DirectorRules.mayStartStrong(true, 0, 5), "strong after strong is blocked");
        check(DirectorRules.mayStartStrong(false, 1, 5), "strong after weak is allowed");
        check(!DirectorRules.mayStartStrong(false, 2, 5), "strong window limit is enforced");
        check(DirectorRules.silenceAfter(false, true, 5) == ObserverTuning.SILENCE_AFTER_ABORT,
                "unnoticed abort uses short silence");
        check(DirectorRules.silenceAfter(true, false, 5) == ObserverTuning.SILENCE_AFTER_BY_STAGE[5],
                "noticed weak uses stage silence");
        check(DirectorRules.silenceAfter(true, true, 5) == ObserverTuning.SILENCE_AFTER_STRONG,
                "noticed strong uses long silence");
        check(DirectorRules.silenceAfter(true, false, 1) == ObserverTuning.SILENCE_AFTER_BY_STAGE[1],
                "stage one silence");
    }

    private static void simulation() {
        ObserverDirector.reset();
        ObserverState.reset();
        ChaseState.reset();
        ObserverState.days = 100;
        ObserverState.ending = -1;
        ObserverDirector.doorNearby = true;
        // Гарантируем хотя бы один сильный эпизод, не полагаясь на случайность.
        ObserverDirector.force(ObserverDirector.Kind.CHASE, false);

        long[] lastEndByKind = new long[ObserverDirector.Kind.values().length];
        java.util.Arrays.fill(lastEndByKind, -1);
        long lastEnd = -1;
        boolean lastStrong = false;
        int episodes = 0;
        int strongEpisodes = 0;
        int maxGap = 0;
        int stage = 1;

        for (int t = 0; t < 200000; t++) {
            stage = Math.min(5, 1 + t / 40000);
            ObserverDirector.tick(0.8f, stage, false, false, true, false);
            check(ObserverDirector.budget() >= 0f, "budget below zero at " + t);
            check(ObserverDirector.budget() <= ObserverTuning.DIRECTOR_BUDGET_CAP + 0.001f,
                    "budget above cap at " + t);
            check(ObserverDirector.silence() >= 0, "silence below zero at " + t);

            ObserverDirector.Plan current = ObserverDirector.current();
            if (current != null) {
                if (current.kind == ObserverDirector.Kind.GHOST && current.scare) ObserverDirector.scareNow();
                if (current.ticks >= 20 + (t % 40)) {
                    boolean strong = current.strong;
                    boolean forced = current.forced;
                    ObserverDirector.end(current.kind, ObserverDirector.Outcome.DONE);
                    episodes++;
                    if (strong) strongEpisodes++;
                    lastEnd = t;
                    lastStrong = strong;
                    lastEndByKind[current.kind.ordinal()] = t;
                    if (strong && !forced) {
                        check(ObserverDirector.strongRecent()
                                        <= ObserverTuning.STRONG_PER_WINDOW_BY_STAGE[stage],
                                "strong window exceeded at " + t);
                    }
                }
                continue;
            }

            ObserverDirector.Plan requested = null;
            if (t % 3000 == 0) requested = ObserverDirector.request(ObserverDirector.Kind.STILL);
            if (requested == null && t % 20000 == 0) requested = ObserverDirector.request(ObserverDirector.Kind.BED);
            if (requested != null) {
                check(!(lastStrong && requested.strong), "two strong episodes in a row: request at " + t);
                check(lastEndByKind[requested.kind.ordinal()] < 0
                                || t - lastEndByKind[requested.kind.ordinal()]
                                >= ObserverTuning.EP_COOLDOWN[requested.kind.ordinal()],
                        "cooldown violated for requested " + requested.kind + " at " + t);
                check(stage >= ObserverTuning.EP_MIN_STAGE[requested.kind.ordinal()],
                        "requested below min stage: " + requested.kind);
                if (lastEnd >= 0) maxGap = Math.max(maxGap, (int) (t - lastEnd));
                float before = ObserverDirector.budget();
                check(ObserverDirector.accept(requested), "requested plan must accept");
                float expected = Math.max(0f, before - ObserverTuning.EP_COST[requested.kind.ordinal()]);
                check(Math.abs(ObserverDirector.budget() - expected) < 0.001f,
                        "budget spend mismatch for requested " + requested.kind);
                if (requested.strong) {
                    check(ObserverDirector.strongRecent()
                                    <= ObserverTuning.STRONG_PER_WINDOW_BY_STAGE[stage],
                            "strong window exceeded on request at " + t);
                }
                continue;
            }

            for (ObserverDirector.Kind kind : new ObserverDirector.Kind[]{
                    ObserverDirector.Kind.GHOST, ObserverDirector.Kind.DOOR,
                    ObserverDirector.Kind.GLITCH, ObserverDirector.Kind.CHASE}) {
                ObserverDirector.Plan plan = ObserverDirector.offered(kind);
                if (plan == null) continue;
                if (!plan.forced) {
                    check(ObserverDirector.silence() == 0, "offer while silence: " + ObserverDirector.silence());
                    check(lastEndByKind[kind.ordinal()] < 0
                                    || t - lastEndByKind[kind.ordinal()]
                                    >= ObserverTuning.EP_COOLDOWN[kind.ordinal()],
                            "cooldown violated for " + kind + " at " + t);
                    check(stage >= ObserverTuning.EP_MIN_STAGE[kind.ordinal()], "offer below min stage: " + kind);
                    check(!(lastStrong && plan.strong), "two strong episodes in a row: " + kind + " at " + t);
                    if (lastEnd >= 0) maxGap = Math.max(maxGap, (int) (t - lastEnd));
                }
                float before = ObserverDirector.budget();
                check(ObserverDirector.accept(plan), "offered plan must accept");
                if (plan.forced) check(ObserverDirector.strongRecent() >= 1, "forced strong must enter the window");
                float expected = Math.max(0f, before - ObserverTuning.EP_COST[kind.ordinal()]);
                check(Math.abs(ObserverDirector.budget() - expected) < 0.001f, "budget spend mismatch for " + kind);
                if (plan.strong && !plan.forced) {
                    check(ObserverDirector.strongRecent()
                                    <= ObserverTuning.STRONG_PER_WINDOW_BY_STAGE[stage],
                            "strong window exceeded on start at " + t);
                }
                break;
            }
        }

        check(episodes >= 10, "simulation produced too few episodes: " + episodes);
        check(strongEpisodes >= 1, "simulation produced no strong episodes");
        check(maxGap <= 24000, "episode window is empty for too long: " + maxGap + " ticks");
        check(ObserverDirector.current() == null || ObserverDirector.current().ticks <= 3000,
                "episode is stuck");
    }
}
