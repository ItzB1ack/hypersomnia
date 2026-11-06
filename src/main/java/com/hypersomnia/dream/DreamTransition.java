package com.hypersomnia.dream;

import com.hypersomnia.FatigueData;
import com.hypersomnia.FatigueTuning;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.SleepPenalty;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import com.hypersomnia.scene.SceneAttachments;
import com.hypersomnia.nightmare.NightmareDefs;
import com.hypersomnia.nightmare.NightmareBuilder;

/** Вход в мир снов и выход из него. Вызывать только на сервере. */
public final class DreamTransition {
    private DreamTransition() {}

    /** Обычный сон: переносим в мир снов, запоминая, куда вернуть. false, если вход невозможен. */
    public static boolean enter(ServerPlayer player) {
        return begin(player, null);
    }

    /** Кошмар: заранее построенное место и особые правила. Если место не построено, обычный сон. */
    public static boolean enterNightmare(ServerPlayer player, NightmareDefs nightmare) {
        MinecraftServer server = player.getServer();
        if (server == null || !NightmareBuilder.ensureBuilt(server, nightmare)) return enter(player);
        return begin(player, nightmare);
    }

    private static boolean begin(ServerPlayer player, NightmareDefs nightmare) {
        // Пока идёт сцена в кровати, в мир снов не уходим
        if (player.getData(SceneAttachments.SCENE).getHold() > 0) return false;
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        ServerLevel dream = server.getLevel(DreamDimension.LEVEL);
        if (dream == null) return false;

        DreamData data = player.getData(DreamAttachments.DREAM);
        ServerLevel from = player.serverLevel();
        if (data.isInDream() || from == dream) return false;

        data.setReturn(from.dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        data.setInDream(true);
        data.setKind(nightmare == null ? "" : nightmare.id());
        data.setTicksLeft(nightmare == null ? DreamTuning.DURATION_TICKS : nightmare.durationTicks());

        BlockPos target;
        if (nightmare == null) {
            // Копия мира совпадает по рельефу, поэтому берём те же координаты (для Незера пересчитываем)
            boolean nether = from.dimension() == Level.NETHER;
            double scale = nether ? DreamTuning.NETHER_SCALE : 1.0;
            int y = nether ? 64 : player.getBlockY();
            target = findSafe(dream, BlockPos.containing(player.getX() * scale, y, player.getZ() * scale));
        } else {
            target = nightmare.spawn();
        }

        player.teleportTo(dream, target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                nightmare == null ? player.getYRot() : nightmare.yaw(), nightmare == null ? player.getXRot() : 0f);
        if (nightmare == null) com.hypersomnia.server.ObserverNotesHandler.onEnterDream(player, dream);
        return true;
    }

    /** Пробуждение: возвращаем туда, где игрок уснул, и снимаем усталость до уровня после сна. */
    public static boolean exit(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;

        DreamData data = player.getData(DreamAttachments.DREAM);
        if (!data.isInDream()) return false;

        ServerLevel target = null;
        ResourceLocation dimId = ResourceLocation.tryParse(data.getReturnDim());
        if (dimId != null) target = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
        if (target == null) target = server.overworld();

        BlockPos safe = findSafe(target, BlockPos.containing(data.getReturnX(), data.getReturnY(), data.getReturnZ()));

        data.setInDream(false);
        data.setTicksLeft(0);
        FatigueData fatigue = player.getData(ModAttachments.FATIGUE);
        fatigue.setFatigue(Math.min(fatigue.getFatigue(), SleepPenalty.restFatigue(fatigue)));
        fatigue.markSynced(-1, false); // клиент получит значение заново: шкала не останется на старом

        // Ночь, в которую игрок уснул, проходит: он просыпается утром, как после обычного сна
        if (DreamTuning.WAKE_SKIPS_NIGHT && target.dimension() == Level.OVERWORLD) skipNight(target);

        player.teleportTo(target, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                data.getReturnYaw(), data.getReturnPitch());
        // Вещи, которые забрал осколок в погоне, возвращаются у кровати (после финала — все сразу, без следов)
        com.hypersomnia.chase.ChaseHandler.onWake(player, target,
                BlockPos.containing(data.getReturnX(), data.getReturnY(), data.getReturnZ()));
        // После финала события мода выключены: ни инерции, ни записок, ни следов
        if (com.hypersomnia.finale.FinaleLogic.completed(player)) return true;
        SleepPenalty.applyInertia(player); // сонная инерция после пробуждения
        // Один след осколка за сон: если в этот сон была записка, следа нет
        if (!com.hypersomnia.server.ObserverNotesHandler.onWake(player, target)) {
            com.hypersomnia.server.TraceHandler.onWake(player, target,
                    BlockPos.containing(data.getReturnX(), data.getReturnY(), data.getReturnZ()));
        }
        return true;
    }

    /** Как после ванильного сна: время переходит на утро. Если ночь уже прошла (сон длиннее остатка ночи), ничего не трогаем. */
    private static void skipNight(ServerLevel level) {
        long time = level.getDayTime();
        long timeOfDay = time % 24000L;
        if (timeOfDay < DreamTuning.NIGHT_STARTS_AT) return;
        level.setDayTime(time - timeOfDay + 24000L + DreamTuning.WAKE_TIME_OF_DAY);
    }

    /** Игрок попал в мир снов не через enter() (команда /tp и т.п.): считаем это сном, вернём в Верхний мир. */
    static void adoptUnexpectedArrival(ServerPlayer player, DreamData data) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        BlockPos spawn = server.overworld().getSharedSpawnPos();
        data.setReturn(Level.OVERWORLD.location().toString(), spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0f, 0f);
        data.setInDream(true);
        data.setTicksLeft(DreamTuning.DURATION_TICKS);
    }

    // ------------------------------------------------------------------
    // Безопасная точка: постройки игрока в копии отсутствуют, так что исходная клетка может оказаться в камне
    // ------------------------------------------------------------------
    public static BlockPos findSafe(ServerLevel level, BlockPos origin) {
        int minY = level.getMinBuildHeight() + 1;
        int maxY = level.getMaxBuildHeight() - 3;
        int x = origin.getX();
        int z = origin.getZ();
        int oy = Math.max(minY, Math.min(maxY, origin.getY()));

        // 1) Рядом с исходной высотой: сначала сама клетка, затем вверх и вниз по очереди
        for (int d = 0; d <= DreamTuning.SAFE_SEARCH_RANGE; d++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                if (d == 0 && sign == -1) continue;
                int y = oy + sign * d;
                if (y < minY || y > maxY) continue;
                if (standable(level, new BlockPos(x, y, z))) return new BlockPos(x, y, z);
            }
        }

        // 2) Поверхность в этой колонке
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, Math.max(minY, Math.min(maxY, surface)), z);
    }

    private static boolean standable(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        return passable(level, feet)
                && passable(level, feet.above())
                && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                && level.getFluidState(below).isEmpty();
    }

    private static boolean passable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getFluidState(pos).isEmpty();
    }
}
