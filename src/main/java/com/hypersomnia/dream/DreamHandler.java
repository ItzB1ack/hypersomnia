package com.hypersomnia.dream;

import com.hypersomnia.FatigueData;
import com.hypersomnia.FatigueTuning;
import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.SleepPenalty;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import com.hypersomnia.scene.SceneAttachments;

/** Серверная логика мира снов: таймер сна, усталость во сне и пустой мир без мобов. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class DreamHandler {
    private DreamHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

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
            DreamTransition.enter(player);
            return;
        }

        if (inDreamLevel && !dream.isInDream()) {
            DreamTransition.adoptUnexpectedArrival(player, dream);
        } else if (!inDreamLevel && dream.isInDream()) {
            // Вышли из мира снов другим путём (команда, смерть): сон закончен
            dream.setInDream(false);
            dream.setTicksLeft(0);
            return;
        }
        if (!dream.isInDream()) return;

        // Во сне усталость не копится
        FatigueData fatigue = player.getData(ModAttachments.FATIGUE);
        int rest = SleepPenalty.restFatigue(fatigue);
        if (fatigue.getFatigue() > rest) {
            fatigue.setFatigue(rest);
        }

        dream.setTicksLeft(dream.getTicksLeft() - 1);
        if (dream.getTicksLeft() <= 0) {
            DreamTransition.exit(player);
        }
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
