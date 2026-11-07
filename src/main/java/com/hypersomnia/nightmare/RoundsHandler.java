package com.hypersomnia.nightmare;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.dream.DreamAttachments;
import com.hypersomnia.dream.DreamData;
import com.hypersomnia.dream.DreamDimension;
import com.hypersomnia.dream.DreamTransition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class RoundsHandler {
    private RoundsHandler() {}
    private static boolean active(ServerPlayer p) {
        DreamData data = p.getData(DreamAttachments.DREAM);
        return p.isAlive() && p.serverLevel().dimension().equals(DreamDimension.LEVEL)
                && data.isInDream() && NightmareDefs.ROUNDS.id().equals(data.getKind());
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !active(p)) return;
        if (p.isSprinting()) p.setSprinting(false);
        if (p.isSleeping() && p.getSleepTimer() >= NightmareTuning.ROUNDS_EXIT_LIE_TICKS
                && p.getSleepingPos().map(RoundsBuilder::isExit).orElse(false)) {
            p.stopSleepInBed(true, true);
            DreamTransition.exit(p);
        }
    }
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer p)
                || !active(p) || !RoundsBuilder.isExit(event.getPos())
                || !event.getLevel().getBlockState(event.getPos()).is(Blocks.WHITE_BED)) return;
        event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
        if (!p.isSleeping()) p.startSleeping(RoundsBuilder.exitFoot().east());
    }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(DreamDimension.LEVEL)
                && !event.getPlayer().isCreative() && RoundsBuilder.containsBuilt(level, event.getPos()))
            event.setCanceled(true);
    }
    @SubscribeEvent public static void placeBlock(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(DreamDimension.LEVEL)
                && event.getEntity() instanceof ServerPlayer p && !p.isCreative()
                && RoundsBuilder.containsBuilt(level, event.getPos())) event.setCanceled(true);
    }
    @SubscribeEvent public static void explode(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(DreamDimension.LEVEL))
            event.getAffectedBlocks().removeIf(pos -> RoundsBuilder.containsBuilt(level, pos));
    }
}
