package com.hypersomnia.nightmare;

import com.hypersomnia.dream.DreamDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Построители мест кошмаров. Вызывать только на сервере. */
public final class NightmareBuilder {
    private NightmareBuilder() {}

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState FLOOR = Blocks.DEEPSLATE_TILES.defaultBlockState();
    private static final BlockState CEIL = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState PANEL = Blocks.WHITE_CONCRETE.defaultBlockState();
    private static final BlockState WALL = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState COT = Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState();
    private static final BlockState LIGHT_CORRIDOR =
            Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, NightmareTuning.LIGHT_CORRIDOR);
    private static final BlockState LIGHT_WARD =
            Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, NightmareTuning.LIGHT_WARD);

    /** true, если место кошмара есть (при необходимости строит сразу). Для кошмаров без построителя всегда false. */
    public static boolean ensureBuilt(MinecraftServer server, NightmareDefs n) {
        if (n != NightmareDefs.WARD_CORRIDOR) return false; // №2-4: свои построители в шагах 7+
        ServerLevel dream = server.getLevel(DreamDimension.LEVEL);
        if (dream == null) return false;
        if (NightmareSavedData.get(dream).isBuilt(n)) return true;
        return build(server, n);
    }

    /** Строит (или перестраивает) место кошмара. */
    public static boolean build(MinecraftServer server, NightmareDefs n) {
        if (n != NightmareDefs.WARD_CORRIDOR) return false;
        ServerLevel dream = server.getLevel(DreamDimension.LEVEL);
        if (dream == null) return false;
        buildWard(dream, n.origin());
        NightmareSavedData.get(dream).setBuilt(n, true);
        return true;
    }

    /**
     * Коридор вдоль +X: внутри z 0..W-1, высота H, пол на y = origin.y. Палаты по обе стороны, проём в центре каждой.
     * Рисунок повторяется с периодом P по X (нужно для телепорта на период). Ячейки x = -1 и L+1 — торцы.
     */
    private static void buildWard(ServerLevel lv, BlockPos o) {
        int p = NightmareTuning.WARD_PERIOD;
        int len = p * NightmareTuning.WARD_PERIODS;
        int h = NightmareTuning.CORRIDOR_HEIGHT;
        int w = NightmareTuning.CORRIDOR_WIDTH;
        int d = NightmareTuning.WARD_DEPTH;
        int zN = -(d + 2);
        int zS = w + d + 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -1; x <= len + 1; x++)
            for (int z = zN; z <= zS; z++)
                for (int ry = 0; ry <= h + 1; ry++) {
                    pos.set(o.getX() + x, o.getY() + ry, o.getZ() + z);
                    lv.setBlock(pos, wardCell(x, ry, z, p, len, h, w, d, zN, zS), FLAGS);
                }
    }

    private static BlockState wardCell(int x, int ry, int z, int p, int len, int h, int w, int d, int zN, int zS) {
        int door = NightmareTuning.DOOR_OFFSET;
        int m = x % p; // для x = -1 не используется: торец отсекается раньше
        boolean shell = x == -1 || x == len + 1 || z == zN || z == zS;
        if (ry == 0) return FLOOR;
        if (ry == h + 1) return (!shell && m == door && z == w / 2) ? PANEL : CEIL;
        if (shell) return WALL;
        if (z >= 0 && z < w) { // коридор
            return (ry == h && m == door && z == w / 2) ? LIGHT_CORRIDOR : AIR;
        }
        if (z == -1 || z == w) { // стена коридора с проёмом 2 высотой
            return (m == door && ry <= 2) ? AIR : WALL;
        }
        if (m == 0) return WALL; // перегородка между палатами
        // внутри палаты: койка у дальней стены, тусклый свет
        if (ry == 1 && (m == door + 1 || m == door + 2) && (z == -(d + 1) || z == w + d)) return COT;
        if (ry == h && m == door && (z == -((d + 1) / 2 + 1) || z == (w + 1) + (d - 1) / 2)) return LIGHT_WARD;
        return AIR;
    }
}