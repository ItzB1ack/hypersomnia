package com.hypersomnia.nightmare;

import com.mojang.serialization.MapCodec;
import com.hypersomnia.lab.LabShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Две декоративные половины койки: не ванильная кровать, сон и взрыв не вызываются. */
public final class WardBedBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<WardBedBlock> CODEC = simpleCodec(WardBedBlock::new);
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
    private static final VoxelShape[] SHAPES = LabShapes.horizontal(new double[][] {{1, 0, 0, 15, 10, 16}});

    public WardBedBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BedPart.FOOT));
    }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }
}
