package com.hypersomnia.nightmare;

import com.hypersomnia.ModBlocks;
import com.hypersomnia.ModItems;
import com.hypersomnia.lab.LabSigns;
import com.hypersomnia.lab.UprightCapsuleBlock;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.slf4j.Logger;

/**
 * Ряд капсул [новое, переработка]: приглушённый коридор ночного обхода. Капсулы закрыты тёмным стеклом,
 * шприцы лежат на тумбах у стены (не на проходе), три контрольные площадки, капсула 47 открыта и освещена.
 * Осколков нет; ряд один на всех игроков, личное состояние ведёт сервер в RoundsHandler.
 */
public final class RoundsBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FLAGS = NightmareLayout.FLAGS;
    private static final String[] MARKS = {
            "Cognitive collapse", "Sleep did not resume", "Subject unresponsive",
            "Terminated during session", "Dissociation, discharged"
    };
    private RoundsBuilder() {}
    private static int x() { return NightmareDefs.ROUNDS.origin().getX(); }
    private static int z() { return NightmareDefs.ROUNDS.origin().getZ(); }
    private static int y() { return NightmareTuning.ROUNDS_Y; }
    private static int length() { return NightmareTuning.ROUNDS_CAPSULES * NightmareTuning.ROUNDS_CAPSULE_STRIDE + 3; }
    private static int capsuleX(int index) { return x() + 2 + index * NightmareTuning.ROUNDS_CAPSULE_STRIDE; }
    public static BlockPos spawn() { return new BlockPos(x(), y() + 1, z() + 1); }
    public static BlockPos exitFoot() { return new BlockPos(capsuleX(NightmareTuning.ROUNDS_CAPSULES - 1), y() + 1, z() + 4); }
    public static boolean isExit(BlockPos p) { return p.equals(exitFoot()) || p.equals(exitFoot().east()); }
    /** Индекс капсулы 0..46 по X игрока; вне ряда зажимается в границы. */
    public static int indexAt(double px) {
        int i = (int) Math.floor((px - (x() + 2)) / NightmareTuning.ROUNDS_CAPSULE_STRIDE);
        return Math.max(0, Math.min(NightmareTuning.ROUNDS_CAPSULES - 1, i));
    }
    public static BlockPos aisle(int index) { return new BlockPos(capsuleX(index), y() + 1, z() + 2); }
    /** Площадка контрольной точки k: перед капсулой, 3 по X и z 1..3. */
    public static boolean inCheckZone(int k, double px, double pz) {
        int cx = capsuleX(NightmareTuning.ROUNDS_CHECKS[k]);
        return px >= cx && px <= cx + 3 && pz >= z() + 1 && pz <= z() + 4;
    }
    /** X, дальше которого без отметки не пускает. */
    public static double checkGateX(int k) { return capsuleX(NightmareTuning.ROUNDS_CHECKS[k]) + 3.6; }
    public static boolean containsBuilt(ServerLevel level, BlockPos p) {
        return NightmareSavedData.get(level).isBuilt(NightmareDefs.ROUNDS)
                && p.getX() >= x() - 1 && p.getX() <= x() + length()
                && p.getZ() >= z() - 1 && p.getZ() <= z() + 7
                && p.getY() >= y() && p.getY() <= y() + 4;
    }
    private static void put(ServerLevel level, int bx, int dy, int bz, BlockState state) {
        if (!level.setBlock(new BlockPos(bx, y() + dy, bz), state, FLAGS))
            throw new IllegalStateException("Could not place rounds block");
    }
    private static void panel(ServerLevel level, int bx, int bz, int lvl) {
        put(level, bx, 3, bz, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, lvl));
        put(level, bx, 4, bz, Blocks.WHITE_STAINED_GLASS.defaultBlockState());
    }
    /**
     * [новое] Ниша капсулы i (3x3 клетки, z+4..z+6) со стоячей капсулой: колонка в центре фасада (cx+1, z+4) дверцей в проход,
     * остальное — глухой белый бетон. Выписанный субъект (последняя метка MARKS): дверца снята, ячейка пуста.
     * Табличка — на левой боковине ниши, чтобы не закрывать стекло.
     */
    private static void upright(ServerLevel level, int cx, int i) {
        boolean open = i % MARKS.length == MARKS.length - 1;
        BlockState[] pod = UprightCapsuleBlock.column(Direction.NORTH, open);
        for (int dx = 0; dx < 3; dx++) for (int dz = 4; dz <= 6; dz++) for (int dy = 1; dy <= 3; dy++) {
            boolean podCell = dx == 1 && dz == 4;
            put(level, cx + dx, dy, z() + dz, podCell ? pod[dy - 1] : Blocks.WHITE_CONCRETE.defaultBlockState());
        }
        LabSigns.wall(level, new BlockPos(cx, y() + 2, z() + 3), Direction.NORTH,
                String.format(java.util.Locale.ROOT, "CAPSULE %02d", i + 1), MARKS[i % MARKS.length]);
    }

    public static boolean ensureBuilt(ServerLevel level) {
        NightmareSavedData data = NightmareSavedData.get(level);
        if (data.isBuilt(NightmareDefs.ROUNDS) && data.layout(NightmareDefs.ROUNDS) != NightmareTuning.ROUNDS_LAYOUT) {
            if (NightmareLayout.anyoneInside(level.getServer(), NightmareDefs.ROUNDS.id())) return true;
            NightmareLayout.clear(level, x() - 1, y(), z() - 1, x() + length(), y() + 4, z() + 7);
            data.setBuilt(NightmareDefs.ROUNDS, false);
            data.setRoundsBuildFailed(false);
        }
        if (data.isBuilt(NightmareDefs.ROUNDS)) return true;
        if (data.roundsBuildFailed()) return false;
        int length = length();
        if (y() < level.getMinBuildHeight() || y() + 4 >= level.getMaxBuildHeight()) return false;
        for (int dx = -1; dx <= length; dx++) for (int dz = -1; dz <= 7; dz++) for (int dy = 0; dy <= 4; dy++) {
            BlockPos pos = new BlockPos(x() + dx, y() + dy, z() + dz);
            if (!level.getWorldBorder().isWithinBounds(pos) || !level.getBlockState(pos).isAir()) {
                data.setRoundsBuildFailed(true);
                LOGGER.warn("Rounds location is occupied or outside the world border; no blocks were changed");
                return false;
            }
        }
        try {
            // Оболочка: плитка пола, ветровая дорожка у капсул, тёмный цоколь у южной стены, торцы глухие.
            for (int dx = -1; dx <= length; dx++) for (int dz = -1; dz <= 7; dz++) for (int dy = 0; dy <= 4; dy++) {
                BlockState state = null;
                if (dy == 0) state = dz == 3 ? Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState()
                        : (((dx + dz) & 1) == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState());
                else if (dy == 4) state = Blocks.SMOOTH_STONE.defaultBlockState();
                else if (dx == -1 || dx == length || dz == -1 || dz == 7)
                    state = dy == 1 && dz == -1 ? Blocks.GRAY_CONCRETE.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState();
                if (state != null) put(level, x() + dx, dy, z() + dz, state);
            }
            for (int i = 0; i < NightmareTuning.ROUNDS_CAPSULES; i++) {
                int cx = capsuleX(i);
                boolean last = i == NightmareTuning.ROUNDS_CAPSULES - 1;
                // Опорные столбы между капсулами.
                for (int dy = 1; dy <= 3; dy++) for (int dz = 4; dz <= 6; dz++)
                    put(level, cx + 3, dy, z() + dz, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
                if (!last && NightmareTuning.ROUNDS_UPRIGHT) {
                    upright(level, cx, i);
                } else if (!last) {
                    // Закрытая капсула: подиум, матрас (цвет меняется: «потревоженные»), тёмное стекло спереди.
                    BlockState sheet = switch ((i * 7) % 5) {
                        case 2 -> Blocks.LIGHT_GRAY_CARPET.defaultBlockState();
                        case 4 -> Blocks.GRAY_CARPET.defaultBlockState();
                        default -> Blocks.WHITE_CARPET.defaultBlockState();
                    };
                    for (int dx = 0; dx < 3; dx++) {
                        for (int dz = 4; dz <= 6; dz++) put(level, cx + dx, 1, z() + dz, Blocks.QUARTZ_BLOCK.defaultBlockState());
                        for (int dz = 5; dz <= 6; dz++) put(level, cx + dx, 2, z() + dz, sheet);
                        for (int dy = 2; dy <= 3; dy++) put(level, cx + dx, dy, z() + 4, Blocks.TINTED_GLASS.defaultBlockState());
                    }
                    LabSigns.wall(level, new BlockPos(cx + 1, y() + 2, z() + 3), Direction.NORTH,
                            String.format(java.util.Locale.ROOT, "CAPSULE %02d", i + 1), MARKS[i % MARKS.length]);
                } else {
                    // Капсула 47: открыта, светлее остальных.
                    for (int dx = 0; dx < 3; dx++) for (int dz = 4; dz <= 6; dz++)
                        put(level, cx + dx, 1, z() + dz, Blocks.QUARTZ_BLOCK.defaultBlockState());
                    BlockState bed = Blocks.WHITE_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.EAST);
                    put(level, exitFoot().getX(), 1, exitFoot().getZ(), bed.setValue(net.minecraft.world.level.block.BedBlock.PART, BedPart.FOOT));
                    put(level, exitFoot().east().getX(), 1, exitFoot().getZ(), bed.setValue(net.minecraft.world.level.block.BedBlock.PART, BedPart.HEAD));
                    LabSigns.wall(level, new BlockPos(cx + 1, y() + 2, z() + 3), Direction.NORTH, "CAPSULE 47");
                    panel(level, cx + 1, z() + 2, 12);
                }
                // Приглушённые панели света через капсулу.
                if (i % 2 == 0 && !last) panel(level, cx + 1, z() + 2, 6);
                // Контрольная площадка: белая плитка на полу и табличка на южной стене.
                for (int k = 0; k < NightmareTuning.ROUNDS_CHECKS.length; k++) {
                    if (NightmareTuning.ROUNDS_CHECKS[k] != i) continue;
                    for (int dx = 0; dx < 4; dx++) for (int dz = 1; dz <= 3; dz++)
                        put(level, cx + dx, 0, z() + dz, Blocks.WHITE_CONCRETE.defaultBlockState());
                    LabSigns.wall(level, new BlockPos(cx + 1, y() + 2, z()), Direction.SOUTH, "ROUND CHECK", "HOLD POSITION");
                }
                // Тумба со шприцем у южной стены: не на проходе, предмет нельзя взять.
                if (!last && i % NightmareTuning.ROUNDS_SYRINGE_EVERY == 0) {
                    put(level, cx + 2, 1, z(), ModBlocks.DESK.get().defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
                    ItemEntity syringe = new ItemEntity(level, cx + 2.5, y() + 2.1, z() + 0.5,
                            new ItemStack(ModItems.ADRENALINE_SYRINGE.get()));
                    syringe.addTag("hypersomnia_rounds_syringe");
                    syringe.setUnlimitedLifetime();
                    syringe.setPickUpDelay(NightmareTuning.ROUNDS_SYRINGE_PICKUP_DELAY);
                    syringe.setInvulnerable(true);
                    syringe.setNoGravity(true);
                    syringe.setDeltaMovement(0, 0, 0);
                    if (!level.addFreshEntity(syringe)) throw new IllegalStateException("Could not place rounds syringe");
                }
            }
            data.setBuilt(NightmareDefs.ROUNDS, true);
            data.setLayout(NightmareDefs.ROUNDS, NightmareTuning.ROUNDS_LAYOUT);
            return true;
        } catch (RuntimeException error) {
            data.setRoundsBuildFailed(true);
            LOGGER.error("Rounds construction failed; inspect the area before repair", error);
            return false;
        }
    }
}
