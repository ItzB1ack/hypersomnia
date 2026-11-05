package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Сервер -> клиент: открыть панель лифта. Маски: бит n = остановка n (0 = поверхность). */
public record LabLiftPayload(int current, int enabled, int built) implements CustomPacketPayload {

    public static final Type<LabLiftPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "lab_lift"));

    public static final StreamCodec<ByteBuf, LabLiftPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, LabLiftPayload::current,
            ByteBufCodecs.INT, LabLiftPayload::enabled,
            ByteBufCodecs.INT, LabLiftPayload::built,
            LabLiftPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
