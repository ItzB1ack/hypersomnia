package com.hypersomnia.document;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Дневник: открывает лист с семью слотами воспоминаний (экран только на клиенте). */
public class DiaryItem extends Item {
    public DiaryItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) com.hypersomnia.client.DiaryScreen.open();
        return InteractionResultHolder.success(stack);
    }
}