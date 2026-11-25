package net.ashpapi.papioptimization.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Aquifer.NoiseBasedAquifer.class, priority = 1500)
public abstract class AquiferNoiseBasedMixin {

    @Shadow @Final private static double FLOWING_UPDATE_SIMULARITY;
    @Shadow @Final protected DensitySampler.Bound barrierNoise;
    @Shadow @Final private PositionalRandomFactory positionalRandomFactory;
    @Shadow @Final protected long[] aquiferLocationCache;
    @Shadow @Final private Aquifer.FluidPicker globalFluidPicker;
    @Shadow protected boolean shouldScheduleFluidUpdate;
    @Shadow @Final private int skipSamplingAboveY;
    @Shadow @Final protected int minGridX;
    @Shadow @Final protected int minGridY;
    @Shadow @Final protected int minGridZ;
    @Shadow @Final protected int gridSizeX;
    @Shadow @Final protected int gridSizeZ;
    @Shadow @Final private DensitySampler.Bound surfaceLevel;
    @Shadow @Final private Long2IntMap surfaceLevelCache;

    @Shadow protected abstract Aquifer.FluidStatus getAquiferStatus(int index);
    @Shadow protected static double similarity(int distA, int distB) { return 0.0D; }
    @Shadow private static int gridX(int x) { return 0; }
    @Shadow private static int gridY(int y) { return 0; }
    @Shadow private static int gridZ(int z) { return 0; }
    @Shadow private static int fromGridX(int gridX, int offset) { return 0; }
    @Shadow private static int fromGridY(int gridY, int offset) { return 0; }
    @Shadow private static int fromGridZ(int gridZ, int offset) { return 0; }

    @Unique
    private long papi$lastSurfaceKey = Long.MIN_VALUE;

    @Unique
    private int papi$lastSurfaceVal;

    @Unique
    private boolean papi$hasLastSurface;

    @Unique
    private double papi$cachedBarrierValue;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$initSurfaceCacheSentinel(CallbackInfo ci) {
        this.surfaceLevelCache.defaultReturnValue(Integer.MIN_VALUE);
    }

    @WrapMethod(method = "getIndex")
    private int papi$inlineGetIndex(int gridX, int gridY, int gridZ, Operation<Integer> original) {
        return ((gridY - this.minGridY) * this.gridSizeZ + (gridZ - this.minGridZ)) * this.gridSizeX + (gridX - this.minGridX);
    }

    @WrapMethod(method = "surfaceLevel(II)I")
    private int papi$fastSurfaceLevel(int x, int z, Operation<Integer> original) {
        int qx = x & ~3;
        int qz = z & ~3;
        long key = ChunkPos.pack(qx, qz);
        if (this.papi$hasLastSurface && key == this.papi$lastSurfaceKey) {
            return this.papi$lastSurfaceVal;
        }
        int cached = this.surfaceLevelCache.get(key);
        if (cached != Integer.MIN_VALUE) {
            this.papi$lastSurfaceKey = key;
            this.papi$lastSurfaceVal = cached;
            this.papi$hasLastSurface = true;
            return cached;
        }
        int computed = Mth.floor(this.surfaceLevel.sampleValue(qx, 0, qz));
        if (computed != Integer.MIN_VALUE) {
            this.surfaceLevelCache.put(key, computed);
            this.papi$lastSurfaceKey = key;
            this.papi$lastSurfaceVal = computed;
            this.papi$hasLastSurface = true;
        }
        return computed;
    }

    @WrapMethod(method = "computeSubstance")
    private BlockState papi$fastComputeSubstance(int x, int y, int z, double density, Operation<BlockState> original) {
        if (density > 0.0D) {
            this.shouldScheduleFluidUpdate = false;
            return null;
        }
        Aquifer.FluidStatus globalStatus = this.globalFluidPicker.computeFluid(x, y, z);
        if (y > this.skipSamplingAboveY) {
            this.shouldScheduleFluidUpdate = false;
            return globalStatus.at(y);
        }
        if (globalStatus.at(y).is(Blocks.LAVA)) {
            this.shouldScheduleFluidUpdate = false;
            return SharedConstants.DEBUG_DISABLE_FLUID_GENERATION
                    ? Blocks.AIR.defaultBlockState()
                    : Blocks.LAVA.defaultBlockState();
        }

        int baseGridX = gridX(x - 5);
        int baseGridY = gridY(y + 1);
        int baseGridZ = gridZ(z - 5);

        int dist1 = Integer.MAX_VALUE;
        int dist2 = Integer.MAX_VALUE;
        int dist3 = Integer.MAX_VALUE;
        int dist4 = Integer.MAX_VALUE;
        int idx1 = 0;
        int idx2 = 0;
        int idx3 = 0;
        int idx4 = 0;

        long[] locCache = this.aquiferLocationCache;
        int minGX = this.minGridX;
        int minGY = this.minGridY;
        int minGZ = this.minGridZ;
        int sizeX = this.gridSizeX;
        int sizeZ = this.gridSizeZ;

        for (int ox = 0; ox <= 1; ox++) {
            int gx = baseGridX + ox;
            for (int oy = -1; oy <= 1; oy++) {
                int gy = baseGridY + oy;
                int yzBase = (gy - minGY) * sizeZ - minGZ;
                for (int oz = 0; oz <= 1; oz++) {
                    int gz = baseGridZ + oz;
                    int idx = (yzBase + gz) * sizeX + (gx - minGX);
                    long loc = locCache[idx];
                    if (loc == Long.MAX_VALUE) {
                        RandomSource rng = this.positionalRandomFactory.at(gx, gy, gz);
                        loc = BlockPos.asLong(
                                fromGridX(gx, rng.nextInt(10)),
                                fromGridY(gy, rng.nextInt(9)),
                                fromGridZ(gz, rng.nextInt(10)));
                        locCache[idx] = loc;
                    }
                    int dx = BlockPos.getX(loc) - x;
                    int dy = BlockPos.getY(loc) - y;
                    int dz = BlockPos.getZ(loc) - z;
                    int d2 = dx * dx + dy * dy + dz * dz;

                    if (dist1 >= d2) {
                        idx4 = idx3;
                        idx3 = idx2;
                        idx2 = idx1;
                        idx1 = idx;
                        dist4 = dist3;
                        dist3 = dist2;
                        dist2 = dist1;
                        dist1 = d2;
                    } else if (dist2 >= d2) {
                        idx4 = idx3;
                        idx3 = idx2;
                        idx2 = idx;
                        dist4 = dist3;
                        dist3 = dist2;
                        dist2 = d2;
                    } else if (dist3 >= d2) {
                        idx4 = idx3;
                        idx3 = idx;
                        dist4 = dist3;
                        dist3 = d2;
                    } else if (dist4 >= d2) {
                        idx4 = idx;
                        dist4 = d2;
                    }
                }
            }
        }

        Aquifer.FluidStatus status1 = this.getAquiferStatus(idx1);
        double sim12 = similarity(dist1, dist2);
        BlockState rawState = status1.at(y);
        BlockState outState = SharedConstants.DEBUG_DISABLE_FLUID_GENERATION ? Blocks.AIR.defaultBlockState() : rawState;

        if (sim12 <= 0.0D) {
            if (sim12 >= FLOWING_UPDATE_SIMULARITY) {
                Aquifer.FluidStatus status2 = this.getAquiferStatus(idx2);
                this.shouldScheduleFluidUpdate = !status1.equals(status2);
            } else {
                this.shouldScheduleFluidUpdate = false;
            }
            return outState;
        }

        if (rawState.is(Blocks.WATER) && this.globalFluidPicker.computeFluid(x, y - 1, z).at(y - 1).is(Blocks.LAVA)) {
            this.shouldScheduleFluidUpdate = true;
            return outState;
        }

        this.papi$cachedBarrierValue = Double.NaN;
        Aquifer.FluidStatus status2 = this.getAquiferStatus(idx2);
        double p12 = sim12 * this.papi$calcPressureNoAlloc(x, y, z, status1, status2);
        if (density + p12 > 0.0D) {
            this.shouldScheduleFluidUpdate = false;
            return null;
        }

        Aquifer.FluidStatus status3 = this.getAquiferStatus(idx3);
        double sim13 = similarity(dist1, dist3);
        if (sim13 > 0.0D) {
            double p13 = sim12 * sim13 * this.papi$calcPressureNoAlloc(x, y, z, status1, status3);
            if (density + p13 > 0.0D) {
                this.shouldScheduleFluidUpdate = false;
                return null;
            }
        }

        double sim23 = similarity(dist2, dist3);
        if (sim23 > 0.0D) {
            double p23 = sim12 * sim23 * this.papi$calcPressureNoAlloc(x, y, z, status2, status3);
            if (density + p23 > 0.0D) {
                this.shouldScheduleFluidUpdate = false;
                return null;
            }
        }

        boolean diff12 = !status1.equals(status2);
        boolean diff23 = sim23 >= FLOWING_UPDATE_SIMULARITY && !status2.equals(status3);
        boolean diff13 = sim13 >= FLOWING_UPDATE_SIMULARITY && !status1.equals(status3);
        if (!diff12 && !diff23 && !diff13) {
            this.shouldScheduleFluidUpdate = sim13 >= FLOWING_UPDATE_SIMULARITY
                    && similarity(dist1, dist4) >= FLOWING_UPDATE_SIMULARITY
                    && !status1.equals(this.getAquiferStatus(idx4));
        } else {
            this.shouldScheduleFluidUpdate = true;
        }
        return outState;
    }

    @Unique
    private double papi$calcPressureNoAlloc(int x, int y, int z, Aquifer.FluidStatus statusA, Aquifer.FluidStatus statusB) {
        BlockState stateA = statusA.at(y);
        BlockState stateB = statusB.at(y);
        if ((stateA.is(Blocks.LAVA) && stateB.is(Blocks.WATER)) || (stateA.is(Blocks.WATER) && stateB.is(Blocks.LAVA))) {
            return 2.0D;
        }
        int levelDiff = Math.abs(statusA.fluidLevel() - statusB.fluidLevel());
        if (levelDiff == 0) {
            return 0.0D;
        }
        double avgLevel = 0.5D * (double) (statusA.fluidLevel() + statusB.fluidLevel());
        double relY = (double) y + 0.5D - avgLevel;
        double halfDiff = (double) levelDiff / 2.0D;
        double distFromEdge = halfDiff - Math.abs(relY);
        double scaled;
        if (relY > 0.0D) {
            double shifted = 0.0D + distFromEdge;
            scaled = shifted > 0.0D ? shifted / 1.5D : shifted / 2.5D;
        } else {
            double shifted = 3.0D + distFromEdge;
            scaled = shifted > 0.0D ? shifted / 3.0D : shifted / 10.0D;
        }
        double barrier;
        if (scaled >= -2.0D && scaled <= 2.0D) {
            double cur = this.papi$cachedBarrierValue;
            if (Double.isNaN(cur)) {
                cur = (double) this.barrierNoise.sampleValue(x, y, z);
                this.papi$cachedBarrierValue = cur;
            }
            barrier = cur;
        } else {
            barrier = 0.0D;
        }
        return 2.0D * (barrier + scaled);
    }
}
