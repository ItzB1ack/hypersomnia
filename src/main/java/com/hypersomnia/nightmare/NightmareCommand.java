package com.hypersomnia.nightmare;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.dream.DreamDimension;
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
                .then(Commands.literal("build").then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> {
                            NightmareDefs n = NightmareDefs.byId(StringArgumentType.getString(ctx, "id"));
                            if (n == null) {
                                ctx.getSource().sendFailure(Component.literal("Unknown nightmare id"));
                                return 0;
                            }
                            boolean ok = NightmareBuilder.build(ctx.getSource().getServer(), n);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Nightmare " + n.id() + (ok ? ": built" : ": no builder yet")), false);
                            return ok ? 1 : 0;
                        })))
                .then(Commands.literal("tp").then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> {
                            NightmareDefs n = NightmareDefs.byId(StringArgumentType.getString(ctx, "id"));
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ServerLevel dream = p.server.getLevel(DreamDimension.LEVEL);
                            if (n == null || dream == null || !NightmareBuilder.ensureBuilt(p.server, n)) {
                                ctx.getSource().sendFailure(Component.literal("Unknown id, no builder, or dream dimension missing"));
                                return 0;
                            }
                            BlockPos s = n.spawn();
                            p.teleportTo(dream, s.getX() + 0.5, s.getY(), s.getZ() + 0.5, n.yaw(), 0f);
                            return 1;
                        }))));
    }
}