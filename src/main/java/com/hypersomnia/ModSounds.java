package com.hypersomnia;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    private ModSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, Hypersomnia.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> BREATH_EXHALE =
            SOUNDS.register("breath_exhale", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "breath_exhale")));

    /** Кассета (экран документа со Style.TAPE): шипение ленты (петля), щелчок механизма, провал записи. */
    public static final DeferredHolder<SoundEvent, SoundEvent> TAPE_HISS = tape("tape_hiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> TAPE_CLICK = tape("tape_click");
    public static final DeferredHolder<SoundEvent, SoundEvent> TAPE_DROPOUT = tape("tape_dropout");

    /** Стук за заложенной дверью палаты 2D (LabKnocks). Файлы — ванильные удары по дереву, см. sounds.json. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LAB_KNOCK = tape("lab_knock");

    /** Сердцебиение игрока во время погони (ChaseClient): «тук-тук», темп задаёт паника. */
    public static final DeferredHolder<SoundEvent, SoundEvent> HEARTBEAT = tape("heartbeat");

    /** Музыка погони (ChaseMusic): петли гула, подъёма и бормотания у головы; шёпот осколка с места фигуры. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CHASE_DRONE = tape("chase_drone");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHASE_RISE = tape("chase_rise");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHASE_NEAR = tape("chase_near");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHARD_WHISPER = tape("shard_whisper");

    private static DeferredHolder<SoundEvent, SoundEvent> tape(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, name)));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}