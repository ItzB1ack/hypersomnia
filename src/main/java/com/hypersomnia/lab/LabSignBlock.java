package com.hypersomnia.lab;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Табличка лаборатории: тёмная пластина на стене без коллизии. FACING = лицевая сторона (от стены).
 * Строки хранит LabSignBlockEntity, текст рисует client/LabSignRenderer; ставит LabSigns.
 */
public final class LabSignBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<LabSignBlock> CODEC = simpleCodec(LabSignBlock::new);

    /** Пластина при FACING = NORTH (стена с юга, +z). Поле текста — внутри рамки, LabSignRenderer. */
    private static final double[][] BOXES = {{1, 4, 15, 15, 12, 16}};
    private static final VoxelShape[] SHAPES = LabShapes.horizontal(BOXES);

    public LabSignBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LabSignBlockEntity(pos, state);
    }
}
