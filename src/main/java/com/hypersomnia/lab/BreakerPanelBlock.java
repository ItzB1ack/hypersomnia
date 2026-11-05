package com.hypersomnia.lab;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Настенный щит MAIN FEED: один блок на все рубильники, сами рубильники — в экране щита (LabPanelScreen).
 * FACING = лицевая сторона (от стены), LAMP = лампа на дверце: OFF / TRIPPED (красная) / ON.
 * Блок только показывает состояние. Щит лаборатории (LabSavedData.inter) перехватывает LabHandler и открывает экран,
 * лампу ставит LabBreakers.refresh(); поставленный вручную или декоративный щит пишет NOT CONNECTED.
 * Неразрушаемый: щит нельзя сломать и потерять загадку.
 */
public final class BreakerPanelBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<BreakerPanelBlock> CODEC = simpleCodec(BreakerPanelBlock::new);
    public static final EnumProperty<Lamp> LAMP = EnumProperty.create("lamp", Lamp.class);

    public enum Lamp implements StringRepresentable {
        OFF("off"), TRIPPED("tripped"), ON("on");

        private final String name;

        Lamp(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** Коробки модели при FACING = NORTH (стена с юга, +z): корпус, рукоять дверцы, ввод кабеля сверху. */
    private static final double[][] BOXES = {
            {2, 1, 11, 14, 15, 16},
            {3.5, 5, 10, 5, 9, 11},
            {6, 15, 12, 10, 16, 15}};
    private static final VoxelShape[] SHAPES = LabShapes.horizontal(BOXES);

    public BreakerPanelBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LAMP, Lamp.OFF));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LAMP);
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
