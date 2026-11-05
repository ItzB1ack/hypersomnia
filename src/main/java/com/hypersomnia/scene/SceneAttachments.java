package com.hypersomnia.scene;

import com.hypersomnia.Hypersomnia;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class SceneAttachments {
    private SceneAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Hypersomnia.MOD_ID);

    /** Переживает смерть: сцена показывается один раз за всё прохождение. */
    public static final Supplier<AttachmentType<SceneData>> SCENE = ATTACHMENTS.register(
            "scene",
            () -> AttachmentType.serializable(SceneData::new).copyOnDeath().build()
    );
}
