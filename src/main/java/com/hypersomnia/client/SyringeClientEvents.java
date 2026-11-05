package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Тик состояния шприца и дрожь камеры (побочка высокой толерантности и предупреждение об откате). */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class SyringeClientEvents {
    private SyringeClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().isPaused()) return;
        SyringeClientState.tick();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SyringeClientState.reset();
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!SyringeClientState.active) return;
        float amp = 0.12f * SyringeClientState.level + 0.5f * SyringeClientState.warning();
        if (amp <= 0.001f) return;

        double t = System.currentTimeMillis() / 1000.0;
        event.setYaw(event.getYaw() + (float) Math.sin(t * 37.0) * amp);
        event.setPitch(event.getPitch() + (float) Math.sin(t * 43.0 + 1.3) * amp * 0.7f);
        event.setRoll(event.getRoll() + (float) Math.sin(t * 29.0 + 2.1) * amp * 0.5f);
    }
}
