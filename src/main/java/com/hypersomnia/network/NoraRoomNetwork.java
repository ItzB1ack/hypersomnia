package com.hypersomnia.network;
import com.hypersomnia.Hypersomnia;
import com.hypersomnia.server.NoraRoomHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class NoraRoomNetwork {
    private NoraRoomNetwork() {}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        // Версия 2 добавляет layout к NoraRoomPayload; старый codec несовместим.
        var r = event.registrar("2");
        r.playToClient(NoraRoomPayload.TYPE, NoraRoomPayload.STREAM_CODEC, ClientPayloadBridge::noraRoom);
        r.playToServer(NoraRoomAck.TYPE, NoraRoomAck.STREAM_CODEC, NoraRoomHandler::ack);
    }
}
