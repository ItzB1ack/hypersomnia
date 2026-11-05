package com.hypersomnia.syringe;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Состояние искусственного бодрствования и скрытая толерантность. */
public class SyringeData implements INBTSerializable<CompoundTag> {
    private int wakeTicks;   // сколько ещё действует
    private int pin;         // уровень усталости, на котором она «заморожена»
    private int debt;        // сколько усталости снято уколом
    private int accrued;     // сколько набежало под спудом
    private int tolerance;   // скрытая толерантность

    // Служебное, не сохраняется
    private boolean wasInDream;
    private int lastSentActive = -1;
    private int lastSentLevel = -1;

    public int getWakeTicks() { return wakeTicks; }
    public void setWakeTicks(int v) { this.wakeTicks = Math.max(0, v); }
    public int getPin() { return pin; }
    public int getDebt() { return debt; }
    public int getAccrued() { return accrued; }
    public void addAccrued(int v) { this.accrued += Math.max(0, v); }
    public int getTolerance() { return tolerance; }
    public void addTolerance(int d) { this.tolerance = Math.max(0, this.tolerance + d); }

    public void begin(int ticks, int pin, int debt) {
        this.wakeTicks = ticks;
        this.pin = pin;
        this.debt = debt;
        this.accrued = 0;
    }

    public void clearWake() {
        this.wakeTicks = 0;
        this.pin = 0;
        this.debt = 0;
        this.accrued = 0;
    }

    public boolean wasInDream() { return wasInDream; }
    public void setWasInDream(boolean v) { this.wasInDream = v; }
    public int getLastSentActive() { return lastSentActive; }
    public int getLastSentLevel() { return lastSentLevel; }
    public void markSent(int active, int level) { this.lastSentActive = active; this.lastSentLevel = level; }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        t.putInt("wake", wakeTicks);
        t.putInt("pin", pin);
        t.putInt("debt", debt);
        t.putInt("accrued", accrued);
        t.putInt("tolerance", tolerance);
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        wakeTicks = Math.max(0, t.getInt("wake"));
        pin = Math.max(0, t.getInt("pin"));
        debt = Math.max(0, t.getInt("debt"));
        accrued = Math.max(0, t.getInt("accrued"));
        tolerance = Math.max(0, t.getInt("tolerance"));
    }
}
