package net.ashpapi.papioptimization.physics;

import java.util.Collection;
import java.util.Optional;
import net.ashpapi.papioptimization.mixin.entity.LevelEntityGetterAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Continuation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.entity.PartEntity;
import org.jspecify.annotations.Nullable;

public final class LevelCollisionHelper {
    private static final double EPSILON = 1.0E-7;
    private static final EntityTypeTest<Entity, Entity> ANY_ENTITY_TEST = EntityTypeTest.forClass(Entity.class);

    public static final ClassValue<Boolean> IS_STANDARD_LEVEL = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            if (type == ServerLevel.class) {
                return Boolean.TRUE;
            }
            return type.getSuperclass() == Level.class
                    && "ClientLevel".equals(type.getSimpleName())
                    && type.getPackageName().startsWith("net.minecraft.");
        }
    };

    private LevelCollisionHelper() {
    }

    public static boolean canUseFastLevelPath(Level level, AABB box) {
        if (!IS_STANDARD_LEVEL.get(level.getClass())) {
            return false;
        }
        if (!Double.isFinite(box.minX) || !Double.isFinite(box.minY) || !Double.isFinite(box.minZ)
                || !Double.isFinite(box.maxX) || !Double.isFinite(box.maxY) || !Double.isFinite(box.maxZ)) {
            return false;
        }
        return (box.maxX - box.minX) <= 64.0 && (box.maxY - box.minY) <= 64.0 && (box.maxZ - box.minZ) <= 64.0;
    }

    public static boolean noBlockCollisionFast(Level level, @Nullable Entity entity, AABB box) {
        int minX = Mth.floor(box.minX - EPSILON) - 1;
        int maxX = Mth.floor(box.maxX + EPSILON) + 1;
        int minY = Mth.floor(box.minY - EPSILON) - 1;
        int maxY = Mth.floor(box.maxY + EPSILON) + 1;
        int minZ = Mth.floor(box.minZ - EPSILON) - 1;
        int maxZ = Mth.floor(box.maxZ + EPSILON) + 1;

        boolean targetTiny = VoxelShapeHelper.isTiny(box);
        CollisionContext context = null;
        VoxelShape entityShape = null;
        BlockPos.MutableBlockPos mpos = null;

        int minCx = minX >> 4;
        int maxCx = maxX >> 4;
        int minCz = minZ >> 4;
        int maxCz = maxZ >> 4;
        int minSecY = minY >> 4;
        int maxSecY = maxY >> 4;

        for (int cx = minCx; cx <= maxCx; cx++) {
            int bxStart = Math.max(minX, cx << 4);
            int bxEnd = Math.min(maxX, (cx << 4) + 15);
            for (int cz = minCz; cz <= maxCz; cz++) {
                BlockGetter getter = level.getChunkForCollisions(cx, cz);
                if (!(getter instanceof ChunkAccess chunk)) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                int bzStart = Math.max(minZ, cz << 4);
                int bzEnd = Math.min(maxZ, (cz << 4) + 15);

                for (int secY = minSecY; secY <= maxSecY; secY++) {
                    int secIdx = chunk.getSectionIndexFromSectionY(secY);
                    if (secIdx < 0 || secIdx >= sections.length) {
                        continue;
                    }
                    LevelChunkSection section = sections[secIdx];
                    if (section == null || section.hasOnlyAir()) {
                        continue;
                    }
                    int byStart = Math.max(minY, secY << 4);
                    int byEnd = Math.min(maxY, (secY << 4) + 15);

                    for (int by = byStart; by <= byEnd; by++) {
                        int edgeY = (by == minY || by == maxY) ? 1 : 0;
                        for (int bz = bzStart; bz <= bzEnd; bz++) {
                            int edgeYZ = edgeY + ((bz == minZ || bz == maxZ) ? 1 : 0);
                            for (int bx = bxStart; bx <= bxEnd; bx++) {
                                int edges = edgeYZ + ((bx == minX || bx == maxX) ? 1 : 0);
                                if (edges == 3) {
                                    continue;
                                }
                                BlockState state = section.getBlockState(bx & 15, by & 15, bz & 15);
                                if (state.isAir()) {
                                    continue;
                                }
                                if (edges == 1 && !state.hasLargeCollisionShape()) {
                                    continue;
                                }
                                if (edges == 2 && !state.is(Blocks.MOVING_PISTON)) {
                                    continue;
                                }
                                if (context == null) {
                                    context = entity == null ? CollisionContext.empty() : CollisionContext.of(entity);
                                    mpos = new BlockPos.MutableBlockPos();
                                }
                                mpos.set(bx, by, bz);
                                VoxelShape shape = context.getCollisionShape(state, level, mpos);
                                if (shape.isEmpty()) {
                                    continue;
                                }
                                if (shape == Shapes.block()) {
                                    if (box.intersects((double) bx, (double) by, (double) bz, (double) (bx + 1), (double) (by + 1), (double) (bz + 1))) {
                                        return false;
                                    }
                                    continue;
                                }
                                if (shape instanceof PapiVoxelShapeExtension ext) {
                                    AABB cuboid = ext.papi$getCuboidBox();
                                    if (cuboid != null && !targetTiny && !VoxelShapeHelper.isTiny(cuboid)) {
                                        if (VoxelShapeHelper.shiftedBoxOverlapsStrict(cuboid, (double) bx, (double) by, (double) bz, box)) {
                                            return false;
                                        }
                                        continue;
                                    }
                                    AABB bounds = ext.papi$getBoundsBox();
                                    if (bounds != null && !VoxelShapeHelper.shiftedBoxOverlapsStrict(bounds, (double) bx, (double) by, (double) bz, box)) {
                                        continue;
                                    }
                                }
                                VoxelShape moved = shape.move((double) bx, (double) by, (double) bz);
                                if (!moved.isEmpty()) {
                                    if (entityShape == null) {
                                        entityShape = Shapes.create(box);
                                    }
                                    if (Shapes.joinIsNotEmpty(moved, entityShape, BooleanOp.AND)) {
                                        return false;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    public static Optional<BlockPos> findSupportingBlockFast(Level level, Entity entity, AABB box) {
        int minX = Mth.floor(box.minX - EPSILON) - 1;
        int maxX = Mth.floor(box.maxX + EPSILON) + 1;
        int minY = Mth.floor(box.minY - EPSILON) - 1;
        int maxY = Mth.floor(box.maxY + EPSILON) + 1;
        int minZ = Mth.floor(box.minZ - EPSILON) - 1;
        int maxZ = Mth.floor(box.maxZ + EPSILON) + 1;

        Vec3 entityPos = entity.position();
        double px = entityPos.x;
        double py = entityPos.y;
        double pz = entityPos.z;

        boolean targetTiny = VoxelShapeHelper.isTiny(box);
        CollisionContext context = null;
        VoxelShape entityShape = null;
        BlockPos.MutableBlockPos mpos = null;

        boolean found = false;
        int bestX = 0;
        int bestY = 0;
        int bestZ = 0;
        double bestDistSqr = Double.MAX_VALUE;

        int minCx = minX >> 4;
        int maxCx = maxX >> 4;
        int minCz = minZ >> 4;
        int maxCz = maxZ >> 4;
        int minSecY = minY >> 4;
        int maxSecY = maxY >> 4;

        for (int cx = minCx; cx <= maxCx; cx++) {
            int bxStart = Math.max(minX, cx << 4);
            int bxEnd = Math.min(maxX, (cx << 4) + 15);
            for (int cz = minCz; cz <= maxCz; cz++) {
                BlockGetter getter = level.getChunkForCollisions(cx, cz);
                if (!(getter instanceof ChunkAccess chunk)) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                int bzStart = Math.max(minZ, cz << 4);
                int bzEnd = Math.min(maxZ, (cz << 4) + 15);

                for (int secY = minSecY; secY <= maxSecY; secY++) {
                    int secIdx = chunk.getSectionIndexFromSectionY(secY);
                    if (secIdx < 0 || secIdx >= sections.length) {
                        continue;
                    }
                    LevelChunkSection section = sections[secIdx];
                    if (section == null || section.hasOnlyAir()) {
                        continue;
                    }
                    int byStart = Math.max(minY, secY << 4);
                    int byEnd = Math.min(maxY, (secY << 4) + 15);

                    for (int by = byStart; by <= byEnd; by++) {
                        int edgeY = (by == minY || by == maxY) ? 1 : 0;
                        for (int bz = bzStart; bz <= bzEnd; bz++) {
                            int edgeYZ = edgeY + ((bz == minZ || bz == maxZ) ? 1 : 0);
                            for (int bx = bxStart; bx <= bxEnd; bx++) {
                                int edges = edgeYZ + ((bx == minX || bx == maxX) ? 1 : 0);
                                if (edges == 3) {
                                    continue;
                                }
                                BlockState state = section.getBlockState(bx & 15, by & 15, bz & 15);
                                if (state.isAir()) {
                                    continue;
                                }
                                if (edges == 1 && !state.hasLargeCollisionShape()) {
                                    continue;
                                }
                                if (edges == 2 && !state.is(Blocks.MOVING_PISTON)) {
                                    continue;
                                }
                                if (context == null) {
                                    context = CollisionContext.of(entity);
                                    mpos = new BlockPos.MutableBlockPos();
                                }
                                mpos.set(bx, by, bz);
                                VoxelShape shape = context.getCollisionShape(state, level, mpos);
                                if (shape.isEmpty()) {
                                    continue;
                                }
                                boolean collides = false;
                                if (shape == Shapes.block()) {
                                    collides = box.intersects((double) bx, (double) by, (double) bz, (double) (bx + 1), (double) (by + 1), (double) (bz + 1));
                                } else if (shape instanceof PapiVoxelShapeExtension ext) {
                                    AABB cuboid = ext.papi$getCuboidBox();
                                    if (cuboid != null && !targetTiny && !VoxelShapeHelper.isTiny(cuboid)) {
                                        collides = VoxelShapeHelper.shiftedBoxOverlapsStrict(cuboid, (double) bx, (double) by, (double) bz, box);
                                    } else {
                                        AABB bounds = ext.papi$getBoundsBox();
                                        if (bounds == null || VoxelShapeHelper.shiftedBoxOverlapsStrict(bounds, (double) bx, (double) by, (double) bz, box)) {
                                            VoxelShape moved = shape.move((double) bx, (double) by, (double) bz);
                                            if (!moved.isEmpty()) {
                                                if (entityShape == null) {
                                                    entityShape = Shapes.create(box);
                                                }
                                                collides = Shapes.joinIsNotEmpty(moved, entityShape, BooleanOp.AND);
                                            }
                                        }
                                    }
                                } else {
                                    VoxelShape moved = shape.move((double) bx, (double) by, (double) bz);
                                    if (!moved.isEmpty()) {
                                        if (entityShape == null) {
                                            entityShape = Shapes.create(box);
                                        }
                                        collides = Shapes.joinIsNotEmpty(moved, entityShape, BooleanOp.AND);
                                    }
                                }

                                if (collides) {
                                    double dx = ((double) bx + 0.5) - px;
                                    double dy = ((double) by + 0.5) - py;
                                    double dz = ((double) bz + 0.5) - pz;
                                    double distSqr = dx * dx + dy * dy + dz * dz;
                                    if (distSqr < bestDistSqr || (distSqr == bestDistSqr && (!found || compareVec3i(bestX, bestY, bestZ, bx, by, bz) < 0))) {
                                        found = true;
                                        bestX = bx;
                                        bestY = by;
                                        bestZ = bz;
                                        bestDistSqr = distSqr;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return found ? Optional.of(new BlockPos(bestX, bestY, bestZ)) : Optional.empty();
    }

    private static int compareVec3i(int x1, int y1, int z1, int x2, int y2, int z2) {
        if (y1 != y2) {
            return y1 - y2;
        }
        if (z1 != z2) {
            return z1 - z2;
        }
        return x1 - x2;
    }

    public static boolean noEntityCollisionFast(Level level, @Nullable Entity entity, AABB box) {
        if (box.getSize() < EPSILON) {
            return true;
        }
        AABB searchBox = box.inflate(EPSILON);
        boolean[] found = new boolean[]{false};

        ((LevelEntityGetterAccessor) level).papi$getEntityGetter().get(ANY_ENTITY_TEST, searchBox, candidate -> {
            if (candidate == entity) {
                return Continuation.CONTINUE;
            }
            boolean matches;
            if (entity == null) {
                matches = EntitySelector.CAN_BE_COLLIDED_WITH.test(candidate);
            } else {
                matches = EntitySelector.NO_SPECTATORS.test(candidate) && entity.canCollideWith(candidate);
            }
            if (matches) {
                found[0] = true;
                return Continuation.ABORT;
            }
            return Continuation.CONTINUE;
        });

        if (found[0]) {
            return false;
        }

        Collection<? extends PartEntity<?>> parts = level.dragonParts();
        if (!parts.isEmpty()) {
            for (PartEntity<?> part : parts) {
                if (part == entity || (entity != null && part.getParent() == entity)) {
                    continue;
                }
                if (!searchBox.intersects(part.getBoundingBox())) {
                    continue;
                }
                boolean matches = entity == null
                        ? EntitySelector.CAN_BE_COLLIDED_WITH.test(part)
                        : (EntitySelector.NO_SPECTATORS.test(part) && entity.canCollideWith(part));
                if (matches) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isStrictlyInsideWorldBorder(WorldBorder border, AABB box) {
        return box.minX > border.getMinX() + EPSILON
                && box.maxX < border.getMaxX() - EPSILON
                && box.minZ > border.getMinZ() + EPSILON
                && box.maxZ < border.getMaxZ() - EPSILON;
    }

    public static boolean probeStandingSupportStop(Entity entity, Level level, AABB box, double dy) {
        int bx = Mth.floor((box.minX + box.maxX) * 0.5);
        int by = Mth.floor(box.minY - 1.0E-6);
        int bz = Mth.floor((box.minZ + box.maxZ) * 0.5);
        BlockGetter getter = level.getChunkForCollisions(bx >> 4, bz >> 4);
        if (!(getter instanceof ChunkAccess chunk)) {
            return false;
        }
        int secIdx = chunk.getSectionIndexFromSectionY(by >> 4);
        LevelChunkSection[] sections = chunk.getSections();
        if (secIdx < 0 || secIdx >= sections.length) {
            return false;
        }
        LevelChunkSection section = sections[secIdx];
        if (section == null || section.hasOnlyAir()) {
            return false;
        }
        BlockState state = section.getBlockState(bx & 15, by & 15, bz & 15);
        if (state.isAir()) {
            return false;
        }
        BlockPos pos = new BlockPos(bx, by, bz);
        VoxelShape shape = state.getCollisionShape(level, pos, CollisionContext.of(entity));
        if (shape.isEmpty()) {
            return false;
        }
        double sMinX;
        double sMinY;
        double sMinZ;
        double sMaxX;
        double sMaxY;
        double sMaxZ;
        if (shape == Shapes.block()) {
            sMinX = (double) bx;
            sMinY = (double) by;
            sMinZ = (double) bz;
            sMaxX = (double) (bx + 1);
            sMaxY = (double) (by + 1);
            sMaxZ = (double) (bz + 1);
        } else if (shape instanceof PapiVoxelShapeExtension ext && ext.papi$isSingleBox()) {
            AABB local = ext.papi$getSingleBox();
            if (local == null) {
                return false;
            }
            sMinX = (double) bx + local.minX;
            sMinY = (double) by + local.minY;
            sMinZ = (double) bz + local.minZ;
            sMaxX = (double) bx + local.maxX;
            sMaxY = (double) by + local.maxY;
            sMaxZ = (double) bz + local.maxZ;
        } else {
            return false;
        }

        if (!(box.maxX - EPSILON < sMinX) && (box.minX + EPSILON < sMaxX)
                && !(box.maxZ - EPSILON < sMinZ) && (box.minZ + EPSILON < sMaxZ)
                && !(box.minY + EPSILON < sMaxY)) {
            double diff = sMaxY - box.minY;
            if (diff <= EPSILON) {
                double clamped = Math.max(dy, diff);
                return Math.abs(clamped) < EPSILON;
            }
        }
        return false;
    }

    public static AABB narrowSingleAxisExpand(AABB box, Vec3 movement) {
        double dx = movement.x;
        double dy = movement.y;
        double dz = movement.z;
        if (dx == 0.0 && dz == 0.0) {
            if (dy <= -1.0E-3) {
                return new AABB(box.minX, box.minY + dy, box.minZ, box.maxX, Math.min(box.maxY, box.minY + 1.0E-6), box.maxZ);
            }
            if (dy >= 1.0E-3) {
                return new AABB(box.minX, Math.max(box.minY, box.maxY - 1.0E-6), box.minZ, box.maxX, box.maxY + dy, box.maxZ);
            }
        } else if (dy == 0.0 && dz == 0.0) {
            if (dx <= -1.0E-3) {
                return new AABB(box.minX + dx, box.minY, box.minZ, Math.min(box.maxX, box.minX + 1.0E-6), box.maxY, box.maxZ);
            }
            if (dx >= 1.0E-3) {
                return new AABB(Math.max(box.minX, box.maxX - 1.0E-6), box.minY, box.minZ, box.maxX + dx, box.maxY, box.maxZ);
            }
        } else if (dx == 0.0 && dy == 0.0) {
            if (dz <= -1.0E-3) {
                return new AABB(box.minX, box.minY, box.minZ + dz, box.maxX, box.maxY, Math.min(box.maxZ, box.minZ + 1.0E-6));
            }
            if (dz >= 1.0E-3) {
                return new AABB(box.minX, box.minY, Math.max(box.minZ, box.maxZ - 1.0E-6), box.maxX, box.maxY, box.maxZ + dz);
            }
        }
        return box.expandTowards(movement);
    }
}
