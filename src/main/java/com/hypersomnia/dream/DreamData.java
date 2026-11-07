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
    private String kind = "";
    private int wardLoops;
    // Прогресс действий и объявлений в текущем сеансе, отдельно для каждого игрока.
    private int sessionAttempts;
    private int sessionNextAttemptTicks;
    private int sessionNotice;
    private int roundsChecks;

    public boolean isInDream() { return inDream; }
    public void setInDream(boolean inDream) {
        this.inDream = inDream;
        if (!inDream) {
            this.kind = ""; this.wardLoops = 0;
            resetSession();
        } // любой выход из сна сбрасывает кошмар
    }

    /** "" = обычный сон, иначе id из NightmareDefs. Ставить после setInDream(true). */
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind == null ? "" : kind; }

    /** Число проходов вперёд за текущий кошмар; не меняет другие механики. */
    public int getWardLoops() { return wardLoops; }
    public void addWardLoop() { if (wardLoops < Integer.MAX_VALUE) wardLoops++; }
    public void resetWardLoops() { wardLoops = 0; }
    public int getSessionAttempts() { return sessionAttempts; }
    public void addSessionAttempt() { sessionAttempts = Math.min(4, sessionAttempts + 1); }
    public boolean hasRoundsCheck(int k) { return (roundsChecks & (1 << k)) != 0; }
    public void setRoundsCheck(int k) { roundsChecks |= 1 << k; }
    public int getSessionNextAttemptTicks() { return sessionNextAttemptTicks; }
    public void setSessionNextAttemptTicks(int ticks) { sessionNextAttemptTicks = Math.max(0, ticks); }
    public int getSessionNotice() { return sessionNotice; }
    public void setSessionNotice(int notice) { sessionNotice = Math.max(0, Math.min(3, notice)); }
    public void resetSession() { sessionAttempts = 0; sessionNextAttemptTicks = 0; sessionNotice = 0; roundsChecks = 0; }

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
        tag.putString("kind", kind);
        tag.putInt("wardLoops", wardLoops);
        tag.putInt("sessionAttempts", sessionAttempts);
        tag.putInt("sessionNextAttemptTicks", sessionNextAttemptTicks);
        tag.putInt("sessionNotice", sessionNotice);
        tag.putInt("roundsChecks", roundsChecks);
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
        kind = tag.getString("kind");
        wardLoops = inDream ? Math.max(0, tag.getInt("wardLoops")) : 0;
        sessionAttempts = inDream ? Math.max(0, Math.min(4, tag.getInt("sessionAttempts"))) : 0;
        sessionNextAttemptTicks = inDream ? Math.max(0, tag.getInt("sessionNextAttemptTicks")) : 0;
        sessionNotice = inDream ? Math.max(0, Math.min(3, tag.getInt("sessionNotice"))) : 0;
        roundsChecks = inDream ? Math.max(0, tag.getInt("roundsChecks")) : 0;
    }
}
