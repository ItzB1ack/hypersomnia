package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Сервер -> клиент: показать заключительный экран концовки (FinaleTuning.OBLIVION..SUBJECT_48), flags — FinaleTuning.FLAG_*. */
public record EndingPayload(int ending, int flags) implements CustomPacketPayload {

    public static final Type<EndingPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "ending"));

    public static final StreamCodec<ByteBuf, EndingPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EndingPayload::ending,
            ByteBufCodecs.VAR_INT, EndingPayload::flags,
            EndingPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
