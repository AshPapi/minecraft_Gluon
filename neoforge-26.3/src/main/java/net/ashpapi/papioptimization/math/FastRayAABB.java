package net.ashpapi.papioptimization.math;

import java.util.List;
import java.util.Optional;
import net.ashpapi.papioptimization.physics.PapiVoxelShapeExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class FastRayAABB {

    public static final double EPSILON = 1.0E-7;

    private FastRayAABB() {
    }

    public static BlockHitResult clip(Iterable<AABB> aabBs, Vec3 from, Vec3 to, BlockPos pos) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;

        boolean parallelX = Math.abs(dx) < EPSILON;
        boolean parallelY = Math.abs(dy) < EPSILON;
        boolean parallelZ = Math.abs(dz) < EPSILON;

        double invDx = parallelX ? 0.0 : (1.0 / dx);
        double invDy = parallelY ? 0.0 : (1.0 / dy);
        double invDz = parallelZ ? 0.0 : (1.0 / dz);

        boolean signX = dx >= 0.0;
        boolean signY = dy >= 0.0;
        boolean signZ = dz >= 0.0;

        double ox = from.x - (double) pos.getX();
        double oy = from.y - (double) pos.getY();
        double oz = from.z - (double) pos.getZ();

        double bestT = 1.0;
        Direction bestDir = null;

        for (AABB box : aabBs) {
            double tminX;
            double tmaxX;
            if (parallelX) {
                if (ox < box.minX - EPSILON || ox > box.maxX + EPSILON) {
                    continue;
                }
                tminX = Double.NEGATIVE_INFINITY;
                tmaxX = Double.POSITIVE_INFINITY;
            } else {
                tminX = ((signX ? box.minX : box.maxX) - ox) * invDx;
                tmaxX = ((signX ? box.maxX : box.minX) - ox) * invDx;
                if (tminX >= bestT || tmaxX <= 0.0) {
                    continue;
                }
            }

            double tminY;
            double tmaxY;
            if (parallelY) {
                if (oy < box.minY - EPSILON || oy > box.maxY + EPSILON) {
                    continue;
                }
                tminY = Double.NEGATIVE_INFINITY;
                tmaxY = Double.POSITIVE_INFINITY;
            } else {
                tminY = ((signY ? box.minY : box.maxY) - oy) * invDy;
                tmaxY = ((signY ? box.maxY : box.minY) - oy) * invDy;
                if (tminY >= bestT || tmaxY <= 0.0) {
                    continue;
                }
            }

            if (tminX > tmaxY + EPSILON || tminY > tmaxX + EPSILON) {
                continue;
            }

            double tminZ;
            double tmaxZ;
            if (parallelZ) {
                if (oz < box.minZ - EPSILON || oz > box.maxZ + EPSILON) {
                    continue;
                }
                tminZ = Double.NEGATIVE_INFINITY;
                tmaxZ = Double.POSITIVE_INFINITY;
            } else {
                tminZ = ((signZ ? box.minZ : box.maxZ) - oz) * invDz;
                tmaxZ = ((signZ ? box.maxZ : box.minZ) - oz) * invDz;
                if (tminZ >= bestT || tmaxZ <= 0.0) {
                    continue;
                }
            }

            double tEnter = Math.max(Math.max(tminX, tminY), tminZ);
            double tExit = Math.min(Math.min(tmaxX, tmaxY), tmaxZ);

            if (tEnter <= tExit + EPSILON && tEnter > -EPSILON && tEnter < bestT) {
                bestT = Math.max(0.0, tEnter);
                if (tEnter == tminX) {
                    bestDir = signX ? Direction.WEST : Direction.EAST;
                } else if (tEnter == tminY) {
                    bestDir = signY ? Direction.DOWN : Direction.UP;
                } else {
                    bestDir = signZ ? Direction.NORTH : Direction.SOUTH;
                }
            }
        }

        if (bestDir == null) {
            return null;
        }

        return new BlockHitResult(from.add(bestT * dx, bestT * dy, bestT * dz), bestDir, pos, false);
    }

    public static Optional<Vec3> clip(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            Vec3 from, Vec3 to
    ) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;

        boolean parallelX = Math.abs(dx) < EPSILON;
        boolean parallelY = Math.abs(dy) < EPSILON;
        boolean parallelZ = Math.abs(dz) < EPSILON;

        double ox = from.x;
        double oy = from.y;
        double oz = from.z;

        double tminX;
        double tmaxX;
        if (parallelX) {
            if (ox < minX - EPSILON || ox > maxX + EPSILON) {
                return Optional.empty();
            }
            tminX = Double.NEGATIVE_INFINITY;
            tmaxX = Double.POSITIVE_INFINITY;
        } else {
            double invDx = 1.0 / dx;
            boolean signX = dx >= 0.0;
            tminX = ((signX ? minX : maxX) - ox) * invDx;
            tmaxX = ((signX ? maxX : minX) - ox) * invDx;
            if (tminX >= 1.0 || tmaxX <= 0.0) {
                return Optional.empty();
            }
        }

        double tminY;
        double tmaxY;
        if (parallelY) {
            if (oy < minY - EPSILON || oy > maxY + EPSILON) {
                return Optional.empty();
            }
            tminY = Double.NEGATIVE_INFINITY;
            tmaxY = Double.POSITIVE_INFINITY;
        } else {
            double invDy = 1.0 / dy;
            boolean signY = dy >= 0.0;
            tminY = ((signY ? minY : maxY) - oy) * invDy;
            tmaxY = ((signY ? maxY : minY) - oy) * invDy;
            if (tminY >= 1.0 || tmaxY <= 0.0) {
                return Optional.empty();
            }
        }

        if (tminX > tmaxY + EPSILON || tminY > tmaxX + EPSILON) {
            return Optional.empty();
        }

        double tminZ;
        double tmaxZ;
        if (parallelZ) {
            if (oz < minZ - EPSILON || oz > maxZ + EPSILON) {
                return Optional.empty();
            }
            tminZ = Double.NEGATIVE_INFINITY;
            tmaxZ = Double.POSITIVE_INFINITY;
        } else {
            double invDz = 1.0 / dz;
            boolean signZ = dz >= 0.0;
            tminZ = ((signZ ? minZ : maxZ) - oz) * invDz;
            tmaxZ = ((signZ ? maxZ : minZ) - oz) * invDz;
            if (tminZ >= 1.0 || tmaxZ <= 0.0) {
                return Optional.empty();
            }
        }

        double tEnter = Math.max(Math.max(tminX, tminY), tminZ);
        double tExit = Math.min(Math.min(tmaxX, tmaxY), tmaxZ);

        if (tEnter <= tExit + EPSILON && tEnter > -EPSILON && tEnter < 1.0) {
            double clampedT = Math.max(0.0, tEnter);
            return Optional.of(from.add(clampedT * dx, clampedT * dy, clampedT * dz));
        }

        return Optional.empty();
    }

    public static boolean hitsVoxelShape(
            VoxelShape shape,
            int bx,
            int by,
            int bz,
            double fromX,
            double fromY,
            double fromZ,
            double dx,
            double dy,
            double dz,
            boolean parallelX,
            boolean parallelY,
            boolean parallelZ,
            double invDx,
            double invDy,
            double invDz,
            boolean signX,
            boolean signY,
            boolean signZ
    ) {
        if (shape.isEmpty()) {
            return false;
        }
        double ox = fromX - (double) bx;
        double oy = fromY - (double) by;
        double oz = fromZ - (double) bz;
        double ix = ox + 0.001 * dx;
        double iy = oy + 0.001 * dy;
        double iz = oz + 0.001 * dz;

        if (shape == Shapes.block()) {
            if (ix >= 0.0 && ix < 1.0 && iy >= 0.0 && iy < 1.0 && iz >= 0.0 && iz < 1.0) {
                return true;
            }
            return rayHitsBounds(
                    0.0, 0.0, 0.0, 1.0, 1.0, 1.0,
                    ox, oy, oz,
                    parallelX, parallelY, parallelZ,
                    invDx, invDy, invDz,
                    signX, signY, signZ
            );
        }

        if (shape instanceof PapiVoxelShapeExtension ext) {
            AABB cuboid = ext.papi$getCuboidBox();
            if (cuboid != null) {
                if (ix >= cuboid.minX && ix < cuboid.maxX
                        && iy >= cuboid.minY && iy < cuboid.maxY
                        && iz >= cuboid.minZ && iz < cuboid.maxZ) {
                    return true;
                }
                return rayHitsBounds(
                        cuboid.minX, cuboid.minY, cuboid.minZ, cuboid.maxX, cuboid.maxY, cuboid.maxZ,
                        ox, oy, oz,
                        parallelX, parallelY, parallelZ,
                        invDx, invDy, invDz,
                        signX, signY, signZ
                );
            }
            AABB bounds = ext.papi$getBoundsBox();
            if (bounds != null) {
                boolean insideBounds = ix >= bounds.minX && ix < bounds.maxX
                        && iy >= bounds.minY && iy < bounds.maxY
                        && iz >= bounds.minZ && iz < bounds.maxZ;
                if (!insideBounds && !rayHitsBounds(
                        bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ,
                        ox, oy, oz,
                        parallelX, parallelY, parallelZ,
                        invDx, invDy, invDz,
                        signX, signY, signZ
                )) {
                    return false;
                }
            }
        }

        List<AABB> boxes = shape.toAabbs();
        for (int i = 0, len = boxes.size(); i < len; i++) {
            AABB b = boxes.get(i);
            if (ix >= b.minX && ix < b.maxX
                    && iy >= b.minY && iy < b.maxY
                    && iz >= b.minZ && iz < b.maxZ) {
                return true;
            }
            if (rayHitsBounds(
                    b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ,
                    ox, oy, oz,
                    parallelX, parallelY, parallelZ,
                    invDx, invDy, invDz,
                    signX, signY, signZ
            )) {
                return true;
            }
        }
        return false;
    }

    private static boolean rayHitsBounds(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            double ox, double oy, double oz,
            boolean parallelX, boolean parallelY, boolean parallelZ,
            double invDx, double invDy, double invDz,
            boolean signX, boolean signY, boolean signZ
    ) {
        double tminX;
        double tmaxX;
        if (parallelX) {
            if (ox < minX - EPSILON || ox > maxX + EPSILON) {
                return false;
            }
            tminX = Double.NEGATIVE_INFINITY;
            tmaxX = Double.POSITIVE_INFINITY;
        } else {
            tminX = ((signX ? minX : maxX) - ox) * invDx;
            tmaxX = ((signX ? maxX : minX) - ox) * invDx;
            if (tminX >= 1.0 || tmaxX <= 0.0) {
                return false;
            }
        }

        double tminY;
        double tmaxY;
        if (parallelY) {
            if (oy < minY - EPSILON || oy > maxY + EPSILON) {
                return false;
            }
            tminY = Double.NEGATIVE_INFINITY;
            tmaxY = Double.POSITIVE_INFINITY;
        } else {
            tminY = ((signY ? minY : maxY) - oy) * invDy;
            tmaxY = ((signY ? maxY : minY) - oy) * invDy;
            if (tminY >= 1.0 || tmaxY <= 0.0) {
                return false;
            }
        }

        if (tminX > tmaxY + EPSILON || tminY > tmaxX + EPSILON) {
            return false;
        }

        double tminZ;
        double tmaxZ;
        if (parallelZ) {
            if (oz < minZ - EPSILON || oz > maxZ + EPSILON) {
                return false;
            }
            tminZ = Double.NEGATIVE_INFINITY;
            tmaxZ = Double.POSITIVE_INFINITY;
        } else {
            tminZ = ((signZ ? minZ : maxZ) - oz) * invDz;
            tmaxZ = ((signZ ? maxZ : minZ) - oz) * invDz;
            if (tminZ >= 1.0 || tmaxZ <= 0.0) {
                return false;
            }
        }

        double tEnter = Math.max(Math.max(tminX, tminY), tminZ);
        double tExit = Math.min(Math.min(tmaxX, tmaxY), tmaxZ);
        return tEnter <= tExit + EPSILON && tEnter > -EPSILON && tEnter < 1.0;
    }
}
