package com.hypersomnia.scene;

/**
 * Учёт одновременных сцен, которые временно запрещают пропуск ночи.
 *
 * <p>Правило сна в Minecraft общее для сервера, поэтому несколько игроков могут держать
 * его одновременно. Первый держатель запоминает исходное значение, последний — возвращает;
 * промежуточные не трогают правило. Класс не зависит от Minecraft и проверяется self-тестом.
 */
public final class NightSkipGate {
    private int holds;
    private int saved = -1;

    /**
     * Занять блокировку.
     *
     * @param current текущее значение правила
     * @return значение, которое нужно сохранить и выставить в 101, или -1, если блокировка уже занята
     */
    public int acquire(int current) {
        if (holds++ > 0) return -1;
        saved = current;
        return current;
    }

    /**
     * Освободить блокировку.
     *
     * @return значение для восстановления или -1, если держатели ещё остались
     */
    public int release() {
        if (holds == 0) return -1;
        if (--holds > 0) return -1;
        int value = saved;
        saved = -1;
        return value;
    }

    /**
     * Снять все блокировки (остановка сервера).
     *
     * @return значение для восстановления или -1, если блокировка не стояла
     */
    public int forceRelease() {
        if (holds == 0) return -1;
        holds = 0;
        int value = saved;
        saved = -1;
        return value;
    }

    public int holds() { return holds; }
}
