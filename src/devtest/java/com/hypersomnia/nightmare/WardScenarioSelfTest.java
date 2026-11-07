package com.hypersomnia.nightmare;

import net.minecraft.core.BlockPos;

/** Геометрия выхода проверяется без запуска Minecraft; интеграционный игровой тест всё равно нужен. */
public final class WardScenarioSelfTest {
    private static int checks;
    private static void check(boolean ok, String text) { checks++; if (!ok) throw new AssertionError(text); }
    public static void main(String[] args) {
        BlockPos bed = WardScenario.paperBed();
        for (int loops = 0; loops <= 5; loops++) {
            boolean open = loops >= NightmareTuning.WARD_EMPTY_AFTER_LOOPS;
            for (int period = 0; period < NightmareTuning.WARD_PERIODS; period++) {
                for (int side : new int[] {-1, 1}) {
                    check(WardScenario.emptyWard(loops, period, side)
                            == (open && period == NightmareTuning.WARD_EMPTY_PERIOD && side == -1), "personal room visibility");
                }
            }
            check(WardScenario.exitBed(bed, loops) == open, "foot opens at correct loop");
            check(WardScenario.exitBed(bed.north(), loops) == open, "head opens at correct loop");
            check(!WardScenario.exitBed(bed.east(), loops), "other bed does not open");
            check(!WardScenario.exitBed(bed.above(), loops), "other heights do not open");
        }
        BlockPos o = NightmareDefs.WARD_CORRIDOR.origin();
        check(WardScenario.roomPeriod(bed) == NightmareTuning.WARD_EMPTY_PERIOD, "north room detection");
        check(WardScenario.roomPeriod(o.offset(19, 1, -(NightmareTuning.WARD_DEPTH + 1))) == 2, "north far edge");
        check(WardScenario.roomPeriod(o.offset(19, 1, NightmareTuning.CORRIDOR_WIDTH + NightmareTuning.WARD_DEPTH)) == 2, "south far edge");
        check(WardScenario.roomPeriod(o.offset(19, 1, -(NightmareTuning.WARD_DEPTH + 2))) == -1, "north wall excluded");
        check(WardScenario.roomPeriod(o.offset(19, 1, NightmareTuning.CORRIDOR_WIDTH + NightmareTuning.WARD_DEPTH + 1)) == -1, "south wall excluded");
        check(WardScenario.roomPeriod(o.offset(19, 1, NightmareTuning.CORRIDOR_WIDTH + 2)) == 2, "south room detection");
        check(WardScenario.roomPeriod(o.offset(19, 1, 1)) == -1, "corridor is not room");
        check(WardScenario.roomPeriod(o.offset(-1, 1, -3)) == -1, "outside corridor length");
        check(WardScenario.roomPeriod(bed.above(5)) == -1, "outside height");
        NightmareSavedData saved = new NightmareSavedData();
        saved.setWardBuildFailed(true);
        var nbt = saved.save(new net.minecraft.nbt.CompoundTag(), null);
        NightmareSavedData restored = NightmareSavedData.load(nbt, null);
        check(restored.wardBuildFailed(), "failed build survives reload");
        check(!restored.isBuilt(NightmareDefs.WARD_CORRIDOR), "failed build is not marked complete");
        restored.setWardBuildFailed(false);
        restored.setBuilt(NightmareDefs.WARD_CORRIDOR, true);
        restored = NightmareSavedData.load(restored.save(new net.minecraft.nbt.CompoundTag(), null), null);
        check(!restored.wardBuildFailed() && restored.isBuilt(NightmareDefs.WARD_CORRIDOR), "successful build survives reload");
        var dream = new com.hypersomnia.dream.DreamData();
        dream.setReturn("minecraft:overworld", 42, 70, 19, 20, 5);
        dream.setInDream(true);
        dream.setKind("ward");
        dream.addWardLoop();
        var reloaded = new com.hypersomnia.dream.DreamData();
        reloaded.deserializeNBT(null, dream.serializeNBT(null));
        check(reloaded.getWardLoops() == 1 && reloaded.getReturnX() == 42, "loop and return persist");
        reloaded.resetWardLoops();
        check(reloaded.getWardLoops() == 0 && reloaded.getReturnX() == 42, "reset loop preserves return point");
        System.out.println("WardScenarioSelfTest: " + checks + " checks passed.");
    }
}
