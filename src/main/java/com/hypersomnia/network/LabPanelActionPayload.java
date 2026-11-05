package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Клиент -> сервер: рубильник на экране щита (0 = MAIN FEED, 1..5 = линии). Сервер проверяет щит, расстояние и номер. */
public record LabPanelActionPayload(int breaker) implements CustomPacketPayload {

    public static final Type<LabPanelActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "lab_panel_action"));

    public static final StreamCodec<ByteBuf, LabPanelActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LabPanelActionPayload::breaker,
            LabPanelActionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
