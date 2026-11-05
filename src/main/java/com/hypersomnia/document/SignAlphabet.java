package com.hypersomnia.document;

/**
 * Алфавит черт (шифр 2): знак = черты (1-3) x подставка снизу (нет/есть) x перечерк (нет / поперёк / наискось, как пятая
 * черта счёта) x точка над знаком (нет/есть) = 36 знаков. Мотив — счёт чёрточками, как «47» в следах. Глаз нет.
 * Какой знак у какой буквы — своё в каждом мире (зерно LabSavedData.cipherSeed, WorldVariant). Латиница (26) и кириллица
 * (32, Ё = Е) — разные перестановки: ключ одного языка не подходит другому. Номер знака 0..35 от мира не зависит,
 * поэтому сервер (таблица ключа в дневнике) проверяет только букву и номер. Без клиентских импортов.
 */
public final class SignAlphabet {
    private SignAlphabet() {}

    /** strokes 1..3; base — черта-подставка; cross 0 нет, 1 поперёк, 2 наискось; mark — точка сверху. */
    public record Sym(int strokes, boolean base, int cross, boolean mark) {}

    private static final int STROKES = 3;
    private static final int CROSSES = 3;
    private static final int COUNT = STROKES * 2 * CROSSES * 2;
    private static final String KEY_LATIN = "signs_latin";
    private static final String KEY_CYRILLIC = "signs_cyrillic";

    private static long cachedSeed;
    private static int[] latin, cyrillic;

    private static synchronized int[] table(long seed, boolean cyr) {
        if (latin == null || seed != cachedSeed) {
            latin = WorldVariant.order(seed, KEY_LATIN, COUNT);
            cyrillic = WorldVariant.order(seed, KEY_CYRILLIC, COUNT);
            cachedSeed = seed;
        }
        return cyr ? cyrillic : latin;
    }

    private static char norm(char c) {
        c = Character.toUpperCase(c);
        return c == '\u0401' ? '\u0415' : c; // Ё -> Е
    }

    /** Буква алфавита (латиница или кириллица); пробел, знаки и цифры — нет. */
    public static boolean isLetter(char c) {
        c = norm(c);
        return (c >= 'A' && c <= 'Z') || (c >= '\u0410' && c <= '\u042F');
    }

    /** Номер знака буквы в мире seed или -1. */
    public static int indexOf(long seed, char c) {
        c = norm(c);
        if (c >= 'A' && c <= 'Z') return table(seed, false)[c - 'A'];
        if (c >= '\u0410' && c <= '\u042F') return table(seed, true)[c - '\u0410'];
        return -1;
    }

    /** Знак буквы в мире seed или null. */
    public static Sym symbol(long seed, char c) {
        int i = indexOf(seed, c);
        return i < 0 ? null : byIndex(i);
    }

    /** Сколько всего знаков (палитра в дневнике). */
    public static int count() { return COUNT; }

    /** Знак по номеру 0..count()-1; вне диапазона заворачивается. */
    public static Sym byIndex(int idx) {
        idx = Math.floorMod(idx, COUNT);
        int strokes = idx % STROKES + 1;
        boolean base = (idx / STROKES) % 2 == 1;
        int cross = (idx / (STROKES * 2)) % CROSSES;
        boolean mark = idx >= STROKES * 2 * CROSSES;
        return new Sym(strokes, base, cross, mark);
    }
}
