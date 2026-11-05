package com.hypersomnia.dream;

import com.hypersomnia.Hypersomnia;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class DreamAttachments {
    private DreamAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Hypersomnia.MOD_ID);

    /** Без copyOnDeath: умер во сне -> просыпаешься в обычном мире, состояние сна сбрасывается само. */
    public static final Supplier<AttachmentType<DreamData>> DREAM = ATTACHMENTS.register(
            "dream",
            () -> AttachmentType.serializable(DreamData::new).build()
    );
}
