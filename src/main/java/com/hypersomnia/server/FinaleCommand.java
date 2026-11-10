package com.hypersomnia.server;

import com.hypersomnia.DebugMode;
import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.ObserverProgressData;
import com.hypersomnia.dream.DreamDimension;
import com.hypersomnia.finale.FinaleTuning;
import com.hypersomnia.lab.LabSavedData;
import com.hypersomnia.lab.LabTuning;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Отладка финала: /hypersomnia_finale status | reset | truth | tp | end <key>. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class FinaleCommand {
    private FinaleCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hypersomnia_finale")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource(), ctx.getSource().getPlayerOrException())))
                // Снова «не пройден»: события мода включаются, правда забыта; полученные концовки (маска) остаются
                .then(Commands.literal("reset").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    p.getData(ModAttachments.OBSERVER).resetFinale();
                    FinaleHandler.forget(p);
                    return status(ctx.getSource(), p);
                }))
                .then(Commands.literal("truth").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    p.getData(ModAttachments.OBSERVER).setTruth(true);
                    return status(ctx.getSource(), p);
                }))
                // В детскую (только в мире снов, лаборатория построена)
                .then(Commands.literal("tp").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    ServerLevel level = p.serverLevel();
                    LabSavedData d = LabSavedData.get(level);
                    BlockPos mark = level.dimension() == DreamDimension.LEVEL && d.built
                            ? d.find(LabSavedData.KIND_FINALE * 1000 + FinaleTuning.PART_FRAGMENT) : null;
                    if (mark == null) {
                        DebugMode.failure(ctx.getSource(), Component.literal("No nursery here: enter the dream with the lab built"));
                        return 0;
                    }
                    p.teleportTo(level, mark.getX() + 0.5, mark.getY() - 1, mark.getZ() - 1.5, 180f, 0f);
                    return 1;
                }))
                // Сразу концовка (экран, достижение, флаг завершения)
                .then(Commands.literal("end").then(Commands.argument("key", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(FinaleTuning.KEYS, b))
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            int e = Arrays.asList(FinaleTuning.KEYS).indexOf(StringArgumentType.getString(ctx, "key"));
                            if (e < 0) {
                                DebugMode.failure(ctx.getSource(), Component.literal("Unknown ending; one of " + String.join(", ", FinaleTuning.KEYS)));
                                return 0;
                            }
                            FinaleHandler.start(p, e);
                            return 1;
                        }))));
    }

    private static int status(CommandSourceStack src, ServerPlayer p) {
        ObserverProgressData d = p.getData(ModAttachments.OBSERVER);
        List<String> got = new ArrayList<>();
        for (int i = 0; i < FinaleTuning.ENDINGS; i++) if (d.hasEnding(i)) got.add(FinaleTuning.KEYS[i]);
        ServerLevel dream = p.server.getLevel(DreamDimension.LEVEL);
        boolean session = dream != null && LabSavedData.get(dream).isLockOpen(LabTuning.LOCK_SESSION);
        String text = "Finale: ending " + (d.ending() >= 0 ? FinaleTuning.KEYS[d.ending()] : "none")
                + ", truth " + d.truth() + ", memories " + d.memoriesCount() + "/7, drawings " + d.drawingsCount()
                + "/5, blank " + d.hasBlank() + ", record " + d.recordReady() + ", session 48 " + session
                + "; endings got: " + (got.isEmpty() ? "-" : String.join(", ", got));
        DebugMode.success(src, () -> Component.literal(text), false);
        return 1;
    }
}
