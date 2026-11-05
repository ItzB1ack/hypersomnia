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
 * Поворотная каретка антенны ANT-1 (крыша павильона, шифр 8): сланцевое основание, поворотный стол с гнездом мачты,
 * редуктор и красный штурвал с рукоятью. FACING = сторона штурвала. Каретку лаборатории (LabSavedData.inter, KIND_ANTENNA)
 * перехватывает LabHandler: ПКМ поворачивает тарелку (LabAntenna). Поставленная вручную пишет NOT CONNECTED.
 */
public final class AntennaDriveBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<AntennaDriveBlock> CODEC = simpleCodec(AntennaDriveBlock::new);

    /** Коробки при FACING = NORTH (штурвал к -z): основание со столом, гнездо мачты, штурвал с редуктором. */
    private static final double[][] BOXES = {{2, 0, 2, 14, 8, 14}, {4, 8, 4, 12, 16, 12}, {3, 2, 0, 13, 12, 2}};
    private static final VoxelShape[] SHAPES = LabShapes.horizontal(BOXES);

    public AntennaDriveBlock(Properties props) {
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
        if (!level.isClientSide) player.displayClientMessage(Component.translatable("hypersomnia.lab.panel.offline"), true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
