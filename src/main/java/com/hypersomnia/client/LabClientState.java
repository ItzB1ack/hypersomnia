package com.hypersomnia.client;

import com.hypersomnia.network.LabLiftPayload;
import com.hypersomnia.network.LabLockPayload;
import com.hypersomnia.network.LabPanelPayload;
import com.hypersomnia.network.LabSeedPayload;
import com.hypersomnia.network.LabTerminalPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Обработчики пакетов лаборатории на клиенте. Без клиентских импортов Minecraft (безопасно на сервере). */
public final class LabClientState {
    private LabClientState() {}

    /** Зерно вариантов шифров текущего мира (LabSeedPayload при входе). seeded = false — ещё не пришло. */
    public static volatile long cipherSeed;
    public static volatile boolean seeded;

    public static void handleSeed(LabSeedPayload pl, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            cipherSeed = pl.seed();
            seeded = true;
        });
    }

    public static void handleLock(LabLockPayload pl, IPayloadContext ctx) {
        ctx.enqueueWork(() -> LabLockScreen.receive(pl.lock(), pl.state(), pl.draft()));
    }

    public static void handleLift(LabLiftPayload pl, IPayloadContext ctx) {
        ctx.enqueueWork(() -> LabLiftScreen.receive(pl.current(), pl.enabled(), pl.built()));
    }

    public static void handleTerminal(LabTerminalPayload pl, IPayloadContext ctx) {
        ctx.enqueueWork(() -> LabTerminalScreen.receive(pl.terminal(), pl.listed(), pl.readable()));
    }

    public static void handlePanel(LabPanelPayload pl, IPayloadContext ctx) {
        ctx.enqueueWork(() -> LabPanelScreen.receive(pl.seq(), pl.flags()));
    }
}
