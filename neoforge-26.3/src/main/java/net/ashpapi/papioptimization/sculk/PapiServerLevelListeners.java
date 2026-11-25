package net.ashpapi.papioptimization.sculk;

import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

public interface PapiServerLevelListeners {
    void papi$registerListenerChunk(long chunkKey, LevelChunk chunk);

    void papi$unregisterListenerChunk(long chunkKey, LevelChunk chunk);

    boolean papi$hasNoListenerChunks();

    @Nullable LevelChunk papi$getListenerChunk(long chunkKey);
}
