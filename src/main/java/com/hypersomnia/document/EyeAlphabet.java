package com.hypersomnia.document;

import java.util.Random;

/**
 * Глазной алфавит: знак = зрачок (3) x веки (2) x наклон (3) x метка над глазом (2) = 36 знаков.
 * Латиница (26) и кириллица (32, Ё = Е) используют разные фиксированные перестановки: ключ одного языка не подходит другому.
 * Без клиентских импортов.
 */
public final class EyeAlphabet {
    private EyeAlphabet() {}

    /** pupil: смещение зрачка по горизонтали (-1.2..1.2), open: раскрытие век, tilt: наклон глаза в градусах. */
    public record Sym(float pupil, float open, float tilt, boolean mark) {}

    private static final float[] PUPIL = {-1.3f, 0f, 1.3f};
    private static final float[] OPEN = {1.0f, 0.55f};
    private static final float[] TILT = {0f, 28f, -28f};
    /** Метка над глазом (есть или нет) удваивает число знаков. */
    private static final int COUNT = PUPIL.length * OPEN.length * TILT.length * 2;

    private static final int[] LATIN = shuffled(47L);
    private static final int[] CYRILLIC = shuffled(1947L);

    private static int[] shuffled(long seed) {
        int[] a = new int[COUNT];
        for (int i = 0; i < COUNT; i++) a[i] = i;
        Random r = new Random(seed);
        for (int i = COUNT - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
        return a;
    }

    /** Знак буквы или null (пробел, знаки препинания, цифры). */
    public static Sym symbol(char c) {
        c = Character.toUpperCase(c);
        if (c == '\u0401') c = '\u0415'; // Ё -> Е
        int idx;
        if (c >= 'A' && c <= 'Z') idx = LATIN[c - 'A'];
        else if (c >= '\u0410' && c <= '\u042F') idx = CYRILLIC[c - '\u0410'];
        else return null;
        return byIndex(idx);
    }

    /** Сколько всего знаков (страница-ключ в дневнике перебирает их по кругу). */
    public static int count() { return COUNT; }

    /** Номер знака буквы или -1: нужен для обратной замены знака на уже известную букву. */
    public static int indexOf(char c) {
        c = Character.toUpperCase(c);
        if (c == '\u0401') c = '\u0415'; // Ё -> Е
        if (c >= 'A' && c <= 'Z') return LATIN[c - 'A'];
        if (c >= '\u0410' && c <= '\u042F') return CYRILLIC[c - '\u0410'];
        return -1;
    }

    /** Знак по номеру 0..count()-1; вне диапазона заворачивается. */
    public static Sym byIndex(int idx) {
        idx = Math.floorMod(idx, COUNT);
        int pupil = idx % PUPIL.length;
        int lid = (idx / PUPIL.length) % OPEN.length;
        int tilt = (idx / (PUPIL.length * OPEN.length)) % TILT.length;
        boolean mark = idx >= PUPIL.length * OPEN.length * TILT.length;
        return new Sym(PUPIL[pupil], OPEN[lid], TILT[tilt], mark);
    }
}