package com.hypersomnia.nightmare;

import com.hypersomnia.ModBlocks;
import com.hypersomnia.lab.LiftPanelBlock;
import com.hypersomnia.lab.LabSigns;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

/** Отдельные кабины и пустые коридоры для персональных поездок. */
public final class ShaftBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private ShaftBuilder() {}
    public static boolean validStop(int stop) { return stop == 0 || (stop >= 6 && stop <= 8); }
    /** [новое] Лифт только спускается: этаж = B6 + число поездок. Физических кабин четыре, глубже B8 кабина B8 повторяется. */
    public static final int FIRST_FLOOR = 6;
    public static int slotOf(int floor) { return Math.max(FIRST_FLOOR, Math.min(8, floor)); }
    public static int index(int stop) { return stop == 0 ? 0 : stop - 5; }
    public static int x(int stop) { return NightmareDefs.SHAFT.origin().getX() + index(stop) * NightmareTuning.SHAFT_STOP_STRIDE; }
    public static int z(int lane) { return NightmareDefs.SHAFT.origin().getZ() + lane * NightmareTuning.SHAFT_LANE_STRIDE; }
    public static int y() { return NightmareTuning.SHAFT_Y; }
    public static BlockPos spawn(int lane, int stop) { return new BlockPos(x(stop) + 1, y() + 1, z(lane) + 1); }
    public static BlockPos panel(int lane, int stop) { return new BlockPos(x(stop), y() + 2, z(lane) + 2); }
    public static AABB cabin(int lane, int stop) { return new AABB(x(stop), y() + 1, z(lane), x(stop) + 3, y() + 4, z(lane) + 3); }
    public static int stopOf(int lane, net.minecraft.world.phys.Vec3 pos) {
        for (int stop : new int[]{0, 6, 7, 8}) if (cabin(lane, stop).contains(pos)) return stop;
        return -1;
    }
    public static boolean containsBuilt(ServerLevel level, BlockPos pos) {
        ShaftStorage data = ShaftStorage.get(level);
        if (pos.getY() < y() || pos.getY() > y() + 4) return false;
        for (int lane = 0; lane < NightmareTuning.SHAFT_LANES; lane++) {
            if (!data.built(lane) || pos.getZ() < z(lane) - 1 || pos.getZ() > z(lane) + 3) continue;
            for (int stop : new int[]{0, 6, 7, 8})
                if (pos.getX() >= x(stop) - 1 && pos.getX() <= x(stop) + 8) return true;
        }
        return false;
    }
    public static boolean ensureBuilt(ServerLevel level, int lane) {
        if (lane < 0 || lane >= NightmareTuning.SHAFT_LANES) return false;
        ShaftStorage data = ShaftStorage.get(level);
        if (data.layout() != NightmareTuning.SHAFT_LAYOUT) {
            // [новое] Новая версия вида шахты: старые секции (только наши) очищаются, пока в шахте никого нет.
            if (NightmareLayout.anyoneInside(level.getServer(), NightmareDefs.SHAFT.id())) return data.built(lane);
            for (int l = 0; l < NightmareTuning.SHAFT_LANES; l++) {
                if (!data.built(l)) continue;
                for (int stop : new int[]{0, 6, 7, 8})
                    NightmareLayout.clear(level, x(stop) - 1, y(), z(l) - 1, x(stop) + 8, y() + 4, z(l) + 3);
            }
            data.resetBuilds();
        }
        if (data.built(lane)) return true;
        if (data.failed(lane)) return false;
        int y = y(), z = z(lane);
        if (y < level.getMinBuildHeight() || y + 4 >= level.getMaxBuildHeight()) return false;
        // Полный preflight: существующие постройки и инвентари не перезаписываем.
        for (int stop : new int[]{0, 6, 7, 8}) {
            int x = x(stop);
            for (int dx = -1; dx <= 8; dx++) for (int dz = -1; dz <= 3; dz++) for (int dy = 0; dy <= 4; dy++) {
                BlockPos p = new BlockPos(x + dx, y + dy, z + dz);
                if (!level.getWorldBorder().isWithinBounds(p) || !level.getBlockState(p).isAir()) {
                    data.setFailed(lane);
                    LOGGER.warn("Shaft lane {} is occupied or outside the world border; leaving it untouched", lane);
                    return false;
                }
            }
        }
        try {
            for (int stop : new int[]{0, 6, 7, 8}) buildStop(level, lane, stop);
            data.setBuilt(lane);
            return true;
        } catch (RuntimeException error) {
            data.setFailed(lane);
            LOGGER.error("Shaft construction failed for lane {}; inspect the area before repair", lane, error);
            return false;
        }
    }
    private static void put(ServerLevel level, int x, int y, int z, BlockState state) {
        if (!level.setBlock(new BlockPos(x, y, z), state, FLAGS)) throw new IllegalStateException("Could not place shaft block");
    }
    private static void sealedDoor(ServerLevel level, int x, int y, int z, Direction facing) {
        BlockState door = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing);
        put(level, x, y + 1, z, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        put(level, x, y + 2, z, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }
    /** [новое] Тема этажа: 0 тёплый и обычный, B6 служебный, B7 темнее, B8 голый и почти без света. */
    private static void buildStop(ServerLevel level, int lane, int stop) {
        int x = x(stop), y = y(), z = z(lane);
        BlockState cabin = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState frame = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState wall = (stop == 0 ? Blocks.WHITE_CONCRETE : stop == 6 ? Blocks.LIGHT_GRAY_CONCRETE
                : stop == 7 ? Blocks.GRAY_CONCRETE : Blocks.DEEPSLATE_BRICKS).defaultBlockState();
        BlockState floor = (stop == 0 ? Blocks.BIRCH_PLANKS : stop == 6 ? Blocks.POLISHED_ANDESITE
                : stop == 7 ? Blocks.STONE : Blocks.COBBLED_DEEPSLATE).defaultBlockState();
        BlockState ceil = (stop == 8 ? Blocks.DEEPSLATE : Blocks.SMOOTH_STONE).defaultBlockState();
        for (int dx = -1; dx <= 8; dx++) for (int dz = -1; dz <= 3; dz++) for (int dy = 0; dy <= 4; dy++) {
            BlockState state;
            boolean inCabin = dx <= 2;
            if (dy == 0) state = inCabin ? cabin : floor;
            else if (dy == 4) state = inCabin ? cabin : ceil;
            else if (dx == -1 || dx == 8 || dz == -1 || dz == 3) state = inCabin ? cabin : wall;
            else if (dx == 3 && (dz != 1 || dy == 3)) state = frame;
            else continue;
            put(level, x + dx, y + dy, z + dz, state);
        }
        // Свет: кабина ровная, коридор слабеет с глубиной (B8 почти тёмный).
        put(level, x + 1, y + 4, z + 1, Blocks.WHITE_STAINED_GLASS.defaultBlockState());
        put(level, x + 1, y + 3, z + 1, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 7));
        int lvl = stop == 0 ? 12 : stop == 6 ? 8 : stop == 7 ? 4 : 2;
        if (stop != 8) put(level, x + 5, y + 4, z + 1, Blocks.WHITE_STAINED_GLASS.defaultBlockState());
        put(level, x + 5, y + 3, z + 1, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, lvl));
        put(level, panel(lane, stop).getX(), panel(lane, stop).getY(), panel(lane, stop).getZ(),
                ModBlocks.LIFT_PANEL.get().defaultBlockState().setValue(LiftPanelBlock.FACING, Direction.NORTH));
        // Запертые железные двери в стенах коридора: B6 с обеих сторон, B7 с одной, B8 и 0 без них.
        if (stop == 6 || stop == 7) sealedDoor(level, x + 5, y, z + 3, Direction.NORTH);
        if (stop == 6) sealedDoor(level, x + 5, y, z - 1, Direction.SOUTH);
        if (stop == 0) put(level, x + 7, y + 1, z, Blocks.POTTED_FERN.defaultBlockState());
        // Табличка у торца: на B8 её нет.
        if (stop != 8) LabSigns.wall(level, new BlockPos(x + 7, y + 2, z + 1), Direction.WEST, stop == 0 ? "0" : "B" + stop);
    }
    public static boolean setDoor(ServerLevel level, int lane, int stop, boolean closed) {
        if (!validStop(stop)) return false;
        BlockPos p = new BlockPos(x(stop) + 3, y() + 1, z(lane) + 1);
        for (int i = 0; i < 2; i++) {
            BlockState state = level.getBlockState(p.above(i));
            if (!state.isAir() && !state.is(Blocks.IRON_BARS)) return false;
        }
        for (int i = 0; i < 2; i++) level.setBlock(p.above(i), closed ? Blocks.IRON_BARS.defaultBlockState() : Blocks.AIR.defaultBlockState(), FLAGS);
        return true;
    }
}
