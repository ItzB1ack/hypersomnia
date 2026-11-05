package com.hypersomnia.client;

import com.hypersomnia.lab.LabTuning;
import com.hypersomnia.network.LabLockCodePayload;
import com.hypersomnia.network.LabLockPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Кодовая панель замка в аналоговом стиле. Ширина и высота — под язык: части заголовка «A / B» идут
 * отдельными строками (длинная часть переносится), панель шире минимума, только если иначе не влезает
 * заголовок или ответ на стекле. Число цифр — LabTuning.lockDigits; панель с черновиком (lockKeepsInput)
 * открывается с набранным ранее, не стирает ввод после отказа и при закрытии отдаёт ввод серверу.
 */
public final class LabLockScreen extends Screen {
    private static final int PW_MIN = 116, PW_MAX = 168, PH_BASE = 164, BW = 30, BH = 20, GAP = 4;
    /** Поле от края панели до текста и стекла; шаг строк заголовка; запас ответа на стекле с каждой стороны. */
    private static final int PAD = 10, LINE = 10, GLASS_MARGIN = 4;
    private static final String[] KEYS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "<"};
    private static final int VEIL = 0xD003050A;
    private static final int FRAME = 0xFF1E2A34;
    private static final int BODY = 0xFF0A0E12;
    private static final int GLASS = 0xFF05080B;
    private static final int TEXT = 0xFFDDE6EC;
    private static final int COLD = 0xFF4A5E70;
    private static final int RED = 0xFF8A2030;

    private enum Mode { INPUT, WAIT, DENIED, INCOMPLETE, NOPOWER, GRANTED }

    /** Ответы на стекле, по ним считается ширина панели. */
    private static final String[] REPLIES = {"hypersomnia.lab.lock.denied", "hypersomnia.lab.lock.granted",
            "hypersomnia.lab.lock.incomplete", "hypersomnia.lab.lock.nopower"};

    private final int lock;
    private final int digits;
    private final boolean keep;
    private final StringBuilder input = new StringBuilder();
    private Mode mode = Mode.INPUT;
    private int timer;
    private List<FormattedCharSequence> head = List.of();
    private int pw = PW_MIN, ph = PH_BASE, shift;

    private LabLockScreen(int lock, String draft) {
        super(Component.translatableWithFallback("hypersomnia.lab.lock." + lock, "CRADLE / ACCESS"));
        this.lock = lock;
        this.digits = LabTuning.lockDigits(lock);
        this.keep = LabTuning.lockKeepsInput(lock);
        if (keep) for (char c : draft.toCharArray()) if (c >= '0' && c <= '9' && input.length() < digits) input.append(c);
    }

    /** Вызывать только на клиенте. draft — черновик с сервера (для OPEN). */
    public static void receive(int lock, int state, String draft) {
        Minecraft mc = Minecraft.getInstance();
        if (state == LabLockPayload.OPEN) {
            mc.setScreen(new LabLockScreen(lock, draft));
        } else if (mc.screen instanceof LabLockScreen s && s.lock == lock) {
            s.result(state);
        }
    }

    private void result(int state) {
        mode = switch (state) {
            case LabLockPayload.GRANTED -> Mode.GRANTED;
            case LabLockPayload.INCOMPLETE -> Mode.INCOMPLETE;
            case LabLockPayload.NOPOWER -> Mode.NOPOWER;
            default -> Mode.DENIED;
        };
        timer = mode == Mode.GRANTED ? LabTuning.KEYPAD_GRANTED_TICKS : LabTuning.KEYPAD_DENIED_TICKS;
    }

    /** Отказ любого рода: красный ответ на стекле, потом снова ввод. */
    private boolean refused() {
        return mode == Mode.DENIED || mode == Mode.INCOMPLETE || mode == Mode.NOPOWER;
    }

    /** Черновик уходит на сервер при любом закрытии, кроме успеха (без соединения — некуда). */
    @Override
    public void removed() {
        if (keep && mode != Mode.GRANTED && minecraft != null && minecraft.getConnection() != null)
            PacketDistributor.sendToServer(new LabLockCodePayload(lock, input.toString(), false));
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        if (timer > 0 && --timer == 0) {
            if (mode == Mode.GRANTED) {
                onClose();
            } else if (refused()) {
                if (!keep) input.setLength(0);
                mode = Mode.INPUT;
            }
        }
    }

    @Override
    protected void init() {
        String[] parts = title.getString().split(" / ");
        int need = 0;
        for (String part : parts) need = Math.max(need, font.width(part) + 2 * PAD);
        for (String key : REPLIES)
            need = Math.max(need, font.width(Component.translatable(key)) + 2 * PAD + 2 * GLASS_MARGIN);
        pw = Math.max(PW_MIN, Math.min(PW_MAX, need));
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String part : parts) lines.addAll(font.split(Component.literal(part), pw - 2 * PAD));
        head = lines;
        shift = Math.max(0, lines.size() - 1) * LINE;
        ph = PH_BASE + shift;
    }

    private int px() { return (width - pw) / 2; }
    private int py() { return (height - ph) / 2; }
    private int keyX(int c) { return px() + (pw - (3 * BW + 2 * GAP)) / 2 + c * (BW + GAP); }
    private int keyY(int r) { return py() + 62 + shift + r * (BH + GAP); }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        g.fill(0, 0, width, height, VEIL);
        int x = px(), y = py();
        g.fill(x - 1, y - 1, x + pw + 1, y + ph + 1, FRAME);
        g.fill(x, y, x + pw, y + ph, BODY);
        for (int i = 0; i < head.size(); i++) g.drawCenteredString(font, head.get(i), x + pw / 2, y + 8 + i * LINE, COLD);

        int dx = x + PAD, dy = y + 26 + shift, dw = pw - 2 * PAD, dh = 26;
        g.fill(dx, dy, dx + dw, dy + dh, GLASS);
        String shown;
        int color = TEXT;
        if (refused()) {
            shown = Component.translatable(mode == Mode.INCOMPLETE ? "hypersomnia.lab.lock.incomplete"
                    : mode == Mode.NOPOWER ? "hypersomnia.lab.lock.nopower" : "hypersomnia.lab.lock.denied").getString();
            color = RED;
        } else if (mode == Mode.GRANTED) {
            // ответ панели после открытия: свой у замка (hypersomnia.lab.lock.granted.<id>), иначе общий
            shown = Component.translatableWithFallback("hypersomnia.lab.lock.granted." + lock,
                    Component.translatable("hypersomnia.lab.lock.granted").getString()).getString();
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < digits; i++) sb.append(i < input.length() ? input.charAt(i) : '_').append(' ');
            shown = sb.toString().trim();
        }
        int jitter = refused() && (timer / 2) % 2 == 0 ? 1 : 0;
        g.drawCenteredString(font, shown, dx + dw / 2 + jitter, dy + 9, color);
        for (int ly = dy; ly < dy + dh; ly += 2) g.fill(dx, ly, dx + dw, ly + 1, 0x18000000);

        for (int i = 0; i < KEYS.length; i++) {
            int bx = keyX(i % 3), by = keyY(i / 3);
            boolean hot = mode == Mode.INPUT && mouseX >= bx && mouseX < bx + BW && mouseY >= by && mouseY < by + BH;
            g.fill(bx - 1, by - 1, bx + BW + 1, by + BH + 1, FRAME);
            g.fill(bx, by, bx + BW, by + BH, hot ? 0xFF1E2A34 : GLASS);
            g.drawCenteredString(font, KEYS[i], bx + BW / 2, by + 6, hot ? TEXT : COLD);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && mode == Mode.INPUT) {
            for (int i = 0; i < KEYS.length; i++) {
                int bx = keyX(i % 3), by = keyY(i / 3);
                if (mx >= bx && mx < bx + BW && my >= by && my < by + BH) {
                    press(KEYS[i]);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (mode == Mode.INPUT && c >= '0' && c <= '9') {
            press(String.valueOf(c));
            return true;
        }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (mode == Mode.INPUT && key == 259) { // Backspace
            press("<");
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void press(String k) {
        if (k.equals("C")) {
            input.setLength(0);
        } else if (k.equals("<")) {
            if (input.length() > 0) input.setLength(input.length() - 1);
        } else if (input.length() < digits) {
            input.append(k);
            if (input.length() == digits) {
                mode = Mode.WAIT;
                PacketDistributor.sendToServer(new LabLockCodePayload(lock, input.toString(), true));
            }
        }
    }
}
