package com.hypersomnia.lab;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Капсула сна зала сеанса B5: две соседние клетки, изножье (FOOT, западная) и изголовье (HEAD, восточная),
 * FACING — от изножья к изголовью. OPEN = крышка поднята: в открытой капсуле можно лежать (SeatEntity.lie),
 * но сон она не начинает. Закрытая капсула — только осмотр («CAPSULE SEALED»).
 * Открытость задаёт чертёж (LabTuning.capsuleOpen), ставит LabBuilder; предмета нет.
 */
public final class CapsuleBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<CapsuleBlock> CODEC = simpleCodec(CapsuleBlock::new);
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    /** Коробки частей при FACING = NORTH (изголовье к северу, дно капсулы — z 8..16). */
    private static final double[][] FOOT_BOXES = {
            {1, 0, 0, 15, 3, 16},                    // дно
            {1, 3, 0, 3, 7, 16},                     // западный борт
            {13, 3, 0, 15, 7, 16},                   // восточный борт
            {3, 3, 14, 13, 7, 16},                   // торцевая стенка изножья
            {3, 3, 0, 13, 6, 14}};                   // ложе до стыка
    private static final double[][] HEAD_BOXES = {
            {1, 0, 0, 15, 3, 16},
            {1, 3, 0, 3, 7, 16},
            {13, 3, 0, 15, 7, 16},
            {3, 3, 0, 13, 7, 2},                     // торцевая стенка изголовья
            {3, 3, 2, 13, 6, 16}};                   // ложе до стыка
    private static final VoxelShape[] FOOT_SHAPES = LabShapes.horizontal(FOOT_BOXES);
    private static final VoxelShape[] HEAD_SHAPES = LabShapes.horizontal(HEAD_BOXES);
    private static final VoxelShape[] FOOT_CLOSED_SHAPES = closed(FOOT_BOXES, 0, 14);
    private static final VoxelShape[] HEAD_CLOSED_SHAPES = closed(HEAD_BOXES, 2, 16);
    /** Открытое изголовье: поднятая крышка на петлях у торца. */
    private static final VoxelShape[] HEAD_OPEN_SHAPES = raised(HEAD_BOXES);

    /** Закрытая крышка части: та же часть плюс плоское стекло между бортами. */
    private static VoxelShape[] closed(double[][] boxes, int z1, int z2) {
        return withExtra(boxes, new double[]{3, 7, z1, 13, 12, z2});
    }

    /** Открытое изголовье: та же часть плюс поднятая крышка у торца. */
    private static VoxelShape[] raised(double[][] boxes) {
        return withExtra(boxes, new double[]{3, 8, 0, 13, 15, 2});
    }

    private static VoxelShape[] withExtra(double[][] boxes, double[] extra) {
        double[][] all = java.util.Arrays.copyOf(boxes, boxes.length + 1);
        all[boxes.length] = extra;
        return LabShapes.horizontal(all);
    }

    public CapsuleBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, BedPart.FOOT)
                .setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, OPEN);
    }

    /** Открыта ли капсула по клетке изножья чертежа: три из двенадцати коек зала B5. */
    public static boolean openAt(int blueprintX, int blueprintZ) {
        return LabTuning.capsuleOpen(blueprintX, blueprintZ);
    }

    /** Пара цела и открыта: изножье в foot, изголовье через одну клетку по facing. */
    public static boolean canLie(BlockGetter level, BlockPos foot, Direction facing) {
        if (foot == null || facing == null || facing.getAxis().isVertical()) return false;
        BlockState fs = level.getBlockState(foot);
        if (!isPart(fs, BedPart.FOOT, facing, true)) return false;
        BlockState hs = level.getBlockState(foot.relative(facing));
        return isPart(hs, BedPart.HEAD, facing, true);
    }

    private static boolean isPart(BlockState st, BedPart part, Direction facing, boolean open) {
        return st.getBlock() instanceof CapsuleBlock
                && st.getValue(PART) == part
                && st.getValue(FACING) == facing
                && st.getValue(OPEN) == open;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int i = state.getValue(FACING).get2DDataValue();
        boolean open = state.getValue(OPEN);
        if (state.getValue(PART) == BedPart.FOOT) return open ? FOOT_SHAPES[i] : FOOT_CLOSED_SHAPES[i];
        return open ? HEAD_OPEN_SHAPES[i] : HEAD_CLOSED_SHAPES[i];
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (dir == connectedDirection(state))
            return neighbor.getBlock() instanceof CapsuleBlock && neighbor.getValue(PART) != state.getValue(PART)
                    ? state : Blocks.AIR.defaultBlockState();
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    /** Направление на вторую половину: изножье смотрит на изголовье, изголовье — обратно. */
    private static Direction connectedDirection(BlockState state) {
        return state.getValue(PART) == BedPart.FOOT ? state.getValue(FACING) : state.getValue(FACING).getOpposite();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive() || player.isPassenger()) return InteractionResult.PASS;
        Direction facing = state.getValue(FACING);
        BlockPos foot = state.getValue(PART) == BedPart.FOOT ? pos : pos.relative(facing.getOpposite());
        BlockState fs = level.getBlockState(foot);
        if (!(fs.getBlock() instanceof CapsuleBlock) || fs.getValue(PART) != BedPart.FOOT
                || fs.getValue(FACING) != facing) return InteractionResult.PASS;
        if (!fs.getValue(OPEN)) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("hypersomnia.lab.capsule.sealed"), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level instanceof ServerLevel server && !SeatEntity.lie(server, foot, player, facing)) {
            player.displayClientMessage(Component.translatable("hypersomnia.lab.capsule.occupied"), true);
            return InteractionResult.sidedSuccess(false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
