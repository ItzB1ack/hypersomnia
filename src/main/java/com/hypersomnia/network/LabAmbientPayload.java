package com.hypersomnia.network;
import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record LabAmbientPayload(BlockPos pos, int kind, boolean forced) implements CustomPacketPayload {
    public static final Type<LabAmbientPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "lab_ambient"));
    public static final StreamCodec<ByteBuf, LabAmbientPayload> STREAM_CODEC = StreamCodec.of(
        (b,p) -> { b.writeLong(p.pos().asLong()); b.writeByte(p.kind()); b.writeBoolean(p.forced()); },
        b -> new LabAmbientPayload(BlockPos.of(b.readLong()), b.readByte(), b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
