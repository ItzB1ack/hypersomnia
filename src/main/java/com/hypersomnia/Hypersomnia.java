package com.hypersomnia;

import com.hypersomnia.client.ClientFatigueState;
import com.hypersomnia.dream.DreamAttachments;
import com.hypersomnia.network.FatigueSyncPayload;
import com.hypersomnia.scene.SceneAttachments;
import com.hypersomnia.syringe.SyringeAttachments;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(Hypersomnia.MOD_ID)
public class Hypersomnia {
    public static final String MOD_ID = "hypersomnia";

    public Hypersomnia(IEventBus modEventBus) {
        ModAttachments.ATTACHMENTS.register(modEventBus);
        DreamAttachments.ATTACHMENTS.register(modEventBus);
        SceneAttachments.ATTACHMENTS.register(modEventBus);
        SyringeAttachments.ATTACHMENTS.register(modEventBus); // НОВОЕ
        com.hypersomnia.trace.TraceAttachments.ATTACHMENTS.register(modEventBus);
        com.hypersomnia.diary.DiaryAttachments.ATTACHMENTS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);                  // НОВОЕ
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModSounds.register(modEventBus);
        modEventBus.addListener(this::registerPayloads);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                FatigueSyncPayload.TYPE,
                FatigueSyncPayload.STREAM_CODEC,
                ClientFatigueState::handleSync
        );
    }
}
