package net.ashpapi.papioptimization.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.synth.GradientNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PerlinNoise.class, priority = 1500)
public abstract class PerlinNoiseFastMixin extends GradientNoise {

    @Unique
    private static final float[] PAPI$FLAT_GRAD = {
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

    protected PerlinNoiseFastMixin(RandomSource randomSource) {
        super(randomSource);
    }

    @Unique
    private static float papi$gradDot(int hash, float dx, float dy, float dz) {
        int idx = (hash & 15) << 2;
        float[] g = PAPI$FLAT_GRAD;
        return g[idx] * dx + g[idx + 1] * dy + g[idx + 2] * dz;
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

    @WrapMethod(method = "sampleAndLerp")
    private float papi$fastSampleAndLerp(
        int x, int y, int z,
        float dx, float dy, float dz, float dySmooth,
        Operation<Float> original
    ) {
        byte[] p = this.perms;
        int pX0 = p[x & 255] & 255;
        int pX1 = p[(x + 1) & 255] & 255;
        int pXY00 = p[(pX0 + y) & 255] & 255;
        int pXY01 = p[(pX0 + y + 1) & 255] & 255;
        int pXY10 = p[(pX1 + y) & 255] & 255;
        int pXY11 = p[(pX1 + y + 1) & 255] & 255;

        float dx1 = dx - 1.0F;
        float dy1 = dy - 1.0F;
        float dz1 = dz - 1.0F;

        float g000 = papi$gradDot(p[(pXY00 + z) & 255], dx, dy, dz);
        float g100 = papi$gradDot(p[(pXY10 + z) & 255], dx1, dy, dz);
        float g010 = papi$gradDot(p[(pXY01 + z) & 255], dx, dy1, dz);
        float g110 = papi$gradDot(p[(pXY11 + z) & 255], dx1, dy1, dz);
        float g001 = papi$gradDot(p[(pXY00 + z + 1) & 255], dx, dy, dz1);
        float g101 = papi$gradDot(p[(pXY10 + z + 1) & 255], dx1, dy, dz1);
        float g011 = papi$gradDot(p[(pXY01 + z + 1) & 255], dx, dy1, dz1);
        float g111 = papi$gradDot(p[(pXY11 + z + 1) & 255], dx1, dy1, dz1);

        float sx = papi$smoothstep(dx);
        float sy = papi$smoothstep(dySmooth);
        float sz = papi$smoothstep(dz);

        return papi$lerp3(sx, sy, sz, g000, g100, g010, g110, g001, g101, g011, g111);
    }

    @Inject(method = "addToVolume", at = @At("HEAD"), cancellable = true)
    private void papi$fastAddToVolume(
        DensityBuffer buffer, DensityVolume volume,
        double xzScale, double yScale, float amplitude,
        CallbackInfo ci
    ) {
        byte[] p = this.perms;
        float[] g = PAPI$FLAT_GRAD;
        float[] out = ((DensityBufferAccessor) buffer).papi$getValues();
        double offX = this.offsetX;
        double offY = this.offsetY;
        double offZ = this.offsetZ;

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
                    double yCoord = wrap((double) volume.blockY(yIdx) * yScale) + offY;
                    int yFloor = Mth.floor(yCoord);
                    float dy = (float) (yCoord - (double) yFloor);
                    float dy1 = dy - 1.0F;
                    float sy = papi$smoothstep(dy);

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

                    float g000 = xz000 + gy000 * dy;
                    float g100 = xz100 + gy100 * dy;
                    float g010 = xz010 + gy010 * dy1;
                    float g110 = xz110 + gy110 * dy1;
                    float g001 = xz001 + gy001 * dy;
                    float g101 = xz101 + gy101 * dy;
                    float g011 = xz011 + gy011 * dy1;
                    float g111 = xz111 + gy111 * dy1;

                    out[index] += amplitude * papi$lerp3(sx, sy, sz, g000, g100, g010, g110, g001, g101, g011, g111);
                    index++;
                }
            }
        }
        ci.cancel();
    }
}
