package com.hypersomnia.client;

import com.hypersomnia.finale.FinaleTuning;
import com.hypersomnia.network.EndingPayload;
import com.hypersomnia.network.FinaleRoomPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Финал на клиенте: игрок в детской, где стоит Фрагмент, запись mem_7 на мониторе (субтитры) и глаза Фрагмента.
 * Без клиентских импортов (как ObserverState): экран концовки открывается в лямбде.
 */
public final class FinaleClientState {
    private FinaleClientState() {}

    public static boolean inside;
    /** Метка Фрагмента: блок над ковром. */
    public static BlockPos mark = BlockPos.ZERO;
    /** Запись: тиков с начала (-1 = не идёт) и услышал ли её Фрагмент. */
    public static int playTick = -1;
    public static boolean playHeard;
    /** Правда была услышана раньше этой записи: глаза уже закрыты и не открываются. */
    private static boolean eyesWereClosed;

    /** Ноги Фрагмента: на ковре под меткой. */
    public static Vec3 feet() {
        return new Vec3(mark.getX() + 0.5, mark.getY() - 1 + 1.0 / 16.0, mark.getZ() + 0.5);
    }

    public static void handleRoom(FinaleRoomPayload pl, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            inside = pl.inside();
            if (inside) mark = pl.fragment();
            if (pl.play() != FinaleRoomPayload.PLAY_NONE) {
                playTick = 0;
                playHeard = pl.play() == FinaleRoomPayload.PLAY_HEARD;
                eyesWereClosed = ObserverState.truth;
            }
            if (!inside) playTick = -1;
        });
    }

    public static void handleEnding(EndingPayload pl, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            inside = false;
            playTick = -1;
            EndingScreen.open(pl.ending(), pl.flags());
        });
    }

    /** Один клиентский тик (FinaleClient, не на паузе). */
    public static void tick() {
        if (playTick >= 0 && ++playTick >= FinaleTuning.playTicks()) playTick = -1;
    }

    /** Глаза Фрагмента: 1 открыты, 0 закрыты. После правды закрываются (во время записи — после ответа, плавно). */
    public static float eyeOpen() {
        if (playTick >= 0 && playHeard && !eyesWereClosed) {
            int t = playTick - FinaleTuning.replyAt();
            if (t <= 0) return 1f;
            return Math.max(0f, 1f - t / (float) FinaleTuning.EYES_CLOSE_TICKS);
        }
        return ObserverState.truth ? 0f : 1f;
    }

    public static void reset() {
        inside = false;
        mark = BlockPos.ZERO;
        playTick = -1;
        playHeard = false;
    }
}
