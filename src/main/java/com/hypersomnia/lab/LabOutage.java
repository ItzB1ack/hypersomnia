package com.hypersomnia.lab;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModBlocks;
import com.hypersomnia.chase.ChaseHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * «Обесточено»: погоня в реальной лаборатории (после MAIN FEED, этажи OUTAGE_MIN_STOP..OUTAGE_MAX_STOP). Её просит обычный
 * режиссёр погони; ChaseHandler.start, видя игрока в лаборатории, зовёт begin. Потолочные трубки этажа мигают и гаснут
 * (END_ROD -> UnlitTubeBlock), щит этажа у лифта выбит (лампа TRIPPED чуть светится), лифт стоит («NO POWER»).
 * Свет возвращается (мигнув), когда игрок включит щит этажа (ПКМ), через OUTAGE_AFTER_CHASE после конца погони
 * или через OUTAGE_MAX_TICKS от начала — тупика нет. Вернулся свет — погоня кончается, фигура исчезает.
 * Одно «обесточено» на сервер, только верхний мир. Этаж в темноте сохраняется (LabSavedData.outageStop): сервер
 * остановили — свет включается до сохранения, упал — при следующем запуске. Щит этажа в старом мире ставится при первом
 * «обесточено» на этом этаже (LabBuilder.board).
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class LabOutage {
    private LabOutage() {}

    private static final class Outage {
        final int stop;
        final long start;
        final List<BlockPos> tubes;
        /** Чья погоня: у его головы звучат лампы и обрыв. */
        final UUID by;
        boolean lit = true;
        boolean dark;
        long restoreAt = Long.MAX_VALUE;
        long restoreStart = -1;

        Outage(int stop, long start, List<BlockPos> tubes, UUID by) {
            this.stop = stop;
            this.start = start;
            this.tubes = tubes;
            this.by = by;
        }
    }

    private static Outage cur;

    /** Этаж под игроком, где сейчас можно начать «обесточено», или -1 (тогда погони в лаборатории нет). */
    public static int stopFor(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (cur != null || !level.dimension().equals(Level.OVERWORLD)) return -1;
        LabSavedData d = LabSavedData.get(level);
        if (!d.built || d.dream || !d.hasFlag(LabSavedData.FLAG_FEED)) return -1;
        int stop = LabProtection.stopAt(d, player.blockPosition());
        if (stop < LabTuning.OUTAGE_MIN_STOP || stop > LabTuning.OUTAGE_MAX_STOP) return -1;
        if (LabLift.stopOf(player, d) >= 0 || LabLift.busy(level)) return -1;
        return tubes(level, d, stop).isEmpty() ? -1 : stop;
    }

    /** Сколько тиков свет мигает до темноты. */
    public static int flickerTicks() {
        return sum(LabTuning.OUTAGE_FLICKER);
    }

    /** Начать на этаже stop (by — чья погоня, может быть null: отладка). false — уже идёт или гасить нечего. */
    public static boolean begin(ServerLevel level, int stop, ServerPlayer by) {
        LabSavedData d = LabSavedData.get(level);
        if (cur != null || !level.dimension().equals(Level.OVERWORLD) || !d.built || d.dream || !d.isBuilt(stop)) return false;
        List<BlockPos> tubes = tubes(level, d, stop);
        if (tubes.isEmpty()) return false;
        LabBuilder.board(level, d, stop);
        cur = new Outage(stop, level.getGameTime(), tubes, by == null ? null : by.getUUID());
        d.outageStop = stop;
        d.setDirty();
        sound(level, d, SoundEvents.REDSTONE_TORCH_BURNOUT, LabTuning.OUTAGE_CLICK_VOLUME, LabTuning.OUTAGE_CLICK_PITCH);
        return true;
    }

    /** Идёт «обесточено» (лифт стоит). */
    public static boolean active(ServerLevel level) {
        return cur != null && level.dimension().equals(Level.OVERWORLD);
    }

    /** Щит этажа stop выбит: темно и свет ещё не возвращается. */
    public static boolean tripped(ServerLevel level, int stop) {
        return active(level) && cur.stop == stop && cur.dark && cur.restoreStart < 0;
    }

    /** Погоня «обесточено» кончилась (любым исходом): свет вернётся через OUTAGE_AFTER_CHASE. */
    public static void chaseEnded(ServerLevel level) {
        if (cur == null || cur.restoreStart >= 0) return;
        cur.restoreAt = Math.min(cur.restoreAt, level.getGameTime() + LabTuning.OUTAGE_AFTER_CHASE);
    }

    /** ПКМ по щиту этажа: выбит — свет возвращается; иначе — состояние щита. */
    public static void board(ServerPlayer player, ServerLevel level, LabSavedData d, int stop, BlockPos pos) {
        Component label = Component.literal(LabTuning.stopLabel(stop));
        if (tripped(level, stop)) {
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8f, 0.5f);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6f, 0.6f);
            restore(level, d);
            player.displayClientMessage(Component.translatable("hypersomnia.lab.outage.reset", label), true);
            return;
        }
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.4f, 1.3f);
        player.displayClientMessage(Component.translatable(d.hasFlag(LabSavedData.FLAG_FEED)
                ? "hypersomnia.lab.board.fed" : "hypersomnia.lab.board.nofeed", label), true);
    }

    /** Свет возвращается (мигнув): погоня «обесточено» кончается, фигура исчезает. Повторный вызов ничего не делает. */
    public static void restore(ServerLevel level, LabSavedData d) {
        if (cur == null || cur.restoreStart >= 0) return;
        cur.restoreStart = level.getGameTime();
        sound(level, d, SoundEvents.BEACON_ACTIVATE, LabTuning.OUTAGE_UP_VOLUME, LabTuning.OUTAGE_UP_PITCH);
        LabBreakers.refresh(level, d);
        ChaseHandler.outageOver(level.getServer());
    }

    /** Строка для отладки. */
    public static String status(ServerLevel level) {
        if (cur == null) return "Outage: none";
        long now = level.getGameTime();
        return "Outage: " + LabTuning.stopLabel(cur.stop) + ", " + cur.tubes.size() + " tubes, "
                + (cur.restoreStart >= 0 ? "restoring" : cur.dark ? "dark" : "flicker") + ", t=" + (now - cur.start)
                + (cur.restoreAt != Long.MAX_VALUE ? ", restore in " + Math.max(0, cur.restoreAt - now) : "");
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (cur == null) return;
        ServerLevel level = event.getServer().overworld();
        LabSavedData d = LabSavedData.get(level);
        if (d.outageStop != cur.stop || !d.isBuilt(cur.stop)) { // этаж пересобрали или стёрли: трубки уже новые
            cur = null;
            return;
        }
        long now = level.getGameTime();
        if (cur.restoreStart >= 0) {
            long r = now - cur.restoreStart;
            if (r >= sum(LabTuning.OUTAGE_RESTORE)) finish(level, d);
            else setLit(level, d, lit(LabTuning.OUTAGE_RESTORE, r, true));
            return;
        }
        long t = now - cur.start;
        if (!cur.dark) {
            if (t >= flickerTicks()) goDark(level, d);
            else setLit(level, d, lit(LabTuning.OUTAGE_FLICKER, t, false));
            return;
        }
        if (now >= cur.restoreAt || t >= LabTuning.OUTAGE_MAX_TICKS) restore(level, d);
    }

    private static void goDark(ServerLevel level, LabSavedData d) {
        lights(level, cur.tubes, false);
        cur.lit = false;
        cur.dark = true;
        LabBreakers.refresh(level, d);
        sound(level, d, SoundEvents.BEACON_DEACTIVATE, LabTuning.OUTAGE_DOWN_VOLUME, LabTuning.OUTAGE_DOWN_PITCH);
        BlockPos board = d.find(LabSavedData.KIND_BREAKER * 1000 + LabBreakers.BOARD + cur.stop);
        if (board != null) level.playSound(null, board, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 0.5f);
        Component msg = Component.translatable("hypersomnia.lab.outage.tripped", Component.literal(LabTuning.stopLabel(cur.stop)));
        for (ServerPlayer p : level.players())
            if (LabProtection.stopAt(d, p.blockPosition()) == cur.stop) p.displayClientMessage(msg, true);
    }

    private static void finish(ServerLevel level, LabSavedData d) {
        lights(level, cur.tubes, true);
        cur = null;
        d.outageStop = -1;
        d.setDirty();
        LabBreakers.refresh(level, d);
    }

    /** Мигание: трубки этажа разом гаснут или зажигаются, щелчок над головой. */
    private static void setLit(ServerLevel level, LabSavedData d, boolean on) {
        if (cur.lit == on) return;
        lights(level, cur.tubes, on);
        cur.lit = on;
        sound(level, d, on ? SoundEvents.COPPER_BULB_TURN_ON : SoundEvents.COPPER_BULB_TURN_OFF,
                LabTuning.OUTAGE_CLICK_VOLUME, LabTuning.OUTAGE_CLICK_PITCH + level.random.nextFloat() * 0.2f);
    }

    /** Состояние на тике t узора длительностей: отрезки попеременно, первый — firstOn; после узора — как последний отрезок. */
    private static boolean lit(int[] pattern, long t, boolean firstOn) {
        long acc = 0;
        for (int i = 0; i < pattern.length; i++) {
            acc += pattern[i];
            if (t < acc) return firstOn == (i % 2 == 0);
        }
        return firstOn == ((pattern.length - 1) % 2 == 0);
    }

    private static int sum(int[] a) {
        int s = 0;
        for (int v : a) s += v;
        return s;
    }

    /** Потолочные трубки этажа: стержни и погасшие трубки в слое под потолком (y = пол + высота - 1). */
    static List<BlockPos> tubes(ServerLevel level, LabSavedData d, int stop) {
        List<BlockPos> out = new ArrayList<>();
        if (!d.isBuilt(stop)) return out;
        int y = d.stopY[stop] + d.stopH[stop] - 1;
        for (int x = d.bbox[stop * 4]; x <= d.bbox[stop * 4 + 2]; x++)
            for (int z = d.bbox[stop * 4 + 1]; z <= d.bbox[stop * 4 + 3]; z++) {
                BlockPos p = new BlockPos(x, y, z);
                BlockState s = level.getBlockState(p);
                if (s.is(Blocks.END_ROD) || s.is(ModBlocks.UNLIT_TUBE.get())) out.add(p);
            }
        return out;
    }

    /** Трубки -> стержни (on) или погасшие трубки; направление сохраняется, чужие блоки на их месте не трогаются. */
    private static void lights(ServerLevel level, List<BlockPos> tubes, boolean on) {
        for (BlockPos p : tubes) {
            BlockState s = level.getBlockState(p);
            if (on && s.is(ModBlocks.UNLIT_TUBE.get()))
                level.setBlock(p, Blocks.END_ROD.defaultBlockState().setValue(DirectionalBlock.FACING, s.getValue(DirectionalBlock.FACING)), 2);
            else if (!on && s.is(Blocks.END_ROD))
                level.setBlock(p, ModBlocks.UNLIT_TUBE.get().defaultBlockState().setValue(DirectionalBlock.FACING, s.getValue(DirectionalBlock.FACING)), 2);
        }
    }

    /** Звук под потолком над игроком погони (он на этом этаже), иначе у щита этажа, иначе над кабиной лифта. */
    private static void sound(ServerLevel level, LabSavedData d, SoundEvent ev, float volume, float pitch) {
        double y = d.stopY[cur.stop] + d.stopH[cur.stop] - 1;
        ServerPlayer p = cur.by == null ? null : level.getServer().getPlayerList().getPlayer(cur.by);
        if (p != null && p.level() == level && LabProtection.stopAt(d, p.blockPosition()) == cur.stop) {
            level.playSound(null, p.getX(), y, p.getZ(), ev, SoundSource.BLOCKS, volume, pitch);
            return;
        }
        BlockPos b = d.find(LabSavedData.KIND_BREAKER * 1000 + LabBreakers.BOARD + cur.stop);
        if (b == null) b = new BlockPos(d.originX, (int) y, d.originZ);
        level.playSound(null, b, ev, SoundSource.BLOCKS, volume, pitch);
    }

    /** Остановка сервера: свет включается до сохранения мира. */
    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        if (cur == null) return;
        ServerLevel level = event.getServer().overworld();
        LabSavedData d = LabSavedData.get(level);
        if (d.outageStop == cur.stop) finish(level, d);
        cur = null;
    }

    /** Сервер упал в темноте: при запуске трубки этажа снова горят. */
    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        cur = null;
        ServerLevel level = event.getServer().overworld();
        if (level == null) return;
        LabSavedData d = LabSavedData.get(level);
        if (d.outageStop < 0) return;
        if (d.isBuilt(d.outageStop)) lights(level, tubes(level, d, d.outageStop), true);
        d.outageStop = -1;
        d.setDirty();
        LabBreakers.refresh(level, d);
    }
}
