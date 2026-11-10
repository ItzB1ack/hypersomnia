package com.hypersomnia.client;

import com.hypersomnia.ObserverTuning;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Клиентский «режиссёр» наблюдателя. Единственный: через него начинается каждый эпизод осколка на клиенте.
 * Бюджет, один эпизод за раз, обязательная тишина, план эпизода, защита от повторов, недавние места наблюдения.
 * Без клиентских импортов Minecraft (его гоняет серверный самотест). Числа только в ObserverTuning.
 * <p>
 * Как начинается эпизод:
 * <ul>
 * <li>предложение: режиссёр сам выбирает вид (GHOST, DOOR, GLITCH, CHASE) и кладёт план в offer; система ищет место
 *     и вызывает accept() или refuse() с причиной (CHASE решает сервер: ObserverClient спрашивает его и передаёт ответ);</li>
 * <li>запрос: контекст игрока (STILL — стоит неподвижно, BED — лёг в кровать) вызывает request() и получает план
 *     или null (причина видна в отладке), дальше так же accept()/refuse().</li>
 * </ul>
 * SCARE — не отдельный эпизод, а фаза реакции внутри GHOST во сне: scareNow(), раз за эпизод и только если её заложил план.
 * Очереди нет: эпизод либо начинается сейчас, либо не начинается (ничего не «догоняет» игрока после закрытия экрана).
 * <p>
 * Эпизод: контекст -> слабый признак (не всегда) -> возможность заметить -> реакция на игрока -> конец -> тишина.
 */
public final class ObserverDirector {
    private ObserverDirector() {}

    /** CHASE — погоня по следу игрока (ведёт сервер, chase/ChaseHandler); в конце, чтобы не сдвигать индексы EP_*. */
    public enum Kind { GHOST, DOOR, SCARE, BED, STILL, GLITCH, CHASE }

    /**
     * Чем кончился эпизод. LOOK — прогнал взглядом или подошёл; TIMEOUT — не заметил (или убежал от погони);
     * SIGN_ONLY — признак без продолжения; HOLD — силуэт за спиной исчез под взглядом; DONE — отыграл до конца
     * (у погони: ушёл от двери или поймал); ABORT — прерван (сцена, смена мира, экран).
     */
    public enum Outcome { LOOK, TIMEOUT, SIGN_ONLY, HOLD, DONE, ABORT }

    /** Варианты места для GHOST: открытое место, дерево, окно, у знакомой точки без укрытия. */
    public static final int V_OPEN = 0, V_TREE = 1, V_WINDOW = 2, V_ANCHOR = 3;

    /** План эпизода: решается до начала, система только исполняет. */
    public static final class Plan {
        public final Kind kind;
        /** GHOST: V_*; DOOR: 1 = дверь у места сна. */
        public int variant = -1;
        /** Слабый признак (шаг) перед появлением; figure = будет ли продолжение. */
        public boolean sign;
        public boolean figure = true;
        /** Нарушение выученного правила: силуэт за спиной (только STILL, стадия 4+). */
        public boolean ruleBreak;
        /** План поставлен отладочной командой (force): CHASE просит сервер пропустить проверки стадии и дней. */
        public boolean forced;
        /** Мир снов: разрешена фаза кадра-сюрприза; scareDone — уже была. */
        public boolean scare;
        public boolean scareDone;
        /** Искать место у знакомой точки маршрута; вернуться к прежнему месту наблюдения. */
        public boolean useAnchor;
        public boolean recognition;
        public boolean strong;
        /** Тики с начала (после accept). */
        public int ticks;

        Plan(Kind kind) { this.kind = kind; }

        public String describe() {
            StringBuilder b = new StringBuilder(kind.name());
            if (variant >= 0) b.append('/').append(variant);
            if (sign) b.append(figure ? " sign+figure" : " sign-only");
            if (ruleBreak) b.append(" RULE");
            if (scare) b.append(scareDone ? " scare(done)" : " scare");
            if (useAnchor) b.append(" anchor");
            if (recognition) b.append(" recog");
            if (strong) b.append(" strong");
            if (forced) b.append(" forced");
            return b.toString();
        }
    }

    private static final int N = Kind.values().length;
    private static final Kind[] OFFERED = {Kind.GHOST, Kind.DOOR, Kind.GLITCH, Kind.CHASE};
    private static final Random RANDOM = new Random();

    private static float budget = ObserverTuning.DIRECTOR_BUDGET_START;
    private static int silence = ObserverTuning.SILENCE_AT_START;
    private static final int[] cooldown = new int[N];
    private static Plan current;
    private static Plan offer;
    private static int offerTtl;
    private static int decide;
    private static long clock;
    private static final long[] strongAt = new long[8];
    static { java.util.Arrays.fill(strongAt, -1L); }
    private static int strongHead;
    private static int ruleCooldown;
    private static Kind lastKind;
    /** Прошлый эпизод был сильным: следующий сильный не начинаем без слабого между ними. */
    private static boolean lastStrong;
    private static int lastGhostVariant = -1;
    private static int sinceCalm;
    private static float pressure;
    private static float pressureTarget;
    private static int stage;
    private static boolean dream;
    private static boolean eligible;
    private static boolean busyNow;
    private static boolean wasBusy;
    private static boolean wasScene;
    private static Kind forceNext;
    private static boolean forceRule;
    /** DoorEcho сообщает, есть ли рядом подходящая дверь: режиссёр не сканирует мир сам. */
    public static boolean doorNearby;

    private static String waiting = "";
    private static String lastRefusal = "-";
    private static long lastRefusalAt = -1;
    /** Когда кончился последний эпизод (часы режиссёра) или -1. */
    private static long lastEndAt = -1;
    private static final ArrayDeque<String> LOG = new ArrayDeque<>();
    /** Отладка: если задан, каждая запись журнала уходит сюда (ObserverClient пишет её в чат). */
    public static Consumer<String> listener;

    private static final double[] spotX = new double[ObserverTuning.RECENT_SPOTS];
    private static final double[] spotY = new double[ObserverTuning.RECENT_SPOTS];
    private static final double[] spotZ = new double[ObserverTuning.RECENT_SPOTS];
    private static final int[] spotV = new int[ObserverTuning.RECENT_SPOTS];
    private static final long[] spotAt = new long[ObserverTuning.RECENT_SPOTS];
    private static int spotN;
    private static int spotHead;

    // ------------------------------------------------------------------
    // Тик и выбор
    // ------------------------------------------------------------------

    /**
     * Раз в тик (не на паузе). target — давление без сглаживания 0..1; busy — открыт экран (документ, дневник, терминал...);
     * tired — контекст «устал» (или шприц, или мир снов); scene — идёт сцена в кровати или её тишина.
     */
    public static void tick(float target, int stageNow, boolean busy, boolean inDream, boolean tired, boolean scene) {
        clock++;
        stage = Math.max(0, Math.min(5, stageNow));
        dream = inDream;
        eligible = tired;
        busyNow = busy;
        pressureTarget = clamp01(target);
        float d = pressureTarget - pressure;
        pressure = clamp01(pressure + Math.max(-ObserverTuning.PRESSURE_FALL_PER_TICK,
                Math.min(ObserverTuning.PRESSURE_RISE_PER_TICK, d)));
        budget = DirectorRules.regen(budget, pressure, stage);
        if (silence > 0) silence--;
        if (ruleCooldown > 0) ruleCooldown--;
        for (int i = 0; i < N; i++) if (cooldown[i] > 0) cooldown[i]--;

        if (scene) {
            if (offer != null) drop("bed scene");
            if (current != null && current.kind != Kind.BED) end(current.kind, Outcome.ABORT);
            wasScene = true;
            waiting = "bed scene";
            return;
        }
        if (wasScene) {
            wasScene = false;
            silence = Math.max(silence, ObserverTuning.SILENCE_AFTER_SCENE);
        }
        if (busy) {
            if (offer != null) drop("screen open");
            wasBusy = true;
            waiting = "screen open";
            return;
        }
        if (wasBusy) {
            wasBusy = false;
            silence = Math.max(silence, ObserverTuning.SILENCE_AFTER_SCREEN);
        }
        if (current != null) {
            if (++current.ticks > ObserverTuning.EP_MAX_TICKS[current.kind.ordinal()]) {
                record("guard: " + current.kind + " ran too long");
                end(current.kind, Outcome.ABORT);
            }
            waiting = "episode " + (current == null ? "-" : current.kind);
            return;
        }
        if (offer != null) {
            if (--offerTtl <= 0) refuse(offer, "not taken in time");
            return;
        }
        if (--decide > 0) return;
        decide = ObserverTuning.DECIDE_TICKS;
        if (silence > 0) { waiting = "silence " + silence; return; }
        if (stage < 1) { waiting = "stage 0: no episodes"; return; }
        if (!eligible) { waiting = "not tired"; return; }
        pick();
    }

    private static void pick() {
        float[] w = new float[N];
        float total = 0f;
        String why = "";
        for (Kind k : OFFERED) {
            if (dream && k != Kind.GHOST && k != Kind.CHASE) continue; // во сне нет дверей и сбоев
            String r = blocked(k);
            if (r != null) {
                why = k + ": " + r;
                continue;
            }
            float wt = ObserverTuning.EP_WEIGHT[k.ordinal()];
            if (ObserverTuning.EP_STRONG[k.ordinal()]) wt *= 1f + pressure * ObserverTuning.PRESSURE_STRONG_BIAS;
            // Один вид не идёт два раза подряд; у GHOST повтор запрещён по варианту места (ObserverClient)
            if (k == lastKind && k != Kind.GHOST) wt = 0f;
            w[k.ordinal()] = wt;
            total += wt;
        }
        if (total <= 0f) {
            waiting = why.isEmpty() ? "nothing allowed" : why;
            return;
        }
        float roll = RANDOM.nextFloat() * total;
        for (Kind k : OFFERED) {
            float wt = w[k.ordinal()];
            if (wt <= 0f) continue;
            roll -= wt;
            if (roll < 0f) {
                offer(plan(k));
                return;
            }
        }
    }

    /** Причина, по которой вид сейчас нельзя начать, или null. */
    private static String blocked(Kind k) {
        int i = k.ordinal();
        if (stage < ObserverTuning.EP_MIN_STAGE[i]) return "stage " + stage + " < " + ObserverTuning.EP_MIN_STAGE[i];
        if (cooldown[i] > 0) return "cooldown " + cooldown[i];
        if (k != Kind.BED && budget < ObserverTuning.EP_COST[i]) return String.format("budget %.0f < %.0f", budget, ObserverTuning.EP_COST[i]);
        if (ObserverTuning.EP_STRONG[i] && lastStrong) return "strong after strong";
        if (ObserverTuning.EP_STRONG[i] && !DirectorRules.strongRoom(strongRecent(), stage)) return "strong limit";
        if (k == Kind.DOOR && !doorNearby) return "no door nearby";
        if (k == Kind.CHASE && ObserverState.days < ChaseState.nextDay)
            return "days " + ObserverState.days + " < " + ChaseState.nextDay;
        return null;
    }

    private static Plan plan(Kind k) {
        Plan p = new Plan(k);
        p.strong = ObserverTuning.EP_STRONG[k.ordinal()];
        switch (k) {
            case GHOST -> {
                // Правило взгляда GHOST не нарушает: исчезает всегда, только смотреть дольше (ObserverTuning.gazeTicks)
                p.sign = !dream && stage >= ObserverTuning.SIGN_MIN_STAGE
                        && RANDOM.nextFloat() < ObserverTuning.SIGN_CHANCE;
                p.figure = !p.sign
                        || RANDOM.nextFloat() >= ObserverTuning.NO_CONTINUE_CHANCE * (1f - ObserverTuning.NO_CONTINUE_PRESSURE_CUT * pressure);
                p.scare = dream && stage >= ObserverTuning.SCARE_MIN_STAGE && cooldown[Kind.SCARE.ordinal()] == 0
                        && DirectorRules.mayStartStrong(lastStrong, strongRecent(), stage)
                        && RANDOM.nextFloat() < ObserverTuning.SCARE_PLAN_CHANCE_BY_STAGE[stage];
                p.recognition = !dream && spotN > 0
                        && stage >= ObserverTuning.RECOGNITION_MIN_STAGE && RANDOM.nextFloat() < ObserverTuning.RECOGNITION_CHANCE;
                p.useAnchor = !dream && RANDOM.nextFloat() < ObserverTuning.ANCHOR_CHANCE_BY_STAGE[stage];
                p.strong = false;
            }
            case STILL -> {
                p.ruleBreak = ruleAllowed() && RANDOM.nextFloat() < ObserverTuning.RULE_CHANCE_BY_STAGE[stage];
                p.figure = p.ruleBreak;
                p.strong = p.ruleBreak;
            }
            default -> { }
        }
        return p;
    }

    private static boolean ruleAllowed() {
        return stage >= ObserverTuning.RULE_MIN_STAGE && ruleCooldown == 0 && strongRoom();
    }

    private static void offer(Plan p) {
        offer = p;
        offerTtl = ObserverTuning.OFFER_TTL;
        waiting = "offer " + p.kind;
        record("offer " + p.describe());
    }

    // ------------------------------------------------------------------
    // Для систем-исполнителей
    // ------------------------------------------------------------------

    /** План, предложенный этому виду, или null. */
    public static Plan offered(Kind k) {
        if (ObserverState.completed()) return null; // после финала событий нет
        return offer != null && offer.kind == k ? offer : null;
    }

    /** Контекст просит эпизод (STILL, BED). План или null; причина отказа видна в отладке. Начать: accept(plan). */
    public static Plan request(Kind k) {
        if (ObserverState.completed()) return null; // после финала событий нет
        if (forceNext == k) {
            forceNext = null;
            Plan p = plan(k);
            p.forced = true;
            if (forceRule) forceRule(p);
            offer = p;
            offerTtl = ObserverTuning.OFFER_TTL;
            record("request (forced) " + p.describe());
            return p;
        }
        String r;
        if (busyNow) r = "screen open";
        else if (wasScene) r = "bed scene";
        else if (current != null) r = "episode " + current.kind + " running";
        else if (offer != null) r = "offer " + offer.kind + " pending";
        else if (k == Kind.BED && lastEndAt >= 0 && clock - lastEndAt < ObserverTuning.BED_MIN_GAP_TICKS)
            r = "last episode " + (clock - lastEndAt) / 20 + " s ago";
        else if (k != Kind.BED && silence > 0) r = "silence " + silence;
        else if (k != Kind.BED && !eligible) r = "not tired";
        else r = blocked(k);
        if (r == null) {
            float chance = switch (k) {
                case BED -> Math.min(1f, ObserverTuning.BED_WATCH_CHANCE_BASE + ObserverTuning.BED_WATCH_CHANCE_PER_STAGE
                        * Math.max(0, stage - ObserverTuning.EP_MIN_STAGE[Kind.BED.ordinal()]));
                case STILL -> ObserverTuning.STILL_CHANCE;
                default -> 1f;
            };
            if (RANDOM.nextFloat() >= chance) r = "roll";
        }
        if (r != null) {
            refusal(k, r);
            return null;
        }
        Plan p = plan(k);
        offer = p;
        offerTtl = ObserverTuning.OFFER_TTL;
        record("request " + p.describe());
        return p;
    }

    /** Система нашла место и начинает эпизод. false, если план уже не актуален. */
    public static boolean accept(Plan p) {
        if (p == null || offer != p || current != null) return false;
        offer = null;
        current = p;
        p.ticks = 0;
        budget = DirectorRules.spend(budget, p.kind.ordinal());
        if (p.ruleBreak) ruleCooldown = ObserverTuning.RULE_COOLDOWN;
        if (p.strong) markStrong();
        lastStrong = p.strong;
        lastKind = p.kind;
        if (p.kind == Kind.GHOST) lastGhostVariant = p.variant;
        waiting = "episode " + p.kind;
        record("start " + p.describe());
        return true;
    }

    /** Система не может исполнить план (нет места, светло, нет двери): эпизод не начинается и не откладывается. */
    public static void refuse(Plan p, String reason) {
        if (p == null || offer != p) return;
        offer = null;
        int i = p.kind.ordinal();
        cooldown[i] = Math.max(cooldown[i], ObserverTuning.REFUSE_RETRY_TICKS);
        refusal(p.kind, reason);
    }

    /** Конец эпизода (лишний вызов безопасен: без идущего эпизода этого вида ничего не делает). */
    public static void end(Kind k, Outcome o) {
        if (current == null || current.kind != k) return;
        Plan p = current;
        current = null;
        int i = k.ordinal();
        cooldown[i] = Math.max(cooldown[i], ObserverTuning.EP_COOLDOWN[i]);
        // Прерванный эпизод, который уже шёл какое-то время (игрок слышал шаги или дверь, видел фигуру), считается
        // состоявшимся: иначе экран, открытый на звук шагов, давал бы новый эпизод через полминуты.
        boolean noticed = o != Outcome.ABORT || p.ticks >= ObserverTuning.ABORT_NOTICED_TICKS;
        int s = DirectorRules.silenceAfter(noticed, p.strong, stage);
        silence = Math.max(silence, s);
        lastEndAt = clock;
        lastStrong = p.strong;
        record("end " + p.describe() + " -> " + o + ", silence " + silence);
        if (noticed && ++sinceCalm >= ObserverTuning.CALM_EVERY) {
            sinceCalm = 0;
            silence = Math.max(silence, ObserverTuning.CALM_TICKS);
            record("calm " + ObserverTuning.CALM_TICKS);
        }
    }

    /** То же, но только если p всё ещё идущий эпизод (система не закроет чужой эпизод того же вида). */
    public static void end(Plan p, Outcome o) {
        if (p != null && current == p) end(p.kind, o);
    }

    /**
     * [новое] Погоня началась не по предложению режиссёра, а серверной засадой (I_AMBUSH).
     * Держим для неё те же кулдаун и тишину, чтобы следом не наложился второй эпизод.
     */
    public static void externalChase() {
        int i = Kind.CHASE.ordinal();
        cooldown[i] = Math.max(cooldown[i], ObserverTuning.EP_COOLDOWN[i]);
        if (offer != null) drop("external chase");
        lastKind = Kind.CHASE;
        lastStrong = true;
        markStrong();
        silence = Math.max(silence, ObserverTuning.SILENCE_AFTER_STRONG);
        lastEndAt = clock;
        record("external chase -> silence " + silence);
    }

    /** Фаза кадра-сюрприза внутри текущего GHOST во сне: true один раз за эпизод и только если её заложил план. */
    public static boolean scareNow() {
        Plan p = current;
        if (p == null || p.kind != Kind.GHOST || !p.scare || p.scareDone) return false;
        if (cooldown[Kind.SCARE.ordinal()] > 0) return false;
        p.scareDone = true;
        p.strong = true;
        lastStrong = true;
        cooldown[Kind.SCARE.ordinal()] = ObserverTuning.EP_COOLDOWN[Kind.SCARE.ordinal()];
        markStrong();
        record("scare in " + p.describe());
        return true;
    }

    public static Plan current() { return current; }
    public static float pressure() { return pressure; }
    public static int silence() { return silence; }
    public static float budget() { return budget; }
    public static int lastGhostVariant() { return lastGhostVariant; }
    public static String lastRefusal() { return lastRefusal; }
    public static int strongRecent() {
        int n = 0;
        for (long t : strongAt) if (t >= 0 && clock - t < ObserverTuning.STRONG_WINDOW_TICKS) n++;
        return n;
    }

    /** Игрок проснулся / вышел из мира снов: долгая тишина, чтобы найти след или записку. */
    public static void onWake() {
        silence = Math.max(silence, ObserverTuning.SILENCE_AFTER_WAKE);
    }

    /** Вход в мир снов: сон короткий, поэтому тишина не дольше DREAM_SILENCE_CAP. */
    public static void onDreamEnter() {
        silence = Math.min(silence, ObserverTuning.DREAM_SILENCE_CAP);
        decide = 1;
    }

    // ------------------------------------------------------------------
    // Недавние места наблюдения (только этот вход в мир)
    // ------------------------------------------------------------------

    public static void rememberSpot(double x, double y, double z, int variant) {
        spotX[spotHead] = x;
        spotY[spotHead] = y;
        spotZ[spotHead] = z;
        spotV[spotHead] = variant;
        spotAt[spotHead] = clock;
        spotHead = (spotHead + 1) % spotX.length;
        if (spotN < spotX.length) spotN++;
    }

    /** Множитель веса места: рядом с недавним местом наблюдения он ниже (защита от привыкания). */
    public static float spotWeight(double x, double z) {
        double r2 = ObserverTuning.RECENT_SPOT_RADIUS * ObserverTuning.RECENT_SPOT_RADIUS;
        for (int i = 0; i < spotN; i++) {
            if (clock - spotAt[i] > ObserverTuning.RECENT_SPOT_TICKS) continue;
            double dx = spotX[i] - x;
            double dz = spotZ[i] - z;
            if (dx * dx + dz * dz < r2) return ObserverTuning.RECENT_SPOT_WEIGHT;
        }
        return 1f;
    }

    /** Одно из прежних мест наблюдения {x, y, z, вариант V_*} (для узнавания) или null. */
    public static double[] familiarSpot() {
        if (spotN == 0) return null;
        int i = RANDOM.nextInt(spotN);
        return new double[]{spotX[i], spotY[i], spotZ[i], spotV[i]};
    }

    // ------------------------------------------------------------------
    // Служебное
    // ------------------------------------------------------------------

    private static void markStrong() {
        strongAt[strongHead] = clock;
        strongHead = (strongHead + 1) % strongAt.length;
    }

    private static boolean strongRoom() {
        return DirectorRules.strongRoom(strongRecent(), stage);
    }

    private static void drop(String reason) {
        refusal(offer.kind, reason);
        offer = null;
    }

    private static void refusal(Kind k, String reason) {
        lastRefusal = k + ": " + reason;
        lastRefusalAt = clock;
        record("refuse " + lastRefusal);
    }

    private static void record(String s) {
        String line = (clock / 20) + "s " + s;
        LOG.addLast(line);
        while (LOG.size() > 12) LOG.removeFirst();
        if (listener != null) listener.accept(line);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static void forceRule(Plan p) {
        p.ruleBreak = true;
        p.figure = true;
        p.sign = false;
        p.strong = true;
        p.recognition = spotN > 0;
    }

    // ------------------------------------------------------------------
    // Отладка (/hypersomnia_observer director ...): обычному игроку не показывается
    // ------------------------------------------------------------------

    /** Полный бюджет, без тишины и кулдаунов. */
    public static void fill() {
        budget = ObserverTuning.DIRECTOR_BUDGET_CAP;
        silence = 0;
        ruleCooldown = 0;
        java.util.Arrays.fill(cooldown, 0);
        java.util.Arrays.fill(strongAt, -1L);
        lastStrong = false;
        lastEndAt = -1;
    }

    public static void calm(int ticks) {
        silence = Math.max(silence, ticks);
        if (offer != null) drop("debug calm");
    }

    /** Следующий эпизод этого вида без тишины, бюджета и стадии (место всё равно проверяется). rule — с нарушением правила
     *  (только STILL). Тишина не обнуляется: после отказа места режиссёр не подставит другой эпизод сразу. */
    public static void force(Kind k, boolean rule) {
        if (current != null) end(current.kind, Outcome.ABORT);
        if (offer != null) drop("debug force");
        if (k == Kind.BED || k == Kind.STILL) {
            forceNext = k;
            forceRule = rule;
            record("force next request " + k + (rule ? " RULE" : ""));
            return;
        }
        Plan p = plan(k);
        p.forced = true;
        if (rule) forceRule(p);
        offer(p);
    }

    public static List<String> status() {
        List<String> out = new ArrayList<>();
        out.add(String.format("stage %d%s, pressure %.2f (target %.2f), budget %.0f/%.0f, silence %d s, calm after %d more",
                stage, ObserverState.stageOverride >= 0 ? " (forced)" : "", pressure, pressureTarget, budget, ObserverTuning.DIRECTOR_BUDGET_CAP, silence / 20,
                ObserverTuning.CALM_EVERY - sinceCalm));
        out.add("episode: " + (current == null ? "-" : current.describe() + " t=" + current.ticks)
                + ", offer: " + (offer == null ? "-" : offer.describe()));
        out.add("waiting: " + (waiting.isEmpty() ? "-" : waiting));
        out.add("last refusal: " + lastRefusal + (lastRefusalAt < 0 ? "" : " (" + (clock - lastRefusalAt) / 20 + " s ago)"));
        out.add(String.format("strong %d/%d per %d s, rule cooldown %d s, last kind %s, gaze %d ticks, chase from day %d (now %d)",
                strongRecent(), DirectorRules.strongLimit(stage), ObserverTuning.STRONG_WINDOW_TICKS / 20,
                ruleCooldown / 20, lastKind, ObserverTuning.gazeTicks(stage), ChaseState.nextDay, ObserverState.days));
        StringBuilder c = new StringBuilder("cooldowns s:");
        for (Kind k : Kind.values()) c.append(' ').append(k.name().toLowerCase()).append('=').append(cooldown[k.ordinal()] / 20);
        out.add(c.toString());
        out.add("recent spots " + spotN + ", door nearby " + doorNearby);
        return out;
    }

    public static List<String> journal() {
        return new ArrayList<>(LOG);
    }

    public static void reset() {
        budget = ObserverTuning.DIRECTOR_BUDGET_START;
        silence = ObserverTuning.SILENCE_AT_START;
        java.util.Arrays.fill(cooldown, 0);
        java.util.Arrays.fill(strongAt, -1L);
        current = null;
        offer = null;
        offerTtl = 0;
        decide = 0;
        ruleCooldown = 0;
        lastKind = null;
        lastStrong = false;
        lastGhostVariant = -1;
        sinceCalm = 0;
        pressure = 0f;
        pressureTarget = 0f;
        wasBusy = false;
        wasScene = false;
        busyNow = false;
        forceNext = null;
        forceRule = false;
        doorNearby = false;
        spotN = 0;
        spotHead = 0;
        waiting = "";
        lastRefusal = "-";
        lastRefusalAt = -1;
        lastEndAt = -1;
        LOG.clear();
    }
}
