package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.nightmare.ShaftHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ShaftNetwork {
    private ShaftNetwork() {}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToClient(ShaftOpenPayload.TYPE, ShaftOpenPayload.STREAM_CODEC, ClientPayloadBridge::shaftOpen);
        r.playToServer(ShaftSelectPayload.TYPE, ShaftSelectPayload.STREAM_CODEC, ShaftHandler::handleSelect);
    }
}
