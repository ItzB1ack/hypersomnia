package com.hypersomnia.lab;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Цельная мебель лаборатории (стол desk): полный блок, FACING = лицевая сторона (ящики) в комнату.
 * Только интерьер: без интерактива, неразрушаемая, без дропа (ставит LabBuilder). Шкаф с ящиками — CabinetBlock.
 */
public final class FurnitureBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<FurnitureBlock> CODEC = simpleCodec(FurnitureBlock::new);

    public FurnitureBlock(Properties props) {
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
}
