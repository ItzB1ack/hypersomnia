package com.hypersomnia.chase;

import com.hypersomnia.FatigueData;
import com.hypersomnia.FatigueTuning;
import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.ObserverProgressData;
import com.hypersomnia.dream.DreamDimension;
import com.hypersomnia.finale.FinaleLogic;
import com.hypersomnia.lab.LabOutage;
import com.hypersomnia.lab.LabProtection;
import com.hypersomnia.network.ChasePayload;
import com.hypersomnia.network.ChaseRequestPayload;
import com.hypersomnia.server.TraceHandler;
import com.hypersomnia.trace.TraceAttachments;
import com.hypersomnia.trace.TraceData;
import com.hypersomnia.trace.TraceType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
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
    }

    private static final Map<UUID, Trail> TRAILS = new HashMap<>();
    private static final Map<UUID, Run> RUNS = new HashMap<>();
    private static int nextId = 1;

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
        if (added != null) run.path.add(added);
        step(player, run);
    }

    private static void step(ServerPlayer player, Run run) {
        if (!player.isAlive() || player.isSleeping() || !player.level().dimension().equals(run.dim)
                || FinaleLogic.completed(player)) {
            end(player, run, ChasePayload.O_ABORT);
            return;
        }
        run.phaseTicks++;
        Vec3 feet = player.position();
        if (run.pos.distanceTo(feet) > ChaseTuning.ABORT_DIST) {
            end(player, run, ChasePayload.O_ABORT);
            return;
        }
        if (run.phase == ChasePayload.PH_CAUGHT) {
            if (run.phaseTicks >= ChaseTuning.CAUGHT_TICKS) end(player, run, ChasePayload.O_CAUGHT);
            else send(player, run, ChasePayload.PH_CAUGHT, ChasePayload.O_NONE);
            return;
        }
        // Взгляд в погоне не прогоняет. Пока он стоит на следе: заметил его (WARN_SEEN_TICKS взгляда) — сразу бежит
        if (run.phase == ChasePayload.PH_WARN && lookedAt(player, run.pos)) run.stare++;
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
            left = followRoute(run, nav, left);
            if (run.route != null || run.phase != ChasePayload.PH_RUN) return;
        }
        if (run.onTrail) followTrail(player, run, left);
    }

    /** Новый путь к игроку; нет — к точке следа (с конца следа, TRAIL_JOIN_TRIES попыток); нет и его — по следу. */
    private static void repath(Run run, ChasePath nav, Vec3 target) {
        run.sinceRepath = 0;
        run.routeGoal = target;
        run.ri = 0;
        ChasePath.Route r = nav.find(run.pos, target, ChaseTuning.PATH_MAX_NODES);
        if (r == null) {
            for (int t = 0, i = run.path.size() - 1; t < ChaseTuning.TRAIL_JOIN_TRIES && i >= run.next;
                 t++, i -= ChaseTuning.TRAIL_JOIN_STEP) {
                r = nav.find(run.pos, run.path.get(i), ChaseTuning.TRAIL_JOIN_NODES);
                if (r != null) {
                    r.trailIndex = i;
                    break;
                }
            }
        }
        run.route = r;
        run.routeFailed = r == null;
        run.onTrail = r == null;
    }

    /** Идёт по пути A*; возвращает непройденный остаток шага. */
    private static double followRoute(Run run, ChasePath nav, double left) {
        ChasePath.Route r = run.route;
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
        FatigueData f = player.getData(ModAttachments.FATIGUE);
        f.addFatigue(Math.round(FatigueTuning.MAX_FATIGUE * ChaseTuning.FATIGUE_ON_CATCH));
        f.addMadness(ChaseTuning.MADNESS_ON_CATCH);
        if (run.stage >= ChaseTuning.DAMAGE_MIN_STAGE) {
            player.hurt(damage(player.serverLevel()), ChaseTuning.DAMAGE);
        } else {
            take(player);
        }
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
        if (run != null && run.outage >= 0) LabOutage.chaseEnded(player.serverLevel());
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
            int force = pl.force() > 0 && player.hasPermissions(2) ? pl.force() : 0;
            int r = start(player, force);
            if (r != 0) sendIdle(player, ChasePayload.PH_REJECT, r);
        });
    }

    /** 0 — погоня началась, иначе причина отказа ChasePayload.R_*. force: 1 — отладка, 2 — отладка и не учебная. */
    public static int start(ServerPlayer player, int force) {
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
        Trail t = TRAILS.get(player.getUUID());
        if (t == null || t.points.size() < 2) return ChasePayload.R_TRAIL;
        List<Vec3> pts = new ArrayList<>(t.points);
        int startIdx = pickStart(player, level, pts);
        if (startIdx == -1) return ChasePayload.R_TRAIL;
        if (startIdx < 0) return ChasePayload.R_VIEW;

        Run run = new Run();
        run.id = nextId++;
        run.dim = level.dimension();
        run.stage = Mth.clamp(stage, ChaseTuning.MIN_STAGE, 5);
        run.teach = force < 2 && (data.count() == 0 || stage < ChaseTuning.CATCH_MIN_STAGE);
        if (force == 2) run.stage = Math.max(run.stage, ChaseTuning.CATCH_MIN_STAGE);
        run.warnTicks = Mth.randomBetweenInclusive(player.getRandom(), ChaseTuning.WARN_MIN, ChaseTuning.WARN_MAX);
        run.pos = pts.get(startIdx);
        run.path.addAll(pts.subList(startIdx + 1, pts.size()));
        if (outage >= 0) {
            if (!LabOutage.begin(level, outage, player)) return ChasePayload.R_LAB;
            run.outage = outage;
            run.warnTicks += LabOutage.flickerTicks(); // стоит на следе, пока мигает свет, и ещё WARN_MIN..MAX в темноте
        }
        data.markStarted(prog.getDays());
        RUNS.put(player.getUUID(), run);
        send(player, run, ChasePayload.PH_WARN, ChasePayload.O_NONE);
        return 0;
    }

    /** Точка старта на следе: START_PATH_MIN..MAX блоков пути позади, вне взгляда. -1 — следа мало, -2 — всё на виду. */
    private static int pickStart(ServerPlayer player, ServerLevel level, List<Vec3> pts) {
        Vec3 feet = player.position();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        double cosView = Math.cos(Math.toRadians(ChaseTuning.START_VIEW_HALF_DEG));
        double acc = 0;
        Vec3 prev = feet;
        int best = -1;
        boolean any = false;
        for (int i = pts.size() - 1; i >= 0; i--) {
            Vec3 p = pts.get(i);
            acc += prev.distanceTo(p);
            prev = p;
            if (acc > ChaseTuning.START_PATH_MAX) break;
            if (acc < ChaseTuning.START_PATH_MIN || p.distanceTo(feet) < ChaseTuning.START_DIST_MIN) continue;
            if (!level.noCollision(body(p))) continue;
            any = true;
            if (visible(player, eye, look, cosView, p)) continue;
            best = i;
        }
        return best >= 0 ? best : (any ? -2 : -1);
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
    // Возврат забранных вещей: при пробуждении (DreamTransition.exit), у кровати, рядом его шаги
    // ------------------------------------------------------------------

    public static void onWake(ServerPlayer player, ServerLevel level, BlockPos bed) {
        ChaseData data = player.getData(ModAttachments.CHASE);
        if (data.taken().isEmpty()) return;
        int days = player.getData(ModAttachments.OBSERVER).getDays();
        boolean all = FinaleLogic.completed(player); // после финала событий нет: всё возвращается сразу
        TraceData traces = player.getData(TraceAttachments.TRACES);
        boolean traced = false;
        Iterator<ChaseData.Taken> it = data.taken().iterator();
        while (it.hasNext()) {
            ChaseData.Taken t = it.next();
            if (!all && t.returnDay() > days) continue;
            BlockPos floor = findFloor(level, bed, player.getRandom());
            if (floor == null) {
                if (!all) continue;
                floor = player.blockPosition().below();
            }
            spawn(level, floor, t.stack());
            it.remove();
            if (!all && level.dimension() == Level.OVERWORLD && !traces.has(floor, Direction.UP.ordinal())) {
                traces.add(new TraceData.Entry(TraceType.STEPS.ordinal(), floor, Direction.UP.ordinal(), 0,
                        TraceHandler.rotToward(floor, bed)));
                traced = true;
            }
        }
        if (traced) TraceHandler.send(player, traces);
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

    private static void spawn(ServerLevel level, BlockPos floor, ItemStack stack) {
        ItemEntity e = new ItemEntity(level, floor.getX() + 0.5, floor.getY() + 1.05, floor.getZ() + 0.5, stack.copy());
        e.setDeltaMovement(Vec3.ZERO);
        e.setUnlimitedLifetime();
        e.setPickUpDelay(ChaseTuning.RETURN_PICKUP_DELAY);
        level.addFreshEntity(e);
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
        src.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
