package com.hypersomnia.network;
import com.hypersomnia.Hypersomnia;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ObserverExposureNetwork {
    private ObserverExposureNetwork() {}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(ObserverExposurePayload.TYPE, ObserverExposurePayload.STREAM_CODEC, ClientPayloadBridge::observerExposure);
    }
}
