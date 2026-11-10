package com.hypersomnia.nightmare;

import com.hypersomnia.DebugMode;
import com.hypersomnia.Hypersomnia;
import com.hypersomnia.dream.DreamAttachments;
import com.hypersomnia.dream.DreamDimension;
import com.hypersomnia.dream.DreamTransition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Операторский вход: ждём загрузки сущностей между тиками, не блокируя сервер. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class WardEntryQueue {
    private static final int INTERVAL = 5;
    private static final int MAX_ATTEMPTS = 120; // 30 секунд при 20 TPS
    private static final Map<UUID, Request> REQUESTS = new HashMap<>();
    private WardEntryQueue() {}

    private static final class Request {
        final MinecraftServer server;
        final ServerPlayer player;
        final ResourceKey<Level> startDimension;
        int attempts;
        Request(ServerPlayer player) {
            server = player.server;
            this.player = player;
            startDimension = player.serverLevel().dimension();
        }
    }

    public static boolean request(ServerPlayer player) {
        if (!player.isAlive()) {
            DebugMode.chat(player, Component.literal("Ward: cannot enter while dead."));
            return false;
        }
        if (player.getData(DreamAttachments.DREAM).isInDream()
                || player.serverLevel().dimension().equals(DreamDimension.LEVEL)) {
            DebugMode.chat(player, Component.literal("Ward: already in a dream. Use /hypersomnia_nightmare exit first."));
            return false;
        }
        if (player.server.getLevel(DreamDimension.LEVEL) == null) {
            DebugMode.chat(player, Component.literal("Ward: dream dimension is missing."));
            return false;
        }
        REQUESTS.put(player.getUUID(), new Request(player));
        DebugMode.chat(player, Component.literal("Ward: preparing portals, please wait..."));
        return true;
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % INTERVAL != 0 || REQUESTS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Request>> it = REQUESTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Request> entry = it.next();
            Request request = entry.getValue();
            if (request.server != server) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != request.player) { it.remove(); continue; }
            if (!player.isAlive() || !player.serverLevel().dimension().equals(request.startDimension)
                    || player.getData(DreamAttachments.DREAM).isInDream()) {
                DebugMode.chat(player, Component.literal("Ward: entry cancelled because your state or dimension changed."));
                it.remove();
                continue;
            }
            ServerLevel dream = server.getLevel(DreamDimension.LEVEL);
            if (dream == null) {
                DebugMode.chat(player, Component.literal("Ward: dream dimension is missing."));
                it.remove();
                continue;
            }
            if (NightmareSavedData.get(dream).wardBuildFailed()) {
                it.remove();
                DebugMode.chat(player, Component.literal("Ward build failed. Inspect latest.log and the world before rebuilding."));
                continue;
            }
            if (!NightmareSavedData.get(dream).isBuilt(NightmareDefs.WARD_CORRIDOR)) {
                // Действие как в прежней команде: первый вход строит коридор один раз.
                NightmareBuilder.build(server, NightmareDefs.WARD_CORRIDOR);
                if (NightmareSavedData.get(dream).wardBuildFailed()) {
                    it.remove();
                    DebugMode.chat(player, Component.literal("Ward build failed. Inspect latest.log and the world before rebuilding."));
                    continue;
                }
            }
            WardLoopPortals.Result result = WardLoopPortals.ensureDetailed(dream);
            request.attempts++;
            if (result == WardLoopPortals.Result.READY) {
                it.remove();
                if (!DreamTransition.enterNightmare(player, NightmareDefs.WARD_CORRIDOR)) {
                    DebugMode.chat(player, Component.literal("Ward: portals ready, but your current state prevents entry."));
                }
            } else if (result != WardLoopPortals.Result.LOADING) {
                it.remove();
                DebugMode.chat(player, Component.literal("Ward portal error: " + result + ". Do not delete portals; send this message and latest.log."));
            } else if (request.attempts >= MAX_ATTEMPTS) {
                it.remove();
                DebugMode.chat(player, Component.literal("Ward portal error: LOADING_TIMEOUT. Send latest.log."));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Request request = REQUESTS.get(event.getEntity().getUUID());
        if (request != null && request.player == event.getEntity()) REQUESTS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        REQUESTS.entrySet().removeIf(e -> e.getValue().server == event.getServer());
    }
}
