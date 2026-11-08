package com.hypersomnia.lab;

import com.hypersomnia.document.WorldVariant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Шифры лаборатории по мирам: ответ выводится из зерна сохранения (LabSavedData.cipherSeed) и ключа шифра.
 * Общий код сервера и клиента, без клиентских импортов: сервер проверяет ответ, клиент подставляет тот же вариант
 * в текст при отрисовке (постройки не меняются, rebuild не нужен). Таблицы и ключи — в LabTuning.
 */
public final class LabVariants {
    private LabVariants() {}

    // ---------------- MAIN FEED: порядок рубильников (бланк SW-B1-01) ----------------

    /** Действующий порядок (ревизия C): рубильники 1..FEEDERS в порядке замыкания. Никогда не 1-2-3-...: это ревизия A. */
    public static int[] feedOrder(long seed) {
        int[] o = WorldVariant.order(seed, LabTuning.FEED_KEY, LabTuning.FEEDERS);
        for (int i = 0; i < o.length; i++) o[i]++;
        if (identity(o)) swap(o, 0, 1);
        return o;
    }

    /** Порядок ревизии rev: 'C' действующий, 'B' старая ревизия (тот же порядок, одна пара соседних шагов переставлена),
     *  'A' первая ревизия (1-2-3-4-5). */
    public static int[] feedRevision(long seed, char rev) {
        int[] c = feedOrder(seed);
        if (rev == 'C') return c;
        if (rev == 'A') {
            int[] a = new int[c.length];
            for (int i = 0; i < a.length; i++) a[i] = i + 1;
            return a;
        }
        Random r = new Random(seed ^ (LabTuning.FEED_KEY + "/B").hashCode() * 0x9E3779B97F4A7C15L);
        int start = r.nextInt(c.length - 1);
        for (int k = 0; k < c.length - 1; k++) {
            int[] b = c.clone();
            int i = (start + k) % (c.length - 1);
            swap(b, i, i + 1);
            if (!identity(b)) return b;
        }
        return c.clone(); // недостижимо при FEEDERS >= 3
    }

    /** Ответ щита в виде LabSavedData.breakerSeq (цифры в порядке замыкания), например 31524. */
    public static int feedCode(long seed) {
        int v = 0;
        for (int d : feedOrder(seed)) v = v * 10 + d;
        return v;
    }

    /** Строка порядка в бланке: «3 - 1 - 5 - 2 - 4». */
    public static String feedText(long seed, char rev) {
        StringBuilder sb = new StringBuilder();
        for (int d : feedRevision(seed, rev)) sb.append(sb.isEmpty() ? "" : " - ").append(d);
        return sb.toString();
    }

    /** Ревизия бланка по id записи архива (sw_b1_01a/b/c) или 0. */
    public static char switchingRevision(String recordId) {
        return switch (recordId) {
            case "sw_b1_01a" -> 'A';
            case "sw_b1_01b" -> 'B';
            case "sw_b1_01c" -> 'C';
            default -> 0;
        };
    }

    // ---------------- Входной замок: какая запись журнала ворот даёт ночной код ----------------

    /** Номер правила письма g_keypad (0..HATCH_CODES.length-1). 0 — прежнее «последний вход». */
    public static int hatchRule(long seed) {
        return WorldVariant.order(seed, LabTuning.HATCH_KEY, LabTuning.HATCH_CODES.length)[0];
    }

    /** Код входного замка: во сне журнал ворот другой (все записи 03:13), поэтому там всегда HATCH_DREAM_CODE. */
    public static int hatchCode(long seed, boolean dream) {
        return dream ? LabTuning.HATCH_DREAM_CODE : LabTuning.HATCH_CODES[hatchRule(seed)];
    }

    // ---------------- Шифр 4: приказ bay_transfer + «the far door reads backwards» ----------------

    /**
     * Минуты от полуночи, от BAY_MIN до BAY_MAX включительно. Время, цифры которого читаются одинаково с обеих сторон
     * (03:30, 04:40, 05:50), сдвигается на минуту внутрь диапазона: иначе правило записки ничего не меняет.
     */
    public static int bayMinutes(long seed) {
        Random r = new Random(seed ^ LabTuning.BAY_KEY.hashCode() * 0x9E3779B97F4A7C15L);
        int m = LabTuning.BAY_MIN + r.nextInt(LabTuning.BAY_MAX - LabTuning.BAY_MIN + 1);
        if (palindrome(digits(m))) m += m < LabTuning.BAY_MAX ? 1 : -1;
        return m;
    }

    /** «04:12». */
    public static String bayTime(long seed) {
        int m = bayMinutes(seed);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", m / 60, m % 60);
    }

    /** Код решётки блока 47-48 (LOCK_BAY): цифры времени перевода задом наперёд, 04:12 -> 2140. */
    public static String bayCode(long seed) {
        return new StringBuilder(digits(bayMinutes(seed))).reverse().toString();
    }

    /** Цифры времени без двоеточия: 252 -> «0412». */
    private static String digits(int minutes) {
        return String.format(java.util.Locale.ROOT, "%02d%02d", minutes / 60, minutes % 60);
    }

    private static boolean palindrome(String s) {
        return new StringBuilder(s).reverse().toString().equals(s);
    }

    // ---------------- Шифр 5: план зала наблюдения (plan_l5) ----------------

    /**
     * План зала наблюдения: сетка POSTS_W x POSTS_H клеток, (0, 0) — левый верхний угол, север сверху; вход — проём
     * посередине верхней стены. Камера i: узел внутри зала (camX, camZ) и румб взгляда (dirX, dirZ, шаг -1..1).
     * Посты — узлы стен (не углы и не проём), postNum — цифра поста. front[i] — пост, куда смотрит камера по бумаге
     * (конец её линии), back[i] — пост на той же прямой за спиной камеры (ответ). Все посты разные и не стоят рядом.
     */
    public record PostPlan(int[] camX, int[] camZ, int[] dirX, int[] dirZ,
                           int[] postX, int[] postZ, int[] postNum, int[] front, int[] back) {
        public int cameras() {
            return camX.length;
        }

        public int posts() {
            return postX.length;
        }
    }

    private static final Map<Long, PostPlan> PLANS = new ConcurrentHashMap<>();
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    /** План для зерна (кэш: раскладка одна и та же при каждом вызове). */
    public static PostPlan posts(long seed) {
        return PLANS.computeIfAbsent(seed, LabVariants::layoutPosts);
    }

    /** Ответ замка зала наблюдения (LOCK_NURSERY): цифры постов за спиной камер 01..04. */
    public static String postCode(long seed) {
        PostPlan p = posts(seed);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.cameras(); i++) sb.append(p.postNum()[p.back()[i]]);
        return sb.toString();
    }

    /** Код «по бумаге» (посты на концах линий камер 01..04): неверный ответ, для проверок и отладки. */
    public static String planCode(long seed) {
        PostPlan p = posts(seed);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.cameras(); i++) sb.append(p.postNum()[p.front()[i]]);
        return sb.toString();
    }

    /** Узел стены, в который упирается луч из (x, z) по румбу (dx, dz); null — угол зала (там постов нет). */
    public static int[] wallHit(int x, int z, int dx, int dz) {
        int w = LabTuning.POSTS_W, h = LabTuning.POSTS_H;
        while (x > 0 && x < w && z > 0 && z < h) {
            x += dx;
            z += dz;
        }
        if ((x == 0 || x == w) && (z == 0 || z == h)) return null;
        return new int[]{x, z};
    }

    /** Узел верхней стены в проёме входа (там постов нет). */
    public static boolean entrance(int x, int z) {
        return z == 0 && Math.abs(2 * x - LabTuning.POSTS_W) <= 2;
    }

    private static PostPlan layoutPosts(long seed) {
        // s = 0 — последовательность зерна мира; дальше запасные (на практике не нужны: VariantSelfTest проверяет тысячи зёрен)
        for (long s = 0; ; s++) {
            Random r = new Random((seed ^ LabTuning.POSTS_KEY.hashCode() * 0x9E3779B97F4A7C15L) + s * 0x632BE59BD9B4E019L);
            for (int a = 0; a < LabTuning.POSTS_ATTEMPTS; a++) {
                PostPlan p = tryPosts(r);
                if (p != null) return p;
            }
        }
    }

    private static PostPlan tryPosts(Random r) {
        int w = LabTuning.POSTS_W, h = LabTuning.POSTS_H, n = LabTuning.POSTS_CAMERAS;
        int[] cx = new int[n], cz = new int[n], dx = new int[n], dz = new int[n];
        List<int[]> used = new ArrayList<>(); // посты: front0, back0, front1, back1, ...
        for (int i = 0; i < n; i++) {
            boolean placed = false;
            for (int t = 0; t < 64 && !placed; t++) {
                int x = 1 + r.nextInt(w - 1), z = 1 + r.nextInt(h - 1);
                int[] d = DIRS[r.nextInt(DIRS.length)];
                int[] f = wallHit(x, z, d[0], d[1]), b = wallHit(x, z, -d[0], -d[1]);
                if (f == null || b == null || entrance(f[0], f[1]) || entrance(b[0], b[1])) continue;
                if (steps(x, z, f) < LabTuning.POSTS_MIN_RAY || steps(x, z, b) < LabTuning.POSTS_MIN_RAY) continue;
                if (!far(f, used) || !far(b, used) || dist(f, b) < LabTuning.POSTS_MIN_GAP) continue;
                boolean clash = false; // камеры не стоят на чужих линиях: каждая линия ведёт только от своей камеры
                for (int j = 0; j < i && !clash; j++)
                    clash = collinear(cx[j], cz[j], dx[j], dz[j], x, z) || collinear(x, z, d[0], d[1], cx[j], cz[j]);
                if (clash) continue;
                cx[i] = x;
                cz[i] = z;
                dx[i] = d[0];
                dz[i] = d[1];
                used.add(f);
                used.add(b);
                placed = true;
            }
            if (!placed) return null;
        }
        // ложные посты: свободные узлы стен по кругу в случайном порядке, не рядом с другими постами
        List<int[]> slots = new ArrayList<>();
        for (int x = 1; x < w; x++) {
            if (!entrance(x, 0)) slots.add(new int[]{x, 0});
            slots.add(new int[]{x, h});
        }
        for (int z = 1; z < h; z++) {
            slots.add(new int[]{0, z});
            slots.add(new int[]{w, z});
        }
        for (int k = slots.size() - 1; k > 0; k--) {
            int j = r.nextInt(k + 1);
            int[] tmp = slots.get(k);
            slots.set(k, slots.get(j));
            slots.set(j, tmp);
        }
        for (int[] s : slots) {
            if (used.size() >= LabTuning.POSTS_TOTAL) break;
            if (far(s, used)) used.add(s);
        }
        // номера постов: разные цифры в случайном порядке
        int[] digits = new int[10];
        for (int k = 0; k < 10; k++) digits[k] = k;
        for (int k = 9; k > 0; k--) {
            int j = r.nextInt(k + 1);
            int tmp = digits[k];
            digits[k] = digits[j];
            digits[j] = tmp;
        }
        int m = used.size();
        int[] px = new int[m], pz = new int[m], num = new int[m], front = new int[n], back = new int[n];
        for (int k = 0; k < m; k++) {
            px[k] = used.get(k)[0];
            pz[k] = used.get(k)[1];
            num[k] = digits[k];
        }
        for (int i = 0; i < n; i++) {
            front[i] = 2 * i;
            back[i] = 2 * i + 1;
        }
        return new PostPlan(cx, cz, dx, dz, px, pz, num, front, back);
    }

    private static int steps(int x, int z, int[] p) {
        return Math.max(Math.abs(p[0] - x), Math.abs(p[1] - z));
    }

    private static double dist(int[] a, int[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    private static boolean far(int[] p, List<int[]> used) {
        for (int[] u : used) if (dist(p, u) < LabTuning.POSTS_MIN_GAP) return false;
        return true;
    }

    /** Точка (px, pz) лежит на прямой через (x, z) с направлением (dx, dz). */
    private static boolean collinear(int x, int z, int dx, int dz, int px, int pz) {
        return (px - x) * dz - (pz - z) * dx == 0;
    }

    // ---------------- Форма 12-B: слово контрольной передачи (Морзе) ----------------

    /** Номер слова 0..MORSE_WORDS.length-1; 0 — прежнее HELP. Слова на других языках — lang hypersomnia.variant.morse.<n>. */
    public static int morseWord(long seed) {
        return WorldVariant.order(seed, LabTuning.MORSE_KEY, LabTuning.MORSE_WORDS.length)[0];
    }

    // ---------------- Шифр 7: хранилище B2 (ряды и лист проверки копии B2-CHK) ----------------

    /** Пустое место каждого ряда, 0..STACK_PLACES-1 (место 1 — у таблички ряда): коробки копии 03:13, не возвращённые. */
    public static int[] stacksGaps(long seed) {
        Random r = WorldVariant.random(seed, LabTuning.STACKS_KEY);
        int[] g = new int[LabTuning.STACK_ROWS];
        for (int i = 0; i < g.length; i++) g[i] = r.nextInt(LabTuning.STACK_PLACES);
        return g;
    }

    /** Лист B2-CHK: контрольная цифра каждого места, [ряд][место]. */
    public static int[][] stacksSheet(long seed) {
        Random r = WorldVariant.random(seed, LabTuning.STACKS_SHEET_KEY);
        int[][] s = new int[LabTuning.STACK_ROWS][LabTuning.STACK_PLACES];
        for (int[] row : s) for (int j = 0; j < row.length; j++) row[j] = r.nextInt(10);
        return s;
    }

    /** Код LIFT ACCESS B2: цифры листа на пустых местах, ряд 1 первым. */
    public static String stacksCode(long seed) {
        int[] g = stacksGaps(seed);
        int[][] s = stacksSheet(seed);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < g.length; i++) sb.append(s[i][g[i]]);
        return sb.toString();
    }

    // ---------------- Серверная B3: удержанные буферы (строки HOLD в BUFFER.LOG, лампы стоек) ----------------

    /** Номера стоек (1..RACKS) в порядке удержания буфера: первые RACK_HELD — SOURCE 47, последняя — копия B2. */
    public static int[] rackHold(long seed) {
        int[] o = WorldVariant.order(seed, LabTuning.RACK_KEY, LabTuning.RACKS);
        int[] out = new int[LabTuning.RACK_HELD + 1];
        for (int i = 0; i < out.length; i++) out[i] = o[i] + 1;
        return out;
    }

    /** Стойка n удерживает буфер (лампа HOLD). */
    public static boolean rackHeld(long seed, int rack) {
        for (int n : rackHold(seed)) if (n == rack) return true;
        return false;
    }

    /** Адрес буфера на лампах стойки n (1..RACKS): у удержанных 1-9 (ни одной горящей лампы не бывает), у остальных 0-15. */
    public static int rackValue(long seed, int rack) {
        Random r = WorldVariant.random(seed, LabTuning.RACK_LAMPS_KEY);
        int v = 0;
        for (int n = 1; n <= rack; n++) v = rackHeld(seed, n) ? 1 + r.nextInt(9) : r.nextInt(16);
        return v;
    }

    // ---------------- Шифр 8: антенна ANT-1 на крыше (LabAntenna) ----------------

    /** Азимут, на который тарелку отвернули вручную в 03:13 (с него она начинает): 0..345, кратно ANTENNA_STEP; 0 = север. */
    public static int antennaStart(long seed) {
        int steps = 360 / LabTuning.ANTENNA_STEP;
        return WorldVariant.random(seed, LabTuning.ANTENNA_KEY).nextInt(steps) * LabTuning.ANTENNA_STEP;
    }

    /** Азимут ретранслятора (строка UPLINK в BUFFER.LOG): на 180° от начального. На нём буферы уходят, панель B3 открывается. */
    public static int antennaTarget(long seed) {
        return (antennaStart(seed) + 180) % 360;
    }

    // ---------------- Шифр 9: код директора для B5 (записка 2D, группы счёта) ----------------

    /**
     * Код LIFT ACCESS B4 (код директора): четыре разные цифры 1..9. Ноль не используется: цифра рисуется группой
     * чёрточек, а пустую группу не прочитать. Рендер — DocumentDefs.Tally (записка 2D).
     */
    public static String wardCode(long seed) {
        int[] o = WorldVariant.order(seed, LabTuning.WARD_KEY, 9);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < LabTuning.CODE_DIGITS; i++) sb.append(o[i] + 1);
        return sb.toString();
    }

    private static boolean identity(int[] o) {
        for (int i = 0; i < o.length; i++) if (o[i] != i + 1) return false;
        return true;
    }

    private static void swap(int[] a, int i, int j) {
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
    }
}
