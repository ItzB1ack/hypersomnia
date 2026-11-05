package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Сервер -> клиент: все листы в дневнике, строками "id|page|x|y" (DiaryData.toStrings), и таблица ключа "A=3". */
public record DiarySyncPayload(List<String> entries, List<String> key) implements CustomPacketPayload {

    public static final Type<DiarySyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "diary_sync"));

    public static final StreamCodec<ByteBuf, DiarySyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), DiarySyncPayload::entries,    // [проверить]
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), DiarySyncPayload::key,
            DiarySyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}