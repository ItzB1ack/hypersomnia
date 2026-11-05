package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Клиент -> сервер: заключительный экран закрыт, игрока можно будить (сервер проверяет, что финал пройден и игрок во сне). */
public record EndingDonePayload() implements CustomPacketPayload {

    public static final Type<EndingDonePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "ending_done"));

    public static final StreamCodec<ByteBuf, EndingDonePayload> STREAM_CODEC = StreamCodec.unit(new EndingDonePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
