package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Сервер -> клиент: запустить сцену. seed определяет, какие фразы выпадут. */
public record SleepScenePayload(int seed) implements CustomPacketPayload {

    public static final Type<SleepScenePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "sleep_scene"));

    public static final StreamCodec<ByteBuf, SleepScenePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SleepScenePayload::seed,
            SleepScenePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
