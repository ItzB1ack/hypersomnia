package com.hypersomnia.chase;

import com.hypersomnia.DebugMode;
import com.hypersomnia.FatigueData;
import com.hypersomnia.FatigueTuning;
import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.ObserverProgressData;
import com.hypersomnia.dream.DreamClock;
import com.hypersomnia.dream.DreamDimension;
import com.hypersomnia.dream.DreamTransition;
import com.hypersomnia.finale.FinaleLogic;
import com.hypersomnia.lab.LabOutage;
import com.hypersomnia.lab.LabProtection;
import com.hypersomnia.network.ChasePayload;
import com.hypersomnia.network.ChaseRequestPayload;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Погоня (сервер). Фигура бежит к игроку кратчайшей дорогой по земле (ChasePath, поиск пути A*): обходит стены, срезает
 * углы, спрыгивает, лезет по лестницам — бег по кругу не спасает. Если пути к игроку нет (он на столбе, за пропастью,
 * слишком далеко в лабиринте) — идёт к ближайшей достижимой точке его следа (точки, где игрок прошёл) и дальше по следу:
 * сквозь стены не ходит никогда. Просит погоню клиентский режиссёр (ObserverDirector.Kind.CHASE); решает и ведёт сервер: стадия, дни,
 * время суток, безопасность места, взгляд, двери, поимка и последствия. Клиент только рисует ту же фигуру
 * (ObserverClient, Mode.CHASE) по пакетам ChasePayload и не может «потерять» её: если он её не показывает, просит STOP.
 * <p>
 * Фазы: WARN — стоит далеко на следе (заметил его — сразу бежит); RUN — бежит; BLOCKED — на пути закрытая
 * дверь (обхода нет или он намного длиннее) или стена на следе: стучит и уходит, если не откроют; CAUGHT — чёрный кадр.
 * Взгляд в погоне его НЕ прогоняет (решение пользователя): спасают дверь, время и в лаборатории — питание (LabOutage).
 * В лаборатории погоня бывает только как «обесточено» (LabOutage): этаж гаснет, лифт стоит, спасает щит этажа у лифта;
 * темнота своя, поэтому ночь не нужна; пакеты таких погонь несут флаг ChasePayload.I_DARK (клиент видит глаза издалека).
 * Поимка до стадии DAMAGE_MIN_STAGE забирает вещь из руки (вернётся у кровати через 1-3 дня, рядом с его шагами),
 * прибавляет усталость и безумие; с неё — ещё и ранит (тип урона hypersomnia:fragment, мимо брони). Пока он бежит,
 * лечь спать нельзя («рядом монстры»).
 * <p>
 * Тестовые команды для мультиплеера (/hypersomnia_shard, только оператор): watch — фигура встаёт перед другим игроком
 * и смотрит заданное время (PH_WATCH, не бежит и не ловит, день погони не тратит); chase [teach] — настоящая погоня
 * без требования следа, стадии, ночи и дней; stop/status. Фигуру видит только цель: она рисуется на её клиенте.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class ChaseHandler {
    private ChaseHandler() {}

    public static final ResourceKey<DamageType> FRAGMENT_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "fragment"));

    /** След игрока: точки на земле, мир и позиция прошлого тика (для скачков). В память не сохраняется. */
    private static final class Trail {
        final ArrayDeque<Vec3> points = new ArrayDeque<>();
        ResourceKey<Level> dim;
        Vec3 last;
        /** Скорость игрока (блоков за тик, сглажена): по ней он бежит на упреждение. */
        Vec3 vel = Vec3.ZERO;
    }

    private static final class Run {
        int id;
        ResourceKey<Level> dim;
        int stage;
        boolean teach;
        boolean fromDoor;
        /** Тестовое наблюдение (команда /hypersomnia_shard watch): стоит на месте, не бежит и не ловит. */
        boolean watch;
        int watchLeft;
        /** Сервер вывел фигуру сам (тестовая команда): клиенту не нужен свой запрос погони. */
        boolean pushed;
        int phase = ChasePayload.PH_WARN;
        int phaseTicks;
        int warnTicks;
        int runTicks;
        int stare;
        Vec3 pos;
        /** Где игрок был, когда он последний раз бежал к нему напрямую: туда он идёт, потеряв игрока из виду. */
        Vec3 lastSeen;
        final ArrayList<Vec3> path = new ArrayList<>();
        int next;
        /** Путь A* (к игроку или к точке следа), по нему он идёт сейчас; null — идёт по следу. */
        ChasePath.Route route;
        int ri;
        /** Куда вёл путь (позиция игрока при поиске) и сколько тиков с поиска. */
        Vec3 routeGoal;
        int sinceRepath = 1000;
        boolean routeFailed;
        /** Идёт по следу (пути к игроку нет или путь A* довёл до точки следа). */
        boolean onTrail;
        /** Закрытая дверь, в которую он упёрся (BLOCKED), иначе null. */
        BlockPos door;
        /** «Обесточено»: этаж лаборатории в темноте (LabOutage), иначе -1. */
        int outage = -1;
        /** [новое] Погоня из напольных часов в мире снов: поимка будит, а не ведёт в палату. */
        boolean dreamClock;
        /** [новое] Засада: фигура появилась вплотную; взгляд не сокращает WARN, клиент даёт кадр-испуг. */
        boolean ambush;
    }

    private static final Map<UUID, Trail> TRAILS = new HashMap<>();
    private static final Map<UUID, Run> RUNS = new HashMap<>();
    private static int nextId = 1;
    public static boolean activeFor(ServerPlayer player) { return RUNS.containsKey(player.getUUID()); }

    // ------------------------------------------------------------------
    // Тик игрока: след и шаг погони
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Run run = RUNS.get(player.getUUID());
        Trail t = TRAILS.computeIfAbsent(player.getUUID(), k -> new Trail());
        ResourceKey<Level> dim = player.level().dimension();
        Vec3 at = player.position();
        boolean jumped = false;
        if (!dim.equals(t.dim)) {
            t.points.clear();
            t.dim = dim;
            t.last = null;
            jumped = true;
        }
        if (t.last != null && t.last.distanceToSqr(at) > ChaseTuning.TRAIL_TELEPORT * ChaseTuning.TRAIL_TELEPORT) {
            t.points.clear();
            jumped = true;
        }
        Vec3 dv = t.last == null || jumped ? Vec3.ZERO : at.subtract(t.last);
        t.vel = t.vel.scale(1.0 - ChaseTuning.VEL_SMOOTH).add(dv.scale(ChaseTuning.VEL_SMOOTH));
        t.last = at;
        Vec3 added = null;
        if (player.isAlive() && !player.isSpectator() && !player.isPassenger() && !player.isFallFlying()
                && (player.onGround() || player.onClimbable() || player.isInWater())) {
            Vec3 prev = t.points.peekLast();
            if (prev == null || prev.distanceToSqr(at) >= ChaseTuning.TRAIL_STEP * ChaseTuning.TRAIL_STEP) {
                t.points.addLast(at);
                added = at;
                while (t.points.size() > ChaseTuning.TRAIL_MAX) t.points.removeFirst();
            }
        }
        if (run == null) return;
        if (jumped) {
            end(player, run, ChasePayload.O_ABORT);
            return;
        }
        if (added != null && !run.watch) run.path.add(added);
        step(player, run);
    }

    private static void step(ServerPlayer player, Run run) {
        if (!player.isAlive() || player.isSleeping() || !player.level().dimension().equals(run.dim)
                || FinaleLogic.completed(player)) {
            end(player, run, ChasePayload.O_ABORT);
            return;
        }
        run.phaseTicks++;
        if (run.watch) {
            if (--run.watchLeft <= 0) {
                end(player, run, ChasePayload.O_WATCH);
                return;
            }
            send(player, run, ChasePayload.PH_WATCH, ChasePayload.O_NONE);
            return;
        }
        Vec3 feet = player.position();
        if (run.pos.distanceTo(feet) > ChaseTuning.ABORT_DIST) {
            end(player, run, ChasePayload.O_ABORT);
            return;
        }
        if (run.phase == ChasePayload.PH_CAUGHT) {
            if (run.phaseTicks >= ChaseTuning.CAUGHT_TICKS) {
                end(player, run, ChasePayload.O_CAUGHT);
                // поимка = сон: run уже снят, поэтому смена измерения его не прервёт
                if (run.dreamClock) DreamClock.onCaught(player);
                else DreamTransition.enterNightmare(player, com.hypersomnia.nightmare.NightmareDefs.WARD_CORRIDOR);
            } else send(player, run, ChasePayload.PH_CAUGHT, ChasePayload.O_NONE);
            return;
        }
        // Взгляд в погоне не прогоняет. Пока он стоит на следе: заметил его (WARN_SEEN_TICKS взгляда) — сразу бежит.
        // В засаде он уже стоит вплотную: взгляд не должен обрывать короткий кадр-испуг раньше времени.
        if (run.phase == ChasePayload.PH_WARN && !run.ambush && lookedAt(player, run.pos)) run.stare++;
        switch (run.phase) {
            case ChasePayload.PH_WARN -> {
                if (run.phaseTicks >= run.warnTicks || run.stare >= ChaseTuning.WARN_SEEN_TICKS) {
                    run.stare = 0;
                    phase(run, ChasePayload.PH_RUN);
                }
            }
            case ChasePayload.PH_RUN -> {
                if (++run.runTicks > ChaseTuning.RUN_TICKS_BY_STAGE[run.stage]) {
                    end(player, run, ChasePayload.O_ESCAPED);
                    return;
                }
                move(player, run);
                double d = run.pos.distanceTo(feet);
                if (run.teach) {
                    if (d <= ChaseTuning.TEACH_VANISH_DIST) {
                        end(player, run, ChasePayload.O_TAUGHT);
                        return;
                    }
                } else if (d <= ChaseTuning.CATCH_DIST) {
                    caught(player, run);
                    return;
                }
            }
            case ChasePayload.PH_BLOCKED -> {
                if (canAdvance(player, run)) {
                    run.door = null;
                    phase(run, ChasePayload.PH_RUN);
                }
                else if (run.phaseTicks >= ChaseTuning.BLOCKED_TICKS) {
                    end(player, run, ChasePayload.O_BLOCKED);
                    return;
                }
            }
            default -> { }
        }
        send(player, run, run.phase, ChasePayload.O_NONE);
    }

    private static void phase(Run run, int phase) {
        run.phase = phase;
        run.phaseTicks = 0;
    }

    /**
     * Шаг бега. Рядом (DIRECT_DIST), на виду и по полу без ям — прямо к игроку. Иначе по пути A* (ChasePath) к игроку,
     * путь пересчитывается раз в REPATH_TICKS и когда игрок ушёл от цели. Пути нет — к самой свежей достижимой точке
     * следа и дальше по следу. Закрытая дверь впереди — BLOCKED (стучит); путь перегородили блоком — новый путь.
     */
    private static void move(ServerPlayer player, Run run) {
        ChasePath nav = new ChasePath(player.serverLevel());
        double speed = run.teach ? ChaseTuning.TEACH_SPEED : ChaseTuning.SPEED_BY_STAGE[run.stage];
        Vec3 target = aim(nav, player);
        double dist = run.pos.distanceTo(target);
        // рывок: вплотную он быстрее (учебная не ловит — без рывка)
        double left = !run.teach && dist < ChaseTuning.LUNGE_DIST ? speed * ChaseTuning.LUNGE_SPEED : speed;
        run.sinceRepath++;
        if (direct(player, run, nav, target)) {
            run.pos = toward(run.pos, lead(player, run, nav, target, speed, dist), left);
            run.lastSeen = target;
            run.next = run.path.size();
            run.route = null;
            run.onTrail = false;
            run.routeFailed = false;
            run.sinceRepath = ChaseTuning.REPATH_TICKS; // потерял из виду — сразу новый путь
            return;
        }
        if (run.lastSeen != null) {
            run.path.add(run.next, run.lastSeen);
            run.lastSeen = null;
        }
        boolean stale = run.route == null || run.ri >= run.route.size() || run.routeGoal == null
                || run.routeGoal.distanceToSqr(target) > ChaseTuning.REPATH_MOVE * ChaseTuning.REPATH_MOVE;
        int wait = run.routeFailed ? ChaseTuning.REPATH_FAIL_TICKS
                : stale ? ChaseTuning.REPATH_MIN_TICKS : ChaseTuning.REPATH_TICKS;
        if (run.sinceRepath >= wait) repath(run, nav, target);
        if (run.route != null) {
            left = followRoute(player, run, nav, left);
            if (run.route != null || run.phase != ChasePayload.PH_RUN) return;
        }
        if (run.onTrail) followTrail(player, run, left);
    }

    /**
     * Новый путь к игроку. Недостижим (за водой, на высоком столбе) — путь к ближайшей достижимой клетке; если она
     * не под игроком, сначала пробует точку следа (TRAIL_JOIN_TRIES попыток). Совсем нет пути — по следу.
     */
    private static void repath(Run run, ChasePath nav, Vec3 target) {
        run.sinceRepath = 0;
        run.routeGoal = target;
        run.ri = 0;
        ChasePath.Route r = nav.find(run.pos, target, ChaseTuning.PATH_MAX_NODES, true);
        if (r != null && r.partial && !under(r, target)) {
            for (int t = 0, i = run.path.size() - 1; t < ChaseTuning.TRAIL_JOIN_TRIES && i >= run.next;
                 t++, i -= ChaseTuning.TRAIL_JOIN_STEP) {
                ChasePath.Route j = nav.find(run.pos, run.path.get(i), ChaseTuning.TRAIL_JOIN_NODES);
                if (j != null) {
                    j.trailIndex = i;
                    r = j;
                    break;
                }
            }
        }
        run.route = r;
        run.routeFailed = r == null || r.partial;
        run.onTrail = r == null;
    }

    /** Ближайшая достижимая клетка пути почти под целью (по горизонтали): игрок выше, чем он может залезть. */
    private static boolean under(ChasePath.Route r, Vec3 target) {
        Vec3 e = r.points.get(r.size() - 1);
        return Math.hypot(e.x - target.x, e.z - target.z) <= ChaseTuning.PATH_UNDER_DIST;
    }

    /** Ход по прямой без стен: два луча по бокам тела, чтобы не цеплять угол блока. */
    private static boolean wide(ServerPlayer player, Vec3 a, Vec3 b) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double len = Math.hypot(dx, dz);
        if (len < 1.0E-4) return true;
        Vec3 n = new Vec3(-dz / len * ChaseTuning.CORNER_SIDE, 0.0, dx / len * ChaseTuning.CORNER_SIDE);
        return clear(player, a.add(n), b.add(n)) && clear(player, a.subtract(n), b.subtract(n));
    }

    /** Идёт по пути A*, срезая углы; возвращает непройденный остаток шага. */
    private static double followRoute(ServerPlayer player, Run run, ChasePath nav, double left) {
        ChasePath.Route r = run.route;
        // Срез углов: точка впереди на том же уровне, пол по прямой есть, тело проходит — идём сразу к ней
        int far = run.ri;
        for (int k = run.ri + 1; k < Math.min(r.size(), run.ri + 1 + ChaseTuning.CORNER_LOOKAHEAD); k++) {
            Vec3 q = r.points.get(k);
            if (Math.abs(q.y - run.pos.y) > 0.05 || nav.closedDoor(r.cells.get(k))
                    || !nav.floorAlong(run.pos, q) || !wide(player, run.pos, q)) break;
            far = k;
        }
        run.ri = far;
        while (left > 1.0E-4 && run.ri < r.size()) {
            BlockPos cell = r.cells.get(run.ri);
            Vec3 p = r.points.get(run.ri);
            if (nav.closedDoor(cell)) {
                // дверь закрыта: подойти вплотную и стучать
                double d = run.pos.distanceTo(p);
                double go = Math.min(left, Math.max(0.0, d - ChaseTuning.DOOR_STOP));
                run.pos = toward(run.pos, p, go);
                if (d - go <= ChaseTuning.DOOR_STOP + 1.0E-3) {
                    run.door = cell;
                    phase(run, ChasePayload.PH_BLOCKED);
                }
                return 0;
            }
            if (nav.walledNow(cell)) {
                // перегородили: новый путь на следующем тике
                run.route = null;
                run.routeFailed = false;
                run.onTrail = false;
                run.sinceRepath = ChaseTuning.REPATH_TICKS;
                return 0;
            }
            double d = run.pos.distanceTo(p);
            if (d <= left) {
                run.pos = p;
                run.ri++;
                left -= d;
            } else {
                run.pos = toward(run.pos, p, left);
                left = 0;
            }
        }
        if (run.ri >= r.size()) {
            // дошёл до точки следа — дальше по следу; дошёл до игрока (он ушёл) — новый путь на следующем тике
            if (r.trailIndex >= 0) {
                run.next = Math.min(r.trailIndex + 1, run.path.size());
                run.onTrail = true;
            }
            run.route = null;
            run.sinceRepath = Math.max(run.sinceRepath, ChaseTuning.REPATH_TICKS);
        }
        return left;
    }

    /** По точкам следа; проход между точками закрыт (дверь, блок) — BLOCKED. */
    private static void followTrail(ServerPlayer player, Run run, double left) {
        while (left > 1.0E-4 && run.next < run.path.size()) {
            Vec3 p = run.path.get(run.next);
            if (!clear(player, run.pos, p)) {
                phase(run, ChasePayload.PH_BLOCKED);
                return;
            }
            double d = run.pos.distanceTo(p);
            if (d <= left) {
                run.pos = p;
                run.next++;
                left -= d;
            } else {
                run.pos = toward(run.pos, p, left);
                left = 0;
            }
        }
        if (run.next > 256 && run.route == null) {
            run.path.subList(0, run.next).clear();
            run.next = 0;
        }
    }

    /**
     * Упреждение: бежит туда, где игрок будет через dist/speed тиков (не больше LEAD_MAX_TICKS; вплотную —
     * LEAD_NEAR_TICKS), по его скорости. Так он срезает круги: по кругу от него не убежать. Точка в стене или над ямой —
     * прямо к игроку.
     */
    private static Vec3 lead(ServerPlayer player, Run run, ChasePath nav, Vec3 target, double speed, double dist) {
        Trail t = TRAILS.get(player.getUUID());
        if (t == null) return target;
        double tau = dist < ChaseTuning.LUNGE_DIST ? ChaseTuning.LEAD_NEAR_TICKS
                : Math.min(dist / Math.max(speed, 0.05), ChaseTuning.LEAD_MAX_TICKS);
        Vec3 p = target.add(t.vel.x * tau, 0.0, t.vel.z * tau);
        if (p.distanceToSqr(target) < 1.0E-4) return target;
        if (!clear(player, run.pos, p) || !clear(player, target, p) || !nav.floorAlong(run.pos, p)) return target;
        return p;
    }

    /** Куда бежать: к игроку, а если он в прыжке — к полу под ним (не взлетать за ним в воздух). */
    private static Vec3 aim(ChasePath nav, ServerPlayer player) {
        Vec3 p = player.position();
        if (player.onGround() || player.isInWater() || player.onClimbable()) return p;
        BlockPos c = nav.locate(p, 3);
        if (c == null) return p;
        double f = nav.floor(c.getX(), c.getY(), c.getZ());
        return Double.isNaN(f) ? p : new Vec3(p.x, f, p.z);
    }

    private static boolean direct(ServerPlayer player, Run run, ChasePath nav, Vec3 target) {
        return run.pos.distanceTo(target) <= ChaseTuning.DIRECT_DIST && Math.abs(target.y - run.pos.y) <= 1.5
                && clear(player, run.pos, target) && nav.floorAlong(run.pos, target);
    }

    /** Можно ли снова бежать после BLOCKED: дверь открыли, игрок сам вышел к нему или проход на следе свободен. */
    private static boolean canAdvance(ServerPlayer player, Run run) {
        ChasePath nav = new ChasePath(player.serverLevel());
        if (direct(player, run, nav, aim(nav, player))) return true;
        if (run.door != null) return !nav.closedDoor(run.door);
        return run.next < run.path.size() && clear(player, run.pos, run.path.get(run.next));
    }

    private static Vec3 toward(Vec3 from, Vec3 to, double step) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len <= step || len < 1.0E-6) return to;
        return from.add(d.scale(step / len));
    }

    /** Проход между точками свободен, если свободен хотя бы один из лучей (низкий — потолки, высокий — ступени). */
    private static boolean clear(ServerPlayer player, Vec3 a, Vec3 b) {
        return ray(player, a, b, ChaseTuning.RAY_LOW) || ray(player, a, b, ChaseTuning.RAY_HIGH);
    }

    private static boolean ray(ServerPlayer player, Vec3 a, Vec3 b, double h) {
        Vec3 from = a.add(0, h, 0);
        Vec3 to = b.add(0, h, 0);
        if (from.distanceToSqr(to) < 1.0E-6) return true;
        return player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .getType() == HitResult.Type.MISS;
    }

    /** Игрок смотрит на него: точка тела в конусе взгляда и ничем не закрыта (стекло не закрывает). */
    private static boolean lookedAt(ServerPlayer player, Vec3 feet) {
        return visible(player, player.getEyePosition(), player.getViewVector(1.0f),
                Math.cos(Math.toRadians(ChaseTuning.STARE_HALF_DEG)), feet);
    }

    private static boolean visible(ServerPlayer player, Vec3 eye, Vec3 look, double cosMax, Vec3 feet) {
        for (double off : ChaseTuning.STARE_POINTS) {
            Vec3 t = feet.add(0, off, 0);
            Vec3 v = t.subtract(eye);
            double len = v.length();
            if (len < 0.01) return true;
            if (v.dot(look) / len < cosMax) continue;
            if (player.level().clip(new ClipContext(eye, t, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player))
                    .getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Поимка и конец
    // ------------------------------------------------------------------

    private static void caught(ServerPlayer player, Run run) {
        phase(run, ChasePayload.PH_CAUGHT);
        send(player, run, ChasePayload.PH_CAUGHT, ChasePayload.O_NONE);
        // Погоня из часов: урон, вещи и усталость не нужны — последствия выдаются при пробуждении.
        if (run.dreamClock) return;
        FatigueData f = player.getData(ModAttachments.FATIGUE);
        f.addFatigue(Math.round(FatigueTuning.MAX_FATIGUE * ChaseTuning.FATIGUE_ON_CATCH));
        f.addMadness(ChaseTuning.MADNESS_ON_CATCH);
        if (run.stage >= ChaseTuning.DAMAGE_MIN_STAGE) {
            player.hurt(damage(player.serverLevel()), ChaseTuning.DAMAGE);
        } else {
            take(player);
        }
        // Готовим портал пока идёт чёрный кадр, чтобы его сущности успели загрузиться.
        if (player.isAlive()) com.hypersomnia.nightmare.NightmareBuilder.ensureBuilt(
                player.server, com.hypersomnia.nightmare.NightmareDefs.WARD_CORRIDOR);
    }

    private static DamageSource damage(ServerLevel level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(FRAGMENT_DAMAGE));
    }

    /** Забирает вещь из основной руки (или из второй). Предметы мода (дневник, документы, шприцы) не трогает. */
    private static void take(ServerPlayer player) {
        ChaseData data = player.getData(ModAttachments.CHASE);
        if (data.taken().size() >= ChaseTuning.TAKEN_MAX) return;
        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack s = player.getItemInHand(hand);
        if (!takeable(s)) {
            hand = InteractionHand.OFF_HAND;
            s = player.getItemInHand(hand);
            if (!takeable(s)) return;
        }
        int days = player.getData(ModAttachments.OBSERVER).getDays();
        data.addTaken(s.copy(), days + Mth.randomBetweenInclusive(player.getRandom(),
                ChaseTuning.RETURN_DAYS_MIN, ChaseTuning.RETURN_DAYS_MAX));
        player.setItemInHand(hand, ItemStack.EMPTY);
    }

    private static boolean takeable(ItemStack s) {
        return !s.isEmpty() && !Hypersomnia.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace());
    }

    private static void end(ServerPlayer player, Run run, int outcome) {
        RUNS.remove(player.getUUID());
        send(player, run, ChasePayload.PH_END, outcome);
        dropped(player, run);
    }

    /** Погоня снята (конец, STOP клиента, выход): после «обесточено» свет вернётся сам (LabOutage.chaseEnded). */
    private static void dropped(ServerPlayer player, Run run) {
        if (run != null && run.outage >= 0) LabOutage.chaseEnded(player.server.overworld());
    }

    /** Свет вернулся (LabOutage.restore): погони «обесточено» кончаются — он исчезает (поимку не прерываем). */
    public static void outageOver(MinecraftServer server) {
        List<UUID> ids = new ArrayList<>();
        for (Map.Entry<UUID, Run> e : RUNS.entrySet())
            if (e.getValue().outage >= 0 && e.getValue().phase != ChasePayload.PH_CAUGHT) ids.add(e.getKey());
        for (UUID id : ids) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            Run r = RUNS.get(id);
            if (p != null && r != null) end(p, r, ChasePayload.O_ESCAPED);
        }
    }

    private static void send(ServerPlayer player, Run run, int phase, int info) {
        // stare в пакете — 0: взгляд в погоне не работает, пыли «он исчезает» нет. Вне PH_END info — флаги (I_DARK)
        if (phase != ChasePayload.PH_END && run.outage >= 0) info |= ChasePayload.I_DARK;
        if (phase != ChasePayload.PH_END && run.fromDoor) info |= ChasePayload.I_DOOR;
        if (phase != ChasePayload.PH_END && run.pushed) info |= ChasePayload.I_PUSH;
        if (phase != ChasePayload.PH_END && run.ambush) info |= ChasePayload.I_AMBUSH;
        PacketDistributor.sendToPlayer(player, new ChasePayload(run.id, phase, info, run.pos.x, run.pos.y, run.pos.z,
                0f, player.getData(ModAttachments.CHASE).nextDay()));
    }

    private static void sendIdle(ServerPlayer player, int phase, int info) {
        PacketDistributor.sendToPlayer(player, new ChasePayload(0, phase, info, 0, 0, 0, 0f,
                player.getData(ModAttachments.CHASE).nextDay()));
    }

    // ------------------------------------------------------------------
    // Старт
    // ------------------------------------------------------------------

    public static void handleRequest(ChaseRequestPayload pl, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (pl.action() == ChaseRequestPayload.STOP) {
                dropped(player, RUNS.remove(player.getUUID()));
                return;
            }
            // Неизвестные действия и режимы не должны превращаться в запуск погони.
            if (pl.action() != ChaseRequestPayload.START || pl.force() < 0 || pl.force() > 2) return;
            int force = pl.force() > 0 && player.hasPermissions(2) ? pl.force() : 0;
            int r = start(player, force);
            if (r != 0) sendIdle(player, ChasePayload.PH_REJECT, r);
        });
    }

    /** 0 — погоня началась, иначе причина отказа ChasePayload.R_*. force: 1 — отладка, 2 — отладка и не учебная. */
    public static int start(ServerPlayer player, int force) {
        return start(player, force, false);
    }

    /**
     * [новое] Погоня из напольных часов в мире снов. Без проверок стадии, ночи, дней и следа:
     * часы уже пробили, игрок не нашёл их вовремя. Всегда настоящая (не учебная), чтобы поимка
     * разбудила игрока с последствиями.
     */
    public static int startDreamClock(ServerPlayer player) {
        if (RUNS.containsKey(player.getUUID())) return ChasePayload.R_BUSY;
        if (!player.isAlive() || player.isSpectator() || FinaleLogic.completed(player)) return ChasePayload.R_CONTEXT;
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(DreamDimension.LEVEL)) return ChasePayload.R_CONTEXT;
        if (player.isCreative()) return ChasePayload.R_CONTEXT;
        Vec3 spawn = spotAround(player, level, false);
        if (spawn == null) return ChasePayload.R_SAFETY;

        Run run = new Run();
        run.id = nextId++;
        run.dim = level.dimension();
        run.stage = Math.max(Mth.clamp(player.getData(ModAttachments.OBSERVER).stage(),
                ChaseTuning.MIN_STAGE, 5), ChaseTuning.CATCH_MIN_STAGE);
        run.teach = false;
        run.dreamClock = true;
        // Фигуру вывел сервер: клиенту не нужно запрашивать погоню самому (флаг I_PUSH).
        run.pushed = true;
        run.warnTicks = 10;
        run.pos = spawn;
        RUNS.put(player.getUUID(), run);
        send(player, run, ChasePayload.PH_WARN, ChasePayload.O_NONE);
        return 0;
    }

    /**
     * [новое] Засада: фигура уже стоит вплотную (дверь/контейнер). В отличие от обычной погони старт не ищется
     * по следу и не прячется за спиной — сервер передаёт желаемую точку, а пол под ней проверяется.
     * debug=true обходит стадию, дни и ночь для команды /hypersomnia_shard ambush.
     */
    public static int startAmbush(ServerPlayer player, Vec3 preferred) {
        return startAmbush(player, preferred, false);
    }

    public static int startAmbush(ServerPlayer player, Vec3 preferred, boolean debug) {
        if (RUNS.containsKey(player.getUUID())) return ChasePayload.R_BUSY;
        if (!player.isAlive() || player.isSpectator() || player.isSleeping() || player.isCreative()
                || FinaleLogic.completed(player)) return ChasePayload.R_CONTEXT;
        ServerLevel level = player.serverLevel();
        boolean dream = level.dimension().equals(DreamDimension.LEVEL);
        if (!dream && !level.dimension().equals(Level.OVERWORLD)) return ChasePayload.R_CONTEXT;

        ObserverProgressData prog = player.getData(ModAttachments.OBSERVER);
        ChaseData data = player.getData(ModAttachments.CHASE);
        if (!debug) {
            if (prog.stage() < AmbushTuning.MIN_STAGE) return ChasePayload.R_STAGE;
            if (prog.getDays() < data.nextDay()) return ChasePayload.R_DAYS;
            if (!dream && !nightOrDark(level, player)) return ChasePayload.R_CONTEXT;
        }
        if (!safe(player, level)) return ChasePayload.R_SAFETY;

        int outage = -1;
        if (LabProtection.inside(level, player.blockPosition())) {
            outage = LabOutage.stopFor(player);
            if (outage < 0) return ChasePayload.R_LAB;
        }
        Vec3 spawn = preferred == null ? null : floorNear(level, preferred);
        if (spawn == null) spawn = spotAround(player, level, true);
        if (spawn == null) return ChasePayload.R_TRAIL;

        Run run = new Run();
        run.id = nextId++;
        run.dim = level.dimension();
        run.stage = Math.max(ChaseTuning.CATCH_MIN_STAGE,
                Mth.clamp(prog.stage(), AmbushTuning.MIN_STAGE, 5));
        run.teach = false;
        // Фигуру вывел сервер: клиенту не нужен собственный запрос погони (флаг I_PUSH).
        run.pushed = true;
        run.ambush = true;
        run.warnTicks = AmbushTuning.WARN_TICKS;
        run.pos = spawn;
        if (outage >= 0) {
            if (!LabOutage.begin(level, outage, player)) return ChasePayload.R_LAB;
            run.outage = outage;
            run.warnTicks += LabOutage.flickerTicks();
        }
        // Отладочная засада не тратит день и не двигает расписание.
        if (!debug) data.markStarted(prog.getDays());
        RUNS.put(player.getUUID(), run);
        send(player, run, ChasePayload.PH_WARN, ChasePayload.O_NONE);
        return 0;
    }

    /**
     * spawnIfNoTrail — тестовая команда (/hypersomnia_shard chase): сервер выводит фигуру сам (флаг I_PUSH).
     * Старт всегда ищется за спиной игрока (ChaseTuning.SPAWN_BEHIND_DIST), след больше не нужен.
     * Остальные проверки (стадия, мир, безопасность, лаборатория) остаются прежними.
     */
    private static int start(ServerPlayer player, int force, boolean spawnIfNoTrail) {
        if (RUNS.containsKey(player.getUUID())) return ChasePayload.R_BUSY;
        ServerLevel level = player.serverLevel();
        boolean dream = level.dimension().equals(DreamDimension.LEVEL);
        if (!dream && !level.dimension().equals(Level.OVERWORLD)) return ChasePayload.R_CONTEXT;
        if (FinaleLogic.completed(player)) return ChasePayload.R_CONTEXT;
        int outage = -1; // в лаборатории — только «обесточено» (реальный мир, после MAIN FEED, этажи B1-B4)
        if (LabProtection.inside(level, player.blockPosition())) {
            outage = LabOutage.stopFor(player);
            if (outage < 0) return ChasePayload.R_LAB;
        }
        ObserverProgressData prog = player.getData(ModAttachments.OBSERVER);
        ChaseData data = player.getData(ModAttachments.CHASE);
        int stage = prog.stage();
        if (force == 0) {
            if (stage < ChaseTuning.MIN_STAGE) return ChasePayload.R_STAGE;
            if (prog.getDays() < data.nextDay()) return ChasePayload.R_DAYS;
            if (player.isCreative()) return ChasePayload.R_CONTEXT;
            if (!dream && outage < 0 && !nightOrDark(level, player)) return ChasePayload.R_CONTEXT;
        }
        if (!safe(player, level)) return ChasePayload.R_SAFETY;
        // След больше не нужен: фигура всегда появляется за спиной игрока, независимо от пройденных блоков.
        Vec3 spawn = spotBehind(player, level, ChaseTuning.SPAWN_BEHIND_DIST);
        if (spawn == null) spawn = spotAround(player, level, false);
        if (spawn == null) return ChasePayload.R_TRAIL;

        Run run = new Run();
        run.id = nextId++;
        run.dim = level.dimension();
        run.stage = Mth.clamp(stage, ChaseTuning.MIN_STAGE, 5);
        run.teach = force < 2 && (data.count() == 0 || stage < ChaseTuning.CATCH_MIN_STAGE);
        if (force == 2) run.stage = Math.max(run.stage, ChaseTuning.CATCH_MIN_STAGE);
        run.pushed = spawnIfNoTrail;
        run.warnTicks = Mth.randomBetweenInclusive(player.getRandom(), ChaseTuning.WARN_MIN, ChaseTuning.WARN_MAX);
        run.pos = spawn;
        if (outage >= 0) {
            if (!LabOutage.begin(level, outage, player)) return ChasePayload.R_LAB;
            run.outage = outage;
            run.warnTicks += LabOutage.flickerTicks(); // стоит на месте, пока мигает свет, и ещё WARN_MIN..MAX в темноте
        }
        // Тестовая погоня не тратит день и не двигает расписание: она не меняет прогресс.
        if (!run.pushed) data.markStarted(prog.getDays());
        RUNS.put(player.getUUID(), run);
        send(player, run, ChasePayload.PH_WARN, ChasePayload.O_NONE);
        return 0;
    }

    /**
     * Тестовое наблюдение: фигура встаёт перед игроком и смотрит на него ticks тиков. Не бежит, не ловит, не тратит
     * день погони и не запускает «обесточено». Стадия, ночь и след не нужны — только живой игрок и место под фигуру.
     */
    public static int startWatch(ServerPlayer player, int ticks) {
        if (RUNS.containsKey(player.getUUID())) return ChasePayload.R_BUSY;
        if (!player.isAlive() || player.isSpectator() || FinaleLogic.completed(player)) {
            return ChasePayload.R_CONTEXT;
        }
        Vec3 spot = spotAround(player, player.serverLevel(), true);
        if (spot == null) return ChasePayload.R_SAFETY;
        Run run = new Run();
        run.id = nextId++;
        run.dim = player.level().dimension();
        run.stage = Mth.clamp(player.getData(ModAttachments.OBSERVER).stage(), ChaseTuning.MIN_STAGE, 5);
        run.watch = true;
        run.pushed = true;
        run.watchLeft = Math.max(1, ticks);
        run.phase = ChasePayload.PH_WATCH;
        run.warnTicks = 0;
        run.pos = spot;
        RUNS.put(player.getUUID(), run);
        send(player, run, ChasePayload.PH_WATCH, ChasePayload.O_NONE);
        return 0;
    }

    /**
     * Место для погони по умолчанию: за спиной игрока примерно на dist блоков. След не нужен — фигура
     * появляется независимо от того, сколько игрок прошёл. Сначала строго сзади, потом с небольшим
     * разворотом (всё ещё вне взгляда) и чуть ближе/дальше, если сзади стена или обрыв.
     */
    private static Vec3 spotBehind(ServerPlayer player, ServerLevel level, double dist) {
        Vec3 look = player.getViewVector(1.0f);
        Vec3 base = player.position();
        for (Vec3 offset : ChaseSpawn.behind(look, dist)) {
            Vec3 ground = floorAt(level, base.add(offset));
            if (ground != null) return ground;
        }
        return null;
    }

    /**
     * Место для тестовой фигуры: кольцо вокруг игрока, на твёрдом полу, без воды и коллизий.
     * front — перед игроком (наблюдатель должен быть виден), иначе за спиной (погоня).
     */
    private static Vec3 spotAround(ServerPlayer player, ServerLevel level, boolean front) {
        Vec3 look = player.getViewVector(1.0f);
        Vec3 base = player.position();
        for (Vec3 offset : ChaseSpawn.around(look, front)) {
            Vec3 ground = floorAt(level, base.add(offset));
            if (ground != null) return ground;
        }
        return null;
    }

    /** Пол под точкой в радиусе нескольких блоков по вертикали: два свободных блока и твёрдый блок под ними. */
    private static Vec3 floorAt(ServerLevel level, Vec3 at) {
        BlockPos c = BlockPos.containing(at);
        if (!level.hasChunkAt(c)) return null;
        for (int dy = 2; dy >= -4; dy--) {
            BlockPos p = c.offset(0, dy, 0);
            BlockPos below = p.below();
            if (!level.hasChunkAt(below)) continue;
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                    || !level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) continue;
            if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) continue;
            if (!level.getFluidState(p).isEmpty() || !level.getFluidState(p.above()).isEmpty()) continue;
            return Vec3.atBottomCenterOf(p);
        }
        return null;
    }

    /** Пол в самой точке или рядом с ней (кольца SPOT_SEARCH): для засады у двери/контейнера. */
    private static Vec3 floorNear(ServerLevel level, Vec3 at) {
        Vec3 direct = floorAt(level, at);
        if (direct != null) return direct;
        double r = AmbushTuning.SPOT_SEARCH;
        for (int ring = 1; ring <= 2; ring++) {
            double d = r * ring / 2.0;
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4.0;
                Vec3 q = at.add(Math.cos(a) * d, 0.0, Math.sin(a) * d);
                Vec3 floor = floorAt(level, q);
                if (floor != null) return floor;
            }
        }
        return null;
    }

    /** Можно ли безопасно предложить дверной эпизод; отладочные обходы здесь не действуют. */
    public static boolean canStartDoor(ServerPlayer player) {
        ObserverProgressData progress = player.getData(ModAttachments.OBSERVER);
        return player.serverLevel().dimension().equals(Level.OVERWORLD)
                && !RUNS.containsKey(player.getUUID()) && !FinaleLogic.completed(player)
                && progress.stage() >= 4 && progress.getDays() >= player.getData(ModAttachments.CHASE).nextDay()
                && !player.isCreative() && !LabProtection.inside(player.serverLevel(), player.blockPosition())
                && nightOrDark(player.serverLevel(), player) && safe(player, player.serverLevel());
    }

    /** Погоня из дверного эпизода, только после серверного таймера. Клиенту передаётся флаг связи с эпизодом. */
    public static boolean startFromDoor(ServerPlayer player, BlockPos door) {
        if (!canStartDoor(player)) return false;
        ServerLevel level = player.serverLevel();
        Vec3 spawn = null;
        double best = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos p = door.offset(dx, 0, dz);
            if (!level.hasChunkAt(p) || !level.hasChunkAt(p.above()) || !level.hasChunkAt(p.below())) continue;
            Vec3 candidate = Vec3.atBottomCenterOf(p);
            double distance = candidate.distanceToSqr(player.position());
            if (distance < 4 || distance > 100 || !level.noCollision(body(candidate))) continue;
            if (level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) continue;
            double score = p.distSqr(door);
            if (score < best) { best = score; spawn = candidate; }
        }
        if (spawn == null) return false;
        ObserverProgressData progress = player.getData(ModAttachments.OBSERVER);
        ChaseData data = player.getData(ModAttachments.CHASE);
        Run run = new Run();
        run.id = nextId++;
        run.dim = level.dimension();
        run.stage = Mth.clamp(progress.stage(), ChaseTuning.MIN_STAGE, 5);
        run.teach = data.count() == 0 || run.stage < ChaseTuning.CATCH_MIN_STAGE;
        run.fromDoor = true;
        run.warnTicks = 1;
        run.pos = spawn;
        Trail trail = TRAILS.get(player.getUUID());
        if (trail != null) run.path.addAll(trail.points);
        data.markStarted(progress.getDays());
        RUNS.put(player.getUUID(), run);
        send(player, run, ChasePayload.PH_WARN, ChasePayload.O_NONE);
        return true;
    }

    private static AABB body(Vec3 p) {
        return new AABB(p.x - 0.3, p.y + 0.05, p.z - 0.3, p.x + 0.3, p.y + 1.75, p.z + 0.3);
    }

    private static boolean nightOrDark(ServerLevel level, ServerPlayer player) {
        long tod = level.getDayTime() % 24000L;
        if (tod >= ChaseTuning.NIGHT_FROM && tod <= ChaseTuning.NIGHT_TO) return true;
        return level.getMaxLocalRawBrightness(player.blockPosition()) <= ChaseTuning.DARK_LIGHT;
    }

    /** Не в бою, не в воде и огне, не на лету, нет лавы и обрыва рядом. */
    private static boolean safe(ServerPlayer player, ServerLevel level) {
        if (!player.isAlive() || player.isSpectator() || player.isSleeping() || player.isPassenger()
                || player.isFallFlying() || player.getAbilities().flying) return false;
        if (!player.onGround() || player.isInWater() || player.isInLava() || player.isOnFire()) return false;
        if (player.hurtTime > 0) return false;
        if (player.getLastHurtByMob() != null
                && player.tickCount - player.getLastHurtByMobTimestamp() < ChaseTuning.RECENT_HURT_TICKS) return false;
        if (!level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(ChaseTuning.SAFE_MOB_RADIUS),
                Entity::isAlive).isEmpty()) return false;
        BlockPos c = player.blockPosition();
        int r = ChaseTuning.SAFE_LAVA_RADIUS;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -2, -r), c.offset(r, 1, r))) {
            if (level.getFluidState(p).is(FluidTags.LAVA)) return false;
        }
        int cr = ChaseTuning.CLIFF_RADIUS;
        for (int dx = -cr; dx <= cr; dx++) {
            for (int dz = -cr; dz <= cr; dz++) {
                if (dx == 0 && dz == 0) continue;
                boolean empty = true;
                for (int dy = 1; dy <= ChaseTuning.CLIFF_DEPTH; dy++) {
                    BlockPos p = c.offset(dx, -dy, dz);
                    if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty() || !level.getFluidState(p).isEmpty()) {
                        empty = false;
                        break;
                    }
                }
                if (empty) return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Возврат забранных вещей: при пробуждении (DreamTransition.exit), у кровати.
    // Следы осколка в реальном мире больше не ставятся: место запоминается, и шаги появятся
    // при следующем входе в сон (копия дома). [новое]
    // ------------------------------------------------------------------

    public static void onWake(ServerPlayer player, ServerLevel level, BlockPos bed) {
        ChaseData data = player.getData(ModAttachments.CHASE);
        if (data.taken().isEmpty()) return;
        int days = player.getData(ModAttachments.OBSERVER).getDays();
        boolean all = FinaleLogic.completed(player); // после финала событий нет: всё возвращается сразу
        ObserverProgressData progress = player.getData(ModAttachments.OBSERVER);
        Iterator<ChaseData.Taken> it = data.taken().iterator();
        while (it.hasNext()) {
            ChaseData.Taken t = it.next();
            if (!all && t.returnDay() > days) continue;
            BlockPos floor = findFloor(level, bed, player.getRandom());
            if (floor == null) {
                if (!all) continue;
                floor = player.blockPosition().below();
            }
            // Если другой мод или сервер отклонил сущность, не удаляем оригинал из сохранённых вещей.
            if (!spawn(level, floor, t.stack())) continue;
            it.remove();
            if (!all && level.dimension() == Level.OVERWORLD) {
                progress.noteSeen(floor.getX(), floor.getY(), floor.getZ(),
                        player.level().getGameTime());
            }
        }
    }

    /** Случайный пол рядом с кроватью: прочный верх, над ним пусто, без жидкости, не сама кровать. */
    private static BlockPos findFloor(ServerLevel level, BlockPos bed, RandomSource rnd) {
        List<BlockPos> found = new ArrayList<>();
        int r = ChaseTuning.RETURN_RADIUS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (Math.abs(dx) + Math.abs(dz) < 2) continue;
                for (int dy = 1; dy >= -2; dy--) {
                    BlockPos p = bed.offset(dx, dy, dz);
                    if (!level.isLoaded(p)) continue;
                    if (level.getBlockState(p).is(BlockTags.BEDS)) continue;
                    if (!level.getBlockState(p).isFaceSturdy(level, p, Direction.UP)) continue;
                    BlockPos air = p.above();
                    if (!level.isEmptyBlock(air) || !level.getFluidState(air).isEmpty()) continue;
                    found.add(p);
                    break;
                }
            }
        }
        return found.isEmpty() ? null : found.get(rnd.nextInt(found.size()));
    }

    private static boolean spawn(ServerLevel level, BlockPos floor, ItemStack stack) {
        ItemEntity e = new ItemEntity(level, floor.getX() + 0.5, floor.getY() + 1.05, floor.getZ() + 0.5, stack.copy());
        e.setDeltaMovement(Vec3.ZERO);
        e.setUnlimitedLifetime();
        e.setPickUpDelay(ChaseTuning.RETURN_PICKUP_DELAY);
        return level.addFreshEntity(e);
    }

    // ------------------------------------------------------------------
    // События и отладка
    // ------------------------------------------------------------------

    /** Пока он бежит, лечь нельзя: ванильное «рядом монстры». */
    @SubscribeEvent
    public static void onCanSleep(CanPlayerSleepEvent event) {
        Player p = event.getEntity();
        if (RUNS.containsKey(p.getUUID()) && event.getProblem() == null) {
            event.setProblem(Player.BedSleepingProblem.NOT_SAFE);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sendIdle(p, ChasePayload.PH_IDLE, 0);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Run run = RUNS.remove(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer p) dropped(p, run);
        TRAILS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RUNS.clear();
        TRAILS.clear();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hypersomnia_chase")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("reset").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    p.getData(ModAttachments.CHASE).reset();
                    sendIdle(p, ChasePayload.PH_IDLE, 0);
                    return say(ctx.getSource(), "Chase: counter and day reset (next chase is a teaching one)");
                }))
                .then(Commands.literal("return").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    List<ChaseData.Taken> list = p.getData(ModAttachments.CHASE).taken();
                    int n = list.size();
                    for (ChaseData.Taken t : list) spawn(p.serverLevel(), p.blockPosition().below(), t.stack());
                    list.clear();
                    return say(ctx.getSource(), "Chase: returned " + n + " item(s) at your feet");
                })));
        // [новое] Тестовые команды для мультиплеера: один игрок запускает фигуру другому. Не тратят дни погони
        // и не меняют прогресс; watch не бежит и не ловит, chase — настоящая погоня с обходом требований следа/стадии.
        event.getDispatcher().register(Commands.literal("hypersomnia_shard")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("watch")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> shardWatch(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), 20))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 300))
                                        .executes(ctx -> shardWatch(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                                                IntegerArgumentType.getInteger(ctx, "seconds"))))))
                .then(Commands.literal("chase")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> shardChase(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))
                                .then(Commands.literal("teach").executes(ctx -> shardChaseTeach(ctx.getSource(),
                                        EntityArgument.getPlayers(ctx, "targets"))))))
                .then(Commands.literal("ambush")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> shardAmbush(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))))
                .then(Commands.literal("stop")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> shardStop(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))))
                .then(Commands.literal("status").executes(ctx -> shardStatus(ctx.getSource()))));
    }

    private static int shardWatch(CommandSourceStack src, Collection<ServerPlayer> targets, int seconds) {
        int ok = 0;
        for (ServerPlayer t : targets) {
            int r = startWatch(t, seconds * 20);
            if (r == 0) {
                ok++;
                DebugMode.overlay(t, Component.literal("Shard: watching you (" + seconds + "s)"));
                say(src, "Shard: watching " + t.getName().getString() + " for " + seconds + "s");
            } else {
                say(src, "Shard: " + t.getName().getString() + " - " + ChasePayload.reason(r));
            }
        }
        if (ok > 0) say(src, "Shard: visible only to the target (client-side figure)");
        return ok;
    }

    private static int shardChase(CommandSourceStack src, Collection<ServerPlayer> targets) {
        return shardChase(src, targets, 2);
    }

    private static int shardChaseTeach(CommandSourceStack src, Collection<ServerPlayer> targets) {
        return shardChase(src, targets, 1);
    }

    /** force 2 — настоящая погоня (ловит), 1 — отладочная учебная (исчезает рядом). */
    private static int shardChase(CommandSourceStack src, Collection<ServerPlayer> targets, int force) {
        int ok = 0;
        for (ServerPlayer t : targets) {
            int r = start(t, force, true);
            if (r == 0) {
                ok++;
                DebugMode.overlay(t, Component.literal("Shard: chase started (test)"));
                say(src, "Shard: chasing " + t.getName().getString() + (force == 2 ? " (real)" : " (teaching)"));
            } else {
                say(src, "Shard: " + t.getName().getString() + " - " + ChasePayload.reason(r));
            }
        }
        return ok;
    }

    /** [новое] Тестовая засада: фигура встаёт вплотную перед игроком и сразу переходит в погоню. */
    private static int shardAmbush(CommandSourceStack src, Collection<ServerPlayer> targets) {
        int ok = 0;
        for (ServerPlayer t : targets) {
            Vec3 look = t.getViewVector(1.0f);
            Vec3 at = t.position().add(look.x * AmbushTuning.CONTAINER_BEHIND, 0.0,
                    look.z * AmbushTuning.CONTAINER_BEHIND);
            int r = startAmbush(t, at, true);
            if (r == 0) {
                ok++;
                DebugMode.overlay(t, Component.literal("Shard: ambush (test)"));
                say(src, "Shard: ambush on " + t.getName().getString());
            } else {
                say(src, "Shard: " + t.getName().getString() + " - " + ChasePayload.reason(r));
            }
        }
        return ok;
    }

    private static int shardStop(CommandSourceStack src, Collection<ServerPlayer> targets) {
        int n = 0;
        for (ServerPlayer t : targets) {
            Run run = RUNS.get(t.getUUID());
            if (run == null) {
                say(src, "Shard: " + t.getName().getString() + " - not active");
                continue;
            }
            n++;
            end(t, run, ChasePayload.O_ABORT);
            DebugMode.overlay(t, Component.literal("Shard: stopped (test)"));
            say(src, "Shard: stopped for " + t.getName().getString());
        }
        return n;
    }

    private static int shardStatus(CommandSourceStack src) {
        int n = 0;
        for (ServerPlayer p : src.getServer().getPlayerList().getPlayers()) {
            Run r = RUNS.get(p.getUUID());
            if (r == null) continue;
            n++;
            String what = r.watch
                    ? "watch, " + Math.max(0, r.watchLeft / 20) + "s left"
                    : "chase, phase " + r.phase + (r.teach ? " (teaching)" : "");
            say(src, "Shard: " + p.getName().getString() + " - #" + r.id + ", " + what + ", "
                    + String.format("%.1f", r.pos.distanceTo(p.position())) + " blocks");
        }
        if (n == 0) say(src, "Shard: none");
        return n > 0 ? 1 : 0;
    }

    private static int status(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ChaseData d = p.getData(ModAttachments.CHASE);
        ObserverProgressData prog = p.getData(ModAttachments.OBSERVER);
        Trail t = TRAILS.get(p.getUUID());
        say(src, "Chase: day " + prog.getDays() + ", stage " + prog.stage() + ", chases " + d.count() + ", last day "
                + d.lastDay() + ", next day " + d.nextDay() + ", trail points " + (t == null ? 0 : t.points.size()));
        StringBuilder b = new StringBuilder("taken:");
        for (ChaseData.Taken k : d.taken()) b.append(' ').append(k.stack().getCount()).append('x')
                .append(BuiltInRegistries.ITEM.getKey(k.stack().getItem())).append("@day").append(k.returnDay());
        say(src, d.taken().isEmpty() ? "taken: -" : b.toString());
        Run r = RUNS.get(p.getUUID());
        if (r != null) say(src, "running #" + r.id + ": phase " + r.phase + ", t=" + r.phaseTicks + ", stage " + r.stage
                + (r.teach ? " (teaching)" : "") + ", " + String.format("%.1f", r.pos.distanceTo(p.position())) + " blocks");
        return 1;
    }

    private static int say(CommandSourceStack src, String text) {
        DebugMode.success(src, () -> Component.literal(text), false);
        return 1;
    }
}
