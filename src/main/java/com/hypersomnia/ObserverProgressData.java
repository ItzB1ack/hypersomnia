package com.hypersomnia;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.ArrayList;
import java.util.List;

/**
 * Прогресс для стадий осколка: прожитые дни и найденные воспоминания (битовая маска из 7).
 * Плюс ограниченная память привычек: точки маршрута и места сна (только Верхний мир). Клиент получает копию
 * в ObserverStagePayload и выбирает по ней знакомые места; на стадию память не влияет.
 */
public class ObserverProgressData implements INBTSerializable<CompoundTag> {
    private int days;
    private int ticks;
    private int memories;
    private int exposurePoints;
    private int exposureTicks;
    /** Служебное: значения изменились, клиенту нужно отправить (не сохраняется). */
    private boolean dirty;
    /** Маска выданных записок слоя 1. */
    private int notes;
    /** Маска найденных рисунков Nora: биты 0-4 рисунки, бит 5 пустой лист (бонус). */
    private int drawings;
    /** Записка, выбранная при входе в сон и ждущая пробуждения (сохраняется). */
    private int pendingNote = -1;
    private int pendingWay = -1;
    /** В этот сон записка уже выдана при входе (сохраняется). Тогда след после него не ставится. */
    private boolean noteThisSleep;

    /** Маршрут: ROUTE_SLOTS четвёрок x, y, z, вес (вес 0 = пусто). Места сна: SLEEP_SLOTS четвёрок x, y, z, счёт. */
    private final int[] route = new int[ObserverTuning.ROUTE_SLOTS * 4];
    private final int[] sleep = new int[ObserverTuning.SLEEP_SLOTS * 4];
    /** Места, где игрок прогнал фигуру взглядом: x, y, z, день + 1 (0 = пусто), новое первым. */
    private final int[] seen = new int[ObserverTuning.SEEN_SLOTS * 4];
    /** Когда сервер последний раз принял место (игровое время, не сохраняется). */
    private long seenAcceptedAt = Long.MIN_VALUE;
    private int sampleTicks;
    /** Отладка (/hypersomnia_progress stage): стадия задана вручную, -1 = по формуле. Сохраняется (переживает смерть и перезаход). */
    private int stageOverride = -1;
    /**
     * Шифр 6 (панель RECORDER 47-OP): набранные, но не принятые цифры (черновик, пусто = нет) и «запись подготовлена».
     * Сохраняются: переживают закрытие экрана, смерть и перезаход.
     */
    private String indexDraft = "";
    private boolean recordReady;
    /**
     * Финал (finale/FinaleTuning): последняя выбранная концовка (-1 = финал не пройден), маска всех полученных концовок,
     * «Фрагмент услышал правду» (запись mem_7 проиграна в детской при всех воспоминаниях). Сохраняются.
     */
    private int ending = -1;
    private int endings;
    private boolean truth;
    private boolean session47Seen;
    private boolean noraRoomSeen;
    private boolean labPathSeen;
    /** Подарок воспоминания (ObserverNotesHandler.giveGift): пробуждений подряд без новой находки, маска на прошлом пробуждении, подаренные. */
    private int giftNights;
    private int giftSeen;
    private int gifted;

    public int getDays() { return days; }
    public void setDays(int v) { days = Math.max(0, v); dirty = true; }
    public int getMemories() { return memories; }
    public boolean noraRoomSeen() { return noraRoomSeen; }
    public void markNoraRoomSeen() { noraRoomSeen = true; dirty = true; }
    public boolean labPathSeen() { return labPathSeen; }
    public void markLabPathSeen() { labPathSeen = true; dirty = true; }
    public void resetStage5Scenes() { noraRoomSeen = false; labPathSeen = false; dirty = true; }
    public boolean session47Seen() { return session47Seen; }
    public void markSession47Seen() { session47Seen = true; dirty = true; }
    public int memoriesCount() { return Integer.bitCount(memories); }
    /** Заглушка до пункта 2: index 0..6 = номер воспоминания. */
    public void addMemory(int index) { memories |= 1 << index; dirty = true; }
    public void setMemoriesCount(int n) { memories = (1 << Math.max(0, Math.min(7, n))) - 1; dirty = true; }
    public void markDirty() { dirty = true; }
    /** Стадия осколка: заданная отладкой или по формуле ObserverTuning.stage (дни + воспоминания). */
    public int stage() { return stageOverride >= 0 ? stageOverride : ObserverTuning.stage(days, memoriesCount(), exposurePoints); }
    public int exposurePoints() { return exposurePoints; }
    public void registerSyringeExposure() {
        exposurePoints = Math.min(ObserverTuning.EXPOSURE_MAX_POINTS, exposurePoints + ObserverTuning.EXPOSURE_SYRINGE_POINTS);
        dirty = true;
    }
    public void addExposureTick() {
        if (exposurePoints >= ObserverTuning.EXPOSURE_MAX_POINTS) return;
        if (++exposureTicks >= ObserverTuning.EXPOSURE_AWAKE_TICKS_PER_POINT) {
            exposureTicks = 0;
            exposurePoints++;
            dirty = true;
        }
    }
    public void restExposure() {
        exposureTicks = 0;
        exposurePoints = Math.max(0, exposurePoints - ObserverTuning.EXPOSURE_REST_REDUCTION);
        dirty = true;
    }
    public int stageOverride() { return stageOverride; }
    /** -1 = снова по формуле. */
    public void setStageOverride(int s) { stageOverride = s < 0 ? -1 : Math.min(5, s); dirty = true; }
    public boolean consumeDirty() { boolean d = dirty; dirty = false; return d; }

    public String indexDraft() { return indexDraft; }
    /** Только цифры, не длиннее индекса; иное — пусто. */
    public void setIndexDraft(String s) { indexDraft = cleanDraft(s); }
    public boolean recordReady() { return recordReady; }
    public void setRecordReady(boolean v) { recordReady = v; }

    public int ending() { return ending; }
    public int endingsMask() { return endings; }
    public boolean hasEnding(int e) { return e >= 0 && (endings & (1 << e)) != 0; }
    /** Концовка выбрана: запоминается последней и добавляется в маску. -1 = снять (отладка), маска остаётся. */
    public void setEnding(int e) {
        ending = e < 0 ? -1 : Math.min(com.hypersomnia.finale.FinaleTuning.ENDINGS - 1, e);
        if (ending >= 0) endings |= 1 << ending;
        dirty = true;
    }
    public boolean truth() { return truth; }
    public void setTruth(boolean v) { truth = v; dirty = true; }
    /** Отладка (/hypersomnia_finale reset): финал снова не пройден, правда не услышана; маска концовок остаётся. */
    public void resetFinale() { ending = -1; truth = false; dirty = true; }

    public int giftNights() { return giftNights; }
    public int giftedMask() { return gifted; }
    /** Утро после сна: нашёл новое воспоминание с прошлого пробуждения — счёт заново, иначе ещё одна пустая ночь. */
    public void countGiftNight() {
        if (memories != giftSeen) {
            giftSeen = memories;
            giftNights = 0;
        } else {
            giftNights++;
        }
    }
    /** Самое младшее воспоминание, которого нет и которое ещё не дарили; -1 — дарить нечего. */
    public int nextGift() {
        for (int i = 0; i < com.hypersomnia.document.MemoryTuning.COUNT; i++)
            if ((memories & (1 << i)) == 0 && (gifted & (1 << i)) == 0) return i;
        return -1;
    }
    public void markGifted(int i) { gifted |= 1 << i; giftNights = 0; }
    public void resetGift() { giftNights = 0; giftSeen = memories; gifted = 0; }

    private static String cleanDraft(String s) {
        if (s == null || s.length() > com.hypersomnia.lab.LabTuning.INDEX_DIGITS.length) return "";
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) < '0' || s.charAt(i) > '9') return "";
        return s;
    }

    public int notesCount() { return Integer.bitCount(notes); }
    public boolean hasNote(int i) { return (notes & (1 << i)) != 0; }
    public void addNote(int i) { notes |= 1 << i; }
    public void resetNotes() { notes = 0; clearPending(); noteThisSleep = false; }

    public void addDrawing(int i) { drawings |= 1 << i; }
    public boolean hasDrawing(int i) { return (drawings & (1 << i)) != 0; }
    /** Найдено рисунков из условия секрета (без пустого листа). */
    public int drawingsCount() { return Integer.bitCount(drawings & ((1 << com.hypersomnia.document.DrawingTuning.COUNT) - 1)); }
    public boolean hasBlank() { return hasDrawing(com.hypersomnia.document.DrawingTuning.BLANK_INDEX); }
    /** Первая ещё не выданная страница, доступная на этой стадии, или -1. Страницы идут строго по порядку. */
    public int nextFreeNote(int stage) {
        for (int i = 0; i < ObserverTuning.NOTE_PAGES.length; i++) {
            if (hasNote(i)) continue;
            return stage >= ObserverTuning.NOTE_PAGE_MIN_STAGE[i] ? i : -1;
        }
        return -1;
    }
    public int getPendingNote() { return pendingNote; }
    public int getPendingWay() { return pendingWay; }
    public void setPending(int note, int way) { pendingNote = note; pendingWay = way; }
    public void clearPending() { pendingNote = -1; pendingWay = -1; }
    public boolean noteThisSleep() { return noteThisSleep; }
    public void setNoteThisSleep(boolean v) { noteThisSleep = v; }

    // ------------------------------------------------------------------
    // Память привычек
    // ------------------------------------------------------------------

    /** Вызывать каждый тик, пока игрок ходит по Верхнему миру; раз в ROUTE_SAMPLE_TICKS запоминает точку. */
    public void sampleRoute(int x, int y, int z) {
        if (++sampleTicks < ObserverTuning.ROUTE_SAMPLE_TICKS) return;
        sampleTicks = 0;
        noteRoute(x, y, z);
    }

    /** Точка ближе ROUTE_MERGE_DIST к известной усиливает её (вес +1), иначе занимает пустую или самую слабую ячейку. */
    public void noteRoute(int x, int y, int z) {
        note(route, x, y, z, ObserverTuning.ROUTE_MERGE_DIST, ObserverTuning.ROUTE_WEIGHT_MAX);
    }

    /** Место, где игрок лёг спать (кровать в Верхнем мире). */
    public void noteSleep(int x, int y, int z) {
        note(sleep, x, y, z, ObserverTuning.SLEEP_MERGE_DIST, ObserverTuning.SLEEP_COUNT_MAX);
    }

    private void note(int[] a, int x, int y, int z, int merge, int max) {
        int empty = -1;
        int weakest = -1;
        for (int i = 0; i < a.length; i += 4) {
            int w = a[i + 3];
            if (w <= 0) {
                if (empty < 0) empty = i;
                continue;
            }
            long dx = (long) a[i] - x;
            long dy = (long) a[i + 1] - y;
            long dz = (long) a[i + 2] - z;
            // Далёкие координаты не должны сливаться из-за переполнения int.
            if (Math.abs(dx) <= merge && Math.abs(dz) <= merge && Math.abs(dy) <= merge
                    && dx * dx + dz * dz <= (long) merge * merge) {
                a[i + 3] = w >= max ? max : w + 1;
                dirty = true;
                return;
            }
            if (weakest < 0 || w < a[weakest + 3]) weakest = i;
        }
        int slot = empty >= 0 ? empty : weakest;
        a[slot] = x;
        a[slot + 1] = y;
        a[slot + 2] = z;
        a[slot + 3] = 1;
        dirty = true;
    }

    /** Раз в день веса маршрута x3/4: старые привычки забываются, ячейки с весом 0 освобождаются. */
    public void decayRoute() {
        for (int i = 0; i < route.length; i += 4) route[i + 3] = route[i + 3] * 3 / 4;
        dirty = true;
    }

    // ---- Места, где игрок видел фигуру (TraceHandler.seen / place) ----

    /** Новое место первым; самое старое вытесняется. */
    public void noteSeen(int x, int y, int z, long gameTime) {
        System.arraycopy(seen, 0, seen, 4, seen.length - 4);
        seen[0] = x;
        seen[1] = y;
        seen[2] = z;
        seen[3] = days + 1;
        seenAcceptedAt = gameTime;
        dirty = true;
    }

    public long seenAcceptedAt() { return seenAcceptedAt; }

    /** Самое новое место не старше SEEN_MAX_AGE_DAYS дней {x, y, z} или null. Устаревшие удаляются. */
    public int[] peekSeen() {
        for (int i = 0; i < seen.length; i += 4) {
            if (seen[i + 3] <= 0) continue;
            if (days - (seen[i + 3] - 1) > ObserverTuning.SEEN_MAX_AGE_DAYS) {
                seen[i + 3] = 0;
                dirty = true;
                continue;
            }
            return new int[]{seen[i], seen[i + 1], seen[i + 2]};
        }
        return null;
    }

    /** Убрать место (след уже поставлен). */
    public void removeSeen(int x, int y, int z) {
        for (int i = 0; i < seen.length; i += 4) {
            if (seen[i + 3] > 0 && seen[i] == x && seen[i + 1] == y && seen[i + 2] == z) {
                seen[i + 3] = 0;
                dirty = true;
            }
        }
    }

    /** Непустые места четвёрками x, y, z, день. */
    public List<Integer> seenList() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < seen.length; i += 4) {
            if (seen[i + 3] <= 0) continue;
            out.add(seen[i]);
            out.add(seen[i + 1]);
            out.add(seen[i + 2]);
            out.add(seen[i + 3] - 1);
        }
        return out;
    }

    public void clearHabits() {
        java.util.Arrays.fill(route, 0);
        java.util.Arrays.fill(sleep, 0);
        java.util.Arrays.fill(seen, 0);
        sampleTicks = 0;
        dirty = true;
    }

    /** Непустые точки маршрута четвёрками x, y, z, вес. */
    public List<Integer> routeList() { return list(route); }
    public List<Integer> sleepList() { return list(sleep); }
    public int routeCount() { return list(route).size() / 4; }
    public int sleepCount() { return list(sleep).size() / 4; }

    private static List<Integer> list(int[] a) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < a.length; i += 4) {
            if (a[i + 3] <= 0) continue;
            for (int k = 0; k < 4; k++) out.add(a[i + k]);
        }
        return out;
    }

    /** Самое частое место сна {x, y, z} или null. */
    public int[] home() {
        int best = -1;
        for (int i = 0; i < sleep.length; i += 4) {
            if (sleep[i + 3] > 0 && (best < 0 || sleep[i + 3] > sleep[best + 3])) best = i;
        }
        return best < 0 ? null : new int[]{sleep[best], sleep[best + 1], sleep[best + 2]};
    }

    /** Один тик в Верхнем мире. true, если прошёл целый день. */
    public boolean addTick() {
        if (++ticks >= ObserverTuning.TICKS_PER_DAY) {
            ticks = 0;
            days++;
            decayRoute();
            dirty = true;
            return true;
        }
        return false;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("nora_room_seen", noraRoomSeen);
        tag.putBoolean("lab_path_seen", labPathSeen);
        tag.putInt("days", days);
        tag.putInt("ticks", ticks);
        tag.putInt("memories", memories);
        tag.putInt("exposure_points", exposurePoints);
        tag.putInt("exposure_ticks", exposureTicks);
        tag.putInt("notes", notes);
        tag.putInt("drawings", drawings);
        if (pendingNote >= 0) {
            tag.putInt("pending_note", pendingNote);
            tag.putInt("pending_way", pendingWay);
        }
        if (noteThisSleep) tag.putBoolean("note_this_sleep", true);
        tag.putIntArray("route", route);
        tag.putIntArray("sleep", sleep);
        tag.putIntArray("seen", seen);
        if (stageOverride >= 0) tag.putInt("stage_override", stageOverride);
        if (!indexDraft.isEmpty()) tag.putString("index_draft", indexDraft);
        if (recordReady) tag.putBoolean("record_ready", true);
        tag.putInt("ending", ending);
        if (endings != 0) tag.putInt("endings", endings);
        if (truth) tag.putBoolean("truth", true);
        if (session47Seen) tag.putBoolean("session47_seen", true);
        if (giftNights != 0) tag.putInt("gift_nights", giftNights);
        if (giftSeen != 0) tag.putInt("gift_seen", giftSeen);
        if (gifted != 0) tag.putInt("gifted", gifted);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        exposurePoints = Math.max(0, Math.min(ObserverTuning.EXPOSURE_MAX_POINTS, tag.getInt("exposure_points")));
        exposureTicks = Math.max(0, Math.min(ObserverTuning.EXPOSURE_AWAKE_TICKS_PER_POINT - 1, tag.getInt("exposure_ticks")));
        noraRoomSeen = tag.getBoolean("nora_room_seen");
        labPathSeen = tag.getBoolean("lab_path_seen");
        days = Math.max(0, tag.getInt("days"));
        ticks = Math.max(0, tag.getInt("ticks"));
        memories = tag.getInt("memories") & 0x7F;
        session47Seen = tag.getBoolean("session47_seen");
        notes = tag.getInt("notes");
        drawings = tag.getInt("drawings");
        int pn = tag.contains("pending_note") ? tag.getInt("pending_note") : -1;
        int pw = tag.contains("pending_way") ? tag.getInt("pending_way") : -1;
        // Отложенные варианты — только BED, CONTAINER, WINDOW. Старый или повреждённый NBT не индексируем.
        if (pn >= 0 && pn < ObserverTuning.NOTE_PAGES.length && pw >= 0 && pw < ObserverTuning.NOTE_DEFERRED_WAYS) setPending(pn, pw);
        else clearPending();
        noteThisSleep = tag.getBoolean("note_this_sleep");
        copy(tag.getIntArray("route"), route);
        copy(tag.getIntArray("sleep"), sleep);
        copy(tag.getIntArray("seen"), seen);
        stageOverride = tag.contains("stage_override") ? Math.max(-1, Math.min(5, tag.getInt("stage_override"))) : -1;
        indexDraft = cleanDraft(tag.getString("index_draft"));
        recordReady = tag.getBoolean("record_ready");
        int e = tag.contains("ending") ? tag.getInt("ending") : -1;
        ending = e < 0 || e >= com.hypersomnia.finale.FinaleTuning.ENDINGS ? -1 : e;
        endings = tag.getInt("endings") & ((1 << com.hypersomnia.finale.FinaleTuning.ENDINGS) - 1);
        truth = tag.getBoolean("truth");
        giftNights = Math.max(0, tag.getInt("gift_nights"));
        giftSeen = tag.getInt("gift_seen") & 0x7F;
        gifted = tag.getInt("gifted") & 0x7F;
    }

    /** Старое сохранение без памяти или другой размер массива: берём, сколько помещается, остальное пусто. */
    private static void copy(int[] from, int[] to) {
        java.util.Arrays.fill(to, 0);
        int n = Math.min(from.length, to.length);
        n -= n % 4;
        System.arraycopy(from, 0, to, 0, n);
        for (int i = 3; i < to.length; i += 4) if (to[i] < 0) to[i] = 0;
    }
}