package com.hypersomnia.lab;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.dream.DreamDimension;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;


/** Одна точка входа на оба мира; лаборатория строится, когда игрок подходит ближе BUILD_RADIUS. */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class LabPlacement {
    private LabPlacement() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % LabTuning.CHECK_EVERY != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerLevel level = p.serverLevel();
            boolean dream = level.dimension() == DreamDimension.LEVEL;
            if (!dream && level.dimension() != Level.OVERWORLD) continue;
            LabSavedData d = LabSavedData.get(level);
            if (d.built) {
                // [новое] Старый мир: шахты G↔B1 ещё нет — вырыть её, когда игрок рядом и чанки загружены.
                if (!d.shaftBuilt) {
                    BlockPos site = site(server);
                    double dx = p.getX() - site.getX(), dz = p.getZ() - site.getZ();
                    if (dx * dx + dz * dz <= (double) LabTuning.BUILD_RADIUS * LabTuning.BUILD_RADIUS) LabShaft.ensure(level, d);
                }
                continue;
            }
            if (d.buildFailed) continue;
            BlockPos site = site(server);
            double dx = p.getX() - site.getX(), dz = p.getZ() - site.getZ();
            if (dx * dx + dz * dz > (double) LabTuning.BUILD_RADIUS * LabTuning.BUILD_RADIUS) continue;
            build(level, site, dream);
            return;
        }
    }

    /** Выбор места заранее, пока идёт загрузка мира: поиск по рельефу занимает заметное время. */
    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        site(event.getServer());
    }

    /** Координаты входа (x, z), общие для обоих миров. Выбираются один раз (LabSiteFinder), хранятся в Верхнем мире. */
    public static BlockPos site(MinecraftServer server) {
        ServerLevel ow = server.overworld();
        LabSavedData d = LabSavedData.get(ow);
        if (!d.sited) {
            BlockPos p = LabSiteFinder.find(ow, d.siteTry);
            d.sited = true;
            d.siteX = p.getX();
            d.siteZ = p.getZ();
            d.setDirty();
        }
        return new BlockPos(d.siteX, 0, d.siteZ);
    }

    /** Высота земли в точке (первый воздух над землёй, без листвы). */
    public static int ground(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    /** Мир, где сейчас идёт стройка (она синхронная, внутри build); null — стройки нет. */
    private static ServerLevel building;

    /**
     * Во время стройки блоки, потерявшие опору (грибы и трава на срезанном рельефе двора, факелы, вторая половина кровати),
     * исчезают без выпадения: иначе вокруг новой лаборатории лежат грибы и семена. Содержимое контейнеров идёт мимо
     * этого события: вещи из сундука игрока по-прежнему падают на пол (лут природных тайников убирает LabBuilder).
     */
    @SubscribeEvent
    public static void onDrops(BlockDropsEvent event) {
        if (building != null && event.getLevel() == building) event.setCanceled(true);
    }

    /** Строит все этажи, для которых есть чертёж. Ошибка чертежа не роняет сервер, но пишется в лог. */
    public static void build(ServerLevel level, BlockPos site, boolean dream) {
        building = level;
        try {
            buildFloors(level, site, dream);
        } finally {
            building = null;
        }
    }

    private static void buildFloors(ServerLevel level, BlockPos site, boolean dream) {
        LabSavedData d = LabSavedData.get(level);
        // Старую шахту закладываем до resetBuild: нужны прежние origin/stopY/stopH.
        LabShaft.clear(level, d);
        d.resetBuild();
        // Уровень двора не пересчитываем при пересборке на том же месте: иначе площадка «ползёт» вверх.
        boolean sameSite = d.groundY != 0 && d.originX == site.getX() && d.originZ == site.getZ();
        d.dream = dream;
        d.originX = site.getX();
        d.originZ = site.getZ();
        try {
            for (int n = 0; n < LabTuning.STOPS; n++) {
                String name = LabTuning.blueprintName(dream, n);
                if (!LabBlueprint.exists(name)) continue;
                LabBlueprint bp = LabBlueprint.get(name);
                int fy;
                if (n == 0) {
                    fy = sameSite ? d.groundY : LabBuilder.padLevel(level, bp, site.getX(), site.getZ());
                    d.groundY = fy;
                } else {
                    fy = LabTuning.floorY(n);
                }
                LabBuilder.build(level, bp, site.getX(), site.getZ(), fy, n, n == 0, d);
            }
            LabLift.refreshDoors(level, d);
            LabLocks.refreshKeypads(level, d);
            LabBreakers.refresh(level, d);
            LabFixtures.refresh(level, d);
            // [новое] Шахта между G и B1: кабина едет в готовом проёме, а не сквозь породу.
            LabShaft.ensure(level, d);
            d.built = true;
            d.setDirty();
        } catch (RuntimeException e) {
            // Повторная автоматическая постройка может снова затронуть блоки и контейнеры игрока.
            d.buildFailed = true;
            d.setDirty();
            LOGGER.error("Lab build failed in {}; automatic retries disabled. Inspect the world and logs before /hypersomnia_lab rebuild", level.dimension().location(), e);
        }
    }
}
