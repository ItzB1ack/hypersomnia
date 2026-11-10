package com.hypersomnia.server;

/**
 * [новое] Чистые формулы рейса лифта (N12): профиль smootherstep, вертикальная дуга, тайминги и
 * безопасная высота выхода. Вынесены из {@link LiftRide}, lab/LabLift и nightmare/ShaftHandler,
 * чтобы их можно было проверить self-test'ом без мира.
 *
 * <p>Профиль — quintic smootherstep {@code 6k^5 - 15k^4 + 10k^3}: в начале и в конце скорость и
 * ускорение равны нулю, поэтому кабина не дёргается на старте и не «щёлкает» при остановке.
 * Дуга задаётся положительным {@code dip} и внутри вычитается: в концах она нулевая, в середине
 * кабина уходит на {@code -dip}.
 */
public final class LiftProfile {
    private LiftProfile() {}

    /** Ограничение прогресса рейса долей [0, 1]. */
    public static double clamp01(double value) {
        return value < 0.0 ? 0.0 : Math.min(value, 1.0);
    }

    /** Профиль smootherstep: s(0)=0, s(1)=1, производные на концах нулевые. */
    public static double smootherstep(double k) {
        double t = clamp01(k);
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    /** Прогресс по номеру тика: t ограничивается 0..moveTicks, moveTicks — не меньше 1. */
    public static double progress(int t, int moveTicks) {
        int total = Math.max(1, moveTicks);
        int clamped = Math.max(0, Math.min(t, total));
        return smootherstep((double) clamped / total);
    }

    /** Вертикальная дуга: 0 в концах, -dip в середине; dip задаётся положительным. */
    public static double arc(double dip, double s) {
        double wave = Math.sin(Math.PI * clamp01(s));
        return -dip * wave * wave;
    }

    /** Сдвиг кабины по вертикали: dy*s + arc. */
    public static double offsetY(double dy, double dip, double s) {
        return dy * clamp01(s) + arc(dip, s);
    }

    /** Итоговая высота игрока не ниже пола целевой кабины: иначе он останется в плите. */
    public static double safeEndY(double rideEndY, double floorY) {
        return Math.max(rideEndY, floorY);
    }

    /**
     * Нужен ли корректирующий телепорт после рейса: игрок утонул в полу или разошёлся с концом
     * больше чем на toleranceSqr (квадрат расстояния по всем трём осям). Любое утопание важнее порога.
     */
    public static boolean snapNeeded(double y, double endY, double dx, double dz, double toleranceSqr) {
        double dy = y - endY;
        return y < endY - 0.01 || dx * dx + dy * dy + dz * dz > toleranceSqr;
    }

    /** Время поездки лабораторного лифта: база плюс тики на блок реальной высоты. */
    public static int labTripTicks(int baseTicks, int perBlock, int fromY, int toY) {
        return baseTicks + Math.abs(toY - fromY) * perBlock;
    }

    /** Время поездки шахты: база плюс надбавка за номер поездки, но не больше потолка. */
    public static int shaftTripTicks(int baseTicks, int extraPerTrip, int extraMax, int done) {
        int extra = Math.min(extraMax, Math.max(0, done) * extraPerTrip);
        return baseTicks + extra;
    }

    /** Тиков движения лабораторного рейса: за вычетом паузы до старта и запаса, но не меньше паузы. */
    public static int labMoveTicks(int totalTicks, int startDelay, int padTicks) {
        return Math.max(startDelay, totalTicks - startDelay - padTicks);
    }

    /** Тиков движения шахты: не меньше distance*stepTicks и не меньше total - pad. */
    public static int shaftMoveTicks(int distance, int stepTicks, int totalTicks, int padTicks) {
        return Math.max(Math.max(1, distance) * Math.max(1, stepTicks), totalTicks - padTicks);
    }
}
