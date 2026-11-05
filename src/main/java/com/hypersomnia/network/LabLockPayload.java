package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Сервер -> клиент: открыть кодовую панель замка (OPEN) или результат проверки.
 * draft — набранные ранее цифры (черновик панели, LabTuning.lockKeepsInput) или пусто.
 */
public record LabLockPayload(int lock, int state, String draft) implements CustomPacketPayload {
    public static final int OPEN = -1;
    public static final int DENIED = 0;
    public static final int GRANTED = 1;
    /** Индекс не принят: у игрока найдены не все воспоминания. */
    public static final int INCOMPLETE = 2;
    /** Панели нужно питание MAIN FEED, а его нет. */
    public static final int NOPOWER = 3;

    public static final Type<LabLockPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "lab_lock"));

    public static final StreamCodec<ByteBuf, LabLockPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, LabLockPayload::lock,
            ByteBufCodecs.INT, LabLockPayload::state,
            ByteBufCodecs.stringUtf8(16), LabLockPayload::draft,
            LabLockPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
