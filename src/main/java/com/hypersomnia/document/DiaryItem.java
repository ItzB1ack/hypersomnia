package com.hypersomnia.document;

import com.hypersomnia.client.DiaryOpenAnim;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Дневник: обычный ПКМ раскрывает книгу в руке почти на 180 градусов
 * (кадры hypersomnia:using), после чего на клиенте открывается GUI.
 * Держать кнопку не нужно.
 */
public class DiaryItem extends Item {
    public DiaryItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            DiaryOpenAnim.start();
        }
        return InteractionResultHolder.consume(stack);
    }
}
