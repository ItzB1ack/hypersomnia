package com.hypersomnia.lab;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Капсула сна зала сеанса B5: три клетки вдоль FACING — изножье (FOOT), середина (MID) и изголовье (HEAD).
 * FACING — от изножья к изголовью. OPEN = крышка поднята: в открытой можно лежать (SeatEntity.lie),
 * но сон она не начинает. Закрытая капсула — только осмотр («CAPSULE SEALED»).
 * Открытость задаёт чертёж (LabTuning.capsuleOpen), ставит LabBuilder; предмета нет.
 */
public final class CapsuleBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<CapsuleBlock> CODEC = simpleCodec(CapsuleBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    /** Часть капсулы: порядок вдоль FACING. */
    public enum Part implements StringRepresentable {
        FOOT("foot"), MID("mid"), HEAD("head");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** Коробки при FACING = NORTH (изголовье к северу, тело вытянуто вдоль -z). */
    private static final double PLINTH_Y = 2;      // низ корпуса
    private static final double PAD_Y = 6;         // верх ложа: на нём спина лежащего (SeatEntity)
    private static final double RAIL_Y = 12;       // борта до уровня макушки; выше — стекло
    private static final double[] GLASS = {3, RAIL_Y, 0, 13, 16, 16};

    /** Общие коробки: цоколь, ложе между бортами, два борта. */
    private static double[][] body() {
        return new double[][]{
                {1, 0, 0, 15, PLINTH_Y, 16},          // цоколь
                {3, PLINTH_Y, 0, 13, PAD_Y, 16},      // ложе (верх 6/16)
                {1, PLINTH_Y, 0, 3, RAIL_Y, 16},      // западный борт
                {13, PLINTH_Y, 0, 15, RAIL_Y, 16}};   // восточный борт
    }

    /** Изголовье: низкая стенка (ниже головы), консоль над головой и подушка. */
    private static final double[][] HEAD_BOXES = head();

    private static double[][] head() {
        double[][] b = body();
        double[][] all = java.util.Arrays.copyOf(b, b.length + 3);
        all[b.length] = new double[]{3, 0, 0, 13, 4, 2};        // стенка изголовья ниже головы
        all[b.length + 1] = new double[]{3, RAIL_Y, 0, 13, 16, 3}; // консоль/поднятая крышка над головой
        all[b.length + 2] = new double[]{3, PAD_Y, 2, 13, 7, 5};   // подушка
        return all;
    }

    private static final VoxelShape[] FOOT_SHAPES = LabShapes.horizontal(body());
    private static final VoxelShape[] MID_SHAPES = LabShapes.horizontal(body());
    private static final VoxelShape[] HEAD_SHAPES = LabShapes.horizontal(HEAD_BOXES);
    private static final VoxelShape[] FOOT_CLOSED_SHAPES = withGlass(body());
    private static final VoxelShape[] MID_CLOSED_SHAPES = withGlass(body());
    private static final VoxelShape[] HEAD_CLOSED_SHAPES = withGlass(HEAD_BOXES);

    private static VoxelShape[] withGlass(double[][] boxes) {
        double[][] all = java.util.Arrays.copyOf(boxes, boxes.length + 1);
        all[boxes.length] = GLASS;
        return LabShapes.horizontal(all);
    }

    public CapsuleBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, Part.FOOT)
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

    /** Угол тела лежащего: как sleepDirectionToRotation у ванильной кровати (голова к FACING). */
    public static float sleepYaw(Direction facing) {
        return switch (facing) {
            case SOUTH -> 90f;
            case WEST -> 0f;
            case NORTH -> 270f;
            case EAST -> 180f;
            default -> 0f;
        };
    }

    /** Тройка цела и открыта: изножье в foot, середина и изголовье через клетку по facing. */
    public static boolean canLie(BlockGetter level, BlockPos foot, Direction facing) {
        if (foot == null || facing == null || facing.getAxis().isVertical()) return false;
        return isPart(level.getBlockState(foot), Part.FOOT, facing, true)
                && isPart(level.getBlockState(foot.relative(facing)), Part.MID, facing, true)
                && isPart(level.getBlockState(foot.relative(facing, 2)), Part.HEAD, facing, true);
    }

    private static boolean isPart(BlockState st, Part part, Direction facing, boolean open) {
        return st.getBlock() instanceof CapsuleBlock
                && st.getValue(PART) == part
                && st.getValue(FACING) == facing
                && st.getValue(OPEN) == open;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int i = state.getValue(FACING).get2DDataValue();
        boolean open = state.getValue(OPEN);
        return switch (state.getValue(PART)) {
            case FOOT -> open ? FOOT_SHAPES[i] : FOOT_CLOSED_SHAPES[i];
            case MID -> open ? MID_SHAPES[i] : MID_CLOSED_SHAPES[i];
            case HEAD -> open ? HEAD_SHAPES[i] : HEAD_CLOSED_SHAPES[i];
        };
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        Part part = state.getValue(PART);
        boolean open = state.getValue(OPEN);
        if (dir == facing && part != Part.HEAD) {
            Part next = part == Part.FOOT ? Part.MID : Part.HEAD;
            return isPart(neighbor, next, facing, open) ? state : Blocks.AIR.defaultBlockState();
        }
        if (dir == facing.getOpposite() && part != Part.FOOT) {
            Part prev = part == Part.HEAD ? Part.MID : Part.FOOT;
            return isPart(neighbor, prev, facing, open) ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive() || player.isPassenger()) return InteractionResult.PASS;
        Direction facing = state.getValue(FACING);
        BlockPos foot = pos.relative(facing.getOpposite(), state.getValue(PART).ordinal());
        BlockState fs = level.getBlockState(foot);
        if (!(fs.getBlock() instanceof CapsuleBlock) || fs.getValue(PART) != Part.FOOT
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
