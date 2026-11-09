package com.hypersomnia.lab;

import com.hypersomnia.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
 * FACING — от изножья к изголовью. OPEN = крышка поднята: в открытую можно лечь (SeatEntity.lie), после чего
 * она закрывается сама и выпускает игрока только при вставании. Закрытая снаружи — только осмотр («CAPSULE SEALED»).
 * Открытость задаёт чертёж (LabTuning.capsuleOpen), ставит LabBuilder. Предмет — только для творческой вкладки:
 * ставит всю тройку вперёд по взгляду, крышка открыта.
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
    /** Закрытая крышка — полая П-образная скорлупа: борта на рейлингах и потолок, изнутри непрозрачный опалубок. */
    private static final double[] GLASS = {1, RAIL_Y, 0, 15, 16, 16};
    /** Крышка изголовья начинается после консоли, иначе купол пересекается с ней. */
    private static final double[] HEAD_GLASS = {1, RAIL_Y, 3, 15, 16, 16};

    /** Общие коробки: цоколь, ложе между бортами, два борта. */
    private static double[][] body() {
        return new double[][]{
                {1, 0, 0, 15, PLINTH_Y, 16},          // цоколь
                {3, PLINTH_Y, 0, 13, PAD_Y, 16},      // ложе (верх 6/16)
                {1, PLINTH_Y, 0, 3, RAIL_Y, 16},      // западный борт
                {13, PLINTH_Y, 0, 15, RAIL_Y, 16}};   // восточный борт
    }

    /** Изножье: торцевая стенка над ложем, чтобы закрытая капсула не светилась насквозь. */
    private static double[][] foot() {
        double[][] b = body();
        double[][] all = java.util.Arrays.copyOf(b, b.length + 1);
        all[b.length] = new double[]{3, PAD_Y, 14, 13, RAIL_Y, 16}; // стенка изножья
        return all;
    }

    /** Изголовье: стенка от ложа до бортов, консоль над головой и подушка. */
    private static final double[][] HEAD_BOXES = head();

    private static double[][] head() {
        double[][] b = body();
        double[][] all = java.util.Arrays.copyOf(b, b.length + 3);
        all[b.length] = new double[]{3, PAD_Y, 0, 13, RAIL_Y, 2};  // стенка изголовья от ложа до бортов
        all[b.length + 1] = new double[]{3, RAIL_Y, 0, 13, 16, 3}; // консоль/поднятая крышка над головой
        all[b.length + 2] = new double[]{3, PAD_Y, 2, 13, 7, 5};   // подушка
        return all;
    }

    private static final VoxelShape[] FOOT_SHAPES = LabShapes.horizontal(foot());
    private static final VoxelShape[] MID_SHAPES = LabShapes.horizontal(body());
    private static final VoxelShape[] HEAD_SHAPES = LabShapes.horizontal(HEAD_BOXES);
    private static final VoxelShape[] FOOT_CLOSED_SHAPES = withGlass(foot(), GLASS);
    private static final VoxelShape[] MID_CLOSED_SHAPES = withGlass(body(), GLASS);
    private static final VoxelShape[] HEAD_CLOSED_SHAPES = withGlass(HEAD_BOXES, HEAD_GLASS);

    private static VoxelShape[] withGlass(double[][] boxes, double[] glass) {
        double[][] all = java.util.Arrays.copyOf(boxes, boxes.length + 1);
        all[boxes.length] = glass;
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

    /** Тройка цела (крышка может быть в любом положении): изножье в foot, середина и изголовье по facing. */
    public static boolean isIntact(BlockGetter level, BlockPos foot, Direction facing) {
        if (foot == null || facing == null || facing.getAxis().isVertical()) return false;
        return isPart(level.getBlockState(foot), Part.FOOT, facing)
                && isPart(level.getBlockState(foot.relative(facing)), Part.MID, facing)
                && isPart(level.getBlockState(foot.relative(facing, 2)), Part.HEAD, facing);
    }

    /** Тройка цела и открыта: можно лечь. */
    public static boolean canLie(BlockGetter level, BlockPos foot, Direction facing) {
        return isIntact(level, foot, facing) && level.getBlockState(foot).getValue(OPEN);
    }

    /**
     * Открыть или закрыть все три клетки целой тройки (крышка — общее состояние). Игрок в капсуле при этом
     * остаётся на месте: сиденье (SeatEntity) проверяет целость, а не OPEN. false — тройка не цела.
     */
    public static boolean setOpen(Level level, BlockPos foot, Direction facing, boolean open) {
        if (!isIntact(level, foot, facing)) return false;
        boolean changed = false;
        for (int i = 0; i < 3; i++) {
            BlockPos pos = foot.relative(facing, i);
            BlockState st = level.getBlockState(pos);
            if (st.getValue(OPEN) != open) {
                level.setBlock(pos, st.setValue(OPEN, open), Block.UPDATE_ALL);
                changed = true;
            }
        }
        if (changed) {
            level.playSound(null, foot, open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE,
                    SoundSource.BLOCKS, LabTuning.CAPSULE_LID_VOLUME,
                    open ? LabTuning.CAPSULE_LID_OPEN_PITCH : LabTuning.CAPSULE_LID_CLOSE_PITCH);
        }
        return true;
    }

    private static boolean isPart(BlockState st, Part part, Direction facing) {
        return st.getBlock() instanceof CapsuleBlock
                && st.getValue(PART) == part
                && st.getValue(FACING) == facing;
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

    /** Творческий предмет: изножье в клетке клика, середина и изголовье впереди по взгляду, крышка открыта. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection();
        Level level = ctx.getLevel();
        for (int i = 1; i < 3; i++) {
            BlockPos p = ctx.getClickedPos().relative(facing, i);
            if (level.isOutsideBuildHeight(p) || !level.getBlockState(p).canBeReplaced(ctx)) return null;
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, Part.FOOT).setValue(OPEN, true);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (state.getValue(PART) != Part.FOOT) return;
        Direction facing = state.getValue(FACING);
        level.setBlock(pos.relative(facing), state.setValue(PART, Part.MID), Block.UPDATE_ALL);
        level.setBlock(pos.relative(facing, 2), state.setValue(PART, Part.HEAD), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        Part part = state.getValue(PART);
        // OPEN не сверяем: setOpen меняет три клетки по очереди, и сосед на миг ещё в старом положении.
        if (dir == facing && part != Part.HEAD) {
            Part next = part == Part.FOOT ? Part.MID : Part.HEAD;
            return isPart(neighbor, next, facing) ? state : Blocks.AIR.defaultBlockState();
        }
        if (dir == facing.getOpposite() && part != Part.FOOT) {
            Part prev = part == Part.HEAD ? Part.MID : Part.FOOT;
            return isPart(neighbor, prev, facing) ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    /** Ключ-карта (ModItems.KEYCARD): ПКМ открывает или закрывает всю тройку, в том числе закрытую снаружи. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.KEYCARD.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        Direction facing = state.getValue(FACING);
        BlockPos foot = pos.relative(facing.getOpposite(), state.getValue(PART).ordinal());
        if (!isIntact(level, foot, facing)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) setOpen(level, foot, facing, !level.getBlockState(foot).getValue(OPEN));
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive() || player.isPassenger()) return InteractionResult.PASS;
        Direction facing = state.getValue(FACING);
        BlockPos foot = pos.relative(facing.getOpposite(), state.getValue(PART).ordinal());
        if (!isPart(level.getBlockState(foot), Part.FOOT, facing)) return InteractionResult.PASS;
        if (!canLie(level, foot, facing)) {
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
