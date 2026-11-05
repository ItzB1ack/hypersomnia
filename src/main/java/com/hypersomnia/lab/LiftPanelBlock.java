package com.hypersomnia.lab;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Панель лифта на стене кабины: табло с номером этажа и шесть кнопок G, B1..B5. FACING = лицевая сторона,
 * STOP = этаж, где стоит панель (на табло его номер, его кнопка светится); ставит LabBuilder.
 * Панель кабины лаборатории (LabSavedData.inter) перехватывает LabHandler и открывает LabLiftScreen;
 * поставленная вручную пишет NOT CONNECTED.
 */
public final class LiftPanelBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<LiftPanelBlock> CODEC = simpleCodec(LiftPanelBlock::new);
    public static final IntegerProperty STOP = IntegerProperty.create("stop", 0, LabTuning.STOPS - 1);

    /** Пластина при FACING = NORTH (стена с юга, +z). */
    private static final double[][] BOXES = {{3, 1, 15, 13, 15, 16}};
    private static final VoxelShape[] SHAPES = LabShapes.horizontal(BOXES);

    public LiftPanelBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STOP, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STOP);
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
        if (!level.isClientSide) player.displayClientMessage(Component.translatable("hypersomnia.lab.panel.offline"), true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
