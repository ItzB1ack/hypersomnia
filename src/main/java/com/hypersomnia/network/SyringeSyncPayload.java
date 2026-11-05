package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Сервер -> клиент: сколько тиков осталось искусственного бодрствования и уровень побочек (0..3). Толерантность не раскрывается. */
public record SyringeSyncPayload(int wakeTicks, int level) implements CustomPacketPayload {

    public static final Type<SyringeSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "syringe_sync"));

    public static final StreamCodec<ByteBuf, SyringeSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SyringeSyncPayload::wakeTicks,
            ByteBufCodecs.VAR_INT, SyringeSyncPayload::level,
            SyringeSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
