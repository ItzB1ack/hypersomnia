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
    private final Map<UUID, Integer> trips = new HashMap<>();

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
    public void release(UUID id) { boolean a = lanes.remove(id) != null, b = trips.remove(id) != null; if (a || b) setDirty(); }
    public int trips(UUID id) { return trips.getOrDefault(id, 0); }
    public void addTrip(UUID id) { trips.merge(id, 1, (a, b) -> Math.min(999, a + b)); setDirty(); }
    public boolean built(int lane) { return (builtMask & (1 << lane)) != 0; }
    public boolean failed(int lane) { return (failedMask & (1 << lane)) != 0; }
    public void setBuilt(int lane) { builtMask |= 1 << lane; failedMask &= ~(1 << lane); setDirty(); }
    public void setFailed(int lane) { failedMask |= 1 << lane; setDirty(); }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("built", builtMask); tag.putInt("failed", failedMask);
        CompoundTag entries = new CompoundTag();
        lanes.forEach((id, lane) -> entries.putInt(id.toString(), lane));
        tag.put("lanes", entries);
        CompoundTag tripTag = new CompoundTag();
        trips.forEach((id, n) -> tripTag.putInt(id.toString(), n));
        tag.put("trips", tripTag);
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
        CompoundTag tripTag = tag.getCompound("trips");
        for (String key : tripTag.getAllKeys()) {
            try { result.trips.put(UUID.fromString(key), Math.max(0, tripTag.getInt(key))); }
            catch (IllegalArgumentException ignored) { }
        }
        return result;
    }
}
