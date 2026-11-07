package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.server.DiaryHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class DiaryNetwork {
    private DiaryNetwork() {}

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(DiarySyncPayload.TYPE, DiarySyncPayload.STREAM_CODEC, ClientPayloadBridge::diary);
        registrar.playToServer(DiaryActionPayload.TYPE, DiaryActionPayload.STREAM_CODEC, DiaryHandler::handleAction);
        registrar.playToServer(DiaryKeyPayload.TYPE, DiaryKeyPayload.STREAM_CODEC, DiaryHandler::handleKey);
    }
}