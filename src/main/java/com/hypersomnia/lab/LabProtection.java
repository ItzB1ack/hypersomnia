package com.hypersomnia.lab;

import com.hypersomnia.Hypersomnia;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Постройку лаборатории нельзя разобрать: стены, пол, потолок и всё внутри этажа (bbox этажа + PROTECT_MARGIN,
 * от пола до крыши; у павильона ещё PROTECT_ROOF над крышей) не ломаются и не взрываются. Иначе любой шифр
 * обходится киркой через стену. Творческий режим не ограничен (отладка, правка чертежей). Ставить блоки можно.
 * Сервер не начинает копать блок лаборатории (ЛКМ отменяется); клиент границ не знает и может «доломать» блок у себя —
 * сервер вернёт его подтверждением действия. Раньше отменялась скорость копания (BreakSpeed) только на сервере: клиент
 * доламывал блок, сервер вставал в отложенную поломку, которая не кончалась, и раз в секунду писал «Не поддаётся» без конца.
 * [проверить: PlayerInteractEvent.LeftClickBlock, BlockEvent.BreakEvent, ExplosionEvent.Detonate в NeoForge 21.1.235]
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class LabProtection {
    private LabProtection() {}

    /** Когда игрок последний раз видел «Не поддаётся» (игровое время): не чаще LabTuning.PROTECT_MESSAGE_TICKS. */
    private static final Map<Player, Long> TOLD = new WeakHashMap<>();

    /** ЛКМ по блоку лаборатории: сервер не начинает копать — ни трещин у других, ни отложенной поломки. */
    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        Player p = event.getEntity();
        if (!LabTuning.PROTECT || p.isCreative() || !(p.level() instanceof ServerLevel level)) return;
        if (!inside(level, event.getPos())) return;
        event.setCanceled(true);
        tell(p, level);
    }

    /** Страховка: если поломка всё же дошла до сервера, блок остаётся (клиенту уходит его настоящее состояние). */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        Player p = event.getPlayer();
        if (!LabTuning.PROTECT || p.isCreative() || !(event.getLevel() instanceof ServerLevel level)) return;
        if (!inside(level, event.getPos())) return;
        event.setCanceled(true);
        tell(p, level);
    }

    private static void tell(Player p, ServerLevel level) {
        long now = level.getGameTime();
        Long last = TOLD.get(p);
        if (last != null && now - last < LabTuning.PROTECT_MESSAGE_TICKS && now >= last) return;
        TOLD.put(p, now);
        p.displayClientMessage(Component.translatable("hypersomnia.lab.protected"), true);
    }

    /** Взрыв рядом с лабораторией (крипер, TNT) не трогает её блоки; остальное рушится как обычно. */
    @SubscribeEvent
    public static void onExplode(ExplosionEvent.Detonate event) {
        if (!LabTuning.PROTECT || !(event.getLevel() instanceof ServerLevel level)) return;
        LabSavedData d = LabSavedData.get(level);
        if (!d.built || d.builtMask == 0) return;
        event.getAffectedBlocks().removeIf(pos -> inside(d, pos));
    }

    public static boolean inside(ServerLevel level, BlockPos pos) {
        LabSavedData d = LabSavedData.get(level);
        return d.built && inside(d, pos);
    }

    /** Точка в объёме какого-нибудь построенного этажа этого мира. */
    public static boolean inside(LabSavedData d, BlockPos pos) {
        int m = LabTuning.PROTECT_MARGIN;
        for (int stop = 0; stop < LabTuning.STOPS; stop++) {
            if (!d.isBuilt(stop)) continue;
            int fy = d.stopY[stop], h = d.stopH[stop];
            int top = fy + h + (stop == 0 ? LabTuning.PROTECT_ROOF : 0);
            if (pos.getY() < fy - 1 || pos.getY() > top) continue;
            if (pos.getX() < d.bbox[stop * 4] - m || pos.getX() > d.bbox[stop * 4 + 2] + m) continue;
            if (pos.getZ() < d.bbox[stop * 4 + 1] - m || pos.getZ() > d.bbox[stop * 4 + 3] + m) continue;
            return true;
        }
        return false;
    }
}
