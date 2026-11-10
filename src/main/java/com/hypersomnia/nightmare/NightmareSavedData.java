package com.hypersomnia.nightmare;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.UUID;

/** Какие кошмары построены. Хранится в данных измерения снов. */
public final class NightmareSavedData extends SavedData {
    private static final String NAME = "hypersomnia_nightmare";
    /** Версия формата; 0 — сохранения до появления поля version. */
    public static final int DATA_VERSION = 1;
    private int builtMask;
    private final int[] layouts = new int[8];
    private boolean wardBuildFailed;
    private boolean roundsBuildFailed;
    private boolean sessionBuildFailed;
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

    public int layout(NightmareDefs n) { return layouts[n.ordinal()]; }
    public void setLayout(NightmareDefs n, int v) { layouts[n.ordinal()] = v; setDirty(); }

    public boolean wardBuildFailed() { return wardBuildFailed; }
    public void setWardBuildFailed(boolean failed) { wardBuildFailed = failed; setDirty(); }
    public boolean roundsBuildFailed() { return roundsBuildFailed; }
    public void setRoundsBuildFailed(boolean failed) { roundsBuildFailed = failed; setDirty(); }
    public boolean sessionBuildFailed() { return sessionBuildFailed; }
    public void setSessionBuildFailed(boolean failed) { sessionBuildFailed = failed; setDirty(); }

    public UUID wardForwardPortal() { return wardForwardPortal; }
    public UUID wardReversePortal() { return wardReversePortal; }
    public void setWardPortals(UUID forward, UUID reverse) {
        wardForwardPortal = forward;
        wardReversePortal = reverse;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("version", DATA_VERSION);
        tag.putInt("built", builtMask);
        tag.putIntArray("layouts", layouts);
        if (wardBuildFailed) tag.putBoolean("wardBuildFailed", true);
        if (roundsBuildFailed) tag.putBoolean("roundsBuildFailed", true);
        if (sessionBuildFailed) tag.putBoolean("sessionBuildFailed", true);
        if (wardForwardPortal != null) tag.putUUID("wardForwardPortal", wardForwardPortal);
        if (wardReversePortal != null) tag.putUUID("wardReversePortal", wardReversePortal);
        return tag;
    }

    static NightmareSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        NightmareSavedData d = new NightmareSavedData();
        d.builtMask = tag.getInt("built");
        int[] l = tag.getIntArray("layouts");
        System.arraycopy(l, 0, d.layouts, 0, Math.min(l.length, d.layouts.length));
        if (tag.getInt("version") < 1) {
            // Старый формат: мусорные отрицательные layout не должны совпасть
            // с текущими константами и заблокировать перестройку комнаты.
            for (int i = 0; i < d.layouts.length; i++) if (d.layouts[i] < 0) d.layouts[i] = 0;
        }
        d.wardBuildFailed = tag.getBoolean("wardBuildFailed");
        d.roundsBuildFailed = tag.getBoolean("roundsBuildFailed");
        d.sessionBuildFailed = tag.getBoolean("sessionBuildFailed");
        if (tag.hasUUID("wardForwardPortal")) d.wardForwardPortal = tag.getUUID("wardForwardPortal");
        if (tag.hasUUID("wardReversePortal")) d.wardReversePortal = tag.getUUID("wardReversePortal");
        return d;
    }
}