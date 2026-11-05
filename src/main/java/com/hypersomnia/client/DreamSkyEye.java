package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.dream.DreamTuning;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Мир снов: вместо луны в небе глаз осколка. Рисуется ровно там, где ваниль рисует луну
 * (те же повороты, плоскость y=-100), поэтому закрывает её.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class DreamSkyEye {
    private DreamSkyEye() {}

    private static final ResourceLocation DREAM = ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "dream");
    private static final ResourceLocation VOID = tex("dream_sky_void");
    private static final ResourceLocation SCLERA = tex("silhouette_eye_sclera");
    private static final ResourceLocation IRIS = tex("silhouette_eye_iris");
    private static final ResourceLocation LIDS = tex("silhouette_eye_lids");
    private static final float SKY_Y = -100f;

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "textures/misc/" + name + ".png");
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().equals(DREAM)) return;

        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long ms = (long) ((level.getGameTime() + pt) * 50f);

        Matrix4f m = new Matrix4f(event.getModelViewMatrix());
        m.rotate(Axis.YP.rotationDegrees(-90f));
        m.rotate(Axis.XP.rotationDegrees(level.getTimeOfDay(pt) * 360f));

        float hw = DreamTuning.SKY_EYE_HALF_WIDTH;
        float hh = hw * 81f / 120f;
        float sy = blink(ms, DreamTuning.SKY_EYE_BLINK_PERIOD_MS);

        // Взгляд: в основном вниз на игрока, иногда в сторону
        float gx = 0f, gy = 0f;
        RandomSource gr = RandomSource.create(ms / 1300L * 31L + 7L);
        if (gr.nextInt(10) >= 7) {
            gx = (gr.nextFloat() - 0.5f) * 0.34f;
            gy = (gr.nextFloat() - 0.5f) * 0.12f;
        }
        gx += (float) Math.sin(ms / 410.0) * 0.02f;

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        quad(m, VOID, 0f, 0f, hw * 0.64f, hw * 0.64f, 1f);
        if (ObserverState.stage() >= DreamTuning.SKY_EYE_MIN_STAGE) {
            quad(m, SCLERA, 0f, 0f, hw, hh * sy, DreamTuning.SKY_EYE_ALPHA);
            quad(m, IRIS, gx * 2f * hw, gy * 2f * hh * sy, hw * 0.44f, hh * 0.647f * sy, DreamTuning.SKY_EYE_ALPHA);
            quad(m, LIDS, 0f, 0f, hw, hh * sy, 1f);
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /** 1 = открыт, ~0.08 = закрыт; моргание 160 мс раз в period мс. */
    private static float blink(long ms, int period) {
        long ph = ms % period;
        if (ph > 160) return 1f;
        return 1f - 0.92f * (float) Math.sin(Math.PI * ph / 160.0);
    }

    private static void quad(Matrix4f m, ResourceLocation tex, float cx, float cz, float hw, float hh, float alpha) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(m, cx - hw, SKY_Y, cz + hh).setUv(0f, 1f);
        b.addVertex(m, cx + hw, SKY_Y, cz + hh).setUv(1f, 1f);
        b.addVertex(m, cx + hw, SKY_Y, cz - hh).setUv(1f, 0f);
        b.addVertex(m, cx - hw, SKY_Y, cz - hh).setUv(0f, 0f);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }
}