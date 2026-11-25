package net.ashpapi.papioptimization.mixin.sculk;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.ashpapi.papioptimization.sculk.IChunkListenerAccess;
import net.ashpapi.papioptimization.sculk.PapiServerLevelListeners;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerLevel.class, priority = 1500)
public abstract class ServerLevelGameEventMixin implements PapiServerLevelListeners {

    @Unique
    private final Long2ObjectOpenHashMap<LevelChunk> papi$listenerChunks = new Long2ObjectOpenHashMap<>();

    @Override
    public void papi$registerListenerChunk(long chunkKey, LevelChunk chunk) {
        this.papi$listenerChunks.put(chunkKey, chunk);
    }

    @Override
    public void papi$unregisterListenerChunk(long chunkKey, LevelChunk chunk) {
        if (this.papi$listenerChunks.get(chunkKey) == chunk) {
            this.papi$listenerChunks.remove(chunkKey);
        }
    }

    @Override
    public boolean papi$hasNoListenerChunks() {
        return this.papi$listenerChunks.isEmpty();
    }

    @Override
    public @Nullable LevelChunk papi$getListenerChunk(long chunkKey) {
        return this.papi$listenerChunks.get(chunkKey);
    }

    @Inject(method = "startTickingChunk", at = @At("RETURN"))
    private void papi$syncListenerChunkOnTickStart(LevelChunk chunk, CallbackInfo ci) {
        if (chunk instanceof IChunkListenerAccess access && access.papi$hasAnyListenerRegistries()) {
            this.papi$listenerChunks.put(chunk.getPos().pack(), chunk);
        }
    }

    @Inject(method = "unload", at = @At("HEAD"))
    private void papi$removeListenerChunkOnUnload(LevelChunk chunk, CallbackInfo ci) {
        long key = chunk.getPos().pack();
        if (this.papi$listenerChunks.get(key) == chunk) {
            this.papi$listenerChunks.remove(key);
        }
    }
}
