package com.hypersomnia.document;

/** Воспоминания 1-7 (канон, пункт 8). Индекс 0..6 = бит в ObserverProgressData.memories. Общий код, без клиентских импортов. */
public final class MemoryTuning {
    private MemoryTuning() {}

    public static final String[] IDS = {
            "mem_1_photo", "mem_2_discharge", "mem_3_contract", "mem_4_session12",
            "mem_5_memo", "mem_6_protocol", "mem_7_log"
    };
    public static final int COUNT = IDS.length;
    /** Как часто сервер проверяет инвентарь игрока (тики). */
    public static final int SCAN_INTERVAL_TICKS = 20;

    /** 0..6 или -1, если это не воспоминание. */
    public static int indexOf(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return -1;
    }
}