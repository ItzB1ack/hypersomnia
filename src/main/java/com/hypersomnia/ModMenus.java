package com.hypersomnia;

import com.hypersomnia.lab.LabTuning;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Меню мода. Шкаф: обычный ChestMenu (ряд = ящик), свой тип только ради экрана CabinetScreen в палитре лаборатории. */
public final class ModMenus {
    private ModMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Hypersomnia.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ChestMenu>> CABINET = MENUS.register("cabinet",
            () -> new MenuType<>((id, inv) -> new ChestMenu(ModMenus.CABINET.get(), id, inv,
                    new SimpleContainer(LabTuning.CABINET_ROWS * 9), LabTuning.CABINET_ROWS), FeatureFlags.DEFAULT_FLAGS));
}
