package com.hypersomnia.dream;

import com.hypersomnia.FatigueData;
import com.hypersomnia.FatigueTuning;
import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.SleepPenalty;
import com.hypersomnia.chase.ChaseHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import com.hypersomnia.scene.SceneAttachments;

/** Серверная логика мира снов: таймер сна, усталость во сне и пустой мир без мобов. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class DreamHandler {
    private DreamHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // [новое] Зеркало дома: захват во время сна и плавный перенос в мир снов.
        DreamMirror.tick(player);
        // [новое] Напольные часы: тиканье, удары и погоня.
        DreamClock.tick(player);

        DreamData dream = player.getData(DreamAttachments.DREAM);
        boolean inDreamLevel = player.level().dimension() == DreamDimension.LEVEL;

        // Кровать: полежал ~4 секунды, и вместо пропуска ночи уходишь в мир снов
        // После финала (FinaleLogic.completed) мир снов закрыт: обычная кровать, ночь пропускается как в ванили
        if (!dream.isInDream() && !inDreamLevel && player.isSleeping()
                && player.getSleepTimer() >= DreamTuning.BED_SLEEP_TICKS
                && player.getData(SceneAttachments.SCENE).getHold() <= 0 // пока идёт сцена, не трогаем
                && !com.hypersomnia.finale.FinaleLogic.completed(player)) {
            // Память привычек: где игрок спит (только кровати Верхнего мира)
            if (player.level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                player.getSleepingPos().ifPresent(p -> player.getData(ModAttachments.OBSERVER)
                        .noteSleep(p.getX(), p.getY(), p.getZ()));
            }
            player.stopSleepInBed(true, true);
            var progress = player.getData(ModAttachments.OBSERVER);
            boolean session = (progress.getMemories() & (1 << 5)) != 0 && !progress.session47Seen();
            boolean entered = session
                    ? DreamTransition.enterNightmare(player, com.hypersomnia.nightmare.NightmareDefs.SESSION_47)
                    : DreamTransition.enter(player);
            if (!entered)
                player.displayClientMessage(Component.translatable("hypersomnia.dream.transition_failed"), false);
            return;
        }

        if (inDreamLevel && !dream.isInDream()) {
            DreamTransition.adoptUnexpectedArrival(player, dream);
        } else if (!inDreamLevel && dream.isInDream()) {
            // Вышли из мира снов другим путём (команда, смерть): сон закончен
            DreamMirror.abort(player);
            DreamClock.clear(player);
            if (com.hypersomnia.nightmare.NightmareDefs.SHAFT.id().equals(dream.getKind()))
                com.hypersomnia.nightmare.ShaftHandler.release(player);
            dream.setInDream(false);
            dream.setTicksLeft(0);
            var observer = player.getData(ModAttachments.OBSERVER);
            observer.clearPending();
            observer.setNoteThisSleep(false);
            return;
        }
        if (!dream.isInDream() || !player.isAlive()) return;

        // Во сне усталость не копится
        FatigueData fatigue = player.getData(ModAttachments.FATIGUE);
        int rest = SleepPenalty.restFatigue(fatigue);
        if (!com.hypersomnia.nightmare.NightmareDefs.SHAFT.id().equals(dream.getKind())
                && fatigue.getFatigue() > rest) {
            fatigue.setFatigue(rest);
        }

        // [новое] Из лифта выход только через этаж 0: таймер сна для него не идёт.
        if (com.hypersomnia.nightmare.NightmareDefs.SHAFT.id().equals(dream.getKind())) return;
        dream.setTicksLeft(dream.getTicksLeft() - 1);
        if (dream.getTicksLeft() <= 0) {
            if (!DreamTransition.exit(player)) dream.setTicksLeft(DreamTuning.WAKE_RETRY_TICKS);
        }
    }

    /** Выход из игры во время сна не должен оставлять захваченный дом в памяти сервера. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) DreamMirror.abort(player);
    }

    /**
     * [новое] Урон в обычном сне будит игрока. Поимка погоней (hypersomnia:fragment) исключена:
     * у неё своё пробуждение с последствиями (DreamClock.onCaught / кошмар палаты).
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getAmount() <= 0f) return;
        DreamData dream = player.getData(DreamAttachments.DREAM);
        if (!dream.isInDream() || !dream.getKind().isEmpty()) return;
        if (!player.level().dimension().equals(DreamDimension.LEVEL)) return;
        if (event.getSource().is(ChaseHandler.FRAGMENT_DAMAGE)) return;
        event.setCanceled(true);
        DreamTransition.exit(player);
    }

    /** Смерть в обычном сне (в том числе /kill): не оставляем игрока в мире снов, будим. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        DreamData dream = player.getData(DreamAttachments.DREAM);
        if (!dream.isInDream() || !dream.getKind().isEmpty()) return;
        if (!player.level().dimension().equals(DreamDimension.LEVEL)) return;
        event.setCanceled(true);
        if (player.getHealth() <= 0f) player.setHealth(1.0f);
        DreamTransition.exit(player);
    }

    /** Мир снов пуст: ни животных, ни монстров при генерации и естественном спавне. */
    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.getLevel().getLevel().dimension() != DreamDimension.LEVEL) return;
        MobSpawnType type = event.getSpawnType();
        if (type == MobSpawnType.NATURAL || type == MobSpawnType.CHUNK_GENERATION
                || type == MobSpawnType.PATROL || type == MobSpawnType.REINFORCEMENT) {
            event.setSpawnCancelled(true);
        }
    }
}
