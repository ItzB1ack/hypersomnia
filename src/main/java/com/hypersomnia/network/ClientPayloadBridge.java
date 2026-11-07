package com.hypersomnia.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

/** Регистрируется на обеих сторонах; реализация устанавливается только клиентом. */
public final class ClientPayloadBridge {
    private ClientPayloadBridge() {}
    public static volatile IPayloadHandler<FatigueSyncPayload> fatigueHandler = (payload, context) -> {};
    public static void fatigue(FatigueSyncPayload payload, IPayloadContext context) { fatigueHandler.handle(payload, context); }
    public static volatile IPayloadHandler<LabAmbientPayload> labAmbientHandler = (payload, context) -> {};
    public static void labAmbient(LabAmbientPayload payload, IPayloadContext context) { labAmbientHandler.handle(payload, context); }
    public static volatile IPayloadHandler<ObserverExposurePayload> observerExposureHandler = (payload, context) -> {};
    public static void observerExposure(ObserverExposurePayload payload, IPayloadContext context) { observerExposureHandler.handle(payload, context); }
    public static volatile IPayloadHandler<LabPathPayload> labPathHandler = (payload, context) -> {};
    public static void labPath(LabPathPayload payload, IPayloadContext context) { labPathHandler.handle(payload, context); }
    public static volatile IPayloadHandler<NoraRoomPayload> noraRoomHandler = (payload, context) -> {};
    public static void noraRoom(NoraRoomPayload payload, IPayloadContext context) { noraRoomHandler.handle(payload, context); }
    public static volatile IPayloadHandler<DoorEpisodeStatus> doorEpisodeHandler = (payload, context) -> {};
    public static void doorEpisode(DoorEpisodeStatus payload, IPayloadContext context) { doorEpisodeHandler.handle(payload, context); }
    public static volatile IPayloadHandler<ShaftOpenPayload> shaftOpenHandler = (payload, context) -> {};
    public static void shaftOpen(ShaftOpenPayload payload, IPayloadContext context) { shaftOpenHandler.handle(payload, context); }
    public static volatile IPayloadHandler<DiarySyncPayload> diaryHandler = (payload, context) -> {};
    public static void diary(DiarySyncPayload payload, IPayloadContext context) { diaryHandler.handle(payload, context); }
    public static volatile IPayloadHandler<FinaleRoomPayload> finaleRoomHandler = (payload, context) -> {};
    public static void finaleRoom(FinaleRoomPayload payload, IPayloadContext context) { finaleRoomHandler.handle(payload, context); }
    public static volatile IPayloadHandler<EndingPayload> endingHandler = (payload, context) -> {};
    public static void ending(EndingPayload payload, IPayloadContext context) { endingHandler.handle(payload, context); }
    public static volatile IPayloadHandler<LabLockPayload> labLockHandler = (payload, context) -> {};
    public static void labLock(LabLockPayload payload, IPayloadContext context) { labLockHandler.handle(payload, context); }
    public static volatile IPayloadHandler<LabLiftPayload> labLiftHandler = (payload, context) -> {};
    public static void labLift(LabLiftPayload payload, IPayloadContext context) { labLiftHandler.handle(payload, context); }
    public static volatile IPayloadHandler<LabTerminalPayload> labTerminalHandler = (payload, context) -> {};
    public static void labTerminal(LabTerminalPayload payload, IPayloadContext context) { labTerminalHandler.handle(payload, context); }
    public static volatile IPayloadHandler<LabPanelPayload> labPanelHandler = (payload, context) -> {};
    public static void labPanel(LabPanelPayload payload, IPayloadContext context) { labPanelHandler.handle(payload, context); }
    public static volatile IPayloadHandler<LabSeedPayload> labSeedHandler = (payload, context) -> {};
    public static void labSeed(LabSeedPayload payload, IPayloadContext context) { labSeedHandler.handle(payload, context); }
    public static volatile IPayloadHandler<MadnessSyncPayload> madnessHandler = (payload, context) -> {};
    public static void madness(MadnessSyncPayload payload, IPayloadContext context) { madnessHandler.handle(payload, context); }
    public static volatile IPayloadHandler<ObserverStagePayload> stageHandler = (payload, context) -> {};
    public static void stage(ObserverStagePayload payload, IPayloadContext context) { stageHandler.handle(payload, context); }
    public static volatile IPayloadHandler<ChasePayload> chaseHandler = (payload, context) -> {};
    public static void chase(ChasePayload payload, IPayloadContext context) { chaseHandler.handle(payload, context); }
    public static volatile IPayloadHandler<SleepScenePayload> sleepSceneHandler = (payload, context) -> {};
    public static void sleepScene(SleepScenePayload payload, IPayloadContext context) { sleepSceneHandler.handle(payload, context); }
    public static volatile IPayloadHandler<SyringeSyncPayload> syringeHandler = (payload, context) -> {};
    public static void syringe(SyringeSyncPayload payload, IPayloadContext context) { syringeHandler.handle(payload, context); }
    public static volatile IPayloadHandler<TraceSyncPayload> traceHandler = (payload, context) -> {};
    public static void trace(TraceSyncPayload payload, IPayloadContext context) { traceHandler.handle(payload, context); }
}
