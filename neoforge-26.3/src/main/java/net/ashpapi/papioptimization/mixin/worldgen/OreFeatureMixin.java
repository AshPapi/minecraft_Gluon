package net.ashpapi.papioptimization.mixin.worldgen;

import java.util.BitSet;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.AbstractOreFeature;
import net.minecraft.world.level.levelgen.feature.BlockReplacement;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = OreFeature.class, priority = 1500)
public abstract class OreFeatureMixin extends AbstractOreFeature {

    @Unique
    private static final Direction[] PAPI$DIRECTIONS = Direction.values();

    @Unique
    private static final ThreadLocal<BitSet> PAPI$VISITED_BITS = ThreadLocal.withInitial(() -> new BitSet(512));

    @Unique
    private BlockReplacement[] papi$targetsArray;

    public OreFeatureMixin(List<BlockReplacement> targetStates, int size, float discardChanceOnAirExposure) {
        super(targetStates, size, discardChanceOnAirExposure);
    }

    @Inject(method = "<init>(Ljava/util/List;IF)V", at = @At("RETURN"))
    private void papi$cacheTargets(List<BlockReplacement> targetStates, int size, float discardChance, CallbackInfo ci) {
        this.papi$targetsArray = targetStates.toArray(new BlockReplacement[0]);
    }

    @Inject(method = "doPlace", at = @At("HEAD"), cancellable = true)
    private void papi$fastDoPlace(
            WorldGenLevel level,
            RandomSource random,
            double x0, double x1,
            double z0, double z1,
            double y0, double y1,
            int originX, int originY, int originZ,
            int widthXZ, int heightY,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (((Object) this).getClass() != OreFeature.class) {
            return;
        }
        BlockReplacement[] targets = this.papi$targetsArray;
        if (targets == null) {
            targets = this.targetStates.toArray(new BlockReplacement[0]);
            this.papi$targetsArray = targets;
        }
        int targetLen = targets.length;
        int oreSize = this.size;
        float airDiscard = this.discardChanceOnAirExposure;

        int placed = 0;
        BitSet visited = PAPI$VISITED_BITS.get();
        visited.clear();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();
        double[] spheres = new double[oreSize * 4];

        for (int i = 0; i < oreSize; i++) {
            float t = (float) i / (float) oreSize;
            double sx = Mth.lerp((double) t, x0, x1);
            double sy = Mth.lerp((double) t, y0, y1);
            double sz = Mth.lerp((double) t, z0, z1);
            double scale = random.nextDouble() * (double) oreSize / 16.0D;
            double sr = ((double) (Mth.sin(3.1415927F * t) + 1.0F) * scale + 1.0D) / 2.0D;
            int base = i << 2;
            spheres[base] = sx;
            spheres[base + 1] = sy;
            spheres[base + 2] = sz;
            spheres[base + 3] = sr;
        }

        for (int i = 0; i < oreSize - 1; i++) {
            int bi = i << 2;
            if (spheres[bi + 3] <= 0.0D) {
                continue;
            }
            for (int j = i + 1; j < oreSize; j++) {
                int bj = j << 2;
                if (spheres[bj + 3] <= 0.0D) {
                    continue;
                }
                double dx = spheres[bi] - spheres[bj];
                double dy = spheres[bi + 1] - spheres[bj + 1];
                double dz = spheres[bi + 2] - spheres[bj + 2];
                double dr = spheres[bi + 3] - spheres[bj + 3];
                if (dr * dr > dx * dx + dy * dy + dz * dz) {
                    if (dr > 0.0D) {
                        spheres[bj + 3] = -1.0D;
                    } else {
                        spheres[bi + 3] = -1.0D;
                    }
                }
            }
        }

        int strideZ = widthXZ * heightY;
        try (BulkSectionAccess access = new BulkSectionAccess(level)) {
            Function<BlockPos, BlockState> stateGetter = null;
            for (int i = 0; i < oreSize; i++) {
                int base = i << 2;
                double radius = spheres[base + 3];
                if (radius < 0.0D) {
                    continue;
                }
                double cx = spheres[base];
                double cy = spheres[base + 1];
                double cz = spheres[base + 2];

                int minX = Math.max(Mth.floor(cx - radius), originX);
                int minY = Math.max(Mth.floor(cy - radius), originY);
                int minZ = Math.max(Mth.floor(cz - radius), originZ);
                int maxX = Math.max(Mth.floor(cx + radius), minX);
                int maxY = Math.max(Mth.floor(cy + radius), minY);
                int maxZ = Math.max(Mth.floor(cz + radius), minZ);

                for (int x = minX; x <= maxX; x++) {
                    double nx = ((double) x + 0.5D - cx) / radius;
                    double nx2 = nx * nx;
                    if (nx2 >= 1.0D) {
                        continue;
                    }
                    for (int y = minY; y <= maxY; y++) {
                        double ny = ((double) y + 0.5D - cy) / radius;
                        double nxy2 = nx2 + ny * ny;
                        if (nxy2 >= 1.0D) {
                            continue;
                        }
                        boolean outsideY = level.isOutsideBuildHeight(y);
                        for (int z = minZ; z <= maxZ; z++) {
                            double nz = ((double) z + 0.5D - cz) / radius;
                            if (nxy2 + nz * nz >= 1.0D || outsideY) {
                                continue;
                            }
                            int bitIdx = (x - originX) + (y - originY) * widthXZ + (z - originZ) * strideZ;
                            if (visited.get(bitIdx)) {
                                continue;
                            }
                            visited.set(bitIdx);
                            mutablePos.set(x, y, z);
                            if (!level.ensureCanWrite(mutablePos)) {
                                continue;
                            }
                            LevelChunkSection section = access.getSection(mutablePos);
                            if (section == null) {
                                continue;
                            }
                            int rx = x & 15;
                            int ry = y & 15;
                            int rz = z & 15;
                            BlockState existing = section.getBlockState(rx, ry, rz);
                            for (int tIdx = 0; tIdx < targetLen; tIdx++) {
                                BlockReplacement rep = targets[tIdx];
                                if (!rep.target().test(existing, mutablePos, random)) {
                                    continue;
                                }
                                boolean allow;
                                if (airDiscard <= 0.0F) {
                                    allow = true;
                                } else if (airDiscard >= 1.0F) {
                                    allow = !papi$isAdjAir(access, mutablePos, neighborPos);
                                } else if (random.nextFloat() >= airDiscard) {
                                    allow = true;
                                } else {
                                    allow = !papi$isAdjAir(access, mutablePos, neighborPos);
                                }
                                if (allow) {
                                    section.setBlockState(rx, ry, rz, rep.state(), false);
                                    placed++;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        cir.setReturnValue(placed > 0);
    }

    @Unique
    private static boolean papi$isAdjAir(BulkSectionAccess access, BlockPos pos, BlockPos.MutableBlockPos scratch) {
        Direction[] dirs = PAPI$DIRECTIONS;
        for (int i = 0; i < dirs.length; i++) {
            scratch.setWithOffset(pos, dirs[i]);
            if (access.getBlockState(scratch).isAir()) {
                return true;
            }
        }
        return false;
    }
}
