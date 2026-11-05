package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.document.DocumentDefs;
import com.hypersomnia.finale.FinaleTexts;
import com.hypersomnia.finale.FinaleTuning;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.List;

/**
 * Запись mem_7 на мониторе детской: субтитры строк журнала оператора по очереди и ответ Фрагмента
 * (винный цвет) или «нет отклика». Звука голоса нет: только щелчок кассеты и шипение на сервере.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FinaleOverlay {
    private FinaleOverlay() {}

    private static final int COLD = 0x4A5E70;
    private static final int LIGHT = 0xDDE6EC;
    private static final int WINE = 0xC8B8BC;

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "finale_subtitles"), FinaleOverlay::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        int t = FinaleClientState.playTick;
        if (t < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        int line = FinaleTuning.subLineTicks();
        String text = null;
        int color = LIGHT;
        boolean head = true;
        int local;
        int[] ids = FinaleTuning.LOG_LINES;
        if (t < ids.length * line) {
            int i = t / line;
            local = t % line;
            text = logLine(ids[i]);
        } else {
            local = t - FinaleTuning.replyAt();
            if (local < 0) return;
            String key = FinaleClientState.playHeard ? "hypersomnia.finale.reply" : "hypersomnia.finale.reply.none";
            text = Component.translatableWithFallback(key, FinaleTexts.ui(key)).getString();
            color = FinaleClientState.playHeard ? WINE : COLD;
            head = false; // ответ — уже не журнал оператора
        }
        float a = alpha(local);
        if (a <= 0.02f) return;
        draw(g, mc.font, text, color, a, head);
    }

    /** Строка журнала оператора: перевод страницы mem_7_log или английский текст из DocumentDefs. */
    public static String logLine(int el) {
        String en = "";
        DocumentDefs.Doc doc = DocumentDefs.get(FinaleTuning.LOG_DOC);
        if (doc != null && el < doc.els().size() && doc.els().get(el) instanceof DocumentDefs.Para p) en = p.text();
        return Component.translatableWithFallback("hypersomnia.doc." + FinaleTuning.LOG_DOC + "." + el, en).getString();
    }

    private static float alpha(int local) {
        int fade = FinaleTuning.SUB_FADE_TICKS;
        int total = FinaleTuning.subLineTicks();
        if (local < 0 || local >= total) return 0f;
        if (local < fade) return local / (float) fade;
        if (local > total - fade) return (total - local) / (float) fade;
        return 1f;
    }

    private static void draw(GuiGraphics g, Font font, String text, int color, float a, boolean withHead) {
        int w = g.guiWidth(), h = g.guiHeight();
        int maxW = Math.min(320, w - 40);
        List<FormattedCharSequence> rows = font.split(Component.literal(text), maxW);
        String head = withHead
                ? Component.translatableWithFallback("hypersomnia.finale.log", FinaleTexts.ui("hypersomnia.finale.log")).getString() : "";
        int rowH = 11, top = withHead ? 14 : 5;
        int boxH = rows.size() * rowH + top + 4;
        int y0 = h - 64 - boxH;
        int boxW = withHead ? font.width(head) : 0;
        for (FormattedCharSequence r : rows) boxW = Math.max(boxW, font.width(r));
        boxW += 16;
        int x0 = (w - boxW) / 2;
        int ab = (int) (a * 0.62f * 255) << 24;
        g.fill(x0, y0, x0 + boxW, y0 + boxH, ab | 0x03050A);
        int ac = Math.max(4, (int) (a * 255));
        if (withHead) g.drawCenteredString(font, head, w / 2, y0 + 3, (ac << 24) | COLD);
        for (int i = 0; i < rows.size(); i++)
            g.drawCenteredString(font, rows.get(i), w / 2, y0 + top + i * rowH, (ac << 24) | color);
    }
}
