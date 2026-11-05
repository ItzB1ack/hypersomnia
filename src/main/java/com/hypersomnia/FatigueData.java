package com.hypersomnia;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.util.INBTSerializable;

public class FatigueData implements INBTSerializable<CompoundTag> {
    private int fatigue;
    private int madness;
    private int collapseTimer;

    // Служебные поля, в мир не сохраняются
    private int lastSyncedFatigue = -1;
    private boolean lastSyncedCollapsing;

    public int getFatigue() { return fatigue; }
    public void setFatigue(int value) { this.fatigue = Mth.clamp(value, 0, FatigueTuning.MAX_FATIGUE); }
    public void addFatigue(int delta) { setFatigue(this.fatigue + delta); }

    /** Сколько раз осколок «дошёл до края». Пригодится для сюжета и эскалации. */
    public int getMadness() { return madness; }
    public void addMadness(int delta) { this.madness = Math.max(0, this.madness + delta); }

    public int getCollapseTimer() { return collapseTimer; }
    public void setCollapseTimer(int ticks) { this.collapseTimer = Math.max(0, ticks); }

    public int getLastSyncedFatigue() { return lastSyncedFatigue; }
    public boolean isLastSyncedCollapsing() { return lastSyncedCollapsing; }
    public void markSynced(int fatigue, boolean collapsing) {
        this.lastSyncedFatigue = fatigue;
        this.lastSyncedCollapsing = collapsing;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("fatigue", fatigue);
        tag.putInt("madness", madness);
        tag.putInt("collapse", collapseTimer);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        this.fatigue = Mth.clamp(tag.getInt("fatigue"), 0, FatigueTuning.MAX_FATIGUE);
        this.madness = Math.max(0, tag.getInt("madness"));
        this.collapseTimer = Math.max(0, tag.getInt("collapse"));
    }
}
