package com.hypersomnia.nightmare;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.dream.DreamAttachments;
import com.hypersomnia.dream.DreamData;
import com.hypersomnia.dream.DreamDimension;
import com.hypersomnia.dream.DreamTransition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
    /** Дальняя точка пути на игрока; не сохраняется, при перезаходе отсчёт идёт заново. */
    private static final java.util.Map<java.util.UUID, Integer> BEST = new java.util.HashMap<>();
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (!active(p)) { BEST.remove(p.getUUID()); HOLD.remove(p.getUUID()); return; }
        if (p.isSprinting()) p.setSprinting(false);
        if (p.tickCount % 20 == 0) {
            // [новое] Шаг обхода всегда медленный; без урона.
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
                    60, NightmareTuning.ROUNDS_WALK_SLOW_AMPLIFIER, false, false, false));
            int idx = RoundsBuilder.indexAt(p.getX());
            int best = BEST.getOrDefault(p.getUUID(), 0);
            if (idx > best) {
                BEST.put(p.getUUID(), idx);
                int step = NightmareTuning.ROUNDS_MILESTONE_EVERY;
                if (idx / step > best / step && idx < NightmareTuning.ROUNDS_CAPSULES - 1)
                    p.displayClientMessage(Component.translatable("hypersomnia.nightmare.rounds.milestone", idx + 1), true);
            }
        }
        checks(p);
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
    private static final java.util.Map<java.util.UUID, Integer> HOLD = new java.util.HashMap<>();
    /** [новое] Контрольные площадки: стоять неподвижно; без отметки дальше за площадку не пускает. */
    private static void checks(ServerPlayer p) {
        DreamData data = p.getData(DreamAttachments.DREAM);
        double moved = Math.abs(p.getX() - p.xo) + Math.abs(p.getZ() - p.zo);
        for (int k = 0; k < NightmareTuning.ROUNDS_CHECKS.length; k++) {
            if (data.hasRoundsCheck(k)) continue;
            if (RoundsBuilder.inCheckZone(k, p.getX(), p.getZ())) {
                int held = moved < 0.003 && p.onGround() && !p.isSleeping() ? HOLD.merge(p.getUUID(), 1, Integer::sum) : 0;
                HOLD.put(p.getUUID(), held);
                if (held >= NightmareTuning.ROUNDS_CHECK_HOLD_TICKS) {
                    data.setRoundsCheck(k); HOLD.remove(p.getUUID());
                    p.displayClientMessage(Component.translatable("hypersomnia.nightmare.rounds.check.done"), true);
                } else if (p.tickCount % 20 == 0) {
                    p.displayClientMessage(Component.translatable("hypersomnia.nightmare.rounds.check.hold"), true);
                }
            } else if (p.getX() > RoundsBuilder.checkGateX(k)) {
                BlockPos back = RoundsBuilder.aisle(NightmareTuning.ROUNDS_CHECKS[k]);
                p.teleportTo(p.serverLevel(), back.getX() + 1.5, back.getY(), back.getZ() + 0.5, p.getYRot(), p.getXRot());
                p.resetFallDistance();
                p.displayClientMessage(Component.translatable("hypersomnia.nightmare.rounds.check.required"), true);
            }
            return; // только ближайшая непройденная точка
        }
    }
    private static void touched(ServerPlayer p, net.minecraft.world.entity.Entity target) {
        if (!active(p) || !(target instanceof net.minecraft.world.entity.item.ItemEntity)
                || !target.getTags().contains("hypersomnia_rounds_syringe")) return;
        // [новое] Не вред, а возврат назад по ряду: обход нельзя ускорить.
        int back = Math.max(0, RoundsBuilder.indexAt(p.getX()) - NightmareTuning.ROUNDS_TOUCH_BACK_CAPSULES);
        BlockPos to = RoundsBuilder.aisle(back);
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS,
                NightmareTuning.ROUNDS_TOUCH_BLIND_TICKS, 0, false, false, false));
        p.teleportTo(p.serverLevel(), to.getX() + 0.5, to.getY(), to.getZ() + 0.5, p.getYRot(), p.getXRot());
        p.resetFallDistance();
        BEST.put(p.getUUID(), back);
        p.displayClientMessage(Component.translatable("hypersomnia.nightmare.rounds.touch"), true);
    }
    @SubscribeEvent public static void touchUse(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer p && active(p) && event.getTarget() instanceof net.minecraft.world.entity.item.ItemEntity) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
            touched(p, event.getTarget());
        }
    }
    @SubscribeEvent public static void touchHit(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && active(p) && event.getTarget() instanceof net.minecraft.world.entity.item.ItemEntity) {
            event.setCanceled(true);
            touched(p, event.getTarget());
        }
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
