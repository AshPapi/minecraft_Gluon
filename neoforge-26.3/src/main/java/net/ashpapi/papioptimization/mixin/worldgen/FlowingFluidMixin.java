package net.ashpapi.papioptimization.mixin.worldgen;

import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FlowingFluid.class, priority = 1500)
public abstract class FlowingFluidMixin {

    @Unique
    private static final int PAPI$CACHE_MASK = 2047;

    @Unique
    private static final ThreadLocal<long[]> PAPI$OCCLUSION_KEYS = ThreadLocal.withInitial(() -> new long[2048]);

    @Unique
    private static final ThreadLocal<byte[]> PAPI$OCCLUSION_VALS = ThreadLocal.withInitial(() -> new byte[2048]);

    @Unique
    private FluidState papi$sourceFalse;

    @Unique
    private FluidState papi$sourceTrue;

    @Unique
    private FluidState[] papi$flowingCache;

    @Inject(method = "getSource(Z)Lnet/minecraft/world/level/material/FluidState;", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetSource(boolean falling, CallbackInfoReturnable<FluidState> cir) {
        FluidState cached = falling ? this.papi$sourceTrue : this.papi$sourceFalse;
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "getSource(Z)Lnet/minecraft/world/level/material/FluidState;", at = @At("RETURN"))
    private void papi$storeGetSource(boolean falling, CallbackInfoReturnable<FluidState> cir) {
        FluidState res = cir.getReturnValue();
        if (falling) {
            this.papi$sourceTrue = res;
        } else {
            this.papi$sourceFalse = res;
        }
    }

    @Inject(method = "getFlowing(IZ)Lnet/minecraft/world/level/material/FluidState;", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetFlowing(int level, boolean falling, CallbackInfoReturnable<FluidState> cir) {
        if (level >= 1 && level <= 8) {
            FluidState[] arr = this.papi$flowingCache;
            if (arr != null) {
                FluidState cached = arr[((level - 1) << 1) | (falling ? 1 : 0)];
                if (cached != null) {
                    cir.setReturnValue(cached);
                }
            }
        }
    }

    @Inject(method = "getFlowing(IZ)Lnet/minecraft/world/level/material/FluidState;", at = @At("RETURN"))
    private void papi$storeGetFlowing(int level, boolean falling, CallbackInfoReturnable<FluidState> cir) {
        if (level >= 1 && level <= 8) {
            FluidState[] arr = this.papi$flowingCache;
            if (arr == null) {
                arr = new FluidState[16];
                this.papi$flowingCache = arr;
            }
            arr[((level - 1) << 1) | (falling ? 1 : 0)] = cir.getReturnValue();
        }
    }

    @Inject(method = "canPassThroughWall", at = @At("HEAD"), cancellable = true)
    private static void papi$fastCanPassThroughWall(
            Direction direction, BlockGetter level,
            BlockPos fromPos, BlockState fromState,
            BlockPos toPos, BlockState toState,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (SharedConstants.DEBUG_DISABLE_LIQUID_SPREADING) {
            cir.setReturnValue(false);
            return;
        }
        if (SharedConstants.DEBUG_ONLY_GENERATE_HALF_THE_WORLD && toPos.getZ() < 0) {
            cir.setReturnValue(false);
            return;
        }
        if (papi$isEmptyFluidOrAir(fromState) && papi$isEmptyFluidOrAir(toState)) {
            cir.setReturnValue(true);
            return;
        }

        boolean cacheable = (!fromState.hasLargeCollisionShape() && !toState.hasLargeCollisionShape());
        long packedKey = 0L;
        int slot = 0;
        long[] keys = null;
        byte[] vals = null;

        if (cacheable) {
            int idA = Block.BLOCK_STATE_REGISTRY.getId(fromState);
            int idB = Block.BLOCK_STATE_REGISTRY.getId(toState);
            if (idA >= 0 && idB >= 0 && idA < 0x10000000 && idB < 0x10000000) {
                packedKey = ((long) idA) | (((long) idB) << 28) | (((long) direction.ordinal()) << 56) | (1L << 62);
                slot = (int) HashCommon.mix(packedKey) & PAPI$CACHE_MASK;
                keys = PAPI$OCCLUSION_KEYS.get();
                vals = PAPI$OCCLUSION_VALS.get();
                if (keys[slot] == packedKey) {
                    cir.setReturnValue(vals[slot] != 0);
                    return;
                }
            } else {
                cacheable = false;
            }
        }

        VoxelShape shapeFrom = fromState.getCollisionShape(level, fromPos);
        VoxelShape shapeTo = toState.getCollisionShape(level, toPos);
        boolean pass = !Shapes.mergedFaceOccludes(shapeFrom, shapeTo, direction);
        if (cacheable) {
            keys[slot] = packedKey;
            vals[slot] = (byte) (pass ? 1 : 0);
        }
        cir.setReturnValue(pass);
    }

    @Unique
    private static boolean papi$isEmptyFluidOrAir(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER) || state.is(Blocks.LAVA);
    }
}
