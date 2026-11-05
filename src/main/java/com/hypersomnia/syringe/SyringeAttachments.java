package com.hypersomnia.syringe;

import com.hypersomnia.Hypersomnia;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class SyringeAttachments {
    private SyringeAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Hypersomnia.MOD_ID);

    /** Толерантность переживает смерть; действие укола при респауне сбрасывается в SyringeHandler. */
    public static final Supplier<AttachmentType<SyringeData>> SYRINGE = ATTACHMENTS.register(
            "syringe",
            () -> AttachmentType.serializable(SyringeData::new).copyOnDeath().build()
    );
}
