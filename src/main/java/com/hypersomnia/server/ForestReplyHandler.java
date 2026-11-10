package com.hypersomnia.server;

import com.hypersomnia.DebugMode;
import com.hypersomnia.ForestReplyTuning;
import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.finale.FinaleLogic;
import com.hypersomnia.forest.ForestReplyContext;
import com.hypersomnia.forest.ForestReplySession;
import com.hypersomnia.network.ForestReplyAckPayload;
import com.hypersomnia.network.ForestReplyPayload;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Контекстная серверная часть. Не обращается к ObserverDirector и не меняет мир. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class ForestReplyHandler {
    private ForestReplyHandler() {}

    private record Broken(BlockEvent.BreakEvent event, BlockPos pos, long at) {}
    private record Request(long ticket, long at, boolean forced) {}

    private static final class State {
        final ServerLevel level;
        final ArrayDeque<Broken> breaks = new ArrayDeque<>();
        ForestReplySession session;
        BlockPos anchor;
        Request request;

        State(ServerLevel level) { this.level = level; }
    }

    // Доступ только на серверном потоке. Никаких ссылок на клиент и сохраняемых transient-сессий.
    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)
                || event.getLevel() != player.serverLevel() || !eligible(player, false)
                || !event.getState().is(BlockTags.LOGS)) return;
        ServerLevel level = player.serverLevel();
        long now = clock(player);
        State state = ACTIVE.get(player.getUUID());
        if (state != null && state.level != level) {
            ACTIVE.remove(player.getUUID());
            state = null;
        }
        if (state != null && state.request != null) return;
        if ((state == null || state.session == null)
                && !player.getData(ModAttachments.FOREST_REPLY).schedule().canAttempt(now)) return;

        BlockPos pos = event.getPos().immutable();
        boolean sameTreeSession = state != null && state.session != null
                && state.anchor.distSqr(pos) <= 16 * 16;
        boolean tree = sameTreeSession || ForestReplyContext.hasTree(level, pos);
        if (!tree) return;
        if (state == null) {
            state = new State(level);
            ACTIVE.put(player.getUUID(), state);
        }
        if (state.breaks.size() >= ForestReplyTuning.MAX_BREAKS_BUFFERED) state.breaks.removeFirst();
        // BreakEvent ещё можно отменить другим слушателем; сам по себе он НЕ доказывает разрушение.
        state.breaks.addLast(new Broken(event, pos, now));
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        State state = ACTIVE.get(player.getUUID());
        if (state == null) return;
        if (!eligible(player, state.request != null && state.request.forced())
                || state.level != player.serverLevel()) {
            ACTIVE.remove(player.getUUID());
            return;
        }
        long now = clock(player);
        while (!state.breaks.isEmpty() && state.breaks.peekFirst().at() < now) {
            Broken broken = state.breaks.removeFirst();
            if (broken.event().isCanceled() || !ForestReplyContext.loaded(state.level, broken.pos())
                    || state.level.getBlockState(broken.pos()).is(broken.event().getState().getBlock())) continue;
            if (state.session == null || state.anchor.distSqr(broken.pos()) > 16 * 16) {
                int pause = ForestReplyTuning.PAUSE_MIN + player.getRandom().nextInt(
                        ForestReplyTuning.PAUSE_MAX - ForestReplyTuning.PAUSE_MIN + 1);
                state.session = new ForestReplySession(pause);
                state.anchor = broken.pos();
            }
            state.session.recordBreak(broken.at());
        }

        if (state.request != null) {
            if (now < state.request.at() || now - state.request.at() > ForestReplyTuning.REQUEST_TTL) {
                if (state.request.forced()) debug(player, "клиент не подтвердил запуск за 10 секунд.");
                ACTIVE.remove(player.getUUID());
            }
            return;
        }
        if (state.session == null) {
            if (state.breaks.isEmpty()) ACTIVE.remove(player.getUUID());
            return;
        }
        // Рубка могла возобновиться именно в тик окончания паузы. Сначала подтверждаем
        // новое разрушение на следующем тике, не запускаем ответ по старому lastBreak.
        if (!state.breaks.isEmpty()) return;
        if (!state.session.ready(now)) return;
        int gap = state.session.gap();
        state.session = null;
        state.anchor = null;
        if (!player.getData(ModAttachments.FOREST_REPLY).schedule().consumeAttempt(now)
                || !ForestReplyTuning.chancePasses(player.getRandom().nextDouble())) {
            ACTIVE.remove(player.getUUID());
            return;
        }
        offer(player, state, now, gap, false);
    }

    private static void offer(ServerPlayer player, State state, long now, int gap, boolean forced) {
        long ticket = UUID.randomUUID().getMostSignificantBits();
        state.request = new Request(ticket, now, forced);
        PacketDistributor.sendToPlayer(player, new ForestReplyPayload(ticket, gap, forced));
    }

    public static void handleAck(ForestReplyAckPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            State state = ACTIVE.get(player.getUUID());
            if (state == null || state.request == null || state.request.ticket() != payload.ticket()) return;
            Request request = state.request;
            long now = clock(player);
            // Только собственный неистёкший билет, один раз, в том же мире и измерении.
            ACTIVE.remove(player.getUUID());
            if (state.level != player.serverLevel() || !eligible(player, request.forced())
                    || now < request.at() || now - request.at() > ForestReplyTuning.REQUEST_TTL) return;
            if (payload.result() == ForestReplyAckPayload.STARTED) {
                if (!request.forced()) {
                    int days = ForestReplyTuning.COOLDOWN_DAYS_MIN + player.getRandom().nextInt(
                            ForestReplyTuning.COOLDOWN_DAYS_MAX - ForestReplyTuning.COOLDOWN_DAYS_MIN + 1);
                    player.getData(ModAttachments.FOREST_REPLY).schedule().recordStarted(now, days);
                } else debug(player, "первый звук запущен. Шанс и сохранённые лимиты не менялись.");
            } else if (request.forced()) {
                debug(player, switch (payload.result()) {
                    case ForestReplyAckPayload.BLOCKED -> "запуск пропущен: экран, сцена, другое событие или неподходящий режим.";
                    case ForestReplyAckPayload.NO_SOURCE -> "не найден скрытый источник в лесу на расстоянии 8–12 блоков.";
                    case ForestReplyAckPayload.NO_AUDIO -> "звук выключен или звуковой движок не подтвердил запуск.";
                    default -> "запуск прерван до первого звука.";
                });
            }
        });
    }

    public static boolean test(ServerPlayer player) {
        if (!eligible(player, true)) return false;
        State previous = ACTIVE.get(player.getUUID());
        if (previous != null && previous.request != null) {
            debug(player, "предыдущий запрос ещё ожидает ответа; повтори через несколько секунд.");
            return false;
        }
        State state = new State(player.serverLevel());
        ACTIVE.put(player.getUUID(), state);
        offer(player, state, clock(player), ForestReplyTuning.GAP_DEFAULT, true);
        return true;
    }

    public static String status(ServerPlayer player) {
        var schedule = player.getData(ModAttachments.FOREST_REPLY).schedule();
        long now = clock(player);
        State state = ACTIVE.get(player.getUUID());
        return "Лес отвечает: gameTime=" + now
                + ", до суточной попытки=" + Math.max(0L, schedule.nextAttempt() - now)
                + " т., до конца кулдауна=" + Math.max(0L, schedule.nextEvent() - now)
                + " т., сессия=" + (state != null && state.session != null)
                + ", запрос=" + (state != null && state.request != null);
    }

    public static boolean resetLimits(ServerPlayer player) {
        State state = ACTIVE.get(player.getUUID());
        if (state != null && state.request != null) return false;
        ACTIVE.remove(player.getUUID());
        player.getData(ModAttachments.FOREST_REPLY).schedule().reset();
        return true;
    }

    private static boolean eligible(ServerPlayer player, boolean forced) {
        return player.serverLevel().dimension() == Level.OVERWORLD && player.isAlive()
                && !player.isSleeping() && !player.isSpectator() && (forced || !player.isCreative())
                && !(player instanceof FakePlayer) && !FinaleLogic.completed(player);
    }

    private static long clock(ServerPlayer player) {
        return player.serverLevel().getServer().overworld().getGameTime();
    }

    private static void debug(ServerPlayer player, String text) {
        DebugMode.chat(player, Component.literal("Лес отвечает: " + text));
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { ACTIVE.remove(event.getEntity().getUUID()); }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { ACTIVE.remove(event.getEntity().getUUID()); }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) { ACTIVE.remove(event.getEntity().getUUID()); }

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ACTIVE.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { ACTIVE.clear(); }
}