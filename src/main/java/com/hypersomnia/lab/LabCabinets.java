package com.hypersomnia.lab;

import com.hypersomnia.ModBlocks;
import com.hypersomnia.ModItems;
import com.hypersomnia.document.DocumentDefs;
import com.hypersomnia.document.DocumentItem;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

/**
 * Шкафы лаборатории при стройке: в основном бумага, документ только в шкафу, который задан в чертеже
 * строкой cabinet. Реальный мир — служебные документы, воспоминания 1, 2, 3, 7, пропуск, пустой лист из палаты Nora;
 * мир снов — записки, воспоминания 4, 5, 6, правка Voss, рисунок 2 (каждая находка ровно в одном шкафу, проверяет самотест finds).
 * Рисунки 0, 1, 3 в шкафах не лежат: их оставляет Фрагмент у дома (ObserverNotesHandler), рисунок 4 — в детской (FinaleHandler).
 * Адреналиновые шприцы задаются директивой чертежа syringe &lt;символ&gt; = &lt;сколько&gt;.
 * Раскладка зависит только от координат и мира: при каждой перестройке одна и та же.
 */
public final class LabCabinets {
    private LabCabinets() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Соль для ленивого досевания шприцев в старых мирах (не совпадает с солью place). */
    private static final long SEED_SALT = 0x5DEECE66DL;

    /**
     * Ставит шкаф (FACING = facing) и заполняет его заново. doc — id страницы DocumentDefs или null.
     * paper = false — без бумаги, только документ (запечатанный ящик: строка seal чертежа).
     * syringes — сколько адреналиновых шприцев положить (директива syringe чертежа).
     */
    public static void place(ServerLevel level, BlockPos pos, Direction facing, String doc, boolean dream, boolean paper,
                             int syringes) {
        level.setBlock(pos, ModBlocks.CABINET.get().defaultBlockState().setValue(CabinetBlock.FACING, facing), 2);
        if (!(level.getBlockEntity(pos) instanceof CabinetBlockEntity cab)) return;
        cab.clearContent(); // стройка поверх старой: тот же блок, старая блок-сущность осталась
        RandomSource r = RandomSource.create(pos.asLong() * 0x9E3779B97F4A7C15L + (dream ? 47 : 0));
        boolean empty = doc == null && LabTuning.CABINET_EMPTY_ONE_IN > 0 && r.nextInt(LabTuning.CABINET_EMPTY_ONE_IN) == 0;
        if (!empty && paper) {
            int stacks = 1 + r.nextInt(LabTuning.CABINET_PAPER_STACKS);
            for (int i = 0; i < stacks; i++) put(cab, r, new ItemStack(Items.PAPER, 1 + r.nextInt(LabTuning.CABINET_PAPER_MAX)));
        }
        if (doc != null) {
            if (DocumentDefs.get(doc) == null) LOGGER.warn("Lab cabinet at {}: unknown document '{}'", pos.toShortString(), doc);
            else put(cab, r, DocumentItem.make(doc));
        }
        for (int i = 0; i < syringes; i++) put(cab, r, new ItemStack(ModItems.ADRENALINE_SYRINGE.get()));
        cab.setSyringeSeeded(true);
        cab.setChanged();
    }

    /**
     * Старые миры: шкафы уже построены без директивы syringe. Досеваем шприцы один раз при открытии,
     * чтобы не требовать /hypersomnia_lab rebuild. Флаг хранится в самом шкафу.
     */
    public static void seedOnOpen(ServerLevel level, BlockPos pos, CabinetBlockEntity cab) {
        if (cab.isSyringeSeeded()) return;
        LabSavedData d = LabSavedData.get(level);
        if (!d.built) return; // ещё не построено: шкаф может быть игрокским
        cab.setSyringeSeeded(true);
        int stop = -1;
        for (int s = 0; s < LabTuning.STOPS; s++) {
            if (!d.isBuilt(s) || pos.getY() != d.stopY[s]) continue;
            int x1 = d.bbox[s * 4], z1 = d.bbox[s * 4 + 1], x2 = d.bbox[s * 4 + 2], z2 = d.bbox[s * 4 + 3];
            if (pos.getX() >= x1 && pos.getX() <= x2 && pos.getZ() >= z1 && pos.getZ() <= z2) {
                stop = s;
                break;
            }
        }
        if (stop < 0) { // не шкаф лаборатории
            cab.setChanged();
            return;
        }
        LabBlueprint bp = LabBlueprint.get(LabTuning.blueprintName(d.dream, stop));
        int x = pos.getX() - d.originX + bp.ax;
        int z = pos.getZ() - d.originZ + bp.az;
        int count = bp.syringes.getOrDefault(bp.at(x, z), 0);
        if (count > 0) {
            RandomSource r = RandomSource.create(pos.asLong() * 0x9E3779B97F4A7C15L + SEED_SALT);
            for (int i = 0; i < count; i++) put(cab, r, new ItemStack(ModItems.ADRENALINE_SYRINGE.get()));
        }
        cab.setChanged();
    }

    /** В случайную пустую ячейку (ящик тоже случайный). */
    private static void put(CabinetBlockEntity cab, RandomSource r, ItemStack stack) {
        int size = cab.getContainerSize();
        for (int t = 0; t < size * 2; t++) {
            int s = r.nextInt(size);
            if (cab.getItem(s).isEmpty()) {
                cab.setItem(s, stack);
                return;
            }
        }
        for (int s = 0; s < size; s++) {
            if (cab.getItem(s).isEmpty()) {
                cab.setItem(s, stack);
                return;
            }
        }
    }
}
