package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Клиент -> сервер: режиссёр предложил погоню (START) или клиент больше не показывает фигуру погони (STOP).
 * force — отладка /hypersomnia_observer chase: 1 — без стадии, дней и времени суток; 2 — то же и не учебная.
 * Сервер сам проверяет всё остальное (ChaseHandler.start) и право на отладку.
 */
public record ChaseRequestPayload(int action, int force) implements CustomPacketPayload {
    public static final int START = 0;
    public static final int STOP = 1;

    public static final Type<ChaseRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "chase_request"));

    public static final StreamCodec<ByteBuf, ChaseRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChaseRequestPayload::action,
            ByteBufCodecs.VAR_INT, ChaseRequestPayload::force,
            ChaseRequestPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
