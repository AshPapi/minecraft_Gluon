package net.ashpapi.papioptimization.chunk;

import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

public interface PapiChunkHolderFastAccess {
    @Nullable LevelChunk papi$getFullChunkNow();
}
