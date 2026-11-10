package com.hypersomnia.server;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * [новое] Журнал блоков, которые рейс лифта временно убрал из мира.
 *
 * <p>Если сервер жёстко упадёт посреди поездки, обычный {@link LiftRide} не успеет вернуть блоки, и в кабине
 * останется дырка. Журнал пишется в тот же проход сохранения, что и мир, и при следующем запуске сервера
 * возвращает все скрытые состояния. Блоки с block entity сюда не попадают: их рейс не трогает.
 */
public final class LiftRideStorage extends SavedData {
    private static final String NAME = "hypersomnia_lift_ride";
    /** Версия формата; 0 — сохранения до появления поля version. */
    public static final int DATA_VERSION = 1;
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<Long, BlockState> hidden = new HashMap<>();

    public static LiftRideStorage get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(LiftRideStorage::new, LiftRideStorage::load, null), NAME);
    }

    public void remember(BlockPos pos, BlockState state) {
        hidden.put(pos.asLong(), state);
        setDirty();
    }

    public void forget(BlockPos pos) {
        if (hidden.remove(pos.asLong()) != null) setDirty();
    }

    /** Возвращает все блоки, скрытые до падения сервера. */
    public void restoreAll(ServerLevel level) {
        if (hidden.isEmpty()) return;
        hidden.forEach((key, state) -> level.setBlock(BlockPos.of(key), state, Block.UPDATE_CLIENTS));
        hidden.clear();
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("version", DATA_VERSION);
        ListTag list = new ListTag();
        hidden.forEach((key, state) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", key);
            entry.put("state", NbtUtils.writeBlockState(state));
            list.add(entry);
        });
        tag.put("blocks", list);
        return tag;
    }

    static LiftRideStorage load(CompoundTag tag, HolderLookup.Provider provider) {
        LiftRideStorage result = new LiftRideStorage();
        ListTag list = tag.getList("blocks", Tag.TAG_COMPOUND);
        if (list.isEmpty()) return result;
        HolderLookup.RegistryLookup<Block> blocks = provider.lookupOrThrow(Registries.BLOCK);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.contains("pos", Tag.TAG_LONG) || !entry.contains("state", Tag.TAG_COMPOUND)) {
                LOGGER.warn("Lift ride journal: skipping malformed entry {}", i);
                continue;
            }
            BlockState state;
            try {
                state = NbtUtils.readBlockState(blocks, entry.getCompound("state"));
            } catch (RuntimeException error) {
                LOGGER.warn("Lift ride journal: skipping unreadable block state at {}", entry.getLong("pos"), error);
                continue;
            }
            // readBlockState возвращает воздух для неизвестного блока. Возвращать
            // воздух на место скрытого блока нельзя: это удалит то, что там сейчас.
            if (state.isAir()) {
                LOGGER.warn("Lift ride journal: unknown block at {}, leaving the world as is", entry.getLong("pos"));
                continue;
            }
            result.hidden.put(entry.getLong("pos"), state);
        }
        return result;
    }
}
