package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShaftSelectPayload(int target) implements CustomPacketPayload {
    public static final Type<ShaftSelectPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "shaft_select"));
    public static final StreamCodec<ByteBuf, ShaftSelectPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.INT, ShaftSelectPayload::target, ShaftSelectPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
