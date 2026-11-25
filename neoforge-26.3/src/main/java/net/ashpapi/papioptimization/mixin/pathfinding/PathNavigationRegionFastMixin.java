package net.ashpapi.papioptimization.mixin.pathfinding;

import net.ashpapi.papioptimization.pathfinding.PapiNavigationRegionAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PathNavigationRegion.class, priority = 1500)
public abstract class PathNavigationRegionFastMixin implements PapiNavigationRegionAccess {

    @Shadow
    @Final
    protected int centerX;

    @Shadow
    @Final
    protected int centerZ;

    @Shadow
    @Final
    protected ChunkAccess[][] chunks;

    @Shadow
    protected boolean allEmpty;

    @Unique
    private ChunkAccess[] papi$flatChunks;

    @Unique
    private int papi$xLen;

    @Unique
    private int papi$zLen;

    @Unique
    private int papi$minY;

    @Unique
    private int papi$height;

    @Unique
    private int papi$minSectionY;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$initFlatRegion(Level level, BlockPos start, BlockPos end, CallbackInfo ci) {
        this.papi$minY = level.getMinY();
        this.papi$height = level.getHeight();
        this.papi$minSectionY = level.getMinSectionY();
        ChunkAccess[][] grid = this.chunks;
        int xLen = grid.length;
        int zLen = xLen > 0 ? grid[0].length : 0;
        this.papi$xLen = xLen;
        this.papi$zLen = zLen;
        ChunkAccess[] flat = new ChunkAccess[xLen * zLen];
        int idx = 0;
        for (int ix = 0; ix < xLen; ix++) {
            ChunkAccess[] col = grid[ix];
            for (int iz = 0; iz < zLen; iz++) {
                flat[idx++] = col[iz];
            }
        }
        this.papi$flatChunks = flat;
    }

    @Unique
    private @Nullable ChunkAccess papi$getLoadedChunk(int chunkX, int chunkZ) {
        ChunkAccess[] flat = this.papi$flatChunks;
        if (flat == null) {
            return null;
        }
        int ix = chunkX - this.centerX;
        int iz = chunkZ - this.centerZ;
        if (Integer.compareUnsigned(ix, this.papi$xLen) >= 0 || Integer.compareUnsigned(iz, this.papi$zLen) >= 0) {
            return null;
        }
        return flat[ix * this.papi$zLen + iz];
    }

    @Override
    public @Nullable LevelChunkSection papi$getSectionIfLoaded(int sectionX, int sectionY, int sectionZ) {
        ChunkAccess chunk = papi$getLoadedChunk(sectionX, sectionZ);
        if (chunk == null || chunk instanceof EmptyLevelChunk) {
            return null;
        }
        int secIdx = sectionY - this.papi$minSectionY;
        LevelChunkSection[] sections = chunk.getSections();
        if (Integer.compareUnsigned(secIdx, sections.length) >= 0) {
            return null;
        }
        return sections[secIdx];
    }

    @Override
    public boolean papi$isSectionGuaranteedAir(int sectionX, int sectionY, int sectionZ) {
        if (this.allEmpty) {
            return true;
        }
        ChunkAccess[] flat = this.papi$flatChunks;
        if (flat == null) {
            return false;
        }
        int yBase = sectionY << 4;
        if (Integer.compareUnsigned(yBase - this.papi$minY, this.papi$height) >= 0) {
            return true;
        }
        int ix = sectionX - this.centerX;
        int iz = sectionZ - this.centerZ;
        if (Integer.compareUnsigned(ix, this.papi$xLen) >= 0 || Integer.compareUnsigned(iz, this.papi$zLen) >= 0) {
            return true;
        }
        ChunkAccess chunk = flat[ix * this.papi$zLen + iz];
        if (chunk == null || chunk instanceof EmptyLevelChunk) {
            return true;
        }
        int secIdx = sectionY - this.papi$minSectionY;
        LevelChunkSection[] sections = chunk.getSections();
        if (Integer.compareUnsigned(secIdx, sections.length) >= 0) {
            return true;
        }
        LevelChunkSection section = sections[secIdx];
        return section == null || section.hasOnlyAir();
    }

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        ChunkAccess[] flat = this.papi$flatChunks;
        if (flat == null) {
            return;
        }
        int y = pos.getY();
        if (Integer.compareUnsigned(y - this.papi$minY, this.papi$height) >= 0) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
            return;
        }
        int x = pos.getX();
        int z = pos.getZ();
        int ix = (x >> 4) - this.centerX;
        int iz = (z >> 4) - this.centerZ;
        if (Integer.compareUnsigned(ix, this.papi$xLen) >= 0 || Integer.compareUnsigned(iz, this.papi$zLen) >= 0) {
            cir.setReturnValue(Blocks.VOID_AIR.defaultBlockState());
            return;
        }
        ChunkAccess chunk = flat[ix * this.papi$zLen + iz];
        if (chunk == null || chunk instanceof EmptyLevelChunk) {
            cir.setReturnValue(Blocks.VOID_AIR.defaultBlockState());
            return;
        }
        int secIdx = (y >> 4) - this.papi$minSectionY;
        LevelChunkSection[] sections = chunk.getSections();
        if (Integer.compareUnsigned(secIdx, sections.length) >= 0) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
            return;
        }
        LevelChunkSection section = sections[secIdx];
        if (section == null || section.hasOnlyAir()) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
            return;
        }
        cir.setReturnValue(section.getBlockState(x & 15, y & 15, z & 15));
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetFluidState(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        ChunkAccess[] flat = this.papi$flatChunks;
        if (flat == null) {
            return;
        }
        int y = pos.getY();
        if (Integer.compareUnsigned(y - this.papi$minY, this.papi$height) >= 0) {
            cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
            return;
        }
        int x = pos.getX();
        int z = pos.getZ();
        int ix = (x >> 4) - this.centerX;
        int iz = (z >> 4) - this.centerZ;
        if (Integer.compareUnsigned(ix, this.papi$xLen) >= 0 || Integer.compareUnsigned(iz, this.papi$zLen) >= 0) {
            cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
            return;
        }
        ChunkAccess chunk = flat[ix * this.papi$zLen + iz];
        if (chunk == null || chunk instanceof EmptyLevelChunk) {
            cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
            return;
        }
        int secIdx = (y >> 4) - this.papi$minSectionY;
        LevelChunkSection[] sections = chunk.getSections();
        if (Integer.compareUnsigned(secIdx, sections.length) >= 0) {
            cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
            return;
        }
        LevelChunkSection section = sections[secIdx];
        if (section == null || section.hasOnlyAir()) {
            cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
            return;
        }
        cir.setReturnValue(section.getFluidState(x & 15, y & 15, z & 15));
    }

    @Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetBlockEntity(BlockPos pos, CallbackInfoReturnable<@Nullable BlockEntity> cir) {
        ChunkAccess[] flat = this.papi$flatChunks;
        if (flat == null) {
            return;
        }
        ChunkAccess chunk = papi$getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || chunk instanceof EmptyLevelChunk) {
            cir.setReturnValue(null);
            return;
        }
        cir.setReturnValue(chunk.getBlockEntity(pos));
    }
}
