package com.hypersomnia.lab;

import java.lang.reflect.Method;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** Проверяет сохранение лаборатории: полный round-trip, миграции и устойчивость к старым/битым данным. */
public final class LabSavedDataSelfTest {
    private static int checks;
    private static Method load;

    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }

    private static LabSavedData load(CompoundTag tag) throws Exception {
        return (LabSavedData) load.invoke(null, tag, (HolderLookup.Provider) null);
    }

    public static void main(String[] args) throws Exception {
        load = LabSavedData.class.getDeclaredMethod("load", CompoundTag.class, HolderLookup.Provider.class);
        load.setAccessible(true);

        LabSavedData data = new LabSavedData();
        data.buildFailed = true;
        CompoundTag saved = data.save(new CompoundTag(), null);
        check(saved.getInt("version") == LabSavedData.DATA_VERSION, "save writes the current version");
        LabSavedData restored = load(saved);
        check(restored.buildFailed && !restored.built, "failure flag must survive reload");
        restored.resetBuild();
        check(!restored.buildFailed && !restored.built, "explicit build reset must clear failure");
        LabSavedData old = load(new CompoundTag());
        check(!old.buildFailed, "old saves must not default to failed");
        check(old.closedDoor == -1 && old.outageStop == -1, "missing optional stops default to -1");
        check(!old.seeded && old.cipherSeed == 0, "missing seed stays unseeded");

        // Полный round-trip: каждое сохраняемое поле должно вернуться без потерь.
        LabSavedData full = new LabSavedData();
        full.sited = true;
        full.siteX = 123;
        full.siteZ = -456;
        full.siteTry = 7;
        full.seeded = true;
        full.cipherSeed = 0x123456789ABCDEFL;
        full.built = true;
        full.dream = false;
        full.originX = 1000;
        full.originZ = -2000;
        full.groundY = 64;
        full.builtMask = 0b10101;
        full.shaftBuilt = true;
        full.lampsPending = true;
        full.sealPending = true;
        full.reached = 4;
        full.locksOpen = 0b1011;
        full.flags = 0b101;
        full.breakerSeq = 54321;
        full.breakerTripped = true;
        full.closedDoor = 3;
        full.dishPos = BlockPos.asLong(10, 70, -20);
        full.dishAngle = 135;
        full.roofPending = true;
        full.outageStop = 2;
        for (int i = 0; i < full.stopY.length; i++) {
            full.stopY[i] = 100 + i;
            full.stopH[i] = 200 + i;
            full.door[i] = BlockPos.asLong(i, 300 + i, -i);
        }
        for (int i = 0; i < full.bbox.length; i++) full.bbox[i] = 400 + i;
        full.inter.put(BlockPos.asLong(5, 60, 6), LabSavedData.KIND_LOCK * 1000 + 3);
        full.inter.put(BlockPos.asLong(-1, 61, 7), LabSavedData.KIND_BREAKER * 1000);
        CompoundTag fullTag = full.save(new CompoundTag(), null);
        LabSavedData copy = load(fullTag);
        check(copy.sited && copy.siteX == 123 && copy.siteZ == -456 && copy.siteTry == 7, "site fields round-trip");
        check(copy.seeded && copy.cipherSeed == full.cipherSeed, "cipher seed round-trips");
        check(copy.built && !copy.dream && copy.originX == 1000 && copy.originZ == -2000 && copy.groundY == 64,
                "world placement round-trips");
        check(copy.builtMask == full.builtMask && copy.shaftBuilt && copy.lampsPending && copy.sealPending,
                "build flags round-trip");
        check(copy.reached == 4 && copy.locksOpen == full.locksOpen && copy.flags == full.flags,
                "progress round-trips");
        check(copy.breakerSeq == 54321 && copy.breakerTripped && copy.closedDoor == 3,
                "breaker and door state round-trip");
        check(copy.dishPos == full.dishPos && copy.dishAngle == 135 && copy.roofPending && copy.outageStop == 2,
                "antenna and outage state round-trip");
        check(Arrays.equals(copy.stopY, full.stopY) && Arrays.equals(copy.stopH, full.stopH)
                && Arrays.equals(copy.bbox, full.bbox) && Arrays.equals(copy.door, full.door),
                "geometry arrays round-trip");
        check(copy.inter.equals(full.inter), "interactable map round-trips");

        // Версии: v2 добавляет крышу, v4 — светильники и герметизацию; текущая версия не повторяет миграции.
        CompoundTag v2 = fullTag.copy();
        v2.putInt("version", 2);
        v2.putBoolean("roofPending", false);
        check(load(v2).roofPending, "v2 overworld lab schedules roof upgrade");
        CompoundTag v3 = fullTag.copy();
        v3.putInt("version", 3);
        v3.putBoolean("roofPending", false);
        check(!load(v3).roofPending, "v3 keeps the saved roof state");
        CompoundTag v4 = fullTag.copy();
        v4.putInt("version", 4);
        v4.putBoolean("lampsPending", false);
        v4.putBoolean("sealPending", false);
        LabSavedData migrated = load(v4);
        check(migrated.lampsPending && migrated.sealPending, "v4 built lab schedules lamp and seal migrations");
        CompoundTag current = fullTag.copy();
        current.putInt("version", LabSavedData.DATA_VERSION);
        current.putBoolean("lampsPending", false);
        current.putBoolean("sealPending", false);
        LabSavedData currentData = load(current);
        check(!currentData.lampsPending && !currentData.sealPending, "current version does not re-run migrations");

        // Битые и старые массивы: длина не совпала — остаются значения по умолчанию.
        CompoundTag broken = fullTag.copy();
        broken.putIntArray("stopY", new int[]{1, 2});
        broken.putIntArray("bbox", new int[]{1});
        broken.putLongArray("door", new long[]{1});
        LabSavedData safe = load(broken);
        check(safe.stopY.length == LabTuning.STOPS && safe.stopH.length == LabTuning.STOPS
                && safe.bbox.length == LabTuning.STOPS * 4 && safe.door.length == LabTuning.STOPS,
                "truncated arrays keep the default length");
        check(Arrays.stream(safe.stopY).allMatch(v -> v == 0), "truncated array is not partially copied");

        // Дубликаты интерактивных позиций: карта оставляет последнее значение.
        long key = BlockPos.asLong(1, 2, 3);
        CompoundTag duplicates = new CompoundTag();
        duplicates.putLongArray("interKeys", new long[]{key, key});
        duplicates.putIntArray("interVals", new int[]{1, 2});
        LabSavedData dedup = load(duplicates);
        check(dedup.inter.size() == 1 && dedup.inter.get(key) == 2, "duplicate interactable positions keep the last value");
        CompoundTag mismatch = new CompoundTag();
        mismatch.putLongArray("interKeys", new long[]{key, key + 1});
        mismatch.putIntArray("interVals", new int[]{5});
        check(load(mismatch).inter.size() == 1, "unpaired interactable value is ignored");

        System.out.println("LabSavedDataSelfTest: " + checks + " checks passed.");
    }
}
