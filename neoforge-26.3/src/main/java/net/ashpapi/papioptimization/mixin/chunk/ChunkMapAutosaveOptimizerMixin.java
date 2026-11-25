package net.ashpapi.papioptimization.mixin.chunk;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.ashpapi.papioptimization.server.PapiAutosaveState;
import net.ashpapi.papioptimization.server.PapiMsptController;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChunkMap.class, priority = 1500)
public abstract class ChunkMapAutosaveOptimizerMixin {

    @Unique
    private static final int PAPI$TRIM_PEAK_THRESHOLD = 1024;

    @Shadow
    private volatile Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap;

    @Shadow
    @Final
    private Long2LongMap nextChunkSaveTime;

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    private boolean saveChunkIfNeeded(ChunkHolder chunk, long now) {
        throw new AssertionError();
    }

    @Unique
    private final LongArrayFIFOQueue papi$autosaveQueue = new LongArrayFIFOQueue();

    @Unique
    private final LongOpenHashSet papi$queuedPositions = new LongOpenHashSet();

    @Unique
    private int papi$peakQueueSize;

    @Inject(method = "saveAllChunks", at = @At("HEAD"), cancellable = true)
    private void papi$onSaveAllChunks(boolean flushStorage, CallbackInfo ci) {
        if (flushStorage || !PapiAutosaveState.isInAutoSave()) {
            this.papi$autosaveQueue.clear();
            this.papi$queuedPositions.clear();
            this.papi$peakQueueSize = 0;
            return;
        }
        ci.cancel();
        for (ChunkHolder holder : this.visibleChunkMap.values()) {
            if (holder.wasAccessibleSinceLastSave() && holder.isReadyForSaving()) {
                ChunkAccess chunk = holder.getLatestChunk();
                if ((chunk instanceof ImposterProtoChunk || chunk instanceof LevelChunk) && chunk.isUnsaved()) {
                    long packed = holder.getPos().pack();
                    if (this.papi$queuedPositions.add(packed)) {
                        this.papi$autosaveQueue.enqueue(packed);
                    }
                }
            }
        }
        int qSize = this.papi$autosaveQueue.size();
        if (qSize > this.papi$peakQueueSize) {
            this.papi$peakQueueSize = qSize;
        }
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void papi$processPacedAutosave(CallbackInfo ci) {
        if (this.papi$autosaveQueue.isEmpty()) {
            return;
        }
        if (this.level.noSave()) {
            this.papi$autosaveQueue.clear();
            this.papi$queuedPositions.clear();
            this.papi$peakQueueSize = 0;
            return;
        }

        long now = Util.getMillis();
        int currentSize = this.papi$autosaveQueue.size();
        if (currentSize > this.papi$peakQueueSize) {
            this.papi$peakQueueSize = currentSize;
        }
        int maxPerTick = PapiMsptController.getAutosaveBatchSize(currentSize);
        int processed = 0;
        while (!this.papi$autosaveQueue.isEmpty() && processed < maxPerTick) {
            long pos = this.papi$autosaveQueue.dequeueLong();
            this.papi$queuedPositions.remove(pos);
            ChunkHolder holder = this.visibleChunkMap.get(pos);
            if (holder != null && holder.wasAccessibleSinceLastSave() && holder.isReadyForSaving()) {
                ChunkAccess chunk = holder.getLatestChunk();
                if ((chunk instanceof ImposterProtoChunk || chunk instanceof LevelChunk) && chunk.isUnsaved()) {
                    this.nextChunkSaveTime.remove(pos);
                    if (this.saveChunkIfNeeded(holder, now)) {
                        processed++;
                    }
                }
            }
        }
        if (this.papi$autosaveQueue.isEmpty()) {
            if (this.papi$peakQueueSize > PAPI$TRIM_PEAK_THRESHOLD) {
                this.papi$autosaveQueue.trim();
                this.papi$queuedPositions.trim();
            }
            this.papi$peakQueueSize = 0;
        }
    }
}
