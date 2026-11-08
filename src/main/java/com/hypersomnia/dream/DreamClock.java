package com.hypersomnia.dream;

import com.hypersomnia.FatigueData;
import com.hypersomnia.FatigueTuning;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.ModBlocks;
import com.hypersomnia.ModSounds;
import com.hypersomnia.chase.ChaseHandler;
import com.hypersomnia.server.ObserverNotesHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;

/**
 * [новое] Напольные часы в мире снов.
 *
 * <p>При входе в обычный сон сервер ставит двухблочные часы в случайном месте в кольце
 * вокруг точки появления. Часы тикают, и по звуку их можно найти; ПКМ по любой половине
 * будит игрока. Если за {@link DreamClockTuning#FIND_TICKS} часы не нашли, они бьют
 * ({@link DreamClockTuning#STRIKES_BY_STAGE}: чем выше стадия, тем больше ударов) и
 * начинается погоня. Поимка будит игрока, оставляет 50% усталости и выдаёт случайную
 * ещё не найденную записку.
 *
 * <p>Состояние часов живёт в {@link DreamData} и сохраняется: перезаход и перезапуск
 * сервера не теряют ни часы, ни таймер. Блок удаляется при пробуждении.
 */
public final class DreamClock {
    private DreamClock() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    // ------------------------------------------------------------------ установка

    /** Вызывать после успешного телепорта в мир снов (обычный сон, не кошмар). */
    static void start(ServerPlayer player, ServerLevel dream) {
        DreamData data = player.getData(DreamAttachments.DREAM);
        if (!data.isInDream() || !data.getKind().isEmpty()) return;

        removeBlock(player, data);
        data.resetClock();

        RandomSource rnd = player.getRandom();
        BlockPos spot = findSpot(dream, player.blockPosition(), rnd);
        if (spot == null) {
            LOGGER.info("DreamClock: no free floor near {}, clock skipped", player.blockPosition());
            return;
        }

        BlockState lower = ModBlocks.DREAM_CLOCK.get().defaultBlockState()
                .setValue(DreamClockBlock.FACING, HORIZONTAL[rnd.nextInt(HORIZONTAL.length)])
                .setValue(DreamClockBlock.HALF, DoubleBlockHalf.LOWER);
        dream.setBlock(spot, lower, Block.UPDATE_ALL);
        dream.setBlock(spot.above(), lower.setValue(DreamClockBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);

        data.startClock(spot.getX(), spot.getY(), spot.getZ(), DreamClockTuning.FIND_TICKS);
        LOGGER.info("DreamClock: placed at {} (find {} ticks, stage {})",
                spot, DreamClockTuning.FIND_TICKS, player.getData(ModAttachments.OBSERVER).stage());
    }

    /**
     * Место под часы: кольцо вокруг точки входа, ближайший по высоте пол с двумя
     * свободными клетками. Листья, вода и тонкие уступы не подходят.
     */
    private static BlockPos findSpot(ServerLevel level, BlockPos around, RandomSource rnd) {
        for (int i = 0; i < DreamClockTuning.PLACE_TRIES; i++) {
            double angle = rnd.nextDouble() * Math.PI * 2.0;
            double dist = Mth.lerp(rnd.nextDouble(), DreamClockTuning.PLACE_DIST_MIN, DreamClockTuning.PLACE_DIST_MAX);
            int x = around.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = around.getZ() + (int) Math.round(Math.sin(angle) * dist);
            level.getChunk(x >> 4, z >> 4);

            // Сначала та же высота, затем вверх/вниз по очереди: часы остаются на этаже игрока.
            for (int d = 0; d <= Math.max(DreamClockTuning.PLACE_UP, DreamClockTuning.PLACE_DOWN); d++) {
                for (int sign = 1; sign >= -1; sign -= 2) {
                    if (d == 0 && sign < 0) continue;
                    int dy = d * sign;
                    if (dy > DreamClockTuning.PLACE_UP || dy < -DreamClockTuning.PLACE_DOWN) continue;
                    BlockPos feet = new BlockPos(x, around.getY() + dy, z);
                    if (floorSpot(level, feet)) return feet;
                }
            }
        }
        return null;
    }

    private static boolean floorSpot(ServerLevel level, BlockPos feet) {
        if (!level.isLoaded(feet) || !level.isLoaded(feet.below()) || !level.isLoaded(feet.above())) return false;
        BlockPos below = feet.below();
        BlockState atFeet = level.getBlockState(feet);
        BlockState atHead = level.getBlockState(feet.above());
        // Только пустые клетки: часы не должны затирать чужие постройки и растения.
        if (!atFeet.isAir() || !atHead.isAir()) return false;
        if (level.getBlockState(below).is(BlockTags.LEAVES)) return false;
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return false;
        return level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty();
    }

    // ------------------------------------------------------------------ тик

    static void tick(ServerPlayer player) {
        DreamData data = player.getData(DreamAttachments.DREAM);
        boolean dreamDim = player.level().dimension().equals(DreamDimension.LEVEL);
        if (!data.isInDream() || !dreamDim || !data.getKind().isEmpty()) {
            if (data.clockActive()) clear(player);
            return;
        }
        if (!data.clockActive()) return;

        // Тиканье идёт всё время, пока часы не нашли и погоня не началась.
        // Фаза — по игровому времени: клиентская анимация маятника считается от него же,
        // поэтому щелчок совпадает с крайней точкой.
        long now = player.level().getGameTime();
        if (!data.clockChase() && now % DreamClockTuning.TICK_EVERY_TICKS == 0) playTick(player, data, now);

        if (data.clockTicks() > 0) {
            int left = data.clockTicks() - 1;
            data.setClockTicks(left);
            if (left == 0) {
                data.setClockStrikes(DreamClockTuning.STRIKES_BY_STAGE[strikeIndex(player)]);
                data.setClockNext(DreamClockTuning.STRIKE_FIRST_TICKS);
                LOGGER.info("DreamClock: {} strikes for {}", data.clockStrikes(), player.getGameProfile().getName());
            }
            return;
        }

        if (data.clockStrikes() > 0) {
            int next = data.clockNext() - 1;
            if (next <= 0) {
                playStrike(player, data);
                data.setClockStrikes(data.clockStrikes() - 1);
                data.setClockNext(data.clockStrikes() > 0
                        ? DreamClockTuning.STRIKE_EVERY_TICKS
                        : DreamClockTuning.CHASE_DELAY_TICKS);
            } else {
                data.setClockNext(next);
            }
            return;
        }

        if (!data.clockChase()) {
            int next = data.clockNext() - 1;
            if (next <= 0) {
                int result = ChaseHandler.startDreamClock(player);
                if (result == 0) {
                    data.setClockChase(true);
                    LOGGER.info("DreamClock: chase started for {}", player.getGameProfile().getName());
                } else {
                    data.setClockNext(DreamClockTuning.CHASE_RETRY_TICKS);
                    LOGGER.info("DreamClock: chase rejected ({}) for {}", result, player.getGameProfile().getName());
                }
            } else {
                data.setClockNext(next);
            }
        }
    }

    private static int strikeIndex(ServerPlayer player) {
        int stage = player.getData(ModAttachments.OBSERVER).stage();
        return Mth.clamp(stage, 0, DreamClockTuning.STRIKES_BY_STAGE.length - 1);
    }

    private static void playTick(ServerPlayer player, DreamData data, long now) {
        ServerLevel level = player.serverLevel();
        boolean high = (now / DreamClockTuning.TICK_EVERY_TICKS) % 2 == 0;
        level.playSound(null, data.clockX() + 0.5, data.clockY() + 1.0, data.clockZ() + 0.5,
                ModSounds.CLOCK_TICK.get(), SoundSource.BLOCKS, DreamClockTuning.TICK_VOLUME,
                high ? DreamClockTuning.TICK_PITCH_HIGH : DreamClockTuning.TICK_PITCH_LOW);
    }

    private static void playStrike(ServerPlayer player, DreamData data) {
        ServerLevel level = player.serverLevel();
        level.playSound(null, data.clockX() + 0.5, data.clockY() + 1.0, data.clockZ() + 0.5,
                ModSounds.CLOCK_STRIKE.get(), SoundSource.BLOCKS, DreamClockTuning.STRIKE_VOLUME, 1.0f);
    }

    // ------------------------------------------------------------------ действия

    /** ПКМ по часам: игрок нашёл их и просыпается. */
    static void found(ServerPlayer player) {
        DreamData data = player.getData(DreamAttachments.DREAM);
        if (!data.isInDream() || !data.clockActive() || !data.getKind().isEmpty()) return;
        if (!player.level().dimension().equals(DreamDimension.LEVEL)) return;
        DreamTransition.exit(player);
    }

    /** Поимка погоней из часов: пробуждение, 50% усталости и случайная ненайденная записка. */
    public static void onCaught(ServerPlayer player) {
        DreamData data = player.getData(DreamAttachments.DREAM);
        if (!data.isInDream() || !player.level().dimension().equals(DreamDimension.LEVEL)) return;

        // Не даём утренней выдаче добавить ещё одну находку к записке за поимку.
        player.getData(ModAttachments.OBSERVER).setNoteThisSleep(true);
        boolean woke = DreamTransition.exit(player);
        if (!woke) return;

        FatigueData fatigue = player.getData(ModAttachments.FATIGUE);
        fatigue.setFatigue(Math.round(FatigueTuning.MAX_FATIGUE * DreamClockTuning.CATCH_FATIGUE_RATIO));
        fatigue.markSynced(-1, false);

        BlockPos origin = player.blockPosition();
        ObserverNotesHandler.giveRandomNote(player, player.serverLevel(), origin);
    }

    // ------------------------------------------------------------------ очистка

    /** Убрать часы из мира и сбросить их состояние (пробуждение, выход из сна, смена измерения). */
    static void clear(ServerPlayer player) {
        DreamData data = player.getData(DreamAttachments.DREAM);
        removeBlock(player, data);
        data.resetClock();
    }

    private static void removeBlock(ServerPlayer player, DreamData data) {
        if (!data.clockActive()) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ServerLevel dream = server.getLevel(DreamDimension.LEVEL);
        if (dream == null) return;

        BlockPos base = new BlockPos(data.clockX(), data.clockY(), data.clockZ());
        for (int dy = -1; dy <= 1; dy++) {
            BlockPos p = base.offset(0, dy, 0);
            if (dream.getBlockState(p).is(ModBlocks.DREAM_CLOCK.get())) {
                dream.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    static String status(ServerPlayer player) {
        DreamData data = player.getData(DreamAttachments.DREAM);
        if (!data.clockActive()) return "none";
        return "at " + data.clockX() + " " + data.clockY() + " " + data.clockZ()
                + ", find=" + data.clockTicks()
                + ", strikes=" + data.clockStrikes()
                + ", next=" + data.clockNext()
                + (data.clockChase() ? ", chase" : "");
    }

    /** Отладочная команда: поставить часы рядом с текущей позицией, не дожидаясь сна. */
    static boolean placeNow(ServerPlayer player) {
        if (player.level().dimension() != DreamDimension.LEVEL) return false;
        DreamData data = player.getData(DreamAttachments.DREAM);
        if (!data.isInDream() || !data.getKind().isEmpty()) return false;
        removeBlock(player, data);
        data.resetClock();
        BlockPos spot = findSpot(player.serverLevel(), player.blockPosition(), player.getRandom());
        if (spot == null) return false;
        BlockState lower = ModBlocks.DREAM_CLOCK.get().defaultBlockState()
                .setValue(DreamClockBlock.FACING, HORIZONTAL[player.getRandom().nextInt(HORIZONTAL.length)])
                .setValue(DreamClockBlock.HALF, DoubleBlockHalf.LOWER);
        player.serverLevel().setBlock(spot, lower, Block.UPDATE_ALL);
        player.serverLevel().setBlock(spot.above(), lower.setValue(DreamClockBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        data.startClock(spot.getX(), spot.getY(), spot.getZ(), DreamClockTuning.FIND_TICKS);
        return true;
    }

    /** Отладка: сразу перевести часы к ударам. */
    static void skipToStrikes(ServerPlayer player) {
        DreamData data = player.getData(DreamAttachments.DREAM);
        if (!data.clockActive()) return;
        data.setClockTicks(0);
        data.setClockStrikes(DreamClockTuning.STRIKES_BY_STAGE[strikeIndex(player)]);
        data.setClockNext(DreamClockTuning.STRIKE_FIRST_TICKS);
    }

    /** Отладка: сразу начать погоню. */
    static void forceChase(ServerPlayer player) {
        DreamData data = player.getData(DreamAttachments.DREAM);
        if (!data.clockActive()) return;
        data.setClockTicks(0);
        data.setClockStrikes(0);
        data.setClockNext(0);
        data.setClockChase(false);
    }

    /** Уровень сна для команд и очистки; null — измерение не загружено. */
    static Level level(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server == null ? null : server.getLevel(DreamDimension.LEVEL);
    }
}
