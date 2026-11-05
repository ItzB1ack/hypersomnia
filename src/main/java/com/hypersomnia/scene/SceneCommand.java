package com.hypersomnia.scene;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.network.SleepScenePayload;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Отладка сцены: /hypersomnia_scene play  — показать сцену прямо сейчас (флаг «seen» не трогает)
 *                /hypersomnia_scene reset — снова разрешить сцену при засыпании
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class SceneCommand {
    private SceneCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hypersomnia_scene")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("play").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    PacketDistributor.sendToPlayer(p, new SleepScenePayload(p.getRandom().nextInt()));
                    return 1;
                }))
                .then(Commands.literal("reset").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    p.getData(SceneAttachments.SCENE).setSeen(false);
                    ctx.getSource().sendSuccess(() -> Component.literal("Scene can play again."), false);
                    return 1;
                })));
    }
}
