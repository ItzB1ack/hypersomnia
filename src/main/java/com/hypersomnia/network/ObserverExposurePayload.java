package com.hypersomnia.network;
import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record ObserverExposurePayload(int points) implements CustomPacketPayload {
    public static final Type<ObserverExposurePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "observer_exposure"));
    public static final StreamCodec<ByteBuf, ObserverExposurePayload> STREAM_CODEC = StreamCodec.of((b,p) -> b.writeInt(p.points()), b -> new ObserverExposurePayload(b.readInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
