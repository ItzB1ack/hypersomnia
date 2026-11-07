package com.hypersomnia.lab;

import java.lang.reflect.Method;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** Проверяет сохранение признака прерванной постройки и его сброс при новой попытке. */
public final class LabSavedDataSelfTest {
    public static void main(String[] args) throws Exception {
        LabSavedData data = new LabSavedData();
        data.buildFailed = true;
        CompoundTag saved = data.save(new CompoundTag(), null);
        Method load = LabSavedData.class.getDeclaredMethod("load", CompoundTag.class, HolderLookup.Provider.class);
        load.setAccessible(true);
        LabSavedData restored = (LabSavedData) load.invoke(null, saved, null);
        if (!restored.buildFailed || restored.built) throw new AssertionError("failure flag must survive reload");
        restored.resetBuild();
        if (restored.buildFailed || restored.built) throw new AssertionError("explicit build reset must clear failure");
        LabSavedData old = (LabSavedData) load.invoke(null, new CompoundTag(), null);
        if (old.buildFailed) throw new AssertionError("old saves must not default to failed");
        System.out.println("LabSavedDataSelfTest: 3 checks passed.");
    }
}
