package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Клиент предлагает эпизод у двери; сервер проверяет место и игрока. */
public record DoorEpisodeRequest(int action, int sequence, BlockPos door) implements CustomPacketPayload {
    public static final int START = 0, CANCEL = 1;
    public static final Type<DoorEpisodeRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "door_episode_request"));
    public static final StreamCodec<ByteBuf, DoorEpisodeRequest> STREAM_CODEC = StreamCodec.of(
        (b, p) -> { b.writeByte(p.action()); b.writeInt(p.sequence()); b.writeLong(p.door().asLong()); },
        b -> new DoorEpisodeRequest(b.readByte(), b.readInt(), BlockPos.of(b.readLong())));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
