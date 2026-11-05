package com.hypersomnia.dream;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Состояние сна игрока: где он уснул (чтобы вернуть) и сколько осталось спать. Сохраняется в мир. */
public class DreamData implements INBTSerializable<CompoundTag> {
    private boolean inDream;
    private String returnDim = "minecraft:overworld";
    private double returnX, returnY, returnZ;
    private float returnYaw, returnPitch;
    private int ticksLeft;

    public boolean isInDream() { return inDream; }
    public void setInDream(boolean inDream) { this.inDream = inDream; }

    public int getTicksLeft() { return ticksLeft; }
    public void setTicksLeft(int ticks) { this.ticksLeft = Math.max(0, ticks); }

    public String getReturnDim() { return returnDim; }
    public double getReturnX() { return returnX; }
    public double getReturnY() { return returnY; }
    public double getReturnZ() { return returnZ; }
    public float getReturnYaw() { return returnYaw; }
    public float getReturnPitch() { return returnPitch; }

    public void setReturn(String dim, double x, double y, double z, float yaw, float pitch) {
        this.returnDim = dim;
        this.returnX = x;
        this.returnY = y;
        this.returnZ = z;
        this.returnYaw = yaw;
        this.returnPitch = pitch;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("inDream", inDream);
        tag.putString("returnDim", returnDim);
        tag.putDouble("rx", returnX);
        tag.putDouble("ry", returnY);
        tag.putDouble("rz", returnZ);
        tag.putFloat("ryaw", returnYaw);
        tag.putFloat("rpitch", returnPitch);
        tag.putInt("ticksLeft", ticksLeft);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        inDream = tag.getBoolean("inDream");
        returnDim = tag.contains("returnDim") ? tag.getString("returnDim") : "minecraft:overworld";
        returnX = tag.getDouble("rx");
        returnY = tag.getDouble("ry");
        returnZ = tag.getDouble("rz");
        returnYaw = tag.getFloat("ryaw");
        returnPitch = tag.getFloat("rpitch");
        ticksLeft = Math.max(0, tag.getInt("ticksLeft"));
    }
}
