package com.hypersomnia.client;

import com.hypersomnia.ForestReplyTuning;
import com.hypersomnia.Hypersomnia;
import com.hypersomnia.forest.ForestReplyContext;
import com.hypersomnia.network.ForestReplyAckPayload;
import com.hypersomnia.network.ForestReplyPayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Два локальных звука из одной скрытой точки. Ничего не принимает/завершает в ObserverDirector. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class ForestReplyClient {
    private ForestReplyClient() {}

    private static ForestReplyPayload offer;
    private static ClientLevel origin;
    private static LocalPlayer listener;
    private static Vec3 source;
    private static SimpleSoundInstance first;
    private static SimpleSoundInstance second;
    private static boolean started;
    private static int age;

    public static void receive(ForestReplyPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        stop(mc, ForestReplyAckPayload.INTERRUPTED);
        offer = new ForestReplyPayload(payload.ticket(), ForestReplyTuning.clampGap(payload.gapTicks()), payload.forced());
        origin = mc.level;
        listener = mc.player;
        // Не ждём закрытия меню или окончания сцены: предложение не превращается в очередь.
        if (mc.isPaused() || blocked(mc)) stop(mc, ForestReplyAckPayload.BLOCKED);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ClientTickEvent.Post event) {
        if (offer == null) return;
        Minecraft mc = Minecraft.getInstance();
        // Подтверждаем уже начатый звук даже если в этом же тике появился приоритетный эпизод.
        confirmStarted(mc);
        if (mc.level == origin && mc.player == listener && mc.isPaused()) return;
        if (mc.level != origin || mc.player != listener || blocked(mc)) {
            stop(mc, ForestReplyAckPayload.BLOCKED);
            return;
        }
        if (first == null) {
            if (muted(mc)) {
                stop(mc, ForestReplyAckPayload.NO_AUDIO);
                return;
            }
            source = findSource(origin, listener);
            if (source == null) {
                stop(mc, ForestReplyAckPayload.NO_SOURCE);
                return;
            }
            first = sound(origin, source);
            mc.getSoundManager().play(first);
            confirmStarted(mc);
            return;
        }
        age++;
        if (!started && age > ForestReplyTuning.AUDIO_START_TIMEOUT) {
            stop(mc, ForestReplyAckPayload.NO_AUDIO);
            return;
        }
        if (!ForestReplyContext.loaded(origin, BlockPos.containing(source))
                || source.distanceToSqr(listener.getEyePosition()) > 16 * 16 || muted(mc)) {
            stop(mc, ForestReplyAckPayload.INTERRUPTED);
            return;
        }
        if (second == null && started && age >= offer.gapTicks()) {
            if (!hidden(origin, listener, source)) {
                stop(mc, ForestReplyAckPayload.INTERRUPTED);
                return;
            }
            second = sound(origin, source);
            mc.getSoundManager().play(second);
        }
        if (second != null && age >= offer.gapTicks() + ForestReplyTuning.AUDIO_TAIL) {
            stop(mc, ForestReplyAckPayload.INTERRUPTED);
        }
    }

    private static boolean blocked(Minecraft mc) {
        if (offer == null || origin == null || listener == null || mc.level != origin || mc.player != listener) return true;
        if (origin.dimension() != Level.OVERWORLD || !listener.isAlive() || listener.isSleeping()
                || listener.isSpectator() || (!offer.forced() && listener.isCreative()) || mc.screen != null
                || SleepSceneState.active() || SleepSceneState.silent() || ClientFatigueState.collapsing
                || ObserverState.completed() || ObserverDirector.current() != null || ObserverClient.hasGhost()
                || ChaseClient.active() || ChaseMusic.playing()) return true;
        return mc.options.keyAttack.isDown() && mc.hitResult instanceof BlockHitResult hit
                && origin.getBlockState(hit.getBlockPos()).is(BlockTags.LOGS);
    }

    private static boolean muted(Minecraft mc) {
        return mc.options.getSoundSourceVolume(SoundSource.MASTER) <= 0.0f
                || mc.options.getSoundSourceVolume(SoundSource.BLOCKS) <= 0.0f;
    }

    private static void confirmStarted(Minecraft mc) {
        if (offer != null && first != null && !started && mc.getSoundManager().isActive(first)) {
            started = true;
            PacketDistributor.sendToServer(new ForestReplyAckPayload(offer.ticket(), ForestReplyAckPayload.STARTED));
        }
    }

    private static void stop(Minecraft mc, int reason) {
        if (first != null) mc.getSoundManager().stop(first);
        if (second != null) mc.getSoundManager().stop(second);
        if (offer != null && !started && mc.getConnection() != null) {
            PacketDistributor.sendToServer(new ForestReplyAckPayload(offer.ticket(), reason));
        }
        offer = null;
        origin = null;
        listener = null;
        source = null;
        first = null;
        second = null;
        started = false;
        age = 0;
    }

    private static SimpleSoundInstance sound(ClientLevel level, Vec3 at) {
        return new SimpleSoundInstance(SoundEvents.WOOD_BREAK, SoundSource.BLOCKS,
                ForestReplyTuning.VOLUME, ForestReplyTuning.PITCH, level.random, at.x, at.y, at.z);
    }

    /** Сначала дешёвый ограниченный список брёвен, затем не более 32 проверок листвы/укрытия. */
    private static Vec3 findSource(ClientLevel level, LocalPlayer player) {
        BlockPos center = player.blockPosition();
        Vec3 eye = player.getEyePosition();
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                if (dx * dx + dz * dz < 6 * 6 || dx * dx + dz * dz > 13 * 13) continue;
                pos.set(center.getX() + dx, center.getY(), center.getZ() + dz);
                if (!ForestReplyContext.loaded(level, pos)) continue;
                for (int dy = -3; dy <= 4; dy++) {
                    pos.setY(center.getY() + dy);
                    if (!level.isOutsideBuildHeight(pos) && level.getBlockState(pos).is(BlockTags.LOGS)) {
                        candidates.add(pos.immutable());
                        break;
                    }
                }
            }
        }
        // Перемешиваем без отдельного постоянного RNG и без привязки к стадии/усталости.
        for (int i = candidates.size() - 1; i > 0; i--) {
            Collections.swap(candidates, i, level.random.nextInt(i + 1));
        }
        int checked = 0;
        for (BlockPos log : candidates) {
            if (++checked > ForestReplyTuning.MAX_SOURCE_CHECKS) break;
            Vec3 trunk = Vec3.atCenterOf(log);
            Vec3 away = new Vec3(trunk.x - eye.x, 0, trunk.z - eye.z).normalize();
            // Точка в воздухе за стволом: сам блок-источник НЕ может «заслонить сам себя».
            Vec3 at = trunk.add(away.scale(0.9));
            double distanceSq = at.distanceToSqr(eye);
            if (distanceSq < ForestReplyTuning.DISTANCE_MIN * ForestReplyTuning.DISTANCE_MIN
                    || distanceSq > ForestReplyTuning.DISTANCE_MAX * ForestReplyTuning.DISTANCE_MAX) continue;
            BlockPos cell = BlockPos.containing(at);
            if (!ForestReplyContext.loaded(level, cell)
                    || !level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()
                    || !level.getFluidState(cell).isEmpty() || !hidden(level, player, at)
                    || !ForestReplyContext.hasTree(level, log)) continue;
            return at;
        }
        return null;
    }

    private static boolean hidden(ClientLevel level, LocalPlayer player, Vec3 at) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double horizontal = Math.hypot(look.x, look.z);
        double yaw = Math.toRadians(player.getYRot());
        Vec3 facing = horizontal > 0.1
                ? new Vec3(look.x / horizontal, 0, look.z / horizontal)
                : new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 toward = new Vec3(at.x - eye.x, 0, at.z - eye.z).normalize();
        return facing.dot(toward) <= ForestReplyTuning.MAX_LOOK_DOT
                && !ObserverCover.hasSight(level, player, eye, at);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        // Соединение уже закрывается; сервер сам удалит transient-запрос при выходе.
        offer = null;
        stop(Minecraft.getInstance(), ForestReplyAckPayload.INTERRUPTED);
    }
}