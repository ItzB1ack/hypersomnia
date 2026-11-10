package com.hypersomnia.dream;

import com.hypersomnia.DebugMode;
import com.hypersomnia.Hypersomnia;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Отладка: /hypersomnia_dream enter | exit | status | mirror ... | clock ... */
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
                    DebugMode.success(ctx.getSource(), () -> Component.literal(
                            ok ? "Dream: entering" : "Dream: cannot enter (already dreaming, or dimension is missing)"), false);
                    return ok ? 1 : 0;
                }))
                .then(Commands.literal("exit").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    boolean ok = DreamTransition.exit(player);
                    DebugMode.success(ctx.getSource(), () -> Component.literal(
                            ok ? "Dream: waking up" : "Dream: not dreaming"), false);
                    return ok ? 1 : 0;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    DreamData data = player.getData(DreamAttachments.DREAM);
                    DebugMode.success(ctx.getSource(), () -> Component.literal(
                            "Dream: inDream=" + data.isInDream() + ", ticksLeft=" + data.getTicksLeft()
                                    + ", return=" + data.getReturnDim()), false);
                    return 1;
                }))
                .then(Commands.literal("clock")
                        .then(Commands.literal("status").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            DebugMode.success(ctx.getSource(), () -> Component.literal("DreamClock: " + DreamClock.status(player)), false);
                            return 1;
                        }))
                        .then(Commands.literal("place").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            boolean ok = DreamClock.placeNow(player);
                            DebugMode.success(ctx.getSource(), () -> Component.literal(ok
                                    ? "DreamClock: placed at " + DreamClock.status(player)
                                    : "DreamClock: cannot place (need a normal dream and free floor)"), false);
                            return ok ? 1 : 0;
                        }))
                        .then(Commands.literal("strike").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            DreamClock.skipToStrikes(player);
                            DebugMode.success(ctx.getSource(), () -> Component.literal("DreamClock: " + DreamClock.status(player)), false);
                            return 1;
                        }))
                        .then(Commands.literal("chase").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            DreamClock.forceChase(player);
                            DebugMode.success(ctx.getSource(), () -> Component.literal("DreamClock: " + DreamClock.status(player)), false);
                            return 1;
                        })))
                .then(Commands.literal("mirror")
                        .then(Commands.literal("status").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            DebugMode.success(ctx.getSource(), () -> Component.literal(DreamMirror.status(player)), false);
                            return 1;
                        }))
                        .then(Commands.literal("apply").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            boolean ok = DreamMirror.force(player);
                            DebugMode.success(ctx.getSource(), () -> Component.literal(ok
                                    ? "DreamMirror: applied at current position"
                                    : "DreamMirror: cannot apply (need Overworld, stage >= "
                                            + DreamMirrorTuning.MIN_STAGE + ", and no protected zone)"), false);
                            return ok ? 1 : 0;
                        }))));
    }
}
