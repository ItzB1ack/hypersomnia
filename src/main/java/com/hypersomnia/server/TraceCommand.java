package com.hypersomnia.server;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.trace.TraceAttachments;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** /hypersomnia_traces wake — добавить следы вокруг игрока (без стадии и шанса); clear — убрать все; count — сколько. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class TraceCommand {
    private TraceCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hypersomnia_traces")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("wake").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    int n = TraceHandler.place(p, p.serverLevel(), p.blockPosition(), true);
                    ctx.getSource().sendSuccess(() -> Component.literal("Traces added: " + n), false);
                    return n;
                }))
                .then(Commands.literal("clear").executes(ctx -> {
                    TraceHandler.clear(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("count").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    int n = p.getData(TraceAttachments.TRACES).all().size();
                    ctx.getSource().sendSuccess(() -> Component.literal("Traces: " + n), false);
                    return n;
                })));
    }
}