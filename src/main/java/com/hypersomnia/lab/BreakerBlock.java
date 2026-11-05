package com.hypersomnia.lab;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Настенный рубильник щита MAIN FEED. FACING = лицевая сторона (от стены), ON = рукоять вверх.
 * Сам блок только показывает состояние. Рубильники лаборатории (LabSavedData.inter) перехватывает
 * LabHandler, и состояние ставит LabBreakers; поставленный вручную рубильник просто щёлкает.
 * Неразрушаемый: щит нельзя сломать и потерять загадку.
 */
public final class BreakerBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<BreakerBlock> CODEC = simpleCodec(BreakerBlock::new);
    public static final BooleanProperty ON = BooleanProperty.create("on");

    private static final VoxelShape NORTH = Block.box(3, 1, 9, 13, 15, 16);
    private static final VoxelShape SOUTH = Block.box(3, 1, 0, 13, 15, 7);
    private static final VoxelShape WEST = Block.box(9, 1, 3, 16, 15, 13);
    private static final VoxelShape EAST = Block.box(0, 1, 3, 7, 15, 13);

    public BreakerBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ON);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        boolean on = !state.getValue(ON);
        level.setBlock(pos, state.setValue(ON, on), 3);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, on ? 0.6f : 0.5f);
        return InteractionResult.CONSUME;
    }
}
