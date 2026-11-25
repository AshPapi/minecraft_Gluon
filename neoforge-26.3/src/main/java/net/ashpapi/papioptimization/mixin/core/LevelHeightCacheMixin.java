package net.ashpapi.papioptimization.mixin.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Level.class, priority = 1500)
public abstract class LevelHeightCacheMixin implements LevelReader {

    @Shadow
    @Final
    private Holder<DimensionType> dimensionTypeRegistration;

    @Unique private int papi$minY;
    @Unique private int papi$height;
    @Unique private int papi$maxY;
    @Unique private int papi$minSectionY;
    @Unique private int papi$maxSectionY;
    @Unique private int papi$sectionsCount;
    @Unique private boolean papi$heightReady;

    @Inject(
        method = "<init>",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/level/Level;dimensionTypeRegistration:Lnet/minecraft/core/Holder;",
            opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER
        )
    )
    private void papi$cacheDimensionHeights(CallbackInfo ci) {
        DimensionType dimType = this.dimensionTypeRegistration.value();
        int minY = dimType.minY();
        int height = dimType.height();
        int maxY = minY + height - 1;
        int minSec = SectionPos.blockToSectionCoord(minY);
        int maxSec = SectionPos.blockToSectionCoord(maxY);
        this.papi$minY = minY;
        this.papi$height = height;
        this.papi$maxY = maxY;
        this.papi$minSectionY = minSec;
        this.papi$maxSectionY = maxSec;
        this.papi$sectionsCount = maxSec - minSec + 1;
        this.papi$heightReady = true;
    }

    @Override
    public int getMinY() {
        return this.papi$heightReady ? this.papi$minY : this.dimensionType().minY();
    }

    @Override
    public int getHeight() {
        return this.papi$heightReady ? this.papi$height : this.dimensionType().height();
    }

    @Override
    public int getMaxY() {
        return this.papi$heightReady ? this.papi$maxY : (this.getMinY() + this.getHeight() - 1);
    }

    @Override
    public int getSectionsCount() {
        return this.papi$heightReady ? this.papi$sectionsCount : (this.getMaxSectionY() - this.getMinSectionY() + 1);
    }

    @Override
    public int getMinSectionY() {
        return this.papi$heightReady ? this.papi$minSectionY : SectionPos.blockToSectionCoord(this.getMinY());
    }

    @Override
    public int getMaxSectionY() {
        return this.papi$heightReady ? this.papi$maxSectionY : SectionPos.blockToSectionCoord(this.getMaxY());
    }

    @Override
    public boolean isInsideBuildHeight(BlockPos pos) {
        return !this.isOutsideBuildHeight(pos.getY());
    }

    @Override
    public boolean isInsideBuildHeight(int y) {
        if (!this.papi$heightReady) {
            return y >= this.getMinY() && y <= this.getMaxY();
        }
        return Integer.compareUnsigned(y - this.papi$minY, this.papi$height) < 0;
    }

    @Override
    public boolean isOutsideBuildHeight(BlockPos pos) {
        return this.isOutsideBuildHeight(pos.getY());
    }

    @Override
    public boolean isOutsideBuildHeight(int y) {
        if (!this.papi$heightReady) {
            return y < this.getMinY() || y > this.getMaxY();
        }
        return Integer.compareUnsigned(y - this.papi$minY, this.papi$height) >= 0;
    }

    @Override
    public int getSectionIndex(int y) {
        return SectionPos.blockToSectionCoord(y) - this.getMinSectionY();
    }

    @Override
    public int getSectionIndexFromSectionY(int sectionY) {
        return sectionY - this.getMinSectionY();
    }

    @Override
    public int getSectionYFromSectionIndex(int sectionIndex) {
        return sectionIndex + this.getMinSectionY();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ) {
        ChunkAccess chunk = this.getChunk(
                SectionPos.blockToSectionCoord(quartX << 2),
                SectionPos.blockToSectionCoord(quartZ << 2),
                ChunkStatus.BIOMES,
                false
        );
        return chunk != null ? chunk.getNoiseBiome(quartX, quartY, quartZ) : this.getUncachedNoiseBiome(quartX, quartY, quartZ);
    }
}
