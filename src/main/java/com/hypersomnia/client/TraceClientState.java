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
        ctx.enqueueWork(() -> {
            if (payload.version() < 1 || payload.version() > TraceSyncPayload.FORMAT_VERSION) return;
            entries = TraceData.fromFlat(payload.version(), payload.flat());
        });
    }

    public static void reset() {
        entries = List.of();
    }
}