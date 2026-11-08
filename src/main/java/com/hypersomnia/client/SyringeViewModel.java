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
 * руки заменяются одной рукой со шприцем. Рука поднимает шприц, разворачивает
 * иглу вниз к собственному торсу (он ниже края кадра) и вкалывает; поршень давит
 * (кадры модели). Второй руки нет, поэтому её рисовать не нужно.
 *
 * Ванильная рука в first person ставится через renderPlayerArm; здесь берётся
 * та же база (swing = 0). Положение кисти задаётся целевой точкой в видовом
 * пространстве: сдвиг руки считается как «цель минус кисть при нулевом сдвиге».
 * Числа подбирались расчётом позы, а не в игре: посадку нужно один раз
 * посмотреть глазами (все X заданы для правой руки и зеркалятся).
 *
 * Плавность: фазы идут внахлёст и на кривых smoother-step, поршень двигается по
 * прогрессу с долей тика, а после укола или раннего отпускания рука не
 * пропадает рывком, а отводит иглу и уходит (RETRACT_MS).
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class SyringeViewModel {
    private SyringeViewModel() {}

    /** Масштаб 3D-модели шприца в руке (длина модели 1 блок). */
    private static final float SCALE = 0.5F;
    /**
     * Сдвиг шприца вдоль иглы (блоки): кисть держит заднюю треть корпуса у фланца,
     * а не центр, поэтому игла выходит из кулака вперёд, а поршень остаётся под большим пальцем.
     */
    private static final float GRIP_SHIFT = 0.17F;
    /** Насколько кисть с шприцем идёт вдоль иглы при проколе (блоки). */
    private static final float INJECT_TRAVEL = 0.10F;

    /** Поза «готов»: кисть поднята правее центра, игла смотрит вверх-вперёд. */
    private static final Vector3f READY_GRIP = new Vector3f(0.30F, -0.02F, -0.85F);
    private static final float READY_SWING = 20.0F, READY_TILT = -15.0F;
    /** Поза «к торсу»: кисть внизу у центра, игла вниз и к телу. */
    private static final Vector3f TORSO_GRIP = new Vector3f(0.14F, -0.20F, -0.66F);
    private static final float TORSO_SWING = 60.0F, TORSO_TILT = -30.0F;

    // ---- фазы укола (доля прогресса 0..1; соседние фазы перекрываются) ----
    private static final float RAISE_END = 0.30F;
    private static final float AIM_START = 0.14F, AIM_LEN = 0.24F;
    private static final float INJECT_START = 0.40F, INJECT_LEN = 0.10F;
    private static final float PLUNGE_START = 0.52F, PLUNGE_LEN = 0.42F;

    /** Насколько кисть отводит шприц назад перед уколом (замах), блоки. */
    private static final float WINDUP = 0.09F;
    /** Дрожь кисти при давлении на поршень (блоки). */
    private static final float TREMBLE = 0.006F;

    // ---- уход рук (доля времени RETRACT_NS 0..1) ----
    /** Длительность ухода после укола или раннего отпускания (нс). */
    private static final long RETRACT_NS = 320_000_000L;
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
        // Резкий выпад с небольшим перелётом (easeOutBack): игла бьёт и чуть отскакивает.
        float t = seg(p, INJECT_START, INJECT_START + INJECT_LEN) - 1.0F;
        return 1.0F + 2.4F * t * t * t + 1.4F * t * t;
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

        HumanoidArm arm = lastHand == InteractionHand.MAIN_HAND
                ? player.getMainArm() : player.getMainArm().getOpposite();
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();

        // ---- поза: покой -> готов (raise) -> к торсу (aim) ----
        float swing = lerp(lerp(0.0F, READY_SWING, raise), TORSO_SWING, aim);
        float tilt = lerp(lerp(0.0F, READY_TILT, raise), TORSO_TILT, aim);

        // Шприц зажат в кулаке поперёк кисти: игла смотрит вдоль оси X части руки
        // (для левой руки зеркально). Куда она указывает, определяет только поза руки:
        // от «вперёд-вниз» в покое до «вниз к торсу» при взмахе.
        Vector3f needleView = handChain(model, arm, equip, swing, tilt)
                .transformDirection(new Vector3f(side, 0.0F, 0.0F)).normalize();

        Vector3f restGrip = gripAtZeroOffset(model, arm, equip, 0.0F, 0.0F);
        Vector3f goal = new Vector3f(restGrip)
                .lerp(mirror(READY_GRIP, side), raise)
                .lerp(mirror(TORSO_GRIP, side), aim);
        // Замах назад, затем резкий прокол вдоль иглы (кисть идёт вместе со шприцем).
        float windup = aim * (1.0F - Mth.clamp(inject * 3.0F, 0.0F, 1.0F));
        goal.fma(INJECT_TRAVEL * inject - WINDUP * windup, needleView);
        // Дрожь кисти, пока давит поршень.
        if (plunger > 0.0F && plunger < 1.0F) {
            double t = System.nanoTime() / 1.0E9;
            goal.x += (float) Math.sin(t * 61.0) * TREMBLE;
            goal.y += (float) Math.sin(t * 47.0 + 1.1) * TREMBLE;
        }
        Vector3f offset = goal.sub(gripAtZeroOffset(model, arm, equip, swing, tilt));
        offset.y -= DROP_DISTANCE * drop;

        pose.pushPose();
        applyArmPose(pose, arm, equip, offset);
        pose.mulPose(Axis.ZP.rotationDegrees(sign(arm) * swing));
        pose.mulPose(Axis.XP.rotationDegrees(tilt));
        if (!player.isInvisible()) {
            if (arm == HumanoidArm.RIGHT) {
                renderer.renderRightHand(pose, buffer, light, player);
            } else {
                renderer.renderLeftHand(pose, buffer, light, player);
            }
        }

        // Шприц продолжает позу руки, поэтому всегда в кисти.
        ModelPart part = partOf(model, arm);
        pose.translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F);
        pose.mulPose(new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot));
        pose.translate(handX(arm), 10.0F / 16.0F, 0.0F);
        // Модель иглой по +X; у левой руки ось кулака зеркальна.
        if (arm == HumanoidArm.LEFT) pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.translate(GRIP_SHIFT, 0.0F, 0.0F);
        pose.scale(SCALE, SCALE, SCALE);

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

    /** Матрица части руки в видовом пространстве при нулевом сдвиге (без поворота замаха). */
    private static Matrix4f handChain(PlayerModel<AbstractClientPlayer> model, HumanoidArm arm,
                                      float equip, float swing, float tilt) {
        float f = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        Matrix4f m = new Matrix4f();
        m.translate(f * 0.64000005F, -0.6F + equip * -0.6F, -0.71999997F);
        m.rotateY((float) Math.toRadians(f * 45.0F));
        m.translate(f * -1.0F, 3.6F, 3.5F);
        m.rotateZ((float) Math.toRadians(f * 120.0F));
        m.rotateX((float) Math.toRadians(200.0F));
        m.rotateY((float) Math.toRadians(f * -135.0F));
        m.translate(f * 5.6F, 0.0F, 0.0F);
        m.rotateZ((float) Math.toRadians(sign(arm) * swing));
        m.rotateX((float) Math.toRadians(tilt));
        // Только смещение плеча: поворот части руки в этот момент ещё от замаха
        // третьего лица (renderRightHand обнуляет его позже), и он сдвигал бы кисть.
        ModelPart part = partOf(model, arm);
        m.translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F);
        return m;
    }

    /** Положение кисти в видовом пространстве при нулевом сдвиге руки. */
    private static Vector3f gripAtZeroOffset(PlayerModel<AbstractClientPlayer> model, HumanoidArm arm,
                                             float equip, float swing, float tilt) {
        return handChain(model, arm, equip, swing, tilt)
                .transformPosition(new Vector3f(handX(arm), 10.0F / 16.0F, 0.0F));
    }

    private static Vector3f mirror(Vector3f v, float side) {
        return new Vector3f(v.x * side, v.y, v.z);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Копия ванильного renderPlayerArm (swing = 0) плюс подъём в пространстве
     * камеры: ставит руку в обычную first-person позицию.
     */
    private static void applyArmPose(PoseStack pose, HumanoidArm side, float equip, Vector3f offset) {
        float f = side == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        pose.translate(offset.x, offset.y, offset.z);
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
