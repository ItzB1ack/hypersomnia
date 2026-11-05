package com.hypersomnia.syringe;

import com.hypersomnia.FatigueData;
import com.hypersomnia.FatigueTuning;
import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.dream.DreamAttachments;
import com.hypersomnia.network.SyringeSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Искусственное бодрствование. Усталость «замораживается» на низком уровне, а настоящая копится под спудом;
 * по окончании наступает откат: снятое с процентом + накопленное. Сон в мире снов гасит долг.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class SyringeHandler {
    private SyringeHandler() {}

    /** Вызывается из предмета. false, если укол сейчас невозможен. */
    public static boolean inject(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) return false;
        SyringeData s = player.getData(SyringeAttachments.SYRINGE);
        if (s.getWakeTicks() > 0) return false;
        if (player.getData(DreamAttachments.DREAM).isInDream()) return false;

        FatigueData f = player.getData(ModAttachments.FATIGUE);
        float eff = (float) Math.pow(SyringeTuning.EFFICIENCY_BASE, s.getTolerance());
        int duration = Math.max(SyringeTuning.MIN_WAKE_TICKS, Math.round(SyringeTuning.BASE_WAKE_TICKS * eff));
        int pin = Math.round(FatigueTuning.MAX_FATIGUE
                * (SyringeTuning.WAKE_LEVEL + (1f - eff) * SyringeTuning.PIN_TOLERANCE_GAIN));
        int removed = Math.max(0, f.getFatigue() - pin);

        s.begin(duration, pin, removed);
        s.addTolerance(1);
        f.setFatigue(Math.min(f.getFatigue(), pin));
        // Награда: быстрая добыча и ночное зрение на всё время бодрствования
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, duration, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, duration, 0, false, false, true));

        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_BREATH,
                SoundSource.PLAYERS, 1.0f, 0.6f);
        return true;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SyringeData s = player.getData(SyringeAttachments.SYRINGE);

        // Толерантность медленно спадает после каждого настоящего сна (выход из мира снов)
        boolean inDream = player.getData(DreamAttachments.DREAM).isInDream();
        if (s.wasInDream() && !inDream) s.addTolerance(-1);
        s.setWasInDream(inDream);

        if (s.getWakeTicks() > 0) {
            boolean exempt = player.isCreative() || player.isSpectator() || !player.isAlive();
            if (inDream || exempt) {
                s.clearWake(); // сон оплатил долг
                player.removeEffect(MobEffects.DIG_SPEED);
                player.removeEffect(MobEffects.NIGHT_VISION);
            } else {
                tickWake(player, s);
            }
        }
        sync(player, s);
    }

    private static void tickWake(ServerPlayer player, SyringeData s) {
        FatigueData f = player.getData(ModAttachments.FATIGUE);
        int cur = f.getFatigue();
        if (cur > s.getPin()) {
            s.addAccrued(cur - s.getPin()); // то, что набежало бы на самом деле
            f.setFatigue(s.getPin());
        }

        int level = SyringeTuning.sideLevel(s.getTolerance());
        player.causeFoodExhaustion(SyringeTuning.EXHAUSTION_BASE + SyringeTuning.EXHAUSTION_PER_LEVEL * level);

        s.setWakeTicks(s.getWakeTicks() - 1);
        if (s.getWakeTicks() <= 0) rebound(player, s, f);
    }

    private static void rebound(ServerPlayer player, SyringeData s, FatigueData f) {
        float interest = SyringeTuning.INTEREST_BASE + SyringeTuning.INTEREST_PER_TOLERANCE * s.getTolerance();
        long target = (long) s.getPin() + Math.round(s.getDebt() * (1f + interest)) + s.getAccrued();
        s.clearWake();
        if (target >= FatigueTuning.MAX_FATIGUE * SyringeTuning.COLLAPSE_RATIO) {
            f.setFatigue(FatigueTuning.MAX_FATIGUE); // дальше сработает принудительный сон
            return;
        }
        f.setFatigue((int) Math.max(f.getFatigue(), target));

        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SyringeTuning.CRASH_SLOW_TICKS, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, SyringeTuning.CRASH_NAUSEA_TICKS, 0, false, false, true));
    }

    private static void sync(ServerPlayer player, SyringeData s) {
        int active = s.getWakeTicks() > 0 ? 1 : 0;
        int level = active == 1 ? SyringeTuning.sideLevel(s.getTolerance()) : 0;
        if (active != s.getLastSentActive() || level != s.getLastSentLevel()) {
            PacketDistributor.sendToPlayer(player, new SyringeSyncPayload(s.getWakeTicks(), level));
            s.markSent(active, level);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.getData(SyringeAttachments.SYRINGE).clearWake();
    }
}
