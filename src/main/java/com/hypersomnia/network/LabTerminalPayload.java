package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Сервер -> клиент: открыть терминал terminal со снимком доступа (id видимых и открываемых записей). */
public record LabTerminalPayload(int terminal, List<String> listed, List<String> readable) implements CustomPacketPayload {

    public static final Type<LabTerminalPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "lab_terminal"));

    // [проверить] ByteBufCodecs.list() как CodecOperation для StreamCodec.apply
    public static final StreamCodec<ByteBuf, LabTerminalPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, LabTerminalPayload::terminal,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), LabTerminalPayload::listed,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), LabTerminalPayload::readable,
            LabTerminalPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
