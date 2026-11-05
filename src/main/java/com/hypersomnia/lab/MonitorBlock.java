package com.hypersomnia.lab;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Монитор наблюдения: тот же ЭЛТ, что у рабочей станции, без клавиатуры. FACING = сторона экрана.
 * LIT = на экране «снег» (свет LabTuning.MONITOR_LIGHT); погасший экран — тёмное стекло. Только интерьер, без интерактива.
 */
public final class MonitorBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<MonitorBlock> CODEC = simpleCodec(MonitorBlock::new);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    /** Коробки модели при FACING = NORTH: подставка, корпус, кинескоп (как у WorkstationBlock). */
    private static final double[][] BOXES = {
            {5, 0, 6, 11, 0.5, 12},
            {3, 0.5, 5, 13, 7.5, 9},
            {4.5, 1.5, 9, 11.5, 6.5, 14}};
    private static final VoxelShape[] SHAPES = LabShapes.horizontal(BOXES);

    public MonitorBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }
}
