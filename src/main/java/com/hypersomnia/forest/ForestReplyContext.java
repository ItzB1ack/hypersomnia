package com.hypersomnia.forest;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Небольшая эвристика «стволы + листва», без истории строительства и без загрузки чанков. */
public final class ForestReplyContext {
    private ForestReplyContext() {}

    public static boolean loaded(Level level, BlockPos pos) {
        return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    /**
     * Работает и для выращенных игроком деревьев. Не обещает отличить декоративное дерево
     * от природного: два бревна и три блока листвы поблизости — сознательно простая граница.
     */
    public static boolean hasTree(Level level, BlockPos center) {
        int logs = 0;
        int leaves = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                pos.set(center.getX() + dx, center.getY(), center.getZ() + dz);
                if (!loaded(level, pos)) continue;
                for (int dy = -2; dy <= 10; dy++) {
                    pos.setY(center.getY() + dy);
                    if (level.isOutsideBuildHeight(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.is(BlockTags.LOGS)) logs++;
                    else if (state.is(BlockTags.LEAVES)) leaves++;
                    if (logs >= 2 && leaves >= 3) return true;
                }
            }
        }
        return false;
    }
}