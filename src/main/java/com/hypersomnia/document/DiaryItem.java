package com.hypersomnia.document;

import com.hypersomnia.client.DiaryScreen;
import com.hypersomnia.diary.DiaryTuning;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Дневник: ПКМ удерживается короткое время. Пока предмет в руке, 3D-модель
 * приподнимает обложку (hypersomnia:using), затем на клиенте открывается GUI.
 * Если отпустить раньше — книга остаётся закрытой.
 */
public class DiaryItem extends Item {
    public DiaryItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return DiaryTuning.OPEN_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!level.isClientSide) return;
        // Небольшой запас на пинг: клиент может не досчитать до 1 тика.
        if (remaining <= 3) {
            DiaryScreen.openIfClosed();
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        // Эффект полностью клиентский: GUI открывается в onUseTick.
        return stack;
    }
}
