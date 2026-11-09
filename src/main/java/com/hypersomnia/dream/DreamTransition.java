package com.hypersomnia.dream;

import com.hypersomnia.FatigueData;
import com.hypersomnia.FatigueTuning;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.SleepPenalty;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
        if (nightmare == NightmareDefs.SHAFT) {
            if (server == null || player.getData(DreamAttachments.DREAM).isInDream()) return false;
            ServerLevel dream = server.getLevel(DreamDimension.LEVEL);
            if (dream == null) return false;
            var storage = com.hypersomnia.nightmare.ShaftStorage.get(dream);
            int previous = storage.lane(player.getUUID());
            int lane = -1;
            for (int i = 0; i < com.hypersomnia.nightmare.NightmareTuning.SHAFT_LANES; i++) {
                lane = storage.reserve(player.getUUID());
                if (lane < 0) return false;
                if (com.hypersomnia.nightmare.ShaftBuilder.ensureBuilt(dream, lane)) break;
                if (previous >= 0) return false;
                storage.release(player.getUUID());
                lane = -1;
            }
            if (lane < 0) return false;
            boolean entered = begin(player, nightmare);
            if (!entered && previous < 0) storage.release(player.getUUID());
            return entered;
        }
        if (nightmare == NightmareDefs.ROUNDS || nightmare == NightmareDefs.SESSION_47) {
            if (server == null || !NightmareBuilder.ensureBuilt(server, nightmare)) return false;
            return begin(player, nightmare);
        }
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

        // [новое] Дом в мире снов: доработать зеркало до выбора безопасной точки, чтобы findSafe
        // уже видел скопированные пол и кровать, а не только рельеф под ними.
        if (nightmare == null) DreamMirror.finish(player);

        DreamData data = player.getData(DreamAttachments.DREAM);
        ServerLevel from = player.serverLevel();
        if (data.isInDream() || from == dream) {
            // Поимка внутри обычного сна: сохраняем исходную точку пробуждения и переходим в кошмар.
            if (nightmare == null || !data.isInDream() || from != dream || !data.getKind().isEmpty()) return false;
            BlockPos spawn = nightmare.spawn();
            player.teleportTo(dream, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, nightmare.yaw(), 0f);
            data.setKind(nightmare.id());
            data.resetWardLoops();
            data.setTicksLeft(nightmare.durationTicks());
            return true;
        }

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
        } else if (nightmare == NightmareDefs.SHAFT) {
            int lane = com.hypersomnia.nightmare.ShaftStorage.get(dream).lane(player.getUUID());
            target = lane < 0 ? null : com.hypersomnia.nightmare.ShaftBuilder.spawn(lane, 6);
        } else {
            target = nightmare.spawn();
        }

        if (target == null) {
            // Не перемещаем игрока в непроверенную точку и не оставляем состояние фиктивного сна.
            data.setInDream(false);
            data.setTicksLeft(0);
            return false;
        }
        player.teleportTo(dream, target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                nightmare == null ? player.getYRot() : nightmare.yaw(), nightmare == null ? player.getXRot() : 0f);
        // Другой мод может отменить смену измерения: не оставляем фиктивный сон.
        if (player.serverLevel() != dream) {
            data.setInDream(false);
            data.setTicksLeft(0);
            return false;
        }
        if (nightmare == null) {
            player.getData(ModAttachments.FATIGUE).setSyringeCollapse(false);
            com.hypersomnia.server.ObserverNotesHandler.onEnterDream(player, dream);
            DreamClock.start(player, dream);
            // След осколка и личная надпись — только в мире сна, у кровати зеркального дома [новое]
            com.hypersomnia.server.TraceHandler.onDreamEnter(player, dream, target);
        }
        if (nightmare == NightmareDefs.SESSION_47)
            player.displayClientMessage(Component.translatable("hypersomnia.nightmare.session47.begin"), false);
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
        boolean recovery = target == null || target.dimension().equals(DreamDimension.LEVEL);
        if (recovery) target = server.overworld();
        BlockPos original = BlockPos.containing(data.getReturnX(), data.getReturnY(), data.getReturnZ());
        BlockPos safe = findSafe(target, recovery ? target.getSharedSpawnPos() : original);
        if (safe == null && !recovery) {
            // Запасной спавн тоже проходит те же проверки, а не принимается на веру.
            safe = findSafe(target, target.getSharedSpawnPos());
            recovery = safe != null;
        }
        if (safe == null) {
            player.displayClientMessage(Component.translatable("hypersomnia.dream.wake_failed"), false);
            return false;
        }

        // Сначала убедимся, что телепорт действительно сменил измерение. Если переход
        // отменён другим модом, сохраняем сон и точку возврата для повторной попытки.
        if (player.isSleeping()) player.stopSleepInBed(true, true);
        player.teleportTo(target, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                data.getReturnYaw(), data.getReturnPitch());
        if (player.serverLevel() != target) {
            player.displayClientMessage(Component.translatable("hypersomnia.dream.wake_failed"), false);
            return false;
        }
        DreamMirror.abort(player);
        DreamClock.clear(player);
        if (recovery) player.displayClientMessage(Component.translatable("hypersomnia.dream.wake_fallback"), false);

        boolean shaft = NightmareDefs.SHAFT.id().equals(data.getKind());
        boolean completedSession = NightmareDefs.SESSION_47.id().equals(data.getKind()) && data.getTicksLeft() == 0;
        if (shaft) com.hypersomnia.nightmare.ShaftHandler.release(player);
        data.setInDream(false);
        data.setTicksLeft(0);
        if (completedSession && (player.getData(ModAttachments.OBSERVER).getMemories() & (1 << 5)) != 0)
            player.getData(ModAttachments.OBSERVER).markSession47Seen();
        FatigueData fatigue = player.getData(ModAttachments.FATIGUE);
        if (shaft) fatigue.setCollapseGraceTicks(com.hypersomnia.nightmare.NightmareTuning.SHAFT_COLLAPSE_GRACE_TICKS);
        else {
            fatigue.setFatigue(Math.min(fatigue.getFatigue(), SleepPenalty.restFatigue(fatigue)));
            player.getData(ModAttachments.OBSERVER).restExposure();
        }
        fatigue.markSynced(-1, false); // клиент получит значение заново: шкала не останется на старом

        // Ночь, в которую игрок уснул, проходит: он просыпается утром, как после обычного сна
        if (DreamTuning.WAKE_SKIPS_NIGHT && target.dimension() == Level.OVERWORLD) skipNight(target);

        // Вещи, которые забрал осколок в погоне, возвращаются у кровати (после финала — все сразу, без следов)
        com.hypersomnia.chase.ChaseHandler.onWake(player, target,
                recovery ? safe : original);
        // После финала события мода выключены: ни инерции, ни записок, ни следов
        if (com.hypersomnia.finale.FinaleLogic.completed(player)) return true;
        SleepPenalty.applyInertia(player); // сонная инерция после пробуждения
        // Записка по-прежнему появляется после пробуждения; следы осколка в реальном мире больше не ставятся.
        com.hypersomnia.server.ObserverNotesHandler.onWake(player, target);
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
    /** Возвращает проверенного кандидата либо null, если подходящая точка не найдена. */
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

        // 2) Проверенная поверхность той же колонки, затем соседние уже загруженные колонки.
        // Высотная карта сама по себе не гарантирует сухое или свободное место.
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos fallback = new BlockPos(x, Math.max(minY, Math.min(maxY, surface)), z);
        if (standable(level, fallback)) return fallback;
        for (int r = 1; r <= DreamTuning.SAFE_SURFACE_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int nx = x + dx, nz = z + dz;
                    if (!level.hasChunkAt(new BlockPos(nx, oy, nz))) continue;
                    int sy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx, nz);
                    BlockPos candidate = new BlockPos(nx, Math.max(minY, Math.min(maxY, sy)), nz);
                    if (standable(level, candidate)) return candidate;
                }
            }
        }
        return null;
    }

    private static boolean standable(ServerLevel level, BlockPos feet) {
        if (!level.getWorldBorder().isWithinBounds(feet)) return false;
        BlockPos below = feet.below();
        BlockState floor = level.getBlockState(below);
        return passable(level, feet)
                && passable(level, feet.above())
                && !hazard(floor)
                && !floor.getCollisionShape(level, below).isEmpty()
                && level.getFluidState(below).isEmpty();
    }

    private static boolean passable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !hazard(state) && state.getCollisionShape(level, pos).isEmpty()
                && level.getFluidState(pos).isEmpty();
    }

    /** Неполный список типичных опасностей: в неизвестных модовых блоках безопасность не гарантируется. */
    private static boolean hazard(BlockState state) {
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW);
    }
}
