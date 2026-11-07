package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Сервер -> клиент: полный список следов игрока (плоский список чисел, см. TraceData.flat). */
public record TraceSyncPayload(int version, List<Integer> flat) implements CustomPacketPayload {
    public static final int FORMAT_VERSION = 2;

    public static final Type<TraceSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "trace_sync"));

    public static final StreamCodec<ByteBuf, TraceSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TraceSyncPayload::version,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), TraceSyncPayload::flat,
            TraceSyncPayload::new
    );
    public TraceSyncPayload(List<Integer> flat) { this(FORMAT_VERSION, flat); }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}