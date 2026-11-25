package net.ashpapi.papioptimization.physics;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

public final class FastBoxCollision {

    public static final double EPSILON = 1.0E-7;

    private FastBoxCollision() {
    }

    public static double collideSingleBox(Direction.Axis axis, AABB moving, AABB box, double distance) {
        if (Math.abs(distance) < EPSILON) {
            return 0.0;
        }

        switch (axis) {
            case X -> {
                if (!(moving.maxY - EPSILON < box.minY) && (moving.minY + EPSILON < box.maxY)
                        && !(moving.maxZ - EPSILON < box.minZ) && (moving.minZ + EPSILON < box.maxZ)) {
                    if (distance > 0.0) {
                        if (moving.maxX - EPSILON < box.minX) {
                            double diff = box.minX - moving.maxX;
                            if (diff >= -EPSILON) {
                                return Math.min(distance, diff);
                            }
                        }
                    } else if (distance < 0.0) {
                        if (!(moving.minX + EPSILON < box.maxX)) {
                            double diff = box.maxX - moving.minX;
                            if (diff <= EPSILON) {
                                return Math.max(distance, diff);
                            }
                        }
                    }
                }
            }
            case Y -> {
                if (!(moving.maxX - EPSILON < box.minX) && (moving.minX + EPSILON < box.maxX)
                        && !(moving.maxZ - EPSILON < box.minZ) && (moving.minZ + EPSILON < box.maxZ)) {
                    if (distance > 0.0) {
                        if (moving.maxY - EPSILON < box.minY) {
                            double diff = box.minY - moving.maxY;
                            if (diff >= -EPSILON) {
                                return Math.min(distance, diff);
                            }
                        }
                    } else if (distance < 0.0) {
                        if (!(moving.minY + EPSILON < box.maxY)) {
                            double diff = box.maxY - moving.minY;
                            if (diff <= EPSILON) {
                                return Math.max(distance, diff);
                            }
                        }
                    }
                }
            }
            case Z -> {
                if (!(moving.maxX - EPSILON < box.minX) && (moving.minX + EPSILON < box.maxX)
                        && !(moving.maxY - EPSILON < box.minY) && (moving.minY + EPSILON < box.maxY)) {
                    if (distance > 0.0) {
                        if (moving.maxZ - EPSILON < box.minZ) {
                            double diff = box.minZ - moving.maxZ;
                            if (diff >= -EPSILON) {
                                return Math.min(distance, diff);
                            }
                        }
                    } else if (distance < 0.0) {
                        if (!(moving.minZ + EPSILON < box.maxZ)) {
                            double diff = box.maxZ - moving.minZ;
                            if (diff <= EPSILON) {
                                return Math.max(distance, diff);
                            }
                        }
                    }
                }
            }
        }
        return distance;
    }
}
