package net.ashpapi.papioptimization.mixin.chunk;

import java.util.Set;
import net.ashpapi.papioptimization.chunk.PapiChunkHolderFastAccess;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ServerChunkCache.class, priority = 1500)
public abstract class ServerChunkCacheMixin {

    @Shadow @Final private Thread mainThread;
    @Shadow @Final private long[] lastChunkPos;
    @Shadow @Final private ChunkStatus[] lastChunkStatus;
    @Shadow @Final private ChunkAccess[] lastChunk;
    @Shadow @Final private Set<ChunkHolder> chunkHoldersToBroadcast;

    @Shadow
    private @Nullable ChunkHolder getVisibleChunkIfPresent(long chunkPos) {
        return null;
    }

    @Shadow
    private void storeInCache(long chunkPos, @Nullable ChunkAccess chunk, ChunkStatus status) {
    }

    @Inject(method = "broadcastChangedChunks", at = @At("HEAD"), cancellable = true)
    private void papi$skipEmptyBroadcast(ProfilerFiller profiler, CallbackInfo ci) {
        if (this.chunkHoldersToBroadcast.isEmpty()) {
            ci.cancel();
        }
    }

    @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetChunkNow(int chunkX, int chunkZ, CallbackInfoReturnable<LevelChunk> cir) {
        if (Thread.currentThread() != this.mainThread) {
            cir.setReturnValue(null);
            return;
        }
        long key = ChunkPos.pack(chunkX, chunkZ);
        long[] posCache = this.lastChunkPos;
        ChunkStatus[] statusCache = this.lastChunkStatus;
        ChunkAccess[] chunkCache = this.lastChunk;
        for (int i = 0; i < 4; i++) {
            if (posCache[i] == key && statusCache[i] == ChunkStatus.FULL) {
                ChunkAccess cached = chunkCache[i];
                cir.setReturnValue(cached instanceof LevelChunk lc ? lc : null);
                return;
            }
        }
        ChunkHolder holder = this.getVisibleChunkIfPresent(key);
        if (holder == null) {
            cir.setReturnValue(null);
            return;
        }
        LevelChunk full = ((PapiChunkHolderFastAccess) holder).papi$getFullChunkNow();
        if (full != null) {
            this.storeInCache(key, full, ChunkStatus.FULL);
            cir.setReturnValue(full);
            return;
        }
        LevelChunk loading = holder.currentlyLoading;
        if (loading != null) {
            cir.setReturnValue(loading);
            return;
        }
        cir.setReturnValue(null);
    }
}
