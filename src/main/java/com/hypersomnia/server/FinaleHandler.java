package com.hypersomnia.server;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.ModSounds;
import com.hypersomnia.ObserverProgressData;
import com.hypersomnia.document.DocumentItem;
import com.hypersomnia.document.DrawingTuning;
import com.hypersomnia.document.MemoryTuning;
import com.hypersomnia.dream.DreamAttachments;
import com.hypersomnia.dream.DreamData;
import com.hypersomnia.dream.DreamDimension;
import com.hypersomnia.dream.DreamTransition;
import com.hypersomnia.finale.FinaleLogic;
import com.hypersomnia.finale.FinaleTexts;
import com.hypersomnia.finale.FinaleTuning;
import com.hypersomnia.lab.LabSavedData;
import com.hypersomnia.lab.LabTuning;
import com.hypersomnia.lab.MonitorBlock;
import com.hypersomnia.network.EndingDonePayload;
import com.hypersomnia.network.EndingPayload;
import com.hypersomnia.network.FinaleRoomPayload;
import com.mojang.logging.LogUtils;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Финал (канон, пункт 9), сервер. Детская за воротами N (мир снов, B1): кровать — уснуть, дверь — проснуться, Фрагмент
 * стоит между ними (клиент рисует его по метке). Монитор: кассета tape_edited — «Вечный сон»; запись mem_7_log — правда
 * для Фрагмента (при всех воспоминаниях). Капсула 47 со шприцем sedation_47 после панели сеанса 48 — «Subject 48».
 * Выбор — вторым ПКМ (FinaleTuning.CONFIRM_TICKS). После концовки — экран, затем пробуждение; события мода выключены.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class FinaleHandler {
    private FinaleHandler() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Части для подтверждения: части детской 0..3, кассета и капсула. */
    private static final int CONFIRM_TAPE = 10;
    private static final int CONFIRM_CAPSULE = 11;

    /** Первый ПКМ: игрок -> {часть, игровое время}. Не сохраняется. */
    private static final Map<UUID, long[]> CONFIRM = new HashMap<>();
    /** Что последний раз отправлено клиенту: игрок в детской. */
    private static final Set<UUID> INSIDE = new HashSet<>();
    /** Запись mem_7 на мониторе: игрок -> игровое время начала. */
    private static final Map<UUID, Long> PLAYING = new HashMap<>();
    /** Финал пройден при входе во сне: разбудить на первом тике. */
    private static final Set<UUID> WAKE = new HashSet<>();

    // ------------------------------------------------------------------
    // ПКМ (из LabHandler.onUse)
    // ------------------------------------------------------------------

    /** Часть детской (KIND_FINALE). */
    public static void use(ServerPlayer player, ServerLevel level, LabSavedData d, int part, BlockPos pos) {
        ObserverProgressData data = player.getData(ModAttachments.OBSERVER);
        if (data.ending() >= 0 || !player.getData(DreamAttachments.DREAM).isInDream()) return;
        if (part == FinaleTuning.PART_BED || part == FinaleTuning.PART_DOOR) {
            boolean bed = part == FinaleTuning.PART_BED;
            if (!confirm(player, level, part)) {
                hint(player, bed ? "hypersomnia.finale.confirm.bed" : "hypersomnia.finale.confirm.door");
                return;
            }
            start(player, FinaleLogic.choose(bed, data));
        } else if (part == FinaleTuning.PART_RECORDER) {
            String held = heldDoc(player);
            if (FinaleTuning.TAPE_DOC.equals(held)) {
                if (!confirm(player, level, CONFIRM_TAPE)) {
                    level.playSound(null, pos, ModSounds.TAPE_CLICK.get(), SoundSource.BLOCKS, 0.6f, 1.0f);
                    hint(player, "hypersomnia.finale.confirm.tape");
                    return;
                }
                start(player, FinaleTuning.ETERNAL);
            } else if (FinaleTuning.LOG_DOC.equals(held)) {
                play(player, level, d, data, pos);
            } else {
                hint(player, "hypersomnia.finale.recorder.empty");
            }
        }
    }

    /** Капсула с номером (KIND_CAPSULE). Секрет «Subject 48»: капсула 47, шприц в руке, панель сеанса открыта. */
    public static void capsule(ServerPlayer player, ServerLevel level, LabSavedData d, int n, BlockPos pos) {
        ObserverProgressData data = player.getData(ModAttachments.OBSERVER);
        if (data.ending() >= 0) return;
        if (n != FinaleTuning.CAPSULE_47) {
            hint(player, n == FinaleTuning.CAPSULE_47 + 1 ? "hypersomnia.finale.capsule.reserved" : "hypersomnia.finale.capsule.empty");
            return;
        }
        if (!d.isLockOpen(LabTuning.LOCK_SESSION)) {
            hint(player, "hypersomnia.finale.capsule.active");
            return;
        }
        if (!FinaleTuning.SEDATION_DOC.equals(heldDoc(player)) || !player.getData(DreamAttachments.DREAM).isInDream()) {
            hint(player, "hypersomnia.finale.capsule.standby");
            return;
        }
        if (!confirm(player, level, CONFIRM_CAPSULE)) {
            hint(player, "hypersomnia.finale.confirm.capsule");
            return;
        }
        start(player, FinaleTuning.SUBJECT_48);
    }

    private static String heldDoc(ServerPlayer player) {
        ItemStack s = player.getMainHandItem();
        return s.getItem() instanceof DocumentItem ? DocumentItem.idOf(s) : "";
    }

    private static void hint(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatableWithFallback(key, FinaleTexts.ui(key)), true);
    }

    /** Второй ПКМ по той же части не позже CONFIRM_TICKS — выбор сделан; иначе запоминаем первый. */
    private static boolean confirm(ServerPlayer player, ServerLevel level, int what) {
        long now = level.getGameTime();
        long[] c = CONFIRM.get(player.getUUID());
        if (c != null && c[0] == what && now - c[1] <= FinaleTuning.CONFIRM_TICKS) {
            CONFIRM.remove(player.getUUID());
            return true;
        }
        CONFIRM.put(player.getUUID(), new long[]{what, now});
        return false;
    }

    /** Запись mem_7 на мониторе: клиент показывает субтитры; при всех воспоминаниях Фрагмент слышит правду. */
    private static void play(ServerPlayer player, ServerLevel level, LabSavedData d, ObserverProgressData data, BlockPos pos) {
        long now = level.getGameTime();
        Long started = PLAYING.get(player.getUUID());
        if (started != null && now - started < FinaleTuning.playTicks()) {
            hint(player, "hypersomnia.finale.recorder.busy");
            return;
        }
        PLAYING.put(player.getUUID(), now);
        boolean heard = data.memoriesCount() >= MemoryTuning.COUNT;
        if (heard && !data.truth()) data.setTruth(true);
        level.playSound(null, pos, ModSounds.TAPE_CLICK.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
        monitor(level, d, true);
        BlockPos mark = d.find(LabSavedData.KIND_FINALE * 1000 + FinaleTuning.PART_FRAGMENT);
        PacketDistributor.sendToPlayer(player, new FinaleRoomPayload(true, mark == null ? BlockPos.ZERO : mark,
                heard ? FinaleRoomPayload.PLAY_HEARD : FinaleRoomPayload.PLAY_UNHEARD));
        LOGGER.info("Finale: {} played {} (memories {}, truth {})", player.getGameProfile().getName(),
                FinaleTuning.LOG_DOC, data.memoriesCount(), heard);
    }

    /** Монитор записи: «снег» на экране, пока идёт запись. */
    private static void monitor(ServerLevel level, LabSavedData d, boolean lit) {
        for (Map.Entry<Long, Integer> e : d.inter.entrySet()) {
            if (e.getValue() != LabSavedData.KIND_FINALE * 1000 + FinaleTuning.PART_RECORDER) continue;
            BlockPos p = BlockPos.of(e.getKey());
            BlockState st = level.getBlockState(p);
            if (st.getBlock() instanceof MonitorBlock && st.getValue(MonitorBlock.LIT) != lit)
                level.setBlock(p, st.setValue(MonitorBlock.LIT, lit), 3);
        }
    }

    // ------------------------------------------------------------------
    // Концовка
    // ------------------------------------------------------------------

    /** Концовка выбрана: прогресс, достижения, экран. Сон держится, пока клиент не закроет экран (EndingDonePayload). */
    public static void start(ServerPlayer player, int ending) {
        ObserverProgressData data = player.getData(ModAttachments.OBSERVER);
        boolean blank = data.hasBlank() && FinaleTuning.withTruth(ending);
        data.setEnding(ending);
        CONFIRM.remove(player.getUUID());
        PLAYING.remove(player.getUUID());
        DreamData dream = player.getData(DreamAttachments.DREAM);
        if (dream.isInDream()) dream.setTicksLeft(Math.max(dream.getTicksLeft(), FinaleTuning.ENDING_HOLD_TICKS));
        LOGGER.info("Finale: {} reached ending {}", player.getGameProfile().getName(), FinaleTuning.KEYS[ending]);
        if (player instanceof FakePlayer) return;
        award(player, ending);
        ObserverProgressHandler.send(player, data);
        setInside(player, false, null);
        PacketDistributor.sendToPlayer(player, new EndingPayload(ending, blank ? FinaleTuning.FLAG_BLANK : 0));
    }

    /** Достижения: корень вкладки и концовка (hypersomnia:ending/<key>, критерий done, minecraft:impossible). */
    public static void award(ServerPlayer player, int ending) {
        for (String path : new String[]{"root", "ending/" + FinaleTuning.KEYS[ending]}) {
            AdvancementHolder h = player.server.getAdvancements()
                    .get(ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, path));
            if (h != null) player.getAdvancements().award(h, "done");
            else LOGGER.warn("Finale: advancement {} not found", path);
        }
    }

    /** Экран концовки закрыт: будим (финал пройден, игрок ещё во сне). */
    public static void handleDone(EndingDonePayload pl, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (FinaleLogic.completed(player) && player.getData(DreamAttachments.DREAM).isInDream()) DreamTransition.exit(player);
        });
    }

    // ------------------------------------------------------------------
    // Присутствие в детской
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        if (WAKE.remove(player.getUUID())) {
            if (FinaleLogic.completed(player) && player.getData(DreamAttachments.DREAM).isInDream()) DreamTransition.exit(player);
            return;
        }
        if (player.tickCount % FinaleTuning.ROOM_CHECK_TICKS != 0) return;
        ServerLevel level = player.serverLevel();
        boolean dreamLevel = level.dimension() == DreamDimension.LEVEL;
        LabSavedData d = dreamLevel ? LabSavedData.get(level) : null;
        BlockPos mark = d != null && d.built ? d.find(LabSavedData.KIND_FINALE * 1000 + FinaleTuning.PART_FRAGMENT) : null;
        Long started = PLAYING.get(player.getUUID());
        if (started != null && level.getGameTime() - started >= FinaleTuning.playTicks()) {
            PLAYING.remove(player.getUUID());
            if (d != null) monitor(level, d, false);
        }
        boolean inside = mark != null && !FinaleLogic.completed(player) && player.isAlive() && !player.isSpectator()
                && inRoom(player.blockPosition(), mark);
        boolean entered = inside && !INSIDE.contains(player.getUUID());
        setInside(player, inside, mark);
        if (!inside) return;
        DreamData dream = player.getData(DreamAttachments.DREAM);
        if (dream.isInDream() && dream.getTicksLeft() < FinaleTuning.ROOM_HOLD_TICKS) dream.setTicksLeft(FinaleTuning.ROOM_HOLD_TICKS);
        if (entered) {
            offerTape(player, level, mark);
            offerDrawing(player, level, d);
        }
    }

    /** Блок ног в прямоугольнике детской от метки Фрагмента (FinaleTuning.ROOM_*). Метка — на блок выше пола. */
    public static boolean inRoom(BlockPos feet, BlockPos mark) {
        int dx = feet.getX() - mark.getX(), dz = feet.getZ() - mark.getZ(), dy = feet.getY() - (mark.getY() - 1);
        return dx >= FinaleTuning.ROOM_MIN_DX && dx <= FinaleTuning.ROOM_MAX_DX
                && dz >= FinaleTuning.ROOM_MIN_DZ && dz <= FinaleTuning.ROOM_MAX_DZ
                && dy >= 0 && dy <= FinaleTuning.ROOM_DY;
    }

    private static void setInside(ServerPlayer player, boolean inside, BlockPos mark) {
        boolean was = INSIDE.contains(player.getUUID());
        if (was == inside) return;
        if (inside) INSIDE.add(player.getUUID());
        else INSIDE.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new FinaleRoomPayload(inside, mark == null ? BlockPos.ZERO : mark,
                FinaleRoomPayload.PLAY_NONE));
    }

    /**
     * Третий вариант (канон): при TAPE_MIN_MEMORIES+ воспоминаниях у ног Фрагмента лежит кассета счастливого сна.
     * Один раз за вход в комнату; не кладётся, если она уже у игрока или лежит рядом.
     */
    public static boolean offerTape(ServerPlayer player, ServerLevel level, BlockPos mark) {
        ObserverProgressData data = player.getData(ModAttachments.OBSERVER);
        if (data.memoriesCount() < FinaleTuning.TAPE_MIN_MEMORIES) return false;
        return dropAt(player, level, mark, FinaleTuning.TAPE_DOC);
    }

    /**
     * Рисунок 4 на красном коврике между кроватью и Фрагментом (часть PART_TOY), пока игрок его не нашёл.
     * Как кассета: один раз за вход в комнату, не кладётся, если он уже у игрока или лежит рядом.
     */
    public static boolean offerDrawing(ServerPlayer player, ServerLevel level, LabSavedData d) {
        BlockPos spot = d.find(LabSavedData.KIND_FINALE * 1000 + FinaleTuning.PART_TOY);
        if (spot == null) return false;
        int idx = DrawingTuning.indexOf(FinaleTuning.TOY_DRAWING_DOC);
        if (idx >= 0 && player.getData(ModAttachments.OBSERVER).hasDrawing(idx)) return false;
        return dropAt(player, level, spot, FinaleTuning.TOY_DRAWING_DOC);
    }

    /** Документ doc на полу под меткой (метка на блок выше пола), если его нет у игрока и он не лежит ближе TAPE_LOOK_RADIUS. */
    private static boolean dropAt(ServerPlayer player, ServerLevel level, BlockPos mark, String doc) {
        if (player.getInventory().contains(s -> doc.equals(DocumentItem.idOf(s)))) return false;
        AABB box = new AABB(mark).inflate(FinaleTuning.TAPE_LOOK_RADIUS);
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box))
            if (doc.equals(DocumentItem.idOf(e.getItem()))) return false;
        ItemEntity item = new ItemEntity(level, mark.getX() + 0.5, mark.getY() - 0.9, mark.getZ() + 0.5,
                DocumentItem.make(doc), 0, 0, 0);
        item.setUnlimitedLifetime();
        item.setPickUpDelay(20);
        level.addFreshEntity(item);
        return true;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        INSIDE.remove(player.getUUID());
        // Вышел посреди концовки: финал засчитан, будим на первом тике
        if (FinaleLogic.completed(player) && player.getData(DreamAttachments.DREAM).isInDream()) WAKE.add(player.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        CONFIRM.remove(id);
        INSIDE.remove(id);
        PLAYING.remove(id);
        WAKE.remove(id);
    }

    /** Отладка (/hypersomnia_finale reset): забыть подтверждения и присутствие. */
    public static void forget(ServerPlayer player) {
        CONFIRM.remove(player.getUUID());
        PLAYING.remove(player.getUUID());
        setInside(player, false, null);
    }
}
