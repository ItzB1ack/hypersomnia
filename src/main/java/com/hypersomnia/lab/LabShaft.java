package com.hypersomnia.lab;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * [новое] Шахта лифта между поверхностью (G) и первым подземным этажом (B1).
 *
 * <p>Кабина вместе с копируемой оболочкой занимает 5x5 (x-2..x+2, z-2..z+2). Шахта вырывает
 * такой же проём на всю высоту между потолком B1 и полом G и облицовывает его тёмной плиткой
 * снаружи. Кабина едет внутри готового проёма и не проходит сквозь породу; объёмный режим
 * {@link com.hypersomnia.server.LiftRide} остаётся только для плит пола/потолка на концах.
 *
 * <p>Шахта не трогает пол G и потолок B1: они остаются настоящими блоками и прячутся только
 * на время поездки. Так кабины на остановках выглядят как обычные комнаты.
 */
public final class LabShaft {
    private LabShaft() {}

    /** Полуширина проёма: 5x5 под оболочку кабины. */
    public static final int HALF = 2;
    /** Толщина облицовки вокруг проёма. */
    public static final int CASING = 1;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** Облицовка: та же тёмная плитка, что и отделка этажей. Лениво — класс грузится и в self-тестах без bootstrap. */
    private static BlockState casing() { return Blocks.DEEPSLATE_TILES.defaultBlockState(); }

    /** Первый воздух над потолком B1 — низ шахты. */
    public static int bottom(LabSavedData d) { return d.stopY[1] + d.stopH[1] + 1; }
    /** Последний воздух под полом G — верх шахты. */
    public static int top(LabSavedData d) { return d.stopY[0] - 2; }

    /** Оба конца построены и между ними есть просвет. */
    public static boolean needed(LabSavedData d) {
        return d.isBuilt(0) && d.isBuilt(1) && top(d) >= bottom(d);
    }

    /** Точка внутри облицованной шахты (для защиты постройки). */
    public static boolean inside(LabSavedData d, BlockPos pos) {
        if (!d.shaftBuilt || !needed(d)) return false;
        if (pos.getY() < bottom(d) || pos.getY() > top(d)) return false;
        return Math.abs(pos.getX() - d.originX) <= HALF + CASING
                && Math.abs(pos.getZ() - d.originZ) <= HALF + CASING;
    }

    /** Объём шахты: по нему проверяем, что внутри никого нет перед выемкой. */
    public static AABB box(LabSavedData d) {
        int y0 = bottom(d), y1 = top(d);
        return new AABB(d.originX - HALF - CASING, y0, d.originZ - HALF - CASING,
                d.originX + HALF + CASING + 1.0, y1 + 1.0, d.originZ + HALF + CASING + 1.0);
    }

    /**
     * Достраивает шахту, если её ещё нет. Возвращает false, если этажи не готовы или внутри
     * шахты стоит игрок (тогда попытка повторится позже).
     */
    public static boolean ensure(ServerLevel level, LabSavedData d) {
        if (d.shaftBuilt) return true;
        if (!needed(d)) {
            // Оба этажа есть, но просвета нет: шахта не нужна, помечаем как готовую.
            if (d.isBuilt(0) && d.isBuilt(1)) {
                d.shaftBuilt = true;
                d.setDirty();
            }
            return false;
        }
        if (!level.getEntitiesOfClass(ServerPlayer.class, box(d)).isEmpty()) return false;
        carve(level, d, false);
        d.shaftBuilt = true;
        d.setDirty();
        return true;
    }

    /** Стирает старую шахту при rebuild/resite: заполняет объём породой, чтобы не оставить пустоту. */
    public static void clear(ServerLevel level, LabSavedData d) {
        if (!d.shaftBuilt || !needed(d)) return;
        carve(level, d, true);
        d.shaftBuilt = false;
        d.setDirty();
    }

    /** plug=true — заложить объём облицовкой (старое место), false — вырыть проём и облицевать стенки. */
    private static void carve(ServerLevel level, LabSavedData d, boolean plug) {
        int cx = d.originX, cz = d.originZ, y0 = bottom(d), y1 = top(d);
        for (int y = y0; y <= y1; y++)
            for (int dx = -HALF - CASING; dx <= HALF + CASING; dx++)
                for (int dz = -HALF - CASING; dz <= HALF + CASING; dz++) {
                    boolean edge = Math.abs(dx) == HALF + CASING || Math.abs(dz) == HALF + CASING;
                    BlockPos p = new BlockPos(cx + dx, y, cz + dz);
                    level.setBlock(p, plug || edge ? casing() : Blocks.AIR.defaultBlockState(), FLAGS);
                }
    }
}
