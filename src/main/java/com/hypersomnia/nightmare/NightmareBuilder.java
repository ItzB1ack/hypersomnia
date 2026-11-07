package com.hypersomnia.nightmare;

import com.hypersomnia.dream.DreamDimension;
import com.hypersomnia.ModBlocks;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Построители мест кошмаров. Вызывать только на сервере. */
public final class NightmareBuilder {
    private NightmareBuilder() {}
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    // Приглушённая палитра: контрастные края пола, светлые стены и холодная полоса.
    private static final BlockState FLOOR = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState FLOOR_EDGE = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState CEIL = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState PANEL = Blocks.WHITE_CONCRETE.defaultBlockState();
    private static final BlockState WALL = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState TRIM = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState STRIPE = Blocks.LIGHT_BLUE_TERRACOTTA.defaultBlockState();
    private static final BlockState WINDOW = Blocks.LIGHT_GRAY_STAINED_GLASS.defaultBlockState();
    private static final BlockState LIGHT_CORRIDOR =
            Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, NightmareTuning.LIGHT_CORRIDOR);
    private static final BlockState LIGHT_WARD =
            Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, 8);

    /** true, если место кошмара есть (при необходимости строит сразу). Для кошмаров без построителя всегда false. */
    public static boolean ensureBuilt(MinecraftServer server, NightmareDefs n) {
        if (n == NightmareDefs.ROUNDS || n == NightmareDefs.SESSION_47) {
            ServerLevel dream = server.getLevel(DreamDimension.LEVEL);
            return dream != null && (n == NightmareDefs.ROUNDS
                    ? RoundsBuilder.ensureBuilt(dream) : SessionBuilder.ensureBuilt(dream));
        }
        if (n != NightmareDefs.WARD_CORRIDOR) return false; // №2-4: свои построители в шагах 7+
        ServerLevel dream = server.getLevel(DreamDimension.LEVEL);
        if (dream == null) return false;
        NightmareSavedData data = NightmareSavedData.get(dream);
        if (data.isBuilt(n)) return portalsReady(dream);
        if (data.wardBuildFailed()) return false;
        return build(server, n);
    }

    /** Строит (или перестраивает) место кошмара. */
    public static boolean build(MinecraftServer server, NightmareDefs n) {
        if (n == NightmareDefs.ROUNDS || n == NightmareDefs.SESSION_47) return ensureBuilt(server, n);
        if (n != NightmareDefs.WARD_CORRIDOR) return false;
        ServerLevel dream = server.getLevel(DreamDimension.LEVEL);
        if (dream == null) return false;
        NightmareSavedData data = NightmareSavedData.get(dream);
        data.setWardBuildFailed(false); // только явный вызов build может повторить неудачную стройку
        data.setBuilt(n, false);
        try {
            buildWard(dream, n.origin());
            data.setBuilt(n, true);
        } catch (RuntimeException error) {
            data.setWardBuildFailed(true);
            LOGGER.error("Ward build failed; automatic retries disabled. Inspect the world before an explicit rebuild", error);
            return false;
        }
        return portalsReady(dream);
    }

    private static boolean portalsReady(ServerLevel dream) {
        try { return WardLoopPortals.ensure(dream); }
        catch (RuntimeException error) {
            LOGGER.error("Ward portal preparation failed; ordinary dream remains available", error);
            return false;
        }
    }

    /**
     * Коридор вдоль +X: внутри z 0..W-1, высота H, пол на y = origin.y. Палаты по обе стороны, проём в центре каждой.
     * Палаты повторяются через P блоков. Ячейки x = -1 и L+1 — торцы.
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

    /**
     * План палат повторяется каждые 8 блоков. Сдвиг порталов — 24: на стыке
     * совпадают не только стены, но и вся мебель, отделка и освещение.
     * m=3..4 — вход и свободный проход; две койки стоят по его сторонам.
     */
    private static BlockState wardCell(int x, int ry, int z, int p, int len, int h, int w, int d, int zN, int zS) {
        int m = Math.floorMod(x, p);
        boolean shell = x == -1 || x == len + 1 || z == zN || z == zS;
        boolean corridor = z >= 0 && z < w;
        boolean front = z == -1 || z == w;
        // t=1 у входа, t=d у дальней стены палаты с любой стороны.
        int t = z < 0 ? -z - 1 : z - w;

        if (ry == 0) {
            if (corridor) return (z == 0 || z == w - 1) ? FLOOR_EDGE : FLOOR;
            return FLOOR;
        }
        if (ry == h + 1) {
            boolean corridorLamp = corridor && m == 4 && z == w / 2;
            boolean wardLamp = !front && !corridor && !shell && m == 4 && t == 3;
            return corridorLamp || wardLamp ? PANEL : CEIL;
        }
        if (shell) {
            // Акцентная полоса на дальней стене — не занимает место внутри палаты.
            if ((z == zN || z == zS) && x >= 0 && x <= len && m != 0 && ry == 2) return STRIPE;
            return WALL;
        }
        if (corridor) {
            return (ry == h && m == 4 && z == w / 2) ? LIGHT_CORRIDOR : AIR;
        }
        if (front) {
            if ((m == 3 || m == 4) && ry <= 2) return AIR;
            if ((m == 2 || m == 5) && ry <= 3) return TRIM;
            if ((m == 3 || m == 4) && ry == 3) return TRIM;
            if (m == 6 && (ry == 2 || ry == 3)) return WINDOW;
            return ry == 2 ? STRIPE : PANEL;
        }
        if (m == 0) return ry == 2 ? WALL : PANEL;
        if (ry == h && m == 4 && t == 3) return LIGHT_WARD;

        // Две полноценные койки с доступом со стороны центрального прохода.
        boolean bed = (m == 2 || m == 6) && (t == 3 || t == 4);
        if (ry == 1 && bed) {
            return WardDecorBlocks.BED.defaultBlockState()
                    .setValue(WardBedBlock.FACING, z < 0 ? Direction.NORTH : Direction.SOUTH)
                    .setValue(WardBedBlock.PART, t == 4 ? BedPart.HEAD : BedPart.FOOT);
        }
        // Мониторы рядом с изголовьями, экраны повёрнуты к койкам.
        if (ry == 1 && t == 4 && (m == 1 || m == 7)) {
            return WardDecorBlocks.MONITOR.defaultBlockState()
                    .setValue(WardMonitorBlock.FACING, m == 1 ? Direction.EAST : Direction.WEST);
        }
        // Вместо безликих кубов — тумбы с ящиками из существующих моделей мода.
        if (ry == 1 && t == 5 && (m == 1 || m == 7)) {
            return ModBlocks.DESK.get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, m == 1 ? Direction.EAST : Direction.WEST);
        }
        // Небольшой стол и стул для посетителя у боковой стены; середина комнаты свободна.
        if (m == 7 && t == 2 && ry == 1) {
            return ModBlocks.DESK.get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, z < 0 ? Direction.SOUTH : Direction.NORTH);
        }
        if (m == 7 && t == 1 && ry == 1) {
            return ModBlocks.CHAIR.get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, z < 0 ? Direction.NORTH : Direction.SOUTH);
        }
        if (m == 7 && t == 2 && ry == 2) return Blocks.POTTED_FERN.defaultBlockState();
        return AIR;
    }
}
