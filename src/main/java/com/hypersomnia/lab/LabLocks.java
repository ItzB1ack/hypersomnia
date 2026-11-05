package com.hypersomnia.lab;

import com.hypersomnia.ModAttachments;
import com.hypersomnia.ModBlocks;
import com.hypersomnia.ObserverProgressData;
import com.hypersomnia.document.MemoryTuning;
import com.hypersomnia.document.WorldVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Замки лаборатории: проверка ответа, последствия открытия, лампы кодовых панелей, запечатанные шкафы. */
public final class LabLocks {
    private LabLocks() {}

    /** Ответ: ровно lockDigits(id) цифр, сверяется с вариантом мира (зерно Верхнего мира, одно на оба мира). */
    public static boolean check(ServerLevel level, LabSavedData d, int id, String digits) {
        if (!digits(id, digits, true)) return false;
        long seed = LabSavedData.cipherSeed(level.getServer());
        if (id == LabTuning.LOCK_INDEX) return digits.equals(indexCode(seed));
        if (id == LabTuning.LOCK_HATCH) return Integer.parseInt(digits) == LabVariants.hatchCode(seed, d.dream);
        if (id == LabTuning.LOCK_BAY) return digits.equals(LabVariants.bayCode(seed));
        if (id == LabTuning.LOCK_NURSERY) return digits.equals(LabVariants.postCode(seed));
        if (id == LabTuning.LOCK_SESSION) return digits.equals(com.hypersomnia.finale.FinaleTuning.SESSION_CODE);
        if (id == LabTuning.LOCK_STACKS) return digits.equals(LabVariants.stacksCode(seed));
        if (id == LabTuning.LOCK_BUFFER) return digits.equals(LabVariants.rackCode(seed));
        if (id == LabTuning.LOCK_WARD) return digits.equals(LabVariants.knockCode(seed));
        return false;
    }

    /** Только цифры 0-9; для проверки — ровно lockDigits(id), для черновика — не больше. */
    public static boolean digits(int id, String s, boolean full) {
        int n = LabTuning.lockDigits(id);
        if (s == null || s.length() > n || (full && s.length() != n)) return false;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) < '0' || s.charAt(i) > '9') return false;
        return true;
    }

    /** Ответ шифра 6 в мире с зерном seed: цифры строк карточки INDEX_CARD в том порядке, в каком карточка их показывает. */
    public static String indexCode(long seed) {
        int[] order = WorldVariant.order(seed, LabTuning.INDEX_CARD, LabTuning.INDEX_DIGITS.length);
        StringBuilder sb = new StringBuilder();
        for (int k : order) sb.append(LabTuning.INDEX_DIGITS[k]);
        return sb.toString();
    }

    /** Найдены ли все воспоминания (условие индекса: неполный не принимается). */
    public static boolean indexComplete(ServerPlayer player) {
        return player.getData(ModAttachments.OBSERVER).memoriesCount() >= MemoryTuning.COUNT;
    }

    /** Ящик замка LOCK_INDEX открыт игроком: у игрока со всеми воспоминаниями запись подготовлена. */
    public static void boxOpened(ServerPlayer player, int id) {
        if (id != LabTuning.LOCK_INDEX || !indexComplete(player)) return;
        ObserverProgressData data = player.getData(ModAttachments.OBSERVER);
        if (!data.recordReady()) data.setRecordReady(true);
    }

    /** Шкаф в pos — запечатанный (KIND_SEAL), и его замок ещё закрыт. */
    public static boolean sealed(ServerLevel level, BlockPos pos) {
        LabSavedData d = LabSavedData.get(level);
        Integer v = d.inter.get(pos.asLong());
        return v != null && v / 1000 == LabSavedData.KIND_SEAL && !d.isLockOpen(v % 1000);
    }

    public static void grant(ServerLevel level, LabSavedData d, int id) {
        if (d.isLockOpen(id)) return;
        d.locksOpen |= 1 << id;
        if (id == LabTuning.LOCK_HATCH) d.reached = Math.max(d.reached, 1);
        if (LabTuning.accessLock(id) && d.hasFlag(LabSavedData.FLAG_FEED)) d.reached = Math.max(d.reached, accessTop(d));
        LabLift.refreshDoors(level, d);
        refreshKeypads(level, d);
        openGate(level, d, id);
        BlockPos p = d.lockPos(id);
        if (p != null) level.playSound(null, p, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0f, 0.55f);
        d.setDirty();
    }

    /**
     * Нижняя остановка, до которой лифт ходит при включённом MAIN FEED: FEED_REACHED и ещё по одной за каждый
     * открытый подряд замок ACCESS_LOCKS (панель B2 открывает B3, B3 — B4, B4 — B5).
     */
    public static int accessTop(LabSavedData d) {
        int top = LabTuning.FEED_REACHED;
        for (int lock : LabTuning.ACCESS_LOCKS) {
            if (!d.isLockOpen(lock)) break;
            top++;
        }
        return Math.min(top, LabTuning.STOPS - 1);
    }

    /**
     * Прут решётки-ворот замка id (LabBuilder, строка gate): железные прутья, сцепленные вдоль стены
     * (alongX — стена идёт вдоль X, прутья цепляются на восток и запад). [проверить: IronBarsBlock.EAST/WEST/NORTH/SOUTH]
     */
    public static BlockState gateBar(boolean alongX) {
        BlockState s = Blocks.IRON_BARS.defaultBlockState();
        return alongX ? s.setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true)
                : s.setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true);
    }

    /** Убирает прутья решёток замка id (только сами прутья: если на их месте уже другое, не трогаем) и забывает их. */
    public static void openGate(ServerLevel level, LabSavedData d, int id) {
        List<Long> bars = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : d.inter.entrySet())
            if (e.getValue() == LabSavedData.KIND_GATE * 1000 + id) bars.add(e.getKey());
        for (long key : bars) {
            BlockPos p = BlockPos.of(key);
            if (level.getBlockState(p).is(Blocks.IRON_BARS)) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            d.inter.remove(key);
        }
        if (!bars.isEmpty()) d.setDirty();
    }

    /** Лампы кодовых панелей: OPEN = замок открыт (светлая), иначе красная. Чужие блоки на месте панели не трогаем. */
    public static void refreshKeypads(ServerLevel level, LabSavedData d) {
        for (Map.Entry<Long, Integer> e : d.inter.entrySet()) {
            int v = e.getValue();
            if (v / 1000 != LabSavedData.KIND_LOCK) continue;
            BlockPos p = BlockPos.of(e.getKey());
            BlockState st = level.getBlockState(p);
            if (!st.is(ModBlocks.KEYPAD.get())) continue;
            boolean open = d.isLockOpen(v % 1000);
            if (st.getValue(KeypadBlock.OPEN) != open) level.setBlock(p, st.setValue(KeypadBlock.OPEN, open), 2);
        }
    }
}
