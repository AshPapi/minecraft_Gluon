package net.ashpapi.papioptimization.mixin.worldgen;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StructurePiecesBuilder.class, priority = 1500)
public abstract class StructurePiecesBuilderMixin {

    @Shadow @Final private List<StructurePiece> pieces;

    @Unique
    private Long2ObjectOpenHashMap<IntArrayList> papi$chunkSpatialIndex;

    @Inject(method = "addPiece", at = @At("TAIL"))
    private void papi$onAddPiece(StructurePiece piece, CallbackInfo ci) {
        int count = this.pieces.size();
        if (count >= 16) {
            if (this.papi$chunkSpatialIndex == null) {
                this.papi$chunkSpatialIndex = new Long2ObjectOpenHashMap<>();
                for (int i = 0; i < count; i++) {
                    papi$indexPiece(i, this.pieces.get(i).getBoundingBox());
                }
            } else {
                papi$indexPiece(count - 1, piece.getBoundingBox());
            }
        }
    }

    @Unique
    private void papi$indexPiece(int pieceIndex, BoundingBox bb) {
        int minChunkX = bb.minX() >> 4;
        int maxChunkX = bb.maxX() >> 4;
        int minChunkZ = bb.minZ() >> 4;
        int maxChunkZ = bb.maxZ() >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                long key = ChunkPos.pack(cx, cz);
                this.papi$chunkSpatialIndex.computeIfAbsent(key, k -> new IntArrayList()).add(pieceIndex);
            }
        }
    }

    @Inject(method = "findCollisionPiece", at = @At("HEAD"), cancellable = true)
    private void papi$fastFindCollisionPiece(BoundingBox box, CallbackInfoReturnable<StructurePiece> cir) {
        if (this.pieces.size() < 16 || this.papi$chunkSpatialIndex == null) {
            return;
        }

        int lowestCollidingIdx = Integer.MAX_VALUE;
        int minChunkX = box.minX() >> 4;
        int maxChunkX = box.maxX() >> 4;
        int minChunkZ = box.minZ() >> 4;
        int maxChunkZ = box.maxZ() >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                long key = ChunkPos.pack(cx, cz);
                IntArrayList list = this.papi$chunkSpatialIndex.get(key);
                if (list != null) {
                    int size = list.size();
                    for (int i = 0; i < size; i++) {
                        int idx = list.getInt(i);
                        if (idx < lowestCollidingIdx) {
                            StructurePiece candidate = this.pieces.get(idx);
                            if (candidate.getBoundingBox().intersects(box)) {
                                lowestCollidingIdx = idx;
                                break;
                            }
                        } else {
                            break;
                        }
                    }
                }
            }
        }

        if (lowestCollidingIdx != Integer.MAX_VALUE) {
            cir.setReturnValue(this.pieces.get(lowestCollidingIdx));
        } else {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void papi$onClear(CallbackInfo ci) {
        this.papi$chunkSpatialIndex = null;
    }
}
