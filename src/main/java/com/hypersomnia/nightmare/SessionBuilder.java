package com.hypersomnia.nightmare;

import com.hypersomnia.lab.LabSigns;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/** Зал сеанса 47; генерация не перезаписывает существующую область. */
public final class SessionBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private SessionBuilder() {}
    private static int x() { return NightmareDefs.SESSION_47.origin().getX(); }
    private static int y() { return NightmareTuning.SESSION_Y; }
    private static int z() { return NightmareDefs.SESSION_47.origin().getZ(); }
    public static BlockPos spawn() { return new BlockPos(x() + 2, y() + 1, z() + 3); }
    public static BlockPos console() { return new BlockPos(x() + 8, y() + 1, z() + 3); }
    public static boolean containsBuilt(ServerLevel level, BlockPos p) {
        return NightmareSavedData.get(level).isBuilt(NightmareDefs.SESSION_47)
                && p.getX() >= x() - 1 && p.getX() <= x() + 14
                && p.getZ() >= z() - 1 && p.getZ() <= z() + 8
                && p.getY() >= y() && p.getY() <= y() + 5;
    }
    public static boolean ensureBuilt(ServerLevel level) {
        NightmareSavedData data = NightmareSavedData.get(level);
        if (data.isBuilt(NightmareDefs.SESSION_47)) return true;
        if (data.sessionBuildFailed()) return false;
        if (y() < level.getMinBuildHeight() || y() + 5 >= level.getMaxBuildHeight()) return false;
        for (int dx = -1; dx <= 14; dx++) for (int dz = -1; dz <= 8; dz++) for (int dy = 0; dy <= 5; dy++) {
            BlockPos pos = new BlockPos(x() + dx, y() + dy, z() + dz);
            if (!level.getWorldBorder().isWithinBounds(pos) || !level.getBlockState(pos).isAir()) {
                data.setSessionBuildFailed(true);
                LOGGER.warn("Session 47 location is occupied or outside the world border; no blocks were changed");
                return false;
            }
        }
        try {
            for (int dx = -1; dx <= 14; dx++) for (int dz = -1; dz <= 8; dz++) for (int dy = 0; dy <= 5; dy++) {
                BlockState state = null;
                if (dy == 0 || dy == 5) state = Blocks.SMOOTH_STONE.defaultBlockState();
                else if (dx == -1 || dx == 14 || dz == -1 || dz == 8) state = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
                if (state != null && !level.setBlock(new BlockPos(x() + dx, y() + dy, z() + dz), state, FLAGS))
                    throw new IllegalStateException("Could not place session shell");
            }
            level.setBlock(new BlockPos(x() + 5, y() + 5, z() + 3), Blocks.SEA_LANTERN.defaultBlockState(), FLAGS);
            level.setBlock(new BlockPos(x() + 11, y() + 5, z() + 4), Blocks.SEA_LANTERN.defaultBlockState(), FLAGS);
            level.setBlock(console(), Blocks.IRON_BLOCK.defaultBlockState(), FLAGS);
            for (int dx = 10; dx <= 12; dx++) {
                level.setBlock(new BlockPos(x() + dx, y() + 1, z() + 4), Blocks.SMOOTH_QUARTZ.defaultBlockState(), FLAGS);
                level.setBlock(new BlockPos(x() + dx, y() + 2, z() + 5), Blocks.TINTED_GLASS.defaultBlockState(), FLAGS);
            }
            LabSigns.wall(level, new BlockPos(x() + 9, y() + 2, z() + 7), Direction.NORTH, "SESSION 47", "INCIDENT PROTOCOL");
            LabSigns.wall(level, new BlockPos(x() + 7, y() + 2, z() + 7), Direction.NORTH, "CONSOLE", "ATTEMPT RESPONSE");
            data.setBuilt(NightmareDefs.SESSION_47, true);
            return true;
        } catch (RuntimeException error) {
            data.setSessionBuildFailed(true);
            LOGGER.error("Session 47 construction failed; inspect the area before repair", error);
            return false;
        }
    }
}
