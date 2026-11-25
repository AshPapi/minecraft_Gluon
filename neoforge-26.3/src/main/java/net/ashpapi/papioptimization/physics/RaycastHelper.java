package net.ashpapi.papioptimization.physics;

import net.ashpapi.papioptimization.mixin.physics.ClipContextAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public final class RaycastHelper {

    private RaycastHelper() {
    }

    public static BlockHitResult clipFast(Level level, ClipContext context) {
        Vec3 from = context.getFrom();
        Vec3 to = context.getTo();
        if (from.equals(to)) {
            return createMiss(from, to);
        }

        double endX = Mth.lerp(-1.0E-7, to.x, from.x);
        double endY = Mth.lerp(-1.0E-7, to.y, from.y);
        double endZ = Mth.lerp(-1.0E-7, to.z, from.z);
        double startX = Mth.lerp(-1.0E-7, from.x, to.x);
        double startY = Mth.lerp(-1.0E-7, from.y, to.y);
        double startZ = Mth.lerp(-1.0E-7, from.z, to.z);

        int bx = Mth.floor(startX);
        int by = Mth.floor(startY);
        int bz = Mth.floor(startZ);

        boolean ignoreFluids = ((ClipContextAccessor) context).papi$getFluid() == ClipContext.Fluid.NONE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(bx, by, bz);

        int lastCx = Integer.MIN_VALUE;
        int lastCz = Integer.MIN_VALUE;
        ChunkAccess lastChunk = null;
        int lastSecY = Integer.MIN_VALUE;
        LevelChunkSection lastSection = null;

        int cx = bx >> 4;
        int cz = bz >> 4;
        lastCx = cx;
        lastCz = cz;
        lastChunk = level.getChunkSource().getChunkNow(cx, cz);
        if (lastChunk != null) {
            int secY = by >> 4;
            lastSecY = secY;
            int idx = lastChunk.getSectionIndexFromSectionY(secY);
            LevelChunkSection[] sections = lastChunk.getSections();
            lastSection = (idx >= 0 && idx < sections.length) ? sections[idx] : null;
        }

        BlockHitResult initialHit = stepVoxel(level, context, from, to, pos, bx, by, bz, lastChunk, lastSection, ignoreFluids);
        if (initialHit != null) {
            return initialHit;
        }

        double dx = endX - startX;
        double dy = endY - startY;
        double dz = endZ - startZ;
        int stepX = Mth.sign(dx);
        int stepY = Mth.sign(dy);
        int stepZ = Mth.sign(dz);
        double tDeltaX = stepX == 0 ? Double.MAX_VALUE : (double) stepX / dx;
        double tDeltaY = stepY == 0 ? Double.MAX_VALUE : (double) stepY / dy;
        double tDeltaZ = stepZ == 0 ? Double.MAX_VALUE : (double) stepZ / dz;
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

            BlockHitResult hit = stepVoxel(level, context, from, to, pos, bx, by, bz, lastChunk, lastSection, ignoreFluids);
            if (hit != null) {
                return hit;
            }
        }

        return createMiss(from, to);
    }

    private static @Nullable BlockHitResult stepVoxel(
            Level level,
            ClipContext context,
            Vec3 from,
            Vec3 to,
            BlockPos.MutableBlockPos pos,
            int bx,
            int by,
            int bz,
            @Nullable ChunkAccess chunk,
            @Nullable LevelChunkSection section,
            boolean ignoreFluids
    ) {
        if (chunk == null || section == null || section.hasOnlyAir()) {
            return null;
        }
        BlockState state = section.getBlockState(bx & 15, by & 15, bz & 15);
        if (state.isAir() && ignoreFluids) {
            return null;
        }
        pos.set(bx, by, bz);
        VoxelShape blockShape = state.isAir() ? net.minecraft.world.phys.shapes.Shapes.empty() : context.getBlockShape(state, level, pos);
        BlockHitResult blockHit = blockShape.isEmpty() ? null : level.clipWithInteractionOverride(from, to, pos, blockShape, state);

        if (ignoreFluids) {
            return blockHit;
        }

        FluidState fluidState = state.getFluidState();
        if (fluidState.isEmpty()) {
            return blockHit;
        }
        VoxelShape fluidShape = context.getFluidShape(fluidState, level, pos);
        BlockHitResult fluidHit = fluidShape.isEmpty() ? null : fluidShape.clip(from, to, pos);
        if (fluidHit == null) {
            return blockHit;
        }
        if (blockHit == null) {
            return fluidHit;
        }
        double blockDistSqr = from.distanceToSqr(blockHit.getLocation());
        double fluidDistSqr = from.distanceToSqr(fluidHit.getLocation());
        return blockDistSqr <= fluidDistSqr ? blockHit : fluidHit;
    }

    private static BlockHitResult createMiss(Vec3 from, Vec3 to) {
        Vec3 diff = from.subtract(to);
        return BlockHitResult.miss(to, Direction.getApproximateNearest(diff.x, diff.y, diff.z), BlockPos.containing(to));
    }
}
