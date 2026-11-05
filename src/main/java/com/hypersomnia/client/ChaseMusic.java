package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModSounds;
import com.hypersomnia.chase.ChaseTuning;
import com.hypersomnia.network.ChasePayload;
import com.hypersomnia.voice.VoiceTuning;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * Музыка погони (только клиент), категория «Враждебные существа». Три петли «в голове», громкость каждой плавно идёт
 * к своей цели: гул (chase_drone) — всю погоню, тише, пока он только стоит на следе; подъём (chase_rise) — громче,
 * чем ближе он; бормотание вокруг головы (chase_near) — когда он в нескольких блоках. Фигура шепчет (shard_whisper)
 * со своего места, вплотную — у самой головы. Пока погоня, ванильная музыка молчит.
 * Конец: прогнал взглядом — обрыв в тишину; поймал — обрыв (дальше чёрный кадр); ушёл или упёрся — затухание.
 * Звуки — tools/make_chase_sounds.py. Вызовы: ObserverClient (begin, track, end), ChaseClient (caught).
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class ChaseMusic {
    private ChaseMusic() {}

    private static final RandomSource RANDOM = RandomSource.create();
    /** Если петлю оборвали снаружи (кто-то заглушил звуки мира), запускаем заново не чаще, чем раз в столько тиков. */
    private static final int RESTART_TICKS = 20;

    private static Loop drone;
    private static Loop rise;
    private static Loop near;
    /** Идёт погоня: петли держат цели, фигура шепчет, ванильная музыка молчит. */
    private static boolean active;
    private static int whisperIn;
    private static int restartIn;
    private static float droneWant;
    private static float riseWant;
    private static float nearWant;

    /** Погоня началась (он стоит на следе): ванильная музыка обрывается, гул нарастает. */
    public static void begin(Minecraft mc) {
        active = true;
        mc.getMusicManager().stopPlaying();
        whisperIn = ChaseTuning.WHISPER_FIRST_TICKS;
        droneWant = ChaseTuning.MUSIC_DRONE_WARN;
        riseWant = 0f;
        nearWant = 0f;
        restartIn = 0;
        apply(mc);
    }

    /** Каждый тик погони: фаза ChasePayload.PH_*, где он (ноги) и расстояние до него (блоки). */
    public static void track(Minecraft mc, LocalPlayer player, int phase, Vec3 at, double dist) {
        if (!active) return;
        float close = (float) Mth.clamp((ChaseTuning.MUSIC_RISE_FAR - dist)
                / (ChaseTuning.MUSIC_RISE_FAR - ChaseTuning.MUSIC_RISE_NEAR), 0.0, 1.0);
        float touch = (float) Mth.clamp(1.0 - dist / ChaseTuning.MUSIC_NEAR_DIST, 0.0, 1.0);
        switch (phase) {
            case ChasePayload.PH_WARN -> {
                droneWant = ChaseTuning.MUSIC_DRONE_WARN;
                riseWant = 0f;
                nearWant = 0f;
            }
            case ChasePayload.PH_BLOCKED -> {
                droneWant = ChaseTuning.MUSIC_DRONE_BLOCKED;
                riseWant = ChaseTuning.MUSIC_RISE_BLOCKED;
                nearWant = 0f;
            }
            case ChasePayload.PH_RUN -> {
                droneWant = ChaseTuning.MUSIC_DRONE_RUN;
                riseWant = ChaseTuning.MUSIC_RISE_MAX * close * close;
                nearWant = ChaseTuning.MUSIC_NEAR_MAX * touch;
            }
            default -> {
                return;
            }
        }
        apply(mc);
        if (--whisperIn <= 0) {
            whisperIn = Mth.randomBetweenInclusive(RANDOM, ChaseTuning.WHISPER_MIN_TICKS, ChaseTuning.WHISPER_MAX_TICKS);
            // Пока он говорит (и чуть после), не шепчет: реплику должно быть слышно
            if (!FragmentVoice.speaking(VoiceTuning.WHISPER_GAP)) whisper(mc, player, at, dist);
        }
    }

    /** Поймал: всё обрывается сразу (тишину и удар сердца делает ChaseClient). */
    public static void caught() {
        active = false;
        stopAll();
    }

    /** Погоня кончилась: abrupt — обрыв в тишину, иначе затухание за MUSIC_FADE_OUT_TICKS. Повторный вызов безвреден
     *  (затухание при этом не начинается заново: громкость уже ниже, шаг — от неё). */
    public static void end(boolean abrupt) {
        active = false;
        droneWant = 0f;
        riseWant = 0f;
        nearWant = 0f;
        if (abrupt) {
            stopAll();
            return;
        }
        for (Loop l : new Loop[]{drone, rise, near}) {
            if (l != null) l.fadeOut(ChaseTuning.MUSIC_FADE_OUT_TICKS);
        }
    }

    /** Звучит ли что-то от погони (пока да — ванильная музыка не начинается). */
    public static boolean playing() {
        return active || drone != null || rise != null || near != null;
    }

    /** Цели громкости -> петли; петли, которых нет (или их оборвали), запускаются с нуля. */
    private static void apply(Minecraft mc) {
        float down = 1f / ChaseTuning.MUSIC_FALL_TICKS;
        boolean may = restartIn <= 0;
        drone = keep(mc, drone, ModSounds.CHASE_DRONE.get(), droneWant, down, may);
        rise = keep(mc, rise, ModSounds.CHASE_RISE.get(), riseWant, down, may);
        near = keep(mc, near, ModSounds.CHASE_NEAR.get(), nearWant, down, may);
    }

    private static Loop keep(Minecraft mc, Loop loop, SoundEvent event, float want, float down, boolean may) {
        if (loop != null && (loop.isStopped() || !mc.getSoundManager().isActive(loop))) {
            loop.kill();
            loop = null;
        }
        if (loop == null) {
            if (want <= 0f || !may) return null;
            loop = new Loop(event);
            mc.getSoundManager().play(loop);
            if (!mc.getSoundManager().isActive(loop)) {
                restartIn = RESTART_TICKS; // не дали зазвучать (глушат звуки мира) — попробуем позже
                return null;
            }
        }
        loop.aim(want, down);
        return loop;
    }

    /** Шёпот: с места фигуры (на высоте головы), а вплотную — у самой головы игрока, с случайной стороны. */
    private static void whisper(Minecraft mc, LocalPlayer player, Vec3 at, double dist) {
        if (mc.level == null) return;
        double x = at.x;
        double y = at.y + 1.6;
        double z = at.z;
        float volume = ChaseTuning.WHISPER_VOLUME;
        if (dist < ChaseTuning.WHISPER_HEAD_DIST) {
            float a = RANDOM.nextFloat() * Mth.TWO_PI;
            Vec3 eye = player.getEyePosition();
            x = eye.x + Mth.cos(a) * ChaseTuning.WHISPER_HEAD_OFFSET;
            y = eye.y + (RANDOM.nextFloat() - 0.5f) * 0.4f;
            z = eye.z + Mth.sin(a) * ChaseTuning.WHISPER_HEAD_OFFSET;
            volume = ChaseTuning.WHISPER_HEAD_VOLUME;
        }
        mc.level.playLocalSound(x, y, z, ModSounds.SHARD_WHISPER.get(), SoundSource.HOSTILE,
                volume, 0.9f + RANDOM.nextFloat() * 0.15f, false);
    }

    private static void stopAll() {
        for (Loop l : new Loop[]{drone, rise, near}) {
            if (l != null) {
                l.kill();
                Minecraft.getInstance().getSoundManager().stop(l);
            }
        }
        drone = null;
        rise = null;
        near = null;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            if (playing()) reset();
            return;
        }
        if (mc.isPaused()) return;
        if (restartIn > 0) restartIn--;
        if (active && (!mc.player.isAlive() || SleepSceneState.silent())) end(true);
        // Затухшие после конца петли отпускаем
        if (!active) {
            if (drone != null && (drone.isStopped() || !mc.getSoundManager().isActive(drone))) drone = null;
            if (rise != null && (rise.isStopped() || !mc.getSoundManager().isActive(rise))) rise = null;
            if (near != null && (near.isStopped() || !mc.getSoundManager().isActive(near))) near = null;
        }
    }

    /** Пока погоня (и её затухание), новая ванильная музыка не начинается. */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance s = event.getSound();
        if (s != null && s.getSource() == SoundSource.MUSIC && playing()) event.setSound(null);
    }

    public static void reset() {
        active = false;
        stopAll();
        droneWant = 0f;
        riseWant = 0f;
        nearWant = 0f;
        whisperIn = 0;
        restartIn = 0;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    /** Петля «в голове»: без затухания по расстоянию, начинает с нуля, громкость тянется к цели сама каждый тик. */
    private static final class Loop extends AbstractTickableSoundInstance {
        private float want;
        private float up = 1f / ChaseTuning.MUSIC_RISE_TICKS;
        private float down = 1f / ChaseTuning.MUSIC_FALL_TICKS;

        Loop(SoundEvent event) {
            super(event, SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.volume = 0f;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.x = 0.0;
            this.y = 0.0;
            this.z = 0.0;
        }

        void aim(float target, float fall) {
            want = target;
            down = fall;
        }

        /** К нулю ровно за ticks тиков с любой громкости. */
        void fadeOut(int ticks) {
            want = 0f;
            down = Math.max(volume, 0.01f) / ticks;
        }

        void kill() {
            stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            float d = want - volume;
            volume += d > 0f ? Math.min(d, up) : Math.max(d, -down);
            if (want <= 0f && volume <= 0.002f) stop();
        }
    }
}
