package com.hypersomnia;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    private ModAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Hypersomnia.MOD_ID);

    /** Усталость, безумие осколка и таймер падения. Живёт на игроке, сохраняется в мир, переживает смерть. */
    public static final Supplier<AttachmentType<FatigueData>> FATIGUE = ATTACHMENTS.register(
            "fatigue",
            () -> AttachmentType.serializable(FatigueData::new).copyOnDeath().build()
    );

    /** Прогресс осколка: дни и воспоминания. Переживает смерть. */
    public static final Supplier<AttachmentType<ObserverProgressData>> OBSERVER = ATTACHMENTS.register(
            "observer",
            () -> AttachmentType.serializable(ObserverProgressData::new).copyOnDeath().build()
    );

    /** Погони (chase/ChaseHandler): день последней, счёт и забранные вещи. Переживает смерть. */
    public static final Supplier<AttachmentType<com.hypersomnia.chase.ChaseData>> CHASE = ATTACHMENTS.register(
            "chase",
            () -> AttachmentType.serializable(com.hypersomnia.chase.ChaseData::new).copyOnDeath().build()
    );

    /** Собственные лимиты «Лес отвечает», без связи с прогрессией. Переживают выход и смерть. */
    public static final Supplier<AttachmentType<ForestReplyData>> FOREST_REPLY = ATTACHMENTS.register(
            "forest_reply",
            () -> AttachmentType.serializable(ForestReplyData::new).copyOnDeath().build()
    );
}