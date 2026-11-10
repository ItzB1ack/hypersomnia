package com.hypersomnia.server;

import com.hypersomnia.DebugMode;
import com.hypersomnia.Hypersomnia;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Только для оператора. test обходит шанс/ожидание, но не видимость и приоритет остальных событий. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class ForestReplyCommand {
    private ForestReplyCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hypersomnia")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("forest")
                        .then(Commands.literal("test").executes(ctx -> test(ctx.getSource())))
                        .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                        .then(Commands.literal("reset").executes(ctx -> reset(ctx.getSource())))));
    }

    private static int test(CommandSourceStack source) throws CommandSyntaxException {
        if (ForestReplyHandler.test(source.getPlayerOrException())) return 1;
        DebugMode.failure(source, Component.literal("Лес отвечает: тест недоступен сейчас. Нужен живой игрок в обычном мире до финала."));
        return 0;
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        String status = ForestReplyHandler.status(source.getPlayerOrException());
        DebugMode.success(source, () -> Component.literal(status), false);
        return 1;
    }

    private static int reset(CommandSourceStack source) throws CommandSyntaxException {
        if (!ForestReplyHandler.resetLimits(source.getPlayerOrException())) {
            DebugMode.failure(source, Component.literal("Лес отвечает: сначала дождись ответа на текущий запрос."));
            return 0;
        }
        DebugMode.success(source, () -> Component.literal("Сброшены только собственные лимиты «Лес отвечает»."), false);
        return 1;
    }
}