package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Подтверждение первого звука либо отказ до старта. Не содержит координат или заданного клиентом кулдауна. */
public record ForestReplyAckPayload(long ticket, int result) implements CustomPacketPayload {
    public static final int STARTED = 0;
    public static final int BLOCKED = 1;
    public static final int NO_SOURCE = 2;
    public static final int NO_AUDIO = 3;
    public static final int INTERRUPTED = 4;

    public static final Type<ForestReplyAckPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "forest_reply_ack"));
    public static final StreamCodec<ByteBuf, ForestReplyAckPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, ForestReplyAckPayload::ticket,
            ByteBufCodecs.VAR_INT, ForestReplyAckPayload::result,
            ForestReplyAckPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}