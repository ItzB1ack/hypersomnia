package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Клиент -> сервер: выбор знака для буквы на странице ключа; sym < 0 стирает. */
public record DiaryKeyPayload(String letter, int sym) implements CustomPacketPayload {

    public static final Type<DiaryKeyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "diary_key"));

    public static final StreamCodec<ByteBuf, DiaryKeyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DiaryKeyPayload::letter,
            ByteBufCodecs.INT, DiaryKeyPayload::sym,
            DiaryKeyPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
