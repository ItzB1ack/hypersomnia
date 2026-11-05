package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.client.TraceClientState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class TraceNetwork {
    private TraceNetwork() {}

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                TraceSyncPayload.TYPE,
                TraceSyncPayload.STREAM_CODEC,
                TraceClientState::handleSync
        );
    }
}