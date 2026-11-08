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
    // [новое] Напольные часы в мире снов (DreamClock).
    private boolean clockActive;
    private int clockX, clockY, clockZ;
    /** Остаток времени на поиск часов; 0 — время вышло. */
    private int clockTicks;
    /** Сколько ударов осталось; 0 — уже пробили. */
    private int clockStrikes;
    /** Тики до следующего удара / до начала погони. */
    private int clockNext;
    /** Погоня из-за часов уже началась. */
    private boolean clockChase;

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

    // ---- [новое] Часы сна ----
    public boolean clockActive() { return clockActive; }
    public int clockX() { return clockX; }
    public int clockY() { return clockY; }
    public int clockZ() { return clockZ; }
    public int clockTicks() { return clockTicks; }
    public int clockStrikes() { return clockStrikes; }
    public int clockNext() { return clockNext; }
    public boolean clockChase() { return clockChase; }

    /** Часы поставлены: запомнить место и запустить отсчёт поиска. */
    public void startClock(int x, int y, int z, int ticks) {
        clockActive = true;
        clockX = x; clockY = y; clockZ = z;
        clockTicks = Math.max(0, ticks);
        clockStrikes = 0;
        clockNext = 0;
        clockChase = false;
    }
    public void setClockTicks(int v) { clockTicks = Math.max(0, v); }
    public void setClockStrikes(int v) { clockStrikes = Math.max(0, v); }
    public void setClockNext(int v) { clockNext = Math.max(0, v); }
    public void setClockChase(boolean v) { clockChase = v; }
    /** Снять часы (пробуждение, выход из сна, смена измерения). */
    public void resetClock() {
        clockActive = false;
        clockX = clockY = clockZ = 0;
        clockTicks = 0;
        clockStrikes = 0;
        clockNext = 0;
        clockChase = false;
    }

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
        tag.putBoolean("clockActive", clockActive);
        tag.putInt("clockX", clockX);
        tag.putInt("clockY", clockY);
        tag.putInt("clockZ", clockZ);
        tag.putInt("clockTicks", clockTicks);
        tag.putInt("clockStrikes", clockStrikes);
        tag.putInt("clockNext", clockNext);
        tag.putBoolean("clockChase", clockChase);
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
        clockActive = inDream && tag.getBoolean("clockActive");
        clockX = tag.getInt("clockX");
        clockY = tag.getInt("clockY");
        clockZ = tag.getInt("clockZ");
        clockTicks = clockActive ? Math.max(0, tag.getInt("clockTicks")) : 0;
        clockStrikes = clockActive ? Math.max(0, tag.getInt("clockStrikes")) : 0;
        clockNext = clockActive ? Math.max(0, tag.getInt("clockNext")) : 0;
        clockChase = clockActive && tag.getBoolean("clockChase");
    }
}
