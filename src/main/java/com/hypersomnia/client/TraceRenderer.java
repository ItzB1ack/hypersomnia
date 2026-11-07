package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.trace.TraceData;
import com.hypersomnia.trace.TraceTuning;
import com.hypersomnia.trace.TraceType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Рисует следы осколка: плоские «наклейки» на полу и стенах и предметы. В мир ничего не добавляется:
 * следы видит только этот игрок, чанки и блоки не трогаются. Только Верхний мир.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class TraceRenderer {
    private TraceRenderer() {}

    private static final Map<TraceType, ResourceLocation[]> TEX = new EnumMap<>(TraceType.class);
    private static final Map<TraceType, ItemStack> STACKS = new EnumMap<>(TraceType.class);

    private static ResourceLocation texture(TraceType t, int variant) {
        ResourceLocation[] arr = TEX.computeIfAbsent(t, k -> {
            ResourceLocation[] a = new ResourceLocation[k.variants];
            for (int i = 0; i < a.length; i++) {
                a[i] = ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "textures/misc/trace_" + k.asset + "_" + i + ".png");
            }
            return a;
        });
        return arr[Math.floorMod(variant, arr.length)];
    }

    private static ItemStack stack(TraceType t) {
        return STACKS.computeIfAbsent(t, k -> new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(k.asset))));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        List<TraceData.Entry> list = TraceClientState.entries;
        if (list.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD) return;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        double maxSq = TraceTuning.RENDER_DISTANCE * TraceTuning.RENDER_DISTANCE;
        TraceType[] types = TraceType.values();
        Direction[] dirs = Direction.values();

        for (TraceData.Entry e : list) {
            if (e.type() < 0 || e.type() >= types.length || e.face() < 0 || e.face() >= dirs.length) continue;
            TraceType type = types[e.type()];
            Direction face = dirs[e.face()];
            BlockPos p = e.pos();
            double dx = p.getX() + 0.5 - cam.x, dy = p.getY() + 0.5 - cam.y, dz = p.getZ() + 0.5 - cam.z;
            if (dx * dx + dy * dy + dz * dz > maxSq || !level.hasChunkAt(p)) continue;
            // опора разрушена или место перекрыто: след не рисуем (вернётся, если блок появится снова)
            if (!level.getBlockState(p).isFaceSturdy(level, p, face)
                    || !level.getBlockState(p.relative(face)).isAir()) continue;
            int light = LevelRenderer.getLightColor(level, p.relative(face));
            float off = 0.5f + TraceTuning.DECAL_OFFSET;

            ps.pushPose();
            ps.translate(dx, dy, dz);
            switch (type.kind) {
                case FLOOR -> {
                    ps.translate(0, off, 0);
                    ps.mulPose(Axis.XP.rotationDegrees(-90f));
                    ps.mulPose(Axis.ZP.rotationDegrees(e.rot() * 45f));
                    decal(ps, buffers, texture(type, e.variant()), type.size, light);
                }
                case WALL -> {
                    ps.translate(face.getStepX() * off, 0, face.getStepZ() * off);
                    ps.mulPose(Axis.YP.rotationDegrees(-face.toYRot()));
                    ps.mulPose(Axis.ZP.rotationDegrees((e.rot() - 3.5f) * 4f));
                    decal(ps, buffers, texture(type, e.variant()), type.size, light);
                }
                case TEXT -> {
                    if (face.getAxis().isVertical()) break;
                    ps.translate(face.getStepX() * off, 0, face.getStepZ() * off);
                    ps.mulPose(Axis.YP.rotationDegrees(-face.toYRot()));
                    ps.mulPose(Axis.ZP.rotationDegrees(((p.hashCode() & 1) == 0 ? 1f : -1f) * TraceTuning.WRITING_TILT_DEGREES));
                    int variant = Math.floorMod(e.variant(), 8);
                    BlockPos reference = e.origin() == null ? p : e.origin();
                    net.minecraft.network.chat.MutableComponent text = variant == 4
                            ? net.minecraft.network.chat.Component.translatable("hypersomnia.trace.writing." + variant, reference.getX(), reference.getY(), reference.getZ())
                            : net.minecraft.network.chat.Component.translatable("hypersomnia.trace.writing." + variant);
                    text.withStyle(net.minecraft.ChatFormatting.ITALIC);
                    float scale = Math.min(TraceTuning.WRITING_MAX_SCALE, TraceTuning.WRITING_MAX_WIDTH / Math.max(1, mc.font.width(text)));
                    ps.scale(scale, -scale, scale);
                    mc.font.drawInBatch(text, -mc.font.width(text) / 2f, -4f, 0xFF423138, false,
                            ps.last().pose(), buffers, net.minecraft.client.gui.Font.DisplayMode.POLYGON_OFFSET, 0, light);
                }
                case ITEM -> {
                    // Игрушка упала: лежит на полу плашмя, повёрнута на rot * 45. Стоящий плоский предмет сбоку не виден.
                    ps.translate(0, 0.5 + TraceTuning.ITEM_LIFT, 0);
                    ps.mulPose(Axis.YP.rotationDegrees(e.rot() * 45f));
                    ps.mulPose(Axis.XP.rotationDegrees(-90f));
                    ps.scale(type.size, type.size, type.size);
                    ps.translate(0, -TraceTuning.ITEM_GROUND_SHIFT, 0);
                    mc.getItemRenderer().renderStatic(stack(type), ItemDisplayContext.GROUND, light,
                            OverlayTexture.NO_OVERLAY, ps, buffers, level, p.hashCode());
                }
            }
            ps.popPose();
        }
        buffers.endBatch();
    }

    /** Квадрат size x size в плоскости XY, нормаль +Z (вызывающий уже повернул систему координат). */
    private static void decal(PoseStack ps, MultiBufferSource buffers, ResourceLocation tex, float size, int light) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(tex));
        PoseStack.Pose pose = ps.last();
        float h = size / 2f;
        vertex(vc, pose, -h, -h, 0f, 1f, light);
        vertex(vc, pose, h, -h, 1f, 1f, light);
        vertex(vc, pose, h, h, 1f, 0f, light);
        vertex(vc, pose, -h, h, 0f, 0f, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float u, float v, int light) {
        vc.addVertex(pose, x, y, 0f).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0f, 0f, 1f);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TraceClientState.reset();
    }
}