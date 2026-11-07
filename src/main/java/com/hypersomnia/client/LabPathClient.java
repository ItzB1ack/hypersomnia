package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModItems;
import com.hypersomnia.ObserverTuning;
import com.hypersomnia.document.DocumentItem;
import com.hypersomnia.network.LabPathAck;
import com.hypersomnia.network.LabPathPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Иллюзия перегородки для одного клиента; блоки в чанке не меняются. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class LabPathClient {
    private LabPathClient() {}
    private static LabPathPayload gate;
    private static int remaining, sceneAge;
    private static boolean playing, acked;
    public static boolean active() { return playing; }
    public static void handle(LabPathPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.ticks() <= 0) { if (gate != null && gate.token() == payload.token()) clear(); return; }
            if (Math.abs(payload.dx()) + Math.abs(payload.dz()) != 1) return;
            gate = payload; remaining = Math.min(payload.ticks(), ObserverTuning.LAB_PATH_DURATION);
            sceneAge = 0; playing = false; acked = false;
        });
    }
    private static void clear() { gate = null; remaining = 0; sceneAge = 0; playing = false; acked = false; }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (gate == null || mc.isPaused()) return;
        if (mc.level == null || mc.player == null || !mc.player.isAlive() || mc.player.isSleeping()
                || !mc.level.dimension().equals(Level.OVERWORLD) || --remaining <= 0) { clear(); return; }
        if (!playing && !ObserverClient.isBusy(mc) && ObserverDirector.current() == null
                && !SleepSceneState.active() && !NoraRoomClient.active()) playing = true;
        if (playing) sceneAge++;
        if (playing && !acked && mc.getConnection() != null) {
            PacketDistributor.sendToServer(new LabPathAck(gate.token()));
            acked = true;
        }
    }
    /** Элементы композиции меняют масштаб, не прозрачность. */
    private static float homeScale() {
        float in = Math.min(1f, sceneAge / (float) ObserverTuning.LAB_PATH_HOME_APPEAR_TICKS);
        float out = Math.min(1f, remaining / (float) ObserverTuning.LAB_PATH_HOME_APPEAR_TICKS);
        float t = Math.max(0f, Math.min(in, out));
        t = t * t * (3f - 2f * t);
        return ObserverTuning.LAB_PATH_HOME_MIN_SCALE + (1f - ObserverTuning.LAB_PATH_HOME_MIN_SCALE) * t;
    }
    private static BlockPos gateCell(int lateral, int y) {
        return gate.base().offset(-gate.dz() * lateral, y, gate.dx() * lateral);
    }
    private static boolean air(Minecraft mc, BlockPos p) {
        return mc.level.hasChunkAt(p) && mc.level.getBlockState(p).isAir();
    }
    private static void block(Minecraft mc, PoseStack pose, MultiBufferSource buffers, Vec3 cam,
                              BlockPos pos, BlockState state, float scale) {
        if (!air(mc, pos)) return;
        pose.pushPose();
        pose.translate(pos.getX() + .5 - cam.x, pos.getY() - cam.y, pos.getZ() + .5 - cam.z);
        pose.scale(scale, scale, scale);
        pose.translate(-.5, 0, -.5);
        mc.getBlockRenderer().renderSingleBlock(state, pose, buffers,
                LevelRenderer.getLightColor(mc.level, pos), OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
    private static void item(Minecraft mc, PoseStack pose, MultiBufferSource buffers, Vec3 cam,
                             BlockPos pos, ItemStack stack, double side, float scale, boolean flat) {
        if (!air(mc, pos)) return;
        pose.pushPose();
        pose.translate(pos.getX() + .5 - cam.x - gate.dz() * side,
                pos.getY() + .63 - cam.y, pos.getZ() + .5 - cam.z + gate.dx() * side);
        pose.scale(scale, scale, scale);
        if (flat) pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90f));
        mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND,
                LevelRenderer.getLightColor(mc.level, pos), OverlayTexture.NO_OVERLAY,
                pose, buffers, mc.level, pos.hashCode());
        pose.popPose();
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (!playing || gate == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (int i = -1; i <= 1; i++) for (int y = 0; y < 3; y++)
            if (i != 0 || y == 2)
                block(mc, pose, buffers, cam, gateCell(i, y), Blocks.DARK_OAK_PLANKS.defaultBlockState(), 1f);
        float scale = homeScale();
        block(mc, pose, buffers, cam, gateCell(0, 0), Blocks.LIGHT_BLUE_CARPET.defaultBlockState(), scale);
        BlockPos shelf = gateCell(0, 1);
        block(mc, pose, buffers, cam, shelf, Blocks.OAK_SLAB.defaultBlockState(), scale);
        float itemScale = ObserverTuning.LAB_PATH_HOME_ITEM_SCALE * scale;
        item(mc, pose, buffers, cam, shelf, new ItemStack(ModItems.BUNNY.get()), -.20, itemScale, false);
        item(mc, pose, buffers, cam, shelf, DocumentItem.make("drawing_4"), .20, itemScale, true);
        buffers.endBatch();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
}
