package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Клиентские события мира снов: тик состояния, холодный туман. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class DreamClientEvents {
    private DreamClientEvents() {}

    private static final float FOG_FAR = 72f;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        DreamClientState.tick(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        DreamClientState.reset();
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!DreamClientState.isDream(Minecraft.getInstance())) return;
        event.setRed(0.05f);
        event.setGreen(0.07f);
        event.setBlue(0.10f);
    }

    /** Вблизи мир чёткий, вдали тонет в холодной дымке: «воспоминание, которое плохо помнится». */
    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!DreamClientState.isDream(Minecraft.getInstance())) return;
        if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || event.getType() != FogType.NONE) return;

        event.setNearPlaneDistance(0f);
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), FOG_FAR));
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }
}
