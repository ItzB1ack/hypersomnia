package com.hypersomnia.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Экран картотечного шкафа: обычный ChestMenu (ряд = ящик), нарисованный в палитре лаборатории
 * вместо ванильной текстуры сундука. Логика ячеек и Shift-перенос — ванильные.
 */
public final class CabinetScreen extends AbstractContainerScreen<ChestMenu> {
    private static final int VEIL = 0xD003050A;
    private static final int FRAME = 0xFF1E2A34;
    private static final int BODY = 0xFF0A0E12;
    private static final int PLATE = 0xFF10161C;
    private static final int GLASS = 0xFF05080B;
    private static final int COLD = 0xFF4A5E70;

    private final int rows;

    public CabinetScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.rows = menu.getRowCount();
        this.imageHeight = 114 + rows * 18;
        this.inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.render(g, mouseX, mouseY, pt);
        renderTooltip(g, mouseX, mouseY);
    }

    /** Без размытия мира: та же вуаль, что у щита и замков. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        g.fill(0, 0, width, height, VEIL);
        renderBg(g, pt, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, FRAME);
        g.fill(x, y, x + imageWidth, y + imageHeight, BODY);
        for (int[] c : new int[][]{{x + 3, y + 3}, {x + imageWidth - 5, y + 3}, {x + 3, y + imageHeight - 5}, {x + imageWidth - 5, y + imageHeight - 5}})
            g.fill(c[0], c[1], c[0] + 2, c[1] + 2, FRAME); // винты
        // Ящики: пластина под рядами шкафа, отдельно — инвентарь игрока.
        g.fill(x + 5, y + 16, x + imageWidth - 5, y + 18 + rows * 18 + 2, PLATE);
        int sep = y + inventoryLabelY - 4;
        g.fill(x + 5, sep, x + imageWidth - 5, sep + 1, FRAME);
        for (Slot slot : menu.slots) {
            int sx = x + slot.x - 1, sy = y + slot.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, FRAME);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, GLASS);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, COLD, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, COLD, false);
    }
}
