package com.hypersomnia.client;

import com.hypersomnia.lab.LabTuning;
import com.hypersomnia.network.LabLiftSelectPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Панель лифта: G, B1..B5 сверху вниз. Недоступные этажи тусклые, закрытые помечены. */
public final class LabLiftScreen extends Screen {
    private static final int PW = 116, ROW = 22, ROW_GAP = 4, BW = 92;
    private static final int VEIL = 0xD003050A;
    private static final int FRAME = 0xFF1E2A34;
    private static final int BODY = 0xFF0A0E12;
    private static final int GLASS = 0xFF05080B;
    private static final int TEXT = 0xFFDDE6EC;
    private static final int COLD = 0xFF4A5E70;
    private static final int DIM = 0xFF2A353E;
    private static final int RED = 0xFF8A2030;

    private final int current;
    private final int enabled;
    private final int built;

    private LabLiftScreen(int current, int enabled, int built) {
        super(Component.translatable("hypersomnia.lab.lift.title"));
        this.current = current;
        this.enabled = enabled;
        this.built = built;
    }

    /** Вызывать только на клиенте. */
    public static void receive(int current, int enabled, int built) {
        Minecraft.getInstance().setScreen(new LabLiftScreen(current, enabled, built));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static boolean bit(int mask, int n) { return ((mask >> n) & 1) != 0; }

    private int ph() { return 30 + LabTuning.STOPS * (ROW + ROW_GAP) + 6; }
    private int px() { return (width - PW) / 2; }
    private int py() { return (height - ph()) / 2; }
    private int rowY(int i) { return py() + 28 + i * (ROW + ROW_GAP); }
    private int rowX() { return px() + (PW - BW) / 2; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        g.fill(0, 0, width, height, VEIL);
        int x = px(), y = py();
        g.fill(x - 1, y - 1, x + PW + 1, y + ph() + 1, FRAME);
        g.fill(x, y, x + PW, y + ph(), BODY);
        g.drawCenteredString(font, title, x + PW / 2, y + 8, COLD);

        for (int i = 0; i < LabTuning.STOPS; i++) {
            int bx = rowX(), by = rowY(i);
            boolean has = bit(built, i);
            boolean en = bit(enabled, i);
            boolean cur = i == current;
            boolean hot = en && mouseX >= bx && mouseX < bx + BW && mouseY >= by && mouseY < by + ROW;
            g.fill(bx - 1, by - 1, bx + BW + 1, by + ROW + 1, cur ? COLD : FRAME);
            g.fill(bx, by, bx + BW, by + ROW, hot ? 0xFF1E2A34 : GLASS);
            int labelColor = en ? (hot ? TEXT : COLD) : (cur ? TEXT : DIM);
            g.drawString(font, LabTuning.stopLabel(i), bx + 8, by + 7, labelColor, false);
            if (cur) {
                g.drawString(font, "<", bx + BW - 12, by + 7, TEXT, false);
            } else if (has && !en) {
                g.drawString(font, Component.translatable("hypersomnia.lab.lift.state.locked"), bx + 30, by + 7, RED, false);
            } else if (!has) {
                g.drawString(font, "--", bx + 30, by + 7, DIM, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = 0; i < LabTuning.STOPS; i++) {
                int bx = rowX(), by = rowY(i);
                if (bit(enabled, i) && mx >= bx && mx < bx + BW && my >= by && my < by + ROW) {
                    PacketDistributor.sendToServer(new LabLiftSelectPayload(i));
                    onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
