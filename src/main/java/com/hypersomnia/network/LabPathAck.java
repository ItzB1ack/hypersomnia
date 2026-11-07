package com.hypersomnia.network;
import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record LabPathAck(int token) implements CustomPacketPayload {
    public static final Type<LabPathAck> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "lab_path_ack"));
    public static final StreamCodec<ByteBuf, LabPathAck> STREAM_CODEC = StreamCodec.of((b,p) -> b.writeInt(p.token()), b -> new LabPathAck(b.readInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
