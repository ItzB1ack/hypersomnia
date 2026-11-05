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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Рабочая станция CRADLE WORKSTATION: ЭЛТ-монитор, клавиатура и мышь. Стоит на цельном столе (блок desk под ней ставит LabBuilder).
 * FACING = сторона экрана (к пользователю).
 * Станции лаборатории (LabSavedData.inter) перехватывает LabHandler и открывает экран станции; поставленная вручную пишет NO NETWORK.
 * Неразрушаемая: станцию с подсказками нельзя сломать и потерять.
 */
public final class WorkstationBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<WorkstationBlock> CODEC = simpleCodec(WorkstationBlock::new);

    /** Коробки модели при FACING = NORTH (экран к -z): подставка, корпус монитора, кинескоп, клавиатура. */
    private static final double[][] BOXES = {
            {5, 0, 6, 11, 0.5, 12},
            {3, 0.5, 5, 13, 7.5, 9},
            {4.5, 1.5, 9, 11.5, 6.5, 14},
            {3.5, 0, 1, 12.5, 0.5, 4}};
    private static final VoxelShape[] SHAPES = LabShapes.horizontal(BOXES);

    public WorkstationBlock(Properties props) {
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
        if (!level.isClientSide) player.displayClientMessage(Component.translatable("hypersomnia.lab.ws.offline"), true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
