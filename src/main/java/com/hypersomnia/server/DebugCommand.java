package com.hypersomnia.server;

import com.hypersomnia.DebugMode;
import com.hypersomnia.Hypersomnia;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /hypersomnia debug [true|false] — включает вывод сообщений команд мода в чат.
 * По умолчанию false. Сама эта команда отвечает всегда, иначе переключатель был бы немым.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class DebugCommand {
    private DebugCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hypersomnia")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("debug")
                        .executes(ctx -> status(ctx.getSource()))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> set(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled"))))));
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Hypersomnia debug: " + DebugMode.enabled()), false);
        return 1;
    }

    private static int set(CommandSourceStack source, boolean value) {
        DebugMode.set(value);
        source.sendSuccess(() -> Component.literal("Hypersomnia debug: " + value), false);
        return 1;
    }
}
