package net.ashpapi.papioptimization.physics;

import net.ashpapi.papioptimization.util.DirectionConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class PistonShapeHelper {
    private static final VoxelShape[] EXTENDED_BASE = new VoxelShape[6];
    private static final VoxelShape[] HEAD_LONG = new VoxelShape[6];
    private static final VoxelShape[] HEAD_SHORT = new VoxelShape[6];
    private static final VoxelShape[] RETRACTING_DISCRETE = new VoxelShape[18];

    static {
        Direction[] dirs = DirectionConstants.ALL;
        for (int i = 0; i < dirs.length; i++) {
            Direction dir = dirs[i];
            int ord = dir.ordinal();
            VoxelShape base = Blocks.PISTON.defaultBlockState()
                    .setValue(PistonBaseBlock.FACING, dir)
                    .setValue(PistonBaseBlock.EXTENDED, Boolean.TRUE)
                    .getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            VoxelShape headLong = Blocks.PISTON_HEAD.defaultBlockState()
                    .setValue(PistonHeadBlock.FACING, dir)
                    .setValue(PistonHeadBlock.SHORT, Boolean.FALSE)
                    .getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            VoxelShape headShort = Blocks.PISTON_HEAD.defaultBlockState()
                    .setValue(PistonHeadBlock.FACING, dir)
                    .setValue(PistonHeadBlock.SHORT, Boolean.TRUE)
                    .getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);

            EXTENDED_BASE[ord] = base;
            HEAD_LONG[ord] = headLong;
            HEAD_SHORT[ord] = headShort;

            RETRACTING_DISCRETE[ord * 3] = Shapes.or(base, headLong.move(
                    (double) dir.getStepX(),
                    (double) dir.getStepY(),
                    (double) dir.getStepZ()
            ));
            RETRACTING_DISCRETE[ord * 3 + 1] = Shapes.or(base, headLong.move(
                    (double) ((float) dir.getStepX() * 0.5F),
                    (double) ((float) dir.getStepY() * 0.5F),
                    (double) ((float) dir.getStepZ() * 0.5F)
            ));
            RETRACTING_DISCRETE[ord * 3 + 2] = Shapes.or(base, headShort);
        }
    }

    private PistonShapeHelper() {
    }

    public static VoxelShape getExtendedBase(Direction facing) {
        return EXTENDED_BASE[facing.ordinal()];
    }

    public static VoxelShape getHead(Direction facing, boolean isShort) {
        return isShort ? HEAD_SHORT[facing.ordinal()] : HEAD_LONG[facing.ordinal()];
    }

    public static VoxelShape getRetractingDiscrete(Direction facing, float progress) {
        if (progress == 0.0F) {
            return RETRACTING_DISCRETE[facing.ordinal() * 3];
        }
        if (progress == 0.5F) {
            return RETRACTING_DISCRETE[facing.ordinal() * 3 + 1];
        }
        if (progress == 1.0F) {
            return RETRACTING_DISCRETE[facing.ordinal() * 3 + 2];
        }
        return null;
    }
}
