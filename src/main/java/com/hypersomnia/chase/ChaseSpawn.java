package com.hypersomnia.chase;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Горизонтальные смещения для постановки фигуры (ChaseHandler.spotBehind/spotAround) без мира и игрока:
 * так порядок и геометрию старта можно проверить self-test'ом. Первое смещение — строго за спиной на
 * ChaseTuning.SPAWN_BEHIND_DIST, дальше — развороты до ±60° и чуть ближе/дальше, если сзади стена или обрыв.
 */
final class ChaseSpawn {
    private ChaseSpawn() {}

    /** Углы от направления «за спиной»: сначала строго сзади, потом шире, но ещё вне взгляда. */
    static final double[] BEHIND_ANGLES = {0, 15, -15, 30, -30, 45, -45, 60, -60};
    /** Поправки к SPAWN_BEHIND_DIST: сначала ровно dist, затем ближе/дальше. */
    static final double[] BEHIND_DISTANCE_STEPS = {0, -2, 2, -4, -6};
    /** Кольцо тестовой фигуры: перед/за спиной и по бокам, дистанции от ближней к дальней. */
    static final double[] AROUND_ANGLES = {0, 20, -20, 40, -40, 60, -60, 90, -90, 180};
    static final double[] AROUND_DISTANCES = {6, 7, 8, 5, 9};
    /** Ближе этого старт не ставится: фигура не должна появляться вплотную без засады. */
    static final double MIN_BEHIND_DIST = 4.0;

    /** Направление взгляда без высоты; при взгляде строго вверх/вниз — на юг, чтобы смещение не выродилось. */
    static Vec3 horizontal(Vec3 look) {
        Vec3 dir = new Vec3(look.x, 0.0, look.z);
        return dir.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
    }

    /** Смещения от ног игрока: за спиной, от строгого «сзади» к разворотам и другим дистанциям. */
    static List<Vec3> behind(Vec3 look, double dist) {
        Vec3 dir = horizontal(look).scale(-1);
        List<Vec3> out = new ArrayList<>();
        for (double step : BEHIND_DISTANCE_STEPS) {
            double d = dist + step;
            if (d < MIN_BEHIND_DIST) continue;
            addRing(out, dir, d, BEHIND_ANGLES);
        }
        return out;
    }

    /** Смещения кольца вокруг игрока: front — перед ним (наблюдатель виден), иначе за спиной (погоня). */
    static List<Vec3> around(Vec3 look, boolean front) {
        Vec3 dir = horizontal(look);
        if (!front) dir = dir.scale(-1);
        List<Vec3> out = new ArrayList<>();
        for (double d : AROUND_DISTANCES) addRing(out, dir, d, AROUND_ANGLES);
        return out;
    }

    private static void addRing(List<Vec3> out, Vec3 dir, double dist, double[] angles) {
        for (double deg : angles) {
            double rad = Math.toRadians(deg);
            double dx = dir.x * Math.cos(rad) - dir.z * Math.sin(rad);
            double dz = dir.x * Math.sin(rad) + dir.z * Math.cos(rad);
            out.add(new Vec3(dx * dist, 0.0, dz * dist));
        }
    }
}
