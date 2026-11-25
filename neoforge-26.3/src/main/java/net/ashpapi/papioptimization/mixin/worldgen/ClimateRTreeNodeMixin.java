package net.ashpapi.papioptimization.mixin.worldgen;

import java.util.List;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.biome.Climate$RTree$Node", priority = 1500)
public abstract class ClimateRTreeNodeMixin {

    @Shadow @Final protected Climate.Parameter[] parameterSpace;

    @Unique
    private long[] papi$bounds;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$flattenBounds(List<Climate.Parameter> params, CallbackInfo ci) {
        Climate.Parameter[] ps = this.parameterSpace;
        if (ps != null && ps.length == 7) {
            long[] b = new long[14];
            for (int i = 0; i < 7; i++) {
                Climate.Parameter p = ps[i];
                if (p == null) {
                    return;
                }
                b[i << 1] = p.min();
                b[(i << 1) + 1] = p.max();
            }
            this.papi$bounds = b;
        }
    }

    @Inject(method = "distance", at = @At("HEAD"), cancellable = true)
    private void papi$fastDistance(long[] target, CallbackInfoReturnable<Long> cir) {
        long[] b = this.papi$bounds;
        if (b == null || target.length < 7) {
            return;
        }
        long sum = 0L;
        for (int i = 0; i < 7; i++) {
            long v = target[i];
            int base = i << 1;
            long dMax = v - b[base + 1];
            long d;
            if (dMax > 0L) {
                d = dMax;
            } else {
                long dMin = b[base] - v;
                d = dMin > 0L ? dMin : 0L;
            }
            sum += d * d;
        }
        cir.setReturnValue(sum);
    }
}
