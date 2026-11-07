package com.hypersomnia.nightmare;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.dream.DreamAttachments;
import com.hypersomnia.dream.DreamData;
import com.hypersomnia.dream.DreamDimension;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Часы неизбежно доходят до 03:13, независимо от действий у пульта. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class SessionHandler {
    private SessionHandler() {}
    private static boolean active(ServerPlayer p) {
        DreamData d = p.getData(DreamAttachments.DREAM);
        return p.isAlive() && p.serverLevel().dimension().equals(DreamDimension.LEVEL)
                && d.isInDream() && NightmareDefs.SESSION_47.id().equals(d.getKind());
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !active(p)
                || p.tickCount % NightmareTuning.SESSION_CLOCK_UPDATE_TICKS != 0) return;
        DreamData data = p.getData(DreamAttachments.DREAM);
        int elapsed = NightmareTuning.SESSION_DURATION_TICKS - data.getTicksLeft();
        String clock = elapsed < NightmareTuning.SESSION_CLOCK_0307_AT ? "02:41"
                : elapsed < NightmareTuning.SESSION_CLOCK_0312_AT ? "03:07"
                : elapsed < NightmareTuning.SESSION_CLOCK_0313_AT ? "03:12" : "03:13";
        p.displayClientMessage(Component.translatable("hypersomnia.nightmare.session47.clock", clock), true);
        // [новое] Тихий счёт последних минут и обрыв: затемнение перед концом (звук — ванильный запасной).
        if (elapsed >= NightmareTuning.SESSION_CLOCK_0312_AT && p.tickCount % NightmareTuning.SESSION_TICK_SOUND_EVERY == 0)
            p.playNotifySound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.value(),
                    net.minecraft.sounds.SoundSource.AMBIENT, 0.4f, 0.6f);
        if (elapsed >= NightmareTuning.SESSION_CLOCK_0313_AT && !p.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS))
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS,
                    NightmareTuning.SESSION_BLACKOUT_TICKS, 0, false, false, false));
        int notice = elapsed < NightmareTuning.SESSION_CLOCK_0307_AT ? 0
                : elapsed < NightmareTuning.SESSION_CLOCK_0312_AT ? 1
                : elapsed < NightmareTuning.SESSION_CLOCK_0313_AT ? 2 : 3;
        if (notice > data.getSessionNotice()) {
            data.setSessionNotice(notice);
            p.displayClientMessage(Component.translatable("hypersomnia.nightmare.session47.notice." + notice), false);
        }
    }
    /** Порядок станций по номеру попытки: пульт -> переговорное -> панель двери -> пульт. */
    private static final int[] ORDER = {0, 1, 2, 0};
    private static final String[] NAMES = {"console", "intercom", "door"};
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer p) || !active(p)) return;
        int station = SessionBuilder.stationAt(event.getPos());
        if (station < 0) return;
        event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
        DreamData data = p.getData(DreamAttachments.DREAM);
        int left = data.getTicksLeft();
        int elapsed = NightmareTuning.SESSION_DURATION_TICKS - left;
        if (elapsed >= NightmareTuning.SESSION_CLOCK_0313_AT) {
            p.displayClientMessage(Component.translatable("hypersomnia.nightmare.session47.expired"), false);
            return;
        }
        int attempt = data.getSessionAttempts();
        if (attempt >= ORDER.length) {
            p.displayClientMessage(Component.translatable("hypersomnia.nightmare.session47.closed"), true);
            return;
        }
        if (station != ORDER[attempt]) {
            p.displayClientMessage(Component.translatable("hypersomnia.nightmare.session47.wrong." + NAMES[ORDER[attempt]]), true);
            return;
        }
        if (data.getSessionNextAttemptTicks() > 0 && left > data.getSessionNextAttemptTicks()) {
            p.displayClientMessage(Component.translatable("hypersomnia.nightmare.session47.pending"), true);
            return;
        }
        int[] waits = NightmareTuning.SESSION_RETRY_BY_ATTEMPT;
        data.setSessionNextAttemptTicks(Math.max(0, left - waits[Math.min(attempt, waits.length - 1)]));
        data.addSessionAttempt();
        p.displayClientMessage(Component.translatable("hypersomnia.nightmare.session47.attempt." + attempt), false);
    }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(DreamDimension.LEVEL)
                && !event.getPlayer().isCreative() && SessionBuilder.containsBuilt(level, event.getPos())) event.setCanceled(true);
    }
    @SubscribeEvent public static void placeBlock(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(DreamDimension.LEVEL)
                && event.getEntity() instanceof ServerPlayer p && !p.isCreative()
                && SessionBuilder.containsBuilt(level, event.getPos())) event.setCanceled(true);
    }
    @SubscribeEvent public static void explode(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(DreamDimension.LEVEL))
            event.getAffectedBlocks().removeIf(pos -> SessionBuilder.containsBuilt(level, pos));
    }
}
