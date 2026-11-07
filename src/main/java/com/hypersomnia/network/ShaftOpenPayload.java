package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShaftOpenPayload(int current) implements CustomPacketPayload {
    public static final Type<ShaftOpenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "shaft_open"));
    public static final StreamCodec<ByteBuf, ShaftOpenPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.INT, ShaftOpenPayload::current, ShaftOpenPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
