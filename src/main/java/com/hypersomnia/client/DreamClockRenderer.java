package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.dream.DreamClockBlock;
import com.hypersomnia.dream.DreamClockBlockEntity;
import com.hypersomnia.dream.DreamClockTuning;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * [новое] Анимация напольных часов сна: маятник в нижней половине и стрелки в верхней.
 *
 * <p>Сами детали вынесены из статических моделей в отдельные: их приходится поворачивать
 * каждый кадр, а элементы block-модели запекаются неподвижными. Модели регистрирует
 * {@link DreamClockClientSetup} через {@code ModelEvent.RegisterAdditional}.
 *
 * <p>Время — {@code level.getGameTime()} + partialTick, то есть то же, по которому сервер
 * щёлкает: маятник замирает в крайних точках на каждой секунде, минутная стрелка идёт
 * ускоренно (оборот за {@link DreamClockTuning#MINUTE_HAND_TICKS}).
 */
public final class DreamClockRenderer implements BlockEntityRenderer<DreamClockBlockEntity> {
    static final ModelResourceLocation PENDULUM = additional("block/dream_clock_pendulum");
    static final ModelResourceLocation HAND_MINUTE = additional("block/dream_clock_hand_minute");
    static final ModelResourceLocation HAND_HOUR = additional("block/dream_clock_hand_hour");

    /** Ось маятника (верх стержня) и ось стрелок (центр циферблата), в долях блока. */
    private static final float PENDULUM_PIVOT_Y = 15.8f / 16f;
    private static final float HAND_PIVOT_X = 8f / 16f;
    private static final float HAND_PIVOT_Y = 7.5f / 16f;

    private final RandomSource random = RandomSource.create();

    public DreamClockRenderer(BlockEntityRendererProvider.Context ctx) {}

    private static ModelResourceLocation additional(String path) {
        return ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, path));
    }

    @Override
    public void render(DreamClockBlockEntity clock, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        BlockState state = clock.getBlockState();
        if (!state.hasProperty(DreamClockBlock.HALF) || clock.getLevel() == null) return;
        boolean upper = state.getValue(DreamClockBlock.HALF) == DoubleBlockHalf.UPPER;
        double time = clock.getLevel().getGameTime() + partialTick;

        pose.pushPose();
        // Та же ориентация, что у варианта blockstate: детали нарисованы лицом на север.
        pose.translate(0.5f, 0.0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - state.getValue(DreamClockBlock.FACING).toYRot()));
        pose.translate(-0.5f, 0.0f, -0.5f);

        if (upper) {
            double minute = time % DreamClockTuning.MINUTE_HAND_TICKS / DreamClockTuning.MINUTE_HAND_TICKS * 360.0;
            double hour = time % DreamClockTuning.HOUR_HAND_TICKS / DreamClockTuning.HOUR_HAND_TICKS * 360.0;
            draw(HAND_HOUR, pose, buffers, light, overlay, hour);
            draw(HAND_MINUTE, pose, buffers, light, overlay, minute);
        } else {
            // Период 2 с: крайние точки на каждой целой секунде, как щелчок механизма.
            double phase = Math.cos(Math.PI * time / (DreamClockTuning.PENDULUM_PERIOD_TICKS / 2.0));
            draw(PENDULUM, pose, buffers, light, overlay, phase * DreamClockTuning.PENDULUM_SWING_DEGREES);
        }
        pose.popPose();
    }

    /** Отрисовать деталь, повернув её вокруг оси циферблата/маятника на {@code degrees}. */
    private void draw(ModelResourceLocation id, PoseStack pose, MultiBufferSource buffers,
                      int light, int overlay, double degrees) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(id);
        if (model == Minecraft.getInstance().getModelManager().getMissingModel()) return;

        float pivotX = id.equals(PENDULUM) ? 0.5f : HAND_PIVOT_X;
        float pivotY = id.equals(PENDULUM) ? PENDULUM_PIVOT_Y : HAND_PIVOT_Y;
        pose.pushPose();
        pose.translate(pivotX, pivotY, 0.0f);
        pose.mulPose(Axis.ZP.rotationDegrees((float) degrees));
        pose.translate(-pivotX, -pivotY, 0.0f);

        VertexConsumer out = buffers.getBuffer(RenderType.cutout());
        for (var quad : model.getQuads(null, null, random)) {
            out.putBulkData(pose.last(), quad, 1.0f, 1.0f, 1.0f, 1.0f, light, overlay);
        }
        pose.popPose();
    }
}
