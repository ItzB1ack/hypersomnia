package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Сервер -> клиент: зерно вариантов шифров сохранения (LabSavedData.cipherSeed). При входе и после index reroll. */
public record LabSeedPayload(long seed) implements CustomPacketPayload {

    public static final Type<LabSeedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "lab_seed"));

    public static final StreamCodec<ByteBuf, LabSeedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, LabSeedPayload::seed,
            LabSeedPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
