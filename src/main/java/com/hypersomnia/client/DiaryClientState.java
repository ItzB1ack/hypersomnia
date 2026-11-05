package com.hypersomnia.client;

import com.hypersomnia.diary.DiaryData;
import com.hypersomnia.document.SignAlphabet;
import com.hypersomnia.network.DiarySyncPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Листы дневника на клиенте. Без клиентских импортов Minecraft. Локальные правки видны сразу, сервер потом подтверждает. */
public final class DiaryClientState {
    private DiaryClientState() {}

    public static volatile List<DiaryData.Entry> entries = List.of();
    /** Таблица ключа: буква -> номер знака SignAlphabet. */
    public static volatile Map<Character, Integer> key = Map.of();

    public static void handleSync(DiarySyncPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            entries = DiaryData.fromStrings(payload.entries());
            key = DiaryData.keyFromStrings(payload.key());
        });
    }

    public static Integer keySym(char c) { return key.get(Character.toUpperCase(c)); }

    public static void applyKey(char c, int sym) {
        Map<Character, Integer> m = new LinkedHashMap<>(key);
        char u = Character.toUpperCase(c);
        if (sym < 0) {
            m.remove(u);
        } else {
            m.entrySet().removeIf(e -> e.getValue() == sym);
            m.put(u, sym);
        }
        key = m;
    }

    /** Буква, которую игрок уже назначил знаку буквы c в этом мире, или null (знак в шифре заменяется буквой). */
    public static Character letterFor(char c) {
        int idx = SignAlphabet.indexOf(LabClientState.seeded ? LabClientState.cipherSeed : 0L, c);
        if (idx < 0) return null;
        for (Map.Entry<Character, Integer> e : key.entrySet()) if (e.getValue() == idx) return e.getKey();
        return null;
    }

    public static void applyPlace(String id, int page, int x, int y) {
        List<DiaryData.Entry> list = new ArrayList<>(entries);
        list.removeIf(e -> e.id().equals(id));
        list.add(new DiaryData.Entry(id, page, x, y));
        entries = list;
    }

    public static void applyRemove(String id) {
        List<DiaryData.Entry> list = new ArrayList<>(entries);
        list.removeIf(e -> e.id().equals(id));
        entries = list;
    }

    public static boolean has(String id) {
        for (DiaryData.Entry e : entries) if (e.id().equals(id)) return true;
        return false;
    }
}