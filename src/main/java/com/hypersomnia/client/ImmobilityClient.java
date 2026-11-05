package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ObserverTuning;
import com.hypersomnia.client.ObserverDirector.Kind;
import com.hypersomnia.client.ObserverDirector.Outcome;
import com.hypersomnia.client.ObserverDirector.Plan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * Эпизод STILL: 20 секунд без ввода -> контекст просит эпизод у режиссёра (не всегда получает).
 * Эпизод: тишина мира (музыка и интерфейс не трогаются) -> один шаг за спиной -> со стадии 4, как нарушение правила,
 * силуэт за спиной (ObserverClient.showBehind): выдержишь взгляд (ObserverTuning.gazeTicks) — исчезнет, иначе уйдёт
 * сам через STILL_VISIBLE_TICKS.
 * Любое движение/взгляд/клик/скролл сбрасывают счётчик; пока открыт экран, счётчик стоит на нуле.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class ImmobilityClient {
    private ImmobilityClient() {}

    private static int stillTicks;
    private static int silenceTicks;
    private static int spawnDelay = -1;
    private static Plan plan;
    private static float lastYaw;
    private static float lastPitch;
    private static boolean hadPlayer;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused()) {
            stillTicks = 0;
            hadPlayer = false;
            return;
        }

        if (plan != null) {
            if (ObserverDirector.current() != plan) {            // режиссёр прервал
                clearEpisode();
            } else if (isBusy(mc, player)) {                     // экран, кровать, сцена: эпизод прерван, ничего не догоняет
                ObserverDirector.end(plan, Outcome.ABORT);
                clearEpisode();
            } else if (silenceTicks > 0) {
                if (--silenceTicks == 0) {
                    playStepBehind(mc, player);
                    if (plan.figure) {
                        spawnDelay = ObserverTuning.STILL_SPAWN_DELAY;
                    } else {
                        ObserverDirector.end(plan, Outcome.DONE);
                        clearEpisode();
                    }
                }
            } else if (spawnDelay > 0 && --spawnDelay == 0) {
                // Дальше эпизод ведёт ObserverClient (силуэт) и сам сообщает режиссёру конец
                if (!ObserverClient.showBehind(plan)) ObserverDirector.end(plan, Outcome.DONE);
                clearEpisode();
            }
            stillTicks = 0;
            return;
        }

        float yaw = player.getYRot();
        float pitch = player.getXRot();
        boolean turned = hadPlayer
                && (Math.abs(yaw - lastYaw) > 0.05f || Math.abs(pitch - lastPitch) > 0.05f);
        lastYaw = yaw;
        lastPitch = pitch;
        hadPlayer = true;

        boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4;
        if (moving || turned || isBusy(mc, player)) {
            stillTicks = 0;
        } else if (stillTicks < Integer.MAX_VALUE) {
            stillTicks++;
        }

        if (stillTicks >= ObserverTuning.STILL_TICKS) {
            stillTicks = 0;
            if (ObserverClient.hasGhost()) return;
            start(mc, ObserverDirector.request(Kind.STILL));
        }
    }

    private static boolean start(Minecraft mc, Plan p) {
        if (p == null) return false;
        if (!ObserverDirector.accept(p)) return false;
        plan = p;
        silenceTicks = ObserverTuning.STILL_SILENCE_TICKS;
        spawnDelay = -1;
        ObserverClient.hushWorld(mc);
        return true;
    }

    private static void clearEpisode() {
        plan = null;
        silenceTicks = 0;
        spawnDelay = -1;
    }

    private static boolean isBusy(Minecraft mc, LocalPlayer player) {
        if (player.isSleeping()) return true;
        if (ObserverClient.isBusy(mc)) return true;
        if (SleepSceneState.active() || SleepSceneState.silent()) return true;
        if (ClientFatigueState.collapsing) return true;
        return player.isCreative() || player.isSpectator() || !player.isAlive();
    }

    /** Один шаг за спиной, звук берётся у блока под ногами. */
    private static void playStepBehind(Minecraft mc, LocalPlayer player) {
        var look = player.getLookAngle();
        double dx = -look.x;
        double dz = -look.z;
        double len = Math.hypot(dx, dz);
        if (len > 1.0E-4) {
            dx /= len;
            dz /= len;
        }
        double dist = 2.0;
        double x = player.getX() + dx * dist;
        double y = player.getY();
        double z = player.getZ() + dz * dist;
        BlockState below = mc.level.getBlockState(player.blockPosition().below());
        SoundType type = below.getSoundType();
        if (type == null) return;
        mc.level.playLocalSound(x, y, z, type.getStepSound(), SoundSource.PLAYERS, 0.7f, 0.85f, false);
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        stillTicks = 0;
    }

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Pre event) {
        stillTicks = 0;
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        stillTicks = 0;
    }

    /** Тишина: глушим новые звуки мира, пока silenceTicks > 0. Музыка и интерфейс звучат. */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (silenceTicks > 0 && ObserverClient.isWorldSound(event.getOriginalSound())) event.setSound(null);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stillTicks = 0;
        clearEpisode();
        hadPlayer = false;
    }

    /** Отладка: запросить эпизод сейчас (после ObserverDirector.force(STILL, ...)). false — не начался. */
    public static boolean triggerNow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || plan != null) return false;
        stillTicks = 0;
        return start(mc, ObserverDirector.request(Kind.STILL));
    }
}
