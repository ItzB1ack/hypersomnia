package com.hypersomnia.nightmare;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Личные секции шахты; аренда сохраняется и на время отключения игрока. */
public final class ShaftStorage extends SavedData {
    private static final String NAME = "hypersomnia_shaft";
    private int builtMask, failedMask;
    private final Map<UUID, Integer> lanes = new HashMap<>();

    public static ShaftStorage get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(ShaftStorage::new, ShaftStorage::load, null), NAME);
    }
    public int lane(UUID id) { return lanes.getOrDefault(id, -1); }
    public int reserve(UUID id) {
        int existing = lane(id);
        if (existing >= 0) return existing;
        for (int i = 0; i < NightmareTuning.SHAFT_LANES; i++) {
            if (!failed(i) && !lanes.containsValue(i)) { lanes.put(id, i); setDirty(); return i; }
        }
        return -1;
    }
    public void release(UUID id) { if (lanes.remove(id) != null) setDirty(); }
    public boolean built(int lane) { return (builtMask & (1 << lane)) != 0; }
    public boolean failed(int lane) { return (failedMask & (1 << lane)) != 0; }
    public void setBuilt(int lane) { builtMask |= 1 << lane; failedMask &= ~(1 << lane); setDirty(); }
    public void setFailed(int lane) { failedMask |= 1 << lane; setDirty(); }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("built", builtMask); tag.putInt("failed", failedMask);
        CompoundTag entries = new CompoundTag();
        lanes.forEach((id, lane) -> entries.putInt(id.toString(), lane));
        tag.put("lanes", entries);
        return tag;
    }
    private static ShaftStorage load(CompoundTag tag, HolderLookup.Provider provider) {
        ShaftStorage result = new ShaftStorage();
        result.builtMask = tag.getInt("built"); result.failedMask = tag.getInt("failed");
        CompoundTag entries = tag.getCompound("lanes");
        for (String key : entries.getAllKeys()) {
            try {
                int lane = entries.getInt(key);
                if (lane >= 0 && lane < NightmareTuning.SHAFT_LANES && !result.lanes.containsValue(lane))
                    result.lanes.put(UUID.fromString(key), lane);
            } catch (IllegalArgumentException ignored) { }
        }
        return result;
    }
}
