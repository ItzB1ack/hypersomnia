package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * First-person viewmodel укола: пока игрок держит адреналиновый шприц, ванильные
 * руки заменяются двумя руками и шприцем. Рука со шприцем подносит иглу к
 * предплечью второй руки, затем игла входит, а поршень давит (кадры модели).
 *
 * Ванильная рука в first person ставится через renderPlayerArm; здесь берётся
 * та же база (swing = 0), после чего обе руки доворачиваются к центру экрана.
 * Все числа вынесены в константы и подбирались расчётом позы, а не в игре:
 * точную посадку нужно один раз посмотреть глазами.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class SyringeViewModel {
    private SyringeViewModel() {}

    /** Доворот руки-цели (предплечье уходит к центру и становится поперёк экрана). */
    private static final float TARGET_SWING = 80.0F;
    /** Доворот руки со шприцем: меньше, поэтому кисть остаётся выше цели. */
    private static final float INJECTOR_SWING = 52.0F;
    /** Наклон обеих рук к центру (поднимает кисти в кадр). */
    private static final float ARM_TILT = -20.0F;
    /** Подъём рук в пространстве камеры. */
    private static final float ARM_LIFT = 0.48F;
    /** Масштаб 3D-модели шприца в руке (длина модели 1 блок). */
    private static final float SYRINGE_SCALE = 0.62F;
    /** Насколько игла входит в предплечье в момент вкола. */
    private static final float INJECT_DEPTH = 0.03F;
    /** Доля предплечья от кисти к плечу, куда должна попасть игла. */
    private static final float TARGET_ALONG_ARM = 0.30F;
    /** Куда смотрит шприц, пока руки ещё не сведены. */
    private static final Vector3f NEUTRAL_AIM = new Vector3f(0.10F, 0.18F, -0.98F).normalize();
    private static final Vector3f NEEDLE_AXIS = new Vector3f(1.0F, 0.0F, 0.0F);

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isUsingItem()) return;

        ItemStack useItem = player.getUseItem();
        if (!useItem.is(ModItems.ADRENALINE_SYRINGE.get())) return;

        // Событие приходит на обе руки. Рисуем всё один раз — на руке с шприцем.
        if (event.getHand() != player.getUsedItemHand()) {
            event.setCanceled(true);
            return;
        }
        event.setCanceled(true);

        int duration = useItem.getUseDuration(player);
        if (duration <= 0) return;

        float partial = event.getPartialTick();
        float remaining = (float) player.getUseItemRemainingTicks() - partial;
        float progress = Mth.clamp(1.0F - remaining / (float) duration, 0.0F, 1.0F);

        renderInjection(player, event, useItem, progress);
    }

    private static void renderInjection(LocalPlayer player, RenderHandEvent event,
                                        ItemStack stack, float progress) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;

        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();
        int light = event.getPackedLight();
        float equip = event.getEquipProgress();

        boolean injectorIsRight = player.getUsedItemHand() == InteractionHand.MAIN_HAND;
        HumanoidArm injector = injectorIsRight ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
        HumanoidArm target = injectorIsRight ? HumanoidArm.LEFT : HumanoidArm.RIGHT;

        // 0..1: руки поднимаются из обычного положения в позу укола.
        float raise = smooth(Mth.clamp(progress / 0.40F, 0.0F, 1.0F));
        // 0..1: игла наводится на предплечье, когда руки уже почти сведены.
        float aim = smooth(Mth.clamp((progress - 0.30F) / 0.15F, 0.0F, 1.0F));
        // 0..1: короткий вкол после наведения.
        float inject = smooth(Mth.clamp((progress - 0.50F) / 0.15F, 0.0F, 1.0F));

        float tilt = ARM_TILT * raise;
        float lift = ARM_LIFT * raise;

        // Корень PoseStack — обратная матрица проекции (ванильный приём для рук).
        // Для расчёта точек её нужно убрать: руки живут в видовом пространстве,
        // а не в этом проективном корне, иначе точка уезжает от кисти.
        Matrix4f rootInverse = new Matrix4f(pose.last().pose()).invert();
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();

        // ---- рука-цель ----
        pose.pushPose();
        applyArmPose(pose, target, equip, lift);
        pose.mulPose(Axis.ZP.rotationDegrees(sign(target) * TARGET_SWING * raise));
        pose.mulPose(Axis.XP.rotationDegrees(tilt));
        if (!player.isInvisible()) {
            if (target == HumanoidArm.RIGHT) {
                renderer.renderRightHand(pose, buffer, light, player);
            } else {
                renderer.renderLeftHand(pose, buffer, light, player);
            }
        }
        Matrix4f targetHand = new Matrix4f(viewMatrix(pose, rootInverse))
                .mul(partMatrix(partOf(model, target)));
        Vector3f targetView = targetHand.transformPosition(new Vector3f(
                handX(target), (10.0F - 12.0F * TARGET_ALONG_ARM) / 16.0F, 0.0F));
        pose.popPose();

        // ---- рука со шприцем: шприц продолжает её позу, поэтому всегда в кисти ----
        pose.pushPose();
        applyArmPose(pose, injector, equip, lift);
        pose.mulPose(Axis.ZP.rotationDegrees(sign(injector) * INJECTOR_SWING * raise));
        pose.mulPose(Axis.XP.rotationDegrees(tilt));
        if (!player.isInvisible()) {
            if (injector == HumanoidArm.RIGHT) {
                renderer.renderRightHand(pose, buffer, light, player);
            } else {
                renderer.renderLeftHand(pose, buffer, light, player);
            }
        }

        Matrix4f injectorHand = new Matrix4f(viewMatrix(pose, rootInverse))
                .mul(partMatrix(partOf(model, injector)));
        Matrix4f toHand = new Matrix4f(injectorHand).invert();
        Vector3f grip = new Vector3f(handX(injector), 10.0F / 16.0F, 0.0F);
        Vector3f targetInHand = toHand.transformPosition(new Vector3f(targetView));
        Vector3f aimDir = new Vector3f(targetInHand).sub(grip);
        if (aimDir.lengthSquared() < 1.0E-6F) {
            aimDir.set(NEUTRAL_AIM);
        } else {
            aimDir.normalize();
        }
        Vector3f neutral = toHand.transformDirection(new Vector3f(NEUTRAL_AIM)).normalize();
        aimDir.lerp(neutral, 1.0F - aim).normalize();

        // Смещение вдоль иглы: на старте кисть держит середину шприца, к моменту
        // укола игла дотягивается ровно до предплечья (минус половина модели).
        float reach = Math.max(0.0F, grip.distance(targetInHand) - 0.5F * SYRINGE_SCALE);

        ModelPart injectorPart = partOf(model, injector);
        pose.translate(injectorPart.x / 16.0F, injectorPart.y / 16.0F, injectorPart.z / 16.0F);
        pose.mulPose(new Quaternionf().rotationZYX(
                injectorPart.zRot, injectorPart.yRot, injectorPart.xRot));
        pose.translate(grip.x, grip.y, grip.z);
        pose.mulPose(new Quaternionf().rotationTo(NEEDLE_AXIS, aimDir));
        pose.translate(reach * aim + INJECT_DEPTH * inject, 0.0F, 0.0F);
        pose.scale(SYRINGE_SCALE, SYRINGE_SCALE, SYRINGE_SCALE);

        mc.getItemRenderer().renderStatic(
                player, stack, ItemDisplayContext.NONE, false, pose, buffer,
                mc.level, light, OverlayTexture.NO_OVERLAY, 0);

        pose.popPose();
    }

    /**
     * Копия ванильного renderPlayerArm (swing = 0) плюс подъём в пространстве
     * камеры: ставит руку в обычную first-person позицию.
     */
    private static void applyArmPose(PoseStack pose, HumanoidArm side, float equip, float lift) {
        float f = side == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        pose.translate(0.0F, lift, 0.0F);
        pose.translate(f * 0.64000005F, -0.6F + equip * -0.6F, -0.71999997F);
        pose.mulPose(Axis.YP.rotationDegrees(f * 45.0F));
        pose.translate(f * -1.0F, 3.6F, 3.5F);
        pose.mulPose(Axis.ZP.rotationDegrees(f * 120.0F));
        pose.mulPose(Axis.XP.rotationDegrees(200.0F));
        pose.mulPose(Axis.YP.rotationDegrees(f * -135.0F));
        pose.translate(f * 5.6F, 0.0F, 0.0F);
    }

    /** Часть руки в модели игрока. */
    private static ModelPart partOf(PlayerModel<AbstractClientPlayer> model, HumanoidArm arm) {
        return arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
    }

    /**
     * Собственная трансформация части руки: смещение плеча и поворот из
     * setupAnim. Именно её применяет ModelPart.translateAndRotate.
     */
    private static Matrix4f partMatrix(ModelPart part) {
        Matrix4f matrix = new Matrix4f();
        matrix.translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F);
        matrix.rotate(new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot));
        return matrix;
    }

    /** Середина кисти в системе координат части: правая смещена в -X, левая в +X. */
    private static float handX(HumanoidArm arm) {
        return arm == HumanoidArm.RIGHT ? -1.0F / 16.0F : 1.0F / 16.0F;
    }

    /** Матрица руки в видовом пространстве: убирает проективный корень PoseStack. */
    private static Matrix4f viewMatrix(PoseStack pose, Matrix4f rootInverse) {
        return new Matrix4f(rootInverse).mul(pose.last().pose());
    }

    /** Знак поворота к центру экрана: правая рука идёт в +, левая в -. */
    private static float sign(HumanoidArm arm) {
        return arm == HumanoidArm.RIGHT ? -1.0F : 1.0F;
    }

    private static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }
}
