package com.hypersomnia.nightmare;

import com.hypersomnia.dream.DreamAttachments;
import com.hypersomnia.dream.DreamData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Общие помощники перестройки зон кошмаров; трогают только собственные объёмы в измерении снов. */
final class NightmareLayout {
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private NightmareLayout() {}

    /** Если кто-то сейчас внутри кошмара этого вида, старую постройку не трогаем. */
    static boolean anyoneInside(MinecraftServer server, String kind) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            DreamData d = p.getData(DreamAttachments.DREAM);
            if (d.isInDream() && kind.equals(d.getKind())) return true;
        }
        return false;
    }

    static void clear(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = y0; y <= y1; y++) {
            BlockPos p = new BlockPos(x, y, z);
            if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
        }
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(x0, y0, z0, x1 + 1, y1 + 1, z1 + 1),
                i -> i.getTags().contains("hypersomnia_rounds_syringe"))) e.discard();
    }
}
