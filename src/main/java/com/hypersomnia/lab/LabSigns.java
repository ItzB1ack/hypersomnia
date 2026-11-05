package com.hypersomnia.lab;

import com.hypersomnia.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Настенные таблички лаборатории (блок lab_sign): строки из чертежа (sign N = LINE1|LINE2|...), светлый текст на тёмной пластине.
 * Русский текст: ru_ru, ключ hypersomnia.lab.sign.&lt;строки через _&gt; (LabSignBlockEntity.key()), строки через «|».
 */
public final class LabSigns {
    private LabSigns() {}

    public static void wall(ServerLevel level, BlockPos pos, Direction facing, String... lines) {
        level.setBlock(pos, ModBlocks.LAB_SIGN.get().defaultBlockState().setValue(LabSignBlock.FACING, facing), 2);
        if (level.getBlockEntity(pos) instanceof LabSignBlockEntity sign) sign.setLines(lines);
    }
}
