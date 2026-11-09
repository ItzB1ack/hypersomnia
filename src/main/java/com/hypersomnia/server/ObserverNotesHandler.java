package com.hypersomnia.server;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModAttachments;
import com.hypersomnia.ObserverProgressData;
import com.hypersomnia.ObserverTuning;
import com.hypersomnia.document.DocumentItem;
import com.hypersomnia.document.DrawingTuning;
import com.hypersomnia.document.MemoryTuning;
import com.hypersomnia.dream.DreamAttachments;
import com.hypersomnia.dream.DreamData;
import com.hypersomnia.dream.DreamTransition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Что Фрагмент оставляет игроку. Записки: бросок при входе в сон (стадия, шанс, способ, страница); кровать, контейнер и окно
 * доставляются при пробуждении, инвентарь и мир снов сразу при входе. Не вышло доставить: запасные способы кровать, затем инвентарь.
 * Страница считается выданной только после доставки.
 * Утром, если записки не было: рисунок у места из подсказки уже выданной записки слоя 1 (leaveHint), иначе подарок
 * воспоминания после нескольких пустых ночей (giveGift). За одно утро не больше одной находки; след осколка
 * ставится отдельно, при входе в сон.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class ObserverNotesHandler {
    private ObserverNotesHandler() {}

    /** Порядок совпадает с массивами NOTE_WAY_* в ObserverTuning. */
    public enum Way { BED, CONTAINER, WINDOW, INVENTORY, DREAM }

    /** Метка рамки-«наклейки» на окне: записку забирают и ЛКМ, и ПКМ, рамка не выпадает. */
    public static final String WINDOW_TAG = "hypersomnia_note";

    // ------------------------------------------------------------------
    // Бросок и выдача
    // ------------------------------------------------------------------

    /** Вызывать из DreamTransition.enter() после телепорта игрока в мир снов. */
    public static void onEnterDream(ServerPlayer player, ServerLevel dream) {
        ObserverProgressData data = player.getData(ModAttachments.OBSERVER);
        data.setNoteThisSleep(false);
        // Не перезаписываем записку, которую не удалось безопасно выдать после предыдущего сна.
        if (data.getPendingNote() >= 0) return;
        int stage = data.stage();
        if (stage < ObserverTuning.NOTE_MIN_STAGE) return;
        if (player.getRandom().nextFloat() >= ObserverTuning.NOTE_CHANCE) return;
        int note = data.nextFreeNote(stage);
        if (note < 0) return;
        Way way = pickWay(player.getRandom(), stage);
        if (way == null) return;

        if (way == Way.INVENTORY || way == Way.DREAM) {
            ItemStack stack = DocumentItem.make(ObserverTuning.NOTE_PAGES[note]);
            if (deliver(player, dream, player.blockPosition(), way, stack)) {
                data.addNote(note);
                data.setNoteThisSleep(true);
                return;
            }
            way = Way.BED;
        }
        data.setPending(note, way.ordinal());
    }

    /**
     * Вызывать из DreamTransition.exit() после возврата игрока в Верхний мир.
     * true — в этот сон была записка (сейчас или при входе в сон).
     */
    public static boolean onWake(ServerPlayer player, ServerLevel level) {
        ObserverProgressData data = player.getData(ModAttachments.OBSERVER);
        data.countGiftNight();
        boolean atEntry = data.noteThisSleep();
        data.setNoteThisSleep(false);
        DreamData dream = player.getData(DreamAttachments.DREAM);
        BlockPos origin = BlockPos.containing(dream.getReturnX(), dream.getReturnY(), dream.getReturnZ());

        // Если пока не удалось выдать предмет, оставляем заявку и не создаём другую находку.
        if (data.getPendingNote() >= 0 && !data.hasNote(data.getPendingNote())) {
            deliverPending(player, level, origin, data);
            return true;
        }
        data.clearPending();
        if (atEntry) return true;
        if (leaveHint(player, level, origin, data) >= 0) return true;
        return giveGift(player, level, origin, data) >= 0;
    }

    /** Заявка сбрасывается только после успешной выдачи или если записка уже отмечена. */
    private static boolean deliverPending(ServerPlayer player, ServerLevel level, BlockPos origin, ObserverProgressData data) {
        int note = data.getPendingNote(), way = data.getPendingWay();
        if (note < 0 || note >= ObserverTuning.NOTE_PAGES.length || way < 0 || way >= ObserverTuning.NOTE_DEFERRED_WAYS || data.hasNote(note)) {
            data.clearPending();
            return false;
        }
        for (Way w : new Way[]{Way.values()[way], Way.BED, Way.INVENTORY}) {
            if (deliver(player, level, origin, w, DocumentItem.make(ObserverTuning.NOTE_PAGES[note]))) {
                data.addNote(note);
                data.clearPending();
                return true;
            }
        }
        return false;
    }

    /** Временную заявку нельзя переносить на новое тело после смерти. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) return;
        ObserverProgressData data = player.getData(ModAttachments.OBSERVER);
        data.clearPending();
        data.setNoteThisSleep(false);
    }

    /** После неудачной выдачи повторяем попытку; в другом измерении используем текущее место игрока. */
    @SubscribeEvent
    public static void retryPending(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive() || player.tickCount % ObserverTuning.NOTE_RETRY_TICKS != 0) return;
        DreamData dream = player.getData(DreamAttachments.DREAM);
        if (dream.isInDream() || player.level().dimension().equals(com.hypersomnia.dream.DreamDimension.LEVEL)) return;
        ObserverProgressData data = player.getData(ModAttachments.OBSERVER);
        if (data.getPendingNote() < 0) return;
        ServerLevel level = player.serverLevel();
        BlockPos origin = level.dimension().location().toString().equals(dream.getReturnDim())
                ? BlockPos.containing(dream.getReturnX(), dream.getReturnY(), dream.getReturnZ()) : player.blockPosition();
        deliverPending(player, level, origin, data);
    }

    // ------------------------------------------------------------------
    // Рисунки по подсказкам записок слоя 1 и подарок воспоминания
    // ------------------------------------------------------------------

    /** Где лежит рисунок записки k (NOTE_PAGES 0..2): тексты записок — акростих WINDOW, глифы BEHIND THE DOOR, UNDER THE BED. */
    public enum Hint { WINDOW, DOOR, UNDER_BED }

    private static final Hint[] HINTS = {Hint.WINDOW, Hint.DOOR, Hint.UNDER_BED};

    /**
     * Рисунок ObserverTuning.HINT_DRAWINGS[k] у места из подсказки записки k: первая выданная записка слоя 1, чей рисунок
     * не найден, не у игрока и не лежит рядом (HINT_LOOK_RADIUS). Нет такого места — на кровать. Возвращает k или -1.
     */
    public static int leaveHint(ServerPlayer player, ServerLevel level, BlockPos origin, ObserverProgressData data) {
        int n = Math.min(HINTS.length, ObserverTuning.HINT_DRAWINGS.length);
        for (int k = 0; k < n; k++) {
            if (!data.hasNote(k)) continue;
            int drawing = ObserverTuning.HINT_DRAWINGS[k];
            if (data.hasDrawing(drawing)) continue;
            String doc = DrawingTuning.idOf(drawing);
            if (carries(player, doc) || lyingNear(level, origin, doc)) continue;
            if (placeHint(level, origin, HINTS[k], DocumentItem.make(doc))) return k;
        }
        return -1;
    }

    /** Рисунок у места подсказки (без проверок прогресса; отладка /hypersomnia_progress hint). Нет места — на кровать. */
    public static boolean placeHint(ServerLevel level, BlockPos origin, Hint hint, ItemStack stack) {
        boolean ok = switch (hint) {
            case WINDOW -> stickToWindow(level, origin, stack);
            case DOOR -> placeBehindDoor(level, origin, stack);
            case UNDER_BED -> placeUnderBed(level, origin, stack);
        };
        return ok || placeOnBed(level, origin, stack);
    }

    /**
     * Подарок (канон, пункт 8): ObserverTuning.GIFT_NIGHTS пробуждений подряд без нового воспоминания и стадия от GIFT_MIN_STAGE —
     * самое младшее ненайденное и ещё не подаренное воспоминание. Не вышло положить — завтра снова. Возвращает индекс или -1.
     */
    public static int giveGift(ServerPlayer player, ServerLevel level, BlockPos origin, ObserverProgressData data) {
        if (data.giftNights() < ObserverTuning.GIFT_NIGHTS || data.stage() < ObserverTuning.GIFT_MIN_STAGE) return -1;
        return giftNow(player, level, origin, data);
    }

    /** Подарок без условий по ночам и стадии (и отладка /hypersomnia_progress gift now): сундук или бочка рядом, или кровать. */
    public static int giftNow(ServerPlayer player, ServerLevel level, BlockPos origin, ObserverProgressData data) {
        int mem = data.nextGift();
        if (mem < 0) return -1;
        boolean chestFirst = player.getRandom().nextFloat() < ObserverTuning.GIFT_CONTAINER_CHANCE;
        Way[] chain = chestFirst ? new Way[]{Way.CONTAINER, Way.BED} : new Way[]{Way.BED, Way.CONTAINER};
        for (Way w : chain) {
            if (deliver(player, level, origin, w, DocumentItem.make(MemoryTuning.IDS[mem]))) {
                data.markGifted(mem);
                return mem;
            }
        }
        return -1;
    }

    private static boolean carries(ServerPlayer player, String doc) {
        return player.getInventory().contains(s -> doc.equals(DocumentItem.idOf(s)));
    }

    /** Такой документ уже лежит рядом: предмет на земле или в рамке (записка на окне). */
    private static boolean lyingNear(ServerLevel level, BlockPos origin, String doc) {
        AABB box = new AABB(origin).inflate(ObserverTuning.HINT_LOOK_RADIUS);
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box))
            if (doc.equals(DocumentItem.idOf(e.getItem()))) return true;
        for (ItemFrame f : level.getEntitiesOfClass(ItemFrame.class, box))
            if (doc.equals(DocumentItem.idOf(f.getItem()))) return true;
        return false;
    }

    /**
     * [новое] Случайная ещё не найденная записка за поимку погоней из часов (DreamClock.onCaught):
     * сначала в инвентарь, иначе на кровать, иначе на землю рядом. Стадия учитывается та же,
     * что и при обычной выдаче (NOTE_PAGE_MIN_STAGE). Возвращает true, если записка выдана.
     */
    public static boolean giveRandomNote(ServerPlayer player, ServerLevel level, BlockPos origin) {
        ObserverProgressData data = player.getData(ModAttachments.OBSERVER);
        int stage = data.stage();
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < ObserverTuning.NOTE_PAGES.length; i++) {
            if (data.hasNote(i) || stage < ObserverTuning.NOTE_PAGE_MIN_STAGE[i]) continue;
            free.add(i);
        }
        if (free.isEmpty()) return false;
        int note = free.get(player.getRandom().nextInt(free.size()));
        for (Way w : new Way[]{Way.INVENTORY, Way.BED, Way.DREAM}) {
            if (deliver(player, level, origin, w, DocumentItem.make(ObserverTuning.NOTE_PAGES[note]))) {
                data.addNote(note);
                return true;
            }
        }
        return false;
    }

    /** Отладка (/hypersomnia_progress notes give <0-4>): первая страница выбранным способом вокруг игрока, без учёта прогресса. */
    public static boolean giveNow(ServerPlayer player, int wayIndex) {
        Way[] ways = Way.values();
        if (wayIndex < 0 || wayIndex >= ways.length) return false;
        ItemStack stack = DocumentItem.make(ObserverTuning.NOTE_PAGES[0]);
        return deliver(player, player.serverLevel(), player.blockPosition(), ways[wayIndex], stack);
    }

    private static Way pickWay(RandomSource r, int stage) {
        Way[] ways = Way.values();
        int total = 0;
        for (Way w : ways) {
            if (stage >= ObserverTuning.NOTE_WAY_MIN_STAGE[w.ordinal()]) total += ObserverTuning.NOTE_WAY_WEIGHT[w.ordinal()];
        }
        if (total <= 0) return null;
        int roll = r.nextInt(total);
        for (Way w : ways) {
            if (stage < ObserverTuning.NOTE_WAY_MIN_STAGE[w.ordinal()]) continue;
            roll -= ObserverTuning.NOTE_WAY_WEIGHT[w.ordinal()];
            if (roll < 0) return w;
        }
        return null;
    }

    private static boolean deliver(ServerPlayer player, ServerLevel level, BlockPos origin, Way way, ItemStack stack) {
        return switch (way) {
            case BED -> placeOnBed(level, origin, stack);
            case CONTAINER -> putInContainer(level, origin, stack);
            case WINDOW -> stickToWindow(level, origin, stack);
            case INVENTORY -> putInInventory(player, stack);
            case DREAM -> dropNearby(player, level, stack);
        };
    }

    // ------------------------------------------------------------------
    // Способы
    // ------------------------------------------------------------------

    /** На кровати, где игрок уснул. Подбор не сразу, чтобы он успел заметить лист. */
    private static boolean placeOnBed(ServerLevel level, BlockPos origin, ItemStack stack) {
        BlockPos best = nearestBed(level, origin);
        if (best == null) return false;
        return spawnItem(level, best.getX() + 0.5, best.getY() + 0.6, best.getZ() + 0.5, stack, ObserverTuning.NOTE_BED_PICKUP_DELAY);
    }

    /** Ближайший к точке сна блок кровати (любая половина) в NOTE_BED_RADIUS, по высоте +-1; null — нет. */
    private static BlockPos nearestBed(ServerLevel level, BlockPos origin) {
        int r = ObserverTuning.NOTE_BED_RADIUS;
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-r, -1, -r), origin.offset(r, 1, r))) {
            if (!level.hasChunkAt(p) || !level.getBlockState(p).is(BlockTags.BEDS)) continue;
            double d = p.distSqr(origin);
            if (d < bestD) {
                bestD = d;
                best = p.immutable();
            }
        }
        return best;
    }

    /**
     * «Под кроватью»: на полу вплотную к длинной стороне ближайшей кровати (под раму лист не пролезет: коллизия кровати),
     * сначала у изголовья. Свободной клетки нет — false.
     */
    private static boolean placeUnderBed(ServerLevel level, BlockPos origin, ItemStack stack) {
        BlockPos bed = nearestBed(level, origin);
        if (bed == null) return false;
        BlockState st = level.getBlockState(bed);
        if (!(st.getBlock() instanceof BedBlock)) return false;
        Direction facing = st.getValue(BedBlock.FACING); // от изножья к изголовью
        BlockPos head = st.getValue(BedBlock.PART) == BedPart.HEAD ? bed : bed.relative(facing);
        BlockPos foot = head.relative(facing.getOpposite());
        Direction side = facing.getClockWise();
        double tuck = ObserverTuning.HINT_BED_TUCK;
        for (BlockPos part : new BlockPos[]{head, foot}) {
            for (Direction s : new Direction[]{side, side.getOpposite()}) {
                BlockPos c = part.relative(s);
                if (!floorSpot(level, c)) continue;
                if (spawnItem(level, c.getX() + 0.5 - s.getStepX() * tuck, c.getY() + 0.05, c.getZ() + 0.5 - s.getStepZ() * tuck,
                        stack, ObserverTuning.HINT_PICKUP_DELAY)) return true;
            }
        }
        return false;
    }

    /**
     * «За дверью»: на полу у нижней половины ближайшей двери (HINT_DOOR_RADIUS), с той стороны, что дальше от места сна;
     * там занято — с ближней. Двери нет или места нет — false.
     */
    private static boolean placeBehindDoor(ServerLevel level, BlockPos origin, ItemStack stack) {
        int r = ObserverTuning.HINT_DOOR_RADIUS;
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-r, -2, -r), origin.offset(r, 2, r))) {
            if (!level.hasChunkAt(p)) continue;
            BlockState st = level.getBlockState(p);
            if (!(st.getBlock() instanceof DoorBlock) || st.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
            double d = p.distSqr(origin);
            if (d < bestD) {
                bestD = d;
                best = p.immutable();
            }
        }
        if (best == null) return false;
        Direction f = level.getBlockState(best).getValue(DoorBlock.FACING);
        BlockPos a = best.relative(f), b = best.relative(f.getOpposite());
        BlockPos far = a.distSqr(origin) >= b.distSqr(origin) ? a : b;
        for (BlockPos c : new BlockPos[]{far, far == a ? b : a}) {
            if (!floorSpot(level, c)) continue;
            if (spawnItem(level, c.getX() + 0.5, c.getY() + 0.05, c.getZ() + 0.5, stack, ObserverTuning.HINT_PICKUP_DELAY)) return true;
        }
        return false;
    }

    /** Лист ляжет здесь на пол: клетка без коллизии и жидкости, под ней твёрдая верхняя грань. */
    private static boolean floorSpot(ServerLevel level, BlockPos c) {
        if (!level.hasChunkAt(c)) return false;
        BlockState st = level.getBlockState(c);
        BlockPos below = c.below();
        return st.getCollisionShape(level, c).isEmpty() && st.getFluidState().isEmpty()
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    /** В свободный слот ближайшего сундука или бочки. */
    private static boolean putInContainer(ServerLevel level, BlockPos origin, ItemStack stack) {
        int r = ObserverTuning.NOTE_CONTAINER_RADIUS;
        Container best = null;
        int bestSlot = -1;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
            if (!level.hasChunkAt(p)) continue;
            BlockEntity be = level.getBlockEntity(p);
            if (!(be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity)) continue;
            Container c = (Container) be;
            int slot = -1;
            for (int i = 0; i < c.getContainerSize(); i++) {
                if (c.getItem(i).isEmpty()) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0) continue;
            double d = p.distSqr(origin);
            if (d < bestD) {
                bestD = d;
                best = c;
                bestSlot = slot;
            }
        }
        if (best == null) return false;
        best.setItem(bestSlot, stack);
        best.setChanged();
        return true;
    }

    /** Приклеена к стеклу изнутри: невидимая рамка с запиской. Внутри = клетка под крышей, снаружи = видно небо. */
    private static boolean stickToWindow(ServerLevel level, BlockPos origin, ItemStack stack) {
        int r = ObserverTuning.NOTE_WINDOW_RADIUS;
        BlockPos bestInner = null;
        Direction bestDir = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos g : BlockPos.betweenClosed(origin.offset(-r, -2, -r), origin.offset(r, 3, r))) {
            if (!level.hasChunkAt(g) || !level.getBlockState(g).is(BlockTags.IMPERMEABLE)) continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos inner = g.relative(d).immutable();
                BlockPos outer = g.relative(d.getOpposite()).immutable();
                if (!level.hasChunkAt(inner) || !level.hasChunkAt(outer)
                        || !level.getBlockState(inner).isAir() || !level.getBlockState(outer).isAir()) continue;
                if (level.canSeeSky(inner) || !level.canSeeSky(outer)) continue;
                double dist = inner.distSqr(origin);
                if (dist < bestD) {
                    bestD = dist;
                    bestInner = inner;
                    bestDir = d;
                }
            }
        }
        if (bestInner == null) return false;
        if (!level.getEntitiesOfClass(HangingEntity.class, new AABB(bestInner)).isEmpty()) return false;

        ItemFrame frame = new ItemFrame(level, bestInner, bestDir);
        if (!frame.survives()) return false;
        frame.setItem(stack);
        frame.setInvisible(true);
        frame.addTag(WINDOW_TAG);
        return level.addFreshEntity(frame);
    }

    /** В свободный слот основного инвентаря (не хотбар): находит позже сам. */
    private static boolean putInInventory(ServerPlayer player, ItemStack stack) {
        Inventory inv = player.getInventory();
        for (int i = 9; i < 36; i++) {
            if (inv.getItem(i).isEmpty()) {
                inv.setItem(i, stack);
                return true;
            }
        }
        return false;
    }

    /** На земле рядом с игроком (в мире снов). */
    private static boolean dropNearby(ServerPlayer player, ServerLevel level, ItemStack stack) {
        RandomSource rnd = player.getRandom();
        double a = rnd.nextDouble() * Math.PI * 2.0;
        double dist = Mth.lerp(rnd.nextFloat(), ObserverTuning.NOTE_DREAM_DIST_MIN, ObserverTuning.NOTE_DREAM_DIST_MAX);
        BlockPos p = DreamTransition.findSafe(level, BlockPos.containing(
                player.getX() + Math.cos(a) * dist, player.getY(), player.getZ() + Math.sin(a) * dist));
        if (p == null) return false;
        return spawnItem(level, p.getX() + 0.5, p.getY() + 0.1, p.getZ() + 0.5, stack, ObserverTuning.NOTE_DREAM_PICKUP_DELAY);
    }

    private static boolean spawnItem(ServerLevel level, double x, double y, double z, ItemStack stack, int pickupDelay) {
        ItemEntity e = new ItemEntity(level, x, y, z, stack);
        e.setDeltaMovement(Vec3.ZERO);
        e.setUnlimitedLifetime();
        e.setPickUpDelay(pickupDelay);
        return level.addFreshEntity(e);
    }

    // ------------------------------------------------------------------
    // Записка на окне: забирается ПКМ или ЛКМ, рамка исчезает без выпадения
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ItemFrame frame) || !frame.getTags().contains(WINDOW_TAG)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!event.getLevel().isClientSide) take(event.getEntity(), frame);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof ItemFrame frame) || !frame.getTags().contains(WINDOW_TAG)) return;
        event.setCanceled(true);
        if (!event.getEntity().level().isClientSide) take(event.getEntity(), frame);
    }

    private static void take(Player player, ItemFrame frame) {
        if (frame.isRemoved()) return;
        ItemStack stack = frame.getItem().copy();
        frame.discard();
        if (!stack.isEmpty()) player.getInventory().placeItemBackInInventory(stack);
    }
}