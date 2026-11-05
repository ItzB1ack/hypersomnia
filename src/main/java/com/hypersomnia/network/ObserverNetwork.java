package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.client.ObserverState;
import com.hypersomnia.server.TraceHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Регистрация пакета наблюдателя отдельно от Hypersomnia.java, чтобы не трогать основной класс. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ObserverNetwork {
    private ObserverNetwork() {}

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // "2": добавлен observer_seen (клиент -> сервер); "3": погоня (chase_request клиент -> сервер, chase сервер -> клиент)
        var registrar = event.registrar("3");
        registrar.playToClient(
                MadnessSyncPayload.TYPE,
                MadnessSyncPayload.STREAM_CODEC,
                ObserverState::handleMadness
        );
        registrar.playToClient(
                ObserverStagePayload.TYPE,
                ObserverStagePayload.STREAM_CODEC,
                ObserverState::handleStage
        );
        registrar.playToServer(
                ObserverSeenPayload.TYPE,
                ObserverSeenPayload.STREAM_CODEC,
                TraceHandler::handleSeen
        );
        registrar.playToServer(
                ChaseRequestPayload.TYPE,
                ChaseRequestPayload.STREAM_CODEC,
                com.hypersomnia.chase.ChaseHandler::handleRequest
        );
        registrar.playToClient(
                ChasePayload.TYPE,
                ChasePayload.STREAM_CODEC,
                com.hypersomnia.client.ChaseState::handle
        );
    }
}
