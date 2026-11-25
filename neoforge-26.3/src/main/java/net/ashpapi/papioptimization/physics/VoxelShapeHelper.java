package net.ashpapi.papioptimization.physics;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class VoxelShapeHelper {
    public static final byte STATE_UNINITIALIZED = 0;
    public static final byte STATE_SINGLE_SEGMENT_BOX = 1;
    public static final byte STATE_MULTI_SEGMENT_CUBOID = 2;
    public static final byte STATE_EMPTY = 3;
    public static final byte STATE_COMPLEX = 4;

    private static final double EPSILON = 1.0E-7;
    private static final double TINY_THRESHOLD = 3.0E-7;

    private VoxelShapeHelper() {
    }

    public static boolean isTiny(AABB box) {
        return (box.maxX - box.minX) < TINY_THRESHOLD
                || (box.maxY - box.minY) < TINY_THRESHOLD
                || (box.maxZ - box.minZ) < TINY_THRESHOLD;
    }

    public static boolean boxesOverlapStrict(AABB b1, AABB b2) {
        return b1.minX < b2.maxX - EPSILON && b1.maxX - EPSILON > b2.minX
                && b1.minY < b2.maxY - EPSILON && b1.maxY - EPSILON > b2.minY
                && b1.minZ < b2.maxZ - EPSILON && b1.maxZ - EPSILON > b2.minZ;
    }

    public static boolean shiftedBoxOverlapsStrict(AABB localBox, double px, double py, double pz, AABB target) {
        return (px + localBox.minX) < target.maxX - EPSILON && (px + localBox.maxX) - EPSILON > target.minX
                && (py + localBox.minY) < target.maxY - EPSILON && (py + localBox.maxY) - EPSILON > target.minY
                && (pz + localBox.minZ) < target.maxZ - EPSILON && (pz + localBox.maxZ) - EPSILON > target.minZ;
    }

    public static void analyzeShape(VoxelShape shape, DiscreteVoxelShape discrete) {
        PapiVoxelShapeExtension ext = (PapiVoxelShapeExtension) shape;
        int minX = discrete.firstFull(Direction.Axis.X);
        int maxX = discrete.lastFull(Direction.Axis.X);
        if (minX >= maxX) {
            ext.papi$initMovedState(STATE_EMPTY, null, null);
            return;
        }
        int minY = discrete.firstFull(Direction.Axis.Y);
        int maxY = discrete.lastFull(Direction.Axis.Y);
        if (minY >= maxY) {
            ext.papi$initMovedState(STATE_EMPTY, null, null);
            return;
        }
        int minZ = discrete.firstFull(Direction.Axis.Z);
        int maxZ = discrete.lastFull(Direction.Axis.Z);
        if (minZ >= maxZ) {
            ext.papi$initMovedState(STATE_EMPTY, null, null);
            return;
        }

        DoubleList cx = shape.getCoords(Direction.Axis.X);
        DoubleList cy = shape.getCoords(Direction.Axis.Y);
        DoubleList cz = shape.getCoords(Direction.Axis.Z);
        AABB bounds = new AABB(
                cx.getDouble(minX),
                cy.getDouble(minY),
                cz.getDouble(minZ),
                cx.getDouble(maxX),
                cy.getDouble(maxY),
                cz.getDouble(maxZ)
        );

        int dx = maxX - minX;
        int dy = maxY - minY;
        int dz = maxZ - minZ;
        if (dx == 1 && dy == 1 && dz == 1) {
            ext.papi$initMovedState(STATE_SINGLE_SEGMENT_BOX, bounds, bounds);
            return;
        }

        long volume = (long) dx * (long) dy * (long) dz;
        if (volume <= 4096L) {
            boolean allFull = true;
            outer:
            for (int x = minX; x < maxX; x++) {
                for (int y = minY; y < maxY; y++) {
                    for (int z = minZ; z < maxZ; z++) {
                        if (!discrete.isFull(x, y, z)) {
                            allFull = false;
                            break outer;
                        }
                    }
                }
            }
            if (allFull) {
                ext.papi$initMovedState(STATE_MULTI_SEGMENT_CUBOID, bounds, bounds);
                return;
            }
        }

        ext.papi$initMovedState(STATE_COMPLEX, null, bounds);
    }

    public static boolean computeIsFullBlock(VoxelShape shape) {
        if (shape == Shapes.block()) {
            return true;
        }
        if (shape.isEmpty()) {
            return false;
        }
        return !Shapes.joinIsNotEmpty(Shapes.block(), shape, BooleanOp.NOT_SAME);
    }
}
