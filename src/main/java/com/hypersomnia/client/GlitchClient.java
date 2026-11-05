package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ObserverTuning;
import com.hypersomnia.client.ObserverDirector.Kind;
import com.hypersomnia.client.ObserverDirector.Outcome;
import com.hypersomnia.client.ObserverDirector.Plan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * Эпизод GLITCH: сбой экспозиции, редко, коротко, без тряски камеры. На время сбоя глушатся звуки мира
 * (музыка и интерфейс звучат). Начинает режиссёр (ObserverDirector); здесь только исполнение.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class GlitchClient {
    private GlitchClient() {}

    private static Plan plan;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused()) {
            if (player == null) stop();
            return;
        }
        if (GlitchState.activeTicks > 0) {
            if (--GlitchState.activeTicks == 0) {
                ObserverDirector.end(plan, Outcome.DONE);
                stop();
            }
            return;
        }
        Plan p = ObserverDirector.offered(Kind.GLITCH);
        if (p == null) return;
        if (!isEligible(mc, player)) {
            ObserverDirector.refuse(p, "not now (sleeping, scene, collapse)");
            return;
        }
        if (!ObserverDirector.accept(p)) return;
        plan = p;
        int dur = Mth.randomBetweenInclusive(GlitchState.RANDOM,
                ObserverTuning.GLITCH_TICKS_MIN, ObserverTuning.GLITCH_TICKS_MAX);
        GlitchState.activeTicks = dur;
        GlitchState.totalTicks = dur;
        GlitchState.lineIndex = GlitchState.RANDOM.nextInt(3);
        ObserverClient.hushWorld(mc);
    }

    private static boolean isEligible(Minecraft mc, LocalPlayer player) {
        if (player.isSleeping() || ObserverClient.isBusy(mc) || !player.isAlive()) return false;
        if (SleepSceneState.active() || SleepSceneState.silent()) return false;
        return !ClientFatigueState.collapsing;
    }

    private static void stop() {
        plan = null;
        GlitchState.activeTicks = 0;
        GlitchState.totalTicks = 0;
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (GlitchState.activeTicks > 0 && ObserverClient.isWorldSound(event.getOriginalSound())) event.setSound(null);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stop();
    }
}
