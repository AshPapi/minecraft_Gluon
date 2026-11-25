package net.ashpapi.papioptimization.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

public final class FastWeatherHandler {

    private FastWeatherHandler() {}

    public static void tickPrecipitationFast(ServerLevel level, BlockPos pos) {
        BlockPos topPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
        BlockPos belowPos = topPos.below();
        BlockState belowState = level.getBlockState(belowPos);

        int bx = belowPos.getX() & 15;
        int bz = belowPos.getZ() & 15;
        boolean safeInsideChunk = bx > 0 && bx < 15 && bz > 0 && bz < 15;
        boolean canCheckFreeze = belowState.getBlock() instanceof LiquidBlock
                && belowState.getFluidState().getType() == Fluids.WATER
                && (safeInsideChunk || level.isAreaLoaded(belowPos, 1));

        boolean raining = level.isRaining();
        if (!canCheckFreeze && !raining) {
            return;
        }

        Biome biome = level.getBiome(topPos).value();

        if (canCheckFreeze && biome.shouldFreeze(level, belowPos)) {
            BlockState iceState = Blocks.ICE.defaultBlockState();
            level.setBlockAndUpdate(belowPos, iceState);
            belowState = iceState;
        }

        if (raining) {
            int maxHeight = level.getGameRules().get(GameRules.MAX_SNOW_ACCUMULATION_HEIGHT);
            if (maxHeight > 0 && biome.shouldSnow(level, topPos)) {
                BlockState state = level.getBlockState(topPos);
                if (state.is(Blocks.SNOW)) {
                    int currentLayers = state.getValue(SnowLayerBlock.LAYERS);
                    if (currentLayers < Math.min(maxHeight, 8)) {
                        BlockState newState = state.setValue(SnowLayerBlock.LAYERS, currentLayers + 1);
                        Block.pushEntitiesUp(state, newState, level, topPos);
                        level.setBlockAndUpdate(topPos, newState);
                    }
                } else {
                    level.setBlockAndUpdate(topPos, Blocks.SNOW.defaultBlockState());
                }
            }

            Biome.Precipitation precipitation = biome.getPrecipitationAt(belowPos, level.getSeaLevel());
            if (precipitation != Biome.Precipitation.NONE) {
                belowState.getBlock().handlePrecipitation(belowState, level, belowPos, precipitation);
            }
        }
    }
}
