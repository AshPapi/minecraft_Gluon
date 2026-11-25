package net.ashpapi.papioptimization.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChunkAccess.class, priority = 1500)
public abstract class ChunkAccessHeightCacheMixin implements LevelHeightAccessor {

    @Shadow @Final protected LevelChunkSection[] sections;

    @Unique private int papi$minY;
    @Unique private int papi$height;
    @Unique private int papi$maxY;
    @Unique private int papi$minSectionY;
    @Unique private int papi$maxSectionY;
    @Unique private int papi$sectionsCount;
    @Unique private int papi$minQuartY;
    @Unique private int papi$maxQuartY;
    @Unique private boolean papi$heightReady;

    @Inject(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/Object;<init>()V",
            shift = At.Shift.AFTER
        )
    )
    private void papi$cacheHeightMetrics(
        ChunkPos chunkPos,
        UpgradeData upgradeData,
        LevelHeightAccessor levelHeightAccessor,
        PalettedContainerFactory containerFactory,
        long inhabitedTime,
        LevelChunkSection[] sections,
        BlendingData blendingData,
        CallbackInfo ci
    ) {
        int minY = levelHeightAccessor.getMinY();
        int height = levelHeightAccessor.getHeight();
        int maxY = minY + height - 1;
        int minSectionY = SectionPos.blockToSectionCoord(minY);
        int maxSectionY = SectionPos.blockToSectionCoord(maxY);
        this.papi$minY = minY;
        this.papi$height = height;
        this.papi$maxY = maxY;
        this.papi$minSectionY = minSectionY;
        this.papi$maxSectionY = maxSectionY;
        this.papi$sectionsCount = maxSectionY - minSectionY + 1;
        this.papi$minQuartY = QuartPos.fromBlock(minY);
        this.papi$maxQuartY = this.papi$minQuartY + QuartPos.fromBlock(height) - 1;
        this.papi$heightReady = true;
    }

    @WrapMethod(method = "getMinY")
    private int papi$fastGetMinY(Operation<Integer> original) {
        return this.papi$heightReady ? this.papi$minY : original.call();
    }

    @WrapMethod(method = "getHeight")
    private int papi$fastGetHeight(Operation<Integer> original) {
        return this.papi$heightReady ? this.papi$height : original.call();
    }

    @WrapMethod(method = "getNoiseBiome")
    private Holder<Biome> papi$fastGetNoiseBiome(int quartX, int quartY, int quartZ, Operation<Holder<Biome>> original) {
        if (!this.papi$heightReady) {
            return original.call(quartX, quartY, quartZ);
        }
        int clampedY = Mth.clamp(quartY, this.papi$minQuartY, this.papi$maxQuartY);
        int secIdx = (clampedY >> 2) - this.papi$minSectionY;
        return this.sections[secIdx].getNoiseBiome(quartX & 3, clampedY & 3, quartZ & 3);
    }

    @Override
    public int getMaxY() {
        return this.papi$heightReady ? this.papi$maxY : this.getMinY() + this.getHeight() - 1;
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
    public int getSectionsCount() {
        return this.papi$heightReady ? this.papi$sectionsCount : this.getMaxSectionY() - this.getMinSectionY() + 1;
    }

    @Override
    public boolean isInsideBuildHeight(int y) {
        if (!this.papi$heightReady) {
            return y >= this.getMinY() && y <= this.getMaxY();
        }
        return y >= this.papi$minY && y <= this.papi$maxY;
    }

    @Override
    public boolean isOutsideBuildHeight(int y) {
        if (!this.papi$heightReady) {
            return y < this.getMinY() || y > this.getMaxY();
        }
        return y < this.papi$minY || y > this.papi$maxY;
    }

    @Override
    public int getSectionIndex(int y) {
        return (y >> 4) - (this.papi$heightReady ? this.papi$minSectionY : this.getMinSectionY());
    }

    @Override
    public int getSectionIndexFromSectionY(int sectionY) {
        return sectionY - (this.papi$heightReady ? this.papi$minSectionY : this.getMinSectionY());
    }

    @Override
    public int getSectionYFromSectionIndex(int sectionIndex) {
        return sectionIndex + (this.papi$heightReady ? this.papi$minSectionY : this.getMinSectionY());
    }
}
