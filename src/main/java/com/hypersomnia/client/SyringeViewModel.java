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
import net.minecraft.world.entity.LivingEntity;
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
 *
 * Плавность: фазы идут внахлёст и на кривых smoother-step (нулевая скорость и
 * ускорение на краях), поршень двигается по прогрессу с долей тика, а после
 * укола или раннего отпускания руки не пропадают рывком, а отводят иглу и
 * уходят (RETRACT_MS).
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

    // ---- фазы укола (доля прогресса 0..1; соседние фазы перекрываются) ----
    private static final float RAISE_END = 0.40F;
    private static final float AIM_START = 0.22F, AIM_LEN = 0.30F;
    private static final float INJECT_START = 0.50F, INJECT_LEN = 0.18F;
    private static final float PLUNGE_START = 0.66F, PLUNGE_LEN = 0.30F;

    // ---- уход рук (доля времени RETRACT_NS 0..1) ----
    /** Длительность ухода после укола или раннего отпускания (нс). */
    private static final long RETRACT_NS = 420_000_000L;
    /** С этой доли времени решается, что делают руки: уходят вниз (шприц потрачен) или возвращаются в покой. */
    private static final float RETRACT_DECIDE = 0.35F;
    /** Насколько руки опускаются вниз при уходе после укола (пространство камеры). */
    private static final float DROP_DISTANCE = 1.1F;
    /** Прогресс, с которого отпускание считается законченным уколом, а не отменой. */
    private static final float FINISHED_PROGRESS = 0.93F;
    /** Если кадры рук не приходили дольше этого времени (открыт экран и т. п.), уход не показываем. */
    private static final long STALE_NS = 150_000_000L;

    // ---- состояние (только поток рендера клиента) ----
    private static boolean wasUsing;
    private static float lastProgress;
    private static long lastUseNanos;
    private static InteractionHand lastHand = InteractionHand.MAIN_HAND;
    private static ItemStack lastStack = ItemStack.EMPTY;
    private static long retractStart = -1L;
    private static boolean retractDecided;
    private static boolean retractDrops;

    /** Положение поршня 0..1 для кадра модели, пока этот класс сам рисует шприц. */
    private static float viewPlunger;
    private static boolean inView;

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        long now = System.nanoTime();
        ItemStack useItem = player.isUsingItem() ? player.getUseItem() : ItemStack.EMPTY;
        boolean using = useItem.is(ModItems.ADRENALINE_SYRINGE.get());

        if (using) {
            // Событие приходит на обе руки. Рисуем всё один раз — на руке с шприцем.
            event.setCanceled(true);
            retractStart = -1L;
            if (event.getHand() != player.getUsedItemHand()) return;

            int duration = useItem.getUseDuration(player);
            if (duration <= 0) return;

            float remaining = (float) player.getUseItemRemainingTicks() - event.getPartialTick();
            float progress = Mth.clamp(1.0F - remaining / (float) duration, 0.0F, 1.0F);

            wasUsing = true;
            lastProgress = progress;
            lastUseNanos = now;
            lastHand = player.getUsedItemHand();
            lastStack = useItem.copy();

            renderInjection(player, event, useItem, raiseOf(progress), aimOf(progress),
                    injectOf(progress), plungerOf(progress), 0.0F);
            return;
        }

        if (wasUsing) {
            wasUsing = false;
            boolean fresh = now - lastUseNanos <= STALE_NS;
            if (fresh && player.isAlive() && !player.isSpectator()) {
                retractStart = now;
                retractDecided = false;
                retractDrops = false;
            }
        }

        if (retractStart < 0L) return;
        // Игрок начал другое действие (еда, лук): дальше ведёт ваниль.
        if (player.isUsingItem()) {
            retractStart = -1L;
            return;
        }
        float t = (float) (now - retractStart) / (float) RETRACT_NS;
        if (t >= 1.0F) {
            retractStart = -1L;
            return;
        }

        event.setCanceled(true);
        if (event.getHand() != lastHand) return;

        float p0 = lastProgress;
        boolean finished = p0 >= FINISHED_PROGRESS;
        if (!retractDecided && t >= RETRACT_DECIDE) {
            retractDecided = true;
            // Шприц израсходован — руки уходят вниз вместе с ним. Остался в руке
            // (творческий режим, отказ сервера, стопка) — руки возвращаются в покой.
            retractDrops = finished
                    && !player.getItemInHand(lastHand).is(ModItems.ADRENALINE_SYRINGE.get());
        }

        float inject = injectOf(p0) * (1.0F - smooth(seg(t, 0.00F, 0.35F)));
        float aim = aimOf(p0) * (1.0F - smooth(seg(t, 0.10F, 0.60F)));
        float tail = smooth(seg(t, RETRACT_DECIDE, 1.0F));
        float raise = raiseOf(p0);
        float plunger = plungerOf(p0);
        float drop = 0.0F;
        if (retractDrops) {
            drop = tail;
        } else {
            raise *= 1.0F - tail;
            plunger *= 1.0F - tail;
        }

        renderInjection(player, event, lastStack, raise, aim, inject, plunger, drop);
    }

    /**
     * Значение предметного свойства hypersomnia:using шприца: положение поршня 0..1.
     * В первом лице его задаёт сама вьюмодель (по субтикам), в остальных случаях — тики.
     */
    public static float modelProgress(LivingEntity entity, ItemStack stack) {
        if (inView) return viewPlunger;
        if (entity == null || !entity.isUsingItem()) return 0.0F;
        ItemStack used = entity.getUseItem();
        if (!ItemStack.isSameItemSameComponents(used, stack)) return 0.0F;
        int duration = stack.getUseDuration(entity);
        if (duration <= 0) return 1.0F;
        return plungerOf(1.0F - (float) entity.getUseItemRemainingTicks() / (float) duration);
    }

    // ---- кривые фаз ----

    private static float raiseOf(float p) {
        return smoother(seg(p, 0.0F, RAISE_END));
    }

    private static float aimOf(float p) {
        return smoother(seg(p, AIM_START, AIM_START + AIM_LEN));
    }

    private static float injectOf(float p) {
        return smooth(seg(p, INJECT_START, INJECT_START + INJECT_LEN));
    }

    private static float plungerOf(float p) {
        return smooth(seg(p, PLUNGE_START, PLUNGE_START + PLUNGE_LEN));
    }

    private static void renderInjection(LocalPlayer player, RenderHandEvent event, ItemStack stack,
                                        float raise, float aim, float inject, float plunger, float drop) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;
        if (stack.isEmpty()) return;

        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();
        int light = event.getPackedLight();
        float equip = event.getEquipProgress();

        boolean injectorIsRight = lastHand == InteractionHand.MAIN_HAND;
        HumanoidArm injector = injectorIsRight ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
        HumanoidArm target = injectorIsRight ? HumanoidArm.LEFT : HumanoidArm.RIGHT;

        float tilt = ARM_TILT * raise;
        float lift = ARM_LIFT * raise - DROP_DISTANCE * drop;

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

        // Кадр модели выбирается по свойству hypersomnia:using: отдаём ему
        // положение поршня этого кадра, а не округлённое до тика.
        viewPlunger = plunger;
        inView = true;
        try {
            mc.getItemRenderer().renderStatic(
                    player, stack, ItemDisplayContext.NONE, false, pose, buffer,
                    mc.level, light, OverlayTexture.NO_OVERLAY, 0);
        } finally {
            inView = false;
        }

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

    /** Доля отрезка [a, b], в которой сейчас t, зажатая в 0..1. */
    private static float seg(float t, float a, float b) {
        return Mth.clamp((t - a) / (b - a), 0.0F, 1.0F);
    }

    private static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    /** Smoother-step: нулевые скорость и ускорение на краях, движение начинается и кончается мягче. */
    private static float smoother(float t) {
        return t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
    }
}
