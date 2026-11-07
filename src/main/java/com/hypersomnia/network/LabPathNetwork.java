package com.hypersomnia.network;
import com.hypersomnia.Hypersomnia;
import com.hypersomnia.server.LabPathHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class LabPathNetwork {
    private LabPathNetwork() {}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToClient(LabPathPayload.TYPE, LabPathPayload.STREAM_CODEC, ClientPayloadBridge::labPath);
        r.playToServer(LabPathAck.TYPE, LabPathAck.STREAM_CODEC, LabPathHandler::ack);
    }
}
