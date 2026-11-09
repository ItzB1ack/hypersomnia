package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.lab.CapsuleBlock;
import com.hypersomnia.lab.UprightCapsuleBlock;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.OptionalDouble;

/**
 * Обводка капсул (лежачей и стоячей) поверх стекла.
 *
 * Вся модель капсулы объявлена как {@code render_type: translucent}, поэтому в режиме «Плавно» (Fabulous)
 * ванильная обводка блока рисуется до стекла и затем просвечивает сквозь него: линия кажется полупрозрачной
 * и видна насквозь. Здесь ванильная обводка для капсул отменяется, а своя рисуется в конце прохода
 * полупрозрачных блоков — в тот же буфер и с той же проверкой глубины, что и стекло. Так линия остаётся
 * чёткой на видимых гранях и полностью скрывается за корпусом и стеклом, а не просвечивает сквозь блок.
 *
 * Для остальных блоков поведение не меняется: {@link RenderHighlightEvent.Block} не отменяется.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class CapsuleOutline {
    private CapsuleOutline() {}

    /**
     * Как {@link RenderType#lines()}, но без {@code ITEM_ENTITY_TARGET}: вывод идёт в тот буфер, который
     * сейчас привязан (в Fabulous это translucent target со стеклом и его глубиной). Формат, прозрачность,
     * запись глубины и отсутствие отсечения повторяют ванильные линии обводки.
     */
    private static final RenderType LINES = RenderType.create(
            "hypersomnia:capsule_outline",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            1536,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeLinesShader))
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /** Капсула, для которой ванильная обводка отменена в этом кадре. */
    private static BlockPos pending;

    @SubscribeEvent
    public static void onHighlight(RenderHighlightEvent.Block event) {
        pending = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockPos pos = event.getTarget().getBlockPos();
        if (!isCapsule(mc.level.getBlockState(pos))) return;
        pending = pos;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        BlockPos pos = pending;
        pending = null;
        if (pos == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockState state = mc.level.getBlockState(pos);
        if (!isCapsule(state)) return;

        Vec3 cam = event.getCamera().getPosition();
        Entity entity = event.getCamera().getEntity();
        CollisionContext ctx = entity != null ? CollisionContext.of(entity) : CollisionContext.empty();
        VoxelShape shape = state.getShape(mc.level, pos, ctx);

        LINES.setupRenderState();
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        renderShape(buffer, shape, pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z,
                0.0F, 0.0F, 0.0F, 0.4F);
        MeshData mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        LINES.clearRenderState();
    }

    private static boolean isCapsule(BlockState state) {
        return state.getBlock() instanceof CapsuleBlock || state.getBlock() instanceof UprightCapsuleBlock;
    }

    /** Копия ванильного {@code LevelRenderer.renderShape}: равномерный цвет по всем рёбрам формы. */
    private static void renderShape(VertexConsumer consumer, VoxelShape shape,
                                    double x, double y, double z,
                                    float red, float green, float blue, float alpha) {
        PoseStack.Pose pose = new PoseStack().last();
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float dx = (float) (x2 - x1);
            float dy = (float) (y2 - y1);
            float dz = (float) (z2 - z1);
            float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= len;
            dy /= len;
            dz /= len;
            consumer.addVertex(pose, (float) (x1 + x), (float) (y1 + y), (float) (z1 + z))
                    .setColor(red, green, blue, alpha)
                    .setNormal(pose, dx, dy, dz);
            consumer.addVertex(pose, (float) (x2 + x), (float) (y2 + y), (float) (z2 + z))
                    .setColor(red, green, blue, alpha)
                    .setNormal(pose, dx, dy, dz);
        });
    }
}
