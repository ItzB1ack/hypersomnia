package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Один глаз осколка теми же текстурами, что в сцене у кровати (склера, радужка, веки; «инфракрасная съёмка»).
 * Для россыпей: углы экрана в погоне (ChaseEyes) и толпа глаз в меню паузы (SleepSceneOverlay).
 * Вызывающий включает смешивание и выключает тест глубины.
 */
public final class EyeSprite {
    private EyeSprite() {}

    /** Пикселей текстуры на юнит (как EYE_PX в tools/make_silhouette.py) и размеры слоёв в юнитах. */
    private static final int PX = 24;
    private static final float SCLERA_W = 5f;
    private static final float SCLERA_H = 3.4f;
    private static final float IRIS = 2.2f;
    /** Радужка дальше этого (юниты) не уходит: иначе вылезет из-под век. */
    public static final float GAZE_X = 0.6f;
    public static final float GAZE_Y = 0.25f;

    private static final ResourceLocation SCLERA = tex("sclera");
    private static final ResourceLocation SCLERA_R = tex("sclera_r");
    private static final ResourceLocation IRIS_L = tex("iris");
    private static final ResourceLocation IRIS_R = tex("iris_r");
    private static final ResourceLocation LIDS = tex("lids");
    private static final ResourceLocation LIDS_R = tex("lids_r");

    private static ResourceLocation tex(String layer) {
        return ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "textures/misc/silhouette_eye_" + layer + ".png");
    }

    /**
     * Глаз с центром (x, y), ширина склеры width (пиксели интерфейса). open 0..1 — раскрытие (глаз сжимается
     * по вертикали), gx/gy — взгляд в долях GAZE_X/GAZE_Y (-1..1), tilt — наклон в градусах, iris — масштаб радужки.
     */
    public static void draw(GuiGraphics g, float x, float y, float width, float open, float gx, float gy,
                            float alpha, boolean mirrored, float tilt, float iris) {
        open = Mth.clamp(open, 0f, 1f);
        alpha = Mth.clamp(alpha, 0f, 1f);
        if (open < 0.03f || alpha < 0.01f || width < 1f) return;
        float k = width / SCLERA_W;
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        if (tilt != 0f) g.pose().mulPose(Axis.ZP.rotationDegrees(tilt));
        g.setColor(1f, 1f, 1f, alpha);
        layer(g, mirrored ? SCLERA_R : SCLERA, 0f, 0f, SCLERA_W, SCLERA_H, k, 1f, open);
        float ix = Mth.clamp(gx, -1f, 1f) * GAZE_X;
        float iy = Mth.clamp(gy, -1f, 1f) * GAZE_Y * open;
        layer(g, mirrored ? IRIS_R : IRIS_L, ix, iy, IRIS, IRIS, k, iris, iris * open);
        layer(g, mirrored ? LIDS_R : LIDS, 0f, 0f, SCLERA_W, SCLERA_H, k, 1f, open);
        g.setColor(1f, 1f, 1f, 1f);
        g.pose().popPose();
    }

    private static void layer(GuiGraphics g, ResourceLocation tex, float dxU, float dyU, float wU, float hU,
                              float k, float sx, float sy) {
        int tw = (int) (wU * PX); // как int(...) в скрипте: 120x81 и 52x52
        int th = (int) (hU * PX);
        g.pose().pushPose();
        g.pose().translate(dxU * k, dyU * k, 0f);
        float sc = k / PX;
        g.pose().scale(sc * sx, sc * sy, 1f);
        g.blit(tex, -tw / 2, -th / 2, 0, 0f, 0f, tw, th, tw, th);
        g.pose().popPose();
    }
}
