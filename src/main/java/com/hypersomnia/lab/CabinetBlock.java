package com.hypersomnia.lab;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Картотечный шкаф лаборатории: три ящика по 9 ячеек (ChestMenu, экран CabinetScreen).
 * FACING = лицевая сторона (ящики) в комнату, OPEN = верхний ящик выдвинут, пока шкаф кто-то смотрит.
 * Содержимое кладёт LabCabinets при стройке. При сносе (rebuild) содержимое не выпадает.
 */
public final class CabinetBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<CabinetBlock> CODEC = simpleCodec(CabinetBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public CabinetBlock(Properties props) {
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CabinetBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof CabinetBlockEntity cab) player.openMenu(cab);
        return InteractionResult.CONSUME;
    }

    /** Повторная проверка открывших (как у бочки): ящик задвигается, даже если экран закрылся без пакета. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof CabinetBlockEntity cab) cab.recheckOpen();
    }
}
