package com.hypersomnia.lab;

import com.hypersomnia.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.Locale;
import java.util.Map;

/**
 * Серверные стойки B3 (KIND_RACK) и ряды хранилища B2 (KIND_STACK): их вид зависит от зерна шифров мира, поэтому
 * ставится здесь — после стройки (LabPlacement) и после /hypersomnia_lab index reroll, без пересборки этажей.
 * Чужие блоки на месте стойки или полки не трогаем.
 */
public final class LabFixtures {
    private LabFixtures() {}

    public static void refresh(ServerLevel level, LabSavedData d) {
        long seed = LabSavedData.cipherSeed(level.getServer());
        int[] hold = LabVariants.rackHold(seed);
        int[] gaps = LabVariants.stacksGaps(seed);
        for (Map.Entry<Long, Integer> e : d.inter.entrySet()) {
            int kind = e.getValue() / 1000, id = e.getValue() % 1000;
            BlockPos p = BlockPos.of(e.getKey());
            if (kind == LabSavedData.KIND_RACK) rack(level, d, p, id, seed, hold);
            else if (kind == LabSavedData.KIND_STACK) stack(level, d, p, id, gaps);
        }
    }

    /**
     * Реальный мир: удержанные стойки — HOLD, пока буферы не ушли через ANT-1 (замок LOCK_BUFFER), остальные — IDLE;
     * на лампах адрес буфера (LabVariants.rackValue). Мир снов: всё погашено, кроме первой удержанной стойки — она «пишет»
     * (HOLD, лампы адреса тёмные).
     */
    private static void rack(ServerLevel level, LabSavedData d, BlockPos p, int id, long seed, int[] hold) {
        BlockState st = level.getBlockState(p);
        if (!st.is(ModBlocks.SERVER_RACK.get()) || st.getValue(ServerRackBlock.HALF) != DoubleBlockHalf.UPPER) return;
        BlockState want;
        if (d.dream) {
            want = ServerRackBlock.lamps(st, id == hold[0] ? ServerRackBlock.Status.HOLD : ServerRackBlock.Status.OFF, 0);
        } else {
            boolean held = false;
            for (int n : hold) held |= n == id;
            held &= !d.isLockOpen(LabTuning.LOCK_BUFFER);
            want = ServerRackBlock.lamps(st, held ? ServerRackBlock.Status.HOLD : ServerRackBlock.Status.IDLE,
                    LabVariants.rackValue(seed, id));
        }
        if (want != st) level.setBlock(p, want, 2);
    }

    /** Пустое место ряда — резная полка без книг лицом к проходу (только реальный мир); остальные места — книжные полки. */
    private static void stack(ServerLevel level, LabSavedData d, BlockPos p, int id, int[] gaps) {
        int row = id / 100, place = id / 10 % 10;
        Direction side = Direction.from2DDataValue(id % 10);
        boolean gap = !d.dream && row >= 1 && row <= gaps.length && gaps[row - 1] == place - 1;
        BlockState st = level.getBlockState(p);
        if (!st.is(Blocks.BOOKSHELF) && !st.is(Blocks.CHISELED_BOOKSHELF)) return;
        BlockState want = gap
                ? Blocks.CHISELED_BOOKSHELF.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, side)
                : Blocks.BOOKSHELF.defaultBlockState();
        if (!st.equals(want)) level.setBlock(p, want, 2);
    }

    /** Строка для полосы над хотбаром: «RACK 07 · HOLD». Состояние берётся с верхней половины. */
    public static Component rackInfo(ServerLevel level, BlockPos pos, int id) {
        BlockState st = level.getBlockState(pos);
        if (st.is(ModBlocks.SERVER_RACK.get()) && st.getValue(ServerRackBlock.HALF) == DoubleBlockHalf.LOWER)
            st = level.getBlockState(pos.above());
        ServerRackBlock.Status s = st.is(ModBlocks.SERVER_RACK.get()) ? st.getValue(ServerRackBlock.STATUS) : ServerRackBlock.Status.OFF;
        return Component.translatable("hypersomnia.lab.rack", String.format(Locale.ROOT, "%02d", id),
                Component.translatable("hypersomnia.lab.rack.status." + s.getSerializedName()));
    }

    /** «ROW 2 · PLACE 5». */
    public static Component stackInfo(int id) {
        return Component.translatable("hypersomnia.lab.stack", id / 100, id / 10 % 10);
    }
}
