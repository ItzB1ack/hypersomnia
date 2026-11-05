package com.hypersomnia.client;

import com.hypersomnia.lab.LabBreakers;
import com.hypersomnia.lab.LabTuning;
import com.hypersomnia.network.LabPanelActionPayload;
import com.hypersomnia.network.LabPanelPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Экран щита MAIN FEED в аналоговом стиле кодовой панели: рубильники линий 1..FEEDERS слева направо и MAIN FEED справа.
 * Рукоять вверху = замкнут. Экран ничего не решает сам: шлёт номер рубильника (LabPanelActionPayload), сервер проверяет
 * и отвечает состоянием (LabPanelPayload), только оно и рисуется.
 */
public final class LabPanelScreen extends Screen {
    private static final int PW = 244, PH = 142;
    /** Рубильник линии: корпус SW x SH, шаг SGAP; главный шире (MW); DIV — промежуток с перегородкой перед ним. */
    private static final int SW = 24, SH = 44, SGAP = 10, MW = 40, DIV = 18, HANDLE = 8;
    private static final int ROW_W = LabTuning.FEEDERS * SW + (LabTuning.FEEDERS - 1) * SGAP + DIV + MW;
    private static final int VEIL = 0xD003050A;
    private static final int FRAME = 0xFF1E2A34;
    private static final int BODY = 0xFF0A0E12;
    private static final int PLATE = 0xFF10161C;
    private static final int GLASS = 0xFF05080B;
    private static final int HANDLE_C = 0xFF8A97A2;
    private static final int SHADOW = 0xFF2A333B;
    private static final int LAMP_OFF = 0xFF151C22;
    private static final int TEXT = 0xFFDDE6EC;
    private static final int COLD = 0xFF4A5E70;
    private static final int RED = 0xFF8A2030;
    private static final int MAIN_SLOT = LabTuning.FEEDERS; // индекс главного в выборе клавиатурой

    private int seq;
    private int flags;
    /** Выбор с клавиатуры: 0..FEEDERS-1 — линии, MAIN_SLOT — главный, -1 — нет. */
    private int sel = -1;
    private int flicker;
    private boolean sentMain;

    private LabPanelScreen(int seq, int flags) {
        super(Component.translatable("hypersomnia.lab.panel.title"));
        this.seq = seq;
        this.flags = flags;
    }

    /** Вызывать только на клиенте. OPEN — открыть экран, иначе обновить уже открытый. */
    public static void receive(int seq, int flags) {
        Minecraft mc = Minecraft.getInstance();
        if ((flags & LabPanelPayload.OPEN) != 0) {
            mc.setScreen(new LabPanelScreen(seq, flags & ~LabPanelPayload.OPEN));
        } else if (mc.screen instanceof LabPanelScreen s) {
            s.update(seq, flags);
        }
    }

    private void update(int seq, int flags) {
        if (sentMain && (flags & LabPanelPayload.TRIPPED) != 0) flicker = LabTuning.PANEL_TRIP_FLICKER_TICKS;
        sentMain = false;
        this.seq = seq;
        this.flags = flags;
    }

    private boolean feed() { return (flags & LabPanelPayload.FEED) != 0; }

    private boolean tripped() { return (flags & LabPanelPayload.TRIPPED) != 0; }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        if (flicker > 0) flicker--;
    }

    private int px() { return (width - PW) / 2; }
    private int py() { return (height - PH) / 2; }
    private int rowX() { return px() + (PW - ROW_W) / 2; }
    private int slotX(int i) { return i == MAIN_SLOT ? rowX() + ROW_W - MW : rowX() + i * (SW + SGAP); }
    private int slotW(int i) { return i == MAIN_SLOT ? MW : SW; }
    private int bodyY() { return py() + 54; }

    private int slotAt(double mx, double my) {
        int by = bodyY();
        for (int i = 0; i <= MAIN_SLOT; i++) {
            int bx = slotX(i);
            if (mx >= bx && mx < bx + slotW(i) && my >= by - 8 && my < by + SH) return i;
        }
        return -1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        g.fill(0, 0, width, height, VEIL);
        int x = px(), y = py();
        g.fill(x - 1, y - 1, x + PW + 1, y + PH + 1, FRAME);
        g.fill(x, y, x + PW, y + PH, BODY);
        for (int[] c : new int[][]{{x + 3, y + 3}, {x + PW - 5, y + 3}, {x + 3, y + PH - 5}, {x + PW - 5, y + PH - 5}})
            g.fill(c[0], c[1], c[0] + 2, c[1] + 2, FRAME); // винты
        g.drawCenteredString(font, title, x + PW / 2, y + 8, COLD);

        // Окно состояния
        int dx = x + 12, dy = y + 22, dw = PW - 24, dh = 16;
        g.fill(dx, dy, dx + dw, dy + dh, GLASS);
        int closed = LabBreakers.count(seq);
        String state;
        int color = TEXT;
        if (feed()) {
            state = Component.translatable("hypersomnia.lab.panel.state.on").getString();
        } else if (tripped()) {
            state = Component.translatable("hypersomnia.lab.panel.state.tripped").getString();
            color = RED;
        } else if (closed == 0) {
            state = Component.translatable("hypersomnia.lab.panel.state.off").getString();
            color = COLD;
        } else {
            state = Component.translatable("hypersomnia.lab.panel.state.closed", closed, LabTuning.FEEDERS).getString();
        }
        int jitter = flicker > 0 && (flicker / 3) % 2 == 0 ? 1 : 0;
        g.drawCenteredString(font, state, dx + dw / 2 + jitter, dy + 4, color);
        for (int ly = dy; ly < dy + dh; ly += 2) g.fill(dx, ly, dx + dw, ly + 1, 0x18000000);

        // Рубильники
        int hover = feed() ? -1 : slotAt(mouseX, mouseY);
        int by = bodyY();
        for (int i = 0; i <= MAIN_SLOT; i++) {
            boolean main = i == MAIN_SLOT;
            boolean on = main ? feed() : feed() || LabBreakers.isClosed(seq, i + 1);
            boolean hot = !feed() && (i == hover || i == sel);
            int bx = slotX(i), bw = slotW(i);
            int lamp = on ? TEXT : main && tripped() ? RED : LAMP_OFF;
            int lx = bx + bw / 2 - 4;
            g.fill(lx - 1, by - 9, lx + 9, by - 3, FRAME);
            g.fill(lx, by - 8, lx + 8, by - 4, lamp);
            g.fill(bx - 1, by - 1, bx + bw + 1, by + SH + 1, hot ? COLD : FRAME);
            g.fill(bx, by, bx + bw, by + SH, PLATE);
            int sx = bx + bw / 2 - 2;
            g.fill(sx, by + 5, sx + 4, by + SH - 5, GLASS);
            int hy = on ? by + 5 : by + SH - 5 - HANDLE;
            g.fill(bx + 4, hy, bx + bw - 4, hy + HANDLE, hot ? TEXT : HANDLE_C);
            g.fill(bx + 4, hy + HANDLE - 1, bx + bw - 4, hy + HANDLE, SHADOW);
            String label = main ? Component.translatable("hypersomnia.lab.panel.main").getString() : String.valueOf(i + 1);
            g.drawCenteredString(font, label, bx + bw / 2, by + SH + 5, hot ? TEXT : COLD);
        }
        int div = slotX(MAIN_SLOT) - DIV / 2;
        g.fill(div, by - 10, div + 1, by + SH + 14, FRAME);

        // Подсказка или блокировка
        g.fill(x + 12, y + PH - 22, x + PW - 12, y + PH - 21, FRAME);
        String foot = Component.translatable(feed() ? "hypersomnia.lab.panel.locked" : "hypersomnia.lab.panel.hint").getString();
        g.drawCenteredString(font, foot, x + PW / 2, y + PH - 15, COLD);

        if (flicker > 0 && (flicker / 3) % 2 == 0) g.fill(x, y, x + PW, y + PH, 0x58000000); // короткое «проседание» при выбивании
    }

    /** Отправить рубильник на сервер (0 = MAIN FEED). После включения питания щит заблокирован, запросы не шлются. */
    private void act(int slot) {
        if (feed() || slot < 0 || slot > MAIN_SLOT) return;
        int breaker = slot == MAIN_SLOT ? LabBreakers.MAIN : slot + 1;
        if (breaker == LabBreakers.MAIN) sentMain = true;
        PacketDistributor.sendToServer(new LabPanelActionPayload(breaker));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int i = slotAt(mx, my);
            if (i >= 0) {
                act(i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= GLFW.GLFW_KEY_1 && key < GLFW.GLFW_KEY_1 + LabTuning.FEEDERS) {
            act(key - GLFW.GLFW_KEY_1);
            return true;
        }
        if (key >= GLFW.GLFW_KEY_KP_1 && key < GLFW.GLFW_KEY_KP_1 + LabTuning.FEEDERS) {
            act(key - GLFW.GLFW_KEY_KP_1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_M) {
            act(MAIN_SLOT);
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            int step = key == GLFW.GLFW_KEY_LEFT ? -1 : 1;
            sel = sel < 0 ? (step > 0 ? 0 : MAIN_SLOT) : Math.floorMod(sel + step, MAIN_SLOT + 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            act(sel);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
