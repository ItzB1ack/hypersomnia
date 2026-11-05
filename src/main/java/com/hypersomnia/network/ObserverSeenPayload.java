package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Клиент -> сервер: игрок прогнал фигуру взглядом, она стояла здесь (блок под ногами фигуры, Верхний мир).
 * Сервер сам проверяет стадию, мир, расстояние и частоту (TraceHandler.seen) и только запоминает место.
 */
public record ObserverSeenPayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<ObserverSeenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "observer_seen"));

    public static final StreamCodec<ByteBuf, ObserverSeenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ObserverSeenPayload::pos,
            ObserverSeenPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
