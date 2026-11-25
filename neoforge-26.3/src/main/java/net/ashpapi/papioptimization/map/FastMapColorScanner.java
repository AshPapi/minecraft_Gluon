package net.ashpapi.papioptimization.map;

import java.util.Arrays;
import net.ashpapi.papioptimization.core.BlockPosPool;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class FastMapColorScanner {

    private static final int MAX_CASCADE_COLUMNS = 4;

    private static final ThreadLocal<int[]> COLOR_COUNTS = ThreadLocal.withInitial(() -> new int[64]);

    private FastMapColorScanner() {}

    public static void updateMap(Level level, Entity player, MapItemSavedData data) {
        if (level.dimension() != data.dimension || !(player instanceof Player player2)) {
            return;
        }

        final int scale = 1 << data.scale;
        final int centerX = data.centerX;
        final int centerZ = data.centerZ;
        final int playerImgX = Mth.floor(player.getX() - (double) centerX) / scale + 64;
        final int playerImgY = Mth.floor(player.getZ() - (double) centerZ) / scale + 64;
        final boolean hasCeiling = level.dimensionType().hasCeiling();
        int radius = 128 / scale;
        if (hasCeiling) {
            radius /= 2;
        }

        final MapItemSavedData.HoldingPlayer holdingPlayer = data.getHoldingPlayer(player2);
        ++holdingPlayer.step;

        final boolean hasBanners = !data.getBanners().isEmpty();
        final MapColor ceilingDirtColor = hasCeiling
                ? Blocks.DIRT.defaultBlockState().getMapColor(level, BlockPos.ZERO)
                : MapColor.NONE;
        final MapColor ceilingStoneColor = hasCeiling
                ? Blocks.STONE.defaultBlockState().getMapColor(level, BlockPos.ZERO)
                : MapColor.NONE;

        int[] counts = COLOR_COUNTS.get();

        int cascadeColumnsProcessed = 0;
        boolean foundConsecutiveChanges = false;

        LevelChunk cachedChunk = null;
        int cachedChunkX = Integer.MIN_VALUE;
        int cachedChunkZ = Integer.MIN_VALUE;

        final int minImgX = Math.max(0, playerImgX - radius + 1);
        final int maxImgX = Math.min(127, playerImgX + radius - 1);
        final int minImgY = Math.max(-1, playerImgY - radius - 1);
        final int maxImgY = Math.min(127, playerImgY + radius - 1);

        int poolMark = BlockPosPool.mark();
        try {
            final BlockPos.MutableBlockPos blockPos = BlockPosPool.get();
            final BlockPos.MutableBlockPos belowPos = BlockPosPool.get();

            for (int imgX = minImgX; imgX <= maxImgX; ++imgX) {
                boolean baseStep = (imgX & 0xF) == (holdingPlayer.step & 0xF);
                if (!baseStep) {
                    if (!foundConsecutiveChanges || cascadeColumnsProcessed >= MAX_CASCADE_COLUMNS) {
                        continue;
                    }
                    cascadeColumnsProcessed++;
                } else {
                    cascadeColumnsProcessed = 0;
                }
                foundConsecutiveChanges = false;

                double previousAverageAreaHeight = 0.0;

                for (int imgY = minImgY; imgY <= maxImgY; ++imgY) {
                    final int distanceToPlayerSqr = Mth.square(imgX - playerImgX) + Mth.square(imgY - playerImgY);
                    final boolean ditherBlack = distanceToPlayerSqr > (radius - 2) * (radius - 2);
                    final int averagingAreaMinX = (centerX / scale + imgX - 64) * scale;
                    final int averagingAreaMinZ = (centerZ / scale + imgY - 64) * scale;

                    final int chunkSectionX = SectionPos.blockToSectionCoord(averagingAreaMinX);
                    final int chunkSectionZ = SectionPos.blockToSectionCoord(averagingAreaMinZ);

                    if (cachedChunk == null || cachedChunkX != chunkSectionX || cachedChunkZ != chunkSectionZ) {
                        cachedChunkX = chunkSectionX;
                        cachedChunkZ = chunkSectionZ;
                        cachedChunk = level.getChunkSource().getChunk(chunkSectionX, chunkSectionZ, false);
                    }

                    if (cachedChunk == null || cachedChunk.isEmpty()) {
                        continue;
                    }

                    int waterDepth = 0;
                    double averageAreaHeight = 0.0;
                    MapColor color;

                    if (hasCeiling) {
                        int ceilingNoise = averagingAreaMinX + averagingAreaMinZ * 231871;
                        ceilingNoise = ceilingNoise * ceilingNoise * 31287121 + ceilingNoise * 11;
                        color = ((ceilingNoise >> 20) & 1) == 0 ? ceilingDirtColor : ceilingStoneColor;
                        averageAreaHeight = 100.0;
                    } else if (scale == 1) {
                        blockPos.set(averagingAreaMinX, 0, averagingAreaMinZ);
                        int columnY = cachedChunk.getHeight(Heightmap.Types.WORLD_SURFACE, blockPos.getX(), blockPos.getZ()) + 1;
                        BlockState state;
                        if (columnY <= level.getMinY()) {
                            state = Blocks.BEDROCK.defaultBlockState();
                        } else {
                            do {
                                blockPos.setY(--columnY);
                            } while ((state = cachedChunk.getBlockState(blockPos)).getMapColor(level, blockPos) == MapColor.NONE
                                    && columnY > level.getMinY());

                            if (columnY > level.getMinY() && !state.getFluidState().isEmpty()) {
                                int solidY = columnY - 1;
                                belowPos.set(blockPos);
                                BlockState belowBlock;
                                do {
                                    belowPos.setY(solidY--);
                                    belowBlock = cachedChunk.getBlockState(belowPos);
                                    ++waterDepth;
                                } while (solidY > level.getMinY() && !belowBlock.getFluidState().isEmpty());
                                state = getCorrectStateForFluidBlock(level, state, blockPos);
                            }
                        }

                        if (hasBanners) {
                            data.checkBanners(level, blockPos.getX(), blockPos.getZ());
                        }
                        averageAreaHeight = columnY;
                        color = state.getMapColor(level, blockPos);
                    } else {
                        Arrays.fill(counts, 0);
                        int maxCount = 0;
                        MapColor maxColor = MapColor.NONE;

                        for (int averagingAreaDeltaX = 0; averagingAreaDeltaX < scale; ++averagingAreaDeltaX) {
                            int bx = averagingAreaMinX + averagingAreaDeltaX;
                            int cx = SectionPos.blockToSectionCoord(bx);
                            for (int averagingAreaDeltaZ = 0; averagingAreaDeltaZ < scale; ++averagingAreaDeltaZ) {
                                int bz = averagingAreaMinZ + averagingAreaDeltaZ;
                                int cz = SectionPos.blockToSectionCoord(bz);
                                if (cachedChunk == null || cachedChunkX != cx || cachedChunkZ != cz) {
                                    cachedChunkX = cx;
                                    cachedChunkZ = cz;
                                    cachedChunk = level.getChunkSource().getChunk(cx, cz, false);
                                }
                                if (cachedChunk == null || cachedChunk.isEmpty()) {
                                    continue;
                                }
                                blockPos.set(bx, 0, bz);
                                int columnY = cachedChunk.getHeight(Heightmap.Types.WORLD_SURFACE, blockPos.getX(), blockPos.getZ()) + 1;
                                BlockState state;
                                if (columnY <= level.getMinY()) {
                                    state = Blocks.BEDROCK.defaultBlockState();
                                } else {
                                    do {
                                        blockPos.setY(--columnY);
                                    } while ((state = cachedChunk.getBlockState(blockPos)).getMapColor(level, blockPos) == MapColor.NONE
                                            && columnY > level.getMinY());

                                    if (columnY > level.getMinY() && !state.getFluidState().isEmpty()) {
                                        int solidY = columnY - 1;
                                        belowPos.set(blockPos);
                                        BlockState belowBlock;
                                        do {
                                            belowPos.setY(solidY--);
                                            belowBlock = cachedChunk.getBlockState(belowPos);
                                            ++waterDepth;
                                        } while (solidY > level.getMinY() && !belowBlock.getFluidState().isEmpty());
                                        state = getCorrectStateForFluidBlock(level, state, blockPos);
                                    }
                                }

                                if (hasBanners) {
                                    data.checkBanners(level, blockPos.getX(), blockPos.getZ());
                                }
                                averageAreaHeight += (double) columnY / (double) (scale * scale);

                                MapColor c = state.getMapColor(level, blockPos);
                                if (c.id < 0) {
                                    continue;
                                }
                                if (c.id >= counts.length) {
                                    counts = Arrays.copyOf(counts, Math.max(counts.length << 1, c.id + 1));
                                    COLOR_COUNTS.set(counts);
                                }
                                int count = ++counts[c.id];
                                if (count > maxCount) {
                                    maxCount = count;
                                    maxColor = c;
                                }
                            }
                        }
                        color = maxColor;
                    }

                    double diff;
                    MapColor.Brightness brightness;
                    if (color == MapColor.WATER) {
                        diff = (double) waterDepth / (double) (scale * scale) * 0.1 + (double) (imgX + imgY & 1) * 0.2;
                        brightness = diff < 0.5 ? MapColor.Brightness.HIGH : (diff > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL);
                    } else {
                        diff = (averageAreaHeight - previousAverageAreaHeight) * 4.0 / (double) (scale + 4)
                                + ((double) (imgX + imgY & 1) - 0.5) * 0.4;
                        brightness = diff > 0.6 ? MapColor.Brightness.HIGH : (diff < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL);
                    }
                    previousAverageAreaHeight = averageAreaHeight;

                    if (imgY < 0 || distanceToPlayerSqr >= radius * radius || (ditherBlack && (imgX + imgY & 1) == 0)) {
                        continue;
                    }

                    foundConsecutiveChanges |= data.updateColor(imgX, imgY, color.getPackedId(brightness));
                }
            }
        } finally {
            BlockPosPool.reset(poolMark);
        }
    }

    private static BlockState getCorrectStateForFluidBlock(Level level, BlockState state, BlockPos pos) {
        FluidState fluidState = state.getFluidState();
        return !fluidState.isEmpty() && !state.isFaceSturdy(level, pos, Direction.UP) ? fluidState.createLegacyBlock() : state;
    }
}
