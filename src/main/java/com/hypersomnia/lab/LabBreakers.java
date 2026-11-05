package com.hypersomnia.lab;

import com.hypersomnia.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * Щит MAIN FEED (B1, Power Room): один настенный блок BreakerPanelBlock, рубильники 1..FEEDERS и главный (MAIN = 0)
 * переключаются в экране щита (LabPanelScreen -> LabHandler.handlePanelAction).
 * Источник истины — LabSavedData (breakerSeq, breakerTripped, FLAG_FEED); лампа блока лишь отражает его (refresh).
 * Правило: замкнуть рубильники в порядке мира (LabVariants.feedOrder, ревизия C бланка SW-B1-01), затем главный. Иначе главный выбивает,
 * все рубильники размыкаются, можно начать заново. Включённое питание не выключается.
 * <p>
 * Щиты этажей (id BOARD + этаж, клетка '&' чертежа у лифта): питаются от MAIN FEED, экрана нет. Во время «обесточено»
 * (LabOutage) щит своего этажа выбит (TRIPPED); ПКМ по нему возвращает свет (LabHandler -> LabOutage.board).
 */
public final class LabBreakers {
    private LabBreakers() {}

    public static final int MAIN = 0;
    /** Щит этажа n: id BOARD + n (11..15). */
    public static final int BOARD = 10;

    /**
     * Ставит щит (при постройке этажа). id >= 0 — щит лаборатории (KIND_BREAKER: MAIN FEED = 0, щиты этажей BOARD + n);
     * id < 0 — декоративный щит без связи (ПКМ пишет NOT CONNECTED). Лампу выставит refresh().
     */
    public static void place(ServerLevel level, BlockPos pos, Direction facing, int id, LabSavedData d) {
        level.setBlock(pos, ModBlocks.BREAKER_PANEL.get().defaultBlockState().setValue(BreakerPanelBlock.FACING, facing), 2);
        if (id >= 0) d.inter.put(pos.asLong(), LabSavedData.KIND_BREAKER * 1000 + id);
    }

    /** Рубильник id (MAIN или 1..FEEDERS) с экрана щита в pos: позицию и расстояние уже проверил LabHandler. */
    public static void use(ServerPlayer player, ServerLevel level, LabSavedData d, int id, BlockPos pos) {
        if (d.hasFlag(LabSavedData.FLAG_FEED)) {
            player.displayClientMessage(Component.translatable("hypersomnia.lab.feed.locked"), true);
            return;
        }
        if (id == MAIN) {
            if (d.breakerSeq == LabVariants.feedCode(LabSavedData.cipherSeed(level.getServer()))) feedOn(level, d);
            else trip(level, d, pos, player);
            return;
        }
        if (id < 1 || id > LabTuning.FEEDERS) return;
        boolean close = !isClosed(d.breakerSeq, id);
        d.breakerSeq = close ? d.breakerSeq * 10 + id : without(d.breakerSeq, id);
        d.breakerTripped = false;
        d.setDirty();
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, close ? 0.6f : 0.5f);
        refresh(level, d);
    }

    /** Питание включено: флаг, лифт до FEED_REACHED и этажей, уже открытых панелями LIFT ACCESS, лампа ON. Повторный вызов ничего не делает. */
    public static void feedOn(ServerLevel level, LabSavedData d) {
        if (d.hasFlag(LabSavedData.FLAG_FEED)) return;
        d.flags |= 1 << LabSavedData.FLAG_FEED;
        d.breakerTripped = false;
        d.reached = Math.max(d.reached, LabLocks.accessTop(d));
        d.setDirty();
        refresh(level, d);
        BlockPos p = d.panelPos();
        if (p != null) {
            level.playSound(null, p, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8f, 0.5f);
            level.playSound(null, p, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.7f, 0.6f);
        }
    }

    /** Отладка: питание выключено, рубильники разомкнуты, reached не выше аварийного контура (B1). */
    public static void reset(ServerLevel level, LabSavedData d) {
        d.flags &= ~(1 << LabSavedData.FLAG_FEED);
        d.breakerSeq = 0;
        d.breakerTripped = false;
        d.reached = Math.min(d.reached, LabTuning.EMERGENCY_REACHED);
        d.setDirty();
        refresh(level, d);
    }

    /** Главный рубильник при неверном порядке: всё размыкается, лампа TRIPPED, улики не трогаются, попытки не ограничены. */
    private static void trip(ServerLevel level, LabSavedData d, BlockPos pos, ServerPlayer player) {
        d.breakerSeq = 0;
        d.breakerTripped = true;
        d.setDirty();
        refresh(level, d);
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 0.5f);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3f, 1.6f);
        level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 5, 0.12, 0.08, 0.12, 0.005);
        player.displayClientMessage(Component.translatable("hypersomnia.lab.feed.tripped"), true);
    }

    /** Лампы щитов по данным: MAIN FEED — lamp(d), щиты этажей — boardLamp. */
    public static void refresh(ServerLevel level, LabSavedData d) {
        for (Map.Entry<Long, Integer> e : d.inter.entrySet()) {
            if (e.getValue() / 1000 != LabSavedData.KIND_BREAKER) continue;
            int id = e.getValue() % 1000;
            BreakerPanelBlock.Lamp lamp = id > BOARD ? boardLamp(level, d, id - BOARD) : lamp(d);
            BlockPos p = BlockPos.of(e.getKey());
            BlockState st = level.getBlockState(p);
            if (st.is(ModBlocks.BREAKER_PANEL.get()) && st.getValue(BreakerPanelBlock.LAMP) != lamp)
                level.setBlock(p, st.setValue(BreakerPanelBlock.LAMP, lamp), 3);
        }
    }

    /** Щит MAIN FEED: питание — ON, выбило — TRIPPED, иначе OFF. */
    public static BreakerPanelBlock.Lamp lamp(LabSavedData d) {
        if (d.hasFlag(LabSavedData.FLAG_FEED)) return BreakerPanelBlock.Lamp.ON;
        return d.breakerTripped ? BreakerPanelBlock.Lamp.TRIPPED : BreakerPanelBlock.Lamp.OFF;
    }

    /** Щит этажа: на этаже темно (LabOutage) — TRIPPED, есть MAIN FEED — ON, иначе OFF. */
    public static BreakerPanelBlock.Lamp boardLamp(ServerLevel level, LabSavedData d, int stop) {
        if (LabOutage.tripped(level, stop)) return BreakerPanelBlock.Lamp.TRIPPED;
        return d.hasFlag(LabSavedData.FLAG_FEED) ? BreakerPanelBlock.Lamp.ON : BreakerPanelBlock.Lamp.OFF;
    }

    public static boolean isClosed(int seq, int id) {
        for (int s = seq; s > 0; s /= 10) if (s % 10 == id) return true;
        return false;
    }

    /** seq без цифры id (порядок остальных сохраняется). */
    static int without(int seq, int id) {
        int out = 0, mul = 1;
        for (int s = seq; s > 0; s /= 10) {
            int dgt = s % 10;
            if (dgt == id) continue;
            out += dgt * mul;
            mul *= 10;
        }
        return out;
    }

    public static int count(int seq) {
        int n = 0;
        for (int s = seq; s > 0; s /= 10) n++;
        return n;
    }
}
