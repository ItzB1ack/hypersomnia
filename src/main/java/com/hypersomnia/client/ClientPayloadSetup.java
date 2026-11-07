package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.network.ClientPayloadBridge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Привязка клиентских обработчиков, не загружаемая выделенным сервером. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientPayloadSetup {
    private ClientPayloadSetup() {}
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ClientPayloadBridge.fatigueHandler = ClientFatigueState::handleSync;
            ClientPayloadBridge.labAmbientHandler = LabAmbientClient::handle;
            ClientPayloadBridge.observerExposureHandler = ObserverState::handleExposure;
            ClientPayloadBridge.labPathHandler = LabPathClient::handle;
            ClientPayloadBridge.noraRoomHandler = NoraRoomClient::handle;
            ClientPayloadBridge.doorEpisodeHandler = DoorEcho::handleStatus;
            ClientPayloadBridge.shaftOpenHandler = ShaftScreen::receive;
            ClientPayloadBridge.diaryHandler = DiaryClientState::handleSync;
            ClientPayloadBridge.finaleRoomHandler = FinaleClientState::handleRoom;
            ClientPayloadBridge.endingHandler = FinaleClientState::handleEnding;
            ClientPayloadBridge.labLockHandler = LabClientState::handleLock;
            ClientPayloadBridge.labLiftHandler = LabClientState::handleLift;
            ClientPayloadBridge.labTerminalHandler = LabClientState::handleTerminal;
            ClientPayloadBridge.labPanelHandler = LabClientState::handlePanel;
            ClientPayloadBridge.labSeedHandler = LabClientState::handleSeed;
            ClientPayloadBridge.madnessHandler = ObserverState::handleMadness;
            ClientPayloadBridge.stageHandler = ObserverState::handleStage;
            ClientPayloadBridge.chaseHandler = ChaseState::handle;
            ClientPayloadBridge.sleepSceneHandler = SleepSceneState::handle;
            ClientPayloadBridge.syringeHandler = SyringeClientState::handle;
            ClientPayloadBridge.traceHandler = TraceClientState::handleSync;
        });
    }
}
