package com.hypersomnia.document;

import java.util.Random;

/**
 * Варианты шифров по мирам: всё выводится из одного зерна сохранения (LabSavedData.cipherSeed) и ключа шифра.
 * Общий код сервера и клиента, без клиентских импортов: сервер проверяет ответ, клиент показывает тот же вариант.
 * java.util.Random и String.hashCode заданы спецификацией Java, поэтому результат одинаков на любой JVM.
 */
public final class WorldVariant {
    private WorldVariant() {}

    /** Перестановка 0..n-1 для шифра key: a[p] = номер элемента, стоящего на месте p (Фишер — Йетс). */
    public static int[] order(long seed, String key, int n) {
        int[] a = new int[Math.max(0, n)];
        for (int i = 0; i < a.length; i++) a[i] = i;
        Random r = random(seed, key);
        for (int i = a.length - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
        return a;
    }

    /** Генератор шифра key в мире с зерном seed (тот же, что у order): для цифр и таблиц вариантов. */
    public static Random random(long seed, String key) {
        return new Random(seed ^ (key.hashCode() * 0x9E3779B97F4A7C15L));
    }
}
