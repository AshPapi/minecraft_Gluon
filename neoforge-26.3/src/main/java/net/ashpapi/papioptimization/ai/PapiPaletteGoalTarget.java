package net.ashpapi.papioptimization.ai;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jspecify.annotations.Nullable;

public interface PapiPaletteGoalTarget {
    @Nullable Predicate<BlockState> papi$getPalettePredicate();
    boolean papi$isValidCachedTarget(
            ChunkAccess chunk,
            LevelReader level,
            BlockPos.MutableBlockPos scratchPos,
            int x,
            int y,
            int z
    );
}
