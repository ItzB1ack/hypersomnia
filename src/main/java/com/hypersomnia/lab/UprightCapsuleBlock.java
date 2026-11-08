package com.hypersomnia.lab;

import com.hypersomnia.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
 * [новое] Стоячая капсула: вертикальный вариант капсулы сна. Колонка из трёх клеток снизу вверх:
 * основание (BASE, пол ячейки на 6/16), середина (MID) и крышка (TOP, потолок ячейки с 12/16).
 * FACING — куда смотрит стеклянная дверца (в зал, в проход). OPEN = дверца снята: ячейка пуста, в неё можно войти.
 * Декор: сиденья и сна нет; закрытая — только осмотр («CAPSULE SEALED»), открытая не реагирует.
 * Ставят LabBuilder (символ I чертежа B5) и RoundsBuilder (ниши обхода) через {@link #column}; предмета нет.
 * Текстуры общие с CapsuleBlock (capsule_frame, capsule_lid, capsule_lid_inner, capsule_pad).
 */
public final class UprightCapsuleBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<UprightCapsuleBlock> CODEC = simpleCodec(UprightCapsuleBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    /** Часть колонки: порядок снизу вверх, ordinal() = число клеток до основания. */
    public enum Part implements StringRepresentable {
        BASE("base"), MID("mid"), TOP("top");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** Высота колонки в клетках: нужна кладка высотой не меньше (потолок обхода — 3, зал B5 — 5). */
    public static final int HEIGHT = 3;

    /** Пол ячейки (верх основания) и низ потолка крышки, 1/16. Игрок 1.8 стоит на 6/16 и не упирается в потолок (2.75). */
    private static final double FLOOR_Y = 6;
    private static final double CAP_Y = 12;

    /**
     * Коробки при FACING = NORTH (дверца к -z): корпус x1..15, стенки по 2/16, полость 10/16 в ширину (плечи 0.6),
     * дверца — стекло z1..2 между боковыми стенками. Основание: цоколь до FLOOR_Y, выше — стенки до верха клетки.
     */
    private static double[][] base() {
        return new double[][]{
                {1, 0, 1, 15, FLOOR_Y, 15},      // цоколь с полом ячейки
                {1, FLOOR_Y, 1, 3, 16, 15},      // западная стенка
                {13, FLOOR_Y, 1, 15, 16, 15},    // восточная стенка
                {3, FLOOR_Y, 13, 13, 16, 15}};   // задняя стенка
    }

    private static double[][] mid() {
        return new double[][]{
                {1, 0, 1, 3, 16, 15},
                {13, 0, 1, 15, 16, 15},
                {3, 0, 13, 13, 16, 15}};
    }

    private static double[][] top() {
        return new double[][]{
                {1, 0, 1, 3, CAP_Y, 15},
                {13, 0, 1, 15, CAP_Y, 15},
                {3, 0, 13, 13, CAP_Y, 15},
                {1, CAP_Y, 1, 15, 16, 15}};      // потолок ячейки
    }

    private static final double[] GLASS_BASE = {3, FLOOR_Y, 1, 13, 16, 2};
    private static final double[] GLASS_MID = {3, 0, 1, 13, 16, 2};
    private static final double[] GLASS_TOP = {3, 0, 1, 13, CAP_Y, 2};

    private static final VoxelShape[] BASE_OPEN = LabShapes.horizontal(base());
    private static final VoxelShape[] MID_OPEN = LabShapes.horizontal(mid());
    private static final VoxelShape[] TOP_OPEN = LabShapes.horizontal(top());
    private static final VoxelShape[] BASE_CLOSED = withGlass(base(), GLASS_BASE);
    private static final VoxelShape[] MID_CLOSED = withGlass(mid(), GLASS_MID);
    private static final VoxelShape[] TOP_CLOSED = withGlass(top(), GLASS_TOP);

    private static VoxelShape[] withGlass(double[][] boxes, double[] glass) {
        double[][] all = java.util.Arrays.copyOf(boxes, boxes.length + 1);
        all[boxes.length] = glass;
        return LabShapes.horizontal(all);
    }

    public UprightCapsuleBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, Part.BASE)
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

    /** Три состояния колонки снизу вверх (индекс = Part.ordinal()): строитель кладёт их в клетки y, y+1, y+2. */
    public static BlockState[] column(Direction facing, boolean open) {
        BlockState base = ModBlocks.CAPSULE_UPRIGHT.get().defaultBlockState()
                .setValue(FACING, facing)
                .setValue(OPEN, open);
        BlockState[] out = new BlockState[HEIGHT];
        for (Part part : Part.values()) out[part.ordinal()] = base.setValue(PART, part);
        return out;
    }

    private static boolean isPart(BlockState st, Part part, Direction facing) {
        return st.getBlock() instanceof UprightCapsuleBlock
                && st.getValue(PART) == part
                && st.getValue(FACING) == facing;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int i = state.getValue(FACING).get2DDataValue();
        boolean open = state.getValue(OPEN);
        return switch (state.getValue(PART)) {
            case BASE -> open ? BASE_OPEN[i] : BASE_CLOSED[i];
            case MID -> open ? MID_OPEN[i] : MID_CLOSED[i];
            case TOP -> open ? TOP_OPEN[i] : TOP_CLOSED[i];
        };
    }

    /** Колонка держится целиком: потеря любой клетки убирает соседние (как у тройки CapsuleBlock). */
    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        Part part = state.getValue(PART);
        if (dir == Direction.UP && part != Part.TOP) {
            Part next = part == Part.BASE ? Part.MID : Part.TOP;
            return isPart(neighbor, next, facing) ? state : Blocks.AIR.defaultBlockState();
        }
        if (dir == Direction.DOWN && part != Part.BASE) {
            Part prev = part == Part.TOP ? Part.MID : Part.BASE;
            return isPart(neighbor, prev, facing) ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive() || state.getValue(OPEN)) return InteractionResult.PASS;
        if (!level.isClientSide) player.displayClientMessage(Component.translatable("hypersomnia.lab.capsule.sealed"), true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
