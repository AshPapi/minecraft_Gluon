package net.ashpapi.papioptimization.core;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.ashpapi.papioptimization.math.FastRayAABB;
import net.ashpapi.papioptimization.physics.LevelCollisionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EntityBasedExplosionDamageCalculator;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public final class ExplosionOptimizer {
    public static final int TOTAL_RAYS = 1352;
    private static final double[] RAY_STEPS = new double[TOTAL_RAYS * 3];

    private static final int CACHE_SIZE = 1024;
    private static final int CACHE_MASK = CACHE_SIZE - 1;
    private static final ThreadLocal<RayCache> LOCAL_CACHE = ThreadLocal.withInitial(RayCache::new);

    private static final int SEEN_CACHE_SIZE = 64;
    private static final int SEEN_CACHE_MASK = SEEN_CACHE_SIZE - 1;
    private static final ThreadLocal<SeenVoxelCache> SEEN_CACHE = ThreadLocal.withInitial(SeenVoxelCache::new);

    private record ExposureKey(Vec3 center, AABB box) {
    }

    private static final class ExposureFrame {
        @Nullable Vec3 primaryCenter;
        final Object2FloatOpenHashMap<AABB> byBox = new Object2FloatOpenHashMap<>(16);
        @Nullable Map<ExposureKey, Float> fallback;

        ExposureFrame() {
            this.byBox.defaultReturnValue(-1.0F);
        }

        void reset() {
            this.primaryCenter = null;
            this.byBox.clear();
            if (this.fallback != null) {
                this.fallback.clear();
            }
        }
    }

    private static final class ExposureStack {
        final ArrayDeque<ExposureFrame> active = new ArrayDeque<>(2);
        final ArrayDeque<ExposureFrame> pool = new ArrayDeque<>(2);
    }

    private static final ThreadLocal<ExposureStack> EXPOSURE_STACK = ThreadLocal.withInitial(ExposureStack::new);

    static {
        int idx = 0;
        for (int xx = 0; xx < 16; ++xx) {
            for (int yy = 0; yy < 16; ++yy) {
                for (int zz = 0; zz < 16; ++zz) {
                    if (xx != 0 && xx != 15 && yy != 0 && yy != 15 && zz != 0 && zz != 15) {
                        continue;
                    }
                    double xd = (float) xx / 15.0F * 2.0F - 1.0F;
                    double yd = (float) yy / 15.0F * 2.0F - 1.0F;
                    double zd = (float) zz / 15.0F * 2.0F - 1.0F;
                    double d = Math.sqrt(xd * xd + yd * yd + zd * zd);
                    RAY_STEPS[idx++] = (xd / d) * (double) 0.3F;
                    RAY_STEPS[idx++] = (yd / d) * (double) 0.3F;
                    RAY_STEPS[idx++] = (zd / d) * (double) 0.3F;
                }
            }
        }
    }

    private ExplosionOptimizer() {
    }

    private static final class RayCache {
        final long[] keys = new long[CACHE_SIZE];
        final float[] delta = new float[CACHE_SIZE];
        final BlockState[] blockStates = new BlockState[CACHE_SIZE];
        boolean inUse;

        void clear() {
            Arrays.fill(this.keys, Long.MIN_VALUE);
            Arrays.fill(this.blockStates, null);
        }
    }

    private static final class SeenVoxelCache {
        final long[] keys = new long[SEEN_CACHE_SIZE];
        final VoxelShape[] shapes = new VoxelShape[SEEN_CACHE_SIZE];
        boolean inUse;

        void clear() {
            Arrays.fill(this.keys, Long.MIN_VALUE);
            Arrays.fill(this.shapes, null);
        }
    }

    public static void startExplosionContext() {
        ExposureStack st = EXPOSURE_STACK.get();
        ExposureFrame frame = st.pool.pollLast();
        if (frame == null) {
            frame = new ExposureFrame();
        }
        st.active.push(frame);
    }

    public static void endExplosionContext() {
        ExposureStack st = EXPOSURE_STACK.get();
        if (!st.active.isEmpty()) {
            ExposureFrame frame = st.active.pop();
            frame.reset();
            if (st.pool.size() < 4) {
                st.pool.addLast(frame);
            }
        }
    }

    public static float getCachedExposureFloat(Vec3 center, AABB box) {
        if (center == null || box == null) {
            return -1.0F;
        }
        ExposureFrame current = EXPOSURE_STACK.get().active.peek();
        if (current == null) {
            return -1.0F;
        }
        if (current.primaryCenter == null) {
            return -1.0F;
        }
        if (current.primaryCenter.equals(center)) {
            return current.byBox.getFloat(box);
        }
        if (current.fallback != null) {
            Float v = current.fallback.get(new ExposureKey(center, box));
            return v != null ? v : -1.0F;
        }
        return -1.0F;
    }

    public static @Nullable Float getCachedExposure(Vec3 center, AABB box) {
        float val = getCachedExposureFloat(center, box);
        return val >= 0.0F ? val : null;
    }

    public static void putCachedExposure(Vec3 center, AABB box, float exposure) {
        if (center == null || box == null) {
            return;
        }
        ExposureFrame current = EXPOSURE_STACK.get().active.peek();
        if (current == null) {
            return;
        }
        if (current.primaryCenter == null) {
            current.primaryCenter = center;
            current.byBox.put(box, exposure);
        } else if (current.primaryCenter.equals(center)) {
            current.byBox.put(box, exposure);
        } else {
            if (current.fallback == null) {
                current.fallback = new HashMap<>(4);
            }
            current.fallback.put(new ExposureKey(center, box), exposure);
        }
    }

    public static List<BlockPos> calculateExplodedPositions(
            ServerExplosion explosion,
            ServerLevel level,
            Vec3 center,
            float radius,
            ExplosionDamageCalculator damageCalculator
    ) {
        return calculateExplodedPositions(explosion, level, center, radius, damageCalculator, true, null);
    }

    public static List<BlockPos> calculateExplodedPositions(
            ServerExplosion explosion,
            ServerLevel level,
            Vec3 center,
            float radius,
            ExplosionDamageCalculator damageCalculator,
            boolean fire,
            int @Nullable [] skippedAirOut
    ) {
        LongOpenHashSet toBlowSet = new LongOpenHashSet();
        boolean skipAir = !fire && !isNearEndOrigin(level, center, radius);
        boolean trackAirCount = skipAir && skippedAirOut != null && hasPlayerWithin64Blocks(level, center);
        LongOpenHashSet airSet = trackAirCount ? new LongOpenHashSet() : null;

        Class<?> calcClass = damageCalculator.getClass();
        boolean standardAirZeroResistance = calcClass == ExplosionDamageCalculator.class
                || calcClass == EntityBasedExplosionDamageCalculator.class;

        RayCache cache = LOCAL_CACHE.get();
        boolean borrowed = !cache.inUse;
        if (!borrowed) {
            cache = new RayCache();
        }
        cache.inUse = true;
        cache.clear();

        try {
            int minY = level.getMinY();
            int maxY = level.getMaxY();
            BlockState airState = Blocks.AIR.defaultBlockState();
            FluidState emptyFluid = Fluids.EMPTY.defaultFluidState();

            BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
            double[] raySteps = RAY_STEPS;
            int stepIdx = 0;

            int lastCx = Integer.MIN_VALUE;
            int lastCz = Integer.MIN_VALUE;
            LevelChunk lastChunk = null;
            int lastSecY = Integer.MIN_VALUE;
            LevelChunkSection lastSection = null;

            for (int i = 0; i < TOTAL_RAYS; i++) {
                double stepX = raySteps[stepIdx++];
                double stepY = raySteps[stepIdx++];
                double stepZ = raySteps[stepIdx++];

                float remainingPower = radius * (0.7F + level.getRandom().nextFloat() * 0.6F);
                double xp = center.x;
                double yp = center.y;
                double zp = center.z;

                int prevBx = Integer.MIN_VALUE;
                int prevBy = Integer.MIN_VALUE;
                int prevBz = Integer.MIN_VALUE;
                long prevPacked = Long.MIN_VALUE;
                float cachedDelta = 0.0F;
                BlockState cachedBlock = null;
                boolean prevExplodedRecorded = false;

                while (remainingPower > 0.0F) {
                    int bx = Mth.floor(xp);
                    int by = Mth.floor(yp);
                    int bz = Mth.floor(zp);

                    if (by < minY || by > maxY
                            || bx < -30000000 || bz < -30000000
                            || bx >= 30000000 || bz >= 30000000) {
                        break;
                    }

                    if (bx == prevBx && by == prevBy && bz == prevBz) {
                        remainingPower -= cachedDelta;
                        if (remainingPower > 0.0F && !prevExplodedRecorded && cachedBlock != null) {
                            if (skipAir && cachedBlock.isAir()) {
                                if (airSet != null) {
                                    mpos.set(bx, by, bz);
                                    if (damageCalculator.shouldBlockExplode(explosion, (BlockGetter) level, mpos, cachedBlock, remainingPower)) {
                                        airSet.add(prevPacked);
                                        prevExplodedRecorded = true;
                                    }
                                } else {
                                    prevExplodedRecorded = true;
                                }
                            } else {
                                mpos.set(bx, by, bz);
                                if (damageCalculator.shouldBlockExplode(explosion, (BlockGetter) level, mpos, cachedBlock, remainingPower)) {
                                    toBlowSet.add(prevPacked);
                                    prevExplodedRecorded = true;
                                }
                            }
                        }
                    } else {
                        prevBx = bx;
                        prevBy = by;
                        prevBz = bz;
                        prevExplodedRecorded = false;

                        long packed = BlockPos.asLong(bx, by, bz);
                        prevPacked = packed;
                        int hash = (int) HashCommon.mix(packed) & CACHE_MASK;

                        if (cache.keys[hash] == packed) {
                            cachedDelta = cache.delta[hash];
                            cachedBlock = cache.blockStates[hash];
                        } else {
                            int cx = bx >> 4;
                            int cz = bz >> 4;
                            int secY = by >> 4;
                            if (cx != lastCx || cz != lastCz) {
                                lastCx = cx;
                                lastCz = cz;
                                lastChunk = level.getChunk(cx, cz);
                                lastSecY = secY;
                                int secIdx = lastChunk.getSectionIndexFromSectionY(secY);
                                LevelChunkSection[] sections = lastChunk.getSections();
                                lastSection = (secIdx >= 0 && secIdx < sections.length) ? sections[secIdx] : null;
                            } else if (secY != lastSecY) {
                                lastSecY = secY;
                                int secIdx = lastChunk.getSectionIndexFromSectionY(secY);
                                LevelChunkSection[] sections = lastChunk.getSections();
                                lastSection = (secIdx >= 0 && secIdx < sections.length) ? sections[secIdx] : null;
                            }

                            BlockState block;
                            float delta;
                            if (lastSection == null || lastSection.hasOnlyAir()) {
                                block = airState;
                                if (standardAirZeroResistance) {
                                    delta = 0.0F;
                                } else {
                                    mpos.set(bx, by, bz);
                                    Optional<Float> res = damageCalculator.getBlockExplosionResistance(
                                            explosion, (BlockGetter) level, mpos, airState, emptyFluid
                                    );
                                    delta = res.isPresent() ? (res.get() + 0.3F) * 0.3F : 0.0F;
                                }
                            } else {
                                block = lastSection.getBlockState(bx & 15, by & 15, bz & 15);
                                FluidState fluid = block.getFluidState();
                                if (standardAirZeroResistance && block.isAir() && fluid.isEmpty()) {
                                    delta = 0.0F;
                                } else {
                                    mpos.set(bx, by, bz);
                                    Optional<Float> res = damageCalculator.getBlockExplosionResistance(
                                            explosion, (BlockGetter) level, mpos, block, fluid
                                    );
                                    delta = res.isPresent() ? (res.get() + 0.3F) * 0.3F : 0.0F;
                                }
                            }

                            cache.keys[hash] = packed;
                            cache.delta[hash] = delta;
                            cache.blockStates[hash] = block;

                            cachedDelta = delta;
                            cachedBlock = block;
                        }

                        remainingPower -= cachedDelta;
                        if (remainingPower > 0.0F && cachedBlock != null) {
                            if (skipAir && cachedBlock.isAir()) {
                                if (airSet != null) {
                                    mpos.set(bx, by, bz);
                                    if (damageCalculator.shouldBlockExplode(explosion, (BlockGetter) level, mpos, cachedBlock, remainingPower)) {
                                        airSet.add(packed);
                                        prevExplodedRecorded = true;
                                    }
                                } else {
                                    prevExplodedRecorded = true;
                                }
                            } else {
                                mpos.set(bx, by, bz);
                                if (damageCalculator.shouldBlockExplode(explosion, (BlockGetter) level, mpos, cachedBlock, remainingPower)) {
                                    toBlowSet.add(packed);
                                    prevExplodedRecorded = true;
                                }
                            }
                        }
                    }

                    xp += stepX;
                    yp += stepY;
                    zp += stepZ;
                    remainingPower -= 0.22500001F;
                }
            }

            if (skippedAirOut != null) {
                skippedAirOut[0] = airSet != null ? airSet.size() : 0;
            }

            ObjectArrayList<BlockPos> result = new ObjectArrayList<>(toBlowSet.size());
            for (long packed : toBlowSet) {
                result.add(BlockPos.of(packed));
            }
            return result;
        } finally {
            cache.clear();
            cache.inUse = false;
        }
    }

    private static boolean isNearEndOrigin(ServerLevel level, Vec3 center, float radius) {
        if (level.dimension() != Level.END) {
            return false;
        }
        double bound = (double) (8 + (int) (6.0F * radius));
        return Math.abs(center.x) <= bound && Math.abs(center.z) <= bound;
    }

    private static boolean hasPlayerWithin64Blocks(ServerLevel level, Vec3 center) {
        List<ServerPlayer> players = level.players();
        for (int i = 0, len = players.size(); i < len; i++) {
            if (players.get(i).distanceToSqr(center) < 4096.0) {
                return true;
            }
        }
        return false;
    }

    public static float computeSeenPercentFast(Vec3 center, Entity entity) {
        AABB box = entity.getBoundingBox();
        float cached = getCachedExposureFloat(center, box);
        if (cached >= 0.0F) {
            return cached;
        }

        Level level = entity.level();
        if (!LevelCollisionHelper.IS_STANDARD_LEVEL.get(level.getClass())) {
            return -1.0F;
        }

        double stepX = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double stepY = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double stepZ = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        double offX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
        double offZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;

        if (stepX < 0.0 || stepY < 0.0 || stepZ < 0.0) {
            putCachedExposure(center, box, 0.0F);
            return 0.0F;
        }

        SeenVoxelCache voxelCache = SEEN_CACHE.get();
        boolean borrowed = !voxelCache.inUse;
        if (!borrowed) {
            voxelCache = new SeenVoxelCache();
        }
        voxelCache.inUse = true;
        voxelCache.clear();

        try {
            CollisionContext collisionContext = CollisionContext.of(entity);
            BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
            int minY = level.getMinY();
            int maxY = level.getMaxY();

            int missedRays = 0;
            int totalRays = 0;

            double toX = center.x;
            double toY = center.y;
            double toZ = center.z;

            for (double fx = 0.0; fx <= 1.0; fx += stepX) {
                double fromX = Mth.lerp(fx, box.minX, box.maxX) + offX;
                for (double fy = 0.0; fy <= 1.0; fy += stepY) {
                    double fromY = Mth.lerp(fy, box.minY, box.maxY);
                    for (double fz = 0.0; fz <= 1.0; fz += stepZ) {
                        double fromZ = Mth.lerp(fz, box.minZ, box.maxZ) + offZ;
                        if (!isRayObstructed(
                                level, collisionContext, mpos, voxelCache,
                                minY, maxY,
                                fromX, fromY, fromZ,
                                toX, toY, toZ
                        )) {
                            missedRays++;
                        }
                        totalRays++;
                    }
                }
            }

            float exposure = (float) missedRays / (float) totalRays;
            putCachedExposure(center, box, exposure);
            return exposure;
        } finally {
            voxelCache.clear();
            voxelCache.inUse = false;
        }
    }

    private static boolean isRayObstructed(
            Level level,
            CollisionContext collisionContext,
            BlockPos.MutableBlockPos mpos,
            SeenVoxelCache voxelCache,
            int minY,
            int maxY,
            double fromX,
            double fromY,
            double fromZ,
            double toX,
            double toY,
            double toZ
    ) {
        if (fromX == toX && fromY == toY && fromZ == toZ) {
            return false;
        }
        double rayDx = toX - fromX;
        double rayDy = toY - fromY;
        double rayDz = toZ - fromZ;
        if ((rayDx * rayDx + rayDy * rayDy + rayDz * rayDz) < 1.0E-7) {
            return false;
        }

        boolean parallelX = Math.abs(rayDx) < FastRayAABB.EPSILON;
        boolean parallelY = Math.abs(rayDy) < FastRayAABB.EPSILON;
        boolean parallelZ = Math.abs(rayDz) < FastRayAABB.EPSILON;
        double invDx = parallelX ? 0.0 : (1.0 / rayDx);
        double invDy = parallelY ? 0.0 : (1.0 / rayDy);
        double invDz = parallelZ ? 0.0 : (1.0 / rayDz);
        boolean signX = rayDx >= 0.0;
        boolean signY = rayDy >= 0.0;
        boolean signZ = rayDz >= 0.0;

        double endX = Mth.lerp(-1.0E-7, toX, fromX);
        double endY = Mth.lerp(-1.0E-7, toY, fromY);
        double endZ = Mth.lerp(-1.0E-7, toZ, fromZ);
        double startX = Mth.lerp(-1.0E-7, fromX, toX);
        double startY = Mth.lerp(-1.0E-7, fromY, toY);
        double startZ = Mth.lerp(-1.0E-7, fromZ, toZ);

        int bx = Mth.floor(startX);
        int by = Mth.floor(startY);
        int bz = Mth.floor(startZ);

        int lastCx = bx >> 4;
        int lastCz = bz >> 4;
        ChunkAccess lastChunk = level.getChunkSource().getChunkNow(lastCx, lastCz);
        int lastSecY = by >> 4;
        LevelChunkSection lastSection = null;
        if (lastChunk != null) {
            int idx = lastChunk.getSectionIndexFromSectionY(lastSecY);
            LevelChunkSection[] sections = lastChunk.getSections();
            lastSection = (idx >= 0 && idx < sections.length) ? sections[idx] : null;
        }

        if (checkSeenVoxel(
                level, collisionContext, mpos, voxelCache,
                minY, maxY, bx, by, bz, lastChunk, lastSection,
                fromX, fromY, fromZ, rayDx, rayDy, rayDz,
                parallelX, parallelY, parallelZ, invDx, invDy, invDz, signX, signY, signZ
        )) {
            return true;
        }

        double ddaDx = endX - startX;
        double ddaDy = endY - startY;
        double ddaDz = endZ - startZ;
        int stepX = Mth.sign(ddaDx);
        int stepY = Mth.sign(ddaDy);
        int stepZ = Mth.sign(ddaDz);
        double tDeltaX = stepX == 0 ? Double.MAX_VALUE : (double) stepX / ddaDx;
        double tDeltaY = stepY == 0 ? Double.MAX_VALUE : (double) stepY / ddaDy;
        double tDeltaZ = stepZ == 0 ? Double.MAX_VALUE : (double) stepZ / ddaDz;
        double tMaxX = tDeltaX * (stepX > 0 ? 1.0 - Mth.frac(startX) : Mth.frac(startX));
        double tMaxY = tDeltaY * (stepY > 0 ? 1.0 - Mth.frac(startY) : Mth.frac(startY));
        double tMaxZ = tDeltaZ * (stepZ > 0 ? 1.0 - Mth.frac(startZ) : Mth.frac(startZ));

        while (tMaxX <= 1.0 || tMaxY <= 1.0 || tMaxZ <= 1.0) {
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    bx += stepX;
                    tMaxX += tDeltaX;
                } else {
                    bz += stepZ;
                    tMaxZ += tDeltaZ;
                }
            } else if (tMaxY < tMaxZ) {
                by += stepY;
                tMaxY += tDeltaY;
            } else {
                bz += stepZ;
                tMaxZ += tDeltaZ;
            }

            int curCx = bx >> 4;
            int curCz = bz >> 4;
            int curSecY = by >> 4;
            if (curCx != lastCx || curCz != lastCz) {
                lastCx = curCx;
                lastCz = curCz;
                lastChunk = level.getChunkSource().getChunkNow(curCx, curCz);
                lastSecY = curSecY;
                if (lastChunk != null) {
                    int idx = lastChunk.getSectionIndexFromSectionY(curSecY);
                    LevelChunkSection[] sections = lastChunk.getSections();
                    lastSection = (idx >= 0 && idx < sections.length) ? sections[idx] : null;
                } else {
                    lastSection = null;
                }
            } else if (curSecY != lastSecY) {
                lastSecY = curSecY;
                if (lastChunk != null) {
                    int idx = lastChunk.getSectionIndexFromSectionY(curSecY);
                    LevelChunkSection[] sections = lastChunk.getSections();
                    lastSection = (idx >= 0 && idx < sections.length) ? sections[idx] : null;
                } else {
                    lastSection = null;
                }
            }

            if (checkSeenVoxel(
                    level, collisionContext, mpos, voxelCache,
                    minY, maxY, bx, by, bz, lastChunk, lastSection,
                    fromX, fromY, fromZ, rayDx, rayDy, rayDz,
                    parallelX, parallelY, parallelZ, invDx, invDy, invDz, signX, signY, signZ
            )) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkSeenVoxel(
            Level level,
            CollisionContext collisionContext,
            BlockPos.MutableBlockPos mpos,
            SeenVoxelCache voxelCache,
            int minY,
            int maxY,
            int bx,
            int by,
            int bz,
            @Nullable ChunkAccess chunk,
            @Nullable LevelChunkSection section,
            double fromX,
            double fromY,
            double fromZ,
            double rayDx,
            double rayDy,
            double rayDz,
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
        if (chunk == null || section == null || section.hasOnlyAir()) {
            return false;
        }
        VoxelShape shape;
        boolean canPack = by >= minY && by <= maxY
                && bx >= -30000000 && bz >= -30000000
                && bx < 30000000 && bz < 30000000;
        if (canPack) {
            long packed = BlockPos.asLong(bx, by, bz);
            int slot = (int) HashCommon.mix(packed) & SEEN_CACHE_MASK;
            if (voxelCache.keys[slot] == packed) {
                shape = voxelCache.shapes[slot];
            } else {
                BlockState state = section.getBlockState(bx & 15, by & 15, bz & 15);
                if (state.isAir()) {
                    shape = Shapes.empty();
                } else {
                    mpos.set(bx, by, bz);
                    shape = state.getCollisionShape(level, mpos, collisionContext);
                }
                voxelCache.keys[slot] = packed;
                voxelCache.shapes[slot] = shape;
            }
        } else {
            BlockState state = section.getBlockState(bx & 15, by & 15, bz & 15);
            if (state.isAir()) {
                return false;
            }
            mpos.set(bx, by, bz);
            shape = state.getCollisionShape(level, mpos, collisionContext);
        }

        if (shape == null || shape.isEmpty()) {
            return false;
        }
        return FastRayAABB.hitsVoxelShape(
                shape, bx, by, bz,
                fromX, fromY, fromZ, rayDx, rayDy, rayDz,
                parallelX, parallelY, parallelZ, invDx, invDy, invDz, signX, signY, signZ
        );
    }

    public static class ConsolidatedDropManager {
        public static class StackCollector {
            public final BlockPos pos;
            public ItemStack stack;

            public StackCollector(BlockPos pos, ItemStack stack) {
                this.pos = pos;
                this.stack = stack;
            }

            public boolean tryMerge(ItemStack input) {
                int limit = Math.min(16, this.stack.getMaxStackSize());
                if (this.stack.getCount() >= limit) {
                    return true;
                }
                if (ItemEntity.areMergable(this.stack, input)) {
                    this.stack = ItemEntity.merge(this.stack, input, limit);
                    return this.stack.getCount() >= limit;
                }
                return false;
            }
        }

        private final List<StackCollector> allCollectors = new ArrayList<>();
        private final Map<Item, List<StackCollector>> buckets = new IdentityHashMap<>();

        public void addStack(ItemStack stack, BlockPos pos) {
            if (stack == null || stack.isEmpty()) {
                return;
            }
            ItemStack working = stack.copy();
            List<StackCollector> list = this.buckets.computeIfAbsent(working.getItem(), k -> new ArrayList<>(2));
            for (int i = 0; i < list.size(); ) {
                StackCollector c = list.get(i);
                boolean full = c.tryMerge(working);
                if (full) {
                    int last = list.size() - 1;
                    if (i != last) {
                        list.set(i, list.get(last));
                    }
                    list.remove(last);
                } else {
                    i++;
                }
                if (working.isEmpty()) {
                    return;
                }
            }

            if (!working.isEmpty()) {
                StackCollector next = new StackCollector(pos.immutable(), working);
                this.allCollectors.add(next);
                if (next.stack.getCount() < Math.min(16, next.stack.getMaxStackSize())) {
                    list.add(next);
                }
            }
        }

        public void popAll(ServerLevel level) {
            for (int i = 0, len = this.allCollectors.size(); i < len; i++) {
                StackCollector c = this.allCollectors.get(i);
                Block.popResource(level, c.pos, c.stack);
            }
        }
    }
}
