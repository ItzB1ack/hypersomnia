package com.hypersomnia.lab;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.ObserverProgressData;
import com.hypersomnia.ObserverTuning;
import com.hypersomnia.document.WorldVariant;
import com.hypersomnia.network.LabSeedPayload;
import com.hypersomnia.network.LabTerminalPayload;
import com.hypersomnia.server.LabAmbientHandler;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import com.hypersomnia.dream.DreamDimension;

/**
 * /hypersomnia_lab where | tp | status | rebuild | resite | unlock <0-5> | lock <id> open | feed on|reset | ws <станция> | index [reroll]
 * | antenna [азимут] | outage [on|off] | ambient [<сцена>].
 * rebuild: стирает старую постройку этого мира, сбрасывает прогресс и строит заново по чертежам.
 * ws: открыть рабочую станцию (ArchiveDefs.Station.key) с доступом по текущему состоянию мира — для проверки содержимого.
 * index: порядок строк карточки RECORDER 47-OP в этом сохранении и ответ, варианты MAIN FEED, входного замка, отсека и формы 12-B,
 * коды панелей LIFT ACCESS B2-B4 (шифры 7-9) с подсказкой, откуда они;
 * reroll — новое зерно шифров (все варианты меняются, знаки тоже).
 * antenna: азимут тарелки ANT-1 (шифр 8), начальный и ретранслятора, питание, замок; с числом — поставить азимут (кратно шагу).
 * outage: «обесточено» (LabOutage) — состояние; on — погасить свет этажа под игроком без погони (проверка света, щита этажа,
 * лифта; погоня — отладкой режиссёра в лаборатории), off — вернуть свет.
 * ambient: тихие эпизоды LabAmbient — ожидание/кулдаун и номер последней сцены; с числом — проиграть сцену 0..14 сразу (11..14 — ночной дом).
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class LabCommand {
    private LabCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hypersomnia_lab")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("where").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    BlockPos site = LabPlacement.site(p.server);
                    LabSavedData d = LabSavedData.get(p.serverLevel());
                    ctx.getSource().sendSuccess(() -> Component.literal("Lab entrance x=" + site.getX() + " z=" + site.getZ()
                            + ", built in this world: " + d.built), false);
                    return 1;
                }))
                .then(Commands.literal("tp").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    BlockPos site = LabPlacement.site(p.server);
                    ServerLevel level = p.serverLevel();
                    int z = site.getZ() + 12;
                    int y = LabPlacement.ground(level, site.getX(), z);
                    p.teleportTo(site.getX() + 0.5, y, z + 0.5);
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    LabSavedData d = LabSavedData.get(p.serverLevel());
                    ctx.getSource().sendSuccess(() -> Component.literal("built=" + d.built + " buildFailed=" + d.buildFailed + " dream=" + d.dream
                            + " builtMask=" + Integer.toBinaryString(d.builtMask) + " reached=" + d.reached
                            + " locks=" + Integer.toBinaryString(d.locksOpen) + " flags=" + Integer.toBinaryString(d.flags)
                            + " breakers=" + d.breakerSeq + (d.breakerTripped ? " tripped" : "") + " origin=" + d.originX + "," + d.originZ
                            + " groundY=" + d.groundY), false);
                    return 1;
                }))
                .then(Commands.literal("rebuild").executes(ctx -> {
                    ctx.getSource().sendFailure(Component.literal("WARNING: this destroys lab blocks, stored items and progress. Back up the world first. To proceed: /hypersomnia_lab rebuild confirm"));
                    return 0;
                }).then(Commands.literal("confirm").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    ServerLevel level = p.serverLevel();
                    boolean dream = level.dimension() == DreamDimension.LEVEL;
                    if (!dream && level.dimension() != Level.OVERWORLD) {
                        ctx.getSource().sendFailure(Component.literal("Only in Overworld or dream"));
                        return 0;
                    }
                    if (LabLift.busy(level)) {
                        ctx.getSource().sendFailure(Component.literal("Wait for the lift to arrive before rebuilding or relocating the lab"));
                        return 0;
                    }
                    LabSavedData d = LabSavedData.get(level);
                    for (int n = 0; n < LabTuning.STOPS; n++) LabBuilder.clear(level, d, n);
                    d.resetProgress();
                    LabPlacement.build(level, LabPlacement.site(p.server), dream);
                    if (d.buildFailed) {
                        ctx.getSource().sendFailure(Component.literal("Lab rebuild failed; inspect the server log and world before retrying"));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("Lab rebuilt (" + (dream ? "dream" : "real")
                            + "), mask=" + Integer.toBinaryString(d.builtMask)), false);
                    return 1;
                })))
                .then(Commands.literal("resite").executes(ctx -> {
                    ctx.getSource().sendFailure(Component.literal("WARNING: this destroys lab blocks, stored items and progress. Back up the world first. To proceed: /hypersomnia_lab resite confirm"));
                    return 0;
                }).then(Commands.literal("confirm").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    ServerLevel level = p.serverLevel();
                    boolean dream = level.dimension() == DreamDimension.LEVEL;
                    if (!dream && level.dimension() != Level.OVERWORLD) {
                        ctx.getSource().sendFailure(Component.literal("Only in Overworld or dream"));
                        return 0;
                    }
                    if (LabLift.busy(level)) {
                        ctx.getSource().sendFailure(Component.literal("Wait for the lift to arrive before rebuilding or relocating the lab"));
                        return 0;
                    }
                    LabSavedData d = LabSavedData.get(level);
                    for (int n = 0; n < LabTuning.STOPS; n++) LabBuilder.clear(level, d, n);
                    d.resetProgress();
                    LabSavedData ow = LabSavedData.get(p.server.overworld());
                    ow.sited = false;
                    ow.siteTry++;
                    ow.groundY = 0;
                    d.groundY = 0;
                    ow.setDirty();
                    BlockPos site = LabPlacement.site(p.server);
                    LabPlacement.build(level, site, dream);
                    if (d.buildFailed) {
                        ctx.getSource().sendFailure(Component.literal("Lab build at the new site failed; inspect the server log and world before retrying"));
                        return 0;
                    }
                    int z = site.getZ() + 12;
                    p.teleportTo(site.getX() + 0.5, LabPlacement.ground(level, site.getX(), z), z + 0.5);
                    ctx.getSource().sendSuccess(() -> Component.literal("New site x=" + site.getX() + " z=" + site.getZ()
                            + " (try " + ow.siteTry + "). Back up the other world before rebuilding."), false);
                    return 1;
                })))
                .then(Commands.literal("unlock")
                        .then(Commands.argument("stop", IntegerArgumentType.integer(0, LabTuning.STOPS - 1)).executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ServerLevel level = p.serverLevel();
                            LabSavedData d = LabSavedData.get(level);
                            d.reached = IntegerArgumentType.getInteger(ctx, "stop");
                            LabLift.refreshDoors(level, d);
                            d.setDirty();
                            ctx.getSource().sendSuccess(() -> Component.literal("reached=" + d.reached), false);
                            return 1;
                        })))
                .then(Commands.literal("lock")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1, 9))
                                .then(Commands.literal("open").executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    ServerLevel level = p.serverLevel();
                                    LabLocks.grant(level, LabSavedData.get(level), IntegerArgumentType.getInteger(ctx, "id"));
                                    return 1;
                                }))))
                .then(Commands.literal("feed")
                        .then(Commands.literal("on").executes(ctx -> {
                            ServerLevel level = ctx.getSource().getPlayerOrException().serverLevel();
                            LabBreakers.feedOn(level, LabSavedData.get(level));
                            ctx.getSource().sendSuccess(() -> Component.literal("Main feed on"), false);
                            return 1;
                        }))
                        .then(Commands.literal("reset").executes(ctx -> {
                            ServerLevel level = ctx.getSource().getPlayerOrException().serverLevel();
                            LabBreakers.reset(level, LabSavedData.get(level));
                            ctx.getSource().sendSuccess(() -> Component.literal("Main feed off, breakers open"), false);
                            return 1;
                        })))
                .then(Commands.literal("index")
                        .executes(ctx -> index(ctx.getSource().getPlayerOrException()))
                        .then(Commands.literal("reroll").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            LabSavedData.reseed(LabSavedData.get(p.server.overworld()));
                            PacketDistributor.sendToAllPlayers(new LabSeedPayload(LabSavedData.cipherSeed(p.server)));
                            // стойки B3 и пустые места хранилища B2 зависят от зерна: перекрасить в обоих мирах
                            for (ServerLevel lv : new ServerLevel[]{p.server.overworld(), p.server.getLevel(com.hypersomnia.dream.DreamDimension.LEVEL)})
                                if (lv != null && LabSavedData.get(lv).built) LabFixtures.refresh(lv, LabSavedData.get(lv));
                            LabAntenna.reseed(p.server.overworld(), LabSavedData.get(p.server.overworld())); // начальный азимут ANT-1
                            return index(p);
                        })))
                .then(Commands.literal("antenna")
                        .executes(ctx -> antenna(ctx.getSource().getPlayerOrException(), -1))
                        .then(Commands.argument("azimuth", IntegerArgumentType.integer(0, 359)).executes(ctx ->
                                antenna(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "azimuth")))))
                .then(Commands.literal("outage")
                        .executes(ctx -> say(ctx.getSource(), LabOutage.status(ctx.getSource().getLevel())))
                        .then(Commands.literal("on").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ServerLevel lv = p.serverLevel();
                            int stop = LabProtection.stopAt(LabSavedData.get(lv), p.blockPosition());
                            boolean ok = stop >= 1 && LabOutage.begin(lv, stop, p);
                            return say(ctx.getSource(), ok ? "Outage started on " + LabTuning.stopLabel(stop)
                                    : "Outage: stand on a built floor B1-B5 of the real lab (no outage running, tubes present)");
                        }))
                        .then(Commands.literal("off").executes(ctx -> {
                            ServerLevel lv = ctx.getSource().getLevel();
                            LabOutage.restore(lv, LabSavedData.get(lv));
                            return say(ctx.getSource(), LabOutage.status(lv));
                        })))
                .then(Commands.literal("ambient")
                        .executes(ctx -> say(ctx.getSource(), LabAmbientHandler.status(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("scene", IntegerArgumentType.integer(0, ObserverTuning.LAB_AMBIENT_SCENE_TICKS.length - 1))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    int scene = IntegerArgumentType.getInteger(ctx, "scene");
                                    return say(ctx.getSource(), LabAmbientHandler.play(p, scene)
                                            ? "Ambient scene " + scene + " sent" : "Ambient: no chunk at the sound position");
                                })))
                .then(Commands.literal("ws")
                        .then(Commands.argument("station", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        ArchiveDefs.stations().stream().map(ArchiveDefs.Station::key), b))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    ArchiveDefs.Station st = ArchiveDefs.station(StringArgumentType.getString(ctx, "station"));
                                    if (st == null) {
                                        ctx.getSource().sendFailure(Component.literal("Unknown workstation"));
                                        return 0;
                                    }
                                    ArchiveAccess.Snapshot snap = ArchiveAccess.snapshot(p, LabSavedData.get(p.serverLevel()), st.id());
                                    PacketDistributor.sendToPlayer(p, new LabTerminalPayload(st.id(), snap.listed(), snap.readable()));
                                    return 1;
                                }))));
    }

    /** Антенна ANT-1 Верхнего мира: состояние; az >= 0 — поставить азимут (вниз до кратного шагу). */
    private static int antenna(ServerPlayer p, int az) {
        ServerLevel level = p.server.overworld();
        LabSavedData d = LabSavedData.get(level);
        if (LabAntenna.drive(d) == null) {
            p.sendSystemMessage(Component.literal("No ANT-1 in the Overworld lab" + (d.roofPending ? " yet: roof upgrade pending (load the site)" : "")));
            return 0;
        }
        if (az >= 0) LabAntenna.set(level, d, az / LabTuning.ANTENNA_STEP * LabTuning.ANTENNA_STEP);
        long seed = LabSavedData.cipherSeed(p.server);
        p.sendSystemMessage(Component.literal("ANT-1 az " + LabAntenna.azimuth(d.dishAngle) + "  start " + LabAntenna.azimuth(LabVariants.antennaStart(seed))
                + "  relay " + LabAntenna.azimuth(LabVariants.antennaTarget(seed)) + "  feed=" + d.hasFlag(LabSavedData.FLAG_FEED)
                + "  lock B3=" + (d.isLockOpen(LabTuning.LOCK_BUFFER) ? "open" : "closed") + "  displays=" + LabAntenna.count(level, d)));
        return 1;
    }

    /** Порядок строк карточки (номера воспоминаний сверху вниз), ответ и состояние игрока. */
    private static int index(ServerPlayer p) {
        long seed = LabSavedData.cipherSeed(p.server);
        int[] order = WorldVariant.order(seed, LabTuning.INDEX_CARD, LabTuning.INDEX_DIGITS.length);
        StringBuilder rows = new StringBuilder();
        for (int k : order) rows.append(rows.isEmpty() ? "" : " ").append(k + 1);
        ObserverProgressData data = p.getData(ModAttachments.OBSERVER);
        boolean open = LabSavedData.get(p.server.overworld()).isLockOpen(LabTuning.LOCK_INDEX);
        String msg = "Index rows (memory no.): " + rows + "  answer=" + LabLocks.indexCode(seed)
                + "  lock=" + (open ? "open" : "closed") + "  memories=" + data.memoriesCount() + "/" + com.hypersomnia.document.MemoryTuning.COUNT
                + "  draft=" + (data.indexDraft().isEmpty() ? "-" : data.indexDraft()) + "  recordReady=" + data.recordReady();
        p.sendSystemMessage(Component.literal(msg));
        // варианты остальных шифров этого мира (отладка)
        p.sendSystemMessage(Component.literal("Variants: feed " + LabVariants.feedText(seed, 'C') + " (rev B " + LabVariants.feedText(seed, 'B')
                + ")  hatch " + String.format("%04d", LabVariants.hatchCode(seed, false)) + " (rule " + LabVariants.hatchRule(seed)
                + ", dream " + String.format("%04d", LabTuning.HATCH_DREAM_CODE) + ")  bay " + LabVariants.bayTime(seed)
                + " -> " + LabVariants.bayCode(seed) + "  posts " + LabVariants.postCode(seed) + " (paper " + LabVariants.planCode(seed)
                + ")  morse " + LabTuning.MORSE_WORDS[LabVariants.morseWord(seed)]));
        int[] gaps = LabVariants.stacksGaps(seed);
        StringBuilder places = new StringBuilder();
        for (int g : gaps) places.append(places.length() == 0 ? "" : " ").append(g + 1);
        int[] hold = LabVariants.rackHold(seed);
        StringBuilder racks = new StringBuilder();
        for (int i = 0; i < LabTuning.RACK_HELD; i++) racks.append(i == 0 ? "" : " ").append(hold[i]);
        p.sendSystemMessage(Component.literal("Lift access: B2->B3 " + LabVariants.stacksCode(seed) + " (empty places " + places
                + ")  B3->B4 ANT-1 " + LabAntenna.azimuth(LabVariants.antennaStart(seed)) + " -> " + LabAntenna.azimuth(LabVariants.antennaTarget(seed))
                + " (relay azimuth in BUFFER.LOG; held racks " + racks + ", copy " + hold[LabTuning.RACK_HELD]
                + ")  B4->B5 " + LabVariants.wardCode(seed) + " (tally groups in the 2D note, B4)"));
        return 1;
    }

    private static int say(net.minecraft.commands.CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
