package com.hypersomnia.nightmare;

import com.hypersomnia.Hypersomnia;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Отдельная регистрация декора кошмара, без изменения ModBlocks и ModItems. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class WardDecorBlocks {
    private WardDecorBlocks() {}

    // Создаём объекты только внутри соответствующего RegisterEvent, когда реестр открыт.
    public static WardBedBlock BED;
    public static WardMonitorBlock MONITOR;
    public static BlockItem MONITOR_ITEM;

    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(SoundType.METAL)
                .strength(-1.0f, 3600000.0f).noLootTable().pushReaction(PushReaction.BLOCK).noOcclusion();
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            WardBedBlock bed = new WardBedBlock(properties());
            WardMonitorBlock monitor = new WardMonitorBlock(properties().lightLevel(state -> 5));
            helper.register(id("ward_bed"), bed);
            helper.register(id("ward_monitor"), monitor);
            BED = bed;
            MONITOR = monitor;
        });
        event.register(Registries.ITEM, helper -> {
            BlockItem item = new BlockItem(MONITOR, new Item.Properties()) {
                @Override
                public Component getName(ItemStack stack) {
                    return Component.translatableWithFallback("block.hypersomnia.ward_monitor", "Прикроватный монитор");
                }
            };
            helper.register(id("ward_monitor"), item);
            MONITOR_ITEM = item;
        });
    }

    @SubscribeEvent
    public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("main")))) {
            event.accept(MONITOR_ITEM);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, path);
    }
}
