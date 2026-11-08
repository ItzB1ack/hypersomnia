package com.hypersomnia.dream;

import com.hypersomnia.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * [новое] Пустая сущность напольных часов: нужна только клиентской анимации
 * (маятник в нижней половине, стрелки в верхней). Данных не хранит — время берётся
 * из {@code level.getGameTime()}.
 */
public final class DreamClockBlockEntity extends BlockEntity {
    public DreamClockBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DREAM_CLOCK.get(), pos, state);
    }
}
