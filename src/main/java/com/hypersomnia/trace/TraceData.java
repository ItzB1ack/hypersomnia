package com.hypersomnia.trace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.ArrayList;
import java.util.List;

/** Следы осколка у игрока (клиентские «наклейки»: в мире блоков нет). Переживает смерть. */
public class TraceData implements INBTSerializable<CompoundTag> {
    /** type = ordinal TraceType, face = ordinal Direction стороны поверхности, на которой лежит след. */
    public record Entry(int type, BlockPos pos, int face, int variant, int rot) {}

    private static final int STRIDE = 7;
    private final List<Entry> entries = new ArrayList<>();
    private boolean dirty;

    public List<Entry> all() { return entries; }

    public boolean has(BlockPos pos, int face) {
        for (Entry e : entries) if (e.pos().equals(pos) && e.face() == face) return true;
        return false;
    }

    public void add(Entry e) {
        entries.add(e);
        while (entries.size() > TraceTuning.MAX_TOTAL) entries.remove(0);
        dirty = true;
    }

    public void clear() { entries.clear(); dirty = true; }
    public boolean consumeDirty() { boolean d = dirty; dirty = false; return d; }

    public List<Integer> flat() {
        List<Integer> out = new ArrayList<>(entries.size() * STRIDE);
        for (Entry e : entries) {
            out.add(e.type()); out.add(e.pos().getX()); out.add(e.pos().getY()); out.add(e.pos().getZ());
            out.add(e.face()); out.add(e.variant()); out.add(e.rot());
        }
        return out;
    }

    public static List<Entry> fromFlat(List<Integer> f) {
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i + STRIDE <= f.size(); i += STRIDE) {
            out.add(new Entry(f.get(i), new BlockPos(f.get(i + 1), f.get(i + 2), f.get(i + 3)),
                    f.get(i + 4), f.get(i + 5), f.get(i + 6)));
        }
        return out;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("e", flat().stream().mapToInt(Integer::intValue).toArray());
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        entries.clear();
        int[] a = tag.getIntArray("e");
        List<Integer> f = new ArrayList<>(a.length);
        for (int v : a) f.add(v);
        entries.addAll(fromFlat(f));
    }
}