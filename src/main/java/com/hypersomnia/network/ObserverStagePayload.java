package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Сервер -> клиент: прогресс, из которого клиент считает стадию осколка, и память привычек
 * (маршрут x,y,z,вес и места сна x,y,z,счёт четвёрками; только непустые ячейки).
 * stageOverride: стадия, заданная отладкой, или -1 (по формуле).
 * finale: состояние финала FinaleTuning.pack (концовка + «правда»); после финала клиент выключает Фрагмента.
 */
public record ObserverStagePayload(int days, int memories, int stageOverride, List<Integer> route, List<Integer> sleep,
                                   int finale)
        implements CustomPacketPayload {

    public static final Type<ObserverStagePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "observer_stage"));

    public static final StreamCodec<ByteBuf, ObserverStagePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ObserverStagePayload::days,
            ByteBufCodecs.INT, ObserverStagePayload::memories,
            ByteBufCodecs.INT, ObserverStagePayload::stageOverride,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), ObserverStagePayload::route,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), ObserverStagePayload::sleep,
            ByteBufCodecs.VAR_INT, ObserverStagePayload::finale,
            ObserverStagePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
