package net.ashpapi.papioptimization.mixin.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WorldGenRegion.class, priority = 1500)
public abstract class WorldGenRegionMixin {

    @Shadow @Final private StaticCache2D<ChunkAccess> cache;
    @Shadow @Final private ChunkAccess center;
    @Shadow @Final private int centerChunkX;
    @Shadow @Final private int centerChunkZ;

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        int y = pos.getY();
        ChunkAccess centerChunk = this.center;
        if (centerChunk.isOutsideBuildHeight(y)) {
            cir.setReturnValue(Blocks.VOID_AIR.defaultBlockState());
            return;
        }
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        if (cx == this.centerChunkX && cz == this.centerChunkZ) {
            cir.setReturnValue(centerChunk.getSection((y >> 4) - centerChunk.getMinSectionY())
                    .getBlockState(pos.getX() & 15, y & 15, pos.getZ() & 15));
            return;
        }
        if (this.cache.contains(cx, cz)) {
            ChunkAccess chunk = this.cache.get(cx, cz);
            cir.setReturnValue(chunk.getSection((y >> 4) - chunk.getMinSectionY())
                    .getBlockState(pos.getX() & 15, y & 15, pos.getZ() & 15));
        }
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetFluidState(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        int y = pos.getY();
        ChunkAccess centerChunk = this.center;
        if (centerChunk.isOutsideBuildHeight(y)) {
            cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
            return;
        }
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        if (cx == this.centerChunkX && cz == this.centerChunkZ) {
            cir.setReturnValue(centerChunk.getSection((y >> 4) - centerChunk.getMinSectionY())
                    .getFluidState(pos.getX() & 15, y & 15, pos.getZ() & 15));
            return;
        }
        if (this.cache.contains(cx, cz)) {
            ChunkAccess chunk = this.cache.get(cx, cz);
            cir.setReturnValue(chunk.getSection((y >> 4) - chunk.getMinSectionY())
                    .getFluidState(pos.getX() & 15, y & 15, pos.getZ() & 15));
        }
    }
}
