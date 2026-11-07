package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DoorEpisodeStatus(int sequence, int status) implements CustomPacketPayload {
    public static final int STARTED = 0, ENDED = 1, REJECTED = 2;
    public static final Type<DoorEpisodeStatus> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "door_episode_status"));
    public static final StreamCodec<ByteBuf, DoorEpisodeStatus> STREAM_CODEC = StreamCodec.of(
        (b, p) -> { b.writeInt(p.sequence()); b.writeByte(p.status()); },
        b -> new DoorEpisodeStatus(b.readInt(), b.readByte()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
