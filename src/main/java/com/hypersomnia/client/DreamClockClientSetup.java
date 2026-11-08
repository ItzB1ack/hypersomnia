package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * [новое] Клиентская часть часов сна: модели маятника и стрелок (их поворачивает
 * {@link DreamClockRenderer}) и сам рендерер блок-сущности.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DreamClockClientSetup {
    private DreamClockClientSetup() {}

    @SubscribeEvent
    public static void additionalModels(ModelEvent.RegisterAdditional event) {
        event.register(DreamClockRenderer.PENDULUM);
        event.register(DreamClockRenderer.HAND_MINUTE);
        event.register(DreamClockRenderer.HAND_HOUR);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.DREAM_CLOCK.get(), DreamClockRenderer::new);
    }
}
