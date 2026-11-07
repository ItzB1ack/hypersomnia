package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * First-person дневник во время раскрытия и закрытия (DiaryOpenAnim).
 *
 * Кадры обложки в модели идут шагом 22.5 градуса (BlockElement не принимает
 * других углов), поэтому одной моделью плавного раскрытия не получить. Здесь
 * книга целиком плавно плывёт к центру экрана, вверх и к камере по той же
 * кривой, и ступени обложки читаются как часть одного движения, а не как
 * смена картинок. Ванильный замах от ПКМ подавляется: книга раскрывается, а не
 * «бьёт».
 *
 * Базовая посадка предмета в руке — копия ванильного applyItemArmTransform.
 * Сдвиги GLIDE_* подобраны расчётом осей, а не в игре: посадку нужно один раз
 * посмотреть глазами.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class DiaryViewModel {
    private DiaryViewModel() {}

    /** Сдвиг книги к центру экрана (в сторону от руки), блоки пространства камеры. */
    private static final float GLIDE_X = 0.22F;
    /** Подъём книги. */
    private static final float GLIDE_Y = 0.12F;
    /** Приближение книги к камере. */
    private static final float GLIDE_Z = 0.14F;

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (!DiaryOpenAnim.active()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        InteractionHand hand = event.getHand();
        if (hand != DiaryOpenAnim.hand()) return;
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(ModItems.DIARY.get())) return;

        event.setCanceled(true);

        HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean right = arm == HumanoidArm.RIGHT;
        float side = right ? 1.0F : -1.0F;
        float e = DiaryOpenAnim.progress();

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        // Ванильная посадка предмета в руке (без замаха).
        pose.translate(side * 0.56F, -0.52F + event.getEquipProgress() * -0.6F, -0.72F);
        // Плавный сдвиг книги по ходу раскрытия.
        pose.translate(-side * GLIDE_X * e, GLIDE_Y * e, GLIDE_Z * e);

        ItemDisplayContext context = right
                ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        mc.getItemRenderer().renderStatic(
                player, stack, context, !right, pose, event.getMultiBufferSource(),
                mc.level, event.getPackedLight(), OverlayTexture.NO_OVERLAY,
                player.getId() + context.ordinal());
        pose.popPose();
    }
}
