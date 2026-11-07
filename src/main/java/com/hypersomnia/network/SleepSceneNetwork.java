package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Регистрация пакета сцены (по образцу ObserverNetwork). */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class SleepSceneNetwork {
    private SleepSceneNetwork() {}

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                SleepScenePayload.TYPE,
                SleepScenePayload.STREAM_CODEC,
                ClientPayloadBridge::sleepScene
        );
    }
}
