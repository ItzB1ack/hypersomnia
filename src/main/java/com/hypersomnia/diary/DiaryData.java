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
        if (!has(id) && entries.size() >= DiaryTuning.MAX_ENTRIES) return;
        remove(id);
        entries.add(new Entry(id, page, x, y));
    }

    public void remove(String id) { entries.removeIf(e -> e.id().equals(id)); }

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
            try {
                out.put(Character.toUpperCase(p[0].charAt(0)), Integer.parseInt(p[1]));
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
        List<Entry> out = new ArrayList<>();
        for (String s : list) {
            String[] p = s.split("\\|");
            if (p.length != 4) continue;
            try {
                out.add(new Entry(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
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
        entries.clear();
        ListTag list = tag.getList("e", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            entries.add(new Entry(c.getString("id"), c.getInt("page"), c.getInt("x"), c.getInt("y")));
        }
        key.clear();
        // Таблица, составленная по прежним (глазным) знакам, к новым не подходит: она не загружается
        if (tag.getInt("kv") != DiaryTuning.KEY_VERSION) return;
        CompoundTag k = tag.getCompound("k");
        for (String name : k.getAllKeys()) {
            if (name.length() != 1) continue;
            key.put(Character.toUpperCase(name.charAt(0)), k.getInt(name));
        }
    }
}