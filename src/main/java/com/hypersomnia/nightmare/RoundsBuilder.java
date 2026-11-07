package com.hypersomnia.nightmare;

import com.hypersomnia.lab.LabSigns;
import com.hypersomnia.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.slf4j.Logger;

/** Ряд капсул без общих для игроков событий или физического осколка. */
public final class RoundsBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final String[] MARKS = {
            "Cognitive collapse", "Sleep did not resume", "Subject unresponsive",
            "Terminated during session", "Dissociation, discharged"
    };
    private RoundsBuilder() {}
    private static int x() { return NightmareDefs.ROUNDS.origin().getX(); }
    private static int z() { return NightmareDefs.ROUNDS.origin().getZ(); }
    private static int y() { return NightmareTuning.ROUNDS_Y; }
    private static int capsuleX(int index) { return x() + 2 + index * NightmareTuning.ROUNDS_CAPSULE_STRIDE; }
    public static BlockPos spawn() { return new BlockPos(x(), y() + 1, z() + 1); }
    public static BlockPos exitFoot() { return new BlockPos(capsuleX(NightmareTuning.ROUNDS_CAPSULES - 1), y() + 1, z() + 4); }
    public static boolean isExit(BlockPos p) { return p.equals(exitFoot()) || p.equals(exitFoot().east()); }
    public static boolean containsBuilt(ServerLevel level, BlockPos p) {
        int length = NightmareTuning.ROUNDS_CAPSULES * NightmareTuning.ROUNDS_CAPSULE_STRIDE + 3;
        return NightmareSavedData.get(level).isBuilt(NightmareDefs.ROUNDS)
                && p.getX() >= x() - 1 && p.getX() <= x() + length
                && p.getZ() >= z() - 1 && p.getZ() <= z() + 7
                && p.getY() >= y() && p.getY() <= y() + 4;
    }
    public static boolean ensureBuilt(ServerLevel level) {
        NightmareSavedData data = NightmareSavedData.get(level);
        if (data.isBuilt(NightmareDefs.ROUNDS)) return true;
        if (data.roundsBuildFailed()) return false;
        int length = NightmareTuning.ROUNDS_CAPSULES * NightmareTuning.ROUNDS_CAPSULE_STRIDE + 3;
        if (y() < level.getMinBuildHeight() || y() + 4 >= level.getMaxBuildHeight()) return false;
        for (int dx = -1; dx <= length; dx++) for (int dz = -1; dz <= 7; dz++) for (int dy = 0; dy <= 4; dy++) {
            BlockPos pos = new BlockPos(x() + dx, y() + dy, z() + dz);
            if (!level.getWorldBorder().isWithinBounds(pos) || !level.getBlockState(pos).isAir()) {
                data.setRoundsBuildFailed(true);
                LOGGER.warn("Rounds location is occupied or outside the world border; no blocks were changed");
                return false;
            }
        }
        try {
            for (int dx = -1; dx <= length; dx++) for (int dz = -1; dz <= 7; dz++) for (int dy = 0; dy <= 4; dy++) {
                BlockState state = null;
                if (dy == 0 || dy == 4) state = Blocks.SMOOTH_STONE.defaultBlockState();
                else if (dx == -1 || dx == length || dz == -1 || dz == 7) state = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
                if (state != null && !level.setBlock(new BlockPos(x() + dx, y() + dy, z() + dz), state, FLAGS))
                    throw new IllegalStateException("Could not place rounds shell");
            }
            for (int i = 0; i < NightmareTuning.ROUNDS_CAPSULES; i++) {
                int cx = capsuleX(i);
                for (int dx = 0; dx < 2; dx++) {
                    level.setBlock(new BlockPos(cx + dx, y() + 1, z() + 5), Blocks.SMOOTH_QUARTZ.defaultBlockState(), FLAGS);
                    level.setBlock(new BlockPos(cx + dx, y() + 2, z() + 5), Blocks.TINTED_GLASS.defaultBlockState(), FLAGS);
                }
                String number = String.format(java.util.Locale.ROOT, "CAPSULE %02d", i + 1);
                if (i < NightmareTuning.ROUNDS_CAPSULES - 1) {
                    level.setBlock(new BlockPos(cx, y() + 1, z() + 4), Blocks.SMOOTH_QUARTZ.defaultBlockState(), FLAGS);
                    level.setBlock(new BlockPos(cx + 1, y() + 1, z() + 4), Blocks.SMOOTH_QUARTZ.defaultBlockState(), FLAGS);
                    LabSigns.wall(level, new BlockPos(cx, y() + 2, z() + 3), Direction.NORTH, number, MARKS[i % MARKS.length]);
                } else {
                    BlockState bed = Blocks.WHITE_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.EAST);
                    level.setBlock(exitFoot(), bed.setValue(net.minecraft.world.level.block.BedBlock.PART, BedPart.FOOT), FLAGS);
                    level.setBlock(exitFoot().east(), bed.setValue(net.minecraft.world.level.block.BedBlock.PART, BedPart.HEAD), FLAGS);
                    LabSigns.wall(level, new BlockPos(cx, y() + 2, z() + 3), Direction.NORTH, number);
                }
                if (i % 4 == 0) level.setBlock(new BlockPos(cx, y() + 4, z() + 1), Blocks.SEA_LANTERN.defaultBlockState(), FLAGS);
                if (i < NightmareTuning.ROUNDS_CAPSULES - 1 && i % NightmareTuning.ROUNDS_SYRINGE_EVERY == 0) {
                    ItemEntity syringe = new ItemEntity(level, cx + 0.5, y() + 1.1, z() + 2.5,
                            new ItemStack(ModItems.ADRENALINE_SYRINGE.get()));
                    syringe.addTag("hypersomnia_rounds_syringe");
                    syringe.setUnlimitedLifetime();
                    syringe.setPickUpDelay(NightmareTuning.ROUNDS_SYRINGE_PICKUP_DELAY);
                    syringe.setInvulnerable(true);
                    syringe.setNoGravity(true);
                    if (!level.addFreshEntity(syringe)) throw new IllegalStateException("Could not place rounds syringe");
                }
            }
            data.setBuilt(NightmareDefs.ROUNDS, true);
            return true;
        } catch (RuntimeException error) {
            data.setRoundsBuildFailed(true);
            LOGGER.error("Rounds construction failed; inspect the area before repair", error);
            return false;
        }
    }
}
