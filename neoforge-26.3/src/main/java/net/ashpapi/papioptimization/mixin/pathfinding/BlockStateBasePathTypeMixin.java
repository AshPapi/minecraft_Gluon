package net.ashpapi.papioptimization.mixin.pathfinding;

import net.ashpapi.papioptimization.pathfinding.PapiBlockStatePathType;
import net.ashpapi.papioptimization.pathfinding.PapiPathTypeHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathType;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockBehaviour.BlockStateBase.class, priority = 1500)
public abstract class BlockStateBasePathTypeMixin implements PapiBlockStatePathType {

    @Shadow
    private boolean isAir;

    @Shadow
    public abstract Block getBlock();

    @Shadow
    public abstract FluidState getFluidState();

    @Unique
    private volatile long papi$packedPathCache = 0L;

    @Unique
    private volatile boolean papi$cacheableState = false;

    @Inject(method = "initCache", at = @At("RETURN"))
    private void papi$resetPathTypeOnInitCache(CallbackInfo ci) {
        FluidState fluid = this.getFluidState();
        boolean vanillaFluid = fluid.isEmpty() || fluid.getType().getClass().getName().startsWith("net.minecraft.");
        this.papi$cacheableState = vanillaFluid && PapiPathTypeHelper.isCacheableBlock(this.getBlock());
        this.papi$packedPathCache = 0L;
    }

    @Override
    public boolean papi$isCacheable() {
        return this.papi$cacheableState;
    }

    @Override
    public @Nullable PathType papi$getCachedPathType(int epoch) {
        if (!this.papi$cacheableState) {
            return null;
        }
        if (this.isAir) {
            return PathType.OPEN;
        }
        long packed = this.papi$packedPathCache;
        if ((int) (packed >>> 32) != epoch) {
            return null;
        }
        return PapiPathTypeHelper.bySlot((int) ((packed >>> 16) & 0xFFFFL));
    }

    @Override
    public void papi$setCachedPathType(PathType type, int epoch) {
        if (this.papi$cacheableState && !this.isAir && type != null) {
            int selfSlot = PapiPathTypeHelper.toSlot(type) & 0xFFFF;
            long prev = this.papi$packedPathCache;
            long neighborBits = ((int) (prev >>> 32) == epoch) ? (prev & 0xFFFFL) : 0L;
            this.papi$packedPathCache = (((long) epoch) << 32) | (((long) selfSlot) << 16) | neighborBits;
        }
    }

    @Override
    public int papi$getCachedNeighborSlot(int epoch) {
        if (!this.papi$cacheableState) {
            return PapiPathTypeHelper.NEIGHBOR_UNSET;
        }
        if (this.isAir) {
            return PapiPathTypeHelper.NEIGHBOR_NONE;
        }
        long packed = this.papi$packedPathCache;
        if ((int) (packed >>> 32) != epoch) {
            return PapiPathTypeHelper.NEIGHBOR_UNSET;
        }
        return (int) (packed & 0xFFFFL);
    }

    @Override
    public void papi$setCachedNeighborSlot(int slot, int epoch) {
        if (this.papi$cacheableState && !this.isAir) {
            long prev = this.papi$packedPathCache;
            long selfBits = ((int) (prev >>> 32) == epoch) ? (prev & 0xFFFF0000L) : 0L;
            this.papi$packedPathCache = (((long) epoch) << 32) | selfBits | ((long) (slot & 0xFFFF));
        }
    }
}
