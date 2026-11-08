package com.hypersomnia.dream;

import com.hypersomnia.ModAttachments;
import com.hypersomnia.ObserverProgressData;
import com.hypersomnia.finale.FinaleLogic;
import com.hypersomnia.lab.LabSavedData;
import com.hypersomnia.lab.LabTuning;
import com.hypersomnia.nightmare.NightmareDefs;
import com.hypersomnia.nightmare.NightmareSavedData;
import com.hypersomnia.nightmare.NightmareTuning;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * [новое] Зеркало дома в мире снов.
 *
 * <p>Пока игрок лежит в кровати, вокруг него захватывается объём Верхнего мира и переносится
 * в сон по тем же координатам. Снаружи и внутри дом выглядит ровно как настоящий. Со стадии 4
 * внутрь добавляется портал-петля: коридор возвращает игрока назад, хотя снаружи дом прежний.
 *
 * <p>Захват и перенос идут по секциям 16x16x16, по две за тик. Сон в кровати длится 80 тиков,
 * поэтому зеркало успевает полностью лечь до телепорта.
 */
public final class DreamMirror {
    private DreamMirror() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int SECTION = 16;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Этот сон уже проверен и зеркало не положено (стадия/защищённая зона): не проверять каждый тик. */
    private static final Set<UUID> SUPPRESSED = new HashSet<>();

    /** Один захваченный/переносимый дом. Живёт только на сервере. */
    private static final class Session {
        final ServerLevel source;
        final ServerLevel dream;
        final BlockPos bed;
        final int stage;
        final int minX, minY, minZ, maxX, maxY, maxZ;
        final int sizeX, sizeY, sizeZ;
        final int sectionsX, sectionsY, sectionsZ, sections;
        final BlockState[] states;
        final Map<Integer, CompoundTag> blockEntities = new HashMap<>();
        int captured;
        int applied;
        /** Сколько тиков ещё пробовать поставить петлю, если сущности в сне не успели загрузиться. */
        int loopWait = DreamMirrorTuning.LOOP_RETRY_TICKS;

        Session(ServerLevel source, ServerLevel dream, BlockPos bed, int stage,
                int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.source = source;
            this.dream = dream;
            this.bed = bed;
            this.stage = stage;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.sizeX = maxX - minX + 1;
            this.sizeY = maxY - minY + 1;
            this.sizeZ = maxZ - minZ + 1;
            this.sectionsX = sections(sizeX);
            this.sectionsY = sections(sizeY);
            this.sectionsZ = sections(sizeZ);
            this.sections = sectionsX * sectionsY * sectionsZ;
            this.states = new BlockState[sizeX * sizeY * sizeZ];
        }

        boolean captureDone() { return captured >= sections; }
        boolean applyDone() { return applied >= sections; }
    }

    private static int sections(int size) { return (size + SECTION - 1) / SECTION; }

    /**
     * Общий тик: запускает захват во сне, продолжает перенос и убирает сессию, если игрок
     * встал с кровати или ушёл из нужного мира.
     */
    public static void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        Session s = SESSIONS.get(id);
        if (s == null) {
            if (!player.isSleeping() || player.serverLevel().dimension() != Level.OVERWORLD) {
                SUPPRESSED.remove(id);
                return;
            }
            if (SUPPRESSED.contains(id)) return;
            if (start(player) == null) SUPPRESSED.add(id);
            return;
        }

        if (player.serverLevel().dimension() == DreamDimension.LEVEL) {
            if (!s.captureDone()) {
                capture(s, DreamMirrorTuning.CAPTURE_PER_TICK);
                return;
            }
            if (!s.applyDone()) {
                apply(s, DreamMirrorTuning.APPLY_PER_TICK);
                return;
            }
            if (!DreamMirrorLoop.ensure(s.dream, s.bed, s.stage)) {
                if (--s.loopWait > 0) return;
            }
            SESSIONS.remove(id);
            return;
        }

        if (player.serverLevel() != s.source || !player.isSleeping()) {
            SESSIONS.remove(id);
            return;
        }

        if (!s.captureDone()) capture(s, DreamMirrorTuning.CAPTURE_PER_TICK);
        else apply(s, DreamMirrorTuning.APPLY_PER_TICK);
        // Сессию в Верхнем мире не убираем: иначе следующий тик сна запустит захват заново.
    }

    /**
     * Доработать зеркало прямо перед телепортом; остаток (если есть) доделается уже во сне.
     * Если срез готов, сразу ставим петлю — игрок попадёт в сон с уже работающим коридором.
     */
    public static void finish(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        int budget = DreamMirrorTuning.TRANSITION_BUDGET;
        while (budget > 0 && !s.captureDone()) {
            capture(s, 1);
            budget--;
        }
        while (budget > 0 && !s.applyDone()) {
            apply(s, 1);
            budget--;
        }
        if (!s.applyDone()) return;
        if (DreamMirrorLoop.ensure(s.dream, s.bed, s.stage)) {
            SESSIONS.remove(player.getUUID());
        }
    }

    /** Отменить сессию (пробуждение, выход из сна, смерть). Уже поставленные блоки остаются в сне. */
    public static void abort(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
        SUPPRESSED.remove(player.getUUID());
    }

    /** Отладочная команда: доделать зеркало для текущей позиции, не дожидаясь сна. */
    public static boolean force(ServerPlayer player) {
        if (player.serverLevel().dimension() != Level.OVERWORLD) return false;
        UUID id = player.getUUID();
        SESSIONS.remove(id);
        Session s = start(player);
        if (s == null) return false;
        while (!s.captureDone()) capture(s, 8);
        while (!s.applyDone()) apply(s, 8);
        DreamMirrorLoop.ensure(s.dream, s.bed, s.stage);
        SESSIONS.remove(id);
        return true;
    }

    public static String status(ServerPlayer player) {
        int stage = player.getData(ModAttachments.OBSERVER).stage();
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) {
            return "DreamMirror: idle, stage=" + stage + ", mirror from " + DreamMirrorTuning.MIN_STAGE
                    + ", loop from " + DreamMirrorTuning.LOOP_MIN_STAGE;
        }
        return "DreamMirror: stage=" + s.stage + ", bed=" + s.bed
                + ", capture=" + s.captured + "/" + s.sections
                + ", apply=" + s.applied + "/" + s.sections
                + ", loop=" + DreamMirrorLoop.status(s.dream, s.bed);
    }

    // ------------------------------------------------------------------ запуск

    private static Session start(ServerPlayer player) {
        if (FinaleLogic.completed(player)) return null;

        ObserverProgressData progress = player.getData(ModAttachments.OBSERVER);
        int stage = progress.stage();
        if (stage < DreamMirrorTuning.MIN_STAGE) return null;
        if ((progress.getMemories() & (1 << 5)) != 0 && !progress.session47Seen()) return null;

        ServerLevel source = player.serverLevel();
        if (source.dimension() != Level.OVERWORLD) return null;
        MinecraftServer server = player.getServer();
        if (server == null) return null;
        ServerLevel dream = server.getLevel(DreamDimension.LEVEL);
        if (dream == null) return null;

        BlockPos bed = player.getSleepingPos().orElse(player.blockPosition());

        int r = DreamMirrorTuning.CHUNK_RADIUS;
        ChunkPos cp = new ChunkPos(bed);
        int minX = (cp.x - r) << 4;
        int maxX = ((cp.x + r) << 4) + 15;
        int minZ = (cp.z - r) << 4;
        int maxZ = ((cp.z + r) << 4) + 15;
        int minY = Math.max(source.getMinBuildHeight(), bed.getY() - DreamMirrorTuning.VERTICAL_BELOW);
        int maxY = Math.min(source.getMaxBuildHeight() - 1, bed.getY() + DreamMirrorTuning.VERTICAL_ABOVE);

        if (protectedArea(source, minX, minZ, maxX, maxZ)
                || protectedArea(dream, minX, minZ, maxX, maxZ)) {
            LOGGER.info("DreamMirror: echo at {} skipped (protected zone)", bed);
            return null;
        }

        Session s = new Session(source, dream, bed, stage,
                minX, minY, minZ, maxX, maxY, maxZ);
        SESSIONS.put(player.getUUID(), s);
        LOGGER.info("DreamMirror: echo at {} stage {}", bed, stage);
        return s;
    }

    private static boolean protectedArea(ServerLevel level, int minX, int minZ, int maxX, int maxZ) {
        LabSavedData lab = LabSavedData.get(level);
        if (lab.built) {
            for (int stop = 0; stop < LabTuning.STOPS; stop++) {
                if (!lab.isBuilt(stop)) continue;
                int x1 = lab.bbox[stop * 4], z1 = lab.bbox[stop * 4 + 1];
                int x2 = lab.bbox[stop * 4 + 2], z2 = lab.bbox[stop * 4 + 3];
                if (minX <= x2 + 4 && maxX >= x1 - 4 && minZ <= z2 + 4 && maxZ >= z1 - 4) return true;
            }
        }
        NightmareSavedData nightmares = NightmareSavedData.get(level);
        for (NightmareDefs n : NightmareDefs.values()) {
            if (!nightmares.isBuilt(n)) continue;
            BlockPos origin = n.origin();
            if (minX <= origin.getX() + NightmareTuning.SLOT_STRIDE && maxX >= origin.getX() - 64
                    && minZ <= origin.getZ() + 128 && maxZ >= origin.getZ() - 128) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ захват

    private static void capture(Session s, int budget) {
        while (budget-- > 0 && !s.captureDone()) {
            int section = s.captured++;
            int sx = section % s.sectionsX;
            int sy = (section / s.sectionsX) % s.sectionsY;
            int sz = section / (s.sectionsX * s.sectionsY);
            int x0 = s.minX + sx * SECTION;
            int y0 = s.minY + sy * SECTION;
            int z0 = s.minZ + sz * SECTION;
            int x1 = Math.min(x0 + SECTION - 1, s.maxX);
            int y1 = Math.min(y0 + SECTION - 1, s.maxY);
            int z1 = Math.min(z0 + SECTION - 1, s.maxZ);

            for (int cx = x0 >> 4; cx <= x1 >> 4; cx++)
                for (int cz = z0 >> 4; cz <= z1 >> 4; cz++)
                    s.source.getChunk(cx, cz, ChunkStatus.FULL, true);

            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    for (int x = x0; x <= x1; x++) {
                        BlockPos p = new BlockPos(x, y, z);
                        int index = index(s, x, y, z);
                        BlockState state = s.source.getBlockState(p);
                        s.states[index] = state;
                        if (!state.hasBlockEntity()) continue;
                        BlockEntity be = s.source.getBlockEntity(p);
                        // Содержимое контейнеров не копируем: иначе вещи можно унести из сна в реальность.
                        if (be != null && !(be instanceof Container))
                            s.blockEntities.put(index, be.saveWithId(s.source.registryAccess()));
                    }
                }
            }
        }
    }

    private static int index(Session s, int x, int y, int z) {
        int lx = x - s.minX;
        int ly = y - s.minY;
        int lz = z - s.minZ;
        return (ly * s.sizeZ + lz) * s.sizeX + lx;
    }

    // ------------------------------------------------------------------ перенос

    private static void apply(Session s, int budget) {
        if (!s.captureDone()) return;
        while (budget-- > 0 && !s.applyDone()) {
            int section = s.applied++;
            int sx = section % s.sectionsX;
            int sy = (section / s.sectionsX) % s.sectionsY;
            int sz = section / (s.sectionsX * s.sectionsY);
            int x0 = s.minX + sx * SECTION;
            int y0 = s.minY + sy * SECTION;
            int z0 = s.minZ + sz * SECTION;
            int x1 = Math.min(x0 + SECTION - 1, s.maxX);
            int y1 = Math.min(y0 + SECTION - 1, s.maxY);
            int z1 = Math.min(z0 + SECTION - 1, s.maxZ);

            for (int cx = x0 >> 4; cx <= x1 >> 4; cx++)
                for (int cz = z0 >> 4; cz <= z1 >> 4; cz++)
                    s.dream.getChunk(cx, cz, ChunkStatus.FULL, true);

            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    for (int x = x0; x <= x1; x++) applyCell(s, x, y, z);
                }
            }
        }
    }

    private static void applyCell(Session s, int x, int y, int z) {
        int srcIndex = index(s, x, y, z);
        BlockState state = s.states[srcIndex];
        if (state == null) return;

        BlockPos pos = new BlockPos(x, y, z);
        if (s.dream.getBlockState(pos) != state) s.dream.setBlock(pos, state, FLAGS);

        if (!state.hasBlockEntity()) return;
        CompoundTag tag = s.blockEntities.get(srcIndex);
        BlockEntity be = s.dream.getBlockEntity(pos);
        if (be == null) return;
        if (tag != null) {
            be.loadWithComponents(tag, s.dream.registryAccess());
            be.setChanged();
        } else if (be instanceof Container container) {
            container.clearContent();
        }
    }
}
