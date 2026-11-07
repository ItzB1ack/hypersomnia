package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Только собственное состояние кошмара игрока, не координаты или состояние других игроков. */
public record WardGhostPayload(boolean active, int loops) implements CustomPacketPayload {
    public static final Type<WardGhostPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "ward_ghosts"));
    public static final StreamCodec<ByteBuf, WardGhostPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, WardGhostPayload::active,
            ByteBufCodecs.VAR_INT, WardGhostPayload::loops, WardGhostPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
