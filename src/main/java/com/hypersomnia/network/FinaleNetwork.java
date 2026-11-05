package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.client.FinaleClientState;
import com.hypersomnia.server.FinaleHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Пакеты финала: детская (Фрагмент, запись), заключительный экран и его закрытие. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class FinaleNetwork {
    private FinaleNetwork() {}

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(FinaleRoomPayload.TYPE, FinaleRoomPayload.STREAM_CODEC, FinaleClientState::handleRoom);
        registrar.playToClient(EndingPayload.TYPE, EndingPayload.STREAM_CODEC, FinaleClientState::handleEnding);
        registrar.playToServer(EndingDonePayload.TYPE, EndingDonePayload.STREAM_CODEC, FinaleHandler::handleDone);
    }
}
