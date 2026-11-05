package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Сервер -> клиент: текущая усталость и идёт ли падение. */
public record FatigueSyncPayload(int fatigue, boolean collapsing) implements CustomPacketPayload {

    public static final Type<FatigueSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "fatigue_sync"));

    public static final StreamCodec<ByteBuf, FatigueSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FatigueSyncPayload::fatigue,
            ByteBufCodecs.BOOL, FatigueSyncPayload::collapsing,
            FatigueSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
