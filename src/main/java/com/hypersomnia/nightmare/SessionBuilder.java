package com.hypersomnia.nightmare;

import com.hypersomnia.ModBlocks;
import com.hypersomnia.lab.LabSigns;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.slf4j.Logger;

/**
 * Зал сеанса 47 [новое, переработка]: комната наблюдения. Зал (z 0..7) смотрит через тёмное окно в закрытую
 * комнату сеанса (z 9..12). Три станции (пульт, переговорное, панель двери) обходятся по кругу; генерация
 * не перезаписывает чужие блоки, а свою прежнюю версию очищает только когда внутри никого нет.
 */
public final class SessionBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FLAGS = NightmareLayout.FLAGS;
    /** Размеры оболочки: dx -1..MAX_X, dz -1..MAX_Z, dy 0..5. */
    private static final int MAX_X = 21, MAX_Z = 13, MAX_Y = 5;
    private SessionBuilder() {}
    private static int x() { return NightmareDefs.SESSION_47.origin().getX(); }
    private static int y() { return NightmareTuning.SESSION_Y; }
    private static int z() { return NightmareDefs.SESSION_47.origin().getZ(); }
    public static BlockPos spawn() { return new BlockPos(x() + 2, y() + 1, z() + 4); }
    /** Станции: 0 пульт (стол), 1 переговорное, 2 панель двери. */
    public static BlockPos station(int k) {
        return switch (k) {
            case 0 -> new BlockPos(x() + 6, y() + 1, z() + 7);
            case 1 -> new BlockPos(x() + 12, y() + 2, z());
            default -> new BlockPos(x() + 20, y() + 2, z() + 2);
        };
    }
    public static BlockPos console() { return station(0); }
    /** Номер станции по нажатому блоку или -1; пульт отзывается и на стол, и на монитор над ним. */
    public static int stationAt(BlockPos p) {
        if (p.equals(station(0)) || p.equals(station(0).above())) return 0;
        if (p.equals(station(1))) return 1;
        if (p.equals(station(2))) return 2;
        return -1;
    }
    public static boolean containsBuilt(ServerLevel level, BlockPos p) {
        return NightmareSavedData.get(level).isBuilt(NightmareDefs.SESSION_47)
                && p.getX() >= x() - 1 && p.getX() <= x() + MAX_X
                && p.getZ() >= z() - 1 && p.getZ() <= z() + MAX_Z
                && p.getY() >= y() && p.getY() <= y() + MAX_Y;
    }
    private static boolean set(ServerLevel level, int dx, int dy, int dz, BlockState state) {
        return level.setBlock(new BlockPos(x() + dx, y() + dy, z() + dz), state, FLAGS);
    }
    private static void light(ServerLevel level, int dx, int dz, int lvl) {
        set(level, dx, 4, dz, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, lvl));
        set(level, dx, 5, dz, Blocks.WHITE_STAINED_GLASS.defaultBlockState());
    }
    public static boolean ensureBuilt(ServerLevel level) {
        NightmareSavedData data = NightmareSavedData.get(level);
        if (data.isBuilt(NightmareDefs.SESSION_47) && data.layout(NightmareDefs.SESSION_47) != NightmareTuning.SESSION_LAYOUT) {
            if (NightmareLayout.anyoneInside(level.getServer(), NightmareDefs.SESSION_47.id())) return true;
            NightmareLayout.clear(level, x() - 1, y(), z() - 1, x() + MAX_X, y() + MAX_Y, z() + MAX_Z);
            data.setBuilt(NightmareDefs.SESSION_47, false);
            data.setSessionBuildFailed(false);
        }
        if (data.isBuilt(NightmareDefs.SESSION_47)) return true;
        if (data.sessionBuildFailed()) return false;
        if (y() < level.getMinBuildHeight() || y() + MAX_Y >= level.getMaxBuildHeight()) return false;
        for (int dx = -1; dx <= MAX_X; dx++) for (int dz = -1; dz <= MAX_Z; dz++) for (int dy = 0; dy <= MAX_Y; dy++) {
            BlockPos pos = new BlockPos(x() + dx, y() + dy, z() + dz);
            if (!level.getWorldBorder().isWithinBounds(pos) || !level.getBlockState(pos).isAir()) {
                data.setSessionBuildFailed(true);
                LOGGER.warn("Session 47 location is occupied or outside the world border; no blocks were changed");
                return false;
            }
        }
        try {
            // Оболочка: шахматный пол, тёмный цоколь, светлые стены, потолок.
            for (int dx = -1; dx <= MAX_X; dx++) for (int dz = -1; dz <= MAX_Z; dz++) for (int dy = 0; dy <= MAX_Y; dy++) {
                BlockState state = null;
                boolean wall = dx == -1 || dx == MAX_X || dz == -1 || dz == MAX_Z;
                boolean partition = dz == 8 && dx >= 0 && dx < MAX_X;
                if (dy == 0) state = ((dx + dz) & 1) == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState();
                else if (dy == MAX_Y) state = Blocks.SMOOTH_STONE.defaultBlockState();
                else if (wall || partition) state = dy == 1 ? Blocks.GRAY_CONCRETE.defaultBlockState() : Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
                if (state != null && !set(level, dx, dy, dz, state)) throw new IllegalStateException("Could not place session shell");
            }
            // Смотровое окно в комнату сеанса: тонированное стекло, за ним темно.
            for (int dx = 5; dx <= 17; dx++) for (int dy = 2; dy <= 3; dy++) set(level, dx, dy, 8, Blocks.TINTED_GLASS.defaultBlockState());
            // Свет: слабые панели в зале, комната сеанса не освещается.
            light(level, 4, 3, 6); light(level, 10, 4, 5); light(level, 16, 3, 5); light(level, 20, 4, 4);
            // Комната сеанса: пустая койка и монитор (светится слабо). Без фигур и лиц.
            BlockState bed = com.hypersomnia.nightmare.WardDecorBlocks.BED.defaultBlockState()
                    .setValue(com.hypersomnia.nightmare.WardBedBlock.FACING, Direction.EAST);
            set(level, 10, 1, 11, bed.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
            set(level, 11, 1, 11, bed.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
            set(level, 9, 1, 11, com.hypersomnia.nightmare.WardDecorBlocks.MONITOR.defaultBlockState()
                    .setValue(com.hypersomnia.nightmare.WardMonitorBlock.FACING, Direction.EAST));
            // Пять пустых кресел лицом к окну; третье повёрнуто в сторону [новое].
            for (int i = 0; i < 5; i++) {
                set(level, 8 + i * 2, 1, 4, ModBlocks.CHAIR.get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, i == 2 ? Direction.WEST : Direction.SOUTH));
            }
            // Станция 0: пульт у перегородки.
            set(level, 6, 1, 7, ModBlocks.DESK.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
            set(level, 6, 2, 7, com.hypersomnia.nightmare.WardDecorBlocks.MONITOR.defaultBlockState()
                    .setValue(com.hypersomnia.nightmare.WardMonitorBlock.FACING, Direction.NORTH));
            LabSigns.wall(level, new BlockPos(x() + 6, y() + 4, z() + 7), Direction.NORTH, "CONSOLE", "ATTEMPT RESPONSE");
            // Станция 1: переговорное на южной стене.
            set(level, 12, 2, 0, ModBlocks.KEYPAD.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
            LabSigns.wall(level, new BlockPos(x() + 12, y() + 3, z()), Direction.SOUTH, "INTERCOM");
            // Станция 2: закрытая железная дверь комнаты сеанса и её панель на восточной стене.
            BlockState door = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST);
            set(level, 21, 1, 3, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            set(level, 21, 2, 3, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            set(level, 20, 2, 2, ModBlocks.KEYPAD.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
            LabSigns.wall(level, new BlockPos(x() + 20, y() + 2, z() + 4), Direction.WEST, "SESSION ROOM");
            LabSigns.wall(level, new BlockPos(x() + 11, y() + 4, z() + 7), Direction.NORTH, "SESSION 47", "INCIDENT PROTOCOL");
            data.setBuilt(NightmareDefs.SESSION_47, true);
            data.setLayout(NightmareDefs.SESSION_47, NightmareTuning.SESSION_LAYOUT);
            return true;
        } catch (RuntimeException error) {
            data.setSessionBuildFailed(true);
            LOGGER.error("Session 47 construction failed; inspect the area before repair", error);
            return false;
        }
    }
}
