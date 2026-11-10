package com.hypersomnia.diary;

import com.hypersomnia.document.SignAlphabet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Листы, положенные в дневник вручную. Порядок списка = порядок наложения (последний сверху). */
public class DiaryData implements INBTSerializable<CompoundTag> {
    public record Entry(String id, int page, int x, int y) {}

    private final List<Entry> entries = new ArrayList<>();
    /** Таблица ключа на последней странице: буква -> номер знака (SignAlphabet.byIndex). */
    private final Map<Character, Integer> key = new LinkedHashMap<>();

    public List<Entry> entries() { return entries; }

    public boolean has(String id) {
        for (Entry e : entries) if (e.id().equals(id)) return true;
        return false;
    }

    public void place(String id, int page, int x, int y) {
        if (id == null || id.isEmpty()) return;
        if (!has(id) && entries.size() >= DiaryTuning.MAX_ENTRIES) return;
        remove(id);
        entries.add(clamp(new Entry(id, page, x, y)));
    }

    public void remove(String id) { entries.removeIf(e -> e.id().equals(id)); }

    /** Страница и положение листа в допустимых пределах бумаги. */
    private static Entry clamp(Entry e) {
        int page = Math.max(1, Math.min(DiaryTuning.PAGES, e.page()));
        int x = Math.max(0, Math.min(DiaryTuning.PAPER_W - DiaryTuning.THUMB_MIN, e.x()));
        int y = Math.max(0, Math.min(DiaryTuning.PAPER_H - DiaryTuning.THUMB_MIN, e.y()));
        return new Entry(e.id(), page, x, y);
    }

    /** Старые и битые записи: пустые id, повторы (последний сверху), лимит листов, края бумаги. */
    private static List<Entry> sanitize(List<Entry> in) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : in) {
            if (e == null || e.id() == null || e.id().isEmpty()) continue;
            out.removeIf(x -> x.id().equals(e.id()));
            if (out.size() >= DiaryTuning.MAX_ENTRIES) continue;
            out.add(clamp(e));
        }
        return out;
    }

    public Integer keySym(char c) { return key.get(Character.toUpperCase(c)); }

    /** sym < 0 стирает знак у буквы. Один знак не может стоять у двух букв. */
    public void setKey(char c, int sym) {
        char u = Character.toUpperCase(c);
        if (!SignAlphabet.isLetter(u)) return;
        if (sym < 0) {
            key.remove(u);
        } else {
            key.entrySet().removeIf(e -> e.getValue() == sym);
            key.put(u, sym);
        }
    }

    /** Знак из сохранения: не буква или номер вне палитры отбрасываются. */
    private void loadKey(char c, int sym) {
        char u = Character.toUpperCase(c);
        if (!SignAlphabet.isLetter(u) || sym < 0 || sym >= SignAlphabet.count()) return;
        key.entrySet().removeIf(e -> e.getValue() == sym);
        key.put(u, sym);
    }

    public List<String> keyStrings() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<Character, Integer> e : key.entrySet()) out.add(e.getKey() + "=" + e.getValue());
        return out;
    }

    public static Map<Character, Integer> keyFromStrings(List<String> list) {
        Map<Character, Integer> out = new LinkedHashMap<>();
        for (String s : list) {
            String[] p = s.split("=");
            if (p.length != 2 || p[0].length() != 1) continue;
            char u = Character.toUpperCase(p[0].charAt(0));
            if (!SignAlphabet.isLetter(u)) continue;
            try {
                int sym = Integer.parseInt(p[1]);
                if (sym < 0 || sym >= SignAlphabet.count()) continue;
                out.entrySet().removeIf(e -> e.getValue() == sym);
                out.put(u, sym);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    public List<String> toStrings() {
        List<String> out = new ArrayList<>();
        for (Entry e : entries) out.add(e.id() + "|" + e.page() + "|" + e.x() + "|" + e.y());
        return out;
    }

    public static List<Entry> fromStrings(List<String> list) {
        List<Entry> parsed = new ArrayList<>();
        for (String s : list) {
            String[] p = s.split("\\|");
            if (p.length != 4) continue;
            try {
                parsed.add(new Entry(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
            } catch (NumberFormatException ignored) {
            }
        }
        return sanitize(parsed);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Entry e : entries) {
            CompoundTag c = new CompoundTag();
            c.putString("id", e.id());
            c.putInt("page", e.page());
            c.putInt("x", e.x());
            c.putInt("y", e.y());
            list.add(c);
        }
        tag.put("e", list);
        CompoundTag k = new CompoundTag();
        for (Map.Entry<Character, Integer> e : key.entrySet()) k.putInt(String.valueOf(e.getKey()), e.getValue());
        tag.put("k", k);
        tag.putInt("kv", DiaryTuning.KEY_VERSION);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        ListTag list = tag.getList("e", Tag.TAG_COMPOUND);
        List<Entry> loaded = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            loaded.add(new Entry(c.getString("id"), c.getInt("page"), c.getInt("x"), c.getInt("y")));
        }
        entries.clear();
        entries.addAll(sanitize(loaded));
        key.clear();
        // Таблица, составленная по прежним (глазным) знакам, к новым не подходит: она не загружается
        if (tag.getInt("kv") != DiaryTuning.KEY_VERSION) return;
        CompoundTag k = tag.getCompound("k");
        for (String name : k.getAllKeys()) {
            if (name.length() != 1) continue;
            loadKey(name.charAt(0), k.getInt(name));
        }
    }
}