package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.server.DoorEpisodeHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class DoorEpisodeNetwork {
    private DoorEpisodeNetwork() {}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToServer(DoorEpisodeRequest.TYPE, DoorEpisodeRequest.STREAM_CODEC, DoorEpisodeHandler::handle);
        r.playToClient(DoorEpisodeStatus.TYPE, DoorEpisodeStatus.STREAM_CODEC, ClientPayloadBridge::doorEpisode);
    }
}
