package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Глаз для документов и шифров (те же текстуры silhouette_eye_*, что у силуэта сцены).
 * open: 1 = широко (тире), ~0.28 = узкий (точка). dx, dy: -1..1, смещение зрачка (слой 3).
 */
public final class EyeGlyphs {
    private EyeGlyphs() {}

    private static final int PX = 24;
    private static final float W_U = 5f;
    private static final float H_U = 3.4f;
    private static final float IRIS_U = 2.2f;

    private static ResourceLocation tex(String layer) {
        return ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "textures/misc/silhouette_eye_" + layer + ".png");
    }

    private static final ResourceLocation SCLERA = tex("sclera");
    private static final ResourceLocation IRIS = tex("iris");
    private static final ResourceLocation LIDS = tex("lids");

    /** cx, cy: центр глаза на экране; widthPx: ширина глаза в пикселях GUI. */
    public static void draw(GuiGraphics g, float cx, float cy, float widthPx,
                            float open, float dx, float dy, float alpha) {
        draw(g, cx, cy, widthPx, open, dx, dy, alpha, 0f);
    }

    /** То же с наклоном всего глаза (градусы): третий признак глазного алфавита. */
    public static void draw(GuiGraphics g, float cx, float cy, float widthPx,
                            float open, float dx, float dy, float alpha, float tiltDeg) {
        open = Mth.clamp(open, 0.08f, 1f);
        float unit = widthPx / W_U;
        float sc = unit / PX;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0f);
        if (tiltDeg != 0f) g.pose().mulPose(Axis.ZP.rotationDegrees(tiltDeg));
        layer(g, SCLERA, W_U, H_U, 0f, 0f, sc, open, alpha);
        layer(g, IRIS, IRIS_U, IRIS_U, dx * unit * 1.0f, dy * unit * 0.6f * open, sc, open, alpha);
        layer(g, LIDS, W_U, H_U, 0f, 0f, sc, open, 1f);
        g.pose().popPose();
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private static void layer(GuiGraphics g, ResourceLocation t, float wU, float hU,
                              float offX, float offY, float sc, float open, float alpha) {
        int w = Math.round(wU * PX);
        int h = Math.round(hU * PX);
        g.pose().pushPose();
        g.pose().translate(offX, offY, 0f);
        g.pose().scale(sc, sc * open, 1f);
        g.setColor(1f, 1f, 1f, Mth.clamp(alpha, 0f, 1f));
        g.blit(t, -w / 2, -h / 2, 0, 0f, 0f, w, h, w, h);
        g.pose().popPose();
    }
}