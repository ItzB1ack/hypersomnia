package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import java.util.function.Consumer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Общая регистрация пакета не загружает клиентские классы на сервере. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class WardGhostNetwork {
    private WardGhostNetwork() {}
    private static Consumer<WardGhostPayload> clientReceiver = payload -> {};
    public static void setClientReceiver(Consumer<WardGhostPayload> receiver) { clientReceiver = receiver; }
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(WardGhostPayload.TYPE, WardGhostPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> clientReceiver.accept(payload)));
    }
}
