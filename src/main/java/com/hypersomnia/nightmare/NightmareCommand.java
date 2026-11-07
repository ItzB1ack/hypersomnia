package com.hypersomnia.nightmare;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.dream.DreamDimension;
import com.hypersomnia.dream.DreamAttachments;
import com.hypersomnia.dream.DreamData;
import com.hypersomnia.dream.DreamTransition;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** /hypersomnia_nightmare build <id> | tp <id>. enter/exit/status добавим в шаге 6. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class NightmareCommand {
    private NightmareCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hypersomnia_nightmare")
                .requires(src -> src.hasPermission(2))
                // Внутренняя команда IP: вызывается от имени пересёкшего игрока, без вывода в чат.
                .then(Commands.literal("_ward_loop").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    DreamData data = player.getData(DreamAttachments.DREAM);
                    if (data.isInDream() && NightmareDefs.WARD_CORRIDOR.id().equals(data.getKind())
                            && WardLoopPortals.atForwardDestination(player.serverLevel(), player.position())) {
                        data.addWardLoop();
                        WardGhostSync.syncNow(player);
                        return 1;
                    }
                    return 0;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    DreamData data = player.getData(DreamAttachments.DREAM);
                    ctx.getSource().sendSuccess(() -> Component.literal("Dream=" + data.isInDream()
                            + ", kind=" + data.getKind() + ", ward forward loops=" + data.getWardLoops()), false);
                    return 1;
                }))
                .then(Commands.literal("enter").then(Commands.literal("ward").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return WardEntryQueue.request(player) ? 1 : 0;
                })).then(Commands.literal("shaft").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (!DreamTransition.enterNightmare(player, NightmareDefs.SHAFT)) {
                        ctx.getSource().sendFailure(Component.literal("Shaft unavailable: inspect the dream dimension and reserved lanes"));
                        return 0;
                    }
                    return 1;
                })).then(Commands.literal("rounds").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (!DreamTransition.enterNightmare(player, NightmareDefs.ROUNDS)) {
                        ctx.getSource().sendFailure(Component.literal("Rounds unavailable: inspect the dream dimension and build location"));
                        return 0;
                    }
                    return 1;
                })).then(Commands.literal("session47").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (!DreamTransition.enterNightmare(player, NightmareDefs.SESSION_47)) {
                        ctx.getSource().sendFailure(Component.literal("Session 47 unavailable: inspect the dream dimension and build location"));
                        return 0;
                    }
                    return 1;
                })))
                .then(Commands.literal("exit").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    boolean ok = DreamTransition.exit(player);
                    if (!ok) ctx.getSource().sendFailure(Component.literal("Not in a dream or cannot exit"));
                    return ok ? 1 : 0;
                }))
                .then(Commands.literal("build").then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> {
                            NightmareDefs n = NightmareDefs.byId(StringArgumentType.getString(ctx, "id"));
                            if (n == null) {
                                ctx.getSource().sendFailure(Component.literal("Unknown nightmare id"));
                                return 0;
                            }
                            boolean ok = NightmareBuilder.build(ctx.getSource().getServer(), n);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Nightmare " + n.id() + (ok ? ": built and ready" : ": not ready; wait and use tp or enter to retry")), false);
                            return ok ? 1 : 0;
                        })))
                .then(Commands.literal("tp").then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> {
                            NightmareDefs n = NightmareDefs.byId(StringArgumentType.getString(ctx, "id"));
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ServerLevel dream = p.server.getLevel(DreamDimension.LEVEL);
                            if (n == null || dream == null || !NightmareBuilder.ensureBuilt(p.server, n)) {
                                ctx.getSource().sendFailure(Component.literal("Unknown id, dream dimension missing, or portals not ready; wait and retry"));
                                return 0;
                            }
                            BlockPos s = n.spawn();
                            p.teleportTo(dream, s.getX() + 0.5, s.getY(), s.getZ() + 0.5, n.yaw(), 0f);
                            return 1;
                        }))));
    }
}