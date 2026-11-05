package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ObserverTuning;
import com.hypersomnia.client.ObserverDirector.Kind;
import com.hypersomnia.client.ObserverDirector.Outcome;
import com.hypersomnia.client.ObserverDirector.Plan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Эпизод DOOR (стадия 4+): где-то за спиной дверь «открывается и закрывается» несколько раз подряд,
 * с одинаковым ритмом и звуком, как заученное действие. Только звук на клиенте, дверь в мире не меняется.
 * Начинает режиссёр (ObserverDirector); здесь только проверка, есть ли рядом дверь, и исполнение.
 * Стадия 5: двери у места сна выбираются чаще.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class DoorEcho {
    private DoorEcho() {}

    private static int check = ObserverTuning.DOOR_CHECK_TICKS;
    private static Plan plan;
    private static int cyclesLeft;
    private static int wait;
    private static boolean open;
    private static Vec3 at = Vec3.ZERO;
    /** Ритм и звук выбираются один раз на эпизод. */
    private static int openGap;
    private static int closeGap;
    private static float pitch;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused()) return;
        boolean blocked = level.dimension() != Level.OVERWORLD || player.isSleeping() || !player.isAlive()
                || SleepSceneState.active() || ObserverClient.isBusy(mc);
        if (plan != null && ObserverDirector.current() != plan) stop(null);  // режиссёр прервал
        if (blocked) {
            if (plan != null) stop(Outcome.ABORT);
            return;
        }
        RandomSource r = level.random;

        if (plan != null) {
            if (watched(level, player)) {
                stop(Outcome.LOOK); // посмотрел на дверь: серия обрывается
                return;
            }
            if (--wait > 0) return;
            if (!open) {
                level.playLocalSound(at.x, at.y, at.z, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS,
                        ObserverTuning.DOOR_VOLUME, pitch, false);
                open = true;
                wait = openGap;
            } else {
                level.playLocalSound(at.x, at.y, at.z, SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS,
                        ObserverTuning.DOOR_VOLUME, pitch, false);
                open = false;
                wait = closeGap;
                if (--cyclesLeft <= 0) stop(Outcome.DONE);
            }
            return;
        }

        // Для решения режиссёра: есть ли рядом подходящая дверь (сам режиссёр мир не сканирует)
        if (--check <= 0) {
            check = ObserverTuning.DOOR_CHECK_TICKS;
            ObserverDirector.doorNearby = ObserverState.stage() >= ObserverTuning.EP_MIN_STAGE[Kind.DOOR.ordinal()]
                    && quietPlace(level, player) && findDoor(level, player, r) != null;
        }

        Plan p = ObserverDirector.offered(Kind.DOOR);
        if (p == null) return;
        if (!quietPlace(level, player)) {
            ObserverDirector.refuse(p, "day, open sky");
            return;
        }
        BlockPos door = findDoor(level, player, r);
        if (door == null) {
            ObserverDirector.refuse(p, "no door out of view");
            return;
        }
        p.variant = nearHome(door) ? 1 : 0;
        if (!ObserverDirector.accept(p)) return;
        plan = p;
        at = Vec3.atCenterOf(door);
        cyclesLeft = range(r, ObserverTuning.DOOR_CYCLES_MIN, ObserverTuning.DOOR_CYCLES_MAX);
        openGap = range(r, ObserverTuning.DOOR_OPEN_GAP_MIN, ObserverTuning.DOOR_OPEN_GAP_MAX);
        closeGap = range(r, ObserverTuning.DOOR_CLOSE_GAP_MIN, ObserverTuning.DOOR_CLOSE_GAP_MAX);
        pitch = 0.9f + r.nextFloat() * 0.2f;
        open = false;
        wait = 1;
    }

    /** Ночью или под крышей: днём на открытом месте дверь не хлопает. */
    private static boolean quietPlace(ClientLevel level, LocalPlayer player) {
        return ObserverCover.isNight(level) || !level.canSeeSky(player.blockPosition().above());
    }

    private static void stop(Outcome o) {
        if (o != null) ObserverDirector.end(plan, o);
        plan = null;
        cyclesLeft = 0;
        open = false;
    }

    /** Игрок смотрит на дверь: она в конусе взгляда и между глазами и дверью нет блоков. */
    private static boolean watched(ClientLevel level, LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 to = at.subtract(eye);
        double len = to.length();
        if (len < 0.5) return true;
        if (to.scale(1.0 / len).dot(player.getLookAngle()) < ObserverTuning.DOOR_STOP_LOOK_DOT) return false;
        BlockPos door = BlockPos.containing(at);
        HitResult hit = level.clip(new ClipContext(eye, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) return true;
        BlockPos hp = ((BlockHitResult) hit).getBlockPos();
        return hp.equals(door) || hp.equals(door.above());
    }

    private static int range(RandomSource r, int min, int max) {
        return min + r.nextInt(max - min + 1);
    }

    private static boolean nearHome(BlockPos door) {
        int[] home = ObserverState.home();
        if (home == null) return false;
        double dx = door.getX() - home[0];
        double dz = door.getZ() - home[2];
        return dx * dx + dz * dz <= ObserverTuning.DOOR_HOME_RADIUS * ObserverTuning.DOOR_HOME_RADIUS;
    }

    /**
     * Случайная дверь в радиусе, не ближе MIN_DIST и не в передней полусфере взгляда.
     * Стадия 5: двери у места сна получают вес DOOR_HOME_WEIGHT.
     */
    private static BlockPos findDoor(ClientLevel level, LocalPlayer player, RandomSource r) {
        BlockPos c = player.blockPosition();
        Vec3 look = player.getLookAngle();
        int rad = ObserverTuning.DOOR_RADIUS;
        double minSq = ObserverTuning.DOOR_MIN_DIST * ObserverTuning.DOOR_MIN_DIST;
        boolean homeBias = ObserverState.stage() >= 5;
        BlockPos best = null;
        int total = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -rad; dx <= rad; dx++) {
            for (int dz = -rad; dz <= rad; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    if (!ObserverCover.loaded(level, p)) continue;
                    BlockState s = level.getBlockState(p);
                    if (!s.is(BlockTags.WOODEN_DOORS) || s.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq < minSq) continue;
                    double dot = (dx * look.x + dy * look.y + dz * look.z) / Math.sqrt(distSq);
                    if (dot > ObserverTuning.DOOR_MAX_LOOK_DOT) continue;
                    int w = homeBias && nearHome(p) ? ObserverTuning.DOOR_HOME_WEIGHT : 1;
                    total += w;
                    if (r.nextInt(total) < w) best = p.immutable();   // взвешенный выбор среди подходящих
                }
            }
        }
        return best;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        plan = null;
        cyclesLeft = 0;
        open = false;
        check = ObserverTuning.DOOR_CHECK_TICKS;
    }
}
