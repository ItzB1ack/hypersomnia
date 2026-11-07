package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModItems;
import com.hypersomnia.document.DocumentItem;
import com.hypersomnia.ObserverTuning;
import com.hypersomnia.network.NoraRoomAck;
import com.hypersomnia.network.NoraRoomPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.List;

/** Видна только этому игроку; отсутствуют block entities, реальные блоки и предметы. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class NoraRoomClient {
    private NoraRoomClient() {}
    private static NoraRoomPayload pending;
    private static int remaining, sceneAge, visibleTicks, lastAck;
    private static boolean playing;
    private record Picture(BlockPos pos, Direction face, int index) {}
    private static final List<Picture> pictures = new ArrayList<>();
    public static boolean active() { return playing; }
    public static void handle(NoraRoomPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (p.ticks() <= 0) { if (pending != null && p.token() == pending.token()) clear(); return; }
            pending = p;
            remaining = Math.min(p.ticks(), ObserverTuning.NORA_ROOM_DURATION);
            sceneAge = 0; visibleTicks = 0; lastAck = 0; playing = false; pictures.clear();
        });
    }
    private static void clear() { pending = null; remaining = 0; sceneAge = 0; visibleTicks = 0; lastAck = 0; playing = false; pictures.clear(); }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (pending == null || mc.isPaused()) return;
        if (mc.level == null || mc.player == null || !mc.player.isAlive() || mc.player.isSleeping()
                || !mc.level.dimension().equals(Level.OVERWORLD) || --remaining <= 0) { clear(); return; }
        if (!playing && !ObserverClient.isBusy(mc) && ObserverDirector.current() == null && !SleepSceneState.active()) {
            playing = true;
            if ((pending.layout() & ObserverTuning.NORA_ROOM_LAYOUT_BED) != 0) findPictures(mc);
        }
        if (!playing) return;
        sceneAge++;
        boolean looking = !ObserverClient.isBusy(mc) && lookingAtRoom(mc);
        if (looking) visibleTicks++;
        if (looking && visibleTicks >= ObserverTuning.NORA_ROOM_ACK_TICKS && mc.getConnection() != null
                && (lastAck == 0 || visibleTicks - lastAck >= ObserverTuning.NORA_ROOM_ACK_RETRY_TICKS)) {
            PacketDistributor.sendToServer(new NoraRoomAck(pending.token()));
            lastAck = visibleTicks;
        }
    }
    private static boolean lookingAtRoom(Minecraft mc) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 view = mc.player.getViewVector(1f);
        BlockPos[] positions = {pending.a(), pending.b(), pending.c()};
        int[] bits = {ObserverTuning.NORA_ROOM_LAYOUT_BED, ObserverTuning.NORA_ROOM_LAYOUT_CARPET, ObserverTuning.NORA_ROOM_LAYOUT_TOY};
        for (int i = 0; i < positions.length; i++) {
            if ((pending.layout() & bits[i]) == 0) continue;
            BlockPos p = positions[i];
            if (bits[i] == ObserverTuning.NORA_ROOM_LAYOUT_BED
                    && (!mc.level.hasChunkAt(p.north()) || !mc.level.getBlockState(p.north()).isAir())) continue;
            Vec3 to = Vec3.atCenterOf(p).subtract(eye);
            if (to.lengthSqr() > 0.01 && view.dot(to.normalize()) >= ObserverTuning.NORA_ROOM_VIEW_DOT
                    && mc.level.hasChunkAt(p) && mc.level.getBlockState(p).isAir()) return true;
        }
        return false;
    }
    private static void findPictures(Minecraft mc) {
        pictures.clear();
        BlockPos anchor = pending.a();
        int radius = ObserverTuning.NORA_ROOM_PICTURE_SEARCH;
        for (int dx = -radius; dx <= radius && pictures.size() < ObserverTuning.NORA_ROOM_PICTURE_COUNT; dx++)
            for (int dz = -radius; dz <= radius && pictures.size() < ObserverTuning.NORA_ROOM_PICTURE_COUNT; dz++)
                for (int dy = 1; dy <= 2 && pictures.size() < ObserverTuning.NORA_ROOM_PICTURE_COUNT; dy++) {
                    BlockPos wall = anchor.offset(dx, dy, dz);
                    if (!mc.level.hasChunkAt(wall) || mc.level.getBlockEntity(wall) != null) continue;
                    for (Direction face : Direction.Plane.HORIZONTAL) {
                        BlockPos air = wall.relative(face);
                        if (mc.level.hasChunkAt(air) && mc.level.getBlockState(air).isAir()
                                && mc.level.getBlockState(wall).isFaceSturdy(mc.level, wall, face)) {
                            int[] indices = ObserverTuning.NORA_ROOM_PICTURE_INDICES;
                            pictures.add(new Picture(wall, face, indices[pictures.size() % indices.length]));
                            break;
                        }
                    }
                }
    }
    /** Плавное изменение масштаба моделей; это не прозрачность. */
    private static float visualScale() {
        float in = Math.min(1f, sceneAge / (float) ObserverTuning.NORA_ROOM_FADE_TICKS);
        float out = Math.min(1f, remaining / (float) ObserverTuning.NORA_ROOM_FADE_TICKS);
        float t = Math.max(0f, Math.min(in, out));
        float phase = t * t * (3f - 2f * t);
        return ObserverTuning.NORA_ROOM_FADE_MIN_SCALE
                + (1f - ObserverTuning.NORA_ROOM_FADE_MIN_SCALE) * phase;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (!playing || pending == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float scale = visualScale();
        BlockPos foot = pending.a().north();
        if ((pending.layout() & ObserverTuning.NORA_ROOM_LAYOUT_BED) != 0
                && mc.level.hasChunkAt(foot) && mc.level.getBlockState(foot).isAir())
            block(mc, pose, buffers, camera, pending.a(), Blocks.LIGHT_BLUE_BED.defaultBlockState(), scale);
        if ((pending.layout() & ObserverTuning.NORA_ROOM_LAYOUT_CARPET) != 0)
            block(mc, pose, buffers, camera, pending.b(), Blocks.LIGHT_BLUE_CARPET.defaultBlockState(), scale);
        if ((pending.layout() & ObserverTuning.NORA_ROOM_LAYOUT_TOY) != 0)
            block(mc, pose, buffers, camera, pending.c(), Blocks.OAK_SLAB.defaultBlockState(), scale);
        BlockPos drawing = pending.b();
        if ((pending.layout() & ObserverTuning.NORA_ROOM_LAYOUT_CARPET) != 0
                && mc.level.hasChunkAt(drawing) && mc.level.getBlockState(drawing).isAir()) {
            pose.pushPose();
            pose.translate(drawing.getX() + .5 - camera.x, drawing.getY() + .15 - camera.y, drawing.getZ() + .5 - camera.z);
            pose.scale(scale, scale, scale);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90f));
            mc.getItemRenderer().renderStatic(DocumentItem.make("drawing_4"), ItemDisplayContext.GROUND,
                    LevelRenderer.getLightColor(mc.level, drawing), OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, drawing.hashCode());
            pose.popPose();
        }
        BlockPos toy = pending.c();
        if ((pending.layout() & ObserverTuning.NORA_ROOM_LAYOUT_TOY) != 0
                && mc.level.hasChunkAt(toy) && mc.level.getBlockState(toy).isAir()) {
            pose.pushPose();
            pose.translate(toy.getX() + .5 - camera.x, toy.getY() + .65 - camera.y, toy.getZ() + .5 - camera.z);
            pose.scale(scale, scale, scale);
            mc.getItemRenderer().renderStatic(new ItemStack(ModItems.BUNNY.get()), ItemDisplayContext.GROUND,
                    LevelRenderer.getLightColor(mc.level, toy), OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, toy.hashCode());
            pose.popPose();
        }
        for (Picture picture : pictures) picture(mc, pose, buffers, camera, picture);
        buffers.endBatch();
    }
    private static void picture(Minecraft mc, PoseStack pose, MultiBufferSource buffers, Vec3 camera, Picture picture) {
        BlockPos p = picture.pos();
        Direction face = picture.face();
        if (!mc.level.hasChunkAt(p) || !mc.level.hasChunkAt(p.relative(face))
                || mc.level.getBlockEntity(p) != null || !mc.level.getBlockState(p.relative(face)).isAir()
                || !mc.level.getBlockState(p).isFaceSturdy(mc.level, p, face)) return;
        pose.pushPose();
        float offset = ObserverTuning.NORA_ROOM_PICTURE_OFFSET;
        pose.translate(p.getX() + .5 - camera.x + face.getStepX() * offset,
                p.getY() + .5 - camera.y, p.getZ() + .5 - camera.z + face.getStepZ() * offset);
        float scale = visualScale();
        pose.scale(scale, scale, scale);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-face.toYRot()));
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID,
                "textures/misc/drawing_" + picture.index() + ".png");
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(texture));
        float w = ObserverTuning.NORA_ROOM_PICTURE_SIZE / 2f, h = w * .75f;
        int light = LevelRenderer.getLightColor(mc.level, p.relative(face));
        pictureVertex(vc, pose.last(), -w, h, 0, 0, light);
        pictureVertex(vc, pose.last(), -w, -h, 0, 1, light);
        pictureVertex(vc, pose.last(), w, -h, 1, 1, light);
        pictureVertex(vc, pose.last(), w, h, 1, 0, light);
        pose.popPose();
    }
    private static void pictureVertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float u, float v, int light) {
        vc.addVertex(pose, x, y, 0).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
    }
    private static void block(Minecraft mc, PoseStack pose, MultiBufferSource buffers, Vec3 camera, BlockPos p, BlockState state, float scale) {
        if (!mc.level.hasChunkAt(p) || !mc.level.getBlockState(p).isAir()) return;
        pose.pushPose();
        pose.translate(p.getX() + .5 - camera.x, p.getY() - camera.y, p.getZ() + .5 - camera.z);
        pose.scale(scale, scale, scale);
        pose.translate(-.5, 0, -.5);
        mc.getBlockRenderer().renderSingleBlock(state, pose, buffers, LevelRenderer.getLightColor(mc.level, p), OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
}
