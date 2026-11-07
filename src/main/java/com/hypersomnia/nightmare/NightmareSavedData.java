package com.hypersomnia.nightmare;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.UUID;

/** Какие кошмары построены. Хранится в данных измерения снов. */
public final class NightmareSavedData extends SavedData {
    private static final String NAME = "hypersomnia_nightmare";
    private int builtMask;
    private UUID wardForwardPortal;
    private UUID wardReversePortal;

    public static NightmareSavedData get(ServerLevel dream) {
        return dream.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NightmareSavedData::new, NightmareSavedData::load, null), NAME);
    }

    public boolean isBuilt(NightmareDefs n) {
        return ((builtMask >> n.ordinal()) & 1) != 0;
    }

    public void setBuilt(NightmareDefs n, boolean built) {
        if (built) builtMask |= 1 << n.ordinal();
        else builtMask &= ~(1 << n.ordinal());
        setDirty();
    }

    public UUID wardForwardPortal() { return wardForwardPortal; }
    public UUID wardReversePortal() { return wardReversePortal; }
    public void setWardPortals(UUID forward, UUID reverse) {
        wardForwardPortal = forward;
        wardReversePortal = reverse;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("built", builtMask);
        if (wardForwardPortal != null) tag.putUUID("wardForwardPortal", wardForwardPortal);
        if (wardReversePortal != null) tag.putUUID("wardReversePortal", wardReversePortal);
        return tag;
    }

    private static NightmareSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        NightmareSavedData d = new NightmareSavedData();
        d.builtMask = tag.getInt("built");
        if (tag.hasUUID("wardForwardPortal")) d.wardForwardPortal = tag.getUUID("wardForwardPortal");
        if (tag.hasUUID("wardReversePortal")) d.wardReversePortal = tag.getUUID("wardReversePortal");
        return d;
    }
}