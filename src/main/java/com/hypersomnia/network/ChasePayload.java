package com.hypersomnia.network;

import com.hypersomnia.Hypersomnia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Сервер -> клиент: состояние погони (ChaseHandler). Каждый тик, пока она идёт; конец и отказ — отдельным пакетом.
 * run — номер погони (0 — нет), phase — PH_*, info — исход O_* (PH_END) или причина R_* (PH_REJECT),
 * x/y/z — где его ноги, stare — сколько взгляда уже набрано (0..1), nextDay — с какого дня осколка возможна следующая.
 */
public record ChasePayload(int run, int phase, int info, double x, double y, double z, float stare, int nextDay)
        implements CustomPacketPayload {

    public static final int PH_IDLE = 0, PH_WARN = 1, PH_RUN = 2, PH_BLOCKED = 3, PH_CAUGHT = 4, PH_END = 5, PH_REJECT = 6;

    /** Исходы: выдержал взгляд, продержался, ушёл от двери, учебная (остановился рядом), поймал, прервана. */
    public static final int O_NONE = 0, O_STARED = 1, O_ESCAPED = 2, O_BLOCKED = 3, O_TAUGHT = 4, O_CAUGHT = 5, O_ABORT = 6;

    /** Флаги в info вне PH_END и PH_REJECT: I_DARK — «обесточено» (лаборатория в темноте, LabOutage): глаза видны издалека. */
    public static final int I_DARK = 1;
    public static final int I_DOOR = 2;

    /** Причины отказа (для журнала режиссёра). */
    public static final int R_BUSY = 1, R_STAGE = 2, R_DAYS = 3, R_CONTEXT = 4, R_LAB = 5, R_SAFETY = 6, R_TRAIL = 7,
            R_VIEW = 8;
    private static final String[] REASONS = {"-", "already running", "stage", "days", "not night / not dark",
            "laboratory (outage: real lab, MAIN FEED, B1-B4, not in the lift)", "unsafe place", "no trail (walk 15+ blocks)", "every start point in view"};

    public static String reason(int r) {
        return r >= 0 && r < REASONS.length ? REASONS[r] : "?";
    }

    public static final Type<ChasePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "chase"));

    public static final StreamCodec<ByteBuf, ChasePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.run());
                buf.writeByte(p.phase());
                buf.writeByte(p.info());
                buf.writeDouble(p.x());
                buf.writeDouble(p.y());
                buf.writeDouble(p.z());
                buf.writeFloat(p.stare());
                buf.writeInt(p.nextDay());
            },
            buf -> new ChasePayload(buf.readInt(), buf.readByte(), buf.readByte(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readFloat(), buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
