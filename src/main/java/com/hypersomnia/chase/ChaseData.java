package com.hypersomnia.chase;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.ArrayList;
import java.util.List;

/**
 * Погони игрока (ModAttachments.CHASE): день последней, сколько их было (первая — учебная) и вещи, которые он забрал
 * при поимке, с днём возврата. Живёт на игроке, сохраняется, переживает смерть.
 */
public class ChaseData implements INBTSerializable<CompoundTag> {
    /** Вещь, забранная при поимке, и день осколка, с которого её можно вернуть. */
    public record Taken(ItemStack stack, int returnDay) {}

    /** День начала последней погони, -1 — погонь не было. */
    private int lastDay = -1;
    private int count;
    private final List<Taken> taken = new ArrayList<>();

    public int lastDay() { return lastDay; }
    public int count() { return count; }

    /** Первый день, когда можно снова (0 — уже можно). */
    public int nextDay() {
        return lastDay < 0 ? 0 : lastDay + ChaseTuning.MIN_DAYS_BETWEEN;
    }

    public void markStarted(int day) {
        lastDay = day;
        count++;
    }

    public void reset() {
        lastDay = -1;
        count = 0;
    }

    public List<Taken> taken() { return taken; }

    public void addTaken(ItemStack stack, int returnDay) {
        if (!stack.isEmpty()) taken.add(new Taken(stack, returnDay));
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("last_day", lastDay);
        tag.putInt("count", count);
        ListTag list = new ListTag();
        for (Taken t : taken) {
            if (t.stack().isEmpty()) continue;
            CompoundTag e = new CompoundTag();
            e.put("item", t.stack().save(provider));
            e.putInt("day", t.returnDay());
            list.add(e);
        }
        if (!list.isEmpty()) tag.put("taken", list);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        lastDay = tag.contains("last_day") ? tag.getInt("last_day") : -1;
        count = Math.max(0, tag.getInt("count"));
        taken.clear();
        ListTag list = tag.getList("taken", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            ItemStack s = ItemStack.parseOptional(provider, e.getCompound("item"));
            if (!s.isEmpty()) taken.add(new Taken(s, e.getInt("day")));
        }
    }
}
