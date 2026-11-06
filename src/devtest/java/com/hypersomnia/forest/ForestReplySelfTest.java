package com.hypersomnia.forest;

import com.hypersomnia.ForestReplyTuning;

/** Чистые детерминированные проверки без зависимостей, не входят в JAR мода. Запуск описан в CHANGES. */
public final class ForestReplySelfTest {
    private static int checks;

    public static void main(String[] args) {
        ForestReplySchedule limits = new ForestReplySchedule();
        check(limits.canAttempt(0), "new player can try");
        check(limits.consumeAttempt(100), "first roll consumed");
        check(!limits.consumeAttempt(100), "not every block/tick");
        check(!limits.canAttempt(24_099), "daily limit before boundary");
        check(limits.canAttempt(24_100), "daily boundary inclusive");
        check(limits.nextEvent() == 0, "failed/pre-start refusal has no multi-day cooldown");
        ForestReplySchedule restored = new ForestReplySchedule(limits.nextAttempt(), limits.nextEvent());
        check(!restored.canAttempt(200), "daily limit survives serialization/relogin");
        restored.recordStarted(200, 3);
        check(restored.nextEvent() == 72_200, "3 elapsed game days from first sound");
        check(!restored.canAttempt(72_199), "full cooldown boundary");
        check(restored.canAttempt(72_200), "expiry permits a roll, not an event");
        check(!ForestReplyTuning.chancePasses(0.8), "expired cooldown does not force event");
        check(restored.consumeAttempt(72_200), "next eligible session rolls once");
        restored.recordStarted(72_300, 5);
        check(restored.nextEvent() == 192_300, "5 day upper boundary");
        ForestReplySchedule other = new ForestReplySchedule();
        check(other.canAttempt(201), "per-player isolation");
        check(!restored.canAttempt(72_301), "another player cannot reset own limits");
        check(restored.nextEvent() == 192_300, "dayTime not part of state or API");
        restored.reset();
        check(restored.canAttempt(0), "debug resets own limits");
        check(new ForestReplySchedule(-5, -9).canAttempt(0), "sanitize old/bad save");
        ForestReplySchedule extreme = new ForestReplySchedule();
        extreme.consumeAttempt(Long.MAX_VALUE - 10);
        extreme.recordStarted(Long.MAX_VALUE - 10, 99);
        check(extreme.nextAttempt() == Long.MAX_VALUE && extreme.nextEvent() == Long.MAX_VALUE, "no timer overflow");

        check(ForestReplyTuning.chancePasses(0), "chance lower boundary");
        check(ForestReplyTuning.chancePasses(0.199999), "20 percent inside");
        check(!ForestReplyTuning.chancePasses(0.2), "20 percent exclusive upper boundary");
        check(!ForestReplyTuning.chancePasses(Double.NaN), "invalid draw rejected");
        check(!ForestReplyTuning.chancePasses(-1), "negative draw rejected");
        int successful = 0;
        for (int i = 0; i < 1_000; i++) if (ForestReplyTuning.chancePasses(i / 1_000.0)) successful++;
        check(successful == 200, "exact deterministic probability threshold");

        ForestReplySession session = new ForestReplySession(100);
        check(!session.ready(1_000), "no hit-only session");
        session.recordBreak(100);
        check(session.gap() == ForestReplyTuning.GAP_DEFAULT, "one log uses fallback rhythm");
        check(!session.ready(199) && session.ready(200), "idle delay boundary");
        session.recordBreak(190);
        check(!session.ready(200) && session.ready(290), "renewed chopping extends only own wait");
        check(session.gap() == ForestReplyTuning.GAP_MAX, "very slow gap bounded");
        session.recordBreak(210);
        check(session.gap() == 20, "copy recent chopping rhythm");
        session.recordBreak(210);
        check(session.gap() == 20, "same-tick tree feller keeps rhythm");
        session.recordBreak(212);
        check(session.gap() == ForestReplyTuning.GAP_MIN, "fast gap bounded");
        session.recordBreak(200);
        check(session.lastBreak() == 212, "out-of-order observation ignored");
        check(new ForestReplySession(1).pause() == 80, "4 second lower pause");
        check(new ForestReplySession(999).pause() == 140, "7 second upper pause");
        System.out.println("ForestReplySelfTest: " + checks + " checks passed.");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}