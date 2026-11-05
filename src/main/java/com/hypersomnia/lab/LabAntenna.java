package com.hypersomnia.lab;

import com.hypersomnia.Hypersomnia;
import com.mojang.logging.LogUtils;
import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Шифр 8 (замок LOCK_BUFFER, B3 -> B4): антенна ANT-1 на крыше реального павильона. В 03:13 буферы SOURCE 47 уходили на
 * ретранслятор, и тарелку отвернули вручную (BUFFER.LOG: азимут ретранслятора и «285 > 105»). Игрок поднимается по лестнице
 * на восточной стене и кареткой у основания мачты (AntennaDriveBlock, KIND_ANTENNA) поворачивает тарелку: ПКМ — на
 * ANTENNA_STEP по часовой стрелке, с Shift — назад. Тарелка простояла на азимуте ретранслятора ANTENNA_LOCK_TICKS при MAIN FEED
 * -> несущая, буферы ушли: замок LOCK_BUFFER открыт (лифт на B4), стойки B3 гаснут до ожидания.
 * <p>
 * Тарелка — 16 дисплеев блоков (block_display) в одной точке на оси мачты: геометрия в их transformation, азимут — их yaw,
 * поворот у клиента плавный (teleport_duration = ANTENNA_TURN_TICKS). Дисплеи сохраняются с чанком; ensure() раз в
 * ANTENNA_SCAN_TICKS сверяет число, метку версии, место и угол и при расхождении ставит заново — только когда сущности чанка
 * загружены (иначе с диска потом пришли бы старые, и тарелок стало бы две). Мир снов: статичная тарелка из блоков (LabBuilder).
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class LabAntenna {
    private LabAntenna() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Метка всех дисплеев тарелки и метка версии её геометрии: дисплеи без неё (старая геометрия) ставятся заново. */
    public static final String TAG = "hypersomnia_dish";
    private static final String TAG_VERSION = "hypersomnia_dish_v1";

    /** Деталь тарелки: блок и transformation дисплея (сдвиг, поворот, масштаб единичного куба) в системе оси. */
    private record Part(BlockState block, Vector3f translation, Quaternionf rotation, Vector3f scale) {}

    private static final List<Part> PARTS = parts();

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % LabTuning.ANTENNA_SCAN_TICKS != 0) return;
        ServerLevel level = server.overworld();
        LabSavedData d = LabSavedData.get(level);
        if (!d.built || d.dream) return;
        if (d.roofPending) {
            if (!roofLoaded(level, d)) return;
            d.roofPending = false; // одна попытка: при ошибке крыша остаётся старой, сервер не падает каждые полсекунды
            d.setDirty();
            try {
                LabBuilder.roofUpgrade(level, d);
            } catch (RuntimeException e) {
                LOGGER.error("ANT-1 roof upgrade failed", e);
            }
        }
        BlockPos drive = drive(d);
        if (drive == null || !loaded(level, drive)) return;
        ensure(level, d, drive);
        if (d.isLockOpen(LabTuning.LOCK_BUFFER) || !d.hasFlag(LabSavedData.FLAG_FEED)) return;
        if (d.dishAngle != LabVariants.antennaTarget(LabSavedData.cipherSeed(server))) return;
        if (level.getGameTime() - d.dishTurnedAt < LabTuning.ANTENNA_LOCK_TICKS) return;
        LabLocks.grant(level, d, LabTuning.LOCK_BUFFER);
        LabFixtures.refresh(level, d);
        level.playSound(null, drive, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, LabTuning.ANTENNA_CARRIER_PITCH);
        Component msg = Component.translatable("hypersomnia.lab.antenna", azimuth(d.dishAngle),
                Component.translatable("hypersomnia.lab.antenna.sent"));
        double r2 = (double) LabTuning.ANTENNA_MESSAGE_RADIUS * LabTuning.ANTENNA_MESSAGE_RADIUS;
        for (ServerPlayer p : level.players())
            if (p.distanceToSqr(Vec3.atCenterOf(drive)) <= r2) p.displayClientMessage(msg, true);
    }

    /** ПКМ по каретке: поворот на шаг (с Shift — назад), если прошлый поворот закончился; строка состояния над хотбаром. */
    public static void use(ServerPlayer player, ServerLevel level, LabSavedData d, BlockPos pos) {
        long now = level.getGameTime();
        if (now >= d.dishBusyUntil) {
            int step = player.isShiftKeyDown() ? -LabTuning.ANTENNA_STEP : LabTuning.ANTENNA_STEP;
            set(level, d, d.dishAngle + step);
            d.dishBusyUntil = now + LabTuning.ANTENNA_TURN_TICKS;
            level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS,
                    LabTuning.ANTENNA_SOUND_VOLUME, LabTuning.ANTENNA_SOUND_PITCH);
        }
        player.displayClientMessage(status(level, d), true);
    }

    /** «ANT-1 · AZ 105° · NO CARRIER»: без MAIN FEED — нет питания; на азимуте ретранслятора — поиск, после передачи — несущая. */
    public static Component status(ServerLevel level, LabSavedData d) {
        String s;
        if (!d.hasFlag(LabSavedData.FLAG_FEED)) s = "nopower";
        else if (d.dishAngle != LabVariants.antennaTarget(LabSavedData.cipherSeed(level.getServer()))) s = "nocarrier";
        else s = d.isLockOpen(LabTuning.LOCK_BUFFER) ? "carrier" : "search";
        return Component.translatable("hypersomnia.lab.antenna", azimuth(d.dishAngle), Component.translatable("hypersomnia.lab.antenna." + s));
    }

    /** Азимут тремя цифрами, как в BUFFER.LOG. */
    public static String azimuth(int az) {
        return String.format(Locale.ROOT, "%03d", Math.floorMod(az, 360));
    }

    /** Поставить азимут (по модулю 360): данные мира и yaw дисплеев; отсчёт несущей начинается заново. */
    public static void set(ServerLevel level, LabSavedData d, int az) {
        d.dishAngle = Math.floorMod(az, 360);
        d.dishTurnedAt = level.getGameTime();
        d.setDirty();
        BlockPos drive = drive(d);
        if (drive == null || !loaded(level, drive)) return;
        float yaw = yaw(d.dishAngle);
        for (Display.BlockDisplay e : find(level, pivot(drive))) e.setYRot(yaw);
    }

    /** Новое зерно шифров (index reroll): тарелка — на начальный азимут нового зерна или на ретранслятор, если буферы ушли. */
    public static void reseed(ServerLevel level, LabSavedData d) {
        if (!d.built || d.dream || drive(d) == null) return;
        long seed = LabSavedData.cipherSeed(level.getServer());
        set(level, d, d.isLockOpen(LabTuning.LOCK_BUFFER) ? LabVariants.antennaTarget(seed) : LabVariants.antennaStart(seed));
    }

    /** Убрать дисплеи тарелки (снос постройки); незагруженные уберёт ensure() после новой стройки (лишнее число). */
    public static void removeDisplays(ServerLevel level, LabSavedData d) {
        BlockPos drive = drive(d);
        if (drive == null || !loaded(level, drive)) return;
        for (Display.BlockDisplay e : find(level, pivot(drive))) e.discard();
    }

    /** Сколько дисплеев тарелки стоит сейчас (отладка), -1 — чанк не загружен или антенны нет. */
    public static int count(ServerLevel level, LabSavedData d) {
        BlockPos drive = drive(d);
        if (drive == null || !loaded(level, drive)) return -1;
        return find(level, pivot(drive)).size();
    }

    /** Каретка ANT-1 или null (сон, старый мир до перестройки крыши). */
    public static BlockPos drive(LabSavedData d) {
        return d.find(LabSavedData.KIND_ANTENNA * 1000);
    }

    // ---------------- Дисплеи ----------------

    /** Число, метка версии и место дисплеев не те — поставить заново; угол не тот — повернуть. */
    private static void ensure(ServerLevel level, LabSavedData d, BlockPos drive) {
        Vec3 pivot = pivot(drive);
        List<Display.BlockDisplay> found = find(level, pivot);
        float yaw = yaw(d.dishAngle);
        boolean ok = found.size() == PARTS.size();
        for (Display.BlockDisplay e : found)
            if (!e.getTags().contains(TAG_VERSION) || e.position().distanceToSqr(pivot) > 1.0E-4) ok = false;
        if (!ok) {
            for (Display.BlockDisplay e : found) e.discard();
            spawn(level, pivot, yaw);
            return;
        }
        for (Display.BlockDisplay e : found)
            if (Math.abs(Mth.wrapDegrees(e.getYRot() - yaw)) > 0.01f) e.setYRot(yaw);
    }

    private static void spawn(ServerLevel level, Vec3 pivot, float yaw) {
        for (Part p : PARTS) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", "minecraft:block_display");
            tag.put(Display.BlockDisplay.TAG_BLOCK_STATE, NbtUtils.writeBlockState(p.block()));
            Transformation t = new Transformation(new Vector3f(p.translation()), new Quaternionf(p.rotation()),
                    new Vector3f(p.scale()), new Quaternionf());
            Transformation.EXTENDED_CODEC.encodeStart(NbtOps.INSTANCE, t).result().ifPresent(n -> tag.put(Display.TAG_TRANSFORMATION, n));
            tag.putInt(Display.TAG_POS_ROT_INTERPOLATION_DURATION, LabTuning.ANTENNA_TURN_TICKS);
            tag.putFloat(Display.TAG_VIEW_RANGE, LabTuning.ANTENNA_VIEW_RANGE);
            ListTag tags = new ListTag();
            tags.add(StringTag.valueOf(TAG));
            tags.add(StringTag.valueOf(TAG_VERSION));
            tag.put("Tags", tags);
            Entity e = EntityType.loadEntityRecursive(tag, level, ent -> {
                ent.moveTo(pivot.x, pivot.y, pivot.z, yaw, 0.0f);
                return ent;
            });
            if (e != null) level.addFreshEntity(e);
        }
    }

    private static List<Display.BlockDisplay> find(ServerLevel level, Vec3 pivot) {
        return level.getEntitiesOfClass(Display.BlockDisplay.class, new AABB(pivot, pivot).inflate(3.0),
                e -> e.getTags().contains(TAG));
    }

    /** Ось поворота: центр верхушки мачты над кареткой. */
    private static Vec3 pivot(BlockPos drive) {
        return new Vec3(drive.getX() + 0.5, drive.getY() + 1 + LabTuning.ANTENNA_MAST, drive.getZ() + 0.5);
    }

    /** Компасный азимут (0 = север, 90 = восток) -> yaw сущности, у которой +Z модели смотрит на этот азимут. */
    private static float yaw(int az) {
        return Math.floorMod(az + 180, 360);
    }

    /** Блок и сущности чанка загружены: только тогда искать и ставить дисплеи. */
    private static boolean loaded(ServerLevel level, BlockPos p) {
        return level.isLoaded(p) && level.areEntitiesLoaded(ChunkPos.asLong(p));
    }

    /** Старый мир: загружены углы павильона (с лестницей), клетка мачты и панель LOCK_BUFFER на B3. */
    private static boolean roofLoaded(ServerLevel level, LabSavedData d) {
        int x1 = d.bbox[0] - 1, z1 = d.bbox[1] - 1, x2 = d.bbox[2] + 1, z2 = d.bbox[3] + 1, y = d.stopY[0];
        for (BlockPos p : new BlockPos[]{new BlockPos(x1, y, z1), new BlockPos(x2, y, z1), new BlockPos(x1, y, z2),
                new BlockPos(x2, y, z2), new BlockPos((x1 + x2) / 2, y, (z1 + z2) / 2)})
            if (!loaded(level, p)) return false;
        BlockPos lock = d.lockPos(LabTuning.LOCK_BUFFER);
        return lock == null || level.isLoaded(lock);
    }

    // ---------------- Геометрия ----------------

    /**
     * Детали в системе оси (yaw 0): +Z — куда смотрит тарелка, +Y — вверх. Поворотная голова на верхушке мачты и стойка к
     * ступице; чаша (наклон ANTENNA_ELEVATION вверх) — восьмиугольник из 9 тонких пластин по параболоиду z = k r^2: центральная,
     * 4 боковые и 4 угловые, повёрнутые на 45°; ступица сзади, облучатель в фокусе на трёх растяжках.
     */
    private static List<Part> parts() {
        List<Part> out = new ArrayList<>();
        BlockState dish = Blocks.IRON_BLOCK.defaultBlockState();
        BlockState dark = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        Quaternionf none = new Quaternionf();
        Vector3f zero = new Vector3f();
        out.add(part(dark, none, zero, new Vector3f(0, 0.12f, 0), none, new Vector3f(0.56f, 0.24f, 0.56f)));
        out.add(part(dark, none, zero, new Vector3f(0, 0.46f, -0.04f), none, new Vector3f(0.18f, 0.5f, 0.18f)));

        Quaternionf tilt = new Quaternionf().rotationX((float) Math.toRadians(-LabTuning.ANTENNA_ELEVATION));
        Vector3f hub = new Vector3f(0, LabTuning.ANTENNA_HUB_UP, LabTuning.ANTENNA_HUB_FORWARD);
        float k = LabTuning.ANTENNA_DEPTH, t = 0.07f, step = 0.98f, c = 0.62f;
        out.add(part(dish, tilt, hub, new Vector3f(0, 0, 0), none, new Vector3f(1.04f, 1.04f, t)));
        for (int[] sd : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            float x = sd[0] * step, y = sd[1] * step;
            Vector3f size = sd[0] != 0 ? new Vector3f(0.96f, 1.04f, t) : new Vector3f(1.04f, 0.96f, t);
            out.add(part(dish, tilt, hub, new Vector3f(x, y, k * (x * x + y * y)), normal(k, x, y), size));
        }
        for (int[] sd : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
            float x = sd[0] * c, y = sd[1] * c;
            Quaternionf q = normal(k, x, y).mul(new Quaternionf().rotationZ((float) (Math.PI / 4)));
            out.add(part(dish, tilt, hub, new Vector3f(x, y, k * (x * x + y * y) - 0.012f), q, new Vector3f(1.24f, 1.24f, t)));
        }
        out.add(part(dark, tilt, hub, new Vector3f(0, 0, -0.17f), none, new Vector3f(0.46f, 0.46f, 0.26f)));
        float focus = 1.0f / (4.0f * k);
        out.add(part(dark, tilt, hub, new Vector3f(0, 0, focus), none, new Vector3f(0.2f, 0.2f, 0.32f)));
        for (int n = 0; n < 3; n++) {
            double a = Math.toRadians(90 + 120 * n);
            float r = 1.38f;
            Vector3f from = new Vector3f((float) (r * Math.cos(a)), (float) (r * Math.sin(a)), k * r * r);
            Vector3f to = new Vector3f(0, 0, focus - 0.14f);
            Vector3f dir = new Vector3f(to).sub(from);
            Vector3f mid = new Vector3f(from).add(to).mul(0.5f);
            Quaternionf q = new Quaternionf().rotationTo(0, 0, 1, dir.x, dir.y, dir.z);
            out.add(part(dark, tilt, hub, mid, q, new Vector3f(0.05f, 0.05f, dir.length())));
        }
        return List.copyOf(out);
    }

    /** Поворот +Z пластины на нормаль параболоида z = k r^2 в точке (x, y) (внутрь чаши). */
    private static Quaternionf normal(float k, float x, float y) {
        Vector3f n = new Vector3f(-2 * k * x, -2 * k * y, 1).normalize();
        return new Quaternionf().rotationTo(0, 0, 1, n.x, n.y, n.z);
    }

    /**
     * Коробка size с центром center и поворотом rot в системе (frameRot, frameT) -> transformation единичного куба:
     * T(сдвиг) * R(frameRot * rot) * S(size), куб [0,1]^3 центром попадает в точку.
     */
    private static Part part(BlockState block, Quaternionf frameRot, Vector3f frameT, Vector3f center, Quaternionf rot, Vector3f size) {
        Quaternionf q = new Quaternionf(frameRot).mul(rot);
        Vector3f c = new Quaternionf(frameRot).transform(new Vector3f(center)).add(frameT);
        Vector3f half = new Quaternionf(q).transform(new Vector3f(size).mul(0.5f));
        return new Part(block, c.sub(half), q, new Vector3f(size));
    }
}
