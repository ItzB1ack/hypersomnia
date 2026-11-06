package com.hypersomnia.forest;

import com.hypersomnia.ForestReplyTuning;

/** Пачка подтверждённых срубленных брёвен: новый слом отодвигает только собственный ответ. */
public final class ForestReplySession {
    private final int pause;
    private long lastBreak = -1L;
    private int gap = ForestReplyTuning.GAP_DEFAULT;

    public ForestReplySession(int pause) {
        this.pause = Math.clamp(pause, ForestReplyTuning.PAUSE_MIN, ForestReplyTuning.PAUSE_MAX);
    }

    public void recordBreak(long now) {
        if (now < 0 || now < lastBreak) return;
        // Tree-feller может сломать несколько брёвен за один тик: нулевой интервал не копируем.
        if (lastBreak >= 0 && now > lastBreak) gap = ForestReplyTuning.clampGap(now - lastBreak);
        lastBreak = now;
    }

    public boolean ready(long now) {
        return lastBreak >= 0 && now >= lastBreak && now - lastBreak >= pause;
    }

    public int gap() { return gap; }
    public int pause() { return pause; }
    public long lastBreak() { return lastBreak; }
}