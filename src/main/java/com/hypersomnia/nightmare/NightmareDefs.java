package com.hypersomnia.nightmare;

import net.minecraft.core.BlockPos;

/** Описание кошмаров: id, триггер, слот в зоне, длительность, способ выхода. */
public enum NightmareDefs {
    WARD_CORRIDOR("ward", Trigger.CAUGHT, Exit.EMPTY_WARD, 0, NightmareTuning.WARD_DURATION_TICKS),
    SHAFT("shaft", Trigger.FORCED_SLEEP, Exit.FLOOR_ZERO, 1, NightmareTuning.SHAFT_DURATION_TICKS),
    ROUNDS("rounds", Trigger.SYRINGE_REBOUND, Exit.CAPSULE_47, 2, NightmareTuning.ROUNDS_DURATION_TICKS),
    SESSION_47("session47", Trigger.MEMORY_6, Exit.CLOCK_END, 3, NightmareTuning.SESSION_DURATION_TICKS);

    public enum Trigger { CAUGHT, FORCED_SLEEP, SYRINGE_REBOUND, MEMORY_6 }
    public enum Exit { EMPTY_WARD, FLOOR_ZERO, CAPSULE_47, CLOCK_END }

    private final String id;
    private final Trigger trigger;
    private final Exit exit;
    private final int slot;
    private final int durationTicks;

    NightmareDefs(String id, Trigger trigger, Exit exit, int slot, int durationTicks) {
        this.id = id;
        this.trigger = trigger;
        this.exit = exit;
        this.slot = slot;
        this.durationTicks = durationTicks;
    }

    public String id() { return id; }
    public Trigger trigger() { return trigger; }
    public Exit exit() { return exit; }
    public int durationTicks() { return durationTicks; }

    /** Начало зоны этого кошмара. Точку появления потом задаст билдер (SavedData). */
    public BlockPos origin() {
        return new BlockPos(NightmareTuning.ZONE_X + slot * NightmareTuning.SLOT_STRIDE,
                NightmareTuning.ZONE_Y, NightmareTuning.ZONE_Z);
    }

    /** Точка появления: середина зоны коридора (период 2), лицом по +X. Для кошмаров №2-4 поправим с их построителями. */
    public BlockPos spawn() {
        if (this == ROUNDS) return RoundsBuilder.spawn();
        if (this == SESSION_47) return SessionBuilder.spawn();
        return origin().offset(NightmareTuning.WARD_PERIOD * 2, 1, 1);
    }

    public float yaw() { return NightmareTuning.SPAWN_YAW; }

    public static NightmareDefs byId(String id) {
        if (id == null || id.isEmpty()) return null;
        for (NightmareDefs n : values()) if (n.id.equals(id)) return n;
        return null;
    }
}