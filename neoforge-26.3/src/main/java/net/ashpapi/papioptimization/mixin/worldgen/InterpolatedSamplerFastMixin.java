package net.ashpapi.papioptimization.mixin.worldgen;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.levelgen.densityfunction.op.InterpolatedFunction$Sampler", priority = 1500)
public abstract class InterpolatedSamplerFastMixin {

    @Shadow @Final private int cellSizeXz;
    @Shadow @Final private int cellSizeY;
    @Shadow @Final private float cellSizeXzInv;
    @Shadow @Final private float cellSizeYInv;

    @Inject(method = "fillCell", at = @At("HEAD"), cancellable = true)
    private void papi$fastFillCell(
        DensityBuffer buffer,
        DensityVolume outVolume,
        DensityVolume cellVolume,
        int cellX, int cellY, int cellZ,
        float v000, float v100, float v010, float v110,
        float v001, float v101, float v011, float v111,
        CallbackInfo ci
    ) {
        int baseX = cellVolume.blockX(cellX) - outVolume.minBlockX();
        int baseY = cellVolume.blockY(cellY) - outVolume.minBlockY();
        int baseZ = cellVolume.blockZ(cellZ) - outVolume.minBlockZ();

        int startX = Math.max(0, -baseX);
        int startY = Math.max(0, -baseY);
        int startZ = Math.max(0, -baseZ);

        int endX = Math.min(this.cellSizeXz, outVolume.sizeX() - baseX) - 1;
        int endY = Math.min(this.cellSizeY, outVolume.sizeY() - baseY) - 1;
        int endZ = Math.min(this.cellSizeXz, outVolume.sizeZ() - baseZ) - 1;

        float[] out = ((DensityBufferAccessor) buffer).papi$getValues();
        float xzInv = this.cellSizeXzInv;
        float yInv = this.cellSizeYInv;
        int outSizeY = outVolume.sizeY();
        int outStrideZ = outVolume.sizeX() * outSizeY;

        float d00z = v001 - v000;
        float d01z = v011 - v010;
        float d10z = v101 - v100;
        float d11z = v111 - v110;

        for (int lz = startZ; lz <= endZ; lz++) {
            float fz = (float) lz * xzInv;
            float c00 = v000 + fz * d00z;
            float c01 = v010 + fz * d01z;
            float c10 = v100 + fz * d10z;
            float c11 = v110 + fz * d11z;

            float dc0x = c10 - c00;
            float dc1x = c11 - c01;
            int zOffset = (baseZ + lz) * outStrideZ + baseY + startY;

            for (int lx = startX; lx <= endX; lx++) {
                float fx = (float) lx * xzInv;
                float y0 = c00 + fx * dc0x;
                float y1 = c01 + fx * dc1x;
                float stepY = (y1 - y0) * yInv;
                float val = y0 + stepY * (float) startY;
                int idx = zOffset + (baseX + lx) * outSizeY;

                for (int ly = startY; ly <= endY; ly++) {
                    out[idx++] = val;
                    val += stepY;
                }
            }
        }
        ci.cancel();
    }
}
