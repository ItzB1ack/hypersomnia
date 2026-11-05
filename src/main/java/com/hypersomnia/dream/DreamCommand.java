package com.hypersomnia.dream;

import com.hypersomnia.Hypersomnia;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Отладка: /hypersomnia_dream enter | exit | status */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class DreamCommand {
    private DreamCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hypersomnia_dream")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("enter").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    boolean ok = DreamTransition.enter(player);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            ok ? "Dream: entering" : "Dream: cannot enter (already dreaming, or dimension is missing)"), false);
                    return ok ? 1 : 0;
                }))
                .then(Commands.literal("exit").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    boolean ok = DreamTransition.exit(player);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            ok ? "Dream: waking up" : "Dream: not dreaming"), false);
                    return ok ? 1 : 0;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    DreamData data = player.getData(DreamAttachments.DREAM);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Dream: inDream=" + data.isInDream() + ", ticksLeft=" + data.getTicksLeft()
                                    + ", return=" + data.getReturnDim()), false);
                    return 1;
                })));
    }
}
