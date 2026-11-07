package com.hypersomnia.network;
import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record LabPathPayload(int token, int ticks, BlockPos base, int dx, int dz) implements CustomPacketPayload {
    public static final Type<LabPathPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "lab_path"));
    public static final StreamCodec<ByteBuf, LabPathPayload> STREAM_CODEC = StreamCodec.of(
        (b, p) -> { b.writeInt(p.token()); b.writeInt(p.ticks()); b.writeLong(p.base().asLong()); b.writeByte(p.dx()); b.writeByte(p.dz()); },
        b -> new LabPathPayload(b.readInt(), b.readInt(), BlockPos.of(b.readLong()), b.readByte(), b.readByte()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
