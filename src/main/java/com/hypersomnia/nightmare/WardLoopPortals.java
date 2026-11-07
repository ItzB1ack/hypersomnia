package com.hypersomnia.nightmare;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.portal.Portal;

import java.util.List;
import java.util.UUID;

/** Пара обычных порталов только в слоте коридора; не меняет глобальные порталы измерения. */
public final class WardLoopPortals {
    private static final String FORWARD_TAG = "hypersomnia:ward_loop_forward";
    private static final String REVERSE_TAG = "hypersomnia:ward_loop_reverse";
    private static final String LOOP_COMMAND = "hypersomnia_nightmare _ward_loop";
    private WardLoopPortals() {}

    private static Vec3 center(BlockPos o, int x) {
        return new Vec3(o.getX() + x, o.getY() + 1 + NightmareTuning.CORRIDOR_HEIGHT / 2.0,
                o.getZ() + NightmareTuning.CORRIDOR_WIDTH / 2.0);
    }

    public enum Result { READY, LOADING, INCOMPLETE_IDS, MISSING_SAVED_PORTAL, INVALID_SAVED_PORTAL,
        ORPHAN_PORTAL, INVALID_NEW_PORTAL, SPAWN_FAILED }

    public static boolean ensure(ServerLevel level) { return ensureDetailed(level) == Result.READY; }

    /** Различает ожидание загрузки от ошибок сохранённой пары; не удаляет чужие сущности. */
    public static Result ensureDetailed(ServerLevel level) {
        BlockPos origin = NightmareDefs.WARD_CORRIDOR.origin();
        Vec3 a = center(origin, NightmareTuning.WARD_LOOP_FORWARD_X);
        Vec3 b = center(origin, NightmareTuning.WARD_LOOP_BACK_X);
        ChunkPos ca = new ChunkPos(BlockPos.containing(a));
        ChunkPos cb = new ChunkPos(BlockPos.containing(b));
        // UNKNOWN ticket от getChunk живёт только один тик. PORTAL удерживает чанки
        // 300 тиков, чтобы асинхронно загрузились их сохранённые сущности.
        level.getChunkSource().addRegionTicket(TicketType.PORTAL, ca, 2, BlockPos.containing(a));
        level.getChunkSource().addRegionTicket(TicketType.PORTAL, cb, 2, BlockPos.containing(b));
        level.getChunk(ca.x, ca.z);
        level.getChunk(cb.x, cb.z);
        // getChunk загружает блоки, но чтение сохранённых сущностей может закончиться позже.
        // Не создавать новую пару, пока нельзя надёжно обнаружить старую.
        if (!level.areEntitiesLoaded(ca.toLong()) || !level.areEntitiesLoaded(cb.toLong())) return Result.LOADING;

        NightmareSavedData data = NightmareSavedData.get(level);
        UUID fId = data.wardForwardPortal(), rId = data.wardReversePortal();
        if (fId != null || rId != null) {
            if (fId == null || rId == null) return Result.INCOMPLETE_IDS;
            Entity f = level.getEntity(fId), r = level.getEntity(rId);
            if (f == null || r == null) return Result.MISSING_SAVED_PORTAL;
            if (!valid(f, fId, FORWARD_TAG, a, b, new Vec3(-1, 0, 0), true)
                    || !valid(r, rId, REVERSE_TAG, b, a, new Vec3(1, 0, 0), false)) {
                return Result.INVALID_SAVED_PORTAL;
            }
            // Обновляем только уже проверенную собственную пару; синхронизируем существующих клиентов.
            hidePlayers((Portal) f);
            hidePlayers((Portal) r);
            return Result.READY;
        }
        // Сохраняем чужие порталы нетронутыми. При найденной сиротской паре не дублируем её.
        if (!level.getEntitiesOfClass(Portal.class, new AABB(a, a).inflate(4),
                p -> FORWARD_TAG.equals(p.portalTag) || REVERSE_TAG.equals(p.portalTag)).isEmpty()
                || !level.getEntitiesOfClass(Portal.class, new AABB(b, b).inflate(4),
                p -> FORWARD_TAG.equals(p.portalTag) || REVERSE_TAG.equals(p.portalTag)).isEmpty()) return Result.ORPHAN_PORTAL;

        Portal forward = Portal.ENTITY_TYPE.create(level);
        if (forward == null) return Result.SPAWN_FAILED;
        forward.setOriginPos(a);
        // W × H = -X: виден игроку, идущему по +X.
        forward.setOrientationAndSize(new Vec3(0, 0, 1), new Vec3(0, 1, 0),
                NightmareTuning.CORRIDOR_WIDTH, NightmareTuning.CORRIDOR_HEIGHT);
        PortalAPI.setPortalTransformation(forward, level.dimension(), b, null, 1.0);
        forward.portalTag = FORWARD_TAG;
        forward.setDoRenderPlayer(false);
        forward.setCommandsOnTeleported(List.of(LOOP_COMMAND));
        Portal reverse = PortalAPI.createReversePortal(forward);
        if (reverse == null) return Result.SPAWN_FAILED;
        reverse.portalTag = REVERSE_TAG;
        reverse.setDoRenderPlayer(false);
        reverse.setCommandsOnTeleported(null);
        if (!forward.isPortalValid() || !reverse.isPortalValid()) return Result.INVALID_NEW_PORTAL;
        if (!level.addFreshEntity(forward)) return Result.SPAWN_FAILED;
        if (!level.addFreshEntity(reverse)) {
            forward.discard();
            return Result.SPAWN_FAILED;
        }
        data.setWardPortals(forward.getUUID(), reverse.getUUID());
        return Result.READY;
    }

    private static void hidePlayers(Portal portal) {
        if (portal.getDoRenderPlayer()) {
            portal.setDoRenderPlayer(false);
            portal.reloadAndSyncToClient();
        }
    }

    private static boolean valid(Entity entity, UUID id, String tag, Vec3 from, Vec3 to, Vec3 normal, boolean forward) {
        if (id == null || !(entity instanceof Portal p)) return false;
        return !p.isGlobalPortal && tag.equals(p.portalTag) && p.isPortalValid() && p.position().distanceToSqr(from) < 1.0e-6
                && p.level().dimension().equals(p.getDestDim()) && p.getDestPos().distanceToSqr(to) < 1.0e-6
                && Math.abs(p.getScaling() - 1.0) < 1.0e-6 && p.getRotation() == null
                && (forward ? List.of(LOOP_COMMAND).equals(p.getCommandsOnTeleported()) : p.getCommandsOnTeleported() == null)
                && p.getNormal().distanceToSqr(normal) < 1.0e-6
                && Math.abs(p.getWidth() - NightmareTuning.CORRIDOR_WIDTH) < 1.0e-6
                && Math.abs(p.getHeight() - NightmareTuning.CORRIDOR_HEIGHT) < 1.0e-6;
    }

    /** IP выполняет внутреннюю команду сразу после переноса вперёд, от лица игрока. */
    public static boolean atForwardDestination(ServerLevel level, Vec3 pos) {
        Vec3 b = center(NightmareDefs.WARD_CORRIDOR.origin(), NightmareTuning.WARD_LOOP_BACK_X);
        return level.dimension().equals(com.hypersomnia.dream.DreamDimension.LEVEL)
                && Math.abs(pos.x - b.x) <= 3 && Math.abs(pos.z - b.z) <= 2
                && pos.y >= b.y - 3 && pos.y <= b.y + 2;
    }
}
