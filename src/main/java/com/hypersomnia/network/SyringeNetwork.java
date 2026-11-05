package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.client.SyringeClientState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class SyringeNetwork {
    private SyringeNetwork() {}

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                SyringeSyncPayload.TYPE,
                SyringeSyncPayload.STREAM_CODEC,
                SyringeClientState::handle
        );
    }
}
