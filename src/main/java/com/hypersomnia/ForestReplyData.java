package com.hypersomnia;

import com.hypersomnia.forest.ForestReplySchedule;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Индивидуальные сохраняемые лимиты. Сессии рубки и сетевые запросы в сохранение не попадают. */
public final class ForestReplyData implements INBTSerializable<CompoundTag> {
    private ForestReplySchedule schedule = new ForestReplySchedule();

    public ForestReplySchedule schedule() { return schedule; }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("next_attempt", schedule.nextAttempt());
        tag.putLong("next_event", schedule.nextEvent());
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        schedule = new ForestReplySchedule(tag.getLong("next_attempt"), tag.getLong("next_event"));
    }
}