package net.ashpapi.papioptimization.mixin.worldgen;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import net.minecraft.world.level.levelgen.synth.SmearedPerlinNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SmearedPerlinNoise.class, priority = 1500)
public abstract class SmearedPerlinNoiseFastMixin extends PerlinNoise {

    @Shadow @Final private double fudgeYScale;

    @Unique
    private static final float[] PAPI$SMEARED_GRAD = {
        1.0F, 1.0F, 0.0F, 0.0F,
        -1.0F, 1.0F, 0.0F, 0.0F,
        1.0F, -1.0F, 0.0F, 0.0F,
        -1.0F, -1.0F, 0.0F, 0.0F,
        1.0F, 0.0F, 1.0F, 0.0F,
        -1.0F, 0.0F, 1.0F, 0.0F,
        1.0F, 0.0F, -1.0F, 0.0F,
        -1.0F, 0.0F, -1.0F, 0.0F,
        0.0F, 1.0F, 1.0F, 0.0F,
        0.0F, -1.0F, 1.0F, 0.0F,
        0.0F, 1.0F, -1.0F, 0.0F,
        0.0F, -1.0F, -1.0F, 0.0F,
        1.0F, 1.0F, 0.0F, 0.0F,
        0.0F, -1.0F, 1.0F, 0.0F,
        -1.0F, 1.0F, 0.0F, 0.0F,
        0.0F, -1.0F, -1.0F, 0.0F
    };

    protected SmearedPerlinNoiseFastMixin(RandomSource randomSource) {
        super(randomSource);
    }

    @Unique
    private static float papi$smoothstep(float t) {
        return t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
    }

    @Unique
    private static float papi$lerp3(
        float sx, float sy, float sz,
        float g000, float g100, float g010, float g110,
        float g001, float g101, float g011, float g111
    ) {
        float lx00 = g000 + sx * (g100 - g000);
        float lx10 = g010 + sx * (g110 - g010);
        float lx01 = g001 + sx * (g101 - g001);
        float lx11 = g011 + sx * (g111 - g011);
        float lxy0 = lx00 + sy * (lx10 - lx00);
        float lxy1 = lx01 + sy * (lx11 - lx01);
        return lxy0 + sz * (lxy1 - lxy0);
    }

    @Inject(method = "addToVolume", at = @At("HEAD"), cancellable = true)
    private void papi$fastSmearedAddToVolume(
        DensityBuffer buffer, DensityVolume volume,
        double xzScale, double yScale, float amplitude,
        CallbackInfo ci
    ) {
        byte[] p = this.perms;
        float[] g = PAPI$SMEARED_GRAD;
        float[] out = ((DensityBufferAccessor) buffer).papi$getValues();
        double offX = this.offsetX;
        double offY = this.offsetY;
        double offZ = this.offsetZ;
        double fScale = this.fudgeYScale;

        float xz000 = 0.0F, xz100 = 0.0F, xz010 = 0.0F, xz110 = 0.0F;
        float xz001 = 0.0F, xz101 = 0.0F, xz011 = 0.0F, xz111 = 0.0F;
        float gy000 = 0.0F, gy100 = 0.0F, gy010 = 0.0F, gy110 = 0.0F;
        float gy001 = 0.0F, gy101 = 0.0F, gy011 = 0.0F, gy111 = 0.0F;

        int index = 0;
        int sizeZ = volume.sizeZ();
        int sizeX = volume.sizeX();
        int sizeY = volume.sizeY();

        for (int zIdx = 0; zIdx < sizeZ; zIdx++) {
            double zCoord = wrap((double) volume.blockZ(zIdx) * xzScale) + offZ;
            int zFloor = Mth.floor(zCoord);
            float dz = (float) (zCoord - (double) zFloor);
            float dz1 = dz - 1.0F;
            float sz = papi$smoothstep(dz);

            for (int xIdx = 0; xIdx < sizeX; xIdx++) {
                double xCoord = wrap((double) volume.blockX(xIdx) * xzScale) + offX;
                int xFloor = Mth.floor(xCoord);
                float dx = (float) (xCoord - (double) xFloor);
                float dx1 = dx - 1.0F;
                int pX0 = p[xFloor & 255] & 255;
                int pX1 = p[(xFloor + 1) & 255] & 255;
                float sx = papi$smoothstep(dx);
                int lastYFloor = Integer.MIN_VALUE;

                for (int yIdx = 0; yIdx < sizeY; yIdx++) {
                    double rawY = (double) volume.blockY(yIdx) * yScale;
                    double yCoord = wrap(rawY) + offY;
                    int yFloor = Mth.floor(yCoord);
                    double dyDouble = yCoord - (double) yFloor;
                    float sy = papi$smoothstep((float) dyDouble);

                    if (lastYFloor != yFloor) {
                        int pXY00 = p[(pX0 + yFloor) & 255] & 255;
                        int pXY01 = p[(pX0 + yFloor + 1) & 255] & 255;
                        int pXY10 = p[(pX1 + yFloor) & 255] & 255;
                        int pXY11 = p[(pX1 + yFloor + 1) & 255] & 255;

                        int i000 = (p[(pXY00 + zFloor) & 255] & 15) << 2;
                        xz000 = g[i000] * dx + g[i000 + 2] * dz;
                        gy000 = g[i000 + 1];

                        int i100 = (p[(pXY10 + zFloor) & 255] & 15) << 2;
                        xz100 = g[i100] * dx1 + g[i100 + 2] * dz;
                        gy100 = g[i100 + 1];

                        int i010 = (p[(pXY01 + zFloor) & 255] & 15) << 2;
                        xz010 = g[i010] * dx + g[i010 + 2] * dz;
                        gy010 = g[i010 + 1];

                        int i110 = (p[(pXY11 + zFloor) & 255] & 15) << 2;
                        xz110 = g[i110] * dx1 + g[i110 + 2] * dz;
                        gy110 = g[i110 + 1];

                        int i001 = (p[(pXY00 + zFloor + 1) & 255] & 15) << 2;
                        xz001 = g[i001] * dx + g[i001 + 2] * dz1;
                        gy001 = g[i001 + 1];

                        int i101 = (p[(pXY10 + zFloor + 1) & 255] & 15) << 2;
                        xz101 = g[i101] * dx1 + g[i101 + 2] * dz1;
                        gy101 = g[i101 + 1];

                        int i011 = (p[(pXY01 + zFloor + 1) & 255] & 15) << 2;
                        xz011 = g[i011] * dx + g[i011 + 2] * dz1;
                        gy011 = g[i011 + 1];

                        int i111 = (p[(pXY11 + zFloor + 1) & 255] & 15) << 2;
                        xz111 = g[i111] * dx1 + g[i111 + 2] * dz1;
                        gy111 = g[i111 + 1];

                        lastYFloor = yFloor;
                    }

                    double fudgeInput = (rawY >= 0.0D && rawY < dyDouble) ? rawY : dyDouble;
                    double fudgeY = (double) Mth.floor(fudgeInput / fScale + 1.0000000116860974E-7D) * fScale;
                    float dyFudge = (float) (dyDouble - fudgeY);
                    float dyFudge1 = dyFudge - 1.0F;

                    float g000 = xz000 + gy000 * dyFudge;
                    float g100 = xz100 + gy100 * dyFudge;
                    float g010 = xz010 + gy010 * dyFudge1;
                    float g110 = xz110 + gy110 * dyFudge1;
                    float g001 = xz001 + gy001 * dyFudge;
                    float g101 = xz101 + gy101 * dyFudge;
                    float g011 = xz011 + gy011 * dyFudge1;
                    float g111 = xz111 + gy111 * dyFudge1;

                    out[index] += amplitude * papi$lerp3(sx, sy, sz, g000, g100, g010, g110, g001, g101, g011, g111);
                    index++;
                }
            }
        }
        ci.cancel();
    }
}
