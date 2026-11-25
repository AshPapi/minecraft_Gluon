package net.ashpapi.papioptimization.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.biome.Climate$RTree", priority = 1500)
public abstract class ClimateRTreeFastMixin<T> {

    @Unique
    private static final ThreadLocal<long[]> PAPI$TARGET_SCRATCH = ThreadLocal.withInitial(() -> new long[7]);

    @WrapOperation(
        method = "search",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Climate$TargetPoint;toParameterArray()[J"
        )
    )
    private long[] papi$reuseParameterArray(Climate.TargetPoint point, Operation<long[]> original) {
        long[] arr = PAPI$TARGET_SCRATCH.get();
        arr[0] = point.temperature();
        arr[1] = point.humidity();
        arr[2] = point.continentalness();
        arr[3] = point.erosion();
        arr[4] = point.depth();
        arr[5] = point.weirdness();
        arr[6] = 0L;
        return arr;
    }

    @Inject(method = "sort", at = @At("HEAD"), cancellable = true)
    private static void papi$fastMultiDimSort(List<?> nodes, int dimensions, int startDim, boolean absCenter, CallbackInfo ci) {
        if (dimensions != 7 || nodes.size() <= 1) {
            return;
        }
        nodes.sort((aObj, bObj) -> {
            Climate.Parameter[] pa = ((ClimateRTreeNodeAccessor) aObj).papi$getParameterSpace();
            Climate.Parameter[] pb = ((ClimateRTreeNodeAccessor) bObj).papi$getParameterSpace();
            for (int i = 0; i < 7; i++) {
                int dim = startDim + i;
                if (dim >= 7) {
                    dim -= 7;
                }
                Climate.Parameter p1 = pa[dim];
                Climate.Parameter p2 = pb[dim];
                long c1 = (p1.min() + p1.max()) / 2L;
                long c2 = (p2.min() + p2.max()) / 2L;
                if (absCenter) {
                    c1 = Math.abs(c1);
                    c2 = Math.abs(c2);
                }
                int cmp = Long.compare(c1, c2);
                if (cmp != 0) {
                    return cmp;
                }
            }
            return 0;
        });
        ci.cancel();
    }
}
