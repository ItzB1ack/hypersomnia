package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Сервер -> клиент: игрок в детской финала (inside) и где стоит Фрагмент (метка X, блок над ковром).
 * play: запись mem_7 проиграна на мониторе — PLAY_HEARD (Фрагмент услышал правду), PLAY_UNHEARD (нет отклика) или PLAY_NONE.
 */
public record FinaleRoomPayload(boolean inside, BlockPos fragment, int play) implements CustomPacketPayload {
    public static final int PLAY_NONE = 0;
    public static final int PLAY_HEARD = 1;
    public static final int PLAY_UNHEARD = 2;

    public static final Type<FinaleRoomPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "finale_room"));

    public static final StreamCodec<ByteBuf, FinaleRoomPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, FinaleRoomPayload::inside,
            BlockPos.STREAM_CODEC, FinaleRoomPayload::fragment,
            ByteBufCodecs.VAR_INT, FinaleRoomPayload::play,
            FinaleRoomPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
