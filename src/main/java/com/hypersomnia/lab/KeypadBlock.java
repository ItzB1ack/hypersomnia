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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Кодовая панель замка на стене: табло, лампа и 12 клавиш. FACING = лицевая сторона (от стены),
 * OPEN = замок открыт (лампа светлая), иначе лампа красная; состояние ставит LabLocks.refreshKeypads().
 * Панель замка лаборатории (LabSavedData.inter) перехватывает LabHandler и открывает LabLockScreen;
 * поставленная вручную пишет NOT CONNECTED.
 */
public final class KeypadBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<KeypadBlock> CODEC = simpleCodec(KeypadBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    /** Пластина при FACING = NORTH (стена с юга, +z). */
    private static final double[][] BOXES = {{3, 2, 15, 13, 14, 16}};
    private static final VoxelShape[] SHAPES = LabShapes.horizontal(BOXES);

    public KeypadBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
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
