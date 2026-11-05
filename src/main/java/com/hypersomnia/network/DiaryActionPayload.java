package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Клиент -> сервер: положить или передвинуть лист на странице дневника, либо снять его (remove). */
public record DiaryActionPayload(String id, int page, int x, int y, boolean remove) implements CustomPacketPayload {

    public static final Type<DiaryActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "diary_action"));

    public static final StreamCodec<ByteBuf, DiaryActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DiaryActionPayload::id,
            ByteBufCodecs.INT, DiaryActionPayload::page,
            ByteBufCodecs.INT, DiaryActionPayload::x,
            ByteBufCodecs.INT, DiaryActionPayload::y,
            ByteBufCodecs.BOOL, DiaryActionPayload::remove,
            DiaryActionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}