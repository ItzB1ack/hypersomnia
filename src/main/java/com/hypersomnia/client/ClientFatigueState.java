package com.hypersomnia.client;

import com.hypersomnia.FatigueTuning;
import com.hypersomnia.network.FatigueSyncPayload;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Состояние усталости на клиенте. Намеренно не импортирует клиентские классы Minecraft,
 * чтобы ссылка на handleSync из основного класса безопасно грузилась и на сервере.
 */
public final class ClientFatigueState {
    private ClientFatigueState() {}

    private static final RandomSource RANDOM = RandomSource.create();

    public static int fatigue;
    public static boolean collapsing;
    private static int collapseAge;

    private static int blackoutAge = -1;
    private static int blackoutDuration = 1;
    private static int ticksUntilBlackout = -1;

    // ---- «Клонит в сон»: виньетка и покачивание камеры ----
    private static int swayAge = -1;
    private static int swayDuration = 1;
    private static int ticksUntilSway = -1;
    private static float swayPhaseRoll;
    private static float swayPhaseYaw;
    private static float swayDir = 1f;

    public static void handleSync(FatigueSyncPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (payload.collapsing() && !collapsing) collapseAge = 0;
            fatigue = payload.fatigue();
            collapsing = payload.collapsing();
        });
    }

    public static float ratio() {
        return FatigueTuning.ratio(fatigue);
    }

    public static void reset() {
        fatigue = 0;
        collapsing = false;
        collapseAge = 0;
        blackoutAge = -1;
        ticksUntilBlackout = -1;
        swayAge = -1;
        ticksUntilSway = -1;
    }

    /** Вызывать раз в клиентский тик (не на паузе). */
    public static void tick() {
        collapseAge = collapsing ? collapseAge + 1 : 0;

        float r = ratio();
        tickSway(r);

        if (r < FatigueTuning.BLACKOUT_START) {
            blackoutAge = -1;
            ticksUntilBlackout = -1;
            return;
        }
        float p = FatigueTuning.progress(r, FatigueTuning.BLACKOUT_START);

        if (blackoutAge >= 0) {
            blackoutAge++;
            if (blackoutAge >= blackoutDuration) {
                blackoutAge = -1;
                scheduleNext(p);
            }
            return;
        }
        if (ticksUntilBlackout < 0) scheduleNext(p);
        if (--ticksUntilBlackout <= 0) {
            blackoutDuration = Mth.lerpInt(p, 30, 80) + RANDOM.nextInt(10);
            blackoutAge = 0;
        }
    }

    private static void tickSway(float r) {
        if (r < FatigueTuning.DROWSY_START) {
            swayAge = -1;
            ticksUntilSway = -1;
            return;
        }
        float p = FatigueTuning.progress(r, FatigueTuning.DROWSY_START);

        if (swayAge >= 0) {
            if (++swayAge >= swayDuration) {
                swayAge = -1;
                scheduleSway(p);
            }
            return;
        }
        if (ticksUntilSway < 0) scheduleSway(p);
        if (--ticksUntilSway <= 0) {
            swayDuration = Mth.lerpInt(p, 70, 110) + RANDOM.nextInt(20);
            swayAge = 0;
            swayPhaseRoll = RANDOM.nextFloat() * Mth.TWO_PI;
            swayPhaseYaw = RANDOM.nextFloat() * Mth.TWO_PI;
            swayDir = RANDOM.nextBoolean() ? 1f : -1f;
        }
    }

    private static void scheduleSway(float p) {
        // От ~25 секунд между покачиваниями до ~7 секунд, разброс +-30%
        int base = Mth.lerpInt(p, 500, 140);
        ticksUntilSway = (int) (base * (0.7f + RANDOM.nextFloat() * 0.6f));
    }

    private static void scheduleNext(float p) {
        // От ~90 секунд между потемнениями до ~10 секунд, с разбросом +-30%
        int base = Mth.lerpInt(p, 1800, 200);
        ticksUntilBlackout = (int) (base * (0.7f + RANDOM.nextFloat() * 0.6f));
    }

    /** 0..1: насколько «закрыты веки» из-за случайного потемнения. */
    public static float blackoutIntensity(float partialTick) {
        if (blackoutAge < 0) return 0f;
        float t = Mth.clamp((blackoutAge + partialTick) / blackoutDuration, 0f, 1f);
        float v;
        if (t < 0.4f) v = t / 0.4f;               // закрываются
        else if (t < 0.6f) v = 1f;                // держатся
        else v = 1f - (t - 0.6f) / 0.4f;          // открываются
        return v * v * (3f - 2f * v);             // сглаживание
    }

    /** 0..1: затемнение при принудительном падении (за 3 секунды до полной темноты). */
    public static float collapseIntensity(float partialTick) {
        if (!collapsing) return 0f;
        return Mth.clamp((collapseAge + partialTick) / 60f, 0f, 1f);
    }

    // ------------------------------------------------------------------
    // Виньетка и покачивание
    // ------------------------------------------------------------------

    /** 0..1: прогресс текущего приступа сонливости (-1, если приступа нет). */
    private static float swayT(float partialTick) {
        if (swayAge < 0) return -1f;
        return Mth.clamp((swayAge + partialTick) / swayDuration, 0f, 1f);
    }

    /** 0..1: плавная огибающая приступа (0 -> 1 -> 0). */
    private static float swayEnvelope(float partialTick) {
        float t = swayT(partialTick);
        if (t < 0f) return 0f;
        float s = Mth.sin(t * Mth.PI);
        return s * s;
    }

    /** Сила виньетки 0..1: постоянная при средней усталости, плюс сгущение во время приступа. */
    public static float vignetteIntensity(float partialTick) {
        float r = ratio();
        if (r < FatigueTuning.DROWSY_START) return 0f;
        float p = FatigueTuning.progress(r, FatigueTuning.DROWSY_START);
        float base = Mth.lerp(p, 0.30f, 0.80f);
        return Mth.clamp(base + swayEnvelope(partialTick) * 0.25f, 0f, 1f);
    }

    /** Амплитуда покачивания 0..1: растёт вместе с усталостью. */
    private static float swayStrength() {
        float r = ratio();
        if (r < FatigueTuning.DROWSY_START) return 0f;
        return Mth.lerp(FatigueTuning.progress(r, FatigueTuning.DROWSY_START), 0.5f, 1f);
    }

    /** Наклон головы вниз в градусах: медленно «клюёт» вниз, затем резко вскидывается. */
    public static float swayPitch(float partialTick) {
        float t = swayT(partialTick);
        if (t < 0f) return 0f;
        float nod;
        if (t < 0.65f) {
            float k = t / 0.65f;
            nod = k * k;                         // медленно падает
        } else if (t < 0.78f) {
            nod = 1f - (t - 0.65f) / 0.13f;      // резкий рывок вверх
        } else {
            nod = 0f;
        }
        return nod * 6.0f * swayStrength();
    }

    /** Крен камеры в градусах. */
    public static float swayRoll(float partialTick) {
        float t = swayT(partialTick);
        if (t < 0f) return 0f;
        float wave = Mth.sin(t * Mth.TWO_PI * 1.5f + swayPhaseRoll);
        return wave * swayEnvelope(partialTick) * 4.0f * swayStrength() * swayDir;
    }

    /** Лёгкое рыскание влево-вправо в градусах. */
    public static float swayYaw(float partialTick) {
        float t = swayT(partialTick);
        if (t < 0f) return 0f;
        float wave = Mth.sin(t * Mth.TWO_PI * 0.8f + swayPhaseYaw);
        return wave * swayEnvelope(partialTick) * 2.0f * swayStrength();
    }
}
