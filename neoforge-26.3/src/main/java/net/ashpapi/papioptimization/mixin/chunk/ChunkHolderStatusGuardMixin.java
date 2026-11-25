package net.ashpapi.papioptimization.mixin.chunk;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GenerationChunkHolder.class, priority = 1500)
public abstract class ChunkHolderStatusGuardMixin {

    @Shadow @Final private AtomicReferenceArray<CompletableFuture<ChunkResult<ChunkAccess>>> futures;

    @Shadow private volatile ChunkStatus highestAllowedStatus;

    @Inject(method = "scheduleChunkGenerationTask", at = @At("HEAD"), cancellable = true)
    private void papi$fastCompletedFutureCheck(
            ChunkStatus status,
            ChunkMap chunkMap,
            CallbackInfoReturnable<CompletableFuture<ChunkResult<ChunkAccess>>> cir
    ) {
        ChunkStatus allowed = this.highestAllowedStatus;
        if (allowed == null || status.isAfter(allowed)) {
            cir.setReturnValue(GenerationChunkHolder.UNLOADED_CHUNK_FUTURE);
            return;
        }
        CompletableFuture<ChunkResult<ChunkAccess>> existing = this.futures.get(status.getIndex());
        if (existing != null && existing.isDone()) {
            cir.setReturnValue(existing);
        }
    }
}
