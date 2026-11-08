package com.hypersomnia.lab;

import com.hypersomnia.ModBlocks;
import com.hypersomnia.finale.FinaleTuning;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

import java.util.Arrays;

/**
 * Строит один этаж по чертежу. Легенда символов:
 * <pre>
 * ' '  пусто (не трогаем)        #  стена от пола до потолка
 * .    пол                        =  проём 3 высотой (перемычка выше)
 * L    пол кабины                 @  центр кабины (пол кабины)
 * Y    пол кабины + лампа         l  дверь кабины лифта (3 высотой, решётка, когда закрыта)
 * *    пол + лампа в потолке      E  пол кабины + панель лифта (LiftPanelBlock, STOP = этаж)
 * 1-9  табличка лаборатории (LabSignBlock) из строки sign    K, J  кодовая панель замка (KeypadBlock, id из строки lock)
 * G, N (любой символ из строк gate в чертеже)  решётка-ворота: проём как '=', закрыт прутьями, пока закрыт замок (KIND_GATE)
 * B (любой символ из строк panel в чертеже)  щит рубильников на стене (id 0 = MAIN FEED, KIND_BREAKER) + кабель-канал до потолка
 * P, Q (любой символ из строк station в чертеже)  рабочая станция CRADLE WORKSTATION (KIND_TERMINAL) на столе
 * Мебель (только интерьер):
 * D  стол                         d  стол + засохшее растение в горшке
 * V  стол + погасший монитор      Z  стол + монитор со «снегом»
 * F  картотечный шкаф (бумага)    p  шкаф + засохшее растение
 * f, g, i (любой символ из строк cabinet в чертеже)  шкаф с документом из строки cabinet (LabCabinets)
 * o (шкаф из строк cabinet + seal)  запечатанный ящик: только документ, открывается вместе с замком (KIND_SEAL)
 * T  низкий столик (плита)        R  стеллаж (2 высотой)
 * e (любой символ из строк equip в чертеже)  оборудование серверной LabEquipmentBlock (2 высотой): кондиционер, ИБП, батареи
 * C  секция капсулы: белое ложе и тёмное стекло крышки (капсула = несколько C подряд)
 * h  стул                         H  стул, развёрнутый наоборот (мир снов)
 * v  решётка вентиляции на стене под потолком
 * A, U (любой символ из строк capsule в чертеже)  капсула с номером, как C (KIND_CAPSULE * 1000 + номер, ложе и крышка)
 * Детская финала (символы из строк finale в чертеже, KIND_FINALE * 1000 + часть FinaleTuning.PART_*):
 * S (bed)  кровать: изголовье на клетке у стены, изножье на соседней клетке пола в сторону комнаты (обе половины в inter)
 * O (door) дверь в стене: клетка стены с проёмом 2 высотой и деревянной дверью лицом в комнату (обе половины в inter)
 * M (recorder)  стол + погасший монитор записи (стол и монитор в inter)
 * X (fragment)  ковёр; над ним метка, где стоит Фрагмент (в inter на высоте 1, блока нет)
 * u (toy)  красный ковёр (игрушка на полу); над ним метка, куда FinaleHandler кладёт рисунок 4 (в inter на высоте 1)
 * w  окно: стена с тёмным стеклом на высоте 1-2       r  светлый ковёр       u  красный ковёр (без строки finale)
 * b (любой символ из строк bed в чертеже)  обычная кровать цвета из строки: изголовье у стены, изножье на соседней клетке пола
 * a (любой символ из строк rack)  серверная стойка ServerRackBlock (2 высотой) с номером по линии (KIND_RACK; лампы — LabFixtures)
 * q (любой символ из строк stack)  место ряда хранилища: книжная полка x2, пустое место — резная полка (KIND_STACK; LabFixtures)
 * k (любой символ из строк knock)  стена: заложенная дверь (KIND_KNOCK: только осмотр; код больше не выстукивается)
 * +  раковина (пустой котёл)       ~  ширма: белая стеклянная панель x2, сцеплена с соседними ширмами и стенами
 * </pre>
 * Табличка (1-9), панели (E, K, J) и щит стоят на клетке пола на высоте 1 и крепятся к первой найденной стене (порядок С, Ю, В, З);
 * нет стены рядом — к торцу высокой мебели (стеллаж, стойка, ряд хранилища): так подписываются ряды.
 * Стул (h): лицом к соседнему монитору или станции, иначе к соседнему столу (D, d, T; порядок С, Ю, В, З);
 * нет стола — спинкой к стене; нет и стены — лицом к кабине лифта.
 * Стол, монитор и станция смотрят (ящики, экран) на свой стул; без своего стула — туда же, куда ряд столов,
 * в котором стоят (стулья сбоку ряда); иначе от стены; иначе к ближайшему стулу (LabTuning.DESK_CHAIR_REACH).
 * Сторону экрана станции можно задать в строке station.
 * Шкаф (F, p, шкаф с документом) — дверцами на свободную клетку пола; из свободных сторон лучше та,
 * где спиной к стене и перед дверцами два шага места.
 */
public final class LabBuilder {
    private LabBuilder() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private record Style(BlockState wall, BlockState patch, BlockState cracked, BlockState wrong,
                         BlockState trim, BlockState floor, BlockState floor2, BlockState roof,
                         int patchEvery, int crackEvery, int wrongEvery) {}

    /**
     * Палитра общая для обоих миров: исследовательский центр — каменный кирпич с тёмной
     * сланцевой отделкой и шахматным каменным полом. Мир снов — те же блоки, только чаще
     * заплатки/трещины и редкий «чужой» блок в стене.
     */
    private static Style style(boolean dream) {
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState trim = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState floor2 = Blocks.POLISHED_ANDESITE.defaultBlockState();
        BlockState roof = Blocks.DEEPSLATE_TILES.defaultBlockState();
        if (dream) {
            return new Style(wall, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
                    Blocks.DEEPSLATE_BRICKS.defaultBlockState(), trim, floor, floor2, roof,
                    LabTuning.PATCH_DREAM, LabTuning.CRACKS_DREAM, LabTuning.WRONG_DREAM);
        }
        return new Style(wall, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
                wall, trim, floor, floor2, roof,
                LabTuning.PATCH_REAL, LabTuning.CRACKS_REAL, 0);
    }

    /** Блок стены: одинаков при каждой перестройке (хэш от координат, без Random). */
    private static BlockState wallAt(Style s, int x, int y, int z) {
        int hsh = Math.floorMod(x * 31 + y * 17 + z * 7, 1440);
        if (s.wrongEvery > 0 && Math.floorMod(hsh, s.wrongEvery) == 0) return s.wrong;
        if (s.patchEvery > 0 && Math.floorMod(hsh, s.patchEvery) == 0) return s.patch;
        if (s.crackEvery > 0 && Math.floorMod(hsh, s.crackEvery) == 0) return s.cracked;
        return s.wall;
    }


    /** [новое] Светильник лаборатории: металлическая планка и матовая трубка вместо торцевого стержня (END_ROD). */
    private static BlockState lamp(Direction pointing) {
        return ModBlocks.LIGHT_TUBE.get().defaultBlockState().setValue(LightTubeBlock.FACING, pointing);
    }

    /**
     * [новое] Миграция старого мира: торцевые стержни в объёме лаборатории (потолки, фонари у входа, мачта тарелки)
     * заменяются светильниками light_tube с тем же направлением. Зовёт LabPlacement по флагу lampsPending.
     */
    public static void upgradeLamps(ServerLevel lv, LabSavedData d) {
        for (int stop = 0; stop < LabTuning.STOPS; stop++) {
            if (!d.isBuilt(stop)) continue;
            int fy = d.stopY[stop], h = d.stopH[stop];
            int x1 = d.bbox[stop * 4], z1 = d.bbox[stop * 4 + 1];
            int x2 = d.bbox[stop * 4 + 2], z2 = d.bbox[stop * 4 + 3];
            int pad = stop == 0 ? 2 : 0;              // фонари у входа стоят за границей чертежа
            int top = stop == 0 ? fy + h + 11 : fy + h; // на крыше — мачта тарелки
            for (int x = x1 - pad; x <= x2 + pad; x++)
                for (int z = z1 - pad; z <= z2 + pad; z++)
                    for (int y = fy; y <= top; y++) {
                        BlockPos p = new BlockPos(x, y, z);
                        BlockState s = lv.getBlockState(p);
                        if (!s.is(Blocks.END_ROD)) continue;
                        lv.setBlock(p, lamp(s.getValue(LightTubeBlock.FACING)), 2);
                    }
        }
    }

    /** Фасад павильона (только поверхность): тёмные пилястры по углам и на сторонах (мимо входа). */
    private static void facade(ServerLevel lv, Style s, int fy, int h, int x1, int z1, int x2, int z2, int[] door) {
        pilaster(lv, s, fy, h, x1, z1); // углы — тёмные столбы во всю высоту
        pilaster(lv, s, fy, h, x2, z1);
        pilaster(lv, s, fy, h, x1, z2);
        pilaster(lv, s, fy, h, x2, z2);
        boolean doorOnZ = door[0] >= 0 && (door[2] == z1 || door[2] == z2);
        boolean doorOnX = door[0] >= 0 && (door[0] == x1 || door[0] == x2);
        double f = LabTuning.PILASTER_FRACTION;
        int aX = x1 + (int) ((x2 - x1 + 1) * f), bX = x2 - (int) ((x2 - x1 + 1) * f);
        int aZ = z1 + (int) ((z2 - z1 + 1) * f), bZ = z2 - (int) ((z2 - z1 + 1) * f);
        for (int px : new int[]{aX, bX}) { // северный и южный фасады
            if (doorOnZ && Math.abs(px - door[0]) <= 2) continue;
            pilaster(lv, s, fy, h, px, z1);
            pilaster(lv, s, fy, h, px, z2);
        }
        for (int pz : new int[]{aZ, bZ}) { // восточный и западный фасады
            if (doorOnX && Math.abs(pz - door[2]) <= 2) continue;
            pilaster(lv, s, fy, h, x1, pz);
            pilaster(lv, s, fy, h, x2, pz);
        }
    }

    /** Сторона зоны антенны на крыше (квадрат DISH_ZONE x DISH_ZONE). */
    private static final int DISH_ZONE = 4;

    /**
     * Угол (x, z) зоны антенны DISH_ZONE x DISH_ZONE на крыше: ближе к центру, мимо машинного помещения над лифтом и люков;
     * null — крыша мала. Одинаков при каждой стройке (по нему же roofUpgrade находит старую тарелку).
     */
    private static int[] dishSpot(int x1, int z1, int x2, int z2, int[] lift) {
        int size = DISH_ZONE;
        if (x2 - x1 < size + 2 || z2 - z1 < size + 2) return null;
        int inset = LabTuning.ROOF_HATCH_INSET;
        int hx1 = x1 + inset, hz1 = z1 + inset, hx2 = x2 - inset, hz2 = z2 - inset;
        int midX = (x1 + x2) / 2, midZ = (z1 + z2) / 2;
        int bestX = Integer.MIN_VALUE, bestZ = 0, best = Integer.MIN_VALUE;
        for (int px = x1 + 1; px <= x2 - size; px++) {
            for (int pz = z1 + 1; pz <= z2 - size; pz++) {
                if (lift[0] >= 0 && overlaps(px, pz, size, lift[0], lift[2], 1)) continue; // машинное помещение
                if (overlaps(px, pz, size, hx1, hz1, 0) || overlaps(px, pz, size, hx2, hz2, 0)) continue; // люки
                int score = -(Math.abs(px + size / 2 - midX) + Math.abs(pz + size / 2 - midZ));
                if (score > best) {
                    best = score;
                    bestX = px;
                    bestZ = pz;
                }
            }
        }
        return best == Integer.MIN_VALUE ? null : new int[]{bestX, bestZ};
    }

    /**
     * Мир снов: статичная тарелка — три плоскости по три железных люка (пол и две стенки), каждая «Г» из 2x2 без
     * дальней клетки; вместе образуют угол куба, с любой стороны читается «Г» с тонкими кромками
     * двух других плоскостей. Всего 9 люков. В реальном мире вместо неё антенна ANT-1 (antenna()).
     */
    private static void dish(ServerLevel lv, Style s, int fy, int h, int x1, int z1, int x2, int z2, int[] lift, LabSavedData d) {
        int size = DISH_ZONE; // плита-основание 4x4
        int[] spot = dishSpot(x1, z1, x2, z2, lift);
        if (spot == null) return;
        int bestX = spot[0], bestZ = spot[1];
        int y = fy + h; // кровля
        // Основа 2x2 прямо на кровле: две ограды из каменного кирпича (2 блока) по одной диагонали
        // и два полированных андезита (1 блок) по другой. Тарелка лежит на оградах.
        // Тарелка: три плоскости по три люка (пол и две стенки), каждая «Г» из 2x2 без дальней клетки.
        // Мачта стоит в пустой клетке тарелки, прямо на её уровне.
        for (int a = 0; a < size; a++) // очистка зоны (на случай старой антенны при rebuild)
            for (int b = 0; b < size; b++)
                for (int k = 1; k <= 9; k++) set(lv, bestX + a, y + k, bestZ + b, AIR);
        int x0 = bestX + 1, z0 = bestZ + 1; // первая клетка пола
        int p = y + 3;                       // уровень тарелки: на верхушках оград
        BlockState fence = Blocks.STONE_BRICK_WALL.defaultBlockState();
        for (int k = 1; k <= 2; k++) {       // ограды: клетки (0,0) и (1,1)
            set(lv, x0, y + k, z0, fence);
            set(lv, x0 + 1, y + k, z0 + 1, fence);
        }
        set(lv, x0 + 1, y + 1, z0, s.floor2);  // андезит: клетки (1,0) и (0,1)
        set(lv, x0, y + 1, z0 + 1, s.floor2);
        BlockState flat = trapdoor(Direction.NORTH, false);  // закрытый, half=bottom
        BlockState wallN = trapdoor(Direction.NORTH, true);  // панель у южного края клетки, вплотную к полу [проверить]
        BlockState wallW = trapdoor(Direction.WEST, true);   // панель у восточного края клетки [проверить]
        set(lv, x0, p, z0, flat);                // пол
        set(lv, x0 + 1, p, z0, flat);
        set(lv, x0, p, z0 + 1, flat);
        set(lv, x0, p, z0 - 1, wallN);           // северная стенка (ряд z0-1)
        set(lv, x0 + 1, p, z0 - 1, wallN);
        set(lv, x0, p + 1, z0 - 1, wallN);
        set(lv, x0 - 1, p, z0, wallW);           // западная стенка (столбец x0-1)
        set(lv, x0 - 1, p, z0 + 1, wallW);
        set(lv, x0 - 1, p + 1, z0, wallW);
        for (int k = 0; k < 6; k++) set(lv, x0 + 1, p + k, z0 + 1, Blocks.IRON_BARS.defaultBlockState()); // мачта из тарелки
        set(lv, x0 + 1, p + 6, z0 + 1, lamp(Direction.UP));  // огонёк-светильник на конце мачты
        d.dishPos = new BlockPos(bestX + 1, y + 1, bestZ + 1).asLong();
        d.dishAngle = 0;
    }

    /**
     * Реальный мир: антенна ANT-1 (шифр 8, LabAntenna). В клетке мачты прежней тарелки — поворотная каретка
     * (AntennaDriveBlock, штурвал к востоку, к лестнице) и мачта из ограды полированного сланца; саму тарелку (дисплеи)
     * ставит LabAntenna. Снаружи восточной стены — лестница на крышу, в парапете над ней проём, рядом табличка ROOF.
     * Азимут: начальный по зерну мира или азимут ретранслятора, если буферы уже ушли (замок LOCK_BUFFER открыт).
     */
    private static void antenna(ServerLevel lv, Style s, int fy, int h, int x1, int z1, int x2, int z2, int[] lift, LabSavedData d) {
        int[] spot = dishSpot(x1, z1, x2, z2, lift);
        if (spot == null) return;
        int y = fy + h; // кровля
        for (int a = 0; a < DISH_ZONE; a++)
            for (int b = 0; b < DISH_ZONE; b++)
                for (int k = 1; k <= 9; k++) set(lv, spot[0] + a, y + k, spot[1] + b, AIR);
        int mx = spot[0] + 2, mz = spot[1] + 2; // клетка мачты прежней тарелки
        BlockPos drive = new BlockPos(mx, y + 1, mz);
        put(lv, drive, ModBlocks.ANTENNA_DRIVE.get().defaultBlockState().setValue(AntennaDriveBlock.FACING, Direction.EAST), 2);
        for (int k = 0; k < LabTuning.ANTENNA_MAST; k++) set(lv, mx, y + 2 + k, mz, Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState());
        d.inter.put(drive.asLong(), LabSavedData.KIND_ANTENNA * 1000);
        d.dishPos = drive.asLong();
        long seed = LabSavedData.cipherSeed(lv.getServer());
        d.dishAngle = d.isLockOpen(LabTuning.LOCK_BUFFER) ? LabVariants.antennaTarget(seed) : LabVariants.antennaStart(seed);
        int lz = ladderZ(z1, z2);
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
        for (int ly = fy; ly <= y; ly++) set(lv, x2 + 1, ly, lz, ladder);
        set(lv, x2, y + 1, lz, AIR); // проём в парапете над лестницей
        set(lv, x2, y + 2, lz, AIR);
        LabSigns.wall(lv, new BlockPos(x2 + 1, fy + 1, lz + 1), Direction.EAST, "ROOF", "ANT-1", "AUTHORISED", "STAFF ONLY");
    }

    /** z лестницы на восточной стене: ближе к середине, не под столбиком парапета и не на пилястре фасада (как facade()). */
    private static int ladderZ(int z1, int z2) {
        int mid = (z1 + z2) / 2;
        double f = LabTuning.PILASTER_FRACTION;
        int aZ = z1 + (int) ((z2 - z1 + 1) * f), bZ = z2 - (int) ((z2 - z1 + 1) * f);
        for (int i = 0; i <= z2 - z1; i++) {
            int z = mid + ((i & 1) == 1 ? (i + 1) / 2 : -(i / 2));
            if (z <= z1 + 1 || z >= z2 - 1) continue;
            if (isPost(z - z1, z2 - z1, LabTuning.PARAPET_POST_EVERY) || z == aZ || z == bZ) continue;
            return z;
        }
        return mid;
    }

    /**
     * Старый реальный мир (LabSavedData.roofPending): статичная тарелка уступает место антенне ANT-1 с лестницей и
     * табличкой, табличка у панели LOCK_BUFFER на B3 получает новый текст. Зовёт LabAntenna, когда чанки загружены.
     */
    public static void roofUpgrade(ServerLevel lv, LabSavedData d) {
        if (!d.isBuilt(0)) return;
        int fy = d.stopY[0], h = d.stopH[0];
        int x1 = d.bbox[0], z1 = d.bbox[1], x2 = d.bbox[2], z2 = d.bbox[3];
        int[] lift = {d.originX, 0, d.originZ};
        int[] spot = dishSpot(x1, z1, x2, z2, lift);
        if (spot == null) return;
        int y = fy + h;
        for (int k = 9; k >= 1; k--) // сверху вниз, без обновления соседей: ничего не отваливается предметом
            for (int a = 0; a < DISH_ZONE; a++)
                for (int b = 0; b < DISH_ZONE; b++) put(lv, new BlockPos(spot[0] + a, y + k, spot[1] + b), AIR, CLEAR_FLAGS);
        antenna(lv, style(false), fy, h, x1, z1, x2, z2, lift, d);
        BlockPos lock = d.lockPos(LabTuning.LOCK_BUFFER);
        if (lock == null) return;
        for (BlockPos p : BlockPos.betweenClosed(lock.offset(-3, -1, -3), lock.offset(3, 1, 3)))
            if (lv.getBlockEntity(p) instanceof LabSignBlockEntity sign && sign.key().equals("lift_access_b3_to_level_b4_held_buffers_ack"))
                sign.setLines("LIFT ACCESS B3", "TO LEVEL B4", "UPLINK ANT-1"); // как sign 9 в real_b3.txt
    }

    /** Железный люк: open=true — вертикальная панель, open=false — горизонтальная. */
    private static BlockState trapdoor(Direction facing, boolean open) {
        return trapdoor(facing, open, Half.BOTTOM);
    }

    /** По умолчанию у люка half=top (первое значение enum), поэтому half задаём явно. */
    private static BlockState trapdoor(Direction facing, boolean open, Half half) {
        return Blocks.IRON_TRAPDOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, open)
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, facing)
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, half);
    }

    /** Полублок столбика ограды: тот же каменный кирпич, что и сама ограда. */
    private static final BlockState PARAPET_SLAB = Blocks.STONE_BRICK_SLAB.defaultBlockState();

    /** Столбик ставится на обоих углах и на каждом step-м блоке ограды. */
    private static boolean isPost(int i, int len, int step) {
        return i == 0 || i == len || i % step == 0;
    }

    /** Пересекается ли квадрат size x size в (x,z) с точкой (ox,oz) и запасом pad. */
    private static boolean overlaps(int x, int z, int size, int ox, int oz, int pad) {
        return ox >= x - pad && ox < x + size + pad && oz >= z - pad && oz < z + size + pad;
    }

    private static void pilaster(ServerLevel lv, Style s, int fy, int h, int x, int z) {
        for (int y = fy; y <= fy + h - 1; y++) set(lv, x, y, z, s.trim);
    }

    /**
     * Рельеф плоской крыши (никаких скатов): ограда из каменного кирпича по краю со столбиками
     * (блок + полублок сверху) на углах и с шагом, машинное помещение над кабиной лифта и два люка.
     * Плюс антенна: во сне статичная тарелка (dish()), в реальном мире ANT-1 (antenna()).
     */
    private static void roofDetails(ServerLevel lv, Style s, int fy, int h, int x1, int z1, int x2, int z2, int[] lift) {
        int y = fy + h + 1; // первый ряд над кровлей: сама ограда
        int step = LabTuning.PARAPET_POST_EVERY;
        for (int x = x1; x <= x2; x++) { // ограда: один ряд каменного кирпича по периметру
            set(lv, x, y, z1, s.wall);
            set(lv, x, y, z2, s.wall);
            if (isPost(x - x1, x2 - x1, step)) {
                set(lv, x, y + 1, z1, PARAPET_SLAB);
                set(lv, x, y + 1, z2, PARAPET_SLAB);
            }
        }
        for (int z = z1 + 1; z < z2; z++) {
            set(lv, x1, y, z, s.wall);
            set(lv, x2, y, z, s.wall);
            if (isPost(z - z1, z2 - z1, step)) {
                set(lv, x1, y + 1, z, PARAPET_SLAB);
                set(lv, x2, y + 1, z, PARAPET_SLAB);
            }
        }

    }

    /**
     * Проход: '=' — 3 блока в ширину на 3 в высоту, 'l' — дверь лифта 1x3 (механизм остаётся одноколоночным).
     * Вокруг проёма — тёмная рамка: столбы по бокам и перемычка сверху. Рисуется после стен.
     */
    private static void passage(ServerLevel lv, Style s, LabBlueprint bp, int x, int z, int wx, int wz, int fy, int h, boolean widen) {
        boolean alongX = bp.at(x - 1, z) == '#' && bp.at(x + 1, z) == '#';
        int dx = alongX ? 1 : 0; // нет стен по бокам — считаем, что стена идёт вдоль Z
        int dz = alongX ? 0 : 1;
        int half = widen ? 1 : 0;     // половина ширины проёма
        int frame = half + 1;         // рамка сразу за проёмом
        for (int k = -frame; k <= frame; k++) {
            int px = wx + dx * k, pz = wz + dz * k;
            set(lv, px, fy - 1, pz, s.floor2); // тёмный порог
            if (Math.abs(k) <= half) {
                for (int y = 0; y < 3; y++) set(lv, px, fy + y, pz, AIR);
                for (int y = 3; y < h; y++) set(lv, px, fy + y, pz, s.trim); // перемычка
            } else {
                for (int y = 0; y < h; y++) set(lv, px, fy + y, pz, s.trim); // столб рамки
            }
        }
    }

    private static final Direction[] ORDER = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    /**
     * @param ox, oz мировые координаты клетки '@'
     * @param fy     y первого блока воздуха над полом
     * @param stop   номер остановки лифта (0 = поверхность)
     */
    public static void build(ServerLevel level, LabBlueprint bp, int ox, int oz, int fy, int stop, boolean surface, LabSavedData d) {
        Style s = style(bp.dream);
        int h = bp.height;
        int[] door = {-1, 0, 0}; // world x/y/z внешнего входа ('=')
        int[] lift = {-1, 0, 0}; // world x/y/z кабины лифта ('@')
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        if (surface) {
            int[] b = bounds(bp, ox, oz);
            terraform(level, s, fy, b[0], b[1], b[2], b[3], entrance(bp, ox, oz));
        }

        for (int z = 0; z < bp.d; z++) {
            for (int x = 0; x < bp.w; x++) {
                char c = bp.at(x, z);
                if (c == ' ') continue;
                int wx = ox + x - bp.ax, wz = oz + z - bp.az;
                minX = Math.min(minX, wx);
                maxX = Math.max(maxX, wx);
                minZ = Math.min(minZ, wz);
                maxZ = Math.max(maxZ, wz);
                Integer part = bp.finale.get(c);
                column(level, s, c, wx, wz, fy, h, bp.gates.containsKey(c), part != null && part == FinaleTuning.PART_DOOR,
                        bp.knocks.containsKey(c));
                if (c == '@' && lift[0] < 0) {
                    lift[0] = wx;
                    lift[2] = wz;
                }
                if (surface && c == '=') {
                    door[0] = wx;
                    door[1] = fy;
                    door[2] = wz;
                    lampPosts(level, bp, s, x, z, wx, wz, fy);
                }
                if (c == 'l') d.door[stop] = new BlockPos(wx, fy, wz).asLong();
            }
        }

        // Таблички, мебель и интерактив (после стен: нужны опора и соседи).
        for (int z = 0; z < bp.d; z++) {
            for (int x = 0; x < bp.w; x++) {
                char c = bp.at(x, z);
                if (c == ' ' || c == '#') continue;
                int wx = ox + x - bp.ax, wz = oz + z - bp.az;
                Integer gate = bp.gates.get(c);
                if (c == '=' || c == 'l' || gate != null) { // проходы режем после стен, иначе соседние клетки закроют проём
                    passage(level, s, bp, x, z, wx, wz, fy, h, c != 'l');
                    if (gate != null) gate(level, bp, x, z, wx, wz, fy, gate, d);
                    continue;
                }
                String[] station = bp.stations.get(c);
                if (station != null) {
                    workstation(level, bp, x, z, wx, wz, fy, station, d);
                    continue;
                }
                Integer part = bp.finale.get(c);
                if (part != null) {
                    finalePart(level, bp, x, z, wx, wz, fy, part, d);
                    continue;
                }
                String bedColor = bp.beds.get(c);
                if (bedColor != null) {
                    bed(level, bp, x, z, wx, wz, fy, bedBlock(bedColor));
                    continue;
                }
                Integer capsule = bp.capsules.get(c);
                if (capsule != null) {
                    int v = LabSavedData.KIND_CAPSULE * 1000 + capsule;
                    set(level, wx, fy, wz, Blocks.SMOOTH_QUARTZ.defaultBlockState());
                    set(level, wx, fy + 1, wz, Blocks.TINTED_GLASS.defaultBlockState());
                    d.inter.put(new BlockPos(wx, fy, wz).asLong(), v);
                    d.inter.put(new BlockPos(wx, fy + 1, wz).asLong(), v);
                    continue;
                }
                Integer panel = bp.panels.get(c);
                if (panel != null) {
                    panel(level, bp, x, z, wx, wz, fy, h, panel, d);
                    continue;
                }
                Integer knock = bp.knocks.get(c);
                if (knock != null) { // стена уже стоит (column); стучат и откликаются на ПКМ на высоте 1
                    d.inter.put(new BlockPos(wx, fy + 1, wz).asLong(), LabSavedData.KIND_KNOCK * 1000 + knock);
                    continue;
                }
                int[] rack = bp.racks.get(c);
                if (rack != null) {
                    int v = LabSavedData.KIND_RACK * 1000 + rack[0] + inLine(bp, c, x, z, Direction.from2DDataValue(rack[1]));
                    BlockState lower = ModBlocks.SERVER_RACK.get().defaultBlockState()
                            .setValue(ServerRackBlock.FACING, Direction.from2DDataValue(rack[2]));
                    set(level, wx, fy, wz, lower);
                    set(level, wx, fy + 1, wz, lower.setValue(ServerRackBlock.HALF, DoubleBlockHalf.UPPER));
                    d.inter.put(new BlockPos(wx, fy, wz).asLong(), v);
                    d.inter.put(new BlockPos(wx, fy + 1, wz).asLong(), v);
                    continue;
                }
                int[] equip = bp.equips.get(c);
                if (equip != null) { // декор без inter: обе половины сразу, лицом по строке equip
                    BlockState lower = equipBlock(equip[0]).defaultBlockState()
                            .setValue(LabEquipmentBlock.FACING, Direction.from2DDataValue(equip[1]));
                    set(level, wx, fy, wz, lower);
                    set(level, wx, fy + 1, wz, lower.setValue(LabEquipmentBlock.HALF, DoubleBlockHalf.UPPER));
                    continue;
                }
                int[] stack = bp.stacks.get(c);
                if (stack != null) { // пустое место ставит LabFixtures.refresh по зерну мира
                    int place = 1 + inLine(bp, c, x, z, Direction.from2DDataValue(stack[1]));
                    int v = LabSavedData.KIND_STACK * 1000 + stack[0] * 100 + place * 10 + stack[2];
                    set(level, wx, fy, wz, Blocks.BOOKSHELF.defaultBlockState());
                    set(level, wx, fy + 1, wz, Blocks.BOOKSHELF.defaultBlockState());
                    d.inter.put(new BlockPos(wx, fy, wz).asLong(), v);
                    d.inter.put(new BlockPos(wx, fy + 1, wz).asLong(), v);
                    continue;
                }
                String doc = bp.cabinets.get(c);
                if (doc != null) {
                    Integer seal = bp.seals.get(c);
                    BlockPos cp = new BlockPos(wx, fy, wz);
                    LabCabinets.place(level, cp, cabinetFacing(bp, x, z), doc, bp.dream, seal == null);
                    if (seal != null) d.inter.put(cp.asLong(), LabSavedData.KIND_SEAL * 1000 + seal);
                    continue;
                }
                if (furniture(level, bp, c, x, z, wx, wz, fy, h)) continue;
                BlockPos pos = new BlockPos(wx, fy + 1, wz);
                Direction facing = facing(bp, x, z);
                if (c >= '1' && c <= '9') {
                    String[] t = bp.signs.get(c);
                    LabSigns.wall(level, pos, facing, t == null ? new String[0] : t);
                } else if (c == 'E') {
                    put(level, pos, ModBlocks.LIFT_PANEL.get().defaultBlockState()
                            .setValue(LiftPanelBlock.FACING, facing).setValue(LiftPanelBlock.STOP, stop), 2);
                    d.inter.put(pos.asLong(), LabSavedData.KIND_LIFT * 1000 + stop);
                } else {
                    Integer id = bp.locks.get(c);
                    if (id == null) continue;
                    put(level, pos, ModBlocks.KEYPAD.get().defaultBlockState()
                            .setValue(KeypadBlock.FACING, facing).setValue(KeypadBlock.OPEN, d.isLockOpen(id)), 2);
                    d.inter.put(pos.asLong(), LabSavedData.KIND_LOCK * 1000 + id);
                }
            }
        }

        if (surface) {
            facade(level, s, fy, h, minX, minZ, maxX, maxZ, door);
            roofDetails(level, s, fy, h, minX, minZ, maxX, maxZ, lift);
            if (bp.dream) dish(level, s, fy, h, minX, minZ, maxX, maxZ, lift, d);
            else antenna(level, s, fy, h, minX, minZ, maxX, maxZ, lift, d);
        }

        d.stopY[stop] = fy;
        d.stopH[stop] = h;
        d.bbox[stop * 4] = minX;
        d.bbox[stop * 4 + 1] = minZ;
        d.bbox[stop * 4 + 2] = maxX;
        d.bbox[stop * 4 + 3] = maxZ;
        d.builtMask |= 1 << stop;
    }

    /**
     * Решётка-ворота в проёме 3x3 (после passage): пока замок id закрыт, прутья во всём проёме, каждый прут — KIND_GATE
     * (LabLocks.openGate убирает их при открытии). Открытый замок — пустой проём, как '='.
     */
    private static void gate(ServerLevel level, LabBlueprint bp, int x, int z, int wx, int wz, int fy, int id, LabSavedData d) {
        if (d.isLockOpen(id)) return;
        boolean alongX = bp.at(x - 1, z) == '#' && bp.at(x + 1, z) == '#';
        BlockState bar = LabLocks.gateBar(alongX);
        for (int k = -1; k <= 1; k++) {
            for (int y = 0; y < 3; y++) {
                BlockPos p = new BlockPos(wx + (alongX ? k : 0), fy + y, wz + (alongX ? 0 : k));
                put(level, p, bar, 2);
                d.inter.put(p.asLong(), LabSavedData.KIND_GATE * 1000 + id);
            }
        }
    }

    /**
     * Кровать: изголовье в клетке (x, z) у первой стены (порядок С, Ю, В, З; стены нет — запад), изножье — соседняя клетка
     * в комнату (в чертеже там пол). Возвращает {изголовье, изножье}. Общая для строки bed и кровати детской финала.
     */
    private static BlockPos[] bed(ServerLevel level, LabBlueprint bp, int x, int z, int wx, int wz, int fy, Block block) {
        Direction wall = wallOf(bp, x, z);
        Direction toHead = wall != null ? wall : Direction.WEST;
        BlockPos head = new BlockPos(wx, fy, wz);
        BlockPos foot = head.relative(toHead.getOpposite());
        BlockState bed = block.defaultBlockState().setValue(BedBlock.FACING, toHead);
        put(level, foot, bed.setValue(BedBlock.PART, BedPart.FOOT), 2);
        put(level, head, bed.setValue(BedBlock.PART, BedPart.HEAD), 2);
        return new BlockPos[]{head, foot};
    }

    /** Ванильная кровать цвета из строки bed (имя DyeColor); неизвестный цвет — белая. */
    private static Block bedBlock(String color) {
        ResourceLocation id = ResourceLocation.tryBuild("minecraft", color + "_bed");
        Block b = id == null ? null : BuiltInRegistries.BLOCK.get(id);
        return b instanceof BedBlock ? b : Blocks.WHITE_BED;
    }

    /**
     * Часть детской финала (строка finale): кровать, дверь, монитор записи, метка Фрагмента, коврик с рисунком 4. Все блоки
     * части — в inter (KIND_FINALE * 1000 + часть): ПКМ ловит LabHandler, сама кровать и дверь не работают (FinaleHandler).
     */
    private static void finalePart(ServerLevel level, LabBlueprint bp, int x, int z, int wx, int wz, int fy, int part, LabSavedData d) {
        int v = LabSavedData.KIND_FINALE * 1000 + part;
        if (part == FinaleTuning.PART_BED) {
            for (BlockPos p : bed(level, bp, x, z, wx, wz, fy, Blocks.LIGHT_BLUE_BED)) d.inter.put(p.asLong(), v);
        } else if (part == FinaleTuning.PART_DOOR) {
            Direction facing = Direction.EAST; // лицом из комнаты в стену: полотно двери со стороны комнаты
            for (Direction dir : ORDER) {
                char n = bp.at(x + dir.getStepX(), z + dir.getStepZ());
                if (n != '#' && n != ' ' && n != 'w') {
                    facing = dir.getOpposite();
                    break;
                }
            }
            BlockState door = Blocks.BIRCH_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing)
                    .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT).setValue(DoorBlock.OPEN, false);
            BlockPos lower = new BlockPos(wx, fy, wz);
            put(level, lower, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 2);
            put(level, lower.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
            d.inter.put(lower.asLong(), v);
            d.inter.put(lower.above().asLong(), v);
        } else if (part == FinaleTuning.PART_RECORDER) {
            Direction f = deskFacing(bp, x, z);
            set(level, wx, fy, wz, desk(f));
            set(level, wx, fy + 1, wz, ModBlocks.MONITOR.get().defaultBlockState()
                    .setValue(MonitorBlock.FACING, f).setValue(MonitorBlock.LIT, false));
            d.inter.put(new BlockPos(wx, fy, wz).asLong(), v);
            d.inter.put(new BlockPos(wx, fy + 1, wz).asLong(), v);
        } else {
            // Фрагмент — на белом коврике, рисунок 4 — на красном (FinaleHandler.offerDrawing); метка на блок выше пола
            set(level, wx, fy, wz, (part == FinaleTuning.PART_TOY ? Blocks.RED_CARPET : Blocks.WHITE_CARPET).defaultBlockState());
            d.inter.put(new BlockPos(wx, fy + 1, wz).asLong(), v);
        }
    }

    /**
     * Станция из строки station: {key, сторона экрана или null}: стол на полу, ПК на нём; без стороны экран — к стулу, как у стола.
     * Неизвестный key — чертёж не совпадает с ArchiveDefs: пропуск.
     */
    private static void workstation(ServerLevel level, LabBlueprint bp, int x, int z, int wx, int wz, int fy, String[] station, LabSavedData d) {
        ArchiveDefs.Station st = ArchiveDefs.station(station[0]);
        if (st == null) {
            LOGGER.warn("Lab blueprint {}: unknown workstation '{}'", bp.name, station[0]);
            return;
        }
        Direction facing = station[1] == null ? null : Direction.byName(station[1]);
        if (facing == null || facing.getAxis().isVertical()) facing = deskFacing(bp, x, z);
        set(level, wx, fy, wz, desk(facing));
        BlockPos pos = new BlockPos(wx, fy + 1, wz);
        put(level, pos, ModBlocks.WORKSTATION.get().defaultBlockState().setValue(WorkstationBlock.FACING, facing), 2);
        d.inter.put(pos.asLong(), LabSavedData.KIND_TERMINAL * 1000 + st.id());
    }

    /** Щит из строки panel на стене (высота 1) и кабель-канал над ним до потолка: железные прутья, прижатые к стене. */
    private static void panel(ServerLevel level, LabBlueprint bp, int x, int z, int wx, int wz, int fy, int h, int id, LabSavedData d) {
        Direction facing = facing(bp, x, z);
        LabBreakers.place(level, new BlockPos(wx, fy + 1, wz), facing, id, d);
        BlockState conduit = Blocks.IRON_BARS.defaultBlockState().setValue(switch (facing.getOpposite()) {
            case NORTH -> IronBarsBlock.NORTH;
            case SOUTH -> IronBarsBlock.SOUTH;
            case EAST -> IronBarsBlock.EAST;
            default -> IronBarsBlock.WEST;
        }, true);
        for (int y = fy + 2; y < fy + h; y++) set(level, wx, y, wz, conduit);
    }

    /**
     * Щит этажа stop (id LabBreakers.BOARD + stop, клетка чертежа из строки panel с этим id) или null. Старый мир, построенный
     * до щитов этажей: щит ставится сейчас (LabOutage.begin), если клетка от пола до потолка пуста, а за ней стена.
     */
    public static BlockPos board(ServerLevel level, LabSavedData d, int stop) {
        if (!d.isBuilt(stop) || d.dream) return null;
        int id = LabBreakers.BOARD + stop;
        BlockPos have = d.find(LabSavedData.KIND_BREAKER * 1000 + id);
        if (have != null) return have;
        LabBlueprint bp = LabBlueprint.get(LabTuning.blueprintName(false, stop));
        int fy = d.stopY[stop], h = d.stopH[stop];
        for (int z = 0; z < bp.d; z++)
            for (int x = 0; x < bp.w; x++) {
                Integer pid = bp.panels.get(bp.at(x, z));
                if (pid == null || pid != id) continue;
                int wx = d.originX + x - bp.ax, wz = d.originZ + z - bp.az;
                BlockPos pos = new BlockPos(wx, fy + 1, wz);
                BlockPos wall = pos.relative(facing(bp, x, z).getOpposite());
                if (!level.getBlockState(wall).isSolidRender(level, wall)) return null;
                for (int y = fy; y < fy + h; y++) if (!level.getBlockState(new BlockPos(wx, y, wz)).isAir()) return null;
                panel(level, bp, x, z, wx, wz, fy, h, id, d);
                d.setDirty();
                LabBreakers.refresh(level, d);
                return pos;
            }
        return null;
    }

    /** Мебель клетки c (см. легенду). false — символ не мебель. */
    private static boolean furniture(ServerLevel lv, LabBlueprint bp, char c, int x, int z, int wx, int wz, int fy, int h) {
        switch (c) {
            case 'T' -> set(lv, wx, fy, wz, Blocks.DARK_OAK_SLAB.defaultBlockState());
            case 'R' -> {
                set(lv, wx, fy, wz, Blocks.BOOKSHELF.defaultBlockState());
                set(lv, wx, fy + 1, wz, Blocks.BOOKSHELF.defaultBlockState());
            }
            case 'D', 'd', 'V', 'Z' -> {
                Direction f = deskFacing(bp, x, z);
                set(lv, wx, fy, wz, desk(f));
                if (c == 'd') set(lv, wx, fy + 1, wz, Blocks.POTTED_DEAD_BUSH.defaultBlockState());
                if (c == 'V' || c == 'Z') set(lv, wx, fy + 1, wz, ModBlocks.MONITOR.get().defaultBlockState()
                        .setValue(MonitorBlock.FACING, f).setValue(MonitorBlock.LIT, c == 'Z'));
            }
            case 'F', 'p' -> {
                LabCabinets.place(lv, new BlockPos(wx, fy, wz), cabinetFacing(bp, x, z), null, bp.dream, true);
                if (c == 'p') set(lv, wx, fy + 1, wz, Blocks.POTTED_DEAD_BUSH.defaultBlockState());
            }
            case 'h', 'H' -> set(lv, wx, fy, wz, ModBlocks.CHAIR.get().defaultBlockState()
                    .setValue(ChairBlock.FACING, chairFront(bp, x, z, c == 'H')));
            case 'C' -> {
                set(lv, wx, fy, wz, Blocks.SMOOTH_QUARTZ.defaultBlockState());
                set(lv, wx, fy + 1, wz, Blocks.TINTED_GLASS.defaultBlockState());
            }
            case 'v' -> {
                Direction wall = wallOf(bp, x, z);
                if (wall != null) set(lv, wx, fy + h - LabTuning.VENT_BELOW_ROOF, wz, trapdoor(wall.getOpposite(), true));
            }
            case 'w' -> {
                // окно: стекло уже вставлено в column()
            }
            case 'r' -> set(lv, wx, fy, wz, Blocks.WHITE_CARPET.defaultBlockState());
            case 'u' -> set(lv, wx, fy, wz, Blocks.RED_CARPET.defaultBlockState());
            case '+' -> set(lv, wx, fy, wz, Blocks.CAULDRON.defaultBlockState());
            case '~' -> {
                BlockState pane = screen(bp, x, z);
                set(lv, wx, fy, wz, pane);
                set(lv, wx, fy + 1, wz, pane);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Ширма: панель сцеплена с соседними ширмами и стенами; одинокая — поперёк оси восток-запад. */
    private static BlockState screen(LabBlueprint bp, int x, int z) {
        BlockState pane = Blocks.WHITE_STAINED_GLASS_PANE.defaultBlockState();
        boolean any = false;
        for (Direction dir : ORDER) {
            char n = bp.at(x + dir.getStepX(), z + dir.getStepZ());
            if (n != '~' && n != 'w' && !solid(bp, n)) continue;
            pane = pane.setValue(switch (dir) {
                case NORTH -> IronBarsBlock.NORTH;
                case SOUTH -> IronBarsBlock.SOUTH;
                case EAST -> IronBarsBlock.EAST;
                default -> IronBarsBlock.WEST;
            }, true);
            any = true;
        }
        return any ? pane : pane.setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true);
    }

    /** Сколько клеток того же символа подряд стоит перед (x, z) по направлению run: номер в линии с 0. */
    private static int inLine(LabBlueprint bp, char c, int x, int z, Direction run) {
        int n = 0;
        for (int px = x - run.getStepX(), pz = z - run.getStepZ(); bp.at(px, pz) == c; px -= run.getStepX(), pz -= run.getStepZ()) n++;
        return n;
    }

    /** Клетка-стена: '#' или заложенная дверь (строка knock). */
    private static boolean solid(LabBlueprint bp, char c) {
        return c == '#' || bp.knocks.containsKey(c);
    }

    /** Высокая мебель, к торцу которой можно прикрепить табличку: стеллаж, стойка, ряд хранилища, оборудование. */
    private static boolean tall(LabBlueprint bp, char c) {
        return c == 'R' || bp.racks.containsKey(c) || bp.stacks.containsKey(c) || bp.equips.containsKey(c);
    }

    /** Блок оборудования по индексу вида LabBlueprint.EQUIP. */
    private static Block equipBlock(int kind) {
        return switch (kind) {
            case 0 -> ModBlocks.CRAC_UNIT.get();
            case 1 -> ModBlocks.UPS_CABINET.get();
            default -> ModBlocks.BATTERY_CABINET.get();
        };
    }

    private static BlockState desk(Direction facing) {
        return ModBlocks.DESK.get().defaultBlockState().setValue(FurnitureBlock.FACING, facing);
    }

    /** Клетка, у которой можно сидеть: стол, низкий столик или станция. */
    private static boolean deskLike(LabBlueprint bp, char c) {
        return c == 'D' || c == 'd' || c == 'V' || c == 'Z' || c == 'T' || bp.stations.containsKey(c);
    }

    /** Стол с рабочей вещью: монитор или станция. Стул выбирает такой стол раньше пустого. */
    private static boolean workItem(LabBlueprint bp, char c) {
        return c == 'V' || c == 'Z' || bp.stations.containsKey(c);
    }

    /** Клетка, где можно встать перед шкафом: пол, лампа, решётка под потолком, табличка на стене, ковёр. */
    private static boolean walkable(char c) {
        return c == '.' || c == '*' || c == 'v' || c == 'r' || c == 'u' || (c >= '1' && c <= '9');
    }

    /** Сторона от стула к его столу: сначала монитор или станция, потом любой стол (порядок С, Ю, В, З); null — стола рядом нет. */
    private static Direction deskOf(LabBlueprint bp, int x, int z) {
        for (Direction dir : ORDER)
            if (workItem(bp, bp.at(x + dir.getStepX(), z + dir.getStepZ()))) return dir;
        for (Direction dir : ORDER)
            if (deskLike(bp, bp.at(x + dir.getStepX(), z + dir.getStepZ()))) return dir;
        return null;
    }

    /** В соседней клетке side стоит стул, который считает стол (x, z) своим. */
    private static boolean seatedFrom(LabBlueprint bp, int x, int z, Direction side) {
        int cx = x + side.getStepX(), cz = z + side.getStepZ();
        char n = bp.at(cx, cz);
        return (n == 'h' || n == 'H') && deskOf(bp, cx, cz) == side.getOpposite();
    }

    /**
     * Куда смотрит стол (ящики; монитор и экран станции — так же):
     * к своему стулу; иначе как ряд столов, в котором стоит; иначе от стены; иначе к ближайшему стулу; иначе на юг.
     */
    private static Direction deskFacing(LabBlueprint bp, int x, int z) {
        for (Direction dir : ORDER)
            if (seatedFrom(bp, x, z, dir)) return dir;
        Direction run = runFacing(bp, x, z, Direction.EAST, Direction.NORTH, Direction.SOUTH);
        if (run == null) run = runFacing(bp, x, z, Direction.SOUTH, Direction.EAST, Direction.WEST);
        if (run != null) return run;
        Direction wall = wallOf(bp, x, z);
        if (wall != null) return wall.getOpposite();
        int best = LabTuning.DESK_CHAIR_REACH + 1, bx = 0, bz = 0;
        for (int cz = 0; cz < bp.d; cz++) {
            for (int cx = 0; cx < bp.w; cx++) {
                char c = bp.at(cx, cz);
                int dist = Math.abs(cx - x) + Math.abs(cz - z);
                if ((c == 'h' || c == 'H') && dist < best) {
                    best = dist;
                    bx = cx - x;
                    bz = cz - z;
                }
            }
        }
        if (best > LabTuning.DESK_CHAIR_REACH) return Direction.SOUTH;
        if (Math.abs(bx) >= Math.abs(bz)) return bx > 0 ? Direction.EAST : Direction.WEST;
        return bz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * Ряд столов через (x, z) вдоль along: сторона a или b, если с этой стороны к какому-нибудь столу ряда приставлен его стул
     * (ряд стоит лицом к сидящим, даже где стула нет). null — в ряду никто не сидит сбоку.
     */
    private static Direction runFacing(LabBlueprint bp, int x, int z, Direction along, Direction a, Direction b) {
        int sx = x, sz = z;
        while (deskLike(bp, bp.at(sx - along.getStepX(), sz - along.getStepZ()))) {
            sx -= along.getStepX();
            sz -= along.getStepZ();
        }
        for (int cx = sx, cz = sz; deskLike(bp, bp.at(cx, cz)); cx += along.getStepX(), cz += along.getStepZ()) {
            if (seatedFrom(bp, cx, cz, a)) return a;
            if (seatedFrom(bp, cx, cz, b)) return b;
        }
        return null;
    }

    /**
     * Лицевая сторона шкафа: дверцы на свободную клетку пола. Из свободных сторон лучше та, где шкаф спиной к стене (+2)
     * и перед дверцами два шага места (+1); равные — по порядку С, Ю, В, З. Свободных нет — от стены, как табличка.
     */
    private static Direction cabinetFacing(LabBlueprint bp, int x, int z) {
        Direction best = null;
        int bestScore = -1;
        for (Direction dir : ORDER) {
            int fx = x + dir.getStepX(), fz = z + dir.getStepZ();
            if (!walkable(bp.at(fx, fz))) continue;
            int score = (bp.at(x - dir.getStepX(), z - dir.getStepZ()) == '#' ? 2 : 0)
                    + (walkable(bp.at(fx + dir.getStepX(), fz + dir.getStepZ())) ? 1 : 0);
            if (score > bestScore) {
                best = dir;
                bestScore = score;
            }
        }
        return best != null ? best : facing(bp, x, z);
    }

    /**
     * Куда смотрит сиденье стула (ChairBlock.FACING, спинка с обратной стороны).
     * К своему столу; нет стола — спинкой к стене; нет и стены — к кабине лифта. reversed (H) — наоборот.
     */
    private static Direction chairFront(LabBlueprint bp, int x, int z, boolean reversed) {
        Direction front = deskOf(bp, x, z);
        if (front == null) {
            Direction wall = wallOf(bp, x, z);
            if (wall != null) {
                front = wall.getOpposite();
            } else {
                int dx = bp.ax - x, dz = bp.az - z;
                front = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
            }
        }
        return reversed ? front.getOpposite() : front;
    }

    /** Сторона первой найденной стены (порядок С, Ю, В, З) или null. */
    private static Direction wallOf(LabBlueprint bp, int x, int z) {
        for (Direction dir : ORDER)
            if (solid(bp, bp.at(x + dir.getStepX(), z + dir.getStepZ()))) return dir;
        return null;
    }

    /** Лицевая сторона таблички и щита: от первой найденной стены в комнату. */
    private static Direction facing(LabBlueprint bp, int x, int z) {
        Direction wall = wallOf(bp, x, z);
        if (wall == null)
            for (Direction dir : ORDER)
                if (tall(bp, bp.at(x + dir.getStepX(), z + dir.getStepZ()))) {
                    wall = dir;
                    break;
                }
        return wall == null ? Direction.SOUTH : wall.getOpposite();
    }

    /**
     * Снос без обновления формы соседей: иначе таблички на стенах, снесённых раньше них,
     * отваливаются и выпадают предметами (каждый rebuild сыпал таблички на пол).
     */
    private static final int CLEAR_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static void set(ServerLevel level, int x, int y, int z, BlockState st) {
        put(level, new BlockPos(x, y, z), st, 2);
    }

    /**
     * Замена блока в стройке, во дворе и при сносе. Природный тайник с ещё не выпавшим лутом (сундук подземелья,
     * бочка деревни) убирается вместе с блок-сущностью: иначе замена высыпает лут на пол (детская во сне поверх подземелья).
     * Контейнер, в котором уже лежат вещи (лут выпал или их положил игрок), не трогаем: вещи падают на пол, как прежде.
     */
    private static void put(ServerLevel level, BlockPos pos, BlockState st, int flags) {
        BlockState old = level.getBlockState(pos);
        if (old.hasBlockEntity() && !old.is(st.getBlock())
                && level.getBlockEntity(pos) instanceof RandomizableContainer c && c.getLootTable() != null)
            level.removeBlockEntity(pos);
        level.setBlock(pos, st, flags);
    }

    private static void column(ServerLevel lv, Style s, char c, int x, int z, int fy, int h, boolean gate, boolean wallDoor,
                               boolean solid) {
        if (c == '#' || c == 'w' || wallDoor || solid) {
            // Стена: тёмный цоколь (первый ряд), поле из камня с редкими трещинами; карниз и пилястры — через facade().
            for (int y = fy - 1; y < fy + h; y++)
                set(lv, x, y, z, y == fy ? s.trim : wallAt(s, x, y, z));
            set(lv, x, fy + h, z, s.roof);
            // окно детской: тёмное стекло на высоте 1-2; дверь детской: проём 2 высотой (дверь ставит finalePart)
            if (c == 'w') for (int y = 1; y <= 2; y++) set(lv, x, fy + y, z, Blocks.TINTED_GLASS.defaultBlockState());
            if (wallDoor) for (int y = 0; y <= 1; y++) set(lv, x, fy + y, z, AIR);
            return;
        }
        boolean door = c == '=' || c == 'l' || gate;
        boolean cabin = c == 'L' || c == '@' || c == 'Y' || c == 'E';
        // Пол: клетка-шахматка из двух оттенков камня; кабина и проёмы — тёмная плита.
        set(lv, x, fy - 1, z, cabin || door ? s.floor2 : ((x ^ z) & 1) == 0 ? s.floor : s.floor2);
        int open = door ? 3 : h;
        for (int y = 0; y < h; y++) set(lv, x, fy + y, z, y >= open ? s.wall : AIR);
        set(lv, x, fy + h, z, s.roof);
        if (c == '*' || c == 'Y') set(lv, x, fy + h - 1, z, lamp(Direction.DOWN));
    }

    /** Мировые границы (minX, minZ, maxX, maxZ) непустых клеток чертежа. */
    private static int[] bounds(LabBlueprint bp, int ox, int oz) {
        int x1 = Integer.MAX_VALUE, z1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, z2 = Integer.MIN_VALUE;
        for (int z = 0; z < bp.d; z++)
            for (int x = 0; x < bp.w; x++) {
                if (bp.at(x, z) == ' ') continue;
                int wx = ox + x - bp.ax, wz = oz + z - bp.az;
                x1 = Math.min(x1, wx);
                x2 = Math.max(x2, wx);
                z1 = Math.min(z1, wz);
                z2 = Math.max(z2, wz);
            }
        return new int[]{x1, z1, x2, z2};
    }

    /** Мировые координаты внешнего входа ('=') или null, если его нет. */
    private static int[] entrance(LabBlueprint bp, int ox, int oz) {
        for (int z = 0; z < bp.d; z++)
            for (int x = 0; x < bp.w; x++)
                if (bp.at(x, z) == '=') return new int[]{ox + x - bp.ax, oz + z - bp.az};
        return null;
    }

    /** Уровень пола павильона: верхний процентиль высот земли по площадке (здание + двор), не ниже уровня моря + 1. */
    public static int padLevel(ServerLevel level, LabBlueprint bp, int ox, int oz) {
        int[] b = bounds(bp, ox, oz);
        int y = LabTuning.YARD;
        int n = 0;
        int[] hs = new int[((b[2] - b[0] + 2 * y) / 2 + 2) * ((b[3] - b[1] + 2 * y) / 2 + 2)];
        for (int x = b[0] - y; x <= b[2] + y; x += 2)
            for (int z = b[1] - y; z <= b[3] + y; z += 2)
                hs[n++] = groundTop(level, x, z);
        Arrays.sort(hs, 0, n);
        int idx = Math.min(n - 1, (int) (n * LabTuning.PAD_PERCENTILE));
        return Math.max(hs[idx], level.getSeaLevel() + 1);
    }

    /** Высота настоящей земли: стволы, листва, растения и снег землёй не считаются. Возвращает первый свободный блок над землёй. */
    private static int groundTop(ServerLevel lv, int x, int z) {
        int y = lv.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        while (y > lv.getMinBuildHeight()) {
            BlockState st = lv.getBlockState(new BlockPos(x, y - 1, z));
            boolean soft = st.isAir() || st.is(BlockTags.LOGS) || st.is(BlockTags.LEAVES)
                    || (st.canBeReplaced() && st.getFluidState().isEmpty()); // [проверить: canBeReplaced()]
            if (!soft) break;
            y--;
        }
        return y;
    }

    /**
     * Минимальная подготовка площадки: ровный мощёный двор YARD блоков вокруг здания, узкая
     * подпорная стенка в один блок по его краю и дорожка от входа. Широкого откоса нет — за стенкой
     * рельеф остаётся таким, каким его создал генератор, поэтому деревни и склоны не разваливаются.
     * Деревья, листва и вода под площадкой заменяются.
     */
    private static void terraform(ServerLevel lv, Style s, int fy, int x1, int z1, int x2, int z2, int[] door) {
        int yard = LabTuning.YARD;
        int px1 = x1 - yard, px2 = x2 + yard, pz1 = z1 - yard, pz2 = z2 + yard;
        for (int x = px1; x <= px2; x++) {
            for (int z = pz1; z <= pz2; z++) {
                int top = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                int topAll = lv.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                // всё выше пола двора срезано (деревья, листва, рельеф)
                for (int y = fy; y <= Math.max(topAll, fy); y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!lv.getBlockState(p).isAir()) put(lv, p, AIR, 2);
                }
                set(lv, x, fy - 1, z, s.floor2); // плита двора
                // подсыпка под плитой до первого твёрдого блока
                for (int y = fy - 2; y >= fy - 1 - LabTuning.FILL_DEPTH; y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState st = lv.getBlockState(p);
                    if (st.isAir() || st.canBeReplaced() || !st.getFluidState().isEmpty()
                            || st.is(BlockTags.LOGS) || st.is(BlockTags.LEAVES)) put(lv, p, s.wall, 2);
                    else break;
                }
            }
        }
        // направление наружу от входа: проём в подпорной стенке и дорожка к нему
        int outX = 0, outZ = 0;
        if (door != null) {
            if (door[1] == z2) outZ = 1;
            else if (door[1] == z1) outZ = -1;
            else if (door[0] == x2) outX = 1;
            else if (door[0] == x1) outX = -1;
        }
        // подпорная стенка по периметру двора: держит и срез, и подсыпку, но не трогает рельеф дальше
        for (int x = px1 - 1; x <= px2 + 1; x++) {
            for (int z = pz1 - 1; z <= pz2 + 1; z++) {
                boolean ring = x == px1 - 1 || x == px2 + 1 || z == pz1 - 1 || z == pz2 + 1;
                if (!ring) continue;
                boolean gap = door != null && (outX > 0 ? x == px2 + 1 && Math.abs(z - door[1]) <= 1
                        : outX < 0 ? x == px1 - 1 && Math.abs(z - door[1]) <= 1
                        : outZ > 0 ? z == pz2 + 1 && Math.abs(x - door[0]) <= 1
                        : outZ < 0 && z == pz1 - 1 && Math.abs(x - door[0]) <= 1);
                if (gap) continue; // место под входную дорожку
                int top = groundTop(lv, x, z);
                int topAll = lv.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                int low = Math.min(fy - 1, top - 1);
                int wallTop = Math.min(Math.max(fy - 1, top - 1), fy + LabTuning.WALL_MAX_ABOVE);
                for (int y = low; y <= wallTop; y++) set(lv, x, y, z, s.trim);
                for (int y = wallTop + 1; y <= topAll; y++) { // срезаем деревья над стенкой
                    BlockPos p = new BlockPos(x, y, z);
                    if (!lv.getBlockState(p).isAir()) put(lv, p, AIR, 2);
                }
            }
        }
        if (door == null || outX == 0 && outZ == 0) return;
        // дорожка от входа: 3 блока в ширину, по 1 блоку подъёма/спуска за шаг, потом упирается в рельеф
        int first = yard + 1; // первая клетка за двором — та самая прореха в подпорной стенке
        for (int k = first; k < first + LabTuning.ENTRY_PATH; k++) {
            for (int side = -1; side <= 1; side++) {
                int x = door[0] + outX * k + (outX == 0 ? side : 0);
                int z = door[1] + outZ * k + (outZ == 0 ? side : 0);
                int top = groundTop(lv, x, z);
                int topAll = lv.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                int natural = top - 1;
                int step = k - first;
                int target = Math.max(fy - 1 - step, Math.min(fy - 1 + step, natural));
                for (int y = target + 1; y <= Math.max(topAll, target); y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!lv.getBlockState(p).isAir()) put(lv, p, AIR, 2);
                }
                set(lv, x, target, z, s.floor2);
                for (int y = target - 1; y >= target - LabTuning.FILL_DEPTH; y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState st = lv.getBlockState(p);
                    if (st.isAir() || st.canBeReplaced() || !st.getFluidState().isEmpty()
                            || st.is(BlockTags.LOGS) || st.is(BlockTags.LEAVES)) put(lv, p, s.wall, 2);
                    else break;
                }
            }
        }
    }

    /** Два фонаря по сторонам наружного проёма: бетонный столб и лампа сверху. */
    private static void lampPosts(ServerLevel lv, LabBlueprint bp, Style s, int x, int z, int wx, int wz, int fy) {
        for (Direction out : ORDER) {
            if (bp.at(x + out.getStepX(), z + out.getStepZ()) != ' ') continue;
            Direction side = out.getClockWise();
            for (int k = -2; k <= 2; k += 4) {
                int px = wx + out.getStepX() * 2 + side.getStepX() * k;
                int pz = wz + out.getStepZ() * 2 + side.getStepZ() * k;
                set(lv, px, fy, pz, s.trim); // серое основание фонаря
                for (int y = 1; y < 3; y++) set(lv, px, fy + y, pz, s.wall);
                set(lv, px, fy + 3, pz, lamp(Direction.UP));
            }
            return;
        }
    }

    /**
     * Стирает постройку остановки (кроме фундамента): воздух от пола до потолка в её прямоугольнике. Подземный этаж —
     * только свои колонны (builtColumn) и без слоя над потолком: раньше пересборка вычищала весь прямоугольник, и вокруг
     * непрямоугольного этажа оставались пустоты за стенами и щель над потолком. Порода вокруг теперь не трогается.
     */
    public static void clear(ServerLevel level, LabSavedData d, int stop) {
        if (!d.isBuilt(stop)) return;
        if (stop == 0) LabAntenna.removeDisplays(level, d); // тарелка ANT-1 — сущности, блоками не стирается
        int fy = d.stopY[stop], h = d.stopH[stop];
        int x1 = d.bbox[stop * 4], z1 = d.bbox[stop * 4 + 1], x2 = d.bbox[stop * 4 + 2], z2 = d.bbox[stop * 4 + 3];
        if (stop == 0) { // фонари у входа стоят за границей чертежа — стираем вместе с ними
            x1 -= 2;
            z1 -= 2;
            x2 += 2;
            z2 += 2;
        }
        // Пол поверхности (fy-1) оставляем: площадка не должна проседать при пересборке.
        int y0 = stop == 0 ? fy : fy - 1;
        int top = stop == 0 ? fy + h + LabTuning.ROOF_CLEAR : fy + h; // поверхность: ещё парапет, будка и тарелка
        Style s = style(d.dream);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                if (stop != 0 && !builtColumn(level, s, x, z, fy, h)) continue;
                for (int y = y0; y <= top; y++) put(level, new BlockPos(x, y, z), AIR, CLEAR_FLAGS);
            }
    }

    /**
     * Колонна подземного этажа построена лабораторией: потолок из её плитки или её пол (column()). В естественной породе
     * на этих высотах таких блоков нет; колонна с выломанным потолком узнаётся по полу.
     */
    private static boolean builtColumn(ServerLevel level, Style s, int x, int z, int fy, int h) {
        BlockState roof = level.getBlockState(new BlockPos(x, fy + h, z));
        BlockState floor = level.getBlockState(new BlockPos(x, fy - 1, z));
        return roof.is(s.roof.getBlock()) || floor.is(s.floor.getBlock()) || floor.is(s.floor2.getBlock());
    }
}
