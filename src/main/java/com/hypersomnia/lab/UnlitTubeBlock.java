package com.hypersomnia.lab;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Погасшая потолочная «трубка» лаборатории: та же модель, что у светильника {@link LightTubeBlock}, но без света.
 * Ставит и убирает только LabOutage на время «обесточено» (направление берётся у лампы); предмета нет.
 */
public final class UnlitTubeBlock extends RodBlock {
    public static final MapCodec<UnlitTubeBlock> CODEC = simpleCodec(UnlitTubeBlock::new);

    public UnlitTubeBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN));
    }

    @Override
    protected MapCodec<UnlitTubeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
