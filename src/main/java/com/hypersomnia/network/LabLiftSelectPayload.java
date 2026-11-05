package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Клиент -> сервер: выбранная остановка. */
public record LabLiftSelectPayload(int target) implements CustomPacketPayload {

    public static final Type<LabLiftSelectPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "lab_lift_select"));

    public static final StreamCodec<ByteBuf, LabLiftSelectPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, LabLiftSelectPayload::target,
            LabLiftSelectPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
