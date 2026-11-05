package com.hypersomnia.lab;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.Random;

/**
 * Выбор места входа по «сырому» рельефу генератора (чанки не создаются): ровная суша, без воды,
 * гор, джунглей и болот, на заданной высоте над морем, вдали от деревень. Выбор детерминирован
 * (seed + siteTry). Лучший кандидат по перепаду высот на всей площадке здания с двором; если
 * идеальных нет, берётся наименее плохой.
 */
public final class LabSiteFinder {
    private LabSiteFinder() {}

    private record Probe(boolean ok, int spread) {}

    public static BlockPos find(ServerLevel ow, int attempt) {
        BlockPos fallback = null;
        for (int retry = 0; retry < 4; retry++) {
            BlockPos p = findOnce(ow, attempt * 4 + retry);
            if (fallback == null) fallback = p;
            if (!villageNear(ow, p.getX(), p.getZ())) return p;
        }
        return fallback; // всё вокруг занято деревнями — берём первый вариант, стройка сама расчистит площадку
    }

    /** Один проход поиска по рельефу без проверки деревень (её делаем только для готового кандидата). */
    private static BlockPos findOnce(ServerLevel ow, int attempt) {
        ChunkGenerator gen = ow.getChunkSource().getGenerator();
        RandomState rs = ow.getChunkSource().randomState();
        Random r = new Random(ow.getSeed() ^ LabTuning.SEED_SALT ^ (attempt * 0x9E3779B97F4A7C15L));
        int bestX = 0, bestZ = 0, bestSpread = Integer.MAX_VALUE;
        for (int pass = 0; pass < 2; pass++) {
            double dMin = pass == 0 ? LabTuning.DIST_MIN : LabTuning.DIST_MIN * 0.7;
            double dMax = pass == 0 ? LabTuning.DIST_MAX : LabTuning.DIST_MAX * 1.8;
            for (int i = 0; i < LabTuning.ATTEMPTS; i++) {
                double a = r.nextDouble() * Math.PI * 2.0;
                double dist = dMin + r.nextDouble() * (dMax - dMin);
                int x = (int) Math.round(Math.cos(a) * dist);
                int z = (int) Math.round(Math.sin(a) * dist);
                Probe p = probe(ow, gen, rs, x, z);
                if (!p.ok) continue;
                if (p.spread < bestSpread) {
                    bestSpread = p.spread;
                    bestX = x;
                    bestZ = z;
                }
                if (p.spread <= LabTuning.SPREAD_OK) return new BlockPos(x, 0, z);
            }
            if (bestSpread <= LabTuning.SPREAD_MAX) break;
        }
        if (bestSpread == Integer.MAX_VALUE) {
            // Совсем нет подходящей суши: берём точку в первом кольце, постройка сама подготовит площадку.
            double a = r.nextDouble() * Math.PI * 2.0;
            bestX = (int) Math.round(Math.cos(a) * LabTuning.DIST_MIN);
            bestZ = (int) Math.round(Math.sin(a) * LabTuning.DIST_MIN);
        }
        return new BlockPos(bestX, 0, bestZ);
    }

    private static Probe probe(ServerLevel ow, ChunkGenerator gen, RandomState rs, int cx, int cz) {
        int sea = ow.getSeaLevel();
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        int step = LabTuning.SAMPLE_STEP;
        for (int dx = LabTuning.SAMPLE_X1; dx <= LabTuning.SAMPLE_X2; dx += step) {
            for (int dz = LabTuning.SAMPLE_Z1; dz <= LabTuning.SAMPLE_Z2; dz += step) {
                int x = cx + dx, z = cz + dz;
                int h = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ow, rs);
                boolean center = dx == 0 && dz == 0 || (dx == LabTuning.SAMPLE_X1 && dz == LabTuning.SAMPLE_Z1);
                if (h < sea + LabTuning.MIN_ABOVE_SEA || h > sea + LabTuning.MAX_ABOVE_SEA) return new Probe(false, 0);
                if (h <= sea + 2) {
                    int floor = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ow, rs);
                    if (floor < h) return new Probe(false, 0); // вода
                }
                if (center && !biomeOk(ow.getUncachedNoiseBiome(x >> 2, h >> 2, z >> 2))) return new Probe(false, 0);
                min = Math.min(min, h);
                max = Math.max(max, h);
                if (max - min > LabTuning.SPREAD_REJECT) return new Probe(false, 0);
            }
        }
        return new Probe(true, max - min);
    }

    /** Деревня ближе VILLAGE_CLEAR_BLOCKS: лаборатория не должна задевать её постройки. */
    private static boolean villageNear(ServerLevel ow, int x, int z) {
        BlockPos pos = new BlockPos(x, ow.getSeaLevel(), z);
        // radius 1 = соседние ячейки сетки структур: этого хватает, чтобы найти деревню в пределах ~130 блоков.
        BlockPos v = ow.findNearestMapStructure(StructureTags.VILLAGE, pos, 1, false);
        return v != null && v.distSqr(pos) <= (double) LabTuning.VILLAGE_CLEAR_BLOCKS * LabTuning.VILLAGE_CLEAR_BLOCKS;
    }

    private static boolean biomeOk(Holder<Biome> b) {
        return !(b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_RIVER) || b.is(BiomeTags.IS_BEACH)
                || b.is(BiomeTags.IS_MOUNTAIN) || b.is(BiomeTags.IS_JUNGLE)
                || b.is(Biomes.SWAMP) || b.is(Biomes.MANGROVE_SWAMP));
    }
}
