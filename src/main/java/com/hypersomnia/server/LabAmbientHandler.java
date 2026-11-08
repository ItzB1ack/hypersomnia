package com.hypersomnia.server;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.ObserverTuning;
import com.hypersomnia.chase.ChaseHandler;
import com.hypersomnia.finale.FinaleLogic;
import com.hypersomnia.lab.LabOutage;
import com.hypersomnia.lab.LabProtection;
import com.hypersomnia.lab.LabLift;
import com.hypersomnia.lab.LabSavedData;
import com.hypersomnia.lab.LabTuning;
import com.hypersomnia.lab.CapsuleBlock;
import com.hypersomnia.network.LabAmbientPayload;
import com.hypersomnia.scene.SceneAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Персональные тихие эпизоды на этажах лаборатории и в ночном доме, без изменения блоков и инвентарей. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class LabAmbientHandler {
    private LabAmbientHandler() {}
    private record Visit(ResourceKey<Level> dimension, int zone, long due) {}
    private static final Map<UUID, Visit> VISITS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
    /** Последняя сцена игрока: одна и та же не играет подряд. */
    private static final Map<UUID, Integer> LAST = new HashMap<>();

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % ObserverTuning.LAB_AMBIENT_SCAN_TICKS != 0) return;
        UUID id = p.getUUID();
        ServerLevel level = p.serverLevel();
        if (!p.isAlive() || p.isSleeping() || p.isSpectator() || p.isCreative()
                || FinaleLogic.completed(p) || ChaseHandler.activeFor(p)
                || p.getData(SceneAttachments.SCENE).getHold() > 0) {
            VISITS.remove(id);
            return;
        }
        int zone = zone(p, level);
        if (zone < 0) { VISITS.remove(id); return; }
        long now = p.getServer().overworld().getGameTime();
        Visit visit = VISITS.get(id);
        if (visit == null || !visit.dimension().equals(level.dimension()) || visit.zone() != zone) {
            int min = zone == 0 ? ObserverTuning.HOME_AMBIENT_DWELL_MIN : ObserverTuning.LAB_AMBIENT_DWELL_MIN;
            int range = zone == 0 ? ObserverTuning.HOME_AMBIENT_DWELL_RANGE : ObserverTuning.LAB_AMBIENT_DWELL_RANGE;
            int delay = min + p.getRandom().nextInt(range + 1);
            VISITS.put(id, new Visit(level.dimension(), zone, now + delay));
            return;
        }
        if (now < visit.due() || now < COOLDOWNS.getOrDefault(id, 0L)) return;
        int kind = choose(p, level, zone);
        if (kind < 0) { VISITS.remove(id); return; }
        BlockPos at = soundAt(p, kind);
        if (!level.hasChunkAt(at)) return;
        PacketDistributor.sendToPlayer(p, new LabAmbientPayload(at, kind, false));
        COOLDOWNS.put(id, now + (zone == 0 ? ObserverTuning.HOME_AMBIENT_COOLDOWN : ObserverTuning.LAB_AMBIENT_COOLDOWN));
        LAST.put(id, kind);
        VISITS.remove(id);
    }

    /**
     * Где сейчас звучит тихий эпизод: 1..5 — этаж лаборатории, 0 — ночной дом у места сна,
     * -1 — здесь эпизоды не играют. Дом — только Верхний мир, ночь и крыша над головой.
     */
    private static int zone(ServerPlayer p, ServerLevel level) {
        LabSavedData lab = LabSavedData.get(level);
        int floor = LabProtection.stopAt(lab, p.blockPosition());
        if (lab.built && floor >= 1 && floor <= 5 && LabLift.stopOf(p, lab) < 0) {
            return p.getData(ModAttachments.OBSERVER).stage() >= ObserverTuning.LAB_AMBIENT_MIN_STAGE ? floor : -1;
        }
        if (!level.dimension().equals(Level.OVERWORLD)) return -1;
        long tod = level.getDayTime() % 24000L;
        if (tod < ObserverTuning.HOME_AMBIENT_NIGHT_FROM || tod >= ObserverTuning.HOME_AMBIENT_NIGHT_TO) return -1;
        if (p.getData(ModAttachments.OBSERVER).stage() < ObserverTuning.HOME_AMBIENT_MIN_STAGE) return -1;
        int[] home = p.getData(ModAttachments.OBSERVER).home();
        if (home == null) return -1;
        BlockPos bed = new BlockPos(home[0], home[1], home[2]);
        if (!level.hasChunkAt(bed)) return -1;
        if (p.blockPosition().distSqr(bed) > ObserverTuning.HOME_AMBIENT_RADIUS * ObserverTuning.HOME_AMBIENT_RADIUS) return -1;
        return level.canSeeSky(p.blockPosition().above()) ? -1 : 0;
    }

    /** Сцена для зоны: только доступные по стадии и питанию, прошлая не повторяется, если есть выбор. */
    private static int choose(ServerPlayer p, ServerLevel level, int zone) {
        int[] pool = zone == 0 ? ObserverTuning.HOME_AMBIENT_SCENES : ObserverTuning.LAB_AMBIENT_FLOOR_SCENES[zone - 1];
        int stage = p.getData(ModAttachments.OBSERVER).stage();
        int last = LAST.getOrDefault(p.getUUID(), -1);
        int[] candidates = new int[pool.length];
        int n = 0;
        for (int k : pool) {
            if (k < 0 || k >= ObserverTuning.LAB_AMBIENT_SCENE_TICKS.length) continue;
            if (stage < ObserverTuning.LAB_AMBIENT_SCENE_MIN_STAGE[k]) continue;
            if (ObserverTuning.LAB_AMBIENT_SCENE_POWER[k] && LabOutage.active(level)) continue;
            candidates[n++] = k;
        }
        if (n == 0) return -1;
        int[] pick = new int[n];
        int m = 0;
        for (int i = 0; i < n; i++) if (candidates[i] != last || n == 1) pick[m++] = candidates[i];
        return pick[p.getRandom().nextInt(m)];
    }

    /** Точка звука сцены: назад по взгляду и/или выше головы (шаги над потолком, лифт за стеной). */
    private static BlockPos soundAt(ServerPlayer p, int kind) {
        if (kind == ObserverTuning.LAB_AMBIENT_CAPSULE_SCENE) {
            // Сцена 10: звук идёт от ближайшей закрытой капсулы, а не из пустого места за спиной.
            BlockPos capsule = closedCapsule(p);
            if (capsule != null) return capsule;
        }
        int distance = ObserverTuning.LAB_AMBIENT_SCENE_DISTANCE[kind];
        double dx = -p.getViewVector(1f).x, dz = -p.getViewVector(1f).z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4) { dx = 0; dz = -1; len = 1; }
        return BlockPos.containing(p.getX() + dx / len * distance,
                p.getY() + ObserverTuning.LAB_AMBIENT_SCENE_Y[kind],
                p.getZ() + dz / len * distance);
    }

    /** Ближайшее изголовье закрытой капсулы в пределах 12 блоков по горизонтали; null — капсул рядом нет. */
    private static BlockPos closedCapsule(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos base = p.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int radius = ObserverTuning.LAB_AMBIENT_CAPSULE_RADIUS;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    if (!level.hasChunkAt(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof CapsuleBlock)
                            || state.getValue(CapsuleBlock.PART) != CapsuleBlock.Part.HEAD
                            || state.getValue(CapsuleBlock.OPEN)) continue;
                    double d = pos.distSqr(base);
                    if (d < bestDistance) { bestDistance = d; best = pos; }
                }
            }
        }
        return best;
    }

    /** Отладка: состояние ожидания и кулдауна для этого игрока. */
    public static String status(ServerPlayer p) {
        long now = p.getServer().overworld().getGameTime();
        UUID id = p.getUUID();
        Visit v = VISITS.get(id);
        return "Ambient: " + (v == null ? "no dwell" : (v.zone() == 0 ? "home" : "floor " + LabTuning.stopLabel(v.zone()))
                + ", in " + Math.max(0L, v.due() - now) + "t")
                + ", cooldown " + Math.max(0L, COOLDOWNS.getOrDefault(id, 0L) - now) + "t"
                + ", last " + LAST.getOrDefault(id, -1)
                + ", stage " + p.getData(ModAttachments.OBSERVER).stage();
    }

    /** Отладка: проиграть сцену сразу, без ожидания и кулдауна. */
    public static boolean play(ServerPlayer p, int kind) {
        if (kind < 0 || kind >= ObserverTuning.LAB_AMBIENT_SCENE_TICKS.length) return false;
        ServerLevel level = p.serverLevel();
        BlockPos at = soundAt(p, kind);
        if (!level.hasChunkAt(at)) return false;
        PacketDistributor.sendToPlayer(p, new LabAmbientPayload(at, kind, true));
        LAST.put(p.getUUID(), kind);
        return true;
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID(); VISITS.remove(id); COOLDOWNS.remove(id); LAST.remove(id);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { VISITS.clear(); COOLDOWNS.clear(); LAST.clear(); }
}
