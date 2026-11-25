package net.ashpapi.papioptimization.ai;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;

public final class SectionBlockCounter {

    public record PatternRequirement(Predicate<BlockState> statePredicate, int minCount) {}

    private static final class ProbeTouchedWorldException extends RuntimeException {
        static final ProbeTouchedWorldException INSTANCE = new ProbeTouchedWorldException();

        private ProbeTouchedWorldException() {
            super(null, null, false, false);
        }
    }

    private static final class StateOnlyProbe extends BlockInWorld {
        private BlockState currentState = Blocks.AIR.defaultBlockState();

        StateOnlyProbe() {
            super(null, BlockPos.ZERO, false);
        }

        StateOnlyProbe with(BlockState state) {
            this.currentState = state;
            return this;
        }

        @Override
        public BlockState getState() {
            return this.currentState;
        }

        @Override
        public @Nullable BlockEntity getEntity() {
            throw ProbeTouchedWorldException.INSTANCE;
        }

        @Override
        public LevelReader getLevel() {
            throw ProbeTouchedWorldException.INSTANCE;
        }

        @Override
        public BlockPos getPos() {
            throw ProbeTouchedWorldException.INSTANCE;
        }
    }

    private static final ThreadLocal<StateOnlyProbe> PROBE_LOCAL = ThreadLocal.withInitial(StateOnlyProbe::new);

    private SectionBlockCounter() {}

    public static @Nullable PatternRequirement analyzePattern(Predicate<BlockInWorld>[][][] pattern) {
        if (pattern == null || pattern.length == 0) {
            return null;
        }
        Reference2IntOpenHashMap<Predicate<BlockInWorld>> counts = new Reference2IntOpenHashMap<>();
        for (Predicate<BlockInWorld>[][] slice : pattern) {
            if (slice == null) {
                continue;
            }
            for (Predicate<BlockInWorld>[] row : slice) {
                if (row == null) {
                    continue;
                }
                for (Predicate<BlockInWorld> cell : row) {
                    if (cell != null) {
                        counts.addTo(cell, 1);
                    }
                }
            }
        }
        if (counts.isEmpty()) {
            return null;
        }
        StateOnlyProbe probe = PROBE_LOCAL.get();
        Predicate<BlockInWorld> bestPred = null;
        int bestCount = 0;

        for (Reference2IntMap.Entry<Predicate<BlockInWorld>> entry : counts.reference2IntEntrySet()) {
            int count = entry.getIntValue();
            if (count <= bestCount) {
                continue;
            }
            Predicate<BlockInWorld> pred = entry.getKey();
            try {
                if (pred.test(probe.with(Blocks.AIR.defaultBlockState()))
                        || pred.test(probe.with(Blocks.CAVE_AIR.defaultBlockState()))
                        || pred.test(probe.with(Blocks.VOID_AIR.defaultBlockState()))) {
                    continue;
                }
                bestPred = pred;
                bestCount = count;
            } catch (Throwable ignored) {
            }
        }

        if (bestPred == null || bestCount <= 0) {
            return null;
        }
        final Predicate<BlockInWorld> chosen = bestPred;
        Predicate<BlockState> statePredicate = state -> chosen.test(PROBE_LOCAL.get().with(state));
        return new PatternRequirement(statePredicate, bestCount);
    }

    public static boolean hasEnoughBlocks(
            LevelReader level,
            BlockPos startPos,
            int maxDim,
            PatternRequirement req
    ) {
        if (maxDim <= 0 || req == null || req.minCount() <= 0) {
            return true;
        }
        try {
            int span = maxDim - 1;
            int minX = startPos.getX() - span;
            int maxX = startPos.getX() + (span << 1);
            int minY = Math.max(level.getMinY(), startPos.getY() - span);
            int maxY = Math.min(level.getMaxY(), startPos.getY() + (span << 1));
            int minZ = startPos.getZ() - span;
            int maxZ = startPos.getZ() + (span << 1);

            if (minY > maxY) {
                return false;
            }

            int minSecX = SectionPos.blockToSectionCoord(minX);
            int maxSecX = SectionPos.blockToSectionCoord(maxX);
            int minSecY = SectionPos.blockToSectionCoord(minY);
            int maxSecY = SectionPos.blockToSectionCoord(maxY);
            int minSecZ = SectionPos.blockToSectionCoord(minZ);
            int maxSecZ = SectionPos.blockToSectionCoord(maxZ);

            Predicate<BlockState> pred = req.statePredicate();
            int needed = req.minCount();
            int found = 0;
            ServerLevel serverLevel = level instanceof ServerLevel sl ? sl : null;

            for (int cx = minSecX; cx <= maxSecX; cx++) {
                int bx0 = Math.max(minX, cx << 4) & 15;
                int bx1 = Math.min(maxX, (cx << 4) + 15) & 15;
                for (int cz = minSecZ; cz <= maxSecZ; cz++) {
                    ChunkAccess chunk = serverLevel != null
                            ? serverLevel.getChunkSource().getChunkNow(cx, cz)
                            : level.getChunk(cx, cz, ChunkStatus.FULL, false);
                    if (chunk == null) {
                        return true;
                    }
                    int bz0 = Math.max(minZ, cz << 4) & 15;
                    int bz1 = Math.min(maxZ, (cz << 4) + 15) & 15;
                    LevelChunkSection[] sections = chunk.getSections();
                    for (int cy = minSecY; cy <= maxSecY; cy++) {
                        int secIdx = chunk.getSectionIndexFromSectionY(cy);
                        if (Integer.compareUnsigned(secIdx, sections.length) >= 0) {
                            continue;
                        }
                        LevelChunkSection section = sections[secIdx];
                        if (section == null || section.hasOnlyAir() || !section.maybeHas(pred)) {
                            continue;
                        }
                        int by0 = Math.max(minY, cy << 4) & 15;
                        int by1 = Math.min(maxY, (cy << 4) + 15) & 15;
                        for (int ly = by0; ly <= by1; ly++) {
                            for (int lz = bz0; lz <= bz1; lz++) {
                                for (int lx = bx0; lx <= bx1; lx++) {
                                    if (pred.test(section.getBlockState(lx, ly, lz))) {
                                        found++;
                                        if (found >= needed) {
                                            return true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return false;
        } catch (Throwable ignored) {
            return true;
        }
    }
}
