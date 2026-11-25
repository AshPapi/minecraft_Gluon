package net.ashpapi.papioptimization.ai;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;

public final class FastBlockSearchHelper {

    private static final ClassValue<Boolean> VANILLA_REMOVE_BLOCK_GOAL = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> cur = type; cur != null && cur != RemoveBlockGoal.class; cur = cur.getSuperclass()) {
                try {
                    cur.getDeclaredMethod("isValidTarget", LevelReader.class, BlockPos.class);
                    return Boolean.FALSE;
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable t) {
                    return Boolean.FALSE;
                }
            }
            return RemoveBlockGoal.class.isAssignableFrom(type);
        }
    };

    private FastBlockSearchHelper() {}

    public static boolean isVanillaRemoveBlockGoal(Class<?> cls) {
        return cls == RemoveBlockGoal.class || VANILLA_REMOVE_BLOCK_GOAL.get(cls);
    }

    public static BlockState getSectionState(ChunkAccess chunk, int x, int y, int z) {
        if (y < chunk.getMinY() || y > chunk.getMaxY()) {
            return Blocks.AIR.defaultBlockState();
        }
        int secIdx = chunk.getSectionIndex(y);
        LevelChunkSection[] sections = chunk.getSections();
        if (Integer.compareUnsigned(secIdx, sections.length) >= 0) {
            return Blocks.AIR.defaultBlockState();
        }
        LevelChunkSection section = sections[secIdx];
        if (section == null || section.hasOnlyAir()) {
            return Blocks.AIR.defaultBlockState();
        }
        return section.getBlockState(x & 15, y & 15, z & 15);
    }

    public static Optional<BlockPos> findNearestInManhattanBox(
            ServerLevel level,
            BlockPos origin,
            int rx,
            int ry,
            int rz,
            Predicate<BlockState> predicate
    ) {
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();

        int minY = Math.max(level.getMinY(), oy - ry);
        int maxY = Math.min(level.getMaxY(), oy + ry);
        if (minY > maxY) {
            return Optional.empty();
        }

        int minSecX = SectionPos.blockToSectionCoord(ox - rx);
        int maxSecX = SectionPos.blockToSectionCoord(ox + rx);
        int minSecY = SectionPos.blockToSectionCoord(minY);
        int maxSecY = SectionPos.blockToSectionCoord(maxY);
        int minSecZ = SectionPos.blockToSectionCoord(oz - rz);
        int maxSecZ = SectionPos.blockToSectionCoord(oz + rz);

        int xLen = maxSecX - minSecX + 1;
        int yLen = maxSecY - minSecY + 1;
        int zLen = maxSecZ - minSecZ + 1;
        ChunkAccess[] chunks = new ChunkAccess[xLen * zLen];
        boolean[] matchingSections = new boolean[xLen * zLen * yLen];
        boolean anyMatch = false;

        for (int ix = 0; ix < xLen; ix++) {
            int cx = minSecX + ix;
            for (int iz = 0; iz < zLen; iz++) {
                int cz = minSecZ + iz;
                ChunkAccess chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
                }
                int colIdx = ix * zLen + iz;
                chunks[colIdx] = chunk;
                if (chunk != null) {
                    LevelChunkSection[] sections = chunk.getSections();
                    for (int iy = 0; iy < yLen; iy++) {
                        int secIdx = chunk.getSectionIndexFromSectionY(minSecY + iy);
                        if (Integer.compareUnsigned(secIdx, sections.length) < 0) {
                            LevelChunkSection sec = sections[secIdx];
                            if (sec != null && !sec.hasOnlyAir() && sec.maybeHas(predicate)) {
                                matchingSections[colIdx * yLen + iy] = true;
                                anyMatch = true;
                            }
                        }
                    }
                }
            }
        }

        if (!anyMatch) {
            return Optional.empty();
        }

        int levelMinY = level.getMinY();
        int levelMaxY = level.getMaxY();
        for (BlockPos pos : BlockPos.withinBoxByManhattanDistance(origin, rx, ry, rz)) {
            int py = pos.getY();
            if (py < levelMinY || py > levelMaxY) {
                continue;
            }
            int px = pos.getX();
            int pz = pos.getZ();
            int ix = (px >> 4) - minSecX;
            int iz = (pz >> 4) - minSecZ;
            int iy = (py >> 4) - minSecY;
            int colIdx = ix * zLen + iz;
            if (!matchingSections[colIdx * yLen + iy]) {
                continue;
            }
            ChunkAccess chunk = chunks[colIdx];
            if (chunk != null) {
                BlockState state = getSectionState(chunk, px, py, pz);
                if (predicate.test(state)) {
                    return Optional.of(pos.immutable());
                }
            }
        }
        return Optional.empty();
    }

    public static @Nullable BlockPos searchMoveToBlockSpiral(
            PathfinderMob mob,
            int searchRange,
            int verticalSearchStart,
            int verticalSearchRange,
            int minDy,
            int maxDy,
            Predicate<BlockState> palettePredicate,
            PapiPaletteGoalTarget validator
    ) {
        Level level = mob.level();
        BlockPos origin = mob.blockPosition();
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();
        int horizRadius = searchRange - 1;

        int minY = Math.max(level.getMinY(), oy + minDy);
        int maxY = Math.min(level.getMaxY(), oy + maxDy);
        if (minY > maxY) {
            return null;
        }

        int minSecX = SectionPos.blockToSectionCoord(ox - horizRadius);
        int maxSecX = SectionPos.blockToSectionCoord(ox + horizRadius);
        int minSecY = SectionPos.blockToSectionCoord(minY);
        int maxSecY = SectionPos.blockToSectionCoord(maxY);
        int minSecZ = SectionPos.blockToSectionCoord(oz - horizRadius);
        int maxSecZ = SectionPos.blockToSectionCoord(oz + horizRadius);

        int xLen = maxSecX - minSecX + 1;
        int yLen = maxSecY - minSecY + 1;
        int zLen = maxSecZ - minSecZ + 1;

        ChunkAccess[] chunks = new ChunkAccess[xLen * zLen];
        boolean[] matchingSections = new boolean[xLen * zLen * yLen];
        boolean anyMatch = false;
        ServerLevel serverLevel = level instanceof ServerLevel sl ? sl : null;

        for (int ix = 0; ix < xLen; ix++) {
            int cx = minSecX + ix;
            for (int iz = 0; iz < zLen; iz++) {
                int cz = minSecZ + iz;
                ChunkAccess chunk = serverLevel != null
                        ? serverLevel.getChunkSource().getChunkNow(cx, cz)
                        : level.getChunk(cx, cz, ChunkStatus.FULL, false);
                int colIdx = ix * zLen + iz;
                chunks[colIdx] = chunk;
                if (chunk != null) {
                    LevelChunkSection[] sections = chunk.getSections();
                    for (int iy = 0; iy < yLen; iy++) {
                        int secIdx = chunk.getSectionIndexFromSectionY(minSecY + iy);
                        if (Integer.compareUnsigned(secIdx, sections.length) < 0) {
                            LevelChunkSection sec = sections[secIdx];
                            if (sec != null && !sec.hasOnlyAir() && sec.maybeHas(palettePredicate)) {
                                matchingSections[colIdx * yLen + iy] = true;
                                anyMatch = true;
                            }
                        }
                    }
                }
            }
        }

        if (!anyMatch) {
            return null;
        }

        int levelMinY = level.getMinY();
        int levelMaxY = level.getMaxY();
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        for (int k = verticalSearchStart; k <= verticalSearchRange; k = k > 0 ? -k : 1 - k) {
            int py = oy + k - 1;
            if (py < levelMinY || py > levelMaxY) {
                continue;
            }
            int iy = (py >> 4) - minSecY;
            for (int r = 0; r < searchRange; r++) {
                for (int dx = 0; dx <= r; dx = dx > 0 ? -dx : 1 - dx) {
                    int px = ox + dx;
                    int ix = (px >> 4) - minSecX;
                    for (int dz = (dx < r && dx > -r) ? r : 0; dz <= r; dz = dz > 0 ? -dz : 1 - dz) {
                        int pz = oz + dz;
                        int iz = (pz >> 4) - minSecZ;
                        int colIdx = ix * zLen + iz;
                        if (!matchingSections[colIdx * yLen + iy]) {
                            continue;
                        }
                        ChunkAccess chunk = chunks[colIdx];
                        if (chunk == null) {
                            continue;
                        }
                        mpos.set(px, py, pz);
                        if (mob.isWithinHome(mpos) && validator.papi$isValidCachedTarget(chunk, level, mpos, px, py, pz)) {
                            return mpos.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }
}
