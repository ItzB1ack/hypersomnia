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
    public record Entry(int type, BlockPos pos, int face, int variant, int rot, BlockPos origin) {
        public Entry {
            pos = pos.immutable();
            origin = origin == null ? null : origin.immutable();
        }
        public Entry(int type, BlockPos pos, int face, int variant, int rot) {
            this(type, pos, face, variant, rot, null);
        }
    }

    private static final int NBT_VERSION = 2;
    private static final int V1_STRIDE = 7;
    private static final int V2_STRIDE = 11;
    private final List<Entry> entries = new ArrayList<>();
    private boolean dirty;
    private BlockPos pendingWriting;
    private int writingDelay;

    public List<Entry> all() { return entries; }
    public BlockPos pendingWriting() { return pendingWriting; }
    public int writingDelay() { return writingDelay; }
    public void scheduleWriting(BlockPos pos, int delay) { pendingWriting = pos.immutable(); writingDelay = Math.max(0, delay); }
    public void tickWriting() { if (writingDelay > 0) writingDelay--; }
    public void deferWriting(int delay) { writingDelay = Math.max(0, delay); }
    public void clearWriting() { pendingWriting = null; writingDelay = 0; }

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
        List<Integer> out = new ArrayList<>(entries.size() * V2_STRIDE);
        for (Entry e : entries) {
            out.add(e.type()); out.add(e.pos().getX()); out.add(e.pos().getY()); out.add(e.pos().getZ());
            out.add(e.face()); out.add(e.variant()); out.add(e.rot());
            BlockPos origin = e.origin();
            out.add(origin == null ? 0 : 1);
            out.add(origin == null ? 0 : origin.getX());
            out.add(origin == null ? 0 : origin.getY());
            out.add(origin == null ? 0 : origin.getZ());
        }
        return out;
    }

    public static List<Entry> fromFlat(int version, List<Integer> f) {
        List<Entry> out = new ArrayList<>();
        int stride = version >= 2 ? V2_STRIDE : V1_STRIDE;
        for (int i = 0; i + stride <= f.size(); i += stride) {
            BlockPos origin = version >= 2 && f.get(i + 7) != 0
                    ? new BlockPos(f.get(i + 8), f.get(i + 9), f.get(i + 10)) : null;
            out.add(new Entry(f.get(i), new BlockPos(f.get(i + 1), f.get(i + 2), f.get(i + 3)),
                    f.get(i + 4), f.get(i + 5), f.get(i + 6), origin));
        }
        return out;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("v", NBT_VERSION);
        if (pendingWriting != null) { tag.putLong("writing_pos", pendingWriting.asLong()); tag.putInt("writing_delay", writingDelay); }
        tag.putIntArray("e", flat().stream().mapToInt(Integer::intValue).toArray());
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        entries.clear();
        pendingWriting = tag.contains("writing_pos") ? BlockPos.of(tag.getLong("writing_pos")) : null;
        writingDelay = Math.max(0, tag.getInt("writing_delay"));
        int[] a = tag.getIntArray("e");
        List<Integer> f = new ArrayList<>(a.length);
        for (int v : a) f.add(v);
        int version = tag.contains("v") ? tag.getInt("v") : 1;
        entries.addAll(fromFlat(version, f));
        dirty = false;
    }
}