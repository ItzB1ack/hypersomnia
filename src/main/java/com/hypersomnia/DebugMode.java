package com.hypersomnia;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * Общий переключатель отладочного вывода: /hypersomnia debug <true|false>.
 * По умолчанию выключен: команды мода (hypersomnia*, кроме самого debug) ничего не пишут в чат.
 * Когда выключен, текст уходит в latest.log, чтобы ошибки не пропадали бесследно.
 * Флаг не сохраняется: при каждом запуске игры или сервера он снова false.
 * Класс common-безопасный: клиентские команды в одиночной игре читают тот же флаг.
 */
public final class DebugMode {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean enabled = false;

    private DebugMode() {}

    public static boolean enabled() { return enabled; }

    public static void set(boolean value) { enabled = value; }

    /** Замена source.sendSuccess для команд мода. */
    public static void success(CommandSourceStack source, Supplier<Component> message, boolean broadcastToOps) {
        if (enabled) source.sendSuccess(message, broadcastToOps);
        else LOGGER.info("[hypersomnia] {}", message.get().getString());
    }

    /** Замена source.sendFailure для команд мода. */
    public static void failure(CommandSourceStack source, Component message) {
        if (enabled) source.sendFailure(message);
        else LOGGER.info("[hypersomnia] {}", message.getString());
    }

    /** Замена player.sendSystemMessage для отладочных строк (чат). */
    public static void chat(Player player, Component message) {
        if (enabled) player.sendSystemMessage(message);
        else LOGGER.info("[hypersomnia] {}", message.getString());
    }

    /** Замена player.displayClientMessage(msg, true) для отладочных строк (над хотбаром). */
    public static void overlay(Player player, Component message) {
        if (enabled) player.displayClientMessage(message, true);
        else LOGGER.info("[hypersomnia] {}", message.getString());
    }
}
