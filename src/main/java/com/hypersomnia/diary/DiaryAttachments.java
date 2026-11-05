package com.hypersomnia.diary;

import com.hypersomnia.Hypersomnia;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class DiaryAttachments {
    private DiaryAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Hypersomnia.MOD_ID);

    public static final Supplier<AttachmentType<DiaryData>> DIARY = ATTACHMENTS.register(
            "diary",
            () -> AttachmentType.serializable(DiaryData::new).copyOnDeath().build()
    );
}