package net.ashpapi.papioptimization.mixin.chunk;

import java.util.concurrent.CompletableFuture;
import net.ashpapi.papioptimization.chunk.PapiChunkHolderFastAccess;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ChunkHolder.class, priority = 1500)
public abstract class ChunkHolderAccessMixin implements PapiChunkHolderFastAccess {

    @Shadow
    private volatile CompletableFuture<ChunkResult<LevelChunk>> fullChunkFuture;

    @Override
    public @Nullable LevelChunk papi$getFullChunkNow() {
        CompletableFuture<ChunkResult<LevelChunk>> future = this.fullChunkFuture;
        if (future == null) {
            return null;
        }
        ChunkResult<LevelChunk> res = future.getNow(null);
        return res != null ? res.orElse(null) : null;
    }
}
