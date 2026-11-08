package com.hypersomnia;

import com.hypersomnia.voice.VoiceLine;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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

    /** [новое] Напольные часы в мире снов (DreamClock): тиканье и удары. Файлы — ванильные, см. sounds.json.
     *  Фиксированная дальность события важна: пакет звука уходит только игрокам в этом радиусе. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CLOCK_TICK = clock("dream_clock_tick", 192f);
    public static final DeferredHolder<SoundEvent, SoundEvent> CLOCK_STRIKE = clock("dream_clock_strike", 256f);

    /** Сердцебиение игрока во время погони (ChaseClient): «тук-тук», темп задаёт паника. */
    public static final DeferredHolder<SoundEvent, SoundEvent> HEARTBEAT = tape("heartbeat");

    /** Музыка погони (ChaseMusic): петли гула, подъёма и бормотания у головы; шёпот осколка с места фигуры. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CHASE_DRONE = tape("chase_drone");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHASE_RISE = tape("chase_rise");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHASE_NEAR = tape("chase_near");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHARD_WHISPER = tape("shard_whisper");
    /** Голос осколка (client/FragmentVoice): событие на каждую реплику — voice.en.<id> (VoiceLine; озвучка только английская). */
    public static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> VOICE = voice();

    private static Map<String, DeferredHolder<SoundEvent, SoundEvent>> voice() {
        Map<String, DeferredHolder<SoundEvent, SoundEvent>> m = new LinkedHashMap<>();
        for (VoiceLine line : VoiceLine.values()) m.put(line.sound(), tape(line.sound()));
        return Collections.unmodifiableMap(m);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> tape(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, name)));
    }

    /** Звук с фиксированной дальностью: сервер шлёт пакет всем в этом радиусе, затухание задаёт sounds.json. */
    private static DeferredHolder<SoundEvent, SoundEvent> clock(String name, float range) {
        return SOUNDS.register(name, () -> SoundEvent.createFixedRangeEvent(
                ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, name), range));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}