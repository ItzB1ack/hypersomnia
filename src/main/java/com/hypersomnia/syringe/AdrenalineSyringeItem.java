package com.hypersomnia.syringe;

import com.hypersomnia.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Адреналиновый шприц: удержание ПКМ ~1 с, затем укол. Пока игрок держит
 * предмет, модель меняет кадры (hypersomnia:using), рука делает движение
 * ввода; при отпускании раньше времени предмет не тратится.
 */
public class AdrenalineSyringeItem extends Item {
    public AdrenalineSyringeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return SyringeTuning.APPLY_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!level.isClientSide) return;
        if (remaining == SyringeTuning.APPLY_TICKS) {
            level.playLocalSound(entity.getX(), entity.getY() + 1.2, entity.getZ(),
                    ModSounds.BREATH_EXHALE.get(), SoundSource.PLAYERS, 0.55f, 1.25f, false);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer sp) {
            if (SyringeHandler.inject(sp)) {
                sp.getCooldowns().addCooldown(this, SyringeTuning.USE_COOLDOWN);
                if (!sp.getAbilities().instabuild) stack.shrink(1);
            } else {
                sp.displayClientMessage(Component.translatable("hypersomnia.syringe.refused"), true);
            }
        }
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeCharged) {
        // отпустил раньше — укол не состоялся, предмет не тратится
    }
}
