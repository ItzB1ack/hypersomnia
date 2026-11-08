package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import qouteall.imm_ptl.core.ClientWorldLoader;

/**
 * Обход падения Immersive Portals 6.0.7 при выходе из сна.
 * <p>
 * При смене измерения {@code Minecraft.setLevel} вызывает {@code ClientWorldLoader.cleanUp()}, который ставит
 * {@code isInitialized = false}. Следующий пакет ({@code ClientboundBlockChangedAckPacket}) попадает в redirect
 * IP, тот зовёт {@code getClientWorlds()}, а там {@code Validate.isTrue(isInitialized)} — и клиент вылетает с
 * «Ошибка сетевого протокола» ещё до того, как IP успевает переинициализироваться в тике.
 * <p>
 * {@link ClientPlayerNetworkEvent.Clone} срабатывает в конце {@code handleRespawn}, уже после {@code setLevel} и
 * после {@code minecraft.player = localplayer1}, то есть ровно в окне между сменой измерения и следующим пакетом.
 * {@link ClientWorldLoader#initializeIfNeeded()} — штатный публичный вход IP, он сам проверяет
 * {@code isInitialized} и совпадение уровней игрока и клиента.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class ImmersivePortalsCompat {
    private ImmersivePortalsCompat() {}

    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        if (!ClientWorldLoader.getIsInitialized()) {
            ClientWorldLoader.initializeIfNeeded();
        }
    }
}
