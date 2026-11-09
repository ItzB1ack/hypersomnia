package com.hypersomnia.server;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.ObserverProgressData;
import com.hypersomnia.lab.ArchiveAccess;
import com.hypersomnia.lab.LabAntenna;
import com.hypersomnia.lab.CabinetBlockEntity;
import com.hypersomnia.lab.LabBreakers;
import com.hypersomnia.lab.LabCabinets;
import com.hypersomnia.lab.LabFixtures;
import com.hypersomnia.lab.LabLift;
import com.hypersomnia.lab.LabLocks;
import com.hypersomnia.lab.LabOutage;
import com.hypersomnia.lab.LabSavedData;
import com.hypersomnia.lab.LabTuning;
import com.hypersomnia.network.LabLiftPayload;
import com.hypersomnia.network.LabLiftSelectPayload;
import com.hypersomnia.network.LabLockCodePayload;
import com.hypersomnia.network.LabLockPayload;
import com.hypersomnia.network.LabPanelActionPayload;
import com.hypersomnia.network.LabPanelPayload;
import com.hypersomnia.network.LabSeedPayload;
import com.hypersomnia.network.LabTerminalPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ПКМ по панелям лаборатории: панель лифта, кодовые панели замков, рабочие станции, щит MAIN FEED, запечатанные шкафы,
 * серверные стойки и места хранилища (номер в полосе над хотбаром), заложенная дверь палаты 2D, каретка антенны ANT-1,
 * щиты этажей (LabOutage: во время «обесточено» возвращают свет; лифт тогда стоит).
 * Сервер проверяет все ответы клиента. При входе отправляет зерно вариантов шифров мира.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class LabHandler {
    private static final Map<UUID, Map<Integer, Long>> DENIED_UNTIL = new HashMap<>();
    private LabHandler() {}

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        DENIED_UNTIL.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent event) { DENIED_UNTIL.clear(); }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            PacketDistributor.sendToPlayer(player, new LabSeedPayload(LabSavedData.cipherSeed(player.server)));
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) return;
        LabSavedData d = LabSavedData.get(level);
        if (!d.built) return;
        Integer v = d.inter.get(event.getPos().asLong());
        if (v == null) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        int kind = v / 1000, id = v % 1000;
        if (kind == LabSavedData.KIND_LIFT) {
            int cur = LabLift.stopOf(player, d);
            if (cur < 0) return;
            if (LabOutage.active(level)) { // «обесточено»: лифт стоит
                level.playSound(null, event.getPos(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4f, 0.6f);
                player.displayClientMessage(Component.translatable("hypersomnia.lab.lock.nopower"), true);
                return;
            }
            int enabled = LabLift.enabledMask(player.serverLevel(), d) & ~(1 << cur);
            PacketDistributor.sendToPlayer(player, new LabLiftPayload(cur, enabled, d.builtMask));
        } else if (kind == LabSavedData.KIND_LOCK) {
            if (d.isLockOpen(id)) {
                player.displayClientMessage(Component.translatable("hypersomnia.lab.lock.open"), true);
                return;
            }
            if (LabTuning.lockNeedsFeed(id) && !d.hasFlag(LabSavedData.FLAG_FEED)) {
                player.displayClientMessage(Component.translatable("hypersomnia.lab.lock.nopower"), true);
                return;
            }
            if (id == LabTuning.LOCK_BUFFER) { // шифр 8: кода нет — панель ждёт, пока ANT-1 на крыше отправит буферы
                level.playSound(null, event.getPos(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4f, 0.7f);
                player.displayClientMessage(Component.translatable("hypersomnia.lab.lock.uplink"), true);
                return;
            }
            String draft = LabTuning.lockKeepsInput(id) ? player.getData(ModAttachments.OBSERVER).indexDraft() : "";
            PacketDistributor.sendToPlayer(player, new LabLockPayload(id, LabLockPayload.OPEN, draft));
        } else if (kind == LabSavedData.KIND_SEAL) {
            if (!d.isLockOpen(id)) {
                level.playSound(null, event.getPos(), SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.5f, 0.8f);
                player.displayClientMessage(Component.translatable("hypersomnia.lab.box.sealed"), true);
                return;
            }
            if (level.getBlockEntity(event.getPos()) instanceof CabinetBlockEntity cab) {
                LabCabinets.seedOnOpen(level, event.getPos(), cab);
                player.openMenu(cab);
            }
            LabLocks.boxOpened(player, id);
        } else if (kind == LabSavedData.KIND_TERMINAL) {
            ArchiveAccess.Snapshot snap = ArchiveAccess.snapshot(player, d, id);
            PacketDistributor.sendToPlayer(player, new LabTerminalPayload(id, snap.listed(), snap.readable()));
        } else if (kind == LabSavedData.KIND_BREAKER) {
            if (id > LabBreakers.BOARD) { // щит этажа: экрана нет; во время «обесточено» возвращает свет
                LabOutage.board(player, level, d, id - LabBreakers.BOARD, event.getPos());
                return;
            }
            level.playSound(null, event.getPos(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.4f, 1.3f);
            PacketDistributor.sendToPlayer(player, panelState(d, true));
        } else if (kind == LabSavedData.KIND_GATE) {
            // решётка-ворота: открывается только своей кодовой панелью
            player.displayClientMessage(Component.translatable("hypersomnia.lab.gate.locked"), true);
        } else if (kind == LabSavedData.KIND_FINALE) {
            FinaleHandler.use(player, level, d, id, event.getPos());
        } else if (kind == LabSavedData.KIND_CAPSULE) {
            FinaleHandler.capsule(player, level, d, id, event.getPos());
        } else if (kind == LabSavedData.KIND_RACK) {
            player.displayClientMessage(LabFixtures.rackInfo(level, event.getPos(), id), true);
        } else if (kind == LabSavedData.KIND_STACK) {
            player.displayClientMessage(LabFixtures.stackInfo(id), true);
        } else if (kind == LabSavedData.KIND_KNOCK) {
            player.displayClientMessage(Component.translatable("hypersomnia.lab.knock.wall"), true);
        } else if (kind == LabSavedData.KIND_ANTENNA) {
            LabAntenna.use(player, level, d, event.getPos());
        }
    }

    /** Состояние щита MAIN FEED для экрана щита; open — открыть экран. */
    public static LabPanelPayload panelState(LabSavedData d, boolean open) {
        int flags = (d.hasFlag(LabSavedData.FLAG_FEED) ? LabPanelPayload.FEED : 0)
                | (d.breakerTripped ? LabPanelPayload.TRIPPED : 0)
                | (open ? LabPanelPayload.OPEN : 0);
        return new LabPanelPayload(d.breakerSeq, flags);
    }

    public static void handlePanelAction(LabPanelActionPayload pl, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            LabPanelPayload reply = panelAction(player, pl.breaker());
            if (reply != null) PacketDistributor.sendToPlayer(player, reply);
        });
    }

    /**
     * Рубильник с экрана щита: щит построен в мире игрока, игрок не дальше PANEL_MAX_DIST, номер 0..FEEDERS.
     * Возвращает новое состояние для экрана или null, если запрос отброшен (тогда экран не меняется).
     */
    public static LabPanelPayload panelAction(ServerPlayer player, int breaker) {
        ServerLevel level = player.serverLevel();
        LabSavedData d = LabSavedData.get(level);
        if (!d.built || breaker < 0 || breaker > LabTuning.FEEDERS) return null;
        BlockPos pos = d.panelPos();
        if (pos == null) return null;
        if (player.blockPosition().distSqr(pos) > LabTuning.PANEL_MAX_DIST * LabTuning.PANEL_MAX_DIST) return null;
        LabBreakers.use(player, level, d, breaker, pos);
        return panelState(d, false);
    }

    public static void handleLockCode(LabLockCodePayload pl, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            LabLockPayload reply = lockCode(player, pl.lock(), pl.digits(), pl.submit());
            if (reply != null) PacketDistributor.sendToPlayer(player, reply);
        });
    }

    /**
     * Цифры с кодовой панели: лаборатория построена, замок закрыт, панель есть, игрок не дальше KEYPAD_MAX_DIST.
     * submit = false — только черновик (lockKeepsInput). Порядок проверки: питание, полнота индекса, код.
     * Непринятые цифры остаются черновиком. Возвращает ответ экрану или null (запрос отброшен или черновик сохранён).
     */
    public static LabLockPayload lockCode(ServerPlayer player, int lock, String digits, boolean submit) {
        ServerLevel level = player.serverLevel();
        LabSavedData d = LabSavedData.get(level);
        if (!d.built || d.isLockOpen(lock) || !LabLocks.digits(lock, digits, submit)) return null;
        BlockPos pos = d.lockPos(lock);
        if (pos == null) return null;
        if (player.blockPosition().distSqr(pos) > LabTuning.KEYPAD_MAX_DIST * LabTuning.KEYPAD_MAX_DIST) return null;
        boolean keep = LabTuning.lockKeepsInput(lock);
        ObserverProgressData data = player.getData(ModAttachments.OBSERVER);
        if (!submit) {
            if (keep) data.setIndexDraft(digits);
            return null;
        }
        if (keep) data.setIndexDraft(digits);
        String back = keep ? digits : "";
        long now = player.server.overworld().getGameTime();
        Map<Integer, Long> denied = DENIED_UNTIL.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        if (now < denied.getOrDefault(lock, 0L)) return null;
        if (LabTuning.lockNeedsFeed(lock) && !d.hasFlag(LabSavedData.FLAG_FEED))
            return new LabLockPayload(lock, LabLockPayload.NOPOWER, back);
        if (lock == LabTuning.LOCK_INDEX && !LabLocks.indexComplete(player)) {
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 0.5f);
            return new LabLockPayload(lock, LabLockPayload.INCOMPLETE, back);
        }
        if (!LabLocks.check(level, d, lock, digits)) {
            denied.put(lock, now + LabTuning.KEYPAD_DENIED_TICKS);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 0.5f);
            return new LabLockPayload(lock, LabLockPayload.DENIED, back);
        }
        denied.remove(lock);
        LabLocks.grant(level, d, lock);
        if (keep) data.setIndexDraft("");
        if (lock == LabTuning.LOCK_INDEX) data.setRecordReady(true);
        return new LabLockPayload(lock, LabLockPayload.GRANTED, "");
    }

    public static void handleLiftSelect(LabLiftSelectPayload pl, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            LabLift.request(player, pl.target());
        });
    }
}
