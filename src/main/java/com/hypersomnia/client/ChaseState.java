package com.hypersomnia.client;

import com.hypersomnia.network.ChasePayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayDeque;

/**
 * Погоня на клиенте: очередь пакетов с сервера (их разбирает ObserverClient в своём тике) и день следующей погони
 * (для режиссёра). Без клиентских импортов, как ObserverState: класс упомянут в общей регистрации сети.
 */
public final class ChaseState {
    private ChaseState() {}

    /** С какого дня осколка сервер снова разрешит погоню (0 — уже можно). */
    public static int nextDay;

    private static final ArrayDeque<ChasePayload> QUEUE = new ArrayDeque<>();

    public static void handle(ChasePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            nextDay = payload.nextDay();
            if (payload.phase() != ChasePayload.PH_IDLE) QUEUE.addLast(payload);
            while (QUEUE.size() > 64) QUEUE.removeFirst();
        });
    }

    /** Следующий пакет или null. */
    public static ChasePayload poll() {
        return QUEUE.pollFirst();
    }

    public static void reset() {
        nextDay = 0;
        QUEUE.clear();
    }
}
