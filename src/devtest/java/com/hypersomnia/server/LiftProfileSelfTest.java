package com.hypersomnia.server;

import com.hypersomnia.lab.LabTuning;
import com.hypersomnia.nightmare.NightmareTuning;
import com.hypersomnia.nightmare.ShaftBuilder;
import net.minecraft.world.phys.Vec3;

/**
 * Проверки чистых формул лифта и шахты (N12): smootherstep, дуга, тайминги, безопасная высота
 * выхода и корректирующий телепорт. Мир не нужен: формулы вынесены в {@link LiftProfile}.
 */
public final class LiftProfileSelfTest {
    private static int checks;

    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    public static void main(String[] args) {
        smootherstep();
        arc();
        timing();
        endY();
        constants();
        shaftGeometry();
        System.out.println("LiftProfileSelfTest: " + checks + " checks passed.");
    }

    // ------------------------------------------------------------------
    // Профиль smootherstep
    // ------------------------------------------------------------------

    private static void smootherstep() {
        check(LiftProfile.smootherstep(0.0) == 0.0, "smootherstep: s(0) must be 0");
        check(Math.abs(LiftProfile.smootherstep(1.0) - 1.0) < 1.0E-12, "smootherstep: s(1) must be 1");
        check(Math.abs(LiftProfile.smootherstep(0.5) - 0.5) < 1.0E-12, "smootherstep: midpoint must be 0.5");
        check(LiftProfile.smootherstep(-1.0) == 0.0, "smootherstep: negative k clamps to 0");
        check(Math.abs(LiftProfile.smootherstep(2.0) - 1.0) < 1.0E-12, "smootherstep: k above 1 clamps to 1");

        double previous = -1.0;
        for (int i = 0; i <= 1000; i++) {
            double s = LiftProfile.smootherstep(i / 1000.0);
            check(s >= 0.0 && s <= 1.0, "smootherstep: value out of [0,1] at " + i);
            check(s >= previous - 1.0E-12, "smootherstep: not monotone at " + i);
            previous = s;
        }

        // Производные на концах нулевые: конечная разность не больше кубического шага.
        double h = 1.0E-4;
        double start = (LiftProfile.smootherstep(h) - LiftProfile.smootherstep(0.0)) / h;
        double end = (LiftProfile.smootherstep(1.0) - LiftProfile.smootherstep(1.0 - h)) / h;
        check(Math.abs(start) < 1.0E-3, "smootherstep: non-zero start velocity: " + start);
        check(Math.abs(end) < 1.0E-3, "smootherstep: non-zero end velocity: " + end);

        check(LiftProfile.progress(0, 100) == 0.0, "progress: tick 0 must be 0");
        check(Math.abs(LiftProfile.progress(100, 100) - 1.0) < 1.0E-12, "progress: last tick must be 1");
        check(Math.abs(LiftProfile.progress(1000, 100) - 1.0) < 1.0E-12, "progress: overshoot clamps to 1");
        check(LiftProfile.progress(-5, 100) == 0.0, "progress: negative tick clamps to 0");
        check(LiftProfile.progress(5, 0) == 1.0, "progress: zero moveTicks behaves as one tick");
    }

    // ------------------------------------------------------------------
    // Вертикальная дуга
    // ------------------------------------------------------------------

    private static void arc() {
        check(LiftProfile.arc(24.0, 0.0) == 0.0, "arc: start must be 0");
        check(Math.abs(LiftProfile.arc(24.0, 1.0)) < 1.0E-9, "arc: end must be 0");
        check(Math.abs(LiftProfile.arc(24.0, 0.5) + 24.0) < 1.0E-9, "arc: middle must reach -dip");
        check(LiftProfile.arc(0.0, 0.5) == 0.0, "arc: zero dip must stay flat");
        double deepest = 0.0;
        for (int i = 0; i <= 100; i++) {
            double a = LiftProfile.arc(24.0, i / 100.0);
            check(a <= 0.0 && a >= -24.0 - 1.0E-9, "arc: outside [-dip,0] at " + i);
            deepest = Math.min(deepest, a);
        }
        check(Math.abs(deepest + 24.0) < 1.0E-9, "arc: maximum depth is not -dip");

        check(LiftProfile.offsetY(16.0, 0.0, 0.0) == 0.0, "offsetY: start must be 0");
        check(Math.abs(LiftProfile.offsetY(16.0, 0.0, 1.0) - 16.0) < 1.0E-12, "offsetY: end must be dy");
        check(Math.abs(LiftProfile.offsetY(16.0, 24.0, 0.5) - (8.0 - 24.0)) < 1.0E-9,
                "offsetY: middle must combine dy and dip");
        check(Math.abs(LiftProfile.offsetY(0.0, 24.0, 0.5) + 24.0) < 1.0E-9,
                "offsetY: pure dip goes down");
    }

    // ------------------------------------------------------------------
    // Тайминги
    // ------------------------------------------------------------------

    private static void timing() {
        check(LiftProfile.labTripTicks(50, 2, 64, 64) == 50, "lab timing: same floor uses base");
        check(LiftProfile.labTripTicks(50, 2, 64, 20) == 50 + 44 * 2, "lab timing: distance in blocks");
        check(LiftProfile.labTripTicks(50, 2, 20, 64) == 50 + 44 * 2, "lab timing: direction must not matter");

        int previous = -1;
        for (int done = 0; done <= 20; done++) {
            int ticks = LiftProfile.shaftTripTicks(80, 20, 160, done);
            check(ticks >= 80, "shaft timing: below base at trip " + done);
            check(ticks <= 80 + 160, "shaft timing: above cap at trip " + done);
            check(ticks >= previous, "shaft timing: not monotone at trip " + done);
            previous = ticks;
        }
        check(LiftProfile.shaftTripTicks(80, 20, 160, 0) == 80, "shaft timing: first trip is base");
        check(LiftProfile.shaftTripTicks(80, 20, 160, 4) == 160, "shaft timing: fourth trip is base + 80");
        check(LiftProfile.shaftTripTicks(80, 20, 160, 100) == 240, "shaft timing: extra is capped");
        check(LiftProfile.shaftTripTicks(80, 20, 160, -3) == 80, "shaft timing: negative trips clamp to base");

        check(LiftProfile.labMoveTicks(100, 10, 9) == 81, "lab move: total minus start and pad");
        check(LiftProfile.labMoveTicks(20, 10, 9) == 10, "lab move: never below start delay");
        check(LiftProfile.labMoveTicks(0, 10, 9) == 10, "lab move: zero total still gives start delay");

        check(LiftProfile.shaftMoveTicks(16, 3, 100, 9) == 91, "shaft move: long total dominates");
        check(LiftProfile.shaftMoveTicks(24, 3, 40, 9) == 72, "shaft move: distance dominates");
        check(LiftProfile.shaftMoveTicks(0, 3, 40, 9) == 31, "shaft move: zero distance still moves");
        check(LiftProfile.shaftMoveTicks(0, 0, 40, 9) == 31, "shaft move: zero step still moves");
    }

    // ------------------------------------------------------------------
    // Конечная точка и телепорт
    // ------------------------------------------------------------------

    private static void endY() {
        check(LiftProfile.safeEndY(20.0, 20.0) == 20.0, "endY: equal stays");
        check(LiftProfile.safeEndY(19.5, 20.0) == 20.0, "endY: sunk rider is lifted to the floor");
        check(LiftProfile.safeEndY(20.5, 20.0) == 20.5, "endY: rider above the floor is kept");

        check(LiftProfile.snapNeeded(19.0, 20.0, 0.0, 0.0, 0.25), "snap: sinking always snaps");
        check(LiftProfile.snapNeeded(20.0, 20.0, 0.6, 0.0, 0.25), "snap: far horizontally snaps");
        check(!LiftProfile.snapNeeded(20.0, 20.0, 0.4, 0.0, 0.25), "snap: close horizontally stays");
        check(!LiftProfile.snapNeeded(20.0, 20.0, 0.0, 0.0, 0.25), "snap: exact end stays");
        check(!LiftProfile.snapNeeded(20.005, 20.0, 0.0, 0.0, 0.25), "snap: rider above the floor stays");
        check(LiftProfile.snapNeeded(20.6, 20.0, 0.0, 0.0, 0.25), "snap: far above the end snaps");
        check(LiftProfile.snapNeeded(19.98, 20.0, 0.0, 0.0, 0.25), "snap: sink beyond 1 cm still snaps");
    }

    // ------------------------------------------------------------------
    // Числа, на которые опираются формулы
    // ------------------------------------------------------------------

    private static void constants() {
        check(LabTuning.LIFT_BASE_TICKS == 50, "lab constants: LIFT_BASE_TICKS must stay 50");
        check(LabTuning.LIFT_PER_BLOCK == 2, "lab constants: LIFT_PER_BLOCK must stay 2");
        check(NightmareTuning.SHAFT_TRIP_TICKS == 80, "shaft constants: SHAFT_TRIP_TICKS must stay 80");
        check(NightmareTuning.SHAFT_TRIP_EXTRA_TICKS == 20, "shaft constants: extra per trip must stay 20");
        check(NightmareTuning.SHAFT_TRIP_EXTRA_MAX == 160, "shaft constants: extra cap must stay 160");
        check(NightmareTuning.SHAFT_STEP_TICKS == 3, "shaft constants: ticks per block must stay 3");
        check(NightmareTuning.SHAFT_START_DELAY == 20, "shaft constants: start delay must stay 20");
        check(NightmareTuning.SHAFT_STOP_STRIDE == 16, "shaft constants: stop stride must stay 16");
        check(NightmareTuning.SHAFT_DROP == 24, "shaft constants: repeat drop must stay 24");
        check(LiftRide.PAD_TICKS > 0 && LiftRide.PAD_TICKS == LiftRide.CALIB_TICKS + LiftRide.SETTLE_TICKS,
                "lift constants: pad is calibration plus settle");
    }

    // ------------------------------------------------------------------
    // Геометрия шахты (без мира)
    // ------------------------------------------------------------------

    private static void shaftGeometry() {
        check(ShaftBuilder.validStop(0) && ShaftBuilder.validStop(6)
                && ShaftBuilder.validStop(7) && ShaftBuilder.validStop(8), "shaft: stops 0/6/7/8 are valid");
        check(!ShaftBuilder.validStop(1) && !ShaftBuilder.validStop(5) && !ShaftBuilder.validStop(9),
                "shaft: other stops are invalid");
        check(ShaftBuilder.slotOf(6) == 6 && ShaftBuilder.slotOf(7) == 7 && ShaftBuilder.slotOf(8) == 8,
                "shaft: physical slots stay B6..B8");
        check(ShaftBuilder.slotOf(9) == 8 && ShaftBuilder.slotOf(5) == 6 && ShaftBuilder.slotOf(0) == 6,
                "shaft: deeper and higher floors clamp to B6..B8");
        check(ShaftBuilder.index(0) == 0 && ShaftBuilder.index(6) == 1
                && ShaftBuilder.index(7) == 2 && ShaftBuilder.index(8) == 3, "shaft: cabin indexes");
        check(ShaftBuilder.y(0) == NightmareTuning.SHAFT_Y, "shaft: top cabin sits at SHAFT_Y");
        check(ShaftBuilder.y(6) == NightmareTuning.SHAFT_Y - NightmareTuning.SHAFT_STOP_STRIDE,
                "shaft: B6 is one stride down");
        check(ShaftBuilder.y(8) == NightmareTuning.SHAFT_Y - 3 * NightmareTuning.SHAFT_STOP_STRIDE,
                "shaft: B8 is three strides down");
        check(ShaftBuilder.shaftBottom() == ShaftBuilder.y(8) - NightmareTuning.SHAFT_DROP - 1,
                "shaft: bottom leaves room for a repeat drop");
        check(ShaftBuilder.shaftTop() == ShaftBuilder.y(0) + 5, "shaft: top is one block above the cabin roof");

        int lane = 2;
        check(ShaftBuilder.spawn(lane, 0).getX() == ShaftBuilder.x(0) + 1
                        && ShaftBuilder.spawn(lane, 0).getY() == ShaftBuilder.y(0) + 1
                        && ShaftBuilder.spawn(lane, 0).getZ() == ShaftBuilder.z(lane) + 1,
                "shaft: spawn is the cabin center");
        check(ShaftBuilder.panel(lane, 0).getY() == ShaftBuilder.y(0) + 2, "shaft: panel is at cabin eye height");
        check(ShaftBuilder.shellMax(lane, 0).getX() - ShaftBuilder.shellMin(lane, 0).getX() == 4
                        && ShaftBuilder.shellMax(lane, 0).getY() - ShaftBuilder.shellMin(lane, 0).getY() == 4
                        && ShaftBuilder.shellMax(lane, 0).getZ() - ShaftBuilder.shellMin(lane, 0).getZ() == 4,
                "shaft: shell is a 5x5x5 box");

        Vec3 inside = new Vec3(ShaftBuilder.x(6) + 1.5, ShaftBuilder.y(6) + 1.5, ShaftBuilder.z(lane) + 1.5);
        check(ShaftBuilder.inShaft(lane, 6, inside), "shaft: cabin center is inside the shaft");
        check(ShaftBuilder.inShaft(lane, 6, new Vec3(inside.x, ShaftBuilder.shaftBottom() + 0.5, inside.z)),
                "shaft: bottom of the shaft is inside");
        check(!ShaftBuilder.inShaft(lane, 6, new Vec3(inside.x, ShaftBuilder.shaftTop() + 1.5, inside.z)),
                "shaft: above the shaft is outside");
        check(!ShaftBuilder.inShaft(lane + 1, 6, inside), "shaft: neighboring lane is outside");
    }
}
