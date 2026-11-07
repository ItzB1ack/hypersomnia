package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Предметное свойство hypersomnia:using (0..1 — прогресс удержания ПКМ).
 * Переключает кадры 3D-моделей шприца (поршень) и дневника (обложка).
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ItemModelSetup {
    private ItemModelSetup() {}

    private static final ResourceLocation USING =
            ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "using");

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            register(ModItems.ADRENALINE_SYRINGE.get());
            register(ModItems.DIARY.get());
        });
    }

    private static void register(Item item) {
        ItemProperties.register(item, USING, (stack, level, entity, seed) -> {
            if (entity == null || !entity.isUsingItem()) return 0.0F;
            ItemStack used = entity.getUseItem();
            if (!ItemStack.isSameItemSameComponents(used, stack)) return 0.0F;
            int duration = stack.getUseDuration(entity);
            if (duration <= 0) return 1.0F;
            return 1.0F - (float) entity.getUseItemRemainingTicks() / (float) duration;
        });
    }
}
