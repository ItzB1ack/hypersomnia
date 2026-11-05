package com.hypersomnia.syringe;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Адреналиновый шприц: мгновенный укол по правому клику. */
public class AdrenalineSyringeItem extends Item {
    public AdrenalineSyringeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);

        if (player instanceof ServerPlayer sp && SyringeHandler.inject(sp)) {
            player.getCooldowns().addCooldown(this, SyringeTuning.USE_COOLDOWN);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResultHolder.consume(stack);
        }
        player.displayClientMessage(Component.translatable("hypersomnia.syringe.refused"), true);
        return InteractionResultHolder.fail(stack);
    }
}
