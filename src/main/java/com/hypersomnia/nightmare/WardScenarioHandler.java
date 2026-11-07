package com.hypersomnia.nightmare;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.ObserverProgressData;
import com.hypersomnia.document.DocumentItem;
import com.hypersomnia.document.DrawingTuning;
import com.hypersomnia.dream.DreamAttachments;
import com.hypersomnia.dream.DreamData;
import com.hypersomnia.dream.DreamDimension;
import com.hypersomnia.dream.DreamTransition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Пустая палата и тихие реплики персональны: другой игрок не теряет осколков или лист. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class WardScenarioHandler {
    private static final Map<UUID, Integer> SPOKEN_LOOP = new HashMap<>();
    private WardScenarioHandler() {}

    private static boolean inWard(ServerPlayer player, DreamData data) {
        return player.isAlive() && !player.isSpectator()
                && player.level().dimension().equals(DreamDimension.LEVEL)
                && data.isInDream() && NightmareDefs.WARD_CORRIDOR.id().equals(data.getKind());
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) return;
        DreamData data = player.getData(DreamAttachments.DREAM);
        if (!inWard(player, data) || !WardScenario.exitBed(event.getPos(), data.getWardLoops())
                || !(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof WardBedBlock)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        ObserverProgressData progress = player.getData(ModAttachments.OBSERVER);
        if (!progress.hasBlank()) {
            ItemStack paper = DocumentItem.make(DrawingTuning.BLANK_ID);
            if (!player.getInventory().add(paper)) {
                player.displayClientMessage(Component.translatable("hypersomnia.ward.inventory_full"), true);
                return;
            }
            progress.addDrawing(DrawingTuning.BLANK_INDEX);
        }
        DreamTransition.exit(player);
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        DreamData data = player.getData(DreamAttachments.DREAM);
        UUID id = player.getUUID();
        if (!inWard(player, data)) { SPOKEN_LOOP.remove(id); return; }
        if (player.tickCount % NightmareTuning.WARD_DIALOGUE_CHECK_TICKS != 0) return;
        BlockPos pos = player.blockPosition();
        int period = WardScenario.roomPeriod(pos), loops = data.getWardLoops();
        if (period < 0 || WardScenario.emptyWard(loops, period, pos.getZ() < NightmareDefs.WARD_CORRIDOR.origin().getZ() ? -1 : 1)
                || SPOKEN_LOOP.getOrDefault(id, -1) == loops) return;
        int line = Math.min(loops, NightmareTuning.WARD_DIALOGUE_LINES - 1);
        player.displayClientMessage(Component.translatable("hypersomnia.ward.line." + line), true);
        SPOKEN_LOOP.put(id, loops);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { SPOKEN_LOOP.remove(event.getEntity().getUUID()); }
    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) { SPOKEN_LOOP.clear(); }
}
