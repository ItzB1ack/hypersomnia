package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.network.WardGhostNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WardGhostClientSetup {
    private WardGhostClientSetup() {}
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> WardGhostNetwork.setClientReceiver(WardGhostClient::receive));
    }
}
