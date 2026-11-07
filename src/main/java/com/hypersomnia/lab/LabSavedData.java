package com.hypersomnia.lab;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/** Данные лаборатории в одном мире (по одной на ServerLevel). Точка входа (sited/site*) живёт только в данных Верхнего мира. */
public final class LabSavedData extends SavedData {
    private static final String NAME = "hypersomnia_lab2";
    public static final int KIND_LIFT = 1;
    public static final int KIND_LOCK = 2;
    public static final int KIND_TERMINAL = 3;
    public static final int KIND_BREAKER = 4;
    /** Запечатанный шкаф (строка seal чертежа): открывается вместе с замком id. */
    public static final int KIND_SEAL = 5;
    /** Прут решётки-ворот (строка gate чертежа): решётка стоит, пока замок id закрыт; при открытии прутья убираются. */
    public static final int KIND_GATE = 6;
    /** Детская финала (строка finale чертежа): kind * 1000 + часть FinaleTuning.PART_*. Кровать и дверь — по два блока. */
    public static final int KIND_FINALE = 7;
    /** Капсула с номером (строка capsule чертежа): kind * 1000 + номер. */
    public static final int KIND_CAPSULE = 8;
    /** Серверная стойка (строка rack чертежа): kind * 1000 + номер стойки, обе половины. Лампы ставит LabFixtures.refresh. */
    public static final int KIND_RACK = 9;
    /** Место в ряду хранилища (строка stack): kind * 1000 + ряд * 100 + место * 10 + сторона полок (Direction 2D), оба блока. */
    public static final int KIND_STACK = 10;
    /** Заложенная дверь, за которой стучат (строка knock): kind * 1000 + замок, чей код выстукивают (LabKnocks). */
    public static final int KIND_KNOCK = 11;
    /** Поворотная каретка антенны ANT-1 на крыше реального павильона (AntennaDriveBlock): kind * 1000. ПКМ — LabAntenna.use. */
    public static final int KIND_ANTENNA = 12;

    /** Биты flags: сюжетные события мира. */
    public static final int FLAG_FEED = 0;

    /**
     * Версия формата данных (тег version; в старых сохранениях нет = 0). 2 — лифт ниже B2 открывают замки LIFT ACCESS 6-8:
     * при загрузке старого мира, где MAIN FEED открывал всё до B5, reached срезается до LabLocks.accessTop.
     * 3 — шифр 8 через антенну ANT-1: у реального мира со старой статичной тарелкой ставится roofPending, и LabAntenna
     * перестраивает крышу (LabBuilder.roofUpgrade), когда её чанки загружены.
     * 4 — шахта лифта G↔B1: у старых миров shaftBuilt=false, LabPlacement выроет её при первой загрузке.
     * 5 — светильники вместо стержней энда и герметизация кабин: у старых миров lampsPending/sealPending,
     *     LabPlacement заменяет END_ROD на light_tube и заделывает природные бока кабин (LabShaft.seal).
     */
    public static final int DATA_VERSION = 5;

    // ---- Точка входа (Верхний мир) ----
    public boolean sited;
    public int siteX;
    public int siteZ;
    /** Номер переброса места (команда resite): меняет выбор при том же seed. */
    public int siteTry;

    // ---- Шифры по мирам (Верхний мир) ----
    /** Зерно вариантов шифров этого сохранения (WorldVariant). Задаётся один раз случайно; rebuild и resite его не меняют. */
    public boolean seeded;
    public long cipherSeed;

    // ---- Состояние мира ----
    public boolean built;
    /** Постройка прервалась: не перезапускаем разрушительную стройку автоматически. */
    public boolean buildFailed;
    public boolean dream;
    /** Мировые x, z центра кабины '@' и y земли у павильона. */
    public int originX;
    public int originZ;
    public int groundY;
    /** Бит n = остановка n построена. */
    public int builtMask;
    /** [новое] Шахта лифта между G и B1 вырыта и облицована (LabShaft). */
    public boolean shaftBuilt;
    /** [новое] Старый мир: заменить END_ROD на light_tube, когда чанки лаборатории загружены (LabBuilder.upgradeLamps). */
    public boolean lampsPending;
    /** [новое] Старый мир: заделать природные бока кабин от воды (LabShaft.seal), когда чанки загружены. */
    public boolean sealPending;
    /** Старший открытый этаж (0 = только поверхность). */
    public int reached;
    /** Бит id = замок id открыт. */
    public int locksOpen;
    /** Бит n = событие n произошло (FLAG_*). */
    public int flags;
    /** Порядок замыкания рубильников щита MAIN FEED: десятичные цифры 1..5 по порядку (0 = все разомкнуты). */
    public int breakerSeq;
    /** MAIN FEED выбило: лампа TRIPPED горит до следующего рубильника, включения питания или сброса. */
    public boolean breakerTripped;
    /** Остановка с закрытыми во время рейса дверями или -1. */
    public int closedDoor = -1;
    /**
     * Антенна: позиция (упакованный BlockPos) и азимут тарелки, градусы (0 = север, по часовой стрелке). Реальный мир —
     * каретка ANT-1 (LabAntenna), сон — угол основания статичной тарелки (угол 0).
     */
    public long dishPos;
    public int dishAngle;
    /** Старый мир (версия данных < 3): крышу перестроить под ANT-1, когда её чанки загрузятся (LabAntenna). */
    public boolean roofPending;
    /** Не сохраняются: игровое время последнего поворота тарелки и до какого тика штурвал занят (LabAntenna). */
    public long dishTurnedAt;
    public long dishBusyUntil;
    /** Этаж, где сейчас «обесточено» (LabOutage), или -1. Сохраняется: упал сервер в темноте — при запуске свет вернётся. */
    public int outageStop = -1;
    public int[] stopY = new int[LabTuning.STOPS];
    public int[] stopH = new int[LabTuning.STOPS];
    public int[] bbox = new int[LabTuning.STOPS * 4];
    public long[] door = new long[LabTuning.STOPS];
    /** Интерактивные таблички: позиция -> kind * 1000 + id. */
    public final Map<Long, Integer> inter = new LinkedHashMap<>();

    public static LabSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(LabSavedData::new, LabSavedData::load, null), NAME);
    }

    /** Зерно шифров сохранения (данные Верхнего мира); при первом обращении задаётся случайно. */
    public static long cipherSeed(MinecraftServer server) {
        LabSavedData ow = get(server.overworld());
        if (!ow.seeded) reseed(ow);
        return ow.cipherSeed;
    }

    /** Новое случайное зерно (первый запуск или отладка: /hypersomnia_lab index reroll). Только для данных Верхнего мира. */
    public static void reseed(LabSavedData ow) {
        ow.cipherSeed = java.util.concurrent.ThreadLocalRandom.current().nextLong();
        ow.seeded = true;
        ow.setDirty();
    }

    public boolean isBuilt(int stop) {
        return stop >= 0 && stop < LabTuning.STOPS && ((builtMask >> stop) & 1) != 0;
    }

    public boolean isLockOpen(int id) {
        return ((locksOpen >> id) & 1) != 0;
    }

    public boolean hasFlag(int flag) {
        return ((flags >> flag) & 1) != 0;
    }

    /** Позиция щита MAIN FEED (LabBreakers) или null. */
    public BlockPos panelPos() {
        for (Map.Entry<Long, Integer> e : inter.entrySet())
            if (e.getValue() == KIND_BREAKER * 1000) return BlockPos.of(e.getKey());
        return null;
    }

    /** Первая позиция с этим значением inter (kind * 1000 + id) или null. Детская: метка Фрагмента, монитор. */
    public BlockPos find(int value) {
        for (Map.Entry<Long, Integer> e : inter.entrySet())
            if (e.getValue() == value) return BlockPos.of(e.getKey());
        return null;
    }

    /** Позиция таблички замка id или null. */
    public BlockPos lockPos(int id) {
        for (Map.Entry<Long, Integer> e : inter.entrySet())
            if (e.getValue() == KIND_LOCK * 1000 + id) return BlockPos.of(e.getKey());
        return null;
    }

    public void resetBuild() {
        built = false;
        buildFailed = false;
        builtMask = 0;
        shaftBuilt = false;
        lampsPending = false;
        sealPending = false;
        closedDoor = -1;
        roofPending = false;
        outageStop = -1;
        inter.clear();
        setDirty();
    }

    public void resetProgress() {
        reached = 0;
        locksOpen = 0;
        flags = 0;
        breakerSeq = 0;
        breakerTripped = false;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean("sited", sited);
        tag.putInt("siteX", siteX);
        tag.putInt("siteZ", siteZ);
        tag.putInt("siteTry", siteTry);
        tag.putBoolean("seeded", seeded);
        tag.putLong("cipherSeed", cipherSeed);
        tag.putBoolean("built", built);
        tag.putBoolean("buildFailed", buildFailed);
        tag.putBoolean("dream", dream);
        tag.putInt("originX", originX);
        tag.putInt("originZ", originZ);
        tag.putInt("groundY", groundY);
        tag.putInt("builtMask", builtMask);
        tag.putBoolean("shaftBuilt", shaftBuilt);
        tag.putBoolean("lampsPending", lampsPending);
        tag.putBoolean("sealPending", sealPending);
        tag.putInt("version", DATA_VERSION);
        tag.putInt("reached", reached);
        tag.putInt("locksOpen", locksOpen);
        tag.putInt("flags", flags);
        tag.putInt("breakerSeq", breakerSeq);
        tag.putBoolean("breakerTripped", breakerTripped);
        tag.putInt("closedDoor", closedDoor);
        tag.putLong("dishPos", dishPos);
        tag.putInt("dishAngle", dishAngle);
        tag.putBoolean("roofPending", roofPending);
        tag.putInt("outageStop", outageStop);
        tag.putIntArray("stopY", stopY);
        tag.putIntArray("stopH", stopH);
        tag.putIntArray("bbox", bbox);
        tag.putLongArray("door", door);
        long[] keys = new long[inter.size()];
        int[] vals = new int[inter.size()];
        int i = 0;
        for (Map.Entry<Long, Integer> e : inter.entrySet()) {
            keys[i] = e.getKey();
            vals[i++] = e.getValue();
        }
        tag.putLongArray("interKeys", keys);
        tag.putIntArray("interVals", vals);
        return tag;
    }

    private static LabSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        LabSavedData d = new LabSavedData();
        d.sited = tag.getBoolean("sited");
        d.siteX = tag.getInt("siteX");
        d.siteZ = tag.getInt("siteZ");
        d.siteTry = tag.getInt("siteTry");
        d.seeded = tag.getBoolean("seeded");
        d.cipherSeed = tag.getLong("cipherSeed");
        d.built = tag.getBoolean("built");
        d.buildFailed = tag.getBoolean("buildFailed");
        d.dream = tag.getBoolean("dream");
        d.originX = tag.getInt("originX");
        d.originZ = tag.getInt("originZ");
        d.groundY = tag.getInt("groundY");
        d.builtMask = tag.getInt("builtMask");
        d.shaftBuilt = tag.getBoolean("shaftBuilt");
        d.lampsPending = tag.getBoolean("lampsPending");
        d.sealPending = tag.getBoolean("sealPending");
        d.reached = tag.getInt("reached");
        d.locksOpen = tag.getInt("locksOpen");
        d.flags = tag.getInt("flags");
        d.breakerSeq = tag.getInt("breakerSeq");
        d.breakerTripped = tag.getBoolean("breakerTripped");
        d.closedDoor = tag.contains("closedDoor") ? tag.getInt("closedDoor") : -1;
        d.dishPos = tag.getLong("dishPos");
        d.dishAngle = tag.getInt("dishAngle");
        d.roofPending = tag.getBoolean("roofPending");
        d.outageStop = tag.contains("outageStop") ? tag.getInt("outageStop") : -1;
        int[] a = tag.getIntArray("stopY");
        if (a.length == d.stopY.length) d.stopY = a;
        a = tag.getIntArray("stopH");
        if (a.length == d.stopH.length) d.stopH = a;
        a = tag.getIntArray("bbox");
        if (a.length == d.bbox.length) d.bbox = a;
        long[] l = tag.getLongArray("door");
        if (l.length == d.door.length) d.door = l;
        long[] keys = tag.getLongArray("interKeys");
        int[] vals = tag.getIntArray("interVals");
        for (int i = 0; i < keys.length && i < vals.length; i++) d.inter.put(keys[i], vals[i]);
        int version = tag.getInt("version");
        if (version < 2 && d.hasFlag(FLAG_FEED)) d.reached = Math.min(d.reached, LabLocks.accessTop(d));
        if (version < 3 && d.built && !d.dream && d.isBuilt(0)) d.roofPending = true;
        // [новое] Старый мир до светильников и герметизации: обе миграции — при первой загрузке рядом с лабораторией.
        if (version < 5 && d.built) {
            d.lampsPending = true;
            d.sealPending = true;
        }
        return d;
    }
}
