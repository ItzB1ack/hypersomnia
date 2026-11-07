package com.hypersomnia.lab;

import net.minecraft.core.BlockPos;

/** Проверяет геометрию шахты G↔B1: низ над потолком B1, верх под полом G, границы облицовки. */
public final class LabShaftSelfTest {
    public static void main(String[] args) {
        LabSavedData d = new LabSavedData();
        d.builtMask = (1 << 0) | (1 << 1);
        d.originX = 100;
        d.originZ = 200;
        d.stopY[0] = 64; d.stopH[0] = 4; // пол G = 63
        d.stopY[1] = 20; d.stopH[1] = 5; // потолок B1 = 25
        d.shaftBuilt = true;
        if (LabShaft.bottom(d) != 26) throw new AssertionError("bottom must start above B1 ceiling");
        if (LabShaft.top(d) != 62) throw new AssertionError("top must stay under G floor");
        if (!LabShaft.needed(d)) throw new AssertionError("shaft must be needed");
        if (!LabShaft.inside(d, new BlockPos(100, 26, 200))) throw new AssertionError("bottom air must be inside");
        if (!LabShaft.inside(d, new BlockPos(103, 62, 203))) throw new AssertionError("casing corner must be inside");
        if (LabShaft.inside(d, new BlockPos(104, 40, 200))) throw new AssertionError("outside casing must not be inside");
        if (LabShaft.inside(d, new BlockPos(100, 63, 200))) throw new AssertionError("G floor must not be shaft");
        d.stopY[0] = 26; // просвета нет
        if (LabShaft.needed(d)) throw new AssertionError("no gap must not need a shaft");
        System.out.println("LabShaftSelfTest: 8 checks passed.");
    }
}
