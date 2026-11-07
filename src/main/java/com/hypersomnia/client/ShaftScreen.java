package com.hypersomnia.client;

import com.hypersomnia.network.ShaftOpenPayload;
import com.hypersomnia.network.ShaftSelectPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Выбор остановки: сервер отдельно проверяет положение игрока и допустимость этажа. */
public final class ShaftScreen extends Screen {
    private final int current;
    private ShaftScreen(int current) { super(Component.translatable("hypersomnia.nightmare.shaft.title")); this.current = current; }
    public static void receive(ShaftOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new ShaftScreen(payload.current())));
    }
    @Override protected void init() {
        int[] stops = {0, 6, 7, 8};
        for (int i = 0; i < stops.length; i++) {
            int stop = stops[i];
            String label = stop == 0 ? "0" : "B" + stop;
            Button button = addRenderableWidget(Button.builder(Component.literal(label), b -> {
                PacketDistributor.sendToServer(new ShaftSelectPayload(stop)); onClose();
            }).bounds(width / 2 - 60, height / 2 - 46 + i * 24, 120, 20).build());
            button.active = stop != current;
        }
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC0000000);
        g.drawCenteredString(font, title, width / 2, height / 2 - 64, 0xFFDDE6EC);
        super.render(g, mx, my, pt);
    }
}
