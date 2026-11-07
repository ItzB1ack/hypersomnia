package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ObserverTuning;
import com.hypersomnia.dream.DreamDimension;
import com.hypersomnia.network.WardGhostPayload;
import com.hypersomnia.nightmare.NightmareDefs;
import com.hypersomnia.nightmare.NightmareTuning;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Тихие фигуры рядом с койками, рендерятся только для игрока в кошмаре и не существуют в мире. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class WardGhostClient {
    private WardGhostClient() {}
    private static int remaining;
    private static int age;
    private static ObserverGhost ghost;

    public static void receive(WardGhostPayload payload) {
        if (payload.active()) remaining = NightmareTuning.WARD_GHOST_TIMEOUT_TICKS;
        else clear();
    }
    private static void clear() { remaining = 0; ghost = null; age = 0; }
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (remaining > 0) {
            remaining--;
            age++;
            if (remaining == 0) clear();
        }
    }
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || remaining <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.level.dimension().equals(DreamDimension.LEVEL)) return;
        if (ghost == null || ghost.level() != mc.level) {
            ghost = new ObserverGhost(mc.level);
            ghost.setPose(Pose.STANDING);
            ghost.variant = ObserverGhost.EYES;
        }
        BlockPos o = NightmareDefs.WARD_CORRIDOR.origin();
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        double max2 = NightmareTuning.WARD_GHOST_RENDER_DISTANCE * NightmareTuning.WARD_GHOST_RENDER_DISTANCE;
        for (int n = 0; n < NightmareTuning.WARD_PERIODS; n++) {
            int base = n * NightmareTuning.WARD_PERIOD;
            for (int side : new int[] {-1, 1}) {
                // На северной стороне изголовье z=-5; на южной z=width+4.
                double z = o.getZ() + (side < 0 ? -4.5 : NightmareTuning.CORRIDOR_WIDTH + 4.5);
                for (int x : new int[] {3, 5}) {
                    Vec3 at = new Vec3(o.getX() + base + x + 0.5, o.getY() + 1, z);
                    if (at.distanceToSqr(cam) > max2 || !mc.level.hasChunkAt(BlockPos.containing(at))) continue;
                    draw(mc, at, cam, age + partial, partial, ps, buffers);
                }
            }
        }
        buffers.endBatch();
    }

    private static void draw(Minecraft mc, Vec3 at, Vec3 cam, float t, float partial,
                             PoseStack ps, MultiBufferSource.BufferSource buffers) {
        double dx = mc.player.getX() - at.x, dz = mc.player.getZ() - at.z;
        double dy = mc.player.getEyeY() - (at.y + ObserverTuning.FACE_PIVOT_Y * ObserverTuning.GHOST_SCALE_Y);
        float yaw = (float)(Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90f;
        float pitch = (float)-(Mth.atan2(dy, Math.hypot(dx, dz)) * (180.0 / Math.PI));
        ghost.setPos(at.x, at.y, at.z);
        ghost.setYRot(yaw); ghost.setXRot(pitch);
        ghost.setYBodyRot(yaw); ghost.setYHeadRot(yaw);
        ghost.yBodyRotO = yaw; ghost.yHeadRotO = yaw;
        ghost.setOldPosAndRot();
        int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(at.x, at.y + 1, at.z));
        ps.pushPose();
        ps.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        float wobble = 1f + ObserverTuning.GHOST_WOBBLE * Mth.sin(t * ObserverTuning.GHOST_WOBBLE_SPEED);
        ps.scale(ObserverTuning.GHOST_SCALE_XZ * wobble, ObserverTuning.GHOST_SCALE_Y,
                ObserverTuning.GHOST_SCALE_XZ * wobble);
        mc.getEntityRenderDispatcher().render(ghost, 0, 0, 0, yaw, partial, ps, buffers, light);
        drawEyes(ps, buffers, t, yaw, pitch);
        ps.popPose();
    }

    private static ResourceLocation eyeTex(String layer, boolean mirrored) {
        return ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID,
                "textures/misc/silhouette_eye_" + layer + (mirrored ? "_r" : "") + ".png");
    }
    private static final ResourceLocation[][] EYES = {
            {eyeTex("sclera", true), eyeTex("sclera", false)},
            {eyeTex("iris", true), eyeTex("iris", false)},
            {eyeTex("lids", true), eyeTex("lids", false)}
    };
    private static float blink(long ms, int period, int phase) {
        long ph = (ms + phase) % period;
        return ph > 160 ? 1f : 1f - 0.92f * (float)Math.sin(Math.PI * ph / 160.0);
    }
    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, float a) {
        vc.addVertex(pose.pose(), x, y, z).setColor(1f, 1f, 1f, a).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0f, 0f, -1f);
    }
    /** Те же текстуры и расположение глаз, что у обычного ObserverClient. */
    private static void drawEyes(PoseStack ps, MultiBufferSource buffers, float t, float yaw, float pitch) {
        long ms = (long)(t * 50f);
        float gx = 0f, gy = 0f;
        RandomSource gr = RandomSource.create(ms / 1300L * 31L + 7L);
        if (gr.nextInt(10) >= 7) { gx = (gr.nextFloat() - 0.5f) * 0.34f; gy = (gr.nextFloat() - 0.5f) * 0.12f; }
        gx += (float)Math.sin(ms / 410.0) * 0.02f;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180f - yaw));
        ps.translate(0, ObserverTuning.FACE_PIVOT_Y, 0);
        ps.mulPose(Axis.XP.rotationDegrees(-pitch));
        PoseStack.Pose pose = ps.last();
        for (int layer = 0; layer < 3; layer++) for (int s = 0; s < 2; s++) {
            boolean left = s == 0;
            float sy = blink(ms, left ? 3700 : 4300, left ? 0 : 900);
            float size = ObserverTuning.FACE_EYE_W * (left ? 1f : 0.94f);
            float cx = (left ? 1f : -1f) * ObserverTuning.FACE_EYE_DX;
            float cy = ObserverTuning.FACE_EYE_Y - ObserverTuning.FACE_PIVOT_Y - (left ? 0f : 0.01f);
            float hw = size / 2f, hh = size * 0.675f / 2f * sy;
            float alpha = layer == 2 ? 1f : (left ? 1f : 0.85f);
            if (layer == 1) { cx += gx * size; cy += gy * size * sy; hw = size * 0.22f; hh = size * 0.2184f * sy; }
            float z = -(ObserverTuning.FACE_Z + layer * 0.003f);
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(EYES[layer][s]));
            vertex(vc, pose, cx + hw, cy - hh, z, 0f, 1f, alpha);
            vertex(vc, pose, cx - hw, cy - hh, z, 1f, 1f, alpha);
            vertex(vc, pose, cx - hw, cy + hh, z, 1f, 0f, alpha);
            vertex(vc, pose, cx + hw, cy + hh, z, 0f, 0f, alpha);
        }
        ps.popPose();
    }
}
