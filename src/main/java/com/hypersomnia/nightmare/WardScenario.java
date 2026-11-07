package com.hypersomnia.nightmare;

import net.minecraft.core.BlockPos;

/** Общая геометрия персонального выхода: без изменения общих блоков мира. */
public final class WardScenario {
    private WardScenario() {}

    public static boolean emptyWard(int loops, int period, int side) {
        return loops >= NightmareTuning.WARD_EMPTY_AFTER_LOOPS
                && period == NightmareTuning.WARD_EMPTY_PERIOD && side < 0;
    }

    /** Обе половины одной койки в пустой северной палате. */
    public static boolean exitBed(BlockPos pos, int loops) {
        if (!emptyWard(loops, NightmareTuning.WARD_EMPTY_PERIOD, -1)) return false;
        BlockPos o = NightmareDefs.WARD_CORRIDOR.origin();
        int x = NightmareTuning.WARD_EMPTY_PERIOD * NightmareTuning.WARD_PERIOD + NightmareTuning.WARD_EMPTY_BED_X;
        int z = pos.getZ() - o.getZ();
        return pos.getX() == o.getX() + x && pos.getY() == o.getY() + 1 && (z == -4 || z == -5);
    }

    public static BlockPos paperBed() {
        BlockPos o = NightmareDefs.WARD_CORRIDOR.origin();
        return o.offset(NightmareTuning.WARD_EMPTY_PERIOD * NightmareTuning.WARD_PERIOD
                + NightmareTuning.WARD_EMPTY_BED_X, 1, -4);
    }

    /** Внутри палат; -1 означает, что игрок сейчас не в палате. */
    public static int roomPeriod(BlockPos pos) {
        BlockPos o = NightmareDefs.WARD_CORRIDOR.origin();
        int x = pos.getX() - o.getX(), y = pos.getY() - o.getY(), z = pos.getZ() - o.getZ();
        if (x < 0 || x >= NightmareTuning.WARD_PERIOD * NightmareTuning.WARD_PERIODS || y < 1 || y > 3) return -1;
        boolean north = z >= -(NightmareTuning.WARD_DEPTH + 1) && z <= -2;
        boolean south = z >= NightmareTuning.CORRIDOR_WIDTH + 1
                && z <= NightmareTuning.CORRIDOR_WIDTH + NightmareTuning.WARD_DEPTH;
        return north || south ? x / NightmareTuning.WARD_PERIOD : -1;
    }
}
