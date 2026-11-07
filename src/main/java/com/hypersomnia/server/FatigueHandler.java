package com.hypersomnia.server;

import com.hypersomnia.FatigueData;
import com.hypersomnia.FatigueTuning;
import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.SleepPenalty;
import com.hypersomnia.dream.DreamTransition;
import com.hypersomnia.network.FatigueSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class FatigueHandler {
    private FatigueHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        FatigueData data = player.getData(ModAttachments.FATIGUE);
        // после финала усталость не копится и не показывается (FinaleLogic.completed)
        boolean exempt = player.isCreative() || player.isSpectator() || !player.isAlive()
                || com.hypersomnia.finale.FinaleLogic.completed(player);

        if (!exempt) {
            tickFatigue(player, data);
        }
        syncIfNeeded(player, data, exempt);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        FatigueData data = player.getData(ModAttachments.FATIGUE);
        data.setCollapseTimer(0);
        data.setCollapseGraceTicks(0);
        data.setSyringeCollapse(false);
        data.resetForcedSleepAttempts();
        data.setFatigue(Math.min(data.getFatigue(), FatigueTuning.FATIGUE_AFTER_FORCED_SLEEP));
    }

    private static void tickFatigue(ServerPlayer player, FatigueData data) {
        // Усталость в кошмаре не набирается и не запускает новое падение.
        if (player.getData(com.hypersomnia.dream.DreamAttachments.DREAM).isInDream()) return;
        boolean grace = data.getCollapseGraceTicks() > 0;
        if (grace) data.setCollapseGraceTicks(data.getCollapseGraceTicks() - 1);
        // 1) Идёт падение: ничего больше не считаем
        if (data.getCollapseTimer() > 0) {
            tickCollapse(player, data);
            return;
        }

        // 2) Набор или восстановление усталости
        // ВНИМАНИЕ: пока кровать просто снимает усталость. Когда будет измерение снов,
        // сон в кровати должен отправлять туда же, куда и принудительный.
        if (player.isSleeping()) {
            data.addFatigue(-FatigueTuning.SLEEP_RECOVERY_PER_TICK);
        } else {
            data.addFatigue(FatigueTuning.GAIN_PER_TICK);
        }

        float ratio = FatigueTuning.ratio(data.getFatigue());

        // 3) Ускоренный голод
        if (ratio >= FatigueTuning.HUNGER_START) {
            float p = FatigueTuning.progress(ratio, FatigueTuning.HUNGER_START);
            player.causeFoodExhaustion(FatigueTuning.EXTRA_EXHAUSTION_PER_TICK_MAX * p);
        }

        // 4) Бег: основная блокировка на клиенте, это страховка
        if (ratio >= FatigueTuning.NO_SPRINT_START && player.isSprinting()) {
            player.setSprinting(false);
        }

        // 5) Достигли максимума: игрок падает
        if (!grace && data.getFatigue() >= FatigueTuning.MAX_FATIGUE) {
            data.resetForcedSleepAttempts();
            data.setCollapseTimer(FatigueTuning.COLLAPSE_TICKS);
            player.setSprinting(false);
        }
    }

    private static void tickCollapse(ServerPlayer player, FatigueData data) {
        int remaining = data.getCollapseTimer() - 1;
        data.setCollapseTimer(remaining);

        // Практически не даём двигаться
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 6, false, false, false));
        player.setSprinting(false);

        if (remaining <= 0) {
            finishForcedSleep(player, data);
        }
    }

    private static void finishForcedSleep(ServerPlayer player, FatigueData data) {
        // Начисляем последствия только после успешного перехода. У сцены и отсутствующего измерения
        // возможен отказ; ограничение повторов не оставляет игрока навсегда обездвиженным.
        var nightmare = data.isSyringeCollapse()
                ? com.hypersomnia.nightmare.NightmareDefs.ROUNDS
                : com.hypersomnia.nightmare.NightmareDefs.SHAFT;
        if (!DreamTransition.enterNightmare(player, nightmare)) {
            if (data.incrementForcedSleepAttempts() < FatigueTuning.FORCED_SLEEP_MAX_ATTEMPTS) {
                data.setCollapseTimer(FatigueTuning.FORCED_SLEEP_RETRY_TICKS);
                return;
            }
            data.resetForcedSleepAttempts();
            data.setCollapseTimer(0);
            data.setSyringeCollapse(false);
            data.setFatigue(Math.round(FatigueTuning.MAX_FATIGUE * FatigueTuning.FAILED_SLEEP_FATIGUE));
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            player.displayClientMessage(Component.translatable("hypersomnia.dream.transition_failed"), false);
            return;
        }
        data.resetForcedSleepAttempts();
        data.setSyringeCollapse(false);
        data.addMadness(1);
        // В «Шахте» отдыха нет: усталость не снижается.
        data.setCollapseTimer(0);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
    }

    private static void syncIfNeeded(ServerPlayer player, FatigueData data, boolean exempt) {
        // Для креатива и наблюдателя клиент видит 0: никаких эффектов
        int shown = exempt ? 0 : data.getFatigue();
        boolean collapsing = !exempt && data.getCollapseTimer() > 0;

        boolean first = data.getLastSyncedFatigue() < 0;
        boolean moved = Math.abs(shown - data.getLastSyncedFatigue()) >= FatigueTuning.SYNC_THRESHOLD;
        boolean zeroFlip = (shown == 0) != (data.getLastSyncedFatigue() == 0);
        boolean collapseFlip = collapsing != data.isLastSyncedCollapsing();
        // Страховка от рассинхрона: значение периодически уходит клиенту в любом случае
        boolean heartbeat = player.tickCount % FatigueTuning.RESYNC_INTERVAL_TICKS == 0;

        if (first || moved || zeroFlip || collapseFlip || heartbeat) {
            PacketDistributor.sendToPlayer(player, new FatigueSyncPayload(shown, collapsing));
            data.markSynced(shown, collapsing);
        }
    }
}
