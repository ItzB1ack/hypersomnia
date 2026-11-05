package com.hypersomnia.client;

import com.hypersomnia.FatigueTuning;
import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModSounds;
import com.hypersomnia.ObserverTuning;
import com.hypersomnia.chase.ChaseTuning;
import com.hypersomnia.client.ObserverDirector.Kind;
import com.hypersomnia.client.ObserverDirector.Outcome;
import com.hypersomnia.client.ObserverDirector.Plan;
import com.hypersomnia.network.ChasePayload;
import com.hypersomnia.network.ChaseRequestPayload;
import com.hypersomnia.network.ObserverSeenPayload;
import com.hypersomnia.trace.TraceTuning;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * Наблюдатель: отколовшаяся часть игрока. Клиентский «призрак» (в мир не добавляется).
 * Каждый эпизод начинает ObserverDirector; здесь только исполнение плана: поиск места, признак, показ, реакция, конец.
 * <p>
 * Правило взгляда одно на всех стадиях и во всех эпизодах: если на него смотреть, он исчезает, но чем выше стадия,
 * тем дольше его надо выдержать (ObserverTuning.gazeTicks: 0.2 с на стадии 1, 2.2 с на стадии 5); пока смотришь,
 * от него летит пыль. Реальный мир: появляется за краем поля зрения, в темноте; пока игрок не смотрит, делает шаги
 * ближе (со стадии 2). Мир снов (hypersomnia:dream): со стадии 4 ещё и идёт к игроку, пока тот смотрит.
 * Погоня (Mode.CHASE): решает и ведёт сервер (chase/ChaseHandler); здесь показ по пакетам ChasePayload, шаги, стук,
 * пыль и ChaseClient (сердце, виньетка, чёрный кадр). Погоня идёт и при открытом экране, и без усталости.
 * Пока открыт экран (кроме кровати и экрана смерти), остальные фигуры замирают и ничего не начинается.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class ObserverClient {
    private ObserverClient() {
    }

    private static final RandomSource RANDOM = RandomSource.create();
    private static final ResourceLocation DREAM_DIMENSION =
            ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "dream");
    /** Точка «взгляда» для проверки видимости места (как у игрока, до масштаба фигуры). */
    private static final double EYE_HEIGHT = 1.62;

    /** Что сейчас делает фигура. */
    /** FINAL — детская финала: стоит на метке (FinaleClientState) и смотрит на игрока, не исчезает. */
    /** CHASE — погоня: где он, решает сервер (ChasePayload), клиент только рисует и озвучивает. */
    private enum Mode { NONE, SIGN, GHOST, BED, STILL, DEATH, FINAL, CHASE }

    private record Spot(Vec3 feet, boolean covered, int variant) {}

    private static Mode mode = Mode.NONE;
    /** План текущего эпизода (GHOST, BED или STILL); null, когда эпизода нет. */
    private static Plan plan;
    private static ObserverGhost ghost;
    private static Vec3 pos = Vec3.ZERO;
    private static int age;
    private static int gazeTicks;
    private static int stepCooldown;
    private static boolean wasLooking;
    private static boolean covered;
    /** Слабый признак: место, где он встанет, ожидание до появления и сколько место уже в поле зрения. */
    private static Spot signSpot;
    private static Level signLevel;
    private static int signWait;
    private static int signOverdue;
    /** Силуэт эпизода неподвижности: столько тиков не исчезает, даже если на него смотрят. */
    private static int forcedVisibleTicks;
    private static boolean bedRolled;
    private static boolean wasAway;
    private static boolean wasDream;
    private static String searchFail = "no place";
    /** Сколько мест отвергнуто последним поиском и почему (только для причины отказа в отладке). */
    private static final int R_FLOOR = 0, R_DIST = 1, R_LIGHT = 2, R_RECENT = 3, R_VIEW = 4, R_SIGHT = 5;
    private static final String[] REJECT_NAMES = {"no floor", "distance", "light", "recent spot", "in view", "no sight"};
    private static final int[] rejects = new int[REJECT_NAMES.length];
    /** Отладка: появляется без усталости и при любом свете. */
    private static boolean debugForce;

    /** Погоня: номер на сервере (0 — нет), закончил ли её сервер сам, план в ожидании ответа и сколько ждём. */
    private static int chaseRun;
    private static boolean chaseEnded;
    private static Plan chasePending;
    private static int chaseWait;
    /** Номер погони, которую клиент уже попросил остановить (STOP уходит один раз). */
    private static int chaseStopped;
    private static int chasePhase;
    private static float chaseStare;
    /** Позиция прошлого тика: рендер ведёт фигуру плавно между пакетами. Путь до следующего шага, тики до стука. */
    private static Vec3 prevPos = Vec3.ZERO;
    private static double stepLeft;
    private static int knockIn;
    /** Отладка: следующий запрос погони — 1: без проверок стадии, дней и ночи; 2: ещё и не учебная (догоняет). */
    private static int chaseForce;

    // ------------------------------------------------------------------
    // Тик
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            clearAll();
            return;
        }
        if (mc.isPaused()) return;
        // Финал: в детской он стоит между кроватью и дверью и не мешает; после финала его нет совсем
        if (FinaleClientState.inside && isDream(mc) && player.isAlive()) {
            tickFinal(mc, player);
            return;
        }
        if (mode == Mode.FINAL) clearGhost();
        if (ObserverState.completed()) {
            if (mode != Mode.NONE || plan != null || ghost != null) clearAll();
            return;
        }
        ObserverScare.tick();
        boolean dream = isDream(mc);
        boolean busy = isBusy(mc);
        boolean scene = SleepSceneState.active() || SleepSceneState.silent();
        boolean tired = isTired(player, dream);
        ObserverDirector.tick(pressureTarget(), ObserverState.stage(), busy, dream, tired, scene);

        boolean away = player.isSleeping() || dream;
        if (wasAway && !away) ObserverDirector.onWake();
        wasAway = away;
        if (dream && !wasDream) ObserverDirector.onDreamEnter();
        wasDream = dream;

        // Сменился мир: фигура принадлежала старому миру. У кровати и во сне эпизод доигран, остальное прервано.
        if (ghost != null && ghost.level() != mc.level) {
            boolean played = mode == Mode.BED || ghost.level().dimension().location().equals(DREAM_DIMENSION);
            finish(played ? Outcome.DONE : Outcome.ABORT);
            clearGhost();
        }
        if (mode == Mode.SIGN && signLevel != mc.level) abortSign();

        // Смерть: фигура остаётся на месте и смотрит на тело до возрождения, потом её нет
        if (!player.isAlive()) {
            if (mode == Mode.SIGN) abortSign();
            if (ghost != null && mode != Mode.DEATH) {
                if (mode == Mode.CHASE) leaveChase(); // сервер закончит погоню сам
                finish(Outcome.DONE);
                mode = Mode.DEATH;
                forcedVisibleTicks = 0;
                ghost.setPose(Pose.STANDING);
            }
            if (mode == Mode.DEATH) {
                age++;
                ghost.tickCount++;
                placeGhost(player);
            }
            return;
        }
        if (mode == Mode.DEATH) clearGhost();

        // Режиссёр прервал эпизод (сцена в кровати, страховка по времени, отладка)
        if (plan != null && ObserverDirector.current() != plan) {
            plan = null;
            if (mode == Mode.SIGN) clearSign();
            else if (ghost != null) clearGhost();
        }

        // Погоня: ведёт сервер, поэтому идёт и при открытом экране, и когда игрок уже не устал
        if (tickChase(mc, player)) return;

        if (busy) {
            // Признак без продолжения: после экрана ничего не «догоняет» игрока. Фигура замирает.
            if (mode == Mode.SIGN) abortSign();
            return;
        }

        if (player.isSleeping() && !dream) {
            tickBed(mc, player);
            return;
        }
        bedRolled = false;
        if (mode == Mode.BED) {
            finishGhost(Outcome.DONE, true); // проснулся: его уже нет
            return;
        }

        if (!tired && mode != Mode.STILL) {
            // Контекст ушёл (отдохнул, креатив): фигура уходит тихо
            if (mode == Mode.SIGN) {
                finish(Outcome.SIGN_ONLY);
                clearSign();
            } else if (mode == Mode.GHOST) {
                finishGhost(Outcome.TIMEOUT, viewState(mc, player, player.getEyePosition()) == VIEW_VISIBLE);
            }
            return;
        }

        switch (mode) {
            case SIGN -> tickSign(mc, player);
            case GHOST -> tickGhost(mc, player, dream);
            case STILL -> tickStill(mc, player);
            default -> {
                Plan offer = ObserverDirector.offered(Kind.GHOST);
                if (offer != null) startGhost(mc, player, offer, dream);
            }
        }
    }

    /** Открыт экран, который отвлекает игрока (документ, дневник, терминал, инвентарь, чат). Кровать и смерть не в счёт. */
    public static boolean isBusy(Minecraft mc) {
        return mc.screen != null && !(mc.screen instanceof InBedChatScreen) && !(mc.screen instanceof DeathScreen);
    }

    /** Контекст «устал»: усталость выше порога, шприц или мир снов. */
    private static boolean isTired(LocalPlayer player, boolean dream) {
        if (!player.isAlive() || player.isSleeping()) return false;
        if (debugForce) return true;
        if (player.isCreative() || player.isSpectator()) return false;
        if (ClientFatigueState.collapsing) return false;
        if (dream) return true;
        if (SyringeClientState.active) return true; // искусственное бодрствование привлекает Осколка
        return ClientFatigueState.ratio() >= threshold();
    }

    private static float threshold() {
        return ObserverState.madness > 0 ? ObserverTuning.START_RATIO_AFTER_MADNESS : ObserverTuning.START_RATIO;
    }

    private static boolean isDream(Minecraft mc) {
        return mc.level != null && mc.level.dimension().location().equals(DREAM_DIMENSION);
    }

    /** Давление 0..1 без сглаживания (сглаживает режиссёр): усталость, безумие, шприц (с потолком). */
    private static float pressureTarget() {
        float fatigueP = FatigueTuning.progress(ClientFatigueState.ratio(), threshold());
        float madnessP = Math.min(ObserverState.madness, ObserverTuning.MADNESS_CAP) / (float) ObserverTuning.MADNESS_CAP;
        float syringe = Math.min(ObserverTuning.PRESSURE_SYRINGE_MAX, SyringeClientState.observerBoost());
        return Mth.clamp(ObserverTuning.PRESSURE_W_FATIGUE * fatigueP + ObserverTuning.PRESSURE_W_MADNESS * madnessP
                + syringe, 0f, 1f);
    }

    // ------------------------------------------------------------------
    // Начало эпизода GHOST: место -> (признак) -> фигура
    // ------------------------------------------------------------------
    private static void startGhost(Minecraft mc, LocalPlayer player, Plan p, boolean dream) {
        int stage = ObserverState.stage();
        Spot s = findSpot(mc, player, p, stage, dream);
        if (s == null) {
            ObserverDirector.refuse(p, searchFail + rejectSummary());
            return;
        }
        p.variant = s.variant();
        if (!ObserverDirector.accept(p)) return;
        plan = p;
        if (p.sign) {
            mode = Mode.SIGN;
            signSpot = s;
            signLevel = mc.level;
            signWait = Mth.randomBetweenInclusive(RANDOM, ObserverTuning.SIGN_DELAY_MIN, ObserverTuning.SIGN_DELAY_MAX);
            signOverdue = 0;
            playSignStep(mc, player, s.feet());
            return;
        }
        show(mc, player, s.feet(), s.covered(), Mode.GHOST);
    }

    /** Слабый признак прозвучал; фигура встаёт на это место, только когда оно вне поля зрения. */
    private static void tickSign(Minecraft mc, LocalPlayer player) {
        if (--signWait > 0) return;
        if (plan == null || !plan.figure) {
            finish(Outcome.SIGN_ONLY);
            clearSign();
            return;
        }
        Vec3 feet = signSpot.feet();
        if (inView(mc, player, feet)) {
            if (++signOverdue > ObserverTuning.SIGN_WAIT_MAX) {
                finish(Outcome.SIGN_ONLY);
                clearSign();
            }
            return;
        }
        double d = Math.hypot(feet.x - player.getX(), feet.z - player.getZ());
        if (d < ObserverTuning.NEAR_VANISH + 1.5 || d > ObserverTuning.FAR_VANISH - 6.0
                || !ObserverCover.hasSight(mc.level, player, player.getEyePosition(), feet.add(0, EYE_HEIGHT, 0))) {
            finish(Outcome.SIGN_ONLY);
            clearSign();
            return;
        }
        Spot s = signSpot;
        clearSign();
        show(mc, player, s.feet(), s.covered(), Mode.GHOST);
    }

    /** Один тихий шаг в стороне места (звук блока под ним). Дальний шаг переносится ближе по тому же направлению. */
    private static void playSignStep(Minecraft mc, LocalPlayer player, Vec3 feet) {
        SoundType type = mc.level.getBlockState(BlockPos.containing(feet.x, feet.y - 0.5, feet.z)).getSoundType();
        double dx = feet.x - player.getX();
        double dz = feet.z - player.getZ();
        double d = Math.hypot(dx, dz);
        double k = d > ObserverTuning.SIGN_SOUND_DIST ? ObserverTuning.SIGN_SOUND_DIST / d : 1.0;
        mc.level.playLocalSound(player.getX() + dx * k, feet.y, player.getZ() + dz * k, type.getStepSound(),
                SoundSource.PLAYERS, ObserverTuning.SIGN_VOLUME, 0.9f, false);
    }

    // ------------------------------------------------------------------
    // Поиск места
    // ------------------------------------------------------------------
    private static Spot findSpot(Minecraft mc, LocalPlayer player, Plan p, int stage, boolean dream) {
        java.util.Arrays.fill(rejects, 0);
        double yaw = Math.toRadians(player.getYRot());
        double hx = -Math.sin(yaw);
        double hz = Math.cos(yaw);
        float minDeg = (float) Math.min(halfHorizontalDeg(mc) + ObserverTuning.SPAWN_OUTSIDE_VIEW_MARGIN, 120.0);
        float maxDeg = Math.min(minDeg + ObserverTuning.SPAWN_ANGLE_SPREAD, 160f);
        searchFail = "no place";

        if (dream) {
            Vec3 f = searchOpen(mc, player, hx, hz, minDeg, maxDeg, stage, true);
            if (f == null) searchFail = "no place in dream";
            return f == null ? null : new Spot(f, false, ObserverDirector.V_OPEN);
        }
        // Узнавание и нарушение правила: то же место, что раньше
        if (p.recognition) {
            Spot s = searchFamiliar(mc, player, hx, hz, minDeg);
            if (s != null) return s;
            p.recognition = false;
        }
        // Знакомая точка маршрута (память привычек)
        if (p.useAnchor) {
            Spot s = searchAnchor(mc, player, hx, hz, minDeg, maxDeg, stage);
            if (s != null) return s;
            p.useAnchor = false;
        }
        boolean cover = RANDOM.nextFloat() < ObserverTuning.COVER_CHANCE_BY_STAGE[stage]
                || (stage >= 1 && ObserverDirector.lastGhostVariant() == ObserverDirector.V_OPEN);
        if (cover) {
            Spot s = searchCover(mc, player, player.blockPosition(), ObserverTuning.WINDOW_RADIUS,
                    ObserverTuning.WINDOW_DIST_MIN_BY_STAGE[stage], ObserverTuning.WINDOW_DIST_MAX,
                    hx, hz, minDeg, maxDeg, stage, false);
            if (s != null) return s;
            if (stage <= 1 && !debugForce) {
                searchFail = "stage 1: no tree or window out of view";
                return null;
            }
        }
        Vec3 f = searchOpen(mc, player, hx, hz, minDeg, maxDeg, stage, false);
        if (f == null) {
            searchFail = "no dark place out of view";
            return null;
        }
        return new Spot(f, false, ObserverDirector.V_OPEN);
    }

    /** Открытое место за краем поля зрения на дистанции стадии. */
    private static Vec3 searchOpen(Minecraft mc, LocalPlayer player, double hx, double hz, float minDeg, float maxDeg,
                                   int stage, boolean dream) {
        for (int i = 0; i < 16; i++) {
            float deg = Mth.lerp(RANDOM.nextFloat(), minDeg, maxDeg);
            double rad = Math.toRadians(deg) * (RANDOM.nextBoolean() ? 1 : -1);
            double c = Math.cos(rad);
            double s = Math.sin(rad);
            double dx = hx * c - hz * s;
            double dz = hx * s + hz * c;
            double dist = Mth.lerp(RANDOM.nextFloat(), ObserverTuning.SPAWN_DISTANCE_MIN_BY_STAGE[stage],
                    ObserverTuning.SPAWN_DISTANCE_MAX_BY_STAGE[stage]);
            BlockPos stand = findStandPos(mc.level, Mth.floor(player.getX() + dx * dist), Mth.floor(player.getZ() + dz * dist),
                    player.getBlockY(), dream ? ObserverTuning.DREAM_HEADROOM : ObserverTuning.GHOST_HEADROOM);
            if (stand == null) continue;
            Vec3 feet = new Vec3(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
            if (!spotOk(mc, player, feet, false, dream)) continue;
            return feet;
        }
        return null;
    }

    /**
     * Укрытие: окно или дерево. Окно ищется вокруг center, дерево — вокруг center (anchor) или в секторе за краем
     * поля зрения (anchor = false). Не повторяет вариант прошлого раза, если есть другой; у дома чаще окно.
     */
    private static Spot searchCover(Minecraft mc, LocalPlayer player, BlockPos center, int windowRadius,
                                    float distMin, float distMax, double hx, double hz, float minDeg, float maxDeg,
                                    int stage, boolean anchor) {
        Level level = mc.level;
        Predicate<Vec3> ok = f -> spotOk(mc, player, f, false, false);
        int last = ObserverDirector.lastGhostVariant();
        boolean windowFirst;
        if (stage >= 2 && nearHome(player)) windowFirst = RANDOM.nextFloat() < ObserverTuning.HOME_WINDOW_SHARE;
        else if (last == ObserverDirector.V_TREE) windowFirst = true;
        else if (last == ObserverDirector.V_WINDOW) windowFirst = false;
        else windowFirst = RANDOM.nextFloat() < ObserverTuning.COVER_WINDOW_SHARE;
        for (int pass = 0; pass < 2; pass++) {
            boolean window = (pass == 0) == windowFirst;
            Vec3 feet = null;
            if (window) {
                feet = ObserverCover.findWindow(level, player, center, windowRadius, distMin, distMax, hx, hz, minDeg, RANDOM, ok);
            } else if (anchor) {
                feet = ObserverCover.findTree(level, player, center.getX(), center.getY(), center.getZ(), hx, hz, minDeg, RANDOM, ok);
            } else {
                for (int i = 0; feet == null && i < ObserverTuning.COVER_ATTEMPTS; i++) {
                    float deg = Mth.lerp(RANDOM.nextFloat(), minDeg, maxDeg);
                    double rad = Math.toRadians(deg) * (RANDOM.nextBoolean() ? 1 : -1);
                    double c = Math.cos(rad);
                    double s = Math.sin(rad);
                    double dx = hx * c - hz * s;
                    double dz = hx * s + hz * c;
                    double dist = Mth.lerp(RANDOM.nextFloat(), ObserverTuning.SPAWN_DISTANCE_MIN_BY_STAGE[stage],
                            ObserverTuning.SPAWN_DISTANCE_MAX_BY_STAGE[stage]);
                    feet = ObserverCover.findTree(level, player, Mth.floor(player.getX() + dx * dist), player.getBlockY(),
                            Mth.floor(player.getZ() + dz * dist), hx, hz, minDeg, RANDOM, ok);
                }
            }
            if (feet != null) return new Spot(feet, true, window ? ObserverDirector.V_WINDOW : ObserverDirector.V_TREE);
        }
        return null;
    }

    /** Знакомая точка маршрута вне поля зрения: укрытие рядом с ней или просто место рядом. */
    private static Spot searchAnchor(Minecraft mc, LocalPlayer player, double hx, double hz, float minDeg, float maxDeg,
                                     int stage) {
        int[] a = pickAnchor(player, hx, hz, minDeg, stage);
        if (a == null) {
            searchFail = "no familiar point out of view";
            return null;
        }
        BlockPos center = new BlockPos(a[0], a[1], a[2]);
        if (RANDOM.nextFloat() < ObserverTuning.COVER_CHANCE_BY_STAGE[stage]) {
            Spot s = searchCover(mc, player, center, ObserverTuning.ANCHOR_SEARCH_RADIUS,
                    ObserverTuning.ANCHOR_DIST_MIN_BY_STAGE[stage] - ObserverTuning.ANCHOR_SEARCH_RADIUS,
                    ObserverTuning.ANCHOR_DIST_MAX + ObserverTuning.ANCHOR_SEARCH_RADIUS, hx, hz, minDeg, maxDeg, stage, true);
            if (s != null) return s;
            if (stage <= 1) return null;
        }
        int r = ObserverTuning.ANCHOR_SEARCH_RADIUS;
        for (int i = 0; i < 8; i++) {
            int x = a[0] + RANDOM.nextInt(2 * r + 1) - r;
            int z = a[2] + RANDOM.nextInt(2 * r + 1) - r;
            BlockPos stand = findStandPos(mc.level, x, z, a[1], ObserverTuning.GHOST_HEADROOM);
            if (stand == null) continue;
            Vec3 feet = new Vec3(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
            if (!spotOk(mc, player, feet, false, false)) continue;
            return new Spot(feet, false, ObserverDirector.V_ANCHOR);
        }
        return null;
    }

    /**
     * Точка маршрута {x, y, z} вне поля зрения на дистанции ANCHOR_DIST_MIN_BY_STAGE..ANCHOR_DIST_MAX, по весу привычки.
     * Недавние места наблюдения реже; на стадии 5 точки на пути к месту сна чаще.
     */
    private static int[] pickAnchor(LocalPlayer player, double hx, double hz, float minDeg, int stage) {
        int[] route = ObserverState.route;
        int[] home = ObserverState.home();
        int n = route.length / 4;
        if (n == 0) return null;
        double[] w = new double[n];
        double total = 0;
        for (int i = 0; i < n; i++) {
            double x = route[i * 4] + 0.5;
            double z = route[i * 4 + 2] + 0.5;
            double vx = x - player.getX();
            double vz = z - player.getZ();
            double d = Math.hypot(vx, vz);
            if (d < ObserverTuning.ANCHOR_DIST_MIN_BY_STAGE[stage] || d > ObserverTuning.ANCHOR_DIST_MAX) continue;
            if (ObserverCover.angleDeg(hx, hz, vx, vz) < minDeg) continue;
            double wt = Math.max(1, route[i * 4 + 3]) * ObserverDirector.spotWeight(x, z);
            if (stage >= 5 && home != null
                    && segmentDist(x, z, player.getX(), player.getZ(), home[0] + 0.5, home[2] + 0.5) < ObserverTuning.HOME_PATH_DIST) {
                wt *= ObserverTuning.HOME_PATH_WEIGHT;
            }
            w[i] = wt;
            total += wt;
        }
        if (total <= 0) return null;
        double roll = RANDOM.nextDouble() * total;
        for (int i = 0; i < n; i++) {
            if (w[i] <= 0) continue;
            roll -= w[i];
            if (roll < 0) return new int[]{route[i * 4], route[i * 4 + 1], route[i * 4 + 2]};
        }
        return null;
    }

    /** Прежнее место наблюдения (кольцо режиссёра): то же место, тот же вариант. */
    private static Spot searchFamiliar(Minecraft mc, LocalPlayer player, double hx, double hz, float minDeg) {
        double[] f = ObserverDirector.familiarSpot();
        if (f == null) return null;
        int variant = (int) f[3];
        boolean cover = variant == ObserverDirector.V_TREE || variant == ObserverDirector.V_WINDOW;
        Vec3 exact = new Vec3(f[0], f[1], f[2]);
        if (ObserverCover.angleDeg(hx, hz, f[0] - player.getX(), f[2] - player.getZ()) < minDeg) {
            searchFail = "familiar spot in view";
            return null;
        }
        if (ObserverCover.standable(mc.level, BlockPos.containing(exact.x, exact.y + 0.1, exact.z), ObserverTuning.GHOST_HEADROOM)
                && spotOk(mc, player, exact, true, false)) {
            return new Spot(exact, cover, variant);
        }
        for (int i = 0; i < 6; i++) {
            int x = Mth.floor(f[0]) + RANDOM.nextInt(3) - 1;
            int z = Mth.floor(f[2]) + RANDOM.nextInt(3) - 1;
            BlockPos stand = findStandPos(mc.level, x, z, Mth.floor(f[1]), ObserverTuning.GHOST_HEADROOM);
            if (stand == null) continue;
            Vec3 feet = new Vec3(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
            if (spotOk(mc, player, feet, true, false)) return new Spot(feet, false, ObserverDirector.V_OPEN);
        }
        return null;
    }

    /**
     * Общая проверка места: дистанция, темнота (кроме сна и отладки), защита от привыкания (недавние места реже),
     * вне кадра с запасом (не возникает на глазах), с места виден игрок.
     */
    private static boolean spotOk(Minecraft mc, LocalPlayer player, Vec3 feet, boolean familiar, boolean dream) {
        double d = Math.hypot(feet.x - player.getX(), feet.z - player.getZ());
        if (d < ObserverTuning.NEAR_VANISH + 1.5 || d > ObserverTuning.FAR_VANISH - 6.0) return reject(R_DIST);
        if (!debugForce && !dream
                && ObserverCover.brightness(mc.level, BlockPos.containing(feet.x, feet.y + 0.1, feet.z)) > ObserverTuning.MAX_LIGHT) {
            return reject(R_LIGHT);
        }
        if (!familiar && RANDOM.nextFloat() >= ObserverDirector.spotWeight(feet.x, feet.z)) return reject(R_RECENT);
        if (inView(mc, player, feet)) return reject(R_VIEW);
        if (!ObserverCover.hasSight(mc.level, player, player.getEyePosition(), feet.add(0, EYE_HEIGHT, 0))) return reject(R_SIGHT);
        return true;
    }

    private static boolean reject(int why) {
        rejects[why]++;
        return false;
    }

    /** " (no floor 3, in view 5)" — что мешало найти место; пусто, если ничего не проверялось. */
    private static String rejectSummary() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < rejects.length; i++) {
            if (rejects[i] == 0) continue;
            b.append(b.length() == 0 ? " (" : ", ").append(REJECT_NAMES[i]).append(' ').append(rejects[i]);
        }
        return b.length() == 0 ? "" : b.append(')').toString();
    }

    private static boolean nearHome(LocalPlayer player) {
        int[] home = ObserverState.home();
        if (home == null) return false;
        return Math.hypot(home[0] + 0.5 - player.getX(), home[2] + 0.5 - player.getZ()) < ObserverTuning.HOME_RADIUS;
    }

    private static double segmentDist(double px, double pz, double ax, double az, double bx, double bz) {
        double vx = bx - ax;
        double vz = bz - az;
        double l2 = vx * vx + vz * vz;
        double t = l2 < 1.0E-6 ? 0 : Mth.clamp(((px - ax) * vx + (pz - az) * vz) / l2, 0.0, 1.0);
        return Math.hypot(px - (ax + vx * t), pz - (az + vz * t));
    }

    /** Где встать в столбце (x, z): ближайшая к aroundY высота с полом и headroom свободных блоков. */
    private static BlockPos findStandPos(Level level, int x, int z, int aroundY, int headroom) {
        for (int k = 0; k <= 12; k++) {
            for (int sgn = 0; sgn < 2; sgn++) {
                if (k == 0 && sgn == 1) continue;
                int y = sgn == 0 ? aroundY - k : aroundY + k;
                if (y > aroundY + 6) continue;
                BlockPos p = new BlockPos(x, y, z);
                if (!ObserverCover.loaded(level, p)) {
                    rejects[R_FLOOR]++;
                    return null;
                }
                if (ObserverCover.standable(level, p, headroom)) return p;
            }
        }
        rejects[R_FLOOR]++;
        return null;
    }

    // ------------------------------------------------------------------
    // У кровати (Верхний мир, игрок засыпает)
    // ------------------------------------------------------------------
    private static void tickBed(Minecraft mc, LocalPlayer player) {
        if (mode == Mode.SIGN) abortSign();
        if (ghost != null && mode != Mode.BED) {
            // Фигура, стоявшая в мире, при засыпании уходит
            finishGhost(mode == Mode.STILL ? Outcome.DONE : Outcome.TIMEOUT, false);
            return;
        }
        // Идёт сцена в кровати: у неё своя фигура
        if (SleepSceneState.active() || SleepSceneState.silent()) {
            if (mode == Mode.BED) {
                finish(Outcome.ABORT);
                clearGhost();
            }
            return;
        }
        if (mode == Mode.BED) {
            age++;
            ghost.tickCount++;
            double dist = Math.hypot(player.getX() - pos.x, player.getZ() - pos.z);
            if (dist > ObserverTuning.BED_WATCH_STOP_DISTANCE) {
                moveToward(mc.level, player, ObserverTuning.BED_WATCH_CREEP_SPEED, ObserverTuning.BED_WATCH_HEADROOM, true);
                dist = Math.hypot(player.getX() - pos.x, player.getZ() - pos.z);
            }
            // Близко или низкий потолок: наклоняется над спящим (проверяет, дышит ли он)
            boolean lean = dist <= ObserverTuning.BED_WATCH_LEAN_DISTANCE
                    || !ObserverCover.standable(mc.level, BlockPos.containing(pos.x, pos.y + 0.1, pos.z), ObserverTuning.GHOST_HEADROOM);
            ghost.setPose(lean ? Pose.CROUCHING : Pose.STANDING);
            placeGhost(player);
            return;
        }
        if (bedRolled || player.getSleepTimer() < ObserverTuning.BED_WATCH_DELAY_TICKS) return;
        bedRolled = true;
        Plan p = ObserverDirector.request(Kind.BED);
        if (p == null) return;
        Vec3 feet = findBedSpot(mc, player);
        if (feet == null) {
            ObserverDirector.refuse(p, "no place by the bed");
            return;
        }
        if (!ObserverDirector.accept(p)) return;
        plan = p;
        show(mc, player, feet, false, Mode.BED);
    }

    /** 2.6-3.4 блока от кровати, в той стороне, куда смотрит лежащий игрок (от изголовья к ногам). */
    private static Vec3 findBedSpot(Minecraft mc, LocalPlayer player) {
        Level level = mc.level;
        Vec3 eye = player.getEyePosition();
        double hx;
        double hz;
        Direction bed = player.getBedOrientation();
        if (bed != null) {
            hx = -bed.getStepX();
            hz = -bed.getStepZ();
        } else {
            double yaw = Math.toRadians(player.getYRot());
            hx = -Math.sin(yaw);
            hz = Math.cos(yaw);
        }
        for (int i = 0; i < 16; i++) {
            double rad = Math.toRadians((RANDOM.nextFloat() - 0.5f) * 2f * ObserverTuning.BED_WATCH_SPREAD_DEG);
            double c = Math.cos(rad);
            double s = Math.sin(rad);
            double dx = hx * c - hz * s;
            double dz = hx * s + hz * c;
            double dist = Mth.lerp(RANDOM.nextFloat(), ObserverTuning.BED_WATCH_DISTANCE_MIN, ObserverTuning.BED_WATCH_DISTANCE_MAX);
            BlockPos stand = findStandPos(level, Mth.floor(player.getX() + dx * dist), Mth.floor(player.getZ() + dz * dist),
                    player.getBlockY(), ObserverTuning.BED_WATCH_HEADROOM);
            if (stand == null) continue;
            Vec3 feet = new Vec3(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
            if (!ObserverCover.hasSight(level, player, eye, feet.add(0, EYE_HEIGHT, 0))) continue;
            return feet;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Эпизод неподвижности: силуэт за спиной (вызывает ImmobilityClient, план уже принят)
    // ------------------------------------------------------------------

    /** Ставит силуэт за спиной, там, где был шаг. false — места нет (ImmobilityClient закроет эпизод сам). */
    public static boolean showBehind(Plan p) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || ghost != null || mode != Mode.NONE) return false;
        double yaw = Math.toRadians(player.getYRot());
        double hx = -Math.sin(yaw);
        double hz = Math.cos(yaw);
        Vec3 eye = player.getEyePosition();
        for (int i = 0; i < 32; i++) {
            double rad = Math.toRadians(180f + (RANDOM.nextFloat() * 2f - 1f) * ObserverTuning.STILL_BEHIND_SPREAD_DEG);
            double c = Math.cos(rad);
            double s = Math.sin(rad);
            double dx = hx * c - hz * s;
            double dz = hx * s + hz * c;
            double dist = Mth.lerp(RANDOM.nextFloat(), ObserverTuning.STILL_DISTANCE_MIN, ObserverTuning.STILL_DISTANCE_MAX);
            BlockPos stand = findStandPos(mc.level, Mth.floor(player.getX() + dx * dist), Mth.floor(player.getZ() + dz * dist),
                    player.getBlockY(), ObserverTuning.GHOST_HEADROOM);
            if (stand == null) continue;
            Vec3 feet = new Vec3(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
            if (!ObserverCover.hasSight(mc.level, player, eye, feet.add(0, EYE_HEIGHT, 0))) continue;
            plan = p;
            show(mc, player, feet, false, Mode.STILL);
            forcedVisibleTicks = ObserverTuning.STILL_VISIBLE_TICKS;
            return true;
        }
        return false;
    }

    private static void tickFinal(Minecraft mc, LocalPlayer player) {
        Vec3 feet = FinaleClientState.feet();
        if (mode != Mode.FINAL || ghost == null || ghost.level() != mc.level) {
            clearAll();
            show(mc, player, feet, false, Mode.FINAL);
        }
        pos = feet;
        age++;
        ghost.tickCount++;
        placeGhost(player);
    }

    /** Силуэт за спиной: выдержал взгляд (gazeTicks) — исчезает; не выдержал — стоит STILL_VISIBLE_TICKS и уходит. */
    private static void tickStill(Minecraft mc, LocalPlayer player) {
        age++;
        ghost.tickCount++;
        boolean looking = viewState(mc, player, player.getEyePosition()) == VIEW_VISIBLE;
        if (gaze(looking, ObserverTuning.gazeTicks(ObserverState.stage()))) {
            finishGhost(Outcome.HOLD, true);
            return;
        }
        if (--forcedVisibleTicks <= 0) {
            finishGhost(Outcome.HOLD, looking);
            return;
        }
        placeGhost(player);
    }

    public static boolean hasGhost() {
        return ghost != null || mode == Mode.SIGN;
    }

    // ------------------------------------------------------------------
    // Погоня (Mode.CHASE): решает и ведёт сервер (chase/ChaseHandler), здесь показ по пакетам ChasePayload
    // ------------------------------------------------------------------

    /** Пакеты сервера, запрос погони, шаги, стук и пыль. true — идёт погоня: остальное в этот тик не делается. */
    private static boolean tickChase(Minecraft mc, LocalPlayer player) {
        boolean was = mode == Mode.CHASE;
        Vec3 before = pos;
        ChasePayload pl;
        while ((pl = ChaseState.poll()) != null) onChasePacket(mc, player, pl);
        if (mode == Mode.CHASE && ghost != null) {
            prevPos = was ? before : pos;
            chaseTick(mc, player);
            return true;
        }
        if (chasePending != null) {
            // Режиссёр снял предложение (экран, страховка) — ответ сервера, если придёт, будет «чужой погоней»
            if (ObserverDirector.offered(Kind.CHASE) != chasePending) chasePending = null;
            else if (++chaseWait > ChaseTuning.REQUEST_TIMEOUT) {
                ObserverDirector.refuse(chasePending, "no answer from server");
                chasePending = null;
            }
            return false;
        }
        if (mode != Mode.NONE || ghost != null) return false;
        Plan offer = ObserverDirector.offered(Kind.CHASE);
        if (offer == null) return false;
        if (mc.getConnection() == null) {
            ObserverDirector.refuse(offer, "no connection");
            return false;
        }
        int force = offer.forced ? Math.max(1, chaseForce) : 0;
        chaseForce = 0;
        PacketDistributor.sendToServer(new ChaseRequestPayload(ChaseRequestPayload.START, force));
        chasePending = offer;
        chaseWait = 0;
        return false;
    }

    private static void onChasePacket(Minecraft mc, LocalPlayer player, ChasePayload pl) {
        int ph = pl.phase();
        if (ph == ChasePayload.PH_REJECT) {
            if (chasePending != null) {
                ObserverDirector.refuse(chasePending, "server: " + ChasePayload.reason(pl.info()));
                chasePending = null;
            }
            return;
        }
        boolean ours = mode == Mode.CHASE && ghost != null && pl.run() == chaseRun;
        if (ph == ChasePayload.PH_END) {
            if (ours) endChase(mc, player, pl.info());
            return;
        }
        Vec3 at = new Vec3(pl.x(), pl.y(), pl.z());
        if (ours) {
            pos = at;
            chasePhase = ph;
            chaseStare = pl.stare();
            if (ph == ChasePayload.PH_CAUGHT) ChaseClient.caught(mc);
            return;
        }
        if (ph == ChasePayload.PH_WARN && chasePending != null && mode == Mode.NONE && ghost == null
                && pl.run() != chaseStopped) {
            Plan p = chasePending;
            chasePending = null;
            if (ObserverDirector.accept(p)) {
                // Он стоит на твоём следе, за спиной: мир смолкает, сбивается дыхание, начинает стучать сердце
                plan = p;
                chaseRun = pl.run();
                chaseEnded = false;
                show(mc, player, at, false, Mode.CHASE);
                chasePhase = ph;
                chaseStare = pl.stare();
                stepLeft = ChaseTuning.STEP_EVERY;
                knockIn = 0;
                hushWorld(mc);
                BreathClient.triggerNow();
                ChaseClient.begin();
                ChaseMusic.begin(mc);
                return;
            }
        }
        // Погоню, которую клиент не показывает, сервер не должен вести вслепую
        if (pl.run() != 0 && pl.run() != chaseStopped && pl.run() != chaseRun) {
            chaseStopped = pl.run();
            sendChaseStop(mc);
        }
    }

    /** Тик фигуры в погоне: бег (анимация), шаги блока под ним, стук в закрытую дверь, пыль под взглядом. */
    private static void chaseTick(Minecraft mc, LocalPlayer player) {
        age++;
        ghost.tickCount++;
        double moved = Math.hypot(pos.x - prevPos.x, pos.z - prevPos.z);
        ghost.walkAnimation.update((float) Math.min(moved * 4.0, 1.0), 0.4f);
        stepLeft -= moved;
        if (stepLeft <= 0) {
            stepLeft = Math.max(stepLeft + ChaseTuning.STEP_EVERY, ChaseTuning.STEP_EVERY * 0.5);
            SoundType type = mc.level.getBlockState(BlockPos.containing(pos.x, pos.y - 0.2, pos.z)).getSoundType();
            mc.level.playLocalSound(pos.x, pos.y, pos.z, type.getStepSound(), SoundSource.PLAYERS,
                    ChaseTuning.STEP_VOLUME * type.getVolume(), type.getPitch() * 0.85f, false);
        }
        if (chasePhase == ChasePayload.PH_BLOCKED) {
            if (--knockIn <= 0) {
                knockIn = ChaseTuning.KNOCK_EVERY;
                mc.level.playLocalSound(pos.x, pos.y + 1.0, pos.z, ModSounds.LAB_KNOCK.get(), SoundSource.PLAYERS,
                        ChaseTuning.KNOCK_VOLUME, 0.75f + RANDOM.nextFloat() * 0.15f, false);
            }
        } else {
            knockIn = 0;
        }
        if (chaseStare > 0f) gazeDust(chaseStare);
        double dist = player.position().distanceTo(pos);
        ChaseClient.track(chasePhase, dist);
        ChaseMusic.track(mc, player, chasePhase, pos, dist);
        placeGhost(player);
    }

    /** Сервер закончил погоню: исход O_* -> итог для режиссёра; частицы, только если игрок видит, как он исчез. */
    private static void endChase(Minecraft mc, LocalPlayer player, int outcome) {
        chaseEnded = true;
        // Прогнал взглядом (или поймал) — музыка обрывается; ушёл, упёрся — затихает
        ChaseMusic.end(outcome == ChasePayload.O_STARED || outcome == ChasePayload.O_TAUGHT
                || outcome == ChasePayload.O_CAUGHT);
        switch (outcome) {
            case ChasePayload.O_STARED, ChasePayload.O_TAUGHT -> finishGhost(Outcome.LOOK, true);
            case ChasePayload.O_ESCAPED ->
                    finishGhost(Outcome.TIMEOUT, viewState(mc, player, player.getEyePosition()) == VIEW_VISIBLE);
            case ChasePayload.O_BLOCKED, ChasePayload.O_CAUGHT -> finishGhost(Outcome.DONE, false);
            default -> finishGhost(Outcome.ABORT, false);
        }
    }

    /** Фигура погони убрана (любой причиной): если сервер её ещё ведёт — STOP; сердце успокаивается само. */
    private static void leaveChase() {
        if (chaseRun != 0 && !chaseEnded) {
            chaseStopped = chaseRun;
            sendChaseStop(Minecraft.getInstance());
        }
        chaseRun = 0;
        chaseEnded = false;
        chasePhase = ChasePayload.PH_IDLE;
        chaseStare = 0f;
        ChaseClient.end();
        ChaseMusic.end(false);
    }

    private static void sendChaseStop(Minecraft mc) {
        if (mc.getConnection() != null) PacketDistributor.sendToServer(new ChaseRequestPayload(ChaseRequestPayload.STOP, 0));
    }

    // ------------------------------------------------------------------
    // Поведение фигуры
    // ------------------------------------------------------------------
    private static void show(Minecraft mc, LocalPlayer player, Vec3 feet, boolean cover, Mode m) {
        ghost = new ObserverGhost(mc.level);
        ghost.setPose(Pose.STANDING);
        pos = feet;
        prevPos = feet;
        age = 0;
        gazeTicks = 0;
        wasLooking = false;
        covered = cover;
        mode = m;
        stepCooldown = Mth.randomBetweenInclusive(RANDOM, ObserverTuning.STEP_COOLDOWN_MIN, ObserverTuning.STEP_COOLDOWN_MAX);
        placeGhost(player);
        if (m == Mode.GHOST) {
            ObserverDirector.rememberSpot(feet.x, feet.y, feet.z, plan == null ? ObserverDirector.V_OPEN : plan.variant);
        }
    }

    private static void tickGhost(Minecraft mc, LocalPlayer player, boolean dream) {
        age++;
        ghost.tickCount++;
        Vec3 eye = player.getEyePosition();
        double dist = eye.distanceTo(pos.add(0, EYE_HEIGHT, 0));
        int view = viewState(mc, player, eye);
        boolean looking = view == VIEW_VISIBLE;      // в поле зрения и ничем не закрыт
        boolean inFov = view != VIEW_OUTSIDE;        // хотя бы часть тела попадает в кадр
        boolean justSeen = looking && !wasLooking;   // первый тик, когда игрок его заметил
        wasLooking = looking;
        int stage = ObserverState.stage();

        int need = ObserverTuning.gazeTicks(stage);
        if (dream) {
            // Во сне: исчезает от взгляда на всех стадиях. Ранние — кадр-сюрприз (по плану) в момент исчезновения;
            // поздние — кадр, как только заметили, и пока смотришь, он идёт к тебе
            boolean approach = stage >= ObserverTuning.DREAM_APPROACH_MIN_STAGE;
            if (approach && justSeen && ObserverDirector.scareNow()) ObserverScare.trigger(stage);
            if (gaze(looking, need)) {
                if (!approach && ObserverDirector.scareNow()) ObserverScare.trigger(stage);
                finishGhost(Outcome.LOOK, true);
                return;
            }
            if (approach && looking && dist > ObserverTuning.DREAM_STOP_DISTANCE) {
                double speed = ObserverTuning.DREAM_APPROACH_SPEED
                        + ObserverTuning.DREAM_APPROACH_PER_PRESSURE * ObserverDirector.pressure();
                moveToward(mc.level, player, speed, ObserverTuning.DREAM_HEADROOM, false);
            } else if (approach && !inFov && dist <= ObserverTuning.DREAM_STOP_DISTANCE + 0.5) {
                finishGhost(Outcome.DONE, false); // дошёл; отвернулся — его уже нет
                return;
            }
            if (age > ObserverTuning.LIFETIME) {
                finishGhost(Outcome.TIMEOUT, looking);
                return;
            }
            placeGhost(player);
            return;
        }

        int lifetime = covered ? (int) (ObserverTuning.LIFETIME * ObserverTuning.COVER_LIFETIME_MULT) : ObserverTuning.LIFETIME;
        // Правило реального мира: исчезает, когда на него смотрят; из укрытия — чуть дольше
        if (gaze(looking, need + (covered ? ObserverTuning.COVER_GAZE_TICKS_BONUS : 0))) {
            finishGhost(Outcome.LOOK, true);
            return;
        }
        if (dist < ObserverTuning.NEAR_VANISH) {
            finishGhost(Outcome.LOOK, true);
            return;
        }
        if (dist > ObserverTuning.FAR_VANISH || age > lifetime) {
            finishGhost(Outcome.TIMEOUT, looking);
            return;
        }
        // Пока игрок не смотрит, подходит на шаг-другой (не сквозь стены, не из укрытия, со стадии 2)
        if (!covered && stage >= ObserverTuning.APPROACH_MIN_STAGE && !inFov && --stepCooldown <= 0) {
            stepCooldown = Mth.randomBetweenInclusive(RANDOM, ObserverTuning.STEP_COOLDOWN_MIN, ObserverTuning.STEP_COOLDOWN_MAX);
            float limit = ObserverTuning.APPROACH_LIMIT_BY_STAGE[stage];
            if (dist > limit) {
                float step = Mth.lerp(RANDOM.nextFloat(), ObserverTuning.STEP_DISTANCE_MIN, ObserverTuning.STEP_DISTANCE_MAX);
                moveToward(mc.level, player, Math.min(step, dist - limit), ObserverTuning.GHOST_HEADROOM, true);
            }
        }
        placeGhost(player);
    }

    /**
     * Счёт взгляда: смотрит — +1 (и пыль с тела, тем гуще, чем ближе к исчезновению), отвернулся — -1.
     * true — выдержал need тиков: фигура исчезает.
     */
    private static boolean gaze(boolean looking, int need) {
        if (looking) {
            if (++gazeTicks >= need) return true;
            gazeDust(gazeTicks / (float) need);
        } else if (gazeTicks > 0) {
            gazeTicks--;
        }
        return false;
    }

    /** Пыль с тела под взглядом: progress 0..1, не больше STARE_DUST_MAX частиц за тик. */
    private static void gazeDust(float progress) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ghost == null || progress <= 0f) return;
        float want = Math.min(1f, progress) * ChaseTuning.STARE_DUST_MAX;
        int n = (int) want + (RANDOM.nextFloat() < want - (int) want ? 1 : 0);
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0f, 0f, 0f), 1.2f);
        for (int i = 0; i < n; i++) {
            mc.level.addParticle(dust,
                    pos.x + (RANDOM.nextDouble() - 0.5) * 0.8,
                    pos.y + 0.2 + RANDOM.nextDouble() * 2.2,
                    pos.z + (RANDOM.nextDouble() - 0.5) * 0.8,
                    (RANDOM.nextDouble() - 0.5) * 0.04,
                    0.02 + RANDOM.nextDouble() * 0.04,
                    (RANDOM.nextDouble() - 0.5) * 0.04);
        }
    }

    /**
     * Сдвигает фигуру к игроку по горизонтали на distance блоков, с подстройкой по высоте.
     * walls: шаг не проходит сквозь блоки и не прыгает больше чем на блок по высоте. false — не сдвинулся.
     */
    private static boolean moveToward(Level level, LocalPlayer player, double distance, int headroom, boolean walls) {
        double dx = player.getX() - pos.x;
        double dz = player.getZ() - pos.z;
        double len = Math.hypot(dx, dz);
        if (len < 1.0E-4) return false;
        double nx = pos.x + dx / len * distance;
        double nz = pos.z + dz / len * distance;
        BlockPos stand = findStandPos(level, Mth.floor(nx), Mth.floor(nz), Mth.floor(pos.y + 0.1), headroom);
        if (stand == null) return false;
        Vec3 next = new Vec3(nx, stand.getY(), nz);
        if (walls) {
            if (Math.abs(next.y - pos.y) > 1.01) return false;
            Vec3 a = pos.add(0, 1.0, 0);
            Vec3 b = next.add(0, 1.0, 0);
            if (level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType()
                    != HitResult.Type.MISS) return false;
        }
        pos = next;
        return true;
    }

    private static final int VIEW_OUTSIDE = 0;   // вне поля зрения
    private static final int VIEW_BLOCKED = 1;   // в поле зрения, но закрыт блоками
    private static final int VIEW_VISIBLE = 2;   // игрок реально его видит
    /** Точки тела: ноги, торс, «глаза» до масштаба, лицо с масштабом фигуры. */
    private static final double[] BODY_POINTS = {0.15, 0.9, EYE_HEIGHT,
            ObserverTuning.FACE_EYE_Y * ObserverTuning.GHOST_SCALE_Y};

    private static double tanHalfVertical(Minecraft mc) {
        return Math.tan(Math.toRadians(mc.options.fov().get() / 2.0));
    }

    private static double aspect(Minecraft mc) {
        return mc.getWindow().getWidth() / (double) Math.max(1, mc.getWindow().getHeight());
    }

    private static double halfHorizontalDeg(Minecraft mc) {
        return Math.toDegrees(Math.atan(tanHalfVertical(mc) * aspect(mc)));
    }

    /**
     * Попадает ли тело в прямоугольник экрана (с учётом FOV и пропорций окна)
     * и видно ли его на самом деле. Достаточно, чтобы хоть одна из точек тела была на экране.
     */
    private static int viewState(Minecraft mc, LocalPlayer player, Vec3 eye) {
        Vec3 look = player.getViewVector(1.0f);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        double rl = right.length();
        if (rl < 1.0E-4) return VIEW_OUTSIDE; // смотрит строго вверх/вниз
        right = right.scale(1.0 / rl);
        Vec3 up = right.cross(look);

        double tv = tanHalfVertical(mc) * ObserverTuning.VIEW_MARGIN;
        double th = tv * aspect(mc);

        int result = VIEW_OUTSIDE;
        for (double off : BODY_POINTS) {
            Vec3 target = pos.add(0, off, 0);
            Vec3 v = target.subtract(eye);
            double z = v.dot(look);
            if (z <= 0.1) continue;
            if (Math.abs(v.dot(right) / z) > th || Math.abs(v.dot(up) / z) > tv) continue;

            if (ObserverCover.hasSight(mc.level, player, eye, target)) return VIEW_VISIBLE;
            result = VIEW_BLOCKED;
        }
        return result;
    }

    /** Попало бы место в кадр (с запасом SPAWN_VIEW_MARGIN), без учёта препятствий: туда нельзя «возникнуть». */
    private static boolean inView(Minecraft mc, LocalPlayer player, Vec3 feet) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        double rl = right.length();
        if (rl < 1.0E-4) return false;
        right = right.scale(1.0 / rl);
        Vec3 up = right.cross(look);
        double tv = tanHalfVertical(mc) * ObserverTuning.SPAWN_VIEW_MARGIN;
        double th = tv * aspect(mc);
        for (double off : BODY_POINTS) {
            Vec3 v = feet.add(0, off, 0).subtract(eye);
            double z = v.dot(look);
            if (z <= 0.1) continue;
            if (Math.abs(v.dot(right) / z) <= th && Math.abs(v.dot(up) / z) <= tv) return true;
        }
        return false;
    }

    /** Ставит фигуру в позицию и поворачивает лицом (и головой) к игроку. */
    private static void placeGhost(LocalPlayer player) {
        ghost.setPos(pos.x, pos.y, pos.z);
        ghost.setOldPosAndRot();

        double headY = (ObserverTuning.FACE_PIVOT_Y - (ghost.isCrouching() ? ObserverTuning.FACE_CROUCH_DROP : 0f))
                * ObserverTuning.GHOST_SCALE_Y;
        double dx = player.getX() - pos.x;
        double dz = player.getZ() - pos.z;
        double dy = player.getEyeY() - (pos.y + headY);
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        float pitch = (float) -(Mth.atan2(dy, Math.hypot(dx, dz)) * (180.0 / Math.PI));

        if (mode == Mode.CHASE) {
            placeRunner(yaw, pitch);
            return;
        }
        ghost.setYRot(yaw);
        ghost.setXRot(pitch);
        ghost.setYBodyRot(yaw);
        ghost.setYHeadRot(yaw);
        ghost.yBodyRotO = yaw;
        ghost.yHeadRotO = yaw;
        ghost.setOldPosAndRot();
    }

    /**
     * Погоня: тело — по ходу бега (плавно, BODY_TURN за тик), голова — к игроку, но не дальше HEAD_MAX от тела
     * (бежит за угол — смотрит вперёд, а не скользит боком). Старые углы остаются в *O: рендер сглаживает поворот.
     */
    private static void placeRunner(float yaw, float pitch) {
        // Только что появился (age 0): стоит лицом к игроку
        float bodyO = age == 0 ? yaw : ghost.yBodyRot;
        float headO = age == 0 ? yaw : ghost.yHeadRot;
        double mx = pos.x - prevPos.x;
        double mz = pos.z - prevPos.z;
        float body = bodyO;
        if (mx * mx + mz * mz > 1.0e-4) {
            float run = (float) (Mth.atan2(mz, mx) * (180.0 / Math.PI)) - 90.0f;
            body = bodyO + Mth.wrapDegrees(run - bodyO) * ChaseTuning.BODY_TURN;
        }
        float head = body + Mth.clamp(Mth.wrapDegrees(yaw - body), -ChaseTuning.HEAD_MAX, ChaseTuning.HEAD_MAX);
        ghost.setYRot(head);
        ghost.setXRot(pitch);
        ghost.setYBodyRot(body);
        ghost.setYHeadRot(head);
        ghost.yBodyRotO = bodyO;
        ghost.yHeadRotO = headO;
    }

    // ------------------------------------------------------------------
    // Конец эпизода
    // ------------------------------------------------------------------

    /** Сообщить режиссёру, чем кончился эпизод (только если это ещё наш эпизод). */
    private static void finish(Outcome o) {
        if (plan != null) ObserverDirector.end(plan, o);
        plan = null;
    }

    /** Конец с фигурой: particles — облако частиц (игрок видит, как он исчезает), иначе тихо. */
    private static void finishGhost(Outcome o, boolean particles) {
        if (particles) spawnVanishParticles();
        reportSeen(o);
        finish(o);
        clearGhost();
    }

    /**
     * Игрок прогнал фигуру взглядом (или она стояла под взглядом): сервер запоминает место, после сна там могут
     * остаться её шаги. Только Верхний мир, только эпизод GHOST, со стадии SEEN_MIN_STAGE. Проверяет сервер.
     */
    private static void reportSeen(Outcome o) {
        if (plan == null || plan.kind != Kind.GHOST || ghost == null) return;
        if (o != Outcome.LOOK && o != Outcome.HOLD) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getConnection() == null || isDream(mc)) return;
        if (ObserverState.stage() < TraceTuning.SEEN_MIN_STAGE) return;
        PacketDistributor.sendToServer(new ObserverSeenPayload(BlockPos.containing(pos.x, pos.y + 0.1, pos.z)));
    }

    private static void abortSign() {
        finish(Outcome.ABORT);
        clearSign();
    }

    private static void clearSign() {
        signSpot = null;
        signLevel = null;
        if (mode == Mode.SIGN) mode = Mode.NONE;
    }

    private static void clearGhost() {
        if (mode == Mode.CHASE) leaveChase();
        ghost = null;
        if (mode != Mode.SIGN) mode = Mode.NONE;
        covered = false;
        wasLooking = false;
        forcedVisibleTicks = 0;
        age = 0;
        gazeTicks = 0;
    }

    private static void clearAll() {
        if (mode == Mode.SIGN) abortSign();
        finish(Outcome.ABORT);
        clearSign();
        clearGhost();
        mode = Mode.NONE;
        bedRolled = false;
    }

    /** Облако чёрных частиц на месте, где он стоял. */
    private static void spawnVanishParticles() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ghost == null) return;

        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0f, 0f, 0f), 1.6f);
        for (int i = 0; i < ObserverTuning.VANISH_DUST_COUNT; i++) {
            mc.level.addParticle(dust,
                    pos.x + (RANDOM.nextDouble() - 0.5) * 0.7,
                    pos.y + RANDOM.nextDouble() * 1.9,
                    pos.z + (RANDOM.nextDouble() - 0.5) * 0.7,
                    (RANDOM.nextDouble() - 0.5) * 0.08,
                    RANDOM.nextDouble() * 0.06,
                    (RANDOM.nextDouble() - 0.5) * 0.08);
        }
        for (int i = 0; i < ObserverTuning.VANISH_INK_COUNT; i++) {
            mc.level.addParticle(ParticleTypes.SQUID_INK,
                    pos.x + (RANDOM.nextDouble() - 0.5) * 0.6,
                    pos.y + RANDOM.nextDouble() * 1.8,
                    pos.z + (RANDOM.nextDouble() - 0.5) * 0.6,
                    (RANDOM.nextDouble() - 0.5) * 0.04,
                    RANDOM.nextDouble() * 0.04,
                    (RANDOM.nextDouble() - 0.5) * 0.04);
        }
    }

    // ------------------------------------------------------------------
    // Тишина мира для эпизодов (неподвижность, сбой): только звуки мира, музыка и интерфейс не трогаются
    // ------------------------------------------------------------------
    private static final SoundSource[] WORLD_SOURCES = {SoundSource.AMBIENT, SoundSource.WEATHER, SoundSource.BLOCKS,
            SoundSource.NEUTRAL, SoundSource.HOSTILE, SoundSource.PLAYERS};

    /** Останавливает звучащие звуки мира. */
    public static void hushWorld(Minecraft mc) {
        for (SoundSource s : WORLD_SOURCES) mc.getSoundManager().stop(null, s);
    }

    /** Звук мира (его глушит эпизод), а не музыка, интерфейс или голос. */
    public static boolean isWorldSound(SoundInstance sound) {
        if (sound == null) return false;
        SoundSource src = sound.getSource();
        for (SoundSource s : WORLD_SOURCES) if (s == src) return true;
        return false;
    }

    // ------------------------------------------------------------------
    // Рендер: рисуем фигуру как обычную сущность, но без добавления в мир
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (ghost == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ghost.level() != mc.level) return;

        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // В погоне позиция приходит с сервера раз в тик: между тиками ведём плавно
        Vec3 at = mode == Mode.CHASE ? prevPos.lerp(pos, partial) : pos;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(at.x, at.y + 1.0, at.z));
        if (mode == Mode.BED) {
            light = LightTexture.pack(Math.max(LightTexture.block(light), ObserverTuning.BED_WATCH_MIN_LIGHT),
                    LightTexture.sky(light));
        }

        PoseStack ps = event.getPoseStack();
        float t = age + partial;
        boolean eyes = isDream(mc) || (mc.player != null
                && mc.player.position().distanceTo(at) < ObserverTuning.EYES_DISTANCE);
        double rad = Math.toRadians(ghost.getYRot());
        double side = Math.sin(t * ObserverTuning.ECHO_SPEED) * ObserverTuning.ECHO_OFFSET;
        for (int pass = 0; pass < 2; pass++) {
            boolean echo = pass == 0;
            ghost.variant = echo ? ObserverGhost.ECHO : (eyes ? ObserverGhost.EYES : ObserverGhost.BODY);
            float w = (1f + ObserverTuning.GHOST_WOBBLE * Mth.sin(t * ObserverTuning.GHOST_WOBBLE_SPEED + pass * 1.7f))
                    * (echo ? 1.04f : 1f);
            ps.pushPose();
            ps.translate(at.x - cam.x + (echo ? Math.cos(rad) * side : 0), at.y - cam.y,
                    at.z - cam.z + (echo ? Math.sin(rad) * side : 0));
            ps.scale(ObserverTuning.GHOST_SCALE_XZ * w, ObserverTuning.GHOST_SCALE_Y, ObserverTuning.GHOST_SCALE_XZ * w);
            mc.getEntityRenderDispatcher().render(ghost, 0, 0, 0, ghost.getYRot(), partial, ps, buffers, light);
            if (!echo && eyes) drawFaceEyes(ps, buffers, t);
            ps.popPose();
        }
        buffers.endBatch();
    }

    private static ResourceLocation eyeTex(String layer, boolean mirrored) {
        return ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID,
                "textures/misc/silhouette_eye_" + layer + (mirrored ? "_r" : "") + ".png");
    }

    /**
     * [слой][0 = глаз слева от зрителя (зеркальная текстура), 1 = справа]; слои: склера, радужка, веки.
     */
    private static final ResourceLocation[][] EYE_TEX = {
            {eyeTex("sclera", true), eyeTex("sclera", false)},
            {eyeTex("iris", true), eyeTex("iris", false)},
            {eyeTex("lids", true), eyeTex("lids", false)}
    };

    private static float eyeBlink(long ms, int period, int phase) {
        long ph = (ms + phase) % period;
        if (ph > 160) return 1f;
        return 1f - 0.92f * (float) Math.sin(Math.PI * ph / 160.0);
    }

    private static void eyeVertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, float a) {
        vc.addVertex(pose.pose(), x, y, z).setColor(1f, 1f, 1f, a).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0f, 0f, -1f);
    }

    /**
     * Реалистичные глаза на лице: те же текстуры, что в сцене с кроватью. Вызывать внутри масштаба фигуры.
     */
    private static void drawFaceEyes(PoseStack ps, MultiBufferSource buffers, float t) {
        long ms = (long) (t * 50f);
        float gx = 0f, gy = 0f;
        RandomSource gr = RandomSource.create(ms / 1300L * 31L + 7L);
        if (gr.nextInt(10) >= 7) {
            gx = (gr.nextFloat() - 0.5f) * 0.34f;
            gy = (gr.nextFloat() - 0.5f) * 0.12f;
        }
        gx += (float) Math.sin(ms / 410.0) * 0.02f;

        // Углы головы между тиками (в погоне голова поворачивается; в остальных режимах старые углы = новым)
        float partial = t - age;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(partial, ghost.yHeadRotO, ghost.yHeadRot)));
        // Наклон (присед у кровати): голова ниже и чуть вперёд, глаза идут за ней
        boolean crouch = ghost.isCrouching();
        ps.translate(0, ObserverTuning.FACE_PIVOT_Y - (crouch ? ObserverTuning.FACE_CROUCH_DROP : 0f),
                crouch ? -ObserverTuning.FACE_CROUCH_FORWARD : 0f);
        ps.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partial, ghost.xRotO, ghost.getXRot())));
        PoseStack.Pose pose = ps.last();
        for (int layer = 0; layer < 3; layer++) {
            for (int s = 0; s < 2; s++) {
                boolean left = s == 0;
                float sy = eyeBlink(ms, left ? 3700 : 4300, left ? 0 : 900)
                        * (mode == Mode.FINAL ? FinaleClientState.eyeOpen() : 1f); // после правды глаза закрыты
                float size = ObserverTuning.FACE_EYE_W * (left ? 1f : 0.94f);
                float cx = (left ? 1f : -1f) * ObserverTuning.FACE_EYE_DX;
                float cy = ObserverTuning.FACE_EYE_Y - ObserverTuning.FACE_PIVOT_Y - (left ? 0f : 0.01f);
                float hw = size / 2f;
                float hh = size * 0.675f / 2f * sy;
                float a = layer == 2 ? 1f : (left ? 1f : 0.85f);
                if (layer == 1) {
                    cx += gx * size;
                    cy += gy * size * sy;
                    hw = size * 0.22f;
                    hh = size * 0.2184f * sy;
                }
                float z = -(ObserverTuning.FACE_Z + layer * 0.003f);
                VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(EYE_TEX[layer][s]));
                eyeVertex(vc, pose, cx + hw, cy - hh, z, 0f, 1f, a);
                eyeVertex(vc, pose, cx - hw, cy - hh, z, 1f, 1f, a);
                eyeVertex(vc, pose, cx - hw, cy + hh, z, 1f, 0f, a);
                eyeVertex(vc, pose, cx + hw, cy + hh, z, 0f, 0f, a);
            }
        }
        ps.popPose();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        // Сервер сам забудет погоню при выходе: STOP не нужен (соединение уже закрывается)
        chaseRun = 0;
        chaseEnded = true;
        chasePending = null;
        chaseForce = 0;
        chaseStopped = 0;
        clearAll();
        ChaseState.reset();
        debugForce = false;
        wasAway = false;
        wasDream = false;
        ObserverDirector.listener = null;
        ObserverState.reset();
    }

    // ------------------------------------------------------------------
    // Отладка: /hypersomnia_observer ... (обычному игроку не нужна)
    // ------------------------------------------------------------------
    private static int say(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int chase(CommandSourceStack src, int force) {
        chaseForce = force;
        ObserverDirector.force(Kind.CHASE, false);
        return say(src, "Chase: requested" + (force == 2 ? " (real: catches, stage 3+ rules)"
                : " (teaching if it is the first one or stage < 3)")
                + "; server still checks: safe place, trail 15+ blocks behind you, start point out of view."
                + " See /hypersomnia_observer director log");
    }

    private static int still(CommandSourceStack src, boolean rule) {
        ObserverDirector.force(Kind.STILL, rule);
        boolean ok = ImmobilityClient.triggerNow();
        return say(src, ok ? "Immobility: started" + (rule ? " (rule break)" : "")
                : "Immobility: not started (see /hypersomnia_observer director)");
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> next = Commands.literal("next");
        for (Kind k : new Kind[]{Kind.GHOST, Kind.DOOR, Kind.GLITCH, Kind.BED, Kind.STILL, Kind.CHASE}) {
            next.then(Commands.literal(k.name().toLowerCase(Locale.ROOT)).executes(ctx -> {
                if (k == Kind.CHASE) chaseForce = 1;
                ObserverDirector.force(k, false);
                return say(ctx.getSource(), "Director: next " + k + (k == Kind.BED ? " when lying in bed" : ""));
            }));
        }
        LiteralArgumentBuilder<CommandSourceStack> rule = Commands.literal("rule");
        for (Kind k : new Kind[]{Kind.STILL}) { // нарушение правила осталось только у силуэта за спиной
            rule.then(Commands.literal(k.name().toLowerCase(Locale.ROOT)).executes(ctx -> {
                ObserverDirector.force(k, true);
                return say(ctx.getSource(), "Director: next " + k + " breaks the rule");
            }));
        }
        // Отладка: только с правом команд (читы в одиночной игре или оператор), как серверные hypersomnia_*
        event.getDispatcher().register(Commands.literal("hypersomnia_observer")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("spawn").executes(ctx -> {
                    debugForce = true;
                    ObserverDirector.force(Kind.GHOST, false);
                    return say(ctx.getSource(), "Observer: forced on (fatigue and light ignored), ghost offered");
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    debugForce = false;
                    if (mode == Mode.SIGN) abortSign();
                    else if (ghost != null) finishGhost(Outcome.ABORT, false);
                    return say(ctx.getSource(), "Observer: back to normal rules");
                }))
                .then(Commands.literal("scare").executes(ctx -> {
                    ObserverScare.reset();
                    ObserverScare.trigger(Math.max(2, ObserverState.stage()));
                    return 1;
                }))
                .then(Commands.literal("still")
                        .executes(ctx -> still(ctx.getSource(), false))
                        .then(Commands.literal("rule").executes(ctx -> still(ctx.getSource(), true))))
                .then(Commands.literal("glitch").executes(ctx -> {
                    ObserverDirector.force(Kind.GLITCH, false);
                    return say(ctx.getSource(), "Glitch: offered");
                }))
                .then(Commands.literal("door").executes(ctx -> {
                    ObserverDirector.force(Kind.DOOR, false);
                    return say(ctx.getSource(), "Door: offered (needs a wooden door within "
                            + ObserverTuning.DOOR_RADIUS + " blocks, out of view)");
                }))
                .then(Commands.literal("chase")
                        .executes(ctx -> chase(ctx.getSource(), 1))
                        .then(Commands.literal("real").executes(ctx -> chase(ctx.getSource(), 2))))
                .then(Commands.literal("breath").executes(ctx -> {
                    BreathClient.triggerNow();
                    return say(ctx.getSource(), "Breath: burst");
                }))
                .then(Commands.literal("director")
                        .executes(ctx -> {
                            for (String line : ObserverDirector.status()) say(ctx.getSource(), line);
                            return 1;
                        })
                        .then(Commands.literal("fill").executes(ctx -> {
                            ObserverDirector.fill();
                            return say(ctx.getSource(), "Director: budget full, silence and cooldowns off");
                        }))
                        .then(Commands.literal("calm").executes(ctx -> {
                            ObserverDirector.calm(ObserverTuning.CALM_TICKS);
                            return say(ctx.getSource(), "Director: calm " + ObserverTuning.CALM_TICKS / 20 + " s");
                        }))
                        .then(Commands.literal("log").executes(ctx -> {
                            for (String line : ObserverDirector.journal()) say(ctx.getSource(), line);
                            return 1;
                        }))
                        .then(Commands.literal("watch").executes(ctx -> {
                            if (ObserverDirector.listener != null) {
                                ObserverDirector.listener = null;
                                return say(ctx.getSource(), "Director: watch off");
                            }
                            ObserverDirector.listener = line -> {
                                Minecraft mc = Minecraft.getInstance();
                                if (mc.gui != null) mc.gui.getChat().addMessage(Component.literal("[director] " + line));
                            };
                            return say(ctx.getSource(), "Director: watch on");
                        }))
                        .then(next)
                        .then(rule)));
    }
}
