package com.hypersomnia.client;

import com.hypersomnia.network.TraceSyncPayload;
import com.hypersomnia.trace.TraceData;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** Следы на клиенте. Без клиентских импортов Minecraft (безопасно на сервере). */
public final class TraceClientState {
    private TraceClientState() {}

    public static volatile List<TraceData.Entry> entries = List.of();

    public static void handleSync(TraceSyncPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> entries = TraceData.fromFlat(payload.flat()));
    }

    public static void reset() {
        entries = List.of();
    }
}