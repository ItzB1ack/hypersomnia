package com.hypersomnia.lab;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Формы блоков лаборатории с FACING: коробки модели заданы для NORTH (лицом к -z) и поворачиваются как blockstate. */
public final class LabShapes {
    private LabShapes() {}

    /** Поворот коробок {x1, y1, z1, x2, y2, z2} как у blockstate (y = 90 по часовой стрелке): north -> east -> south -> west. */
    public static VoxelShape rotated(double[][] boxes, Direction f) {
        VoxelShape s = Shapes.empty();
        for (double[] b : boxes) {
            double x1 = b[0], y1 = b[1], z1 = b[2], x2 = b[3], y2 = b[4], z2 = b[5];
            s = Shapes.or(s, switch (f) {
                case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
                case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
                case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
                default -> Block.box(x1, y1, z1, x2, y2, z2);
            });
        }
        return s.optimize();
    }

    /** Четыре повёрнутые формы по горизонтальным сторонам, индекс = Direction.get2DDataValue(). */
    public static VoxelShape[] horizontal(double[][] boxes) {
        VoxelShape[] out = new VoxelShape[4];
        for (Direction d : Direction.Plane.HORIZONTAL) out[d.get2DDataValue()] = rotated(boxes, d);
        return out;
    }
}
