package com.hypersomnia.lab;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * [новое] Потолочный светильник лаборатории: металлическая планка у потолка и матовая трубка дневного света.
 * Заменяет торцевой стержень (END_ROD), который раньше висел в тех же клетках. FACING — куда смотрит трубка;
 * в потолок ставится DOWN. Во время «обесточено» LabOutage меняет его на {@link UnlitTubeBlock}.
 */
public final class LightTubeBlock extends RodBlock {
    public static final MapCodec<LightTubeBlock> CODEC = simpleCodec(LightTubeBlock::new);

    public LightTubeBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN));
    }

    @Override
    protected MapCodec<LightTubeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
