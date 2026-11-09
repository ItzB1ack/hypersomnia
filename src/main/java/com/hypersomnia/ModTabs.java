package com.hypersomnia;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Вкладка мода в творческом инвентаре. Новые предметы добавляй в displayItems. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ModTabs {
    private ModTabs() {}

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.CREATIVE_MODE_TAB, helper -> helper.register(
                ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "main"),
                CreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.hypersomnia"))
                        .icon(() -> new ItemStack(ModItems.ADRENALINE_SYRINGE.get()))
                        .displayItems((params, output) -> {
                            output.accept(ModItems.ADRENALINE_SYRINGE.get());
                            output.accept(ModItems.DIARY.get());
                            output.accept(ModItems.BUNNY.get());
                            output.accept(ModItems.BREAKER_PANEL.get());
                            output.accept(ModItems.WORKSTATION.get());
                            output.accept(ModItems.DESK.get());
                            output.accept(ModItems.CABINET.get());
                            output.accept(ModItems.MONITOR.get());
                            output.accept(ModItems.CHAIR.get());
                            output.accept(ModItems.KEYPAD.get());
                            output.accept(ModItems.SERVER_RACK.get());
                            output.accept(ModItems.CRAC_UNIT.get());
                            output.accept(ModItems.UPS_CABINET.get());
                            output.accept(ModItems.BATTERY_CABINET.get());
                            output.accept(ModItems.LIFT_PANEL.get());
                            output.accept(ModItems.ANTENNA_DRIVE.get());
                            output.accept(ModItems.LIGHT_TUBE.get());
                            output.accept(ModItems.UNLIT_TUBE.get());
                            output.accept(ModItems.LAB_SIGN.get());
                            output.accept(ModItems.CAPSULE.get());
                            output.accept(ModItems.CAPSULE_UPRIGHT.get());
                            output.accept(ModItems.DREAM_CLOCK.get());
                            for (com.hypersomnia.document.DocumentDefs.Doc d : com.hypersomnia.document.DocumentDefs.all()) {
                                output.accept(com.hypersomnia.document.DocumentItem.make(d.id()));
                            }
                        })
                        .build()
        ));
    }
}
