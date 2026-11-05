package com.hypersomnia.lab;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Серверная стойка B3, два блока высотой (HALF). FACING = лицевая сторона. На верхней половине — лампа состояния STATUS
 * (тёмная OFF, светлая IDLE, красная HOLD — буфер удержан) и четыре лампы адреса буфера сверху вниз 8-4-2-1 (BIT8..BIT1).
 * Состояние ставит LabFixtures.refresh по зерну мира; стойку лаборатории перехватывает LabHandler (номер и состояние),
 * поставленная вручную пишет NOT CONNECTED.
 */
public final class ServerRackBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<ServerRackBlock> CODEC = simpleCodec(ServerRackBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<Status> STATUS = EnumProperty.create("status", Status.class);
    public static final BooleanProperty BIT8 = BooleanProperty.create("bit8");
    public static final BooleanProperty BIT4 = BooleanProperty.create("bit4");
    public static final BooleanProperty BIT2 = BooleanProperty.create("bit2");
    public static final BooleanProperty BIT1 = BooleanProperty.create("bit1");
    /** Лампы адреса по весу сверху вниз: 8, 4, 2, 1. */
    public static final BooleanProperty[] BITS = {BIT8, BIT4, BIT2, BIT1};

    public enum Status implements StringRepresentable {
        OFF("off"), IDLE("idle"), HOLD("hold");

        private final String name;

        Status(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public ServerRackBlock(Properties props) {
        super(props);
        BlockState s = stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(STATUS, Status.OFF);
        for (BooleanProperty b : BITS) s = s.setValue(b, false);
        registerDefaultState(s);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, STATUS, BIT8, BIT4, BIT2, BIT1);
    }

    /** Ставится рукой (творческая вкладка) как дверь: нужна свободная клетка сверху, верхняя половина — в setPlacedBy. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(ctx)) return null;
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /** Половина без пары исчезает (сломали другую половину). LabBuilder ставит обе половины и сносит без обновления формы. */
    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (dir.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (dir == Direction.UP))
            return neighbor.is(this) && neighbor.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    /** Верхняя половина с лампами: состояние и адрес value (0-15, младшие четыре бита). */
    public static BlockState lamps(BlockState upper, Status status, int value) {
        BlockState s = upper.setValue(STATUS, status);
        for (int i = 0; i < BITS.length; i++) s = s.setValue(BITS[i], ((value >> (BITS.length - 1 - i)) & 1) != 0);
        return s;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) player.displayClientMessage(Component.translatable("hypersomnia.lab.panel.offline"), true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
