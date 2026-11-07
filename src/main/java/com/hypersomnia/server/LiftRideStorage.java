package com.hypersomnia.server;

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

    private static LiftRideStorage load(CompoundTag tag, HolderLookup.Provider provider) {
        LiftRideStorage result = new LiftRideStorage();
        HolderLookup.RegistryLookup<Block> blocks = provider.lookupOrThrow(Registries.BLOCK);
        ListTag list = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            result.hidden.put(entry.getLong("pos"), NbtUtils.readBlockState(blocks, entry.getCompound("state")));
        }
        return result;
    }
}
