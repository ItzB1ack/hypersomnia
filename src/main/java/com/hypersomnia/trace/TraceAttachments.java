package com.hypersomnia.trace;

import com.hypersomnia.Hypersomnia;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class TraceAttachments {
    private TraceAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Hypersomnia.MOD_ID);

    public static final Supplier<AttachmentType<TraceData>> TRACES = ATTACHMENTS.register(
            "traces",
            () -> AttachmentType.serializable(TraceData::new).copyOnDeath().build()
    );
}