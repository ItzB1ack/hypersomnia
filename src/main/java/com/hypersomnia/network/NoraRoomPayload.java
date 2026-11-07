package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Индивидуальная иллюзия: опорная кровать, проверенные клетки и маска элементов. */
public record NoraRoomPayload(int token, int ticks, BlockPos bed, BlockPos a, BlockPos b, BlockPos c, int layout) implements CustomPacketPayload {
    public static final Type<NoraRoomPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "nora_room"));
    public static final StreamCodec<ByteBuf, NoraRoomPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeInt(p.token()); buf.writeInt(p.ticks()); buf.writeLong(p.bed().asLong()); buf.writeLong(p.a().asLong()); buf.writeLong(p.b().asLong()); buf.writeLong(p.c().asLong()); buf.writeByte(p.layout()); },
        buf -> new NoraRoomPayload(buf.readInt(), buf.readInt(), BlockPos.of(buf.readLong()), BlockPos.of(buf.readLong()), BlockPos.of(buf.readLong()), BlockPos.of(buf.readLong()), buf.readUnsignedByte()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
