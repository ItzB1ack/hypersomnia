package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Сервер предлагает один немедленный ответ, а не ставит событие в клиентскую очередь. */
public record ForestReplyPayload(long ticket, int gapTicks, boolean forced) implements CustomPacketPayload {
    public static final Type<ForestReplyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "forest_reply"));
    public static final StreamCodec<ByteBuf, ForestReplyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, ForestReplyPayload::ticket,
            ByteBufCodecs.VAR_INT, ForestReplyPayload::gapTicks,
            ByteBufCodecs.BOOL, ForestReplyPayload::forced,
            ForestReplyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}