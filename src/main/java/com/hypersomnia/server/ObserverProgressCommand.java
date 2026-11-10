package com.hypersomnia.server;

import com.hypersomnia.DebugMode;
import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.ObserverProgressData;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Отладка: /hypersomnia_progress days <n> | memories <0-7> | exposure <0-8> | stage <0-5|auto> | status | habits [clear|here|bed|seen] | gift [now|reset] | hint <0-2> */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class ObserverProgressCommand {
    private ObserverProgressCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hypersomnia_progress")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("days").then(Commands.argument("n", IntegerArgumentType.integer(0, 1000))
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            p.getData(ModAttachments.OBSERVER).setDays(IntegerArgumentType.getInteger(ctx, "n"));
                            return status(ctx.getSource(), p);
                        })))
                .then(Commands.literal("memories").then(Commands.argument("n", IntegerArgumentType.integer(0, 7))
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            p.getData(ModAttachments.OBSERVER).setMemoriesCount(IntegerArgumentType.getInteger(ctx, "n"));
                            return status(ctx.getSource(), p);
                        })))
                // Отладка: очки бодрствования (шприцы/долгая усталость) для проверки ускорения стадии.
                .then(Commands.literal("exposure").then(Commands.argument("n",
                                IntegerArgumentType.integer(0, com.hypersomnia.ObserverTuning.EXPOSURE_MAX_POINTS))
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            p.getData(ModAttachments.OBSERVER).setExposure(IntegerArgumentType.getInteger(ctx, "n"));
                            return status(ctx.getSource(), p);
                        })))
                // Отладка: стадия вручную, без изменения дней и воспоминаний; auto = снова по формуле
                .then(Commands.literal("stage")
                        .then(Commands.literal("auto").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            p.getData(ModAttachments.OBSERVER).setStageOverride(-1);
                            return status(ctx.getSource(), p);
                        }))
                        .then(Commands.argument("s", IntegerArgumentType.integer(0, 5)).executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            p.getData(ModAttachments.OBSERVER).setStageOverride(IntegerArgumentType.getInteger(ctx, "s"));
                            return status(ctx.getSource(), p);
                        })))
                .then(Commands.literal("notes")
                        .then(Commands.literal("reset").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            p.getData(ModAttachments.OBSERVER).resetNotes();
                            return status(ctx.getSource(), p);
                        }))
                        .then(Commands.literal("give").then(Commands.argument("way", IntegerArgumentType.integer(0, 4))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    boolean ok = com.hypersomnia.server.ObserverNotesHandler.giveNow(p, IntegerArgumentType.getInteger(ctx, "way"));
                                    DebugMode.success(ctx.getSource(), () -> Component.literal("note delivered=" + ok
                                            + " (0 bed, 1 container, 2 window, 3 inventory, 4 dream)"), false);
                                    return ok ? 1 : 0;
                                }))))
                .then(Commands.literal("drawings").then(Commands.argument("i", IntegerArgumentType.integer(0, 5))
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            p.getInventory().placeItemBackInInventory(com.hypersomnia.document.DocumentItem.make(
                                    com.hypersomnia.document.DrawingTuning.idOf(IntegerArgumentType.getInteger(ctx, "i"))));
                            return 1;
                        })))
                // Подарок воспоминания: счётчик пустых ночей; now — положить сейчас у игрока (без условий); reset — забыть подаренные
                .then(Commands.literal("gift")
                        .executes(ctx -> gift(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.literal("now").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            int mem = ObserverNotesHandler.giftNow(p, p.serverLevel(), p.blockPosition(), p.getData(ModAttachments.OBSERVER));
                            DebugMode.success(ctx.getSource(), () -> Component.literal(mem < 0 ? "gift: nothing placed (no memory left, or no bed/chest nearby)"
                                    : "gift: memory " + (mem + 1) + " placed"), false);
                            return mem < 0 ? 0 : 1;
                        }))
                        .then(Commands.literal("reset").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            p.getData(ModAttachments.OBSERVER).resetGift();
                            return gift(ctx.getSource(), p);
                        })))
                // Рисунок записки k у места подсказки вокруг игрока (0 окно, 1 за дверью, 2 под кроватью), без учёта прогресса
                .then(Commands.literal("hint").then(Commands.argument("k", IntegerArgumentType.integer(0, 2))
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            int k = IntegerArgumentType.getInteger(ctx, "k");
                            boolean ok = ObserverNotesHandler.placeHint(p.serverLevel(), p.blockPosition(), ObserverNotesHandler.Hint.values()[k],
                                    com.hypersomnia.document.DocumentItem.make(com.hypersomnia.document.DrawingTuning.idOf(
                                            com.hypersomnia.ObserverTuning.HINT_DRAWINGS[k])));
                            DebugMode.success(ctx.getSource(), () -> Component.literal("hint " + k + " placed=" + ok), false);
                            return ok ? 1 : 0;
                        })))
                .then(Commands.literal("memory").then(Commands.argument("i", IntegerArgumentType.integer(1, 7))
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            p.getInventory().placeItemBackInInventory(com.hypersomnia.document.DocumentItem.make(
                                    com.hypersomnia.document.MemoryTuning.IDS[IntegerArgumentType.getInteger(ctx, "i") - 1]));
                            return 1;
                        })))
                .then(Commands.literal("habits")
                        .executes(ctx -> habits(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.literal("clear").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            p.getData(ModAttachments.OBSERVER).clearHabits();
                            return habits(ctx.getSource(), p);
                        }))
                        .then(Commands.literal("here").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            p.getData(ModAttachments.OBSERVER).noteRoute(p.getBlockX(), p.getBlockY(), p.getBlockZ());
                            return habits(ctx.getSource(), p);
                        }))
                        .then(Commands.literal("bed").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            p.getData(ModAttachments.OBSERVER).noteSleep(p.getBlockX(), p.getBlockY(), p.getBlockZ());
                            return habits(ctx.getSource(), p);
                        }))
                        // «видел фигуру здесь»: без проверок сервера; /hypersomnia_traces wake поставит шаги сюда
                        .then(Commands.literal("seen").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            p.getData(ModAttachments.OBSERVER).noteSeen(p.getBlockX(), p.getBlockY(), p.getBlockZ(),
                                    p.serverLevel().getGameTime());
                            return habits(ctx.getSource(), p);
                        })))
                .then(Commands.literal("stage5")
                        .then(Commands.literal("reset").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            NoraRoomHandler.resetFor(p);
                            LabPathHandler.resetFor(p);
                            p.getData(ModAttachments.OBSERVER).resetStage5Scenes();
                            DebugMode.success(ctx.getSource(), () -> Component.literal("Stage 5 scenes reset for this player."), false);
                            return status(ctx.getSource(), p);
                        })))
                .then(Commands.literal("status").executes(ctx ->
                        status(ctx.getSource(), ctx.getSource().getPlayerOrException()))));
    }

    /** Память привычек: точки маршрута (x y z вес), места сна (x y z счёт), где видел фигуру (x y z день). */
    private static int habits(net.minecraft.commands.CommandSourceStack src, ServerPlayer p) {
        ObserverProgressData d = p.getData(ModAttachments.OBSERVER);
        DebugMode.success(src, () -> Component.literal("route " + d.routeCount() + ": " + quads(d.routeList())), false);
        DebugMode.success(src, () -> Component.literal("sleep " + d.sleepCount() + ": " + quads(d.sleepList())), false);
        DebugMode.success(src, () -> Component.literal("seen " + d.seenList().size() / 4 + ": "
                + quads(d.seenList()).replace(" w", " day")), false);
        return 1;
    }

    private static String quads(java.util.List<Integer> l) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i + 3 < l.size(); i += 4) {
            if (b.length() > 0) b.append(" | ");
            b.append(l.get(i)).append(' ').append(l.get(i + 1)).append(' ').append(l.get(i + 2)).append(" w").append(l.get(i + 3));
        }
        return b.length() == 0 ? "-" : b.toString();
    }

    private static int gift(net.minecraft.commands.CommandSourceStack src, ServerPlayer p) {
        ObserverProgressData d = p.getData(ModAttachments.OBSERVER);
        int next = d.nextGift();
        DebugMode.success(src, () -> Component.literal("gift: empty nights " + d.giftNights() + "/" + com.hypersomnia.ObserverTuning.GIFT_NIGHTS
                + " (stage " + d.stage() + ", needs " + com.hypersomnia.ObserverTuning.GIFT_MIN_STAGE + ")"
                + " gifted=" + Integer.bitCount(d.giftedMask()) + " next=" + (next < 0 ? "-" : "memory " + (next + 1))), false);
        return 1;
    }

    private static int status(net.minecraft.commands.CommandSourceStack src, ServerPlayer p) {
        ObserverProgressData d = p.getData(ModAttachments.OBSERVER);
        int formula = com.hypersomnia.ObserverTuning.stage(d.getDays(), d.memoriesCount(), d.exposurePoints());
        String stage = d.stageOverride() >= 0 ? d.stageOverride() + " (forced, formula " + formula + ")" : String.valueOf(formula);
        DebugMode.success(src, () -> Component.literal("days=" + d.getDays() + " memories=" + d.memoriesCount()
                + " stage=" + stage + " exposure=" + d.exposurePoints() + " notes=" + d.notesCount()
                + " drawings=" + d.drawingsCount() + (d.hasBlank() ? "+blank" : "")
                + " route=" + d.routeCount() + " sleep=" + d.sleepCount()
                + " noraRoomSeen=" + d.noraRoomSeen() + " labPathSeen=" + d.labPathSeen()
                + " session47Seen=" + d.session47Seen()), false);
        return 1;
    }
}