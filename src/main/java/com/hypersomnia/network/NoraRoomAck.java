package com.hypersomnia.network;
import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record NoraRoomAck(int token) implements CustomPacketPayload {
    public static final Type<NoraRoomAck> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "nora_room_ack"));
    public static final StreamCodec<ByteBuf, NoraRoomAck> STREAM_CODEC = StreamCodec.of((buf, p) -> buf.writeInt(p.token()), buf -> new NoraRoomAck(buf.readInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
