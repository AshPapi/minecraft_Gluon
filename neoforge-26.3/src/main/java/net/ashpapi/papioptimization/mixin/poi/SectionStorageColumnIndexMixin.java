package net.ashpapi.papioptimization.mixin.poi;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.Optional;
import net.ashpapi.papioptimization.access.PapiPoiSectionFast;
import net.ashpapi.papioptimization.ai.PapiPoiColumnIndex;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SectionStorage.class, priority = 1500)
public abstract class SectionStorageColumnIndexMixin<R, P> implements PapiPoiColumnIndex<R> {

    @Shadow
    @Final
    private Long2ObjectMap<Optional<R>> storage;

    @Shadow
    @Final
    protected LevelHeightAccessor levelHeightAccessor;

    @Unique
    private Long2LongOpenHashMap papi$columnMasks;

    @Unique
    private int papi$minSectionY;

    @Unique
    private boolean papi$bitIndexSupported;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$initColumnIndex(CallbackInfo ci) {
        int minSec = this.levelHeightAccessor.getMinSectionY();
        int maxSec = this.levelHeightAccessor.getMaxSectionY();
        int count = maxSec - minSec + 1;
        this.papi$minSectionY = minSec;
        this.papi$bitIndexSupported = count > 0 && count <= 63;
        Long2LongOpenHashMap map = new Long2LongOpenHashMap();
        map.defaultReturnValue(-1L);
        this.papi$columnMasks = map;
    }

    @Override
    public long papi$getColumnNonEmptyMask(long chunkKey) {
        if (!this.papi$bitIndexSupported || this.papi$columnMasks == null) {
            return -1L;
        }
        return this.papi$columnMasks.get(chunkKey);
    }

    @Override
    public void papi$setSectionNonEmpty(long chunkKey, int bit, boolean nonEmpty) {
        if (!this.papi$bitIndexSupported || this.papi$columnMasks == null || Integer.compareUnsigned(bit, 63) >= 0) {
            return;
        }
        long current = this.papi$columnMasks.get(chunkKey);
        if (current < 0L) {
            return;
        }
        long flag = 1L << bit;
        long updated = nonEmpty ? (current | flag) : (current & ~flag);
        if (updated != current) {
            this.papi$columnMasks.put(chunkKey, updated);
        }
    }

    @Override
    public Optional<R> papi$getLoadedSectionDirect(long sectionKey) {
        return this.storage.get(sectionKey);
    }

    @Inject(
        method = "unpackChunk(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/storage/SectionStorage$PackedChunk;)V",
        at = @At("RETURN")
    )
    private void papi$onUnpackChunk(ChunkPos pos, @Coerce Object packedChunk, CallbackInfo ci) {
        if (!this.papi$bitIndexSupported || this.papi$columnMasks == null) {
            return;
        }
        int cx = pos.x();
        int cz = pos.z();
        long chunkKey = pos.pack();
        int minSec = this.papi$minSectionY;
        int maxSec = this.levelHeightAccessor.getMaxSectionY();
        long mask = 0L;
        for (int secY = minSec; secY <= maxSec; secY++) {
            int bit = secY - minSec;
            Optional<R> opt = this.storage.get(SectionPos.asLong(cx, secY, cz));
            if (opt != null && opt.isPresent()) {
                R section = opt.get();
                if (section instanceof PapiPoiSectionFast poiSec) {
                    poiSec.papi$bindColumnIndex(this, chunkKey, bit);
                    if (!poiSec.papi$isEmpty()) {
                        mask |= (1L << bit);
                    }
                }
            }
        }
        this.papi$columnMasks.put(chunkKey, mask);
    }

    @Inject(method = "getOrCreate", at = @At("RETURN"))
    private void papi$onGetOrCreate(long sectionKey, CallbackInfoReturnable<R> cir) {
        if (!this.papi$bitIndexSupported || this.papi$columnMasks == null) {
            return;
        }
        R section = cir.getReturnValue();
        if (section instanceof PapiPoiSectionFast poiSec) {
            int bit = SectionPos.y(sectionKey) - this.papi$minSectionY;
            if (Integer.compareUnsigned(bit, 63) < 0) {
                long chunkKey = ChunkPos.pack(SectionPos.x(sectionKey), SectionPos.z(sectionKey));
                poiSec.papi$bindColumnIndex(this, chunkKey, bit);
                if (!poiSec.papi$isEmpty()) {
                    papi$setSectionNonEmpty(chunkKey, bit, true);
                }
            }
        }
    }

    @Inject(method = "remove", at = @At("RETURN"))
    private void papi$onRemoveChunk(ChunkPos pos, CallbackInfo ci) {
        if (this.papi$columnMasks != null) {
            this.papi$columnMasks.remove(pos.pack());
        }
    }
}
