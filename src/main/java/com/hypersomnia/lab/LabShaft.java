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
                seal(level, d);
                d.shaftBuilt = true;
                d.setDirty();
            }
            return false;
        }
        if (!level.getEntitiesOfClass(ServerPlayer.class, box(d)).isEmpty()) return false;
        carve(level, d, false);
        seal(level, d);
        d.shaftBuilt = true;
        d.setDirty();
        return true;
    }

    /**
     * [новое] Герметизация кабин подземных этажей: природные бока 5x5-оболочки (север, восток, запад и юг ниже пола)
     * и слой под полом заполняются облицовкой, а вода внутри кабины осушается. Иначе во время рейса LiftRide прячет
     * настоящие пол/стены кабины, и вода из соседнего водоносного кармана заливает низ кабины (замечено на B1).
     * Юг выше пола не трогаем: там проход к лифтовой двери.
     */
    public static void seal(ServerLevel level, LabSavedData d) {
        int cx = d.originX, cz = d.originZ;
        for (int stop = 1; stop < LabTuning.STOPS; stop++) {
            if (!d.isBuilt(stop)) continue;
            int fy = d.stopY[stop], h = d.stopH[stop], roof = fy + h;
            int low = fy - 2;
            // До крыши следующего построенного этажа: кабина проходит и этот промежуток.
            for (int n = stop + 1; n < LabTuning.STOPS; n++)
                if (d.isBuilt(n)) {
                    low = d.stopY[n] + d.stopH[n];
                    break;
                }
            for (int y = low; y <= roof; y++) {
                boolean under = y < fy - 1; // ниже плиты пола юг тоже открыт породе (выше — стена здания и проход)
                int zMax = under ? cz + HALF : cz + HALF - 1;
                for (int z = cz - HALF; z <= zMax; z++) {
                    fill(level, cx - HALF - CASING, y, z);
                    fill(level, cx + HALF + CASING, y, z);
                }
                for (int x = cx - HALF; x <= cx + HALF; x++) {
                    fill(level, x, y, cz - HALF - CASING);
                    if (under) fill(level, x, y, cz + HALF + CASING);
                }
            }
            // Слой прямо под полом: при скрытом полу вода не поднимется внутрь.
            for (int x = cx - HALF; x <= cx + HALF; x++)
                for (int z = cz - HALF; z <= cz + HALF; z++)
                    fill(level, x, fy - 2, z);
            // Осушить кабину, если вода уже успела затечь.
            for (int x = cx - HALF; x <= cx + HALF; x++)
                for (int z = cz - HALF; z <= cz + HALF; z++)
                    for (int y = fy - 1; y <= roof; y++) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (!level.getFluidState(p).isEmpty())
                            level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
                    }
        }
    }

    /** Заполнить клетку облицовкой: и породу, и воду, и пустоты — чтобы вода не нашла путь к скрытой оболочке. */
    private static void fill(ServerLevel level, int x, int y, int z) {
        level.setBlock(new BlockPos(x, y, z), casing(), FLAGS);
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
