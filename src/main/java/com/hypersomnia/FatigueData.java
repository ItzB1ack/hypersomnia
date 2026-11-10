package com.hypersomnia;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.util.INBTSerializable;

public class FatigueData implements INBTSerializable<CompoundTag> {
    private int fatigue;
    private int madness;
    private int collapseTimer;
    private int forcedSleepAttempts; // только текущая сессия; ограничение повтора, не прогресс
    private int collapseGraceTicks;
    private boolean syringeCollapse;

    // Служебные поля, в мир не сохраняются
    private int lastSyncedFatigue = -1;
    private boolean lastSyncedCollapsing;
    /** Текущий сон разрешён днём (порог усталости проверен при входе, не сохраняется). */
    private boolean anyTimeSleep;

    public int getFatigue() { return fatigue; }
    public void setFatigue(int value) { this.fatigue = Mth.clamp(value, 0, FatigueTuning.MAX_FATIGUE); }
    public void addFatigue(int delta) {
        setFatigue((int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, (long) fatigue + delta)));
    }

    /** Сколько раз осколок «дошёл до края». Пригодится для сюжета и эскалации. */
    public int getMadness() { return madness; }
    public void addMadness(int delta) {
        long value = (long) madness + delta;
        this.madness = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
    }

    public int getCollapseTimer() { return collapseTimer; }
    public void setCollapseTimer(int ticks) { this.collapseTimer = Math.max(0, ticks); }

    public int incrementForcedSleepAttempts() { return ++forcedSleepAttempts; }
    public void resetForcedSleepAttempts() { forcedSleepAttempts = 0; }
    public int getCollapseGraceTicks() { return collapseGraceTicks; }
    public void setCollapseGraceTicks(int ticks) { collapseGraceTicks = Math.max(0, ticks); }
    public boolean isSyringeCollapse() { return syringeCollapse; }
    public void setSyringeCollapse(boolean value) { syringeCollapse = value; }

    public int getLastSyncedFatigue() { return lastSyncedFatigue; }
    public boolean isLastSyncedCollapsing() { return lastSyncedCollapsing; }
    public boolean isAnyTimeSleep() { return anyTimeSleep; }
    public void setAnyTimeSleep(boolean value) { anyTimeSleep = value; }
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
        tag.putInt("collapseGrace", collapseGraceTicks);
        tag.putBoolean("syringeCollapse", syringeCollapse);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        this.fatigue = Mth.clamp(tag.getInt("fatigue"), 0, FatigueTuning.MAX_FATIGUE);
        this.madness = Math.max(0, tag.getInt("madness"));
        this.collapseTimer = Math.max(0, tag.getInt("collapse"));
        this.collapseGraceTicks = Math.max(0, tag.getInt("collapseGrace"));
        this.syringeCollapse = tag.getBoolean("syringeCollapse");
        this.forcedSleepAttempts = 0;
        this.anyTimeSleep = false;
    }
}
