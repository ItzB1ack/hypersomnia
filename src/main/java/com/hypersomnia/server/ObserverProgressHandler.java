package com.hypersomnia.server;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.ObserverProgressData;
import com.hypersomnia.network.ObserverStagePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Считает прожитые дни и опрашивает маршрут (только Верхний мир), отправляет прогресс клиенту при изменении и при входе. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class ObserverProgressHandler {
    private ObserverProgressHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ObserverProgressData data = player.getData(ModAttachments.OBSERVER);
        if (player.isAlive() && player.level().dimension() == Level.OVERWORLD) {
            data.addTick();
            // Память привычек: где игрок бывает (не во сне, не наблюдателем)
            if (!player.isSleeping() && !player.isSpectator()) {
                data.sampleRoute(player.getBlockX(), player.getBlockY(), player.getBlockZ());
            }
        }
        if (data.consumeDirty()) send(player, data);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            send(player, player.getData(ModAttachments.OBSERVER));
        }
    }

    public static void send(ServerPlayer player, ObserverProgressData data) {
        PacketDistributor.sendToPlayer(player, new ObserverStagePayload(data.getDays(), data.getMemories(),
                data.stageOverride(), data.routeList(), data.sleepList(),
                com.hypersomnia.finale.FinaleTuning.pack(data.ending(), data.truth())));
    }
}