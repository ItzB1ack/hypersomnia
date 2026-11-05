package com.hypersomnia.scene;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.dream.DreamAttachments;
import com.hypersomnia.dream.DreamDimension;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.network.SleepScenePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Сцена «This isn't a dream»: один раз, с шансом, пока игрок лежит в кровати.
 * Его будят, ночь НЕ пропускается и в мир снов он не уходит; на несколько секунд он замирает и видит сцену.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class SleepSceneHandler {
    private SleepSceneHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        SceneData scene = player.getData(SceneAttachments.SCENE);
        if (scene.getHold() > 0) {
            tickHold(player, scene);
            return;
        }
        if (scene.isSeen()) return;
        if (com.hypersomnia.finale.FinaleLogic.completed(player)) return; // после финала сцены нет

        if (!player.isSleeping()) {
            scene.setRolledThisSleep(false);
            return;
        }
        if (scene.isRolledThisSleep() || player.getSleepTimer() < SleepSceneTuning.TRIGGER_TICK) return;
        scene.setRolledThisSleep(true);

        // Только настоящая кровать в обычном мире
        if (player.level().dimension() == DreamDimension.LEVEL
                || player.getData(DreamAttachments.DREAM).isInDream()) return;

        scene.addAttempt();
        int madness = player.getData(ModAttachments.FATIGUE).getMadness();
        float chance = Math.min(1f, SleepSceneTuning.CHANCE_BASE + SleepSceneTuning.CHANCE_PER_MADNESS * madness);
        boolean fire = scene.getAttempts() >= SleepSceneTuning.GUARANTEED_ATTEMPT
                || player.getRandom().nextFloat() < chance;
        if (!fire) return;

        start(player, scene);
    }

    public static void start(ServerPlayer player, SceneData scene) {
        scene.setSeen(true);
        scene.setHold(SleepSceneTuning.SCENE_TICKS); // игрок остаётся в кровати до конца сцены
        blockNightSkip(player.server);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                SleepSceneTuning.SCENE_TICKS, 6, false, false, false));
        PacketDistributor.sendToPlayer(player, new SleepScenePayload(player.getRandom().nextInt()));
    }

    private static void tickHold(ServerPlayer player, SceneData scene) {
        scene.setHold(scene.getHold() - 1);
        if (!player.isSleeping() && scene.getHold() > 0) {
            // ОТЛАДКА (временная): сколько тиков сцены прошло к моменту подъёма
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[scene] woke at tick "
                    + (SleepSceneTuning.SCENE_TICKS - scene.getHold()) + ", day=" + player.level().isDay()));
            scene.setHold(0); // разбудили извне
        }
        if (scene.getHold() == 0) {
            if (player.isSleeping()) player.stopSleepInBed(true, true); // ночь не пропускается
            releaseNightSkip(player.server);
        }
    }

    // ---- Блок пропуска ночи на время сцены: правило ставим в 101%, потом возвращаем ----
    private static int savedPercent = -1;

    private static void blockNightSkip(MinecraftServer server) {
        GameRules.IntegerValue rule = server.getGameRules().getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
        if (savedPercent < 0) savedPercent = rule.get();
        rule.set(101, server);
    }

    private static void releaseNightSkip(MinecraftServer server) {
        if (savedPercent < 0) return;
        server.getGameRules().getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE).set(savedPercent, server);
        savedPercent = -1;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        releaseNightSkip(event.getServer());
    }

    /** Страховка после краша посреди сцены: правило осталось на 101. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        GameRules.IntegerValue rule = event.getServer().getGameRules().getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
        if (rule.get() == 101) rule.set(100, event.getServer());
    }
}
