package com.hypersomnia.nightmare;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Проверяет сохранения кошмаров и личных секций шахты: версии, миграции и битые записи. */
public final class NightmareSavedDataSelfTest {
    private static int checks;

    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }

    public static void main(String[] args) {
        NightmareSavedData data = new NightmareSavedData();
        data.setBuilt(NightmareDefs.WARD_CORRIDOR, true);
        data.setLayout(NightmareDefs.SHAFT, 3);
        data.setWardBuildFailed(true);
        data.setRoundsBuildFailed(true);
        data.setSessionBuildFailed(true);
        UUID forward = UUID.randomUUID();
        UUID reverse = UUID.randomUUID();
        data.setWardPortals(forward, reverse);
        CompoundTag saved = data.save(new CompoundTag(), null);
        check(saved.getInt("version") == NightmareSavedData.DATA_VERSION, "nightmare save writes the current version");
        NightmareSavedData copy = NightmareSavedData.load(saved, null);
        check(copy.isBuilt(NightmareDefs.WARD_CORRIDOR) && !copy.isBuilt(NightmareDefs.SHAFT),
                "built mask round-trips");
        check(copy.layout(NightmareDefs.SHAFT) == 3 && copy.layout(NightmareDefs.WARD_CORRIDOR) == 0,
                "layouts round-trip");
        check(copy.wardBuildFailed() && copy.roundsBuildFailed() && copy.sessionBuildFailed(),
                "build failure flags round-trip");
        check(forward.equals(copy.wardForwardPortal()) && reverse.equals(copy.wardReversePortal()),
                "ward portal ids round-trip");

        NightmareSavedData old = NightmareSavedData.load(new CompoundTag(), null);
        check(!old.isBuilt(NightmareDefs.WARD_CORRIDOR) && old.layout(NightmareDefs.SHAFT) == 0,
                "missing fields default to not built");
        check(old.wardForwardPortal() == null && old.wardReversePortal() == null,
                "missing portals stay null");

        CompoundTag shortLayouts = new CompoundTag();
        shortLayouts.putIntArray("layouts", new int[]{7, 8});
        NightmareSavedData shortData = NightmareSavedData.load(shortLayouts, null);
        check(shortData.layout(NightmareDefs.WARD_CORRIDOR) == 7 && shortData.layout(NightmareDefs.SHAFT) == 8,
                "short layouts array is copied");
        check(shortData.layout(NightmareDefs.ROUNDS) == 0 && shortData.layout(NightmareDefs.SESSION_47) == 0,
                "missing layouts stay zero");

        int[] oversized = new int[16];
        oversized[0] = 9;
        oversized[1] = 10;
        CompoundTag longLayouts = new CompoundTag();
        longLayouts.putIntArray("layouts", oversized);
        NightmareSavedData longData = NightmareSavedData.load(longLayouts, null);
        check(longData.layout(NightmareDefs.WARD_CORRIDOR) == 9 && longData.layout(NightmareDefs.SHAFT) == 10,
                "oversized layouts array does not overflow");

        CompoundTag negative = new CompoundTag();
        negative.putIntArray("layouts", new int[]{-1, -2, -3, -4});
        NightmareSavedData normalized = NightmareSavedData.load(negative, null);
        check(normalized.layout(NightmareDefs.WARD_CORRIDOR) == 0
                && normalized.layout(NightmareDefs.SHAFT) == 0
                && normalized.layout(NightmareDefs.ROUNDS) == 0
                && normalized.layout(NightmareDefs.SESSION_47) == 0,
                "old negative layouts are reset");

        CompoundTag badPortals = new CompoundTag();
        badPortals.putString("wardForwardPortal", "not-a-uuid");
        badPortals.putIntArray("wardReversePortal", new int[]{1, 2, 3});
        NightmareSavedData bad = NightmareSavedData.load(badPortals, null);
        check(bad.wardForwardPortal() == null && bad.wardReversePortal() == null,
                "malformed portal ids are ignored");

        // ---- Личные секции шахты ----
        ShaftStorage shaft = new ShaftStorage();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        check(shaft.reserve(first) == 0, "first player gets lane 0");
        check(shaft.reserve(second) == 1, "second player gets lane 1");
        check(shaft.reserve(first) == 0, "same player keeps the reserved lane");
        shaft.addTrip(first);
        shaft.addTrip(first);
        shaft.setBuilt(0);
        shaft.setFailed(1);
        CompoundTag shaftTag = shaft.save(new CompoundTag(), null);
        check(shaftTag.getInt("version") == ShaftStorage.DATA_VERSION, "shaft save writes the current version");
        ShaftStorage shaftCopy = ShaftStorage.load(shaftTag, null);
        check(shaftCopy.lane(first) == 0 && shaftCopy.lane(second) == 1, "shaft lanes round-trip");
        check(shaftCopy.trips(first) == 2 && shaftCopy.trips(second) == 0, "shaft trips round-trip");
        check(shaftCopy.built(0) && shaftCopy.failed(1), "shaft build masks round-trip");
        UUID third = UUID.randomUUID();
        check(shaftCopy.reserve(third) == 2, "loaded lanes are not handed out twice");
        shaftCopy.release(first);
        check(shaftCopy.lane(first) == -1 && shaftCopy.trips(first) == 0, "release clears lane and trips");
        UUID fourth = UUID.randomUUID();
        check(shaftCopy.reserve(fourth) == 0, "released lane is reusable");

        ShaftStorage empty = ShaftStorage.load(new CompoundTag(), null);
        check(empty.layout() == 0 && empty.lane(first) == -1 && empty.trips(first) == 0,
                "old shaft save is empty and asks for a rebuild");

        CompoundTag broken = new CompoundTag();
        broken.putInt("version", ShaftStorage.DATA_VERSION);
        CompoundTag lanes = new CompoundTag();
        lanes.putInt("not-a-uuid", 2);
        lanes.putInt(first.toString(), 3);
        lanes.putInt(second.toString(), 3);
        lanes.putInt(third.toString(), 99);
        broken.put("lanes", lanes);
        CompoundTag trips = new CompoundTag();
        trips.putInt(first.toString(), -5);
        trips.putInt("not-a-uuid", 7);
        trips.putInt(third.toString(), 1500);
        broken.put("trips", trips);
        ShaftStorage cleaned = ShaftStorage.load(broken, null);
        check((cleaned.lane(first) == 3) ^ (cleaned.lane(second) == 3), "one duplicate shaft lane is kept");
        check((cleaned.lane(first) == -1) ^ (cleaned.lane(second) == -1), "duplicate shaft lane is dropped");
        check(cleaned.lane(third) == -1, "out-of-range shaft lane is dropped");
        check(cleaned.trips(first) == 0, "negative trips clamp to zero");
        check(cleaned.trips(third) == 0, "orphan trips without a lane are dropped");

        CompoundTag highTrips = new CompoundTag();
        CompoundTag highLanes = new CompoundTag();
        highLanes.putInt(first.toString(), 0);
        highTrips.put("lanes", highLanes);
        CompoundTag highTripTag = new CompoundTag();
        highTripTag.putInt(first.toString(), 1500);
        highTrips.put("trips", highTripTag);
        check(ShaftStorage.load(highTrips, null).trips(first) == 999, "trip counter is capped at 999");

        // ---- Повторная постройка и повторный вход (N12) ----
        ShaftStorage rebuild = new ShaftStorage();
        UUID rider = UUID.randomUUID();
        check(rebuild.reserve(rider) == 0, "rebuild: fresh lane starts at 0");
        check(rebuild.reserve(rider) == 0, "rebuild: repeated reserve is idempotent");
        rebuild.setFailed(0);
        check(rebuild.failed(0) && !rebuild.built(0), "rebuild: failed lane is not built");
        UUID next = UUID.randomUUID();
        check(rebuild.reserve(next) == 1, "rebuild: failed lane is not handed out");
        rebuild.setBuilt(0);
        check(rebuild.built(0) && !rebuild.failed(0), "rebuild: building clears the failure flag");
        rebuild.setBuilt(0);
        check(rebuild.built(0), "rebuild: repeated setBuilt is idempotent");

        rebuild.resetBuilds();
        check(!rebuild.built(0) && !rebuild.failed(0), "rebuild: reset clears both masks");
        check(rebuild.layout() == NightmareTuning.SHAFT_LAYOUT, "rebuild: reset records the current layout");
        check(rebuild.lane(rider) == 0 && rebuild.lane(next) == 1, "rebuild: reset keeps lane reservations");
        check(rebuild.trips(rider) == 0, "rebuild: reset keeps trip counters");

        rebuild.addTrip(rider);
        for (int i = 0; i < 1200; i++) rebuild.addTrip(rider);
        check(rebuild.trips(rider) == 999, "rebuild: addTrip caps at 999");
        rebuild.release(rider);
        check(rebuild.lane(rider) == -1 && rebuild.trips(rider) == 0, "rebuild: release clears lane and trips");
        check(rebuild.reserve(rider) == 0, "rebuild: released lane is reusable");
        check(rebuild.lane(next) == 1, "rebuild: release does not touch the other lane");

        // Журнал шахты переживает сохранение и повторную загрузку без изменений.
        CompoundTag rebuildTag = rebuild.save(new CompoundTag(), null);
        CompoundTag rebuildAgain = ShaftStorage.load(rebuildTag, null).save(new CompoundTag(), null);
        check(rebuildTag.equals(rebuildAgain), "rebuild: shaft save/load/save is idempotent");

        System.out.println("NightmareSavedDataSelfTest: " + checks + " checks passed.");
    }
}
