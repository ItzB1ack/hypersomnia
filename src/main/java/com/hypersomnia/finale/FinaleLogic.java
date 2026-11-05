package com.hypersomnia.finale;

import com.hypersomnia.ModAttachments;
import com.hypersomnia.ObserverProgressData;
import com.hypersomnia.document.DrawingTuning;
import com.hypersomnia.document.MemoryTuning;
import net.minecraft.world.entity.player.Player;

/** Выбор концовки (канон, пункт 9). Чистые функции + «финал пройден» для серверных систем. */
public final class FinaleLogic {
    private FinaleLogic() {}

    /** Все воспоминания и Фрагмент услышал правду. */
    public static boolean whole(int memories, boolean truth) {
        return memories >= MemoryTuning.COUNT && truth;
    }

    /**
     * bed = уснуть (кровать), иначе проснуться (дверь). Мало воспоминаний или нет правды: Забвение / Замена;
     * всё и правда: Прощание / Единство. Секрет «Нора» вместо Прощания: ещё все рисунки и подготовленная запись
     * RECORDER 47-OP (шифр 6); кассету игрок не включал (иначе был бы Вечный сон).
     */
    public static int choose(boolean bed, int memories, boolean truth, int drawings, boolean recordReady) {
        boolean whole = whole(memories, truth);
        if (bed) return whole ? FinaleTuning.UNITY : FinaleTuning.REPLACEMENT;
        if (!whole) return FinaleTuning.OBLIVION;
        return drawings >= DrawingTuning.COUNT && recordReady ? FinaleTuning.NORA : FinaleTuning.FAREWELL;
    }

    public static int choose(boolean bed, ObserverProgressData d) {
        return choose(bed, d.memoriesCount(), d.truth(), d.drawingsCount(), d.recordReady());
    }

    /** Финал пройден: события мода (Фрагмент, следы, записки, сцена, усталость, сон в мире снов) отключены. Только сервер. */
    public static boolean completed(Player player) {
        return player.getData(ModAttachments.OBSERVER).ending() >= 0;
    }
}
