package com.hypersomnia.forest;

import com.hypersomnia.ForestReplyData;
import com.hypersomnia.network.ForestReplyAckPayload;
import com.hypersomnia.network.ForestReplyPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;

/** NBT и настоящие Minecraft/Netty-кодеки; без запуска игрового мира и аудиоустройства. */
public final class ForestReplyCodecSelfTest {
    private static int checks;

    public static void main(String[] args) {
        ForestReplyData original = new ForestReplyData();
        original.schedule().consumeAttempt(100);
        original.schedule().recordStarted(200, 4);
        CompoundTag tag = original.serializeNBT(null); // provider не нужен: сохраняются только два long
        check(tag.getLong("next_attempt") == 24_100, "NBT daily limit");
        check(tag.getLong("next_event") == 96_200, "NBT full cooldown");
        ForestReplyData restored = new ForestReplyData();
        restored.deserializeNBT(null, tag);
        check(!restored.schedule().canAttempt(96_199), "restored cooldown still active");
        check(restored.schedule().canAttempt(96_200), "restored boundary");
        restored.schedule().reset();
        check(original.schedule().nextEvent() == 96_200, "serialized copy has no shared mutable schedule");
        CompoundTag invalid = new CompoundTag();
        invalid.putLong("next_attempt", -1);
        invalid.putLong("next_event", -100);
        restored.deserializeNBT(null, invalid);
        check(restored.schedule().canAttempt(0), "sanitize negative saved values");
        restored.deserializeNBT(null, new CompoundTag());
        check(restored.schedule().canAttempt(0), "old world without new keys");

        roundTrip(ForestReplyPayload.STREAM_CODEC, new ForestReplyPayload(Long.MIN_VALUE + 7, 60, false));
        roundTrip(ForestReplyPayload.STREAM_CODEC, new ForestReplyPayload(Long.MAX_VALUE, 12, true));
        roundTrip(ForestReplyAckPayload.STREAM_CODEC,
                new ForestReplyAckPayload(-987654321L, ForestReplyAckPayload.STARTED));
        roundTrip(ForestReplyAckPayload.STREAM_CODEC,
                new ForestReplyAckPayload(123456789L, ForestReplyAckPayload.NO_SOURCE));
        System.out.println("ForestReplyCodecSelfTest: " + checks + " checks passed.");
    }

    private static <T> void roundTrip(StreamCodec<ByteBuf, T> codec, T value) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            codec.encode(buffer, value);
            check(value.equals(codec.decode(buffer)), "packet round trip: " + value);
            check(buffer.readableBytes() == 0, "packet fully consumed");
        } finally {
            buffer.release();
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}