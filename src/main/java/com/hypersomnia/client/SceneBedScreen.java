package com.hypersomnia.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.PauseScreen;

/** Экран кровати во время сцены: встать нельзя ни ESC, ни кнопкой. */
public final class SceneBedScreen extends InBedChatScreen {

    @Override
    protected void init() {
        super.init();
        for (GuiEventListener c : children()) {
            if (c instanceof Button b) {
                b.setMessage(Component.translatableWithFallback("hypersomnia.scene.bed", "I can't move."));
                b.active = false;
            }
        }
    }

    /** ESC в ванильном экране кровати сам шлёт «встать». Перехватываем: вместо этого открываем меню паузы (его закроет осколок). */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && SleepSceneState.active()) {
            if (minecraft != null) minecraft.setScreen(new PauseScreen(true));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** ESC: во время сцены ничего не происходит. */
    @Override
    public void onClose() {
        if (!SleepSceneState.active()) super.onClose();
    }

    /** Сервер разбудил игрока (конец сцены): закрыть экран. */
    @Override
    public void tick() {
        super.tick();
        if (minecraft != null && minecraft.player != null && !minecraft.player.isSleeping()) {
            minecraft.setScreen(null);
        }
    }
}