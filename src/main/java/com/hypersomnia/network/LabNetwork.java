package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.server.LabHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class LabNetwork {
    private LabNetwork() {}

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // "2": формат lab_lock / lab_lock_code изменился (черновик, строка цифр), добавлен lab_seed
        var registrar = event.registrar("2");
        registrar.playToClient(LabLockPayload.TYPE, LabLockPayload.STREAM_CODEC, ClientPayloadBridge::labLock);
        registrar.playToServer(LabLockCodePayload.TYPE, LabLockCodePayload.STREAM_CODEC, LabHandler::handleLockCode);
        registrar.playToClient(LabLiftPayload.TYPE, LabLiftPayload.STREAM_CODEC, ClientPayloadBridge::labLift);
        registrar.playToServer(LabLiftSelectPayload.TYPE, LabLiftSelectPayload.STREAM_CODEC, LabHandler::handleLiftSelect);
        registrar.playToClient(LabTerminalPayload.TYPE, LabTerminalPayload.STREAM_CODEC, ClientPayloadBridge::labTerminal);
        registrar.playToClient(LabPanelPayload.TYPE, LabPanelPayload.STREAM_CODEC, ClientPayloadBridge::labPanel);
        registrar.playToClient(LabSeedPayload.TYPE, LabSeedPayload.STREAM_CODEC, ClientPayloadBridge::labSeed);
        registrar.playToServer(LabPanelActionPayload.TYPE, LabPanelActionPayload.STREAM_CODEC, LabHandler::handlePanelAction);
    }
}
