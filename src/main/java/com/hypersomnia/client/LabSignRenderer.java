package com.hypersomnia.client;

import com.hypersomnia.lab.LabSignBlock;
import com.hypersomnia.lab.LabSignBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Текст таблички лаборатории поверх пластины (модель lab_sign). Строки — из LabSignBlockEntity;
 * перевод — ключ hypersomnia.lab.sign.&lt;key&gt; (строки через «|»), без ключа — английский текст чертежа.
 * Текст по центру поля внутри рамки, мелкий шрифт ужимается под самую длинную строку. Свет — как у окружения.
 */
public final class LabSignRenderer implements BlockEntityRenderer<LabSignBlockEntity> {
    private static final int TEXT = 0xFFDDE6EC;
    /** Поле текста внутри рамки (в блоках) и наибольший масштаб шрифта (как у ванильной таблички). */
    private static final float FIELD_W = 0.75f, FIELD_H = 0.375f, MAX_SCALE = 1f / 96f;
    /** Лицевая плоскость пластины от центра блока (пластина 1/16 у стены) и зазор текста над ней. */
    private static final float FRONT = -0.4375f, LIFT = 0.003f;
    private static final int LINE_H = 10;

    private final Font font;

    public LabSignRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(LabSignBlockEntity sign, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        List<String> raw = sign.text();
        if (raw.isEmpty() || !sign.getBlockState().hasProperty(LabSignBlock.FACING)) return;
        String[] lines = Component.translatableWithFallback("hypersomnia.lab.sign." + sign.key(), String.join("|", raw))
                .getString().split("\\|", -1);
        int n = lines.length;
        int maxW = 1;
        for (String l : lines) maxW = Math.max(maxW, font.width(l));
        float s = Math.min(MAX_SCALE, Math.min(FIELD_W / maxW, FIELD_H / (n * LINE_H - 1)));

        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f);
        // Локальная +z — от стены в комнату (FACING), как у ванильной настенной таблички.
        pose.mulPose(Axis.YP.rotationDegrees(-sign.getBlockState().getValue(LabSignBlock.FACING).toYRot()));
        pose.translate(0f, 0f, FRONT + LIFT);
        pose.scale(s, -s, s);
        Matrix4f m = pose.last().pose();
        float y0 = -(n * LINE_H - 1) / 2f;
        for (int i = 0; i < n; i++) {
            String l = lines[i];
            if (l.isEmpty()) continue;
            font.drawInBatch(l, -font.width(l) / 2f, y0 + i * LINE_H, TEXT, false, m, buffers,
                    Font.DisplayMode.POLYGON_OFFSET, 0, light);
        }
        pose.popPose();
    }
}
