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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

/** Отдельные кабины и пустые коридоры для персональных поездок. */
public final class ShaftBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private ShaftBuilder() {}
    public static boolean validStop(int stop) { return stop == 0 || (stop >= 6 && stop <= 8); }
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
    private static void buildStop(ServerLevel level, int lane, int stop) {
        int x = x(stop), y = y(), z = z(lane);
        for (int dx = -1; dx <= 8; dx++) for (int dz = -1; dz <= 3; dz++) for (int dy = 0; dy <= 4; dy++) {
            BlockState state;
            if (dy == 0) state = Blocks.SMOOTH_STONE.defaultBlockState();
            else if (dy == 4) state = Blocks.SMOOTH_STONE.defaultBlockState();
            else if (dx == -1 || dx == 8 || dz == -1 || dz == 3) state = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
            else if (dx == 3 && (dz != 1 || dy == 3)) state = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
            else state = Blocks.AIR.defaultBlockState();
            if (!state.isAir() && !level.setBlock(new BlockPos(x + dx, y + dy, z + dz), state, FLAGS))
                throw new IllegalStateException("Could not place shaft block");
        }
        if (!level.setBlock(new BlockPos(x + 1, y + 4, z + 1), Blocks.SEA_LANTERN.defaultBlockState(), FLAGS)
                || !level.setBlock(panel(lane, stop), ModBlocks.LIFT_PANEL.get().defaultBlockState()
                .setValue(LiftPanelBlock.FACING, Direction.NORTH), FLAGS))
            throw new IllegalStateException("Could not place shaft panel or light");
        LabSigns.wall(level, new BlockPos(x + 7, y + 2, z + 1), Direction.WEST, stop == 0 ? "0" : "B" + stop);
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
