package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.scene.SleepSceneTuning;
import com.hypersomnia.voice.VoiceLine;
import com.hypersomnia.voice.VoiceTuning;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.minecraft.client.gui.screens.PauseScreen;

/**
 * Тик сцены, давящая тишина и движение камеры.
 * Собственных звуков у сцены НЕТ: страх делает картинка. Наоборот, всё, что звучало, обрывается,
 * а новые звуки не проигрываются до конца сцены и ещё некоторое время после неё.
 * Единственное исключение — его голос, когда игрок прячется в меню паузы (FragmentVoice, VoiceLine.PAUSE_*).
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class SleepSceneClientEvents {
    private SleepSceneClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        // Меню паузы во время сцены: глаз, второй, толпа глаз, его голос — и он закрывает меню сам
        // (это событие приходит и в паузе)
        if (SleepSceneState.active() && mc.screen instanceof PauseScreen) {
            int t = ++SleepSceneState.pauseTicks;
            pauseTickNanos = System.nanoTime();
            if (t == SleepSceneTuning.PAUSE_VOICE_TICKS) {
                FragmentVoice.say(VoiceLine.pause(SleepSceneState.scoldCount), null, VoiceTuning.VOLUME_NEAR,
                        VoiceTuning.SHAKE_PAUSE, true, true);
                SleepSceneState.pauseCloseAt = Math.max(SleepSceneTuning.PAUSE_CLOSE_TICKS,
                        t + FragmentVoice.speechLeft() + SleepSceneTuning.PAUSE_AFTER_VOICE_TICKS);
            }
            int closeAt = SleepSceneState.pauseCloseAt > 0 ? SleepSceneState.pauseCloseAt : SleepSceneTuning.PAUSE_CLOSE_MAX_TICKS;
            if (t >= Math.min(closeAt, SleepSceneTuning.PAUSE_CLOSE_MAX_TICKS)) {
                SleepSceneState.pauseTicks = 0;
                SleepSceneState.pauseCloseAt = 0;
                SleepSceneState.scoldTicks = SleepSceneTuning.SCOLD_SHOW_TICKS;
                SleepSceneState.scoldCount++;
                // Игрок лежит в кровати: возвращаем экран кровати, а не пустой экран
                mc.setScreen(mc.player != null && mc.player.isSleeping() ? new SceneBedScreen() : null);
            }
        } else {
            SleepSceneState.pauseTicks = 0;
            SleepSceneState.pauseCloseAt = 0;
        }
        if (mc.isPaused()) return;
        SleepSceneState.tick();

        // Первый тик сцены: обрываем всё, что уже играет (ночной фон, музыка, шаги)
        if (SleepSceneState.ticks == 1) {
            mc.getSoundManager().stop();
            // Экран кровати уже открыт к этому моменту: подменяем его на блокирующий
            if (mc.screen instanceof InBedChatScreen && !(mc.screen instanceof SceneBedScreen)) {
                mc.setScreen(new SceneBedScreen());
            }

            // После закрытия меню паузы (ESC в нём) возвращаем экран кровати, пока игрок лежит
            if (SleepSceneState.active() && mc.screen == null && mc.player != null && mc.player.isSleeping()) {
                mc.setScreen(new SceneBedScreen());
            }
        }
    }

    /** Давящая тишина: пока сцена идёт (и недолго после) любой новый звук просто не воспроизводится — кроме его голоса. */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (SleepSceneState.silent() && !FragmentVoice.isVoice(event.getOriginalSound())) {
            event.setSound(null);
        }
    }

    /** Тик меню паузы пришёл тогда-то: между тиками глаза двигаются плавно (в паузе partialTick стоит). */
    private static long pauseTickNanos;

    /** Пока игрок сидит в меню паузы, поверх него проступают глаза (в меню игра стоит, тики идут от экрана). Субтитр рисует FragmentVoice. */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!SleepSceneState.active() || !(event.getScreen() instanceof PauseScreen)) return;
        float t = SleepSceneState.pauseTicks + Mth.clamp((System.nanoTime() - pauseTickNanos) / 5.0e7f, 0f, 1f);
        SleepSceneOverlay.drawPauseEyes(event.getGuiGraphics(), event.getScreen().width, event.getScreen().height,
                t, event.getMouseX(), event.getMouseY());
    }

    /** Экран кровати во время сцены заменяем на «не могу пошевелиться» (ESC и кнопка не встают). */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (SleepSceneState.active()
                && event.getNewScreen() instanceof InBedChatScreen
                && !(event.getNewScreen() instanceof SceneBedScreen)) {
            event.setNewScreen(new SceneBedScreen());
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SleepSceneState.reset();
    }

    /**
     * Камера без тряски. Вместо неё: очень медленное дыхание, едва заметный тремор к концу
     * и «вздрагивание» в миг, когда фигура оказывается ближе (то, что раньше делал звуковой удар).
     * В последние тики кадр замирает полностью.
     */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!SleepSceneState.active()) return;

        float t = SleepSceneState.bodyTicks() + (float) event.getPartialTick();
        int body = SleepSceneTuning.BODY_TICKS;
        if (t >= body - SleepSceneTuning.FREEZE_TICKS) return; // заморозка: камера мертва

        float p = Mth.clamp(t / body, 0f, 1f);
        double tm = System.currentTimeMillis() / 1000.0;

        float yaw = 0f;
        float pitch = 0f;
        float roll = 0f;

        // Дыхание: период ~4.5 с, чуть глубже к концу
        float breath = (float) Math.sin(tm * 1.4) * (0.25f + 0.35f * p);
        roll += breath * 0.6f;
        pitch += breath * 0.5f;

        // Тремор: почти не виден, но «живой»
        float tremor = 0.12f * p * p;
        yaw += (float) Math.sin(tm * 37.0) * tremor;
        pitch += (float) Math.sin(tm * 29.0 + 1.0) * tremor;

        // Вздрагивание при каждом скачке фигуры: короткий затухающий рывок
        for (int i = 1; i < SleepSceneOverlay.STAGE_AT.length; i++) {
            float dt = t - SleepSceneOverlay.STAGE_AT[i];
            if (dt >= 0f && dt < 14f) {
                float k = (float) Math.exp(-dt / 4.0);
                float side = ((SleepSceneState.seed >> i) & 1) == 0 ? 1f : -1f;
                roll += side * k * 2.2f;
                pitch += k * 1.1f;
                yaw += side * k * 0.6f;
            }
        }

        event.setYaw(event.getYaw() + yaw);
        event.setPitch(event.getPitch() + pitch);
        event.setRoll(event.getRoll() + roll);
    }
}
