package com.hypersomnia.client;

import com.hypersomnia.nightmare.NightmareTuning;
import com.hypersomnia.nightmare.ShaftBuilder;
import com.hypersomnia.network.ShaftOpenPayload;
import com.hypersomnia.network.ShaftSelectPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Панель лифта кошмара [новое]: собственная отрисовка в стиле LabLiftScreen, без ванильных кнопок.
 * Табло показывает текущий этаж, ниже только этажи глубже; сервер сам решает, куда ехать (всегда вниз).
 */
public final class ShaftScreen extends Screen {
    private static final int PW = 116, ROW = 22, ROW_GAP = 4, BW = 92, LIST = 4;
    private static final int VEIL = 0xD003050A, FRAME = 0xFF1E2A34, BODY = 0xFF0A0E12, GLASS = 0xFF05080B;
    private static final int TEXT = 0xFFDDE6EC, COLD = 0xFF4A5E70, LED = 0xFF8A2030;

    private final int current;
    private ShaftScreen(int current) { super(Component.translatable("hypersomnia.nightmare.shaft.title")); this.current = current; }
    public static void receive(ShaftOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new ShaftScreen(payload.current())));
    }
    @Override public boolean isPauseScreen() { return false; }

    private static String label(int floor) { return floor == 0 ? "0" : "B" + floor; }
    /** [новое] После нескольких поездок снизу появляется рабочая кнопка 0. */
    private boolean zero() { return current - ShaftBuilder.FIRST_FLOOR >= NightmareTuning.SHAFT_TRIPS_TO_ZERO; }
    private int rows() { return LIST + (zero() ? 1 : 0); }
    private int target(int i) { return i < LIST ? current + 1 + i : 0; }
    private int ph() { return 66 + rows() * (ROW + ROW_GAP) + 6; }
    private int px() { return (width - PW) / 2; }
    private int py() { return (height - ph()) / 2; }
    private int rowX() { return px() + (PW - BW) / 2; }
    private int rowY(int i) { return py() + 62 + i * (ROW + ROW_GAP); }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, VEIL);
        int x = px(), y = py();
        g.fill(x - 1, y - 1, x + PW + 1, y + ph() + 1, FRAME);
        g.fill(x, y, x + PW, y + ph(), BODY);
        g.drawCenteredString(font, title, x + PW / 2, y + 8, COLD);
        // Табло: текущий этаж крупно.
        g.fill(x + 16, y + 22, x + PW - 16, y + 52, FRAME);
        g.fill(x + 17, y + 23, x + PW - 17, y + 51, GLASS);
        g.pose().pushPose();
        g.pose().translate(x + PW / 2f, y + 29, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawCenteredString(font, label(current), 0, 0, LED);
        g.pose().popPose();
        for (int i = 0; i < rows(); i++) {
            int bx = rowX(), by = rowY(i);
            boolean hot = mx >= bx && mx < bx + BW && my >= by && my < by + ROW;
            g.fill(bx - 1, by - 1, bx + BW + 1, by + ROW + 1, FRAME);
            g.fill(bx, by, bx + BW, by + ROW, hot ? 0xFF1E2A34 : GLASS);
            g.drawString(font, label(target(i)), bx + 8, by + 7, hot ? TEXT : COLD, false);
        }
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = 0; i < rows(); i++) {
                int bx = rowX(), by = rowY(i);
                if (mx >= bx && mx < bx + BW && my >= by && my < by + ROW) {
                    PacketDistributor.sendToServer(new ShaftSelectPayload(target(i)));
                    onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
