package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Клиент -> сервер: цифры с кодовой панели замка lock. submit = true — проверить код (все цифры набраны),
 * false — только сохранить черновик (экран закрыт с неполным или непринятым вводом).
 */
public record LabLockCodePayload(int lock, String digits, boolean submit) implements CustomPacketPayload {

    public static final Type<LabLockCodePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "lab_lock_code"));

    public static final StreamCodec<ByteBuf, LabLockCodePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, LabLockCodePayload::lock,
            ByteBufCodecs.stringUtf8(16), LabLockCodePayload::digits,
            ByteBufCodecs.BOOL, LabLockCodePayload::submit,
            LabLockCodePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
