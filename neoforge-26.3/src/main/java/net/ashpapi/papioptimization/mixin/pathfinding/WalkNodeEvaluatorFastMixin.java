package net.ashpapi.papioptimization.mixin.pathfinding;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.Arrays;
import net.ashpapi.papioptimization.core.PapiTagEpoch;
import net.ashpapi.papioptimization.pathfinding.PapiBlockStatePathType;
import net.ashpapi.papioptimization.pathfinding.PapiNavigationRegionAccess;
import net.ashpapi.papioptimization.pathfinding.PapiPathTypeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WalkNodeEvaluator.class, priority = 1500)
public abstract class WalkNodeEvaluatorFastMixin extends NodeEvaluator {

    @Shadow
    @Final
    private Long2ObjectMap<PathType> pathTypesByPosCacheByMob;

    @Shadow
    public abstract PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob);

    @Unique
    private static final int PAPI$CACHE_SIZE = 512;

    @Unique
    private static final int PAPI$CACHE_MASK = PAPI$CACHE_SIZE - 1;

    @Unique
    private final long[] papi$posKeys = new long[PAPI$CACHE_SIZE];

    @Unique
    private final PathType[] papi$posVals = new PathType[PAPI$CACHE_SIZE];

    @Unique
    private boolean papi$posCacheDirty = true;

    @Unique
    private void papi$clearPosCache() {
        if (this.papi$posCacheDirty) {
            Arrays.fill(this.papi$posKeys, Long.MIN_VALUE);
            Arrays.fill(this.papi$posVals, null);
            this.papi$posCacheDirty = false;
        }
    }

    @Inject(method = "prepare", at = @At("HEAD"))
    private void papi$resetCacheOnPrepare(PathNavigationRegion region, Mob mob, CallbackInfo ci) {
        papi$clearPosCache();
    }

    @Inject(method = "done", at = @At("HEAD"))
    private void papi$resetCacheOnDone(CallbackInfo ci) {
        papi$clearPosCache();
    }

    @WrapMethod(method = "getCachedPathType(III)Lnet/minecraft/world/level/pathfinder/PathType;")
    private PathType papi$fastCachedPathType(int x, int y, int z, Operation<PathType> original) {
        long key = BlockPos.asLong(x, y, z);
        if (key == Long.MIN_VALUE) {
            PathType mapVal = this.pathTypesByPosCacheByMob.get(key);
            if (mapVal != null) {
                return mapVal;
            }
            PathType computed = this.getPathTypeOfMob(this.currentContext, x, y, z, this.mob);
            this.pathTypesByPosCacheByMob.put(key, computed);
            return computed;
        }
        int slot = (int) (key ^ (key >>> 32) ^ (key >>> 16)) & PAPI$CACHE_MASK;
        if (this.papi$posKeys[slot] == key) {
            PathType cached = this.papi$posVals[slot];
            if (cached != null) {
                return cached;
            }
        }
        PathType val = this.pathTypesByPosCacheByMob.get(key);
        if (val == null) {
            val = this.getPathTypeOfMob(this.currentContext, x, y, z, this.mob);
            this.pathTypesByPosCacheByMob.put(key, val);
        }
        this.papi$posKeys[slot] = key;
        this.papi$posVals[slot] = val;
        this.papi$posCacheDirty = true;
        return val;
    }

    @Inject(
        method = "getPathTypeFromState(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/pathfinder/PathType;",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void papi$headGetPathTypeFromState(
            BlockGetter level,
            BlockPos pos,
            CallbackInfoReturnable<PathType> cir,
            @Share("fetchedState") LocalRef<BlockState> stateRef,
            @Share("cacheEpoch") LocalIntRef epochRef
    ) {
        BlockState state = level.getBlockState(pos);
        stateRef.set(state);
        PapiBlockStatePathType access = (PapiBlockStatePathType) state;
        if (!access.papi$isCacheable()) {
            return;
        }
        if (state.isAir()) {
            cir.setReturnValue(PathType.OPEN);
            return;
        }
        int epoch = PapiTagEpoch.current();
        PathType cached = access.papi$getCachedPathType(epoch);
        if (cached != null) {
            cir.setReturnValue(cached);
            return;
        }
        epochRef.set(epoch);
    }

    @WrapOperation(
        method = "getPathTypeFromState(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/pathfinder/PathType;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private static BlockState papi$reuseFetchedBlockState(
            BlockGetter level,
            BlockPos pos,
            Operation<BlockState> original,
            @Share("fetchedState") LocalRef<BlockState> stateRef
    ) {
        BlockState state = stateRef.get();
        return state != null ? state : original.call(level, pos);
    }

    @Inject(
        method = "getPathTypeFromState(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/pathfinder/PathType;",
        at = @At("RETURN")
    )
    private static void papi$storeComputedPathType(
            BlockGetter level,
            BlockPos pos,
            CallbackInfoReturnable<PathType> cir,
            @Share("fetchedState") LocalRef<BlockState> stateRef,
            @Share("cacheEpoch") LocalIntRef epochRef
    ) {
        int epoch = epochRef.get();
        if (epoch != 0) {
            BlockState state = stateRef.get();
            PathType computed = cir.getReturnValue();
            if (state != null && computed != null && PapiTagEpoch.current() == epoch) {
                ((PapiBlockStatePathType) state).papi$setCachedPathType(computed, epoch);
            }
        }
    }

    @Inject(
        method = "checkNeighbourBlocks",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void papi$fastCheckNeighbourBlocks(
            PathfindingContext context,
            int x,
            int y,
            int z,
            PathType fallback,
            CallbackInfoReturnable<PathType> cir
    ) {
        CollisionGetter level = context.level();
        int epoch = PapiTagEpoch.current();
        boolean interior = Integer.compareUnsigned((x & 15) - 1, 14) < 0
                && Integer.compareUnsigned((y & 15) - 1, 14) < 0
                && Integer.compareUnsigned((z & 15) - 1, 14) < 0;

        if (interior) {
            LevelChunkSection section = null;
            if (level instanceof PapiNavigationRegionAccess reg) {
                int sx = x >> 4;
                int sy = y >> 4;
                int sz = z >> 4;
                if (reg.papi$isSectionGuaranteedAir(sx, sy, sz)) {
                    cir.setReturnValue(fallback);
                    return;
                }
                section = reg.papi$getSectionIfLoaded(sx, sy, sz);
            } else if (level instanceof Level fullLevel) {
                LevelChunk chunk = fullLevel.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk != null) {
                    int secIdx = chunk.getSectionIndexFromSectionY(y >> 4);
                    LevelChunkSection[] sections = chunk.getSections();
                    if (Integer.compareUnsigned(secIdx, sections.length) < 0) {
                        section = sections[secIdx];
                        if (section == null || section.hasOnlyAir()) {
                            cir.setReturnValue(fallback);
                            return;
                        }
                    }
                }
            }

            if (section != null) {
                if (section.hasOnlyAir() || !section.maybeHas(PapiPathTypeHelper.MAY_HAVE_NEIGHBOR_HAZARD)) {
                    cir.setReturnValue(fallback);
                    return;
                }
                int lx = x & 15;
                int ly = y & 15;
                int lz = z & 15;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dz == 0) {
                                continue;
                            }
                            BlockState state = section.getBlockState(lx + dx, ly + dy, lz + dz);
                            if (state.isAir()) {
                                continue;
                            }
                            PapiBlockStatePathType access = (PapiBlockStatePathType) state;
                            if (access.papi$isCacheable()) {
                                PathType hazard = PapiPathTypeHelper.getOrComputeNeighborHazard(state, access, epoch);
                                if (hazard != null) {
                                    cir.setReturnValue(hazard);
                                    return;
                                }
                            } else {
                                PathType hazard = papi$evalUncachedNeighbor(context, x + dx, y + dy, z + dz, state);
                                if (hazard != null) {
                                    cir.setReturnValue(hazard);
                                    return;
                                }
                            }
                        }
                    }
                }
                cir.setReturnValue(fallback);
                return;
            }
        }

        BlockPos.MutableBlockPos mpos = null;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    int nx = x + dx;
                    int ny = y + dy;
                    int nz = z + dz;
                    if (mpos == null) {
                        mpos = new BlockPos.MutableBlockPos();
                    }
                    mpos.set(nx, ny, nz);
                    BlockState state = level.getBlockState(mpos);
                    if (state.isAir()) {
                        continue;
                    }
                    PapiBlockStatePathType access = (PapiBlockStatePathType) state;
                    if (access.papi$isCacheable()) {
                        PathType hazard = PapiPathTypeHelper.getOrComputeNeighborHazard(state, access, epoch);
                        if (hazard != null) {
                            cir.setReturnValue(hazard);
                            return;
                        }
                    } else {
                        PathType hazard = papi$evalUncachedNeighbor(context, nx, ny, nz, state);
                        if (hazard != null) {
                            cir.setReturnValue(hazard);
                            return;
                        }
                    }
                }
            }
        }
        cir.setReturnValue(fallback);
    }

    @Unique
    private static PathType papi$evalUncachedNeighbor(
            PathfindingContext context,
            int nx,
            int ny,
            int nz,
            BlockState state
    ) {
        PathType pathType = context.getPathTypeFromState(nx, ny, nz);
        CollisionGetter level = context.level();
        BlockPos evalPos = new BlockPos(nx, ny, nz);
        PathType adj = state.getAdjacentBlockPathType(level, evalPos, null, pathType);
        if (adj != null) {
            return adj;
        }
        FluidState fluid = state.getFluidState();
        PathType fluidAdj = fluid.getAdjacentBlockPathType(level, evalPos, null, pathType);
        if (fluidAdj != null) {
            return fluidAdj;
        }
        return PapiPathTypeHelper.mapSelfToNeighborHazard(pathType);
    }
}
