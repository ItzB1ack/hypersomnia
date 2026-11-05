package com.hypersomnia.lab;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Офисный стул лаборатории: крестовина, газлифт, сиденье и спинка. FACING = куда смотрит сиденье
 * (спинка с обратной стороны). ПКМ — сесть (SeatEntity), Shift — встать. Ставит LabBuilder.
 */
public final class ChairBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<ChairBlock> CODEC = simpleCodec(ChairBlock::new);

    /** Коробки модели при FACING = NORTH (спинка с юга): крестовина и сиденье одним объёмом, спинка. */
    private static final double[][] BOXES = {
            {2, 0, 2, 14, 8.5, 14},
            {3, 8.5, 12, 13, 15.5, 14.5}};
    private static final VoxelShape[] SHAPES = LabShapes.horizontal(BOXES);

    public ChairBlock(Properties props) {
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive() || player.isPassenger()) return InteractionResult.PASS;
        if (level instanceof ServerLevel server) SeatEntity.sit(server, pos, player, state.getValue(FACING));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
