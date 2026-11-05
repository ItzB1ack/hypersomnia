package com.hypersomnia.scene;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Прогресс сцены: была ли показана и сколько раз игрок пытался уснуть. */
public class SceneData implements INBTSerializable<CompoundTag> {
    private boolean seen;
    private int attempts;
    /** Служебное: бросок уже сделан в текущем лежании (не сохраняется). */
    private boolean rolledThisSleep;

    public boolean isSeen() { return seen; }
    public void setSeen(boolean v) { this.seen = v; }
    public int getAttempts() { return attempts; }
    public void addAttempt() { this.attempts++; }
    public boolean isRolledThisSleep() { return rolledThisSleep; }
    public void setRolledThisSleep(boolean v) { this.rolledThisSleep = v; }
    /** Служебное: сколько тиков игрока ещё держат в кровати (не сохраняется). */
    private int hold;
    public int getHold() { return hold; }
    public void setHold(int v) { this.hold = Math.max(0, v); }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("seen", seen);
        tag.putInt("attempts", attempts);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        this.seen = tag.getBoolean("seen");
        this.attempts = Math.max(0, tag.getInt("attempts"));
    }
}
