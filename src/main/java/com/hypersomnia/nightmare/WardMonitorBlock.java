package com.hypersomnia.nightmare;

import com.hypersomnia.lab.LabShapes;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Небольшой прикроватный монитор: декоративный, с анимированной текстурой ЭКГ. */
public final class WardMonitorBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<WardMonitorBlock> CODEC = simpleCodec(WardMonitorBlock::new);
    private static final VoxelShape[] SHAPES = LabShapes.horizontal(new double[][] {
            {3, 0, 3, 13, 2, 13}, {7, 2, 6, 9, 8, 10}, {2, 8, 3, 14, 16, 10}
    });
    public WardMonitorBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }
}
