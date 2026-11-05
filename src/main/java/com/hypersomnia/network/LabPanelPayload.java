package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Сервер -> клиент: состояние щита MAIN FEED. seq — замкнутые рубильники (цифры 1..5 в порядке замыкания, 0 = все разомкнуты),
 * flags — FEED | TRIPPED | OPEN (OPEN: открыть экран щита, иначе только обновить открытый).
 */
public record LabPanelPayload(int seq, int flags) implements CustomPacketPayload {
    public static final int FEED = 1;
    public static final int TRIPPED = 2;
    public static final int OPEN = 4;

    public static final Type<LabPanelPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "lab_panel"));

    public static final StreamCodec<ByteBuf, LabPanelPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LabPanelPayload::seq,
            ByteBufCodecs.VAR_INT, LabPanelPayload::flags,
            LabPanelPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
