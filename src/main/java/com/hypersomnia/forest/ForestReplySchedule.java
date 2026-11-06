package com.hypersomnia.forest;

import com.hypersomnia.ForestReplyTuning;

/**
 * Только собственные лимиты события. Часы — overworld.getGameTime(), не dayTime:
 * сон и /time не сокращают ожидание. Чистый Java-класс, проверяется без Minecraft.
 */
public final class ForestReplySchedule {
    private long nextAttempt;
    private long nextEvent;

    public ForestReplySchedule() {}

    public ForestReplySchedule(long nextAttempt, long nextEvent) {
        this.nextAttempt = Math.max(0L, nextAttempt);
        this.nextEvent = Math.max(0L, nextEvent);
    }

    public long nextAttempt() { return nextAttempt; }
    public long nextEvent() { return nextEvent; }

    public boolean canAttempt(long now) {
        return now >= 0 && now >= nextAttempt && now >= nextEvent;
    }

    /** В том числе неудачный бросок или отказ клиента расходует только эту суточную попытку. */
    public boolean consumeAttempt(long now) {
        if (!canAttempt(now)) return false;
        nextAttempt = add(now, ForestReplyTuning.DAY_TICKS);
        return true;
    }

    /** Полный кулдаун устанавливается только после подтверждения первого звука. */
    public void recordStarted(long now, int days) {
        int boundedDays = Math.clamp(days,
                ForestReplyTuning.COOLDOWN_DAYS_MIN, ForestReplyTuning.COOLDOWN_DAYS_MAX);
        nextEvent = Math.max(nextEvent, add(Math.max(0L, now),
                boundedDays * ForestReplyTuning.DAY_TICKS));
    }

    public void reset() {
        nextAttempt = 0L;
        nextEvent = 0L;
    }

    private static long add(long value, long delta) {
        return value > Long.MAX_VALUE - delta ? Long.MAX_VALUE : value + delta;
    }
}