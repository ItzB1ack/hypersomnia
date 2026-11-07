package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class TraceNetwork {
    private TraceNetwork() {}

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // Изменился wire-формат trace_sync; несовместимые клиенты отклоняются при согласовании.
        event.registrar("2").playToClient(
                TraceSyncPayload.TYPE,
                TraceSyncPayload.STREAM_CODEC,
                ClientPayloadBridge::trace
        );
    }
}