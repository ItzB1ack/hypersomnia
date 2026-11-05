package com.hypersomnia.client;

import com.hypersomnia.ObserverTuning;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * Места для появления осколка в укрытии: за стволом дерева (выглядывает из-за края)
 * или за стеклом окна. Возвращают ноги осколка или null. ok(feet) проверяет свет и видимость.
 */
public final class ObserverCover {
    private ObserverCover() {}

    /**
     * Затемнение неба сейчас: 0 днём, до 11 ночью. ClientLevel пересчитывает своё getSkyDarken() только при входе
     * в мир, поэтому на клиенте isNight() и getMaxLocalRawBrightness(pos) «застывают» на моменте входа.
     * Здесь та же формула, что в Level.updateSkyBrightness(), но от текущего времени и погоды.
     */
    public static int skyDarken(Level level) {
        double rain = 1.0 - level.getRainLevel(1f) * 5f / 16.0;
        double thunder = 1.0 - level.getThunderLevel(1f) * 5f / 16.0;
        double day = 0.5 + 2.0 * Mth.clamp(Mth.cos(level.getTimeOfDay(1f) * ((float) Math.PI * 2f)), -0.25, 0.25);
        return (int) ((1.0 - day * rain * thunder) * 11.0);
    }

    /** Освещённость клетки с текущим небом (вместо застывшего getMaxLocalRawBrightness(pos) на клиенте). */
    public static int brightness(Level level, BlockPos pos) {
        return level.getMaxLocalRawBrightness(pos, skyDarken(level));
    }

    /** Ночь по текущему небу; правило то же, что у Level.isNight(). */
    public static boolean isNight(Level level) {
        return !level.dimensionType().hasFixedTime() && skyDarken(level) >= 4;
    }

    /**
     * Чанк клетки действительно получен клиентом. ClientLevel.hasChunk() всегда true, а неполученный чанк читается
     * как пустота (VOID_AIR), поэтому проверка идёт через сам кэш чанков.
     */
    public static boolean loaded(Level level, BlockPos p) {
        return level.getChunkSource().hasChunk(p.getX() >> 4, p.getZ() >> 4);
    }

    /** Видимость с учётом стекла: луч проходит сквозь стёкла и панели. */
    public static boolean hasSight(Level level, LocalPlayer player, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1.0E-4) return true;
        Vec3 step = d.scale(0.15 / len);
        Vec3 cur = from;
        for (int i = 0; i < 6; i++) {
            BlockHitResult hit = level.clip(new ClipContext(cur, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS) return true;
            BlockState s = level.getBlockState(hit.getBlockPos());
            if (!s.is(Tags.Blocks.GLASS_BLOCKS) && !s.is(Tags.Blocks.GLASS_PANES)) return false; // [проверить] имена тегов
            cur = hit.getLocation().add(step);
            if (cur.distanceToSqr(from) >= len * len) return true;
        }
        return false;
    }

    /** Ствол дерева рядом с (cx, y0, cz): осколок встаёт за стволом и чуть сбоку, с видимым краем. */
    public static Vec3 findTree(Level level, LocalPlayer player, int cx, int y0, int cz, double hx, double hz,
                                float minDeg, RandomSource r, Predicate<Vec3> ok) {
        int rad = ObserverTuning.TREE_SEARCH_RADIUS;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        Vec3 best = null;
        int seen = 0;
        for (int dx = -rad; dx <= rad; dx++) {
            for (int dz = -rad; dz <= rad; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                for (int y = y0 + 6; y >= y0 - 12; y--) {
                    p.set(x, y, z);
                    if (!loaded(level, p)) break;
                    if (!level.getBlockState(p).is(BlockTags.LOGS)) continue;
                    BlockPos below = p.below();
                    BlockState bs = level.getBlockState(below);
                    if (bs.is(BlockTags.LOGS) || bs.is(BlockTags.LEAVES)
                            || bs.getCollisionShape(level, below).isEmpty()) continue;          // нужен низ ствола на земле
                    if (!level.getBlockState(p.above()).is(BlockTags.LOGS)) continue;            // ствол, а не одинокое бревно

                    double tx = x + 0.5;
                    double tz = z + 0.5;
                    double vx = tx - player.getX();
                    double vz = tz - player.getZ();
                    double l = Math.hypot(vx, vz);
                    if (l < 1.0E-4 || angleDeg(hx, hz, vx, vz) < minDeg) continue;
                    double nx = vx / l;
                    double nz = vz / l;
                    double side0 = r.nextBoolean() ? 1 : -1;
                    for (int k = 0; k < 2; k++) {
                        double side = k == 0 ? side0 : -side0;
                        double fx = tx + nx * ObserverTuning.TREE_PEEK_BEHIND - nz * side * ObserverTuning.TREE_PEEK_SIDE;
                        double fz = tz + nz * ObserverTuning.TREE_PEEK_BEHIND + nx * side * ObserverTuning.TREE_PEEK_SIDE;
                        if (!standable(level, BlockPos.containing(fx, y, fz), ObserverTuning.GHOST_HEADROOM)) continue;
                        Vec3 feet = new Vec3(fx, y, fz);
                        if (!ok.test(feet)) continue;
                        seen++;
                        if (r.nextInt(seen) == 0) best = feet;
                        break;
                    }
                    break; // в этом столбце ствол найден
                }
            }
        }
        return best;
    }

    /**
     * Стекло на уровне головы с твёрдым полом за ним (со стороны, противоположной игроку).
     * Ищет в квадрате radius вокруг center; distMin..distMax — дистанция от игрока.
     */
    public static Vec3 findWindow(Level level, LocalPlayer player, BlockPos c, int rad, float distMin, float distMax,
                                  double hx, double hz, float minDeg, RandomSource r, Predicate<Vec3> ok) {
        BlockPos.MutableBlockPos g = new BlockPos.MutableBlockPos();
        Vec3 best = null;
        int seen = 0;
        for (int dx = -rad; dx <= rad; dx++) {
            for (int dz = -rad; dz <= rad; dz++) {
                double vx = c.getX() + dx + 0.5 - player.getX();
                double vz = c.getZ() + dz + 0.5 - player.getZ();
                double dist = Math.hypot(vx, vz);
                if (dist < distMin || dist > distMax) continue;
                if (angleDeg(hx, hz, vx, vz) < minDeg) continue;
                int sx = Math.abs(vx) > Math.abs(vz) ? (vx > 0 ? 1 : -1) : 0;
                int sz = sx == 0 ? (vz > 0 ? 1 : -1) : 0;
                for (int dy = -2; dy <= 4; dy++) {
                    g.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    if (!loaded(level, g)) break;
                    BlockState gs = level.getBlockState(g);
                    if (!gs.is(Tags.Blocks.GLASS_BLOCKS) && !gs.is(Tags.Blocks.GLASS_PANES)) continue;
                    BlockPos s = new BlockPos(g.getX() + sx, g.getY() - 1, g.getZ() + sz);
                    if (!standable(level, s, ObserverTuning.GHOST_HEADROOM)) continue;
                    Vec3 feet = new Vec3(s.getX() + 0.5 - sx * ObserverTuning.WINDOW_GLASS_GAP, s.getY(),
                            s.getZ() + 0.5 - sz * ObserverTuning.WINDOW_GLASS_GAP);
                    if (!ok.test(feet)) continue;
                    seen++;
                    if (r.nextInt(seen) == 0) best = feet;
                }
            }
        }
        return best;
    }

    /** Угол (градусы) между горизонтальным направлением взгляда (hx, hz) и направлением на точку (vx, vz). */
    public static double angleDeg(double hx, double hz, double vx, double vz) {
        double l = Math.hypot(vx, vz);
        if (l < 1.0E-4) return 0;
        return Math.toDegrees(Math.acos(Mth.clamp((hx * vx + hz * vz) / l, -1.0, 1.0)));
    }

    /**
     * Можно ли встать в p: твёрдый пол без жидкости, headroom свободных блоков над полом.
     * Выше двух блоков допускается листва: голова может уйти в крону, но не в камень или потолок.
     */
    public static boolean standable(Level level, BlockPos p, int headroom) {
        if (!loaded(level, p)) return false;
        BlockPos below = p.below();
        if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                || !level.getFluidState(below).isEmpty()) return false;
        for (int i = 0; i < headroom; i++) {
            BlockPos q = p.above(i);
            if (isFree(level, q)) continue;
            if (i >= 2 && level.getBlockState(q).is(BlockTags.LEAVES)) continue;
            return false;
        }
        return true;
    }

    private static boolean isFree(Level level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                && level.getFluidState(p).isEmpty();
    }
}