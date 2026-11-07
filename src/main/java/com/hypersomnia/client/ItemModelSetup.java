package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Предметное свойство hypersomnia:using (0..1 — прогресс).
 * Шприц: положение поршня по удержанию ПКМ. Дневник: клиентская анимация
 * раскрытия после обычного ПКМ (обложка), закрытия после выхода из GUI.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ItemModelSetup {
    private ItemModelSetup() {}

    private static final ResourceLocation USING =
            ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "using");

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            registerHeldProgress(ModItems.ADRENALINE_SYRINGE.get());
            registerDiary(ModItems.DIARY.get());
        });
    }

    /** Положение поршня (0..1) по удержанию ПКМ: шприц. Кривую и субтики считает SyringeViewModel. */
    private static void registerHeldProgress(Item item) {
        ItemProperties.register(item, USING,
                (stack, level, entity, seed) -> SyringeViewModel.modelProgress(entity, stack));
    }

    /** Клиентская анимация раскрытия без удержания: дневник. Только в руках самого игрока. */
    private static void registerDiary(Item item) {
        ItemProperties.register(item, USING, (stack, level, entity, seed) ->
                entity != null && entity == Minecraft.getInstance().player ? DiaryOpenAnim.progress() : 0.0F);
    }
}
