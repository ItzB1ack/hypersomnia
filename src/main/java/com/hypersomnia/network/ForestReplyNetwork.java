package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.server.ForestReplyHandler;
import java.util.function.Consumer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Общая регистрация намеренно не импортирует и не загружает ни одного Minecraft client-класса. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ForestReplyNetwork {
    private ForestReplyNetwork() {}

    private static Consumer<ForestReplyPayload> clientReceiver = payload -> {};

    public static void setClientReceiver(Consumer<ForestReplyPayload> receiver) {
        clientReceiver = receiver;
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(ForestReplyPayload.TYPE, ForestReplyPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> clientReceiver.accept(payload)));
        registrar.playToServer(ForestReplyAckPayload.TYPE, ForestReplyAckPayload.STREAM_CODEC,
                ForestReplyHandler::handleAck);
    }
}