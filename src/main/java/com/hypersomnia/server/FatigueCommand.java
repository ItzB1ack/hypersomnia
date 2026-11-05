package com.hypersomnia.server;

import com.hypersomnia.FatigueData;
import com.hypersomnia.FatigueTuning;
import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Отладочные команды: /hypersomnia fatigue get | set <процент 0-100> */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class FatigueCommand {
    private FatigueCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("hypersomnia")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("fatigue")
                                .then(Commands.literal("get")
                                        .executes(ctx -> get(ctx.getSource())))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                                .executes(ctx -> set(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "percent"))))))
        );
    }

    private static int get(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FatigueData data = player.getData(ModAttachments.FATIGUE);
        int percent = Math.round(FatigueTuning.ratio(data.getFatigue()) * 100f);
        source.sendSuccess(() -> Component.literal(
                "Fatigue: " + data.getFatigue() + "/" + FatigueTuning.MAX_FATIGUE
                        + " (" + percent + "%), madness: " + data.getMadness()), false);
        return 1;
    }

    private static int set(CommandSourceStack source, int percent) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FatigueData data = player.getData(ModAttachments.FATIGUE);
        data.setFatigue(Math.round(FatigueTuning.MAX_FATIGUE * percent / 100f));
        data.setCollapseTimer(0);
        source.sendSuccess(() -> Component.literal("Fatigue set to " + percent + "%"), false);
        return 1;
    }
}
