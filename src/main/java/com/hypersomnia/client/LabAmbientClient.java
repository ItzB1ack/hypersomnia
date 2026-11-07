package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModSounds;
import com.hypersomnia.ObserverTuning;
import com.hypersomnia.network.LabAmbientPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Одна последовательность на существующий план режиссёра, без экранов и изменения мира. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class LabAmbientClient {
    private LabAmbientClient() {}
    private static ObserverDirector.Plan plan;
    private static ResourceKey<Level> dimension;
    private static BlockPos position;
    private static int scene, ticks;

    public static void handle(LabAmbientPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (plan != null || payload.kind() < 0 || payload.kind() >= ObserverTuning.LAB_AMBIENT_SCENE_TICKS.length
                    || blocked(mc)) return;
            ObserverDirector.Plan candidate = ObserverDirector.request(ObserverDirector.Kind.GLITCH);
            if (candidate == null || !ObserverDirector.accept(candidate)) return;
            plan = candidate;
            dimension = mc.level.dimension();
            position = payload.pos();
            scene = payload.kind();
            ticks = 0;
        });
    }
    private static boolean blocked(Minecraft mc) {
        return mc.level == null || mc.player == null || !mc.player.isAlive() || mc.player.isSleeping()
                || ObserverClient.isBusy(mc) || SleepSceneState.active() || SleepSceneState.silent()
                || NoraRoomClient.active() || LabPathClient.active() || ChaseClient.active()
                || FinaleClientState.inside || ObserverState.completed();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (plan == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (blocked(mc) || !mc.level.dimension().equals(dimension) || ObserverDirector.current() != plan) {
            finish(ObserverDirector.Outcome.ABORT); return;
        }
        if (mc.isPaused()) return;
        ticks++;
        int[] times = ObserverTuning.LAB_AMBIENT_SCENE_TICKS[scene];
        byte[] sounds = ObserverTuning.LAB_AMBIENT_SCENE_SOUNDS[scene];
        for (int i = 0; i < times.length; i++) {
            if (ticks != times[i]) continue;
            SoundEvent sound = switch (sounds[i]) {
                case 0 -> ModSounds.TAPE_CLICK.get();
                case 1 -> ModSounds.TAPE_DROPOUT.get();
                case 2 -> ModSounds.TAPE_HISS.get();
                case 3 -> ModSounds.LAB_KNOCK.get();
                default -> null;
            };
            if (sound != null) mc.level.playLocalSound(position.getX() + .5, position.getY() + .5, position.getZ() + .5,
                    sound, SoundSource.BLOCKS, ObserverTuning.LAB_AMBIENT_VOLUME,
                    ObserverTuning.LAB_AMBIENT_PITCH_MIN + mc.level.random.nextFloat() * ObserverTuning.LAB_AMBIENT_PITCH_RANGE, false);
            break;
        }
        if (ticks >= times[times.length - 1] + ObserverTuning.LAB_AMBIENT_TAIL_TICKS)
            finish(ObserverDirector.Outcome.DONE);
    }
    private static void finish(ObserverDirector.Outcome outcome) {
        ObserverDirector.end(plan, outcome);
        plan = null; position = null; dimension = null; ticks = 0;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (plan != null) finish(ObserverDirector.Outcome.ABORT);
    }
}
